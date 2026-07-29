// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies an operator applied to two SassScript expressions.
@ApiStatus.Internal
@NotNullByDefault
public enum BinaryOperator {
    /// Identifies the Microsoft-style equality operator.
    SINGLE_EQUALS("single equals", "=", 0, false),

    /// Identifies boolean disjunction.
    OR("or", "or", 1, true),

    /// Identifies boolean conjunction.
    AND("and", "and", 2, true),

    /// Identifies equality comparison.
    EQUALS("equals", "==", 3, false),

    /// Identifies inequality comparison.
    NOT_EQUALS("not equals", "!=", 3, false),

    /// Identifies greater-than comparison.
    GREATER_THAN("greater than", ">", 4, false),

    /// Identifies greater-than-or-equal comparison.
    GREATER_THAN_OR_EQUALS("greater than or equals", ">=", 4, false),

    /// Identifies less-than comparison.
    LESS_THAN("less than", "<", 4, false),

    /// Identifies less-than-or-equal comparison.
    LESS_THAN_OR_EQUALS("less than or equals", "<=", 4, false),

    /// Identifies numeric addition.
    PLUS("plus", "+", 5, true),

    /// Identifies numeric subtraction.
    MINUS("minus", "-", 5, false),

    /// Identifies numeric multiplication.
    TIMES("times", "*", 6, true),

    /// Identifies numeric division.
    DIVIDED_BY("divided by", "/", 6, false),

    /// Identifies numeric modulo.
    MODULO("modulo", "%", 6, false);

    /// Contains the human-readable operator name used by diagnostics.
    private final String displayName;

    /// Contains the Sass source spelling of this operator.
    private final String source;

    /// Contains the binding precedence, where higher values bind more tightly.
    private final int precedence;

    /// Records whether repeated uses of this operator may omit grouping parentheses.
    private final boolean associative;

    /// Creates a binary operator descriptor.
    ///
    /// @param displayName the human-readable operator name
    /// @param source      the Sass source spelling
    /// @param precedence  the binding precedence
    /// @param associative whether repeated uses are associative for serialization
    BinaryOperator(String displayName, String source, int precedence, boolean associative) {
        this.displayName = displayName;
        this.source = source;
        this.precedence = precedence;
        this.associative = associative;
    }

    /// Returns the Sass source spelling of this operator.
    ///
    /// @return the operator source spelling
    public String source() {
        return source;
    }

    /// Returns the binding precedence of this operator.
    ///
    /// @return the binding precedence, where higher values bind more tightly
    public int precedence() {
        return precedence;
    }

    /// Returns whether repeated uses of this operator are associative.
    ///
    /// @return whether repeated uses may omit grouping parentheses
    public boolean isAssociative() {
        return associative;
    }

    /// Returns the human-readable operator name.
    ///
    /// @return the operator name
    @Override
    public String toString() {
        return displayName;
    }
}
