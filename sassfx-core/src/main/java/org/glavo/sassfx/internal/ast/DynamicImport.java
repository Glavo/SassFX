// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Loads and executes another Sass stylesheet in the caller's legacy import scope.
///
/// An empty {@code url} is valid in the indented syntax ({@code @import} with no
/// path) and reloads the current file, matching dart-sass.
///
/// @param url  the decoded unresolved stylesheet URL; may be empty
/// @param span the source range occupied by the quoted URL
@ApiStatus.Internal
@NotNullByDefault
public record DynamicImport(String url, SourceSpan span) implements SassImport {
    /// Creates a dynamic Sass import.
    public DynamicImport {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(span, "span");
    }
}
