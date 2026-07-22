// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Returns a value from a user-defined function.
///
/// @param expression the returned expression
/// @param span       the complete return-rule span
@ApiStatus.Internal
@NotNullByDefault
public record ReturnRule(
        SassExpression expression,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable return rule.
    public ReturnRule {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the return-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitReturnRule(this);
    }
}
