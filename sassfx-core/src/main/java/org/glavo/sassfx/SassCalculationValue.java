// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents one value nested in a CSS calculation.
///
/// Calculation values are immutable structural values. They may contain a
/// number, a nested calculation, unquoted CSS text, or a binary operation.
@NotNullByDefault
public sealed interface SassCalculationValue
        permits SassCalculationValue.Value,
        SassCalculationValue.StringValue,
        SassCalculationValue.Operation {
    /// Wraps a number or nested calculation.
    ///
    /// @param value the Sass number or calculation
    @NotNullByDefault
    record Value(SassValue value) implements SassCalculationValue {
        /// Creates a wrapped calculation value.
        ///
        /// @throws IllegalArgumentException if {@code value} is neither a
        /// number nor a calculation
        public Value {
            Objects.requireNonNull(value, "value");
            if (value.type() != SassValueType.NUMBER
                    && value.type() != SassValueType.CALCULATION) {
                throw new IllegalArgumentException(
                        "value must be a Sass number or calculation"
                );
            }
        }
    }

    /// Contains unquoted CSS text retained in a calculation.
    ///
    /// @param text the unquoted CSS text
    @NotNullByDefault
    record StringValue(String text) implements SassCalculationValue {
        /// Creates an unquoted calculation string.
        public StringValue {
            Objects.requireNonNull(text, "text");
        }
    }

    /// Contains a binary calculation operation.
    ///
    /// The operation is simplified when it is passed to
    /// [SassValue#calculation(String, java.util.List)].
    ///
    /// @param operator the binary operator
    /// @param left the left operand
    /// @param right the right operand
    @NotNullByDefault
    record Operation(
            Operator operator,
            SassCalculationValue left,
            SassCalculationValue right
    ) implements SassCalculationValue {
        /// Creates a calculation operation.
        public Operation {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    /// Identifies an operator permitted in a CSS calculation.
    @NotNullByDefault
    enum Operator {
        /// Addition.
        PLUS,

        /// Subtraction.
        MINUS,

        /// Multiplication.
        TIMES,

        /// Division.
        DIVIDED_BY
    }
}
