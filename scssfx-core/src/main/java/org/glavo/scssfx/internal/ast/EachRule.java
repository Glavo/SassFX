// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Iterates over the list view of an expression.
///
/// @param variables the normalized loop variable names without dollar signs
/// @param list      the expression whose list view is iterated
/// @param children  the statements executed for each element
/// @param span      the source range from `@each` through the child block
@ApiStatus.Internal
@NotNullByDefault
public record EachRule(
        @Unmodifiable List<String> variables,
        SassExpression list,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable `@each` rule.
    ///
    /// @throws IllegalArgumentException if {@code variables} is empty or contains
    /// an empty name
    public EachRule {
        variables = List.copyOf(variables);
        if (variables.isEmpty()) {
            throw new IllegalArgumentException("variables must not be empty");
        }
        for (var name : variables) {
            Objects.requireNonNull(name, "variable");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("variable must not be empty");
            }
        }
        Objects.requireNonNull(list, "list");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the each-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitEachRule(this);
    }
}
