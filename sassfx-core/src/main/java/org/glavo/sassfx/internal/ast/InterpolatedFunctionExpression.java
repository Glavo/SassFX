// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Invokes a plain CSS function whose name contains interpolation.
///
/// @param name the unevaluated function name
/// @param arguments the arguments supplied to the function
/// @param span the source range covering the complete invocation
@ApiStatus.Internal
@NotNullByDefault
public record InterpolatedFunctionExpression(
        Interpolation name,
        ArgumentList arguments,
        SourceSpan span
) implements SassExpression {
    /// Creates an interpolated plain CSS function invocation.
    public InterpolatedFunctionExpression {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this expression to the interpolated-function visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitInterpolatedFunctionExpression(this);
    }

    /// Returns a normalized Sass source representation of this invocation.
    ///
    /// @return the interpolated name followed by its arguments
    @Override
    public String toString() {
        return name + arguments.toString();
    }
}
