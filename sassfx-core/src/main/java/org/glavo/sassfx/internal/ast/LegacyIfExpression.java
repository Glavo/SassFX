// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents the special short-circuiting `if()` expression.
///
/// Unlike ordinary function invocations, only the selected branch is evaluated.
///
/// @param arguments the invocation arguments
/// @param span      the complete expression span
@ApiStatus.Internal
@NotNullByDefault
public record LegacyIfExpression(
        ArgumentList arguments,
        SourceSpan span
) implements SassExpression {
    /// Creates a legacy `if()` expression.
    public LegacyIfExpression {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this expression to the legacy-if visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitLegacyIfExpression(this);
    }

    /// Returns the Sass source representation of this expression.
    ///
    /// @return the `if` invocation text
    @Override
    public String toString() {
        return "if" + arguments;
    }
}
