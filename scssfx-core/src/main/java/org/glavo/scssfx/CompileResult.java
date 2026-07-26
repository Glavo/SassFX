// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Contains compiler output and metadata collected during compilation.
///
/// @param output the generated target representation
/// @param sourceMap the generated source map, or {@code null} when source maps were disabled
/// @param loadedUrls the canonical URLs of all stylesheets loaded during compilation
/// @param diagnostics non-error diagnostics in reporting order
/// @param <T> the output representation type
@NotNullByDefault
public record CompileResult<T>(
        T output,
        @Nullable SourceMap sourceMap,
        @Unmodifiable Set<URI> loadedUrls,
        @Unmodifiable List<Diagnostic> diagnostics
) {
    /// Creates a result with immutable snapshots of its metadata collections.
    public CompileResult {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(loadedUrls, "loadedUrls");
        Objects.requireNonNull(diagnostics, "diagnostics");
        loadedUrls = Set.copyOf(loadedUrls);
        diagnostics = List.copyOf(diagnostics);
    }
}
