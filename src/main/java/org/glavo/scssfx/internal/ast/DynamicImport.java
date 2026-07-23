// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Loads and executes another Sass stylesheet in the caller's legacy import scope.
///
/// @param url  the decoded unresolved stylesheet URL
/// @param span the source range occupied by the quoted URL
@ApiStatus.Internal
@NotNullByDefault
public record DynamicImport(String url, SourceSpan span) implements SassImport {
    /// Creates a dynamic Sass import.
    ///
    /// @throws IllegalArgumentException if {@code url} is empty
    public DynamicImport {
        Objects.requireNonNull(url, "url");
        if (url.isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }
}
