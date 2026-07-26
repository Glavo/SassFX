// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents the Sass literal {@code null}.
///
/// @param span the source range occupied by the literal
@ApiStatus.Internal
@NotNullByDefault
public record NullExpression(SourceSpan span) implements SassExpression {
    /// Creates a null expression.
    public NullExpression {
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this expression to the null-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitNullExpression(this);
    }

    /// Returns the Sass source representation of this literal.
    ///
    /// @return {@code null}
    @Override
    public String toString() {
        return "null";
    }
}
