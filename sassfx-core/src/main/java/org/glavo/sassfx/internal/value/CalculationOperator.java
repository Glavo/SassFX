// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Operators permitted inside CSS calculations.
@ApiStatus.Internal
@NotNullByDefault
public enum CalculationOperator {
    /// Addition.
    PLUS("+", 1),
    /// Subtraction.
    MINUS("-", 1),
    /// Multiplication.
    TIMES("*", 2),
    /// Division.
    DIVIDED_BY("/", 2);

    private final String operator;
    private final int precedence;

    CalculationOperator(String operator, int precedence) {
        this.operator = operator;
        this.precedence = precedence;
    }

    /// Returns the CSS operator text.
    ///
    /// @return the operator glyph
    public String operator() {
        return operator;
    }

    /// Returns the operator precedence used when parenthesizing nested ops.
    ///
    /// @return higher values bind more tightly
    public int precedence() {
        return precedence;
    }
}
