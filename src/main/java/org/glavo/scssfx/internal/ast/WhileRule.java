// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Repeatedly executes children while a condition remains truthy.
///
/// @param condition the condition evaluated before each iteration
/// @param children  the statements executed for each iteration
/// @param span      the source range from `@while` through the child block
@ApiStatus.Internal
@NotNullByDefault
public record WhileRule(
        SassExpression condition,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable `@while` rule.
    public WhileRule {
        Objects.requireNonNull(condition, "condition");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the while-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitWhileRule(this);
    }
}