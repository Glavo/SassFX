// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Contains one expression interpolation part and its surrounding syntax range.
///
/// @param expression the interpolated SassScript expression
/// @param interpolationSpan the range from the opening hash through the closing brace
@ApiStatus.Internal
@NotNullByDefault
public record ExpressionInterpolationPart(
        SassExpression expression,
        SourceSpan interpolationSpan
) implements InterpolationPart {
    /// Creates an expression interpolation part.
    public ExpressionInterpolationPart {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(interpolationSpan, "interpolationSpan");
    }

    /// Returns the Sass source representation of this interpolation part.
    ///
    /// @return the expression surrounded by interpolation delimiters
    @Override
    public String toString() {
        return "#{" + expression + "}";
    }
}
