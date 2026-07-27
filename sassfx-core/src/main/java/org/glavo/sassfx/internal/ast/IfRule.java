// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Conditionally executes one of several statement branches.
///
/// @param clauses    the `@if` and `@else if` branches in source order
/// @param lastClause the optional unconditional `@else` branch
/// @param span       the source range from `@if` through the final branch
@ApiStatus.Internal
@NotNullByDefault
public record IfRule(
        @Unmodifiable List<IfClause> clauses,
        @Nullable ElseClause lastClause,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable `@if` rule.
    ///
    /// @throws IllegalArgumentException if {@code clauses} is empty
    public IfRule {
        clauses = List.copyOf(clauses);
        if (clauses.isEmpty()) {
            throw new IllegalArgumentException("clauses must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the if-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitIfRule(this);
    }
}
