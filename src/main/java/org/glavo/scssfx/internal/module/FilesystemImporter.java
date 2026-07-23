// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.Syntax;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Resolves and loads SCSS, indented Sass, or plain CSS files from the filesystem.
@ApiStatus.Internal
@NotNullByDefault
public final class FilesystemImporter {
    /// Contains absolute load-path directories.
    private final @Unmodifiable List<Path> loadPaths;

    /// Creates an importer.
    ///
    /// @param loadPaths additional directories searched after the base URL
    public FilesystemImporter(List<Path> loadPaths) {
        Objects.requireNonNull(loadPaths, "loadPaths");
        var normalized = new ArrayList<Path>(loadPaths.size());
        for (var path : loadPaths) {
            normalized.add(path.toAbsolutePath().normalize());
        }
        this.loadPaths = List.copyOf(normalized);
    }

    /// Canonicalizes and loads a stylesheet URL.
    ///
    /// @param url     the unresolved import URL
    /// @param baseUrl the canonical URL of the containing stylesheet, or {@code null}
    /// @return the loaded stylesheet, or {@code null} when no candidate exists
    /// @throws IOException if a candidate file cannot be read
    /// @throws IllegalStateException if multiple candidates exist at the same
    /// search location
    public @Nullable ImportResult canonicalizeAndLoad(
            String url,
            @Nullable URI baseUrl
    ) throws IOException {
        return canonicalizeAndLoad(url, baseUrl, false);
    }

    /// Canonicalizes and loads a stylesheet using legacy import resolution.
    ///
    /// Import-only files are preferred at each search location. Ordinary
    /// module resolution remains unchanged for [#canonicalizeAndLoad].
    ///
    /// @param url     the unresolved import URL
    /// @param baseUrl the canonical URL of the containing stylesheet, or {@code null}
    /// @return the loaded stylesheet, or {@code null} when no candidate exists
    /// @throws IOException if a candidate file cannot be read
    /// @throws IllegalStateException if multiple candidates exist at the same
    /// search location
    public @Nullable ImportResult canonicalizeAndLoadImport(
            String url,
            @Nullable URI baseUrl
    ) throws IOException {
        return canonicalizeAndLoad(url, baseUrl, true);
    }

    /// Resolves a URL using module or legacy-import candidate precedence.
    ///
    /// @param url       the unresolved stylesheet URL
    /// @param baseUrl   the containing stylesheet URL, or {@code null}
    /// @param forImport whether import-only candidates take precedence
    /// @return the loaded stylesheet, or {@code null} when no candidate exists
    /// @throws IOException if a candidate cannot be read
    /// @throws IllegalStateException if a search location is ambiguous
    private @Nullable ImportResult canonicalizeAndLoad(
            String url,
            @Nullable URI baseUrl,
            boolean forImport
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        if (baseUrl != null && "file".equalsIgnoreCase(baseUrl.getScheme())) {
            var basePath = Path.of(baseUrl).getParent();
            if (basePath != null) {
                @Nullable Path candidate = resolveAt(basePath.resolve(url).normalize(), forImport);
                if (candidate != null) {
                    return load(candidate);
                }
            }
        }
        for (var loadPath : loadPaths) {
            @Nullable Path candidate = resolveAt(loadPath.resolve(url).normalize(), forImport);
            if (candidate != null) {
                return load(candidate);
            }
        }
        return null;
    }

    /// Resolves one path stem without consulting any lower-priority search location.
    ///
    /// @param path the path stem to resolve
    /// @param forImport whether import-only candidates take precedence
    /// @return the sole matching file, or {@code null} when this location has no match
    /// @throws IllegalStateException if this location produces multiple candidates
    private static @Nullable Path resolveAt(Path path, boolean forImport) {
        var candidates = new ArrayList<Path>();
        if (forImport) {
            addImportOnlyCandidates(candidates, path);
            if (!candidates.isEmpty()) {
                return exactlyOne(candidates);
            }
        }
        addCandidates(candidates, path);
        if (!candidates.isEmpty()) {
            return exactlyOne(candidates);
        }
        if (forImport) {
            addImportOnlyIndexCandidates(candidates, path);
            if (!candidates.isEmpty()) {
                return exactlyOne(candidates);
            }
        }
        addIndexCandidates(candidates, path);
        if (!candidates.isEmpty()) {
            return exactlyOne(candidates);
        }
        return null;
    }

    /// Loads a resolved stylesheet and derives its canonical URL from the real path.
    ///
    /// @param path the existing stylesheet path
    /// @return the loaded Sass source
    /// @throws IOException if the real path or contents cannot be read
    private static ImportResult load(Path path) throws IOException {
        var realPath = path.toRealPath();
        var content = Files.readString(realPath, StandardCharsets.UTF_8);
        var canonical = realPath.toUri();
        var syntax = Objects.requireNonNull(
                Syntax.forPath(realPath),
                "resolved stylesheet must have a recognized syntax extension"
        );
        return new ImportResult(
                new SourceFile(content, canonical),
                syntax,
                canonical
        );
    }

    /// Returns the sole candidate or reports an ambiguity.
    ///
    /// @param candidates the nonempty candidate list
    /// @return the sole candidate
    /// @throws IllegalStateException if more than one candidate exists
    private static Path exactlyOne(List<Path> candidates) {
        if (candidates.size() > 1) {
            throw ambiguousCandidates(candidates);
        }
        return candidates.get(0);
    }

    /// Creates a failure describing all candidates at one search location.
    ///
    /// @param candidates the conflicting paths in resolution order
    /// @return the ambiguity failure
    private static IllegalStateException ambiguousCandidates(List<Path> candidates) {
        var message = new StringBuilder(
                "It's not clear which file to import. Found:\n"
        );
        for (var candidate : candidates) {
            message.append("  ").append(candidate).append('\n');
        }
        return new IllegalStateException(message.toString().trim());
    }

    /// Adds resolvable SCSS, Sass, and CSS candidates for one path stem.
    ///
    /// @param candidates the mutable destination list
    /// @param path       the path stem to inspect
    private static void addCandidates(List<Path> candidates, Path path) {
        var fileName = path.getFileName();
        if (fileName == null) {
            return;
        }
        var name = fileName.toString();
        var lowerName = name.toLowerCase(Locale.ROOT);
        var parent = path.getParent();
        if (lowerName.endsWith(".scss")
                || lowerName.endsWith(".sass")
                || lowerName.endsWith(".css")) {
            addIfRegular(candidates, path);
            if (parent != null && !name.startsWith("_")) {
                addIfRegular(candidates, parent.resolve("_" + name));
            }
            return;
        }
        if (parent == null) {
            addIfRegular(candidates, Path.of(name + ".scss"));
            addIfRegular(candidates, Path.of("_" + name + ".scss"));
            addIfRegular(candidates, Path.of(name + ".sass"));
            addIfRegular(candidates, Path.of("_" + name + ".sass"));
            if (!candidates.isEmpty()) {
                return;
            }
            addIfRegular(candidates, Path.of(name + ".css"));
            addIfRegular(candidates, Path.of("_" + name + ".css"));
            return;
        }
        addIfRegular(candidates, parent.resolve(name + ".scss"));
        addIfRegular(candidates, parent.resolve("_" + name + ".scss"));
        addIfRegular(candidates, parent.resolve(name + ".sass"));
        addIfRegular(candidates, parent.resolve("_" + name + ".sass"));
        if (!candidates.isEmpty()) {
            return;
        }
        addIfRegular(candidates, parent.resolve(name + ".css"));
        addIfRegular(candidates, parent.resolve("_" + name + ".css"));
    }

    /// Adds ordinary directory-index candidates for one extensionless path.
    ///
    /// @param candidates the mutable destination list
    /// @param path       the path stem to inspect
    private static void addIndexCandidates(List<Path> candidates, Path path) {
        var fileName = path.getFileName();
        var parent = path.getParent();
        if (fileName == null || parent == null) {
            return;
        }
        var directory = parent.resolve(fileName.toString());
        addIfRegular(candidates, directory.resolve("index.scss"));
        addIfRegular(candidates, directory.resolve("_index.scss"));
        addIfRegular(candidates, directory.resolve("index.sass"));
        addIfRegular(candidates, directory.resolve("_index.sass"));
        if (!candidates.isEmpty()) {
            return;
        }
        addIfRegular(candidates, directory.resolve("index.css"));
        addIfRegular(candidates, directory.resolve("_index.css"));
    }

    /// Adds import-only directory-index candidates for one extensionless path.
    ///
    /// @param candidates the mutable destination list
    /// @param path       the path stem to inspect
    private static void addImportOnlyIndexCandidates(
            List<Path> candidates,
            Path path
    ) {
        var fileName = path.getFileName();
        var parent = path.getParent();
        if (fileName == null || parent == null) {
            return;
        }
        var directory = parent.resolve(fileName.toString());
        addImportPair(candidates, directory, "index.import.scss");
        addImportPair(candidates, directory, "index.import.sass");
        if (!candidates.isEmpty()) {
            return;
        }
        addImportPair(candidates, directory, "index.import.css");
    }

    /// Adds import-only SCSS, Sass, and CSS candidates for one path stem.
    ///
    /// @param candidates the mutable destination list
    /// @param path       the path stem to inspect
    private static void addImportOnlyCandidates(List<Path> candidates, Path path) {
        var fileName = path.getFileName();
        if (fileName == null) {
            return;
        }
        var name = fileName.toString();
        var lowerName = name.toLowerCase(Locale.ROOT);
        var parent = path.getParent();
        if (lowerName.endsWith(".scss")
                || lowerName.endsWith(".sass")
                || lowerName.endsWith(".css")) {
            var extensionIndex = name.lastIndexOf('.');
            var importName = name.substring(0, extensionIndex)
                    + ".import"
                    + name.substring(extensionIndex);
            addImportPair(candidates, parent, importName);
            return;
        }

        addImportPair(candidates, parent, name + ".import.scss");
        addImportPair(candidates, parent, name + ".import.sass");
        if (!candidates.isEmpty()) {
            return;
        }
        addImportPair(candidates, parent, name + ".import.css");
    }

    /// Adds a regular and partial import-only candidate pair.
    ///
    /// @param candidates the mutable destination list
    /// @param parent     the containing directory, or {@code null}
    /// @param name       the regular candidate file name
    private static void addImportPair(
            List<Path> candidates,
            @Nullable Path parent,
            String name
    ) {
        var regular = parent == null ? Path.of(name) : parent.resolve(name);
        addIfRegular(candidates, regular);
        if (!name.startsWith("_")) {
            var partial = "_" + name;
            addIfRegular(
                    candidates,
                    parent == null ? Path.of(partial) : parent.resolve(partial)
            );
        }
    }

    /// Adds a path when it is an existing regular file.
    ///
    /// @param candidates the mutable destination list
    /// @param path       the candidate path
    private static void addIfRegular(List<Path> candidates, Path path) {
        if (Files.isRegularFile(path) && !candidates.contains(path)) {
            candidates.add(path);
        }
    }
}
