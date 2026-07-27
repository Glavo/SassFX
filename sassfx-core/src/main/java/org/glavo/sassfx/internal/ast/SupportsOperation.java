// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a boolean operation between two support conditions.
///
/// @param left the left-hand condition
/// @param right the right-hand condition
/// @param operator the boolean operator
/// @param span the source range covering the operation
@ApiStatus.Internal
@NotNullByDefault
public record SupportsOperation(
        SupportsCondition left,
        SupportsCondition right,
        SupportsBooleanOperator operator,
        SourceSpan span
) implements SupportsCondition {
    /// Creates a boolean support operation.
    public SupportsOperation {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(span, "span");
    }

    /// Returns a source-like representation of the operation.
    ///
    /// @return the operation with its boolean operator
    @Override
    public String toString() {
        return left + " " + operator.cssText() + " " + right;
    }
}
