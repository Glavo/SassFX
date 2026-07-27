// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Declares one Sass {@code @supports} rule.
///
/// The condition retains parsed declaration, function, boolean, and
/// interpolation structure until evaluation. Its body retains the enclosing
/// statement context, allowing nested style and conditional rules to be
/// resolved before CSS serialization.
///
/// @param condition the parsed supports condition
/// @param children  the statements in the supports block
/// @param span      the source range from {@code @supports} through its closing brace
@ApiStatus.Internal
@NotNullByDefault
public record SupportsRule(
        SupportsCondition condition,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable supports rule.
    public SupportsRule {
        Objects.requireNonNull(condition, "condition");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the supports-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitSupportsRule(this);
    }

    /// Returns a Sass source representation of this rule.
    ///
    /// @return the {@code @supports} rule and its child statements
    @Override
    public String toString() {
        return "@supports " + condition + " {" + String.join(
                " ",
                children.stream().map(Object::toString).toList()
        ) + "}";
    }
}
