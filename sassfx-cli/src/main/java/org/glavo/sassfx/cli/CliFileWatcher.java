// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/// Watches directory trees for coalesced filesystem changes.
///
/// The watcher uses the platform watch service by default. Polling mode
/// compares recursive metadata snapshots instead. Instances support one
/// thread that calls [#watch(Collection)] and [#take()]; [#close()] may be
/// called concurrently to release a blocked platform watcher.
@NotNullByDefault
final class CliFileWatcher implements AutoCloseable {
    /// The quiet period used to combine related filesystem changes.
    private static final long DEBOUNCE_NANOS =
            TimeUnit.MILLISECONDS.toNanos(25);

    /// The delay between metadata scans in polling mode.
    private static final long POLL_MILLIS = 25;

    /// Whether changes are detected using metadata snapshots.
    private final boolean poll;

    /// The normalized directory trees requested by the caller.
    private final Set<Path> watchedDirectories = new HashSet<>();

    /// Platform watch keys and the directories that produced them.
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();

    /// Directories already registered with the platform watch service.
    private final Set<Path> registeredDirectories = new HashSet<>();

    /// The last complete metadata snapshot of the requested trees.
    private Map<Path, Metadata> snapshot = new HashMap<>();

    /// The lazily created platform watch service, or {@code null}.
    private volatile @Nullable WatchService watchService;

    /// Whether this watcher has been closed.
    private volatile boolean closed;

    /// Creates a filesystem watcher.
    ///
    /// @param poll whether recursive metadata polling is used instead of the
    ///             platform watch service
    CliFileWatcher(boolean poll) {
        this.poll = poll;
    }

    /// Adds directory trees to this watcher.
    ///
    /// Paths are converted to absolute normalized paths. An absent tree is
    /// observed through its nearest existing parent until it is created.
    /// Existing contents establish a baseline and are not reported as changes.
    ///
    /// @param directories the directory trees to add
    /// @throws IOException if filesystem metadata cannot be read or a platform
    ///                     watcher cannot be created or registered
    void watch(Collection<Path> directories) throws IOException {
        Objects.requireNonNull(directories, "directories");
        ensureOpen();

        var previousDirectories = Set.copyOf(watchedDirectories);
        for (var directory : directories) {
            watchedDirectories.add(Objects.requireNonNull(
                    directory,
                    "directory"
            ).toAbsolutePath().normalize());
        }

        if (!poll) {
            var service = service();
            for (var directory : watchedDirectories) {
                if (Files.isDirectory(directory)) {
                    registerTree(directory, service);
                } else {
                    @Nullable Path parent = nearestExistingDirectory(directory);
                    if (parent != null) {
                        registerDirectory(parent, service);
                    }
                }
            }
        }
        var current = scanWatchedDirectories();
        if (previousDirectories.isEmpty()) {
            snapshot = current;
        } else {
            // Preserve metadata beneath established roots so changes that
            // occur while a new dependency root is registered remain visible.
            for (var entry : current.entrySet()) {
                if (!isCoveredBy(
                        entry.getKey(),
                        previousDirectories
                )) {
                    snapshot.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /// Reports whether a path belongs to any established watched tree.
    ///
    /// @param path the absolute normalized path
    /// @param directories established directory roots
    /// @return whether a root is equal to or an ancestor of the path
    private static boolean isCoveredBy(
            Path path,
            Collection<Path> directories
    ) {
        for (var directory : directories) {
            if (path.startsWith(directory)) {
                return true;
            }
        }
        return false;
    }

    /// Waits for and returns the next debounced set of changes.
    ///
    /// At most one change is returned for each path. Removal takes precedence
    /// over every other event, an addition remains an addition after later
    /// modifications, and all other combinations become modifications.
    ///
    /// @return an immutable list in first-observed path order
    /// @throws IOException if filesystem metadata cannot be read or this
    ///                     watcher has been closed
    /// @throws InterruptedException if the waiting thread is interrupted
    @Unmodifiable List<Change> take()
            throws IOException, InterruptedException {
        ensureOpen();
        return poll ? takePolled() : takeWatched();
    }

    /// Closes the platform watcher and releases a blocked [#take()] call.
    ///
    /// Repeated calls have no effect.
    ///
    /// @throws IOException if the platform watch service cannot be closed
    @Override
    public synchronized void close() throws IOException {
        closed = true;
        @Nullable WatchService service = watchService;
        if (service != null) {
            service.close();
        }
    }

    /// Waits for changes produced by the platform watch service.
    ///
    /// @return one immutable debounced batch
    /// @throws IOException if filesystem state cannot be reconciled
    /// @throws InterruptedException if the waiting thread is interrupted
    private @Unmodifiable List<Change> takeWatched()
            throws IOException, InterruptedException {
        var service = service();
        var merged = new LinkedHashMap<Path, Kind>();
        while (merged.isEmpty()) {
            WatchKey key;
            try {
                key = service.take();
            } catch (ClosedWatchServiceException exception) {
                throw closedException(exception);
            }
            processKey(key, service, merged);

            var deadline = System.nanoTime() + DEBOUNCE_NANOS;
            while (true) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                try {
                    key = service.poll(remaining, TimeUnit.NANOSECONDS);
                } catch (ClosedWatchServiceException exception) {
                    throw closedException(exception);
                }
                if (key == null) {
                    break;
                }
                processKey(key, service, merged);
                deadline = System.nanoTime() + DEBOUNCE_NANOS;
            }
            reconcileSnapshot(merged);
        }
        return changes(merged);
    }

    /// Waits for changes detected by recursive metadata comparison.
    ///
    /// @return one immutable debounced batch
    /// @throws IOException if filesystem metadata cannot be read
    /// @throws InterruptedException if the waiting thread is interrupted
    private @Unmodifiable List<Change> takePolled()
            throws IOException, InterruptedException {
        var merged = new LinkedHashMap<Path, Kind>();
        while (merged.isEmpty()) {
            ensureOpen();
            Thread.sleep(POLL_MILLIS);
            reconcileSnapshot(merged);
        }

        var deadline = System.nanoTime() + DEBOUNCE_NANOS;
        while (true) {
            var remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            TimeUnit.NANOSECONDS.sleep(remaining);
            if (reconcileSnapshot(merged)) {
                deadline = System.nanoTime() + DEBOUNCE_NANOS;
            } else {
                break;
            }
        }
        return changes(merged);
    }

    /// Processes all events currently associated with one watch key.
    ///
    /// @param key the signalled platform key
    /// @param service the owning platform watch service
    /// @param merged the batch receiving coalesced changes
    /// @throws IOException if a newly created directory cannot be registered
    private void processKey(
            WatchKey key,
            WatchService service,
            Map<Path, Kind> merged
    ) throws IOException {
        @Nullable Path directory = watchKeys.get(key);
        if (directory == null) {
            key.reset();
            return;
        }

        var overflow = false;
        for (var event : key.pollEvents()) {
            var eventKind = event.kind();
            if (eventKind == StandardWatchEventKinds.OVERFLOW) {
                overflow = true;
                continue;
            }

            @SuppressWarnings("unchecked")
            var pathEvent = (WatchEvent<Path>) event;
            var path = directory.resolve(pathEvent.context())
                    .toAbsolutePath()
                    .normalize();
            if (eventKind == StandardWatchEventKinds.ENTRY_CREATE
                    && Files.isDirectory(path)
                    && isRegistrationRelevant(path)) {
                registerTree(path, service);
            }
            if (isReportedPath(path)) {
                merge(
                        merged,
                        path,
                        eventKind == StandardWatchEventKinds.ENTRY_CREATE
                                ? Kind.ADD
                                : eventKind
                                == StandardWatchEventKinds.ENTRY_DELETE
                                ? Kind.REMOVE
                                : Kind.MODIFY
                );
            }
        }

        if (!key.reset()) {
            watchKeys.remove(key);
            registeredDirectories.remove(directory);
            registerNearestExistingDirectories(service);
        }
        if (overflow) {
            reconcileSnapshot(merged);
        }
    }

    /// Replaces the baseline and merges its differences into one batch.
    ///
    /// @param merged the batch receiving snapshot differences
    /// @return whether at least one metadata difference was observed
    /// @throws IOException if filesystem metadata cannot be read
    private boolean reconcileSnapshot(Map<Path, Kind> merged)
            throws IOException {
        var current = scanWatchedDirectories();
        var paths = new TreeSet<Path>();
        paths.addAll(snapshot.keySet());
        paths.addAll(current.keySet());
        var changed = false;
        for (var path : paths) {
            @Nullable Metadata before = snapshot.get(path);
            @Nullable Metadata after = current.get(path);
            if (before == null) {
                merge(merged, path, Kind.ADD);
                changed = true;
            } else if (after == null) {
                merge(merged, path, Kind.REMOVE);
                changed = true;
            } else if (!before.equivalentTo(after)) {
                merge(merged, path, Kind.MODIFY);
                changed = true;
            }
        }
        snapshot = current;
        return changed;
    }

    /// Returns a recursive metadata snapshot of all requested trees.
    ///
    /// Concurrently removed entries are omitted from the snapshot.
    ///
    /// @return a mutable snapshot owned by this watcher
    /// @throws IOException if an accessible entry cannot be inspected
    private Map<Path, Metadata> scanWatchedDirectories() throws IOException {
        var result = new HashMap<Path, Metadata>();
        for (var directory : watchedDirectories) {
            if (!Files.exists(directory)) {
                continue;
            }
            try {
                Files.walkFileTree(
                        directory,
                        new SimpleFileVisitor<>() {
                        /// Records one visited directory.
                        ///
                        /// @param path the visited directory
                        /// @param attributes its metadata
                        /// @return continuation status
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path path,
                                BasicFileAttributes attributes
                        ) {
                            result.put(
                                    path.toAbsolutePath().normalize(),
                                    Metadata.from(attributes)
                            );
                            return FileVisitResult.CONTINUE;
                        }

                        /// Records one visited non-directory entry.
                        ///
                        /// @param path the visited path
                        /// @param attributes its metadata
                        /// @return continuation status
                        @Override
                        public FileVisitResult visitFile(
                                Path path,
                                BasicFileAttributes attributes
                        ) {
                            result.put(
                                    path.toAbsolutePath().normalize(),
                                    Metadata.from(attributes)
                            );
                            return FileVisitResult.CONTINUE;
                        }

                        /// Ignores entries that disappeared during traversal.
                        ///
                        /// @param path the failed path
                        /// @param failure the traversal failure
                        /// @return continuation status for a disappeared path
                        /// @throws IOException if the failure was not caused by
                        ///                     concurrent removal
                        @Override
                        public FileVisitResult visitFileFailed(
                                Path path,
                                IOException failure
                        ) throws IOException {
                            if (failure instanceof NoSuchFileException) {
                                return FileVisitResult.CONTINUE;
                            }
                            throw failure;
                        }
                        }
                );
            } catch (NoSuchFileException ignored) {
                // A tree removed before traversal is absent from the snapshot.
            }
        }
        return result;
    }

    /// Registers every existing directory in one tree.
    ///
    /// @param root the root directory
    /// @param service the platform watch service
    /// @throws IOException if traversal or registration fails
    private void registerTree(Path root, WatchService service)
            throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try {
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<>() {
                        /// Registers one visited directory.
                        ///
                        /// @param directory the visited directory
                        /// @param attributes its metadata
                        /// @return continuation status
                        /// @throws IOException if registration fails
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path directory,
                                BasicFileAttributes attributes
                        ) throws IOException {
                            registerDirectory(
                                    directory.toAbsolutePath().normalize(),
                                    service
                            );
                            return FileVisitResult.CONTINUE;
                        }

                        /// Ignores directories removed during registration.
                        ///
                        /// @param path the failed path
                        /// @param failure the traversal failure
                        /// @return continuation status for a disappeared path
                        /// @throws IOException if the failure was not caused by
                        ///                     concurrent removal
                        @Override
                        public FileVisitResult visitFileFailed(
                                Path path,
                                IOException failure
                        ) throws IOException {
                            if (failure instanceof NoSuchFileException) {
                                return FileVisitResult.CONTINUE;
                            }
                            throw failure;
                        }
                    }
            );
        } catch (NoSuchFileException ignored) {
            // A directory removed during registration needs no watch key.
        }
    }

    /// Registers one directory unless it is already registered.
    ///
    /// @param directory the absolute normalized directory
    /// @param service the platform watch service
    /// @throws IOException if registration fails
    private void registerDirectory(Path directory, WatchService service)
            throws IOException {
        if (!registeredDirectories.add(directory)) {
            return;
        }
        try {
            var key = directory.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );
            watchKeys.put(key, directory);
        } catch (ClosedWatchServiceException failure) {
            registeredDirectories.remove(directory);
            throw closedException(failure);
        } catch (IOException | RuntimeException failure) {
            registeredDirectories.remove(directory);
            throw failure;
        }
    }

    /// Registers the nearest existing directory for every absent request.
    ///
    /// This restores observation of a requested tree after that tree, or one
    /// of its registered ancestors, is removed.
    ///
    /// @param service the platform watch service
    /// @throws IOException if registration fails
    private void registerNearestExistingDirectories(WatchService service)
            throws IOException {
        for (var directory : watchedDirectories) {
            if (Files.isDirectory(directory)) {
                continue;
            }
            @Nullable Path parent = nearestExistingDirectory(directory);
            if (parent != null) {
                registerDirectory(parent, service);
            }
        }
    }

    /// Returns the nearest existing directory at or above one path.
    ///
    /// @param path the absolute normalized requested path
    /// @return an existing directory, or {@code null} when none is available
    private static @Nullable Path nearestExistingDirectory(Path path) {
        @Nullable Path current = path;
        while (current != null) {
            if (Files.isDirectory(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /// Returns whether an event path belongs to a requested tree.
    ///
    /// @param path the absolute normalized event path
    /// @return whether the path should be reported
    private boolean isReportedPath(Path path) {
        for (var directory : watchedDirectories) {
            if (path.startsWith(directory)) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether a created directory may lead to a requested tree.
    ///
    /// @param path the absolute normalized created directory
    /// @return whether the directory should be registered recursively
    private boolean isRegistrationRelevant(Path path) {
        for (var directory : watchedDirectories) {
            if (path.startsWith(directory) || directory.startsWith(path)) {
                return true;
            }
        }
        return false;
    }

    /// Returns the platform watch service, creating it when necessary.
    ///
    /// @return the open platform watch service
    /// @throws IOException if the service cannot be created or the watcher was
    ///                     closed
    private WatchService service() throws IOException {
        @Nullable WatchService current = watchService;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            ensureOpen();
            current = watchService;
            if (current == null) {
                current = FileSystems.getDefault().newWatchService();
                watchService = current;
            }
            return current;
        }
    }

    /// Throws if this watcher is closed.
    ///
    /// @throws IOException if this watcher is closed
    private void ensureOpen() throws IOException {
        if (closed) {
            throw closedException(null);
        }
    }

    /// Creates an IO failure for an operation on a closed watcher.
    ///
    /// @param cause the platform closure failure, or {@code null}
    /// @return the checked closure failure
    private static IOException closedException(
            @Nullable ClosedWatchServiceException cause
    ) {
        return new IOException("file watcher is closed", cause);
    }

    /// Merges one path event into a pending batch.
    ///
    /// @param changes the pending changes
    /// @param path the absolute normalized changed path
    /// @param kind the newly observed kind
    private static void merge(
            Map<Path, Kind> changes,
            Path path,
            Kind kind
    ) {
        changes.merge(path, kind, CliFileWatcher::mergeKinds);
    }

    /// Combines two event kinds according to CLI debounce semantics.
    ///
    /// @param previous the previously observed kind
    /// @param current the newly observed kind
    /// @return the combined kind
    private static Kind mergeKinds(Kind previous, Kind current) {
        if (previous == Kind.REMOVE || current == Kind.REMOVE) {
            return Kind.REMOVE;
        }
        if (previous == Kind.ADD || current == Kind.ADD) {
            return Kind.ADD;
        }
        return Kind.MODIFY;
    }

    /// Creates immutable change values from one pending batch.
    ///
    /// @param merged the path-to-kind batch in reporting order
    /// @return an immutable list of changes
    private static @Unmodifiable List<Change> changes(
            Map<Path, Kind> merged
    ) {
        return merged.entrySet().stream()
                .map(entry -> new Change(entry.getKey(), entry.getValue()))
                .toList();
    }

    /// Describes a coalesced filesystem event.
    ///
    /// @param path the absolute normalized changed path
    /// @param kind the coalesced change kind
    @NotNullByDefault
    record Change(Path path, Kind kind) {
        /// Validates one filesystem change.
        Change {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(kind, "kind");
            if (!path.isAbsolute() || !path.equals(path.normalize())) {
                throw new IllegalArgumentException(
                        "change path must be absolute and normalized"
                );
            }
        }
    }

    /// Identifies a coalesced filesystem change.
    @NotNullByDefault
    enum Kind {
        /// A path was created.
        ADD,

        /// Existing path metadata changed.
        MODIFY,

        /// A path was removed.
        REMOVE
    }

    /// Stores metadata used to compare two recursive snapshots.
    ///
    /// @param directory whether the entry is a directory
    /// @param modifiedTime its last-modified time
    /// @param size its byte size
    /// @param fileKey its filesystem identity, or {@code null}
    @NotNullByDefault
    private record Metadata(
            boolean directory,
            FileTime modifiedTime,
            long size,
            @Nullable Object fileKey
    ) {
        /// Validates one metadata snapshot entry.
        private Metadata {
            Objects.requireNonNull(modifiedTime, "modifiedTime");
        }

        /// Creates metadata from basic filesystem attributes.
        ///
        /// @param attributes the source attributes
        /// @return the captured metadata
        private static Metadata from(BasicFileAttributes attributes) {
            return new Metadata(
                    attributes.isDirectory(),
                    attributes.lastModifiedTime(),
                    attributes.size(),
                    attributes.fileKey()
            );
        }

        /// Returns whether another snapshot describes the same entry state.
        ///
        /// Directory timestamp and size changes caused by child entries are
        /// ignored.
        ///
        /// @param other the later metadata
        /// @return whether no reportable metadata changed
        private boolean equivalentTo(Metadata other) {
            if (directory != other.directory) {
                return false;
            }
            if (directory) {
                return fileKey == null
                        || other.fileKey == null
                        || fileKey.equals(other.fileKey);
            }
            return modifiedTime.equals(other.modifiedTime)
                    && size == other.size
                    && (fileKey == null
                    || other.fileKey == null
                    || fileKey.equals(other.fileKey));
        }
    }
}
