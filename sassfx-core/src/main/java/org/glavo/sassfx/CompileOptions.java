// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

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
/// @param importers custom Sass importers consulted in list order before
///                  filesystem load paths
/// @param functions synchronous Java custom functions
/// @param diagnosticOptions logger and deprecation processing configuration
/// @param sourceMapIncludeSources whether generated source maps include aligned
///                                original source text
@NotNullByDefault
public record CompileOptions(
        boolean sourceMap,
        @Unmodifiable List<Path> loadPaths,
        @Nullable JavaFXStylesheetResolver javaFXStylesheetResolver,
        @Unmodifiable List<SassImporter> importers,
        @Unmodifiable List<SassCustomFunction> functions,
        SassDiagnosticOptions diagnosticOptions,
        boolean sourceMapIncludeSources
) {
    /// The default options without source maps or additional load paths.
    public static final CompileOptions DEFAULT =
            new CompileOptions(
                    false,
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    SassDiagnosticOptions.DEFAULT,
                    false
            );

    /// Creates options without a custom JavaFX stylesheet resolver.
    ///
    /// @param sourceMap whether the compiler should generate source-map data
    /// @param loadPaths additional stylesheet search directories
    public CompileOptions(
            boolean sourceMap,
            @Unmodifiable List<Path> loadPaths
    ) {
        this(
                sourceMap,
                loadPaths,
                null,
                List.of(),
                List.of(),
                SassDiagnosticOptions.DEFAULT,
                false
        );
    }

    /// Creates options without custom Sass importers.
    ///
    /// @param sourceMap whether the compiler should generate source-map data
    /// @param loadPaths additional stylesheet search directories
    /// @param javaFXStylesheetResolver the BSS retained-CSS resolver, or
    ///                                 {@code null}
    public CompileOptions(
            boolean sourceMap,
            @Unmodifiable List<Path> loadPaths,
            @Nullable JavaFXStylesheetResolver javaFXStylesheetResolver
    ) {
        this(
                sourceMap,
                loadPaths,
                javaFXStylesheetResolver,
                List.of(),
                List.of(),
                SassDiagnosticOptions.DEFAULT,
                false
        );
    }

    /// Creates options without custom functions.
    ///
    /// @param sourceMap whether the compiler should generate source-map data
    /// @param loadPaths additional stylesheet search directories
    /// @param javaFXStylesheetResolver the BSS retained-CSS resolver, or
    ///                                 {@code null}
    /// @param importers custom Sass importers in precedence order
    public CompileOptions(
            boolean sourceMap,
            @Unmodifiable List<Path> loadPaths,
            @Nullable JavaFXStylesheetResolver javaFXStylesheetResolver,
            @Unmodifiable List<SassImporter> importers
    ) {
        this(
                sourceMap,
                loadPaths,
                javaFXStylesheetResolver,
                importers,
                List.of(),
                SassDiagnosticOptions.DEFAULT,
                false
        );
    }

    /// Creates options with default diagnostic processing.
    ///
    /// @param sourceMap whether the compiler should generate source-map data
    /// @param loadPaths additional stylesheet search directories
    /// @param javaFXStylesheetResolver the BSS retained-CSS resolver, or
    ///                                 {@code null}
    /// @param importers custom Sass importers in precedence order
    /// @param functions synchronous Java custom functions
    public CompileOptions(
            boolean sourceMap,
            @Unmodifiable List<Path> loadPaths,
            @Nullable JavaFXStylesheetResolver javaFXStylesheetResolver,
            @Unmodifiable List<SassImporter> importers,
            @Unmodifiable List<SassCustomFunction> functions
    ) {
        this(
                sourceMap,
                loadPaths,
                javaFXStylesheetResolver,
                importers,
                functions,
                SassDiagnosticOptions.DEFAULT,
                false
        );
    }

    /// Creates options without embedded source contents in generated maps.
    ///
    /// @param sourceMap whether the compiler should generate source-map data
    /// @param loadPaths additional stylesheet search directories
    /// @param javaFXStylesheetResolver the BSS retained-CSS resolver, or
    ///                                 {@code null}
    /// @param importers custom Sass importers in precedence order
    /// @param functions synchronous Java custom functions
    /// @param diagnosticOptions logger and deprecation processing configuration
    public CompileOptions(
            boolean sourceMap,
            @Unmodifiable List<Path> loadPaths,
            @Nullable JavaFXStylesheetResolver javaFXStylesheetResolver,
            @Unmodifiable List<SassImporter> importers,
            @Unmodifiable List<SassCustomFunction> functions,
            SassDiagnosticOptions diagnosticOptions
    ) {
        this(
                sourceMap,
                loadPaths,
                javaFXStylesheetResolver,
                importers,
                functions,
                diagnosticOptions,
                false
        );
    }

    /// Creates compile options with immutable snapshots of ordered collections.
    public CompileOptions {
        Objects.requireNonNull(loadPaths, "loadPaths");
        Objects.requireNonNull(importers, "importers");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(diagnosticOptions, "diagnosticOptions");
        loadPaths = List.copyOf(loadPaths);
        importers = List.copyOf(importers);
        functions = List.copyOf(functions);
    }
}
