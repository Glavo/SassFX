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

/// Resolves and loads SCSS files from the filesystem.
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
    /// @throws IllegalStateException if multiple candidates exist
    public @Nullable ImportResult canonicalizeAndLoad(
            String url,
            @Nullable URI baseUrl
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        var candidates = new ArrayList<Path>();
        if (baseUrl != null && "file".equalsIgnoreCase(baseUrl.getScheme())) {
            var basePath = Path.of(baseUrl).getParent();
            if (basePath != null) {
                addCandidates(candidates, basePath.resolve(url).normalize());
            }
        }
        for (var loadPath : loadPaths) {
            addCandidates(candidates, loadPath.resolve(url).normalize());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            var message = new StringBuilder(
                    "It's not clear which file to import. Found:\n"
            );
            for (var candidate : candidates) {
                message.append("  ").append(candidate).append('\n');
            }
            throw new IllegalStateException(message.toString().trim());
        }
        var path = candidates.get(0);
        var content = Files.readString(path, StandardCharsets.UTF_8);
        var canonical = path.toAbsolutePath().normalize().toUri();
        return new ImportResult(
                new SourceFile(content, canonical),
                Syntax.SCSS,
                canonical
        );
    }

    /// Adds resolvable SCSS candidates for one path stem.
    private static void addCandidates(List<Path> candidates, Path path) {
        var fileName = path.getFileName();
        if (fileName == null) {
            return;
        }
        var name = fileName.toString();
        var parent = path.getParent();
        if (name.endsWith(".scss")) {
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
            return;
        }
        addIfRegular(candidates, parent.resolve(name + ".scss"));
        addIfRegular(candidates, parent.resolve("_" + name + ".scss"));
        addIfRegular(candidates, parent.resolve(name).resolve("index.scss"));
        addIfRegular(candidates, parent.resolve(name).resolve("_index.scss"));
    }

    /// Adds a path when it is an existing regular file.
    private static void addIfRegular(List<Path> candidates, Path path) {
        if (Files.isRegularFile(path) && !candidates.contains(path)) {
            candidates.add(path);
        }
    }
}
