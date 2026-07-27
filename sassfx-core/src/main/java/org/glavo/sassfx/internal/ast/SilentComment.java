// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Contains one Sass-style silent comment block.
///
/// @param text the exact consumed source text, including comment markers
/// @param span the source range covering the comment block
@ApiStatus.Internal
@NotNullByDefault
public record SilentComment(String text, SourceSpan span) implements SassStatement {
    /// Creates a silent comment node.
    public SilentComment {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the silent-comment visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitSilentComment(this);
    }

    /// Returns the documentation-comment lines contained in this block.
    ///
    /// Leading comment markers and at most one following space are removed
    /// from lines whose trimmed text begins with three slashes.
    ///
    /// @return the joined documentation text, or {@code null} when absent
    public @Nullable String documentation() {
        var result = new StringBuilder();
        for (var line : text.split("\\n", -1)) {
            var trimmed = line.strip();
            if (!trimmed.startsWith("///")) {
                continue;
            }
            var contents = trimmed.substring(3);
            if (contents.startsWith(" ")) {
                contents = contents.substring(1);
            }
            result.append(contents).append('\n');
        }
        var documentation = result.toString().stripTrailing();
        return documentation.isEmpty() ? null : documentation;
    }

    /// Returns the exact consumed source text.
    ///
    /// @return the silent comment text
    @Override
    public String toString() {
        return text;
    }
}
