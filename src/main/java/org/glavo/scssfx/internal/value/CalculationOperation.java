// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A binary operation retained inside a [SassCalculation] that could not fully simplify.
///
/// Operands are calculation arguments: [SassNumber], [SassCalculation], unquoted
/// [SassString], or nested [CalculationOperation] instances.
@ApiStatus.Internal
@NotNullByDefault
public final class CalculationOperation {
    private final CalculationOperator operator;
    private final Object left;
    private final Object right;

    /// Creates an operation.
    ///
    /// @param operator the operator
    /// @param left     the left operand
    /// @param right    the right operand
    public CalculationOperation(CalculationOperator operator, Object left, Object right) {
        this.operator = Objects.requireNonNull(operator, "operator");
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    /// Returns the operator.
    ///
    /// @return the operator
    public CalculationOperator operator() {
        return operator;
    }

    /// Returns the left operand.
    ///
    /// @return the left operand
    public Object left() {
        return left;
    }

    /// Returns the right operand.
    ///
    /// @return the right operand
    public Object right() {
        return right;
    }

    /// Serializes this operation for CSS emission.
    ///
    /// @return the CSS text
    public String toCssString() {
        return parenthesize(left, true) + " " + operator.operator() + " " + parenthesize(right, false);
    }

    @Override
    public String toString() {
        return toCssString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CalculationOperation operation
                && operator == operation.operator
                && left.equals(operation.left)
                && right.equals(operation.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, left, right);
    }

    private String parenthesize(Object operand, boolean leftSide) {
        if (operand instanceof CalculationOperation nested
                && nested.operator.precedence() < operator.precedence()) {
            return "(" + nested.toCssString() + ")";
        }
        if (operand instanceof CalculationOperation nested
                && !leftSide
                && nested.operator.precedence() == operator.precedence()
                && (operator == CalculationOperator.MINUS || operator == CalculationOperator.DIVIDED_BY)) {
            return "(" + nested.toCssString() + ")";
        }
        return serializeOperand(operand);
    }

    static String serializeOperand(Object operand) {
        if (operand instanceof SassNumber number) {
            // Nested non-finite numbers keep bare CSS keywords so outer calc()
            // does not emit calc(calc(infinity)).
            return number.toCalculationCssString();
        }
        if (operand instanceof SassCalculation calculation) {
            return calculation.toCssString();
        }
        if (operand instanceof SassString string) {
            return string.toCssString();
        }
        if (operand instanceof CalculationOperation operation) {
            return operation.toCssString();
        }
        return String.valueOf(operand);
    }
}
