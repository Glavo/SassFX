// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Configures behavior shared by all compilation targets.
///
/// [#DEFAULT] provides the standard policy. The `with` methods derive a new
/// immutable option value while retaining every setting that is not changed.
///
/// @param sourceMap whether the compiler should generate source-map data
/// @param loadPaths additional directories searched by Sass import resolution
///                  and BSS retained-stylesheet fallback lookup
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
                    List.of(),
                    List.of(),
                    SassDiagnosticOptions.DEFAULT,
                    false
            );

    /// Creates compile options with immutable snapshots of ordered collections.
    ///
    /// @throws NullPointerException if a collection, one of its elements, or
    ///                               `diagnosticOptions` is `null`
    public CompileOptions {
        Objects.requireNonNull(loadPaths, "loadPaths");
        Objects.requireNonNull(importers, "importers");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(diagnosticOptions, "diagnosticOptions");
        loadPaths = List.copyOf(loadPaths);
        importers = List.copyOf(importers);
        functions = List.copyOf(functions);
    }

    /// Returns options with source-map generation enabled or disabled.
    ///
    /// @param sourceMap whether source-map data is generated
    /// @return the derived options
    public CompileOptions withSourceMap(boolean sourceMap) {
        return new CompileOptions(
                sourceMap,
                loadPaths,
                importers,
                functions,
                diagnosticOptions,
                sourceMapIncludeSources
        );
    }

    /// Returns options with a snapshot of the supplied load paths.
    ///
    /// @param loadPaths additional stylesheet search directories
    /// @return the derived options
    /// @throws NullPointerException if the list or one of its elements is `null`
    public CompileOptions withLoadPaths(
            @Unmodifiable List<? extends Path> loadPaths
    ) {
        return new CompileOptions(
                sourceMap,
                List.copyOf(loadPaths),
                importers,
                functions,
                diagnosticOptions,
                sourceMapIncludeSources
        );
    }

    /// Returns options with a snapshot of the supplied importers.
    ///
    /// @param importers custom importers in precedence order
    /// @return the derived options
    /// @throws NullPointerException if the list or one of its elements is `null`
    public CompileOptions withImporters(
            @Unmodifiable List<? extends SassImporter> importers
    ) {
        return new CompileOptions(
                sourceMap,
                loadPaths,
                List.copyOf(importers),
                functions,
                diagnosticOptions,
                sourceMapIncludeSources
        );
    }

    /// Returns options with a snapshot of the supplied custom functions.
    ///
    /// @param functions synchronous Java custom functions
    /// @return the derived options
    /// @throws NullPointerException if the list or one of its elements is `null`
    public CompileOptions withFunctions(
            @Unmodifiable List<? extends SassCustomFunction> functions
    ) {
        return new CompileOptions(
                sourceMap,
                loadPaths,
                importers,
                List.copyOf(functions),
                diagnosticOptions,
                sourceMapIncludeSources
        );
    }

    /// Returns options with the supplied diagnostic policy.
    ///
    /// @param diagnosticOptions logger and deprecation processing configuration
    /// @return the derived options
    /// @throws NullPointerException if `diagnosticOptions` is `null`
    public CompileOptions withDiagnosticOptions(
            SassDiagnosticOptions diagnosticOptions
    ) {
        return new CompileOptions(
                sourceMap,
                loadPaths,
                importers,
                functions,
                diagnosticOptions,
                sourceMapIncludeSources
        );
    }

    /// Returns options that include or omit source contents in generated maps.
    ///
    /// This setting has no effect when [#sourceMap()] is false.
    ///
    /// @param sourceMapIncludeSources whether source contents are embedded
    /// @return the derived options
    public CompileOptions withSourceMapIncludeSources(
            boolean sourceMapIncludeSources
    ) {
        return new CompileOptions(
                sourceMap,
                loadPaths,
                importers,
                functions,
                diagnosticOptions,
                sourceMapIncludeSources
        );
    }
}
