// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Includes a previously declared mixin.
///
/// @param namespace    the module namespace, or {@code null} for an unqualified include
/// @param originalName the decoded mixin name with underscores retained
/// @param arguments    the arguments supplied to the mixin
/// @param content      the optional content block
/// @param span         the complete include span
@ApiStatus.Internal
@NotNullByDefault
public record IncludeRule(
        @Nullable String namespace,
        String originalName,
        ArgumentList arguments,
        @Nullable ContentBlock content,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable include rule.
    ///
    /// @throws IllegalArgumentException if a name is empty
    public IncludeRule {
        Objects.requireNonNull(originalName, "originalName");
        if (originalName.isEmpty()) {
            throw new IllegalArgumentException("originalName must not be empty");
        }
        if (namespace != null && namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the include-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitIncludeRule(this);
    }

    /// Returns the normalized mixin name used for lookup.
    ///
    /// @return the name with underscores normalized to hyphens
    public String name() {
        return originalName.replace('_', '-');
    }
}
