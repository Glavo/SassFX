// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.SassColor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a parsed Sass color literal.
///
/// @param value the semantic color value
/// @param span the source range occupied by the complete literal
@ApiStatus.Internal
@NotNullByDefault
public record ColorExpression(SassColor value, SourceSpan span) implements SassExpression {
    /// Creates a color expression.
    public ColorExpression {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this expression to the color-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitColorExpression(this);
    }

    /// Returns the Sass representation selected by the color value.
    ///
    /// @return the source-backed or canonical color representation
    @Override
    public String toString() {
        return value.toString();
    }
}
