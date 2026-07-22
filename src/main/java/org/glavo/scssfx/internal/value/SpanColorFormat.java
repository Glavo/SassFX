// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Preserves the original source spelling of an RGB color.
///
/// @param span the source range containing the complete color literal
@ApiStatus.Internal
@NotNullByDefault
public record SpanColorFormat(SourceSpan span) implements ColorFormat {
    /// Creates a source-backed color format.
    public SpanColorFormat {
        Objects.requireNonNull(span, "span");
    }

    /// Returns the original color spelling.
    ///
    /// @return the exact text covered by [#span()]
    public String original() {
        return span.text();
    }
}
