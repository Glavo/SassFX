// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Invokes a non-interpolated Sass or plain CSS function.
///
/// @param namespace the module namespace, or {@code null} for an unqualified invocation
/// @param originalName the decoded function name with underscores retained
/// @param arguments the arguments supplied to the function
/// @param span the source range covering the complete invocation
@ApiStatus.Internal
@NotNullByDefault
public record FunctionExpression(
        @Nullable String namespace,
        String originalName,
        ArgumentList arguments,
        SourceSpan span
) implements SassExpression {
    /// Creates a function invocation.
    ///
    /// @throws IllegalArgumentException if the namespace or name is empty
    public FunctionExpression {
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(span, "span");
        if (namespace != null && namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        if (originalName.isEmpty()) {
            throw new IllegalArgumentException("originalName must not be empty");
        }
    }

    /// Dispatches this expression to the function-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitFunctionExpression(this);
    }

    /// Returns the function name used for Sass lookup.
    ///
    /// @return the name with underscores normalized to hyphens
    public String name() {
        return originalName.replace('_', '-');
    }

    /// Returns a normalized Sass source representation of this invocation.
    ///
    /// @return the optional namespace, original name, and arguments
    @Override
    public String toString() {
        return (namespace == null ? "" : namespace + ".") + originalName + arguments;
    }
}
