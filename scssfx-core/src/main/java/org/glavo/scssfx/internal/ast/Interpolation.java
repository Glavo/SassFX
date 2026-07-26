// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Contains plain text interleaved with SassScript expressions.
///
/// Empty part lists are permitted. Two text parts may not be adjacent because
/// they must be represented by one combined part.
///
/// @param parts the interpolation parts in source order
/// @param span the source range covering the complete interpolation
@ApiStatus.Internal
@NotNullByDefault
public record Interpolation(
        @Unmodifiable List<InterpolationPart> parts,
        SourceSpan span
) implements SassNode {
    /// Creates an immutable interpolation.
    ///
    /// @throws IllegalArgumentException if two text parts are adjacent
    public Interpolation {
        parts = List.copyOf(parts);
        Objects.requireNonNull(span, "span");
        for (var index = 1; index < parts.size(); index++) {
            if (parts.get(index - 1) instanceof TextInterpolationPart
                    && parts.get(index) instanceof TextInterpolationPart) {
                throw new IllegalArgumentException(
                        "adjacent text interpolation parts must be combined"
                );
            }
        }
    }

    /// Creates an interpolation containing one plain-text part.
    ///
    /// @param text the plain text, which may be empty
    /// @param span the source range covering the text
    /// @return the plain interpolation
    public static Interpolation plain(String text, SourceSpan span) {
        return new Interpolation(List.of(new TextInterpolationPart(text)), span);
    }

    /// Returns whether this interpolation contains no expression parts.
    ///
    /// @return whether [#asPlain()] returns a value
    public boolean isPlain() {
        return asPlain() != null;
    }

    /// Returns the complete text when this interpolation has no expressions.
    ///
    /// @return the plain text, or {@code null} when an expression is present
    public @Nullable String asPlain() {
        if (parts.isEmpty()) {
            return "";
        }
        return parts.size() == 1 && parts.get(0) instanceof TextInterpolationPart text
                ? text.text()
                : null;
    }

    /// Returns the plain text before the first expression.
    ///
    /// @return the initial plain text, or the empty string when none is present
    public String initialPlain() {
        return !parts.isEmpty() && parts.get(0) instanceof TextInterpolationPart text
                ? text.text()
                : "";
    }

    /// Returns the Sass source representation of this interpolation.
    ///
    /// @return concatenated text and interpolation expressions
    @Override
    public String toString() {
        var result = new StringBuilder();
        for (var part : parts) {
            result.append(part);
        }
        return result.toString();
    }
}
