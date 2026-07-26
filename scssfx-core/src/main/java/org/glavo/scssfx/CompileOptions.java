// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Configures behavior shared by all compilation targets.
///
/// @param sourceMap whether the compiler should generate source-map data
/// @param loadPaths additional directories searched by the Sass importer and,
///                  using exact filenames, by BSS retained-CSS import resolution
/// @param javaFXStylesheetResolver the resolver consulted before default file
///                                 lookup for BSS retained-CSS imports, or {@code null}
@NotNullByDefault
public record CompileOptions(
        boolean sourceMap,
        @Unmodifiable List<Path> loadPaths,
        @Nullable JavaFXStylesheetResolver javaFXStylesheetResolver
) {
    /// The default options without source maps or additional load paths.
    public static final CompileOptions DEFAULT =
            new CompileOptions(false, List.of(), null);

    /// Creates options without a custom JavaFX stylesheet resolver.
    ///
    /// @param sourceMap whether the compiler should generate source-map data
    /// @param loadPaths additional stylesheet search directories
    public CompileOptions(
            boolean sourceMap,
            @Unmodifiable List<Path> loadPaths
    ) {
        this(sourceMap, loadPaths, null);
    }

    /// Creates compile options with an immutable snapshot of the load paths.
    public CompileOptions {
        Objects.requireNonNull(loadPaths, "loadPaths");
        loadPaths = List.copyOf(loadPaths);
    }
}
