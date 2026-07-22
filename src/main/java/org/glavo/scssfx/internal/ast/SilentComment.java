// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
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
