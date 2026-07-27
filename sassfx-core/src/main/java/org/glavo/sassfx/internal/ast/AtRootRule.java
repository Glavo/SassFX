// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Emits children outside selected enclosing CSS parents.
///
/// @param query    the unevaluated query interpolation, or {@code null} for the default query
/// @param children the statements evaluated under the adjusted parent path
/// @param span     the complete `@at-root` span
@ApiStatus.Internal
@NotNullByDefault
public record AtRootRule(
        @Nullable Interpolation query,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable at-root rule.
    public AtRootRule {
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the at-root visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitAtRootRule(this);
    }
}
