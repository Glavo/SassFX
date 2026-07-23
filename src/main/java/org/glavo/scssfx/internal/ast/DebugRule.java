// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Reports the inspect representation of an evaluated expression for debugging.
///
/// @param expression the expression whose value is reported
/// @param span       the source range from `@debug` through the expression
@ApiStatus.Internal
@NotNullByDefault
public record DebugRule(
        SassExpression expression,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable debug rule.
    public DebugRule {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the debug-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitDebugRule(this);
    }
}
