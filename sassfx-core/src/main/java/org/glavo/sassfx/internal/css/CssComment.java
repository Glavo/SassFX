// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A plain-CSS multi-line comment.
@ApiStatus.Internal
@NotNullByDefault
public final class CssComment extends AbstractCssNode implements CssNode {
    /// Contains the complete comment text, including delimiters.
    private final String text;

    /// Creates a CSS comment.
    ///
    /// @param text the complete comment text, including `/*` and `*/`
    /// @param span the source range of the originating comment
    public CssComment(String text, SourceSpan span) {
        super(span);
        this.text = Objects.requireNonNull(text, "text");
    }

    /// Returns the complete comment text.
    ///
    /// @return the comment including delimiters
    public String text() {
        return text;
    }

    /// Returns whether compressed output must preserve this comment.
    ///
    /// Preserved comments begin with `/*!`.
    ///
    /// @return whether the comment is preserved
    public boolean isPreserved() {
        return text.length() >= 3 && text.charAt(2) == '!';
    }

    /// Returns false because expanded output keeps loud comments.
    ///
    /// @return {@code false}
    @Override
    public boolean isInvisible() {
        return false;
    }
}
