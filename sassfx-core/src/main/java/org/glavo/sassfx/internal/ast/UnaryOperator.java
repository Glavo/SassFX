// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies an operator applied to one SassScript expression.
@ApiStatus.Internal
@NotNullByDefault
public enum UnaryOperator {
    /// Identifies the numeric identity operator.
    PLUS("plus", "+"),

    /// Identifies the numeric negation operator.
    MINUS("minus", "-"),

    /// Identifies the historical leading slash operator.
    DIVIDE("divide", "/"),

    /// Identifies the boolean negation operator.
    NOT("not", "not");

    /// Contains the human-readable operator name used by diagnostics.
    private final String displayName;

    /// Contains the Sass source spelling of this operator.
    private final String source;

    /// Creates a unary operator descriptor.
    ///
    /// @param displayName the human-readable operator name
    /// @param source      the Sass source spelling
    UnaryOperator(String displayName, String source) {
        this.displayName = displayName;
        this.source = source;
    }

    /// Returns the Sass source spelling of this operator.
    ///
    /// @return the operator source spelling
    public String source() {
        return source;
    }

    /// Returns the human-readable operator name.
    ///
    /// @return the operator name
    @Override
    public String toString() {
        return displayName;
    }
}
