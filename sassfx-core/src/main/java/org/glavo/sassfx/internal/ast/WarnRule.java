// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Reports an evaluated expression as a non-fatal warning.
///
/// @param expression the expression whose value is reported
/// @param span       the source range from `@warn` through the expression
@ApiStatus.Internal
@NotNullByDefault
public record WarnRule(
        SassExpression expression,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable warning rule.
    public WarnRule {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the warning-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitWarnRule(this);
    }
}
