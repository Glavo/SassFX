// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Contains one CSS-style loud comment.
///
/// @param text the comment text, including delimiters and any interpolations
@ApiStatus.Internal
@NotNullByDefault
public record LoudComment(Interpolation text) implements SassStatement {
    /// Creates a loud comment node.
    public LoudComment {
        Objects.requireNonNull(text, "text");
    }

    /// Returns the source range covering the complete comment.
    ///
    /// @return the interpolation source range
    @Override
    public SourceSpan span() {
        return text.span();
    }

    /// Returns the normalized comment text.
    ///
    /// @return the comment interpolation representation
    @Override
    public String toString() {
        return text.toString();
    }
}
