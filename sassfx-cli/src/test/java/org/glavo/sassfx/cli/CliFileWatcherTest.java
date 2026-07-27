// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies recursive native and polling file-change detection.
@NotNullByDefault
final class CliFileWatcherTest {
    /// The longest time a test waits for one debounced change batch.
    private static final Duration CHANGE_TIMEOUT = Duration.ofSeconds(3);

    /// The bounded delay used to prove that a baseline produces no event.
    private static final Duration BASELINE_DELAY = Duration.ofMillis(100);

    /// The longest time a worker may take to react to interruption or closure.
    private static final Duration THREAD_TIMEOUT = Duration.ofSeconds(3);

    /// Ignores baseline contents and reports add, modify, and remove changes.
    @Test
    void reportsBaselineAddModifyAndRemove(@TempDir Path directory)
            throws Exception {
        forEachMode(poll -> {
            var root = directory.resolve(poll ? "poll" : "native");
            Files.createDirectories(root);
            var existing = root.resolve("existing.scss");
            Files.writeString(existing, "one");

            try (var watcher = new CliFileWatcher(poll)) {
                watcher.watch(List.of(root.resolve("child").resolve("..")));

                var executor = Executors.newSingleThreadExecutor();
                try {
                    var baseline = executor.submit(watcher::take);
                    Thread.sleep(BASELINE_DELAY.toMillis());
                    assertFalse(baseline.isDone(), "baseline must not produce a change");

                    Files.writeString(existing, "replacement content");
                    var modified = await(baseline);
                    assertChange(
                            modified,
                            existing,
                            CliFileWatcher.Kind.MODIFY
                    );
                    assertNormalized(modified);
                } finally {
                    executor.shutdownNow();
                    assertTrue(
                            executor.awaitTermination(
                                    THREAD_TIMEOUT.toMillis(),
                                    TimeUnit.MILLISECONDS
                            )
                    );
                }

                var added = root.resolve("added.scss");
                assertChange(
                        changesAfter(watcher, () -> Files.writeString(added, "new")),
                        added,
                        CliFileWatcher.Kind.ADD
                );
                assertChange(
                        changesAfter(watcher, () -> Files.delete(added)),
                        added,
                        CliFileWatcher.Kind.REMOVE
                );
            }
        });
    }

    /// Detects files created in a newly created nested directory.
    @Test
    void detectsFilesInNewNestedDirectories(@TempDir Path directory)
            throws Exception {
        forEachMode(poll -> {
            var root = directory.resolve(poll ? "poll" : "native");
            Files.createDirectories(root);
            var nestedFile = root.resolve("first").resolve("second")
                    .resolve("style.scss");

            try (var watcher = new CliFileWatcher(poll)) {
                watcher.watch(List.of(root));
                assertChange(
                        changesAfter(watcher, () -> {
                            Files.createDirectories(nestedFile.getParent());
                            Files.writeString(nestedFile, "a { color: red; }");
                        }),
                        nestedFile,
                        CliFileWatcher.Kind.ADD
                );
            }
        });
    }

    /// Detects a requested tree after its initially missing root is created.
    @Test
    void detectsInitiallyMissingRoot(@TempDir Path directory)
            throws Exception {
        forEachMode(poll -> {
            var root = directory.resolve(poll ? "poll" : "native")
                    .resolve("missing")
                    .resolve("root");
            var source = root.resolve("style.scss");

            try (var watcher = new CliFileWatcher(poll)) {
                watcher.watch(List.of(root));
                assertChange(
                        changesAfter(watcher, () -> {
                            Files.createDirectories(root);
                            Files.writeString(source, "a { color: blue; }");
                        }),
                        source,
                        CliFileWatcher.Kind.ADD
                );
            }
        });
    }

    /// Coalesces a create-modify-delete burst into one removal in native mode.
    @Test
    void coalescesNativeBurstWithRemovalPrecedence(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("native");
        Files.createDirectories(root);
        var source = root.resolve("transient.scss");

        try (var watcher = new CliFileWatcher(false)) {
            watcher.watch(List.of(root));
            Files.writeString(source, "one");
            Files.writeString(source, "two", StandardOpenOption.APPEND);
            Files.delete(source);

            var changes = awaitFromWatcher(watcher);
            assertEquals(
                    CliFileWatcher.Kind.REMOVE,
                    kindOf(changes, source),
                    changes.toString()
            );
            assertNormalized(changes);
        }
    }

    /// Interrupts a pending take operation in both watcher modes.
    @Test
    void interruptsPendingTake(@TempDir Path directory) throws Exception {
        forEachMode(poll -> {
            var root = directory.resolve(poll ? "poll" : "native");
            Files.createDirectories(root);

            try (var watcher = new CliFileWatcher(poll)) {
                watcher.watch(List.of(root));
                var failure = new AtomicReference<@Nullable Throwable>();
                var entered = new CountDownLatch(1);
                var thread = new Thread(() -> {
                    entered.countDown();
                    try {
                        watcher.take();
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    }
                });
                thread.start();
                assertTrue(entered.await(THREAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                thread.interrupt();
                thread.join(THREAD_TIMEOUT.toMillis());

                assertFalse(thread.isAlive());
                assertInstanceOf(InterruptedException.class, failure.get());
            }
        });
    }

    /// Closes a pending take operation in both watcher modes.
    @Test
    void closesPendingTake(@TempDir Path directory) throws Exception {
        forEachMode(poll -> {
            var root = directory.resolve(poll ? "poll" : "native");
            Files.createDirectories(root);
            var watcher = new CliFileWatcher(poll);
            watcher.watch(List.of(root));
            var failure = new AtomicReference<@Nullable Throwable>();
            var entered = new CountDownLatch(1);
            var thread = new Thread(() -> {
                entered.countDown();
                try {
                    watcher.take();
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            });
            thread.start();
            assertTrue(entered.await(THREAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            watcher.close();
            thread.join(THREAD_TIMEOUT.toMillis());

            assertFalse(thread.isAlive());
            assertInstanceOf(IOException.class, failure.get());
        });
    }

    /// Runs one check against the native and polling implementations.
    ///
    /// @param test the mode-specific assertion
    /// @throws Exception if either mode fails
    private static void forEachMode(WatcherTest test) throws Exception {
        test.run(false);
        test.run(true);
    }

    /// Starts a pending take, performs a filesystem mutation, and returns it.
    ///
    /// @param watcher the watcher that will report the mutation
    /// @param action the filesystem mutation
    /// @return the debounced change batch
    /// @throws Exception if waiting or the action fails
    private static @Unmodifiable List<CliFileWatcher.Change> changesAfter(
            CliFileWatcher watcher,
            ThrowingAction action
    ) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            var changes = executor.submit(watcher::take);
            action.run();
            return await(changes);
        } finally {
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(
                            THREAD_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    )
            );
        }
    }

    /// Waits for a watcher without performing an additional mutation.
    ///
    /// @param watcher the watcher with a previously queued change
    /// @return the debounced change batch
    /// @throws Exception if the watcher does not report a batch
    private static @Unmodifiable List<CliFileWatcher.Change> awaitFromWatcher(
            CliFileWatcher watcher
    ) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            return await(executor.submit(watcher::take));
        } finally {
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(
                            THREAD_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS
                    )
            );
        }
    }

    /// Waits for one bounded asynchronous operation.
    ///
    /// @param future the operation to await
    /// @return its result
    /// @throws Exception if it does not complete successfully in time
    private static @Unmodifiable List<CliFileWatcher.Change> await(
            Future<List<CliFileWatcher.Change>> future
    ) throws Exception {
        return future.get(CHANGE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    /// Asserts that a batch contains a change with the expected path and kind.
    ///
    /// @param changes the observed batch
    /// @param path the expected path
    /// @param kind the expected kind
    private static void assertChange(
            @Unmodifiable List<CliFileWatcher.Change> changes,
            Path path,
            CliFileWatcher.Kind kind
    ) {
        assertEquals(kind, kindOf(changes, path), changes.toString());
        assertNormalized(changes);
    }

    /// Returns the reported kind for one path.
    ///
    /// @param changes the observed batch
    /// @param path the queried path
    /// @return the reported kind
    private static CliFileWatcher.Kind kindOf(
            @Unmodifiable List<CliFileWatcher.Change> changes,
            Path path
    ) {
        var normalizedPath = path.toAbsolutePath().normalize();
        for (var change : changes) {
            if (change.path().equals(normalizedPath)) {
                return change.kind();
            }
        }
        assertTrue(false, "missing change for " + normalizedPath + ": " + changes);
        throw new AssertionError("unreachable");
    }

    /// Verifies the path normalization guarantee for one batch.
    ///
    /// @param changes the observed batch
    private static void assertNormalized(
            @Unmodifiable List<CliFileWatcher.Change> changes
    ) {
        for (var change : changes) {
            assertTrue(change.path().isAbsolute(), changes.toString());
            assertEquals(change.path().normalize(), change.path(), changes.toString());
        }
    }

    /// Runs a throwing test operation for one watcher mode.
    @FunctionalInterface
    @NotNullByDefault
    private interface WatcherTest {
        /// Runs one watcher-mode assertion.
        ///
        /// @param poll whether the polling backend is being tested
        /// @throws Exception if the assertion fails
        void run(boolean poll) throws Exception;
    }

    /// Performs one filesystem mutation that may fail.
    @FunctionalInterface
    @NotNullByDefault
    private interface ThrowingAction {
        /// Performs the mutation.
        ///
        /// @throws Exception if the mutation cannot be performed
        void run() throws Exception;
    }
}
