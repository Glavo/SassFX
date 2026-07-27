// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;

/// Represents a numeric literal with an optional single source unit.
///
/// @param value the numeric literal value
/// @param unit  the literal unit, or {@code null} for a unitless number
/// @param span  the source range occupied by the literal
@ApiStatus.Internal
@NotNullByDefault
public record NumberExpression(double value, @Nullable String unit, SourceSpan span)
        implements SassExpression {
    /// Creates a number expression.
    ///
    /// @throws IllegalArgumentException if {@code unit} is empty
    public NumberExpression {
        if (unit != null && unit.isEmpty()) {
            throw new IllegalArgumentException("unit must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this expression to the number-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitNumberExpression(this);
    }

    /// Returns a Sass source representation of this numeric literal.
    ///
    /// @return the number followed by its literal unit when present
    @Override
    public String toString() {
        var number = Double.isFinite(value)
                ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
                : Double.toString(value);
        return unit == null ? number : number + unit;
    }
}
