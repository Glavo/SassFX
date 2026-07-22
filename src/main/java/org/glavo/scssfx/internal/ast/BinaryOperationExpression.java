// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a binary SassScript operation.
///
/// @param operator     the invoked operator
/// @param left         the left operand
/// @param right        the right operand
/// @param allowsSlash  whether a division may evaluate as slash-separated numbers
/// @param operatorSpan the source range containing only the operator
/// @param span         the source range from the leftmost to rightmost operand
@ApiStatus.Internal
@NotNullByDefault
public record BinaryOperationExpression(
        BinaryOperator operator,
        SassExpression left,
        SassExpression right,
        boolean allowsSlash,
        SourceSpan operatorSpan,
        SourceSpan span
) implements SassExpression {
    /// Creates a binary operation expression.
    ///
    /// @throws IllegalArgumentException if slash metadata is used for a
    /// non-division operator or the supplied ranges do not describe the
    /// operands and operator in source order
    public BinaryOperationExpression {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(operatorSpan, "operatorSpan");
        Objects.requireNonNull(span, "span");
        if (allowsSlash && operator != BinaryOperator.DIVIDED_BY) {
            throw new IllegalArgumentException(
                    "allowsSlash is valid only for the division operator"
            );
        }
        if (!Objects.equals(span.url(), left.span().url())
                || !Objects.equals(span.url(), right.span().url())
                || !Objects.equals(span.url(), operatorSpan.url())
                || span.start().offset() != left.span().start().offset()
                || span.end().offset() != right.span().end().offset()
                || operatorSpan.start().offset() < left.span().end().offset()
                || operatorSpan.end().offset() > right.span().start().offset()
                || !operatorTextMatches(operator, operatorSpan.text())) {
            throw new IllegalArgumentException(
                    "binary expression spans must describe operands and operator in source order"
            );
        }
    }

    /// Returns whether source text represents the selected operator.
    ///
    /// Sass accepts ASCII case variations after the lowercase first code unit
    /// of its word operators. Symbol operators are case-insensitive by nature.
    ///
    /// @param operator the selected operator
    /// @param text the exact operator source text
    /// @return whether the source text denotes the operator
    private static boolean operatorTextMatches(BinaryOperator operator, String text) {
        return operator == BinaryOperator.AND || operator == BinaryOperator.OR
                ? operator.source().equalsIgnoreCase(text)
                : operator.source().equals(text);
    }

    /// Returns a Sass source representation of this operation.
    ///
    /// @return the operands and operator, with required grouping parentheses
    @Override
    public String toString() {
        var leftSource = requiresLeftParentheses(left) ? "(" + left + ")" : left.toString();
        var rightSource = requiresRightParentheses(right) ? "(" + right + ")" : right.toString();
        return leftSource + " " + operator.source() + " " + rightSource;
    }

    /// Returns whether the left operand requires grouping parentheses.
    ///
    /// @param expression the left operand
    /// @return whether parentheses are required
    private boolean requiresLeftParentheses(SassExpression expression) {
        if (ListExpression.isUnbracketedMultiElement(expression)) {
            return true;
        }
        return expression instanceof BinaryOperationExpression binary
                && binary.operator.precedence() < operator.precedence();
    }

    /// Returns whether the right operand requires grouping parentheses.
    ///
    /// @param expression the right operand
    /// @return whether parentheses are required
    private boolean requiresRightParentheses(SassExpression expression) {
        if (ListExpression.isUnbracketedMultiElement(expression)) {
            return true;
        }
        if (!(expression instanceof BinaryOperationExpression binary)) {
            return false;
        }
        return binary.operator.precedence() <= operator.precedence()
                && (binary.operator != operator || !operator.isAssociative());
    }
}
