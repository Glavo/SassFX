// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a unary SassScript operation.
///
/// @param operator the invoked operator
/// @param operand  the operator operand
/// @param span     the source range from the operator through the operand
@ApiStatus.Internal
@NotNullByDefault
public record UnaryOperationExpression(
        UnaryOperator operator,
        SassExpression operand,
        SourceSpan span
) implements SassExpression {
    /// Creates a unary operation expression.
    public UnaryOperationExpression {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
        Objects.requireNonNull(span, "span");
    }

    /// Returns a Sass source representation of this operation.
    ///
    /// @return the operator and operand, with required grouping parentheses
    @Override
    public String toString() {
        var needsParentheses = operand instanceof BinaryOperationExpression
                || operand instanceof UnaryOperationExpression
                || ListExpression.isUnbracketedMultiElement(operand);
        var separator = operator == UnaryOperator.NOT ? " " : "";
        return operator.source() + separator
                + (needsParentheses ? "(" + operand + ")" : operand.toString());
    }
}
