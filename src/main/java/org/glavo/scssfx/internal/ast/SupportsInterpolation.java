// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a condition supplied by one SassScript interpolation.
///
/// @param expression the expression producing the complete condition text
/// @param span the source range covering the interpolation
@ApiStatus.Internal
@NotNullByDefault
public record SupportsInterpolation(
        SassExpression expression,
        SourceSpan span
) implements SupportsCondition {
    /// Creates an interpolated support condition.
    public SupportsInterpolation {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }

    /// Returns a source-like representation of the interpolation.
    ///
    /// @return the interpolation expression
    @Override
    public String toString() {
        return "#{" + expression + "}";
    }
}
