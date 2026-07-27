// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents the parent-selector expression {@code &} in SassScript.
///
/// Outside plain CSS, {@code &} evaluates to the current style rule's selector
/// as a nested Sass list. Plain CSS rejects this form at parse time.
///
/// @param span the source range occupied by the {@code &} token
@ApiStatus.Internal
@NotNullByDefault
public record SelectorExpression(SourceSpan span) implements SassExpression {
    /// Creates a parent-selector expression.
    public SelectorExpression {
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this expression to the selector-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitSelectorExpression(this);
    }

    /// Returns the Sass source representation of this expression.
    ///
    /// @return {@code &}
    @Override
    public String toString() {
        return "&";
    }
}
