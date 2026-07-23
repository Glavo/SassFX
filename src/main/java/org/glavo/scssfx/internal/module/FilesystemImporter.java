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
import java.util.Objects;

/// Resolves and loads SCSS or indented Sass files from the filesystem.
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
        Objects.requireNonNull(url, "url");
        if (baseUrl != null && "file".equalsIgnoreCase(baseUrl.getScheme())) {
            var basePath = Path.of(baseUrl).getParent();
            if (basePath != null) {
                @Nullable Path candidate = resolveAt(basePath.resolve(url).normalize());
                if (candidate != null) {
                    return load(candidate);
                }
            }
        }
        for (var loadPath : loadPaths) {
            @Nullable Path candidate = resolveAt(loadPath.resolve(url).normalize());
            if (candidate != null) {
                return load(candidate);
            }
        }
        return null;
    }

    /// Resolves one path stem without consulting any lower-priority search location.
    ///
    /// @param path the path stem to resolve
    /// @return the sole matching file, or {@code null} when this location has no match
    /// @throws IllegalStateException if this location produces multiple candidates
    private static @Nullable Path resolveAt(Path path) {
        var candidates = new ArrayList<Path>();
        addCandidates(candidates, path);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            throw ambiguousCandidates(candidates);
        }
        return candidates.get(0);
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

    /// Adds resolvable SCSS and Sass candidates for one path stem.
    ///
    /// @param candidates the mutable destination list
    /// @param path       the path stem to inspect
    private static void addCandidates(List<Path> candidates, Path path) {
        var fileName = path.getFileName();
        if (fileName == null) {
            return;
        }
        var name = fileName.toString();
        var parent = path.getParent();
        if (name.endsWith(".scss") || name.endsWith(".sass")) {
            addIfRegular(candidates, path);
            if (parent != null && !name.startsWith("_")) {
                addIfRegular(candidates, parent.resolve("_" + name));
            }
            return;
        }
        if (name.contains(".")) {
            return;
        }
        if (parent == null) {
            addIfRegular(candidates, Path.of(name + ".scss"));
            addIfRegular(candidates, Path.of("_" + name + ".scss"));
            addIfRegular(candidates, Path.of(name + ".sass"));
            addIfRegular(candidates, Path.of("_" + name + ".sass"));
            return;
        }
        addIfRegular(candidates, parent.resolve(name + ".scss"));
        addIfRegular(candidates, parent.resolve("_" + name + ".scss"));
        addIfRegular(candidates, parent.resolve(name + ".sass"));
        addIfRegular(candidates, parent.resolve("_" + name + ".sass"));
        addIfRegular(candidates, parent.resolve(name).resolve("index.scss"));
        addIfRegular(candidates, parent.resolve(name).resolve("_index.scss"));
        addIfRegular(candidates, parent.resolve(name).resolve("index.sass"));
        addIfRegular(candidates, parent.resolve(name).resolve("_index.sass"));
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
