// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Represents a reference to a Sass variable.
///
/// @param namespace the optional module namespace
/// @param name      the normalized variable name without its dollar sign
/// @param span      the source range occupied by the complete reference
@ApiStatus.Internal
@NotNullByDefault
public record VariableExpression(@Nullable String namespace, String name, SourceSpan span)
        implements SassExpression {
    /// Creates a variable reference.
    ///
    /// @throws IllegalArgumentException if a name or namespace is empty
    public VariableExpression {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
        if (namespace != null && namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
    }

    /// Dispatches this expression to the variable-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitVariableExpression(this);
    }

    /// Returns the original Sass source representation of this reference.
    ///
    /// @return the source text that produced this reference
    @Override
    public String toString() {
        return span.text();
    }
}
