// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Terminates evaluation with an expression-derived error message.
///
/// @param expression the expression whose inspect representation becomes the message
/// @param span       the source range from `@error` through the expression
@ApiStatus.Internal
@NotNullByDefault
public record ErrorRule(
        SassExpression expression,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable error rule.
    public ErrorRule {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the error-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitErrorRule(this);
    }
}
