// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Declares a user-defined function.
///
/// @param originalName the decoded function name with underscores retained
/// @param parameters   the accepted parameters
/// @param children     the function body statements
/// @param span         the complete rule span
@ApiStatus.Internal
@NotNullByDefault
public record FunctionRule(
        String originalName,
        ParameterList parameters,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable function declaration.
    ///
    /// @throws IllegalArgumentException if {@code originalName} is empty
    public FunctionRule {
        Objects.requireNonNull(originalName, "originalName");
        if (originalName.isEmpty()) {
            throw new IllegalArgumentException("originalName must not be empty");
        }
        Objects.requireNonNull(parameters, "parameters");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the function-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitFunctionRule(this);
    }

    /// Returns the normalized function name used for lookup.
    ///
    /// @return the name with underscores normalized to hyphens
    public String name() {
        return originalName.replace('_', '-');
    }
}
