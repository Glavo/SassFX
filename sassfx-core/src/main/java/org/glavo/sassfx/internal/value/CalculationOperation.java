// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

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
    /// The binary operator.
    private final CalculationOperator operator;

    /// The left calculation operand.
    private final Object left;

    /// The right calculation operand.
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

    /// Serializes an operand with parentheses when required by precedence or
    /// associativity.
    ///
    /// @param operand the nested operand
    /// @param leftSide whether it is the left operand
    /// @return the serialized operand
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
        // Unitful non-finite numbers serialize as {@code infinity * 1px}; that
        // product must be parenthesized as the right operand of {@code /}.
        if (!leftSide
                && operator == CalculationOperator.DIVIDED_BY
                && operand instanceof SassNumber number
                && number.isCompoundCalculationOperand()) {
            return "(" + serializeOperand(operand) + ")";
        }
        return serializeOperand(operand);
    }

    /// Serializes one supported calculation operand.
    ///
    /// @param operand the operand
    /// @return its CSS representation
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
