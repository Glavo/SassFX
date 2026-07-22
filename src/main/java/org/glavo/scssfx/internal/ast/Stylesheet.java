// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Contains the top-level statements of an unevaluated Sass stylesheet.
///
/// @param children the top-level statements in source order
/// @param span the source range covering the complete input
/// @param plainCss whether the input was parsed using plain CSS restrictions
@ApiStatus.Internal
@NotNullByDefault
public record Stylesheet(
        @Unmodifiable List<SassStatement> children,
        SourceSpan span,
        boolean plainCss
) implements SassStatement {
    /// Creates an immutable stylesheet root.
    public Stylesheet {
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Returns the source representation of the top-level statements.
    ///
    /// @return statements separated by one space
    @Override
    public String toString() {
        return String.join(" ", children.stream().map(Object::toString).toList());
    }
}
