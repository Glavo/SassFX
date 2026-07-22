// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Iterates an integer index from one bound toward another.
///
/// @param variable  the normalized index variable name without a dollar sign
/// @param from      the inclusive starting bound expression
/// @param to        the ending bound expression
/// @param exclusive whether the ending bound uses `to` rather than `through`
/// @param children  the statements executed for each index
/// @param span      the source range from `@for` through the child block
@ApiStatus.Internal
@NotNullByDefault
public record ForRule(
        String variable,
        SassExpression from,
        SassExpression to,
        boolean exclusive,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable `@for` rule.
    ///
    /// @throws IllegalArgumentException if {@code variable} is empty
    public ForRule {
        Objects.requireNonNull(variable, "variable");
        if (variable.isEmpty()) {
            throw new IllegalArgumentException("variable must not be empty");
        }
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the for-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitForRule(this);
    }
}
