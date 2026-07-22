// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents an expression whose source is enclosed in parentheses.
///
/// @param expression the expression between the parentheses
/// @param span       the source range including both parentheses
@ApiStatus.Internal
@NotNullByDefault
public record ParenthesizedExpression(SassExpression expression, SourceSpan span)
        implements SassExpression {
    /// Creates a parenthesized expression.
    public ParenthesizedExpression {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this expression to the parenthesized-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitParenthesizedExpression(this);
    }

    /// Returns a Sass source representation of this expression.
    ///
    /// @return the inner expression surrounded by parentheses
    @Override
    public String toString() {
        return "(" + expression + ")";
    }
}
