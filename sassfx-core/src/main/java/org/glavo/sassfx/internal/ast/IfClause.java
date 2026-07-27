// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// One conditional branch of an `@if` or `@else if` clause.
///
/// @param expression the condition that selects this branch
/// @param children   the statements executed when the condition is truthy
@ApiStatus.Internal
@NotNullByDefault
public record IfClause(
        SassExpression expression,
        @Unmodifiable List<SassStatement> children
) {
    /// Creates an immutable conditional clause.
    public IfClause {
        Objects.requireNonNull(expression, "expression");
        children = List.copyOf(children);
    }
}
