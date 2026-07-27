// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Extends the current style rule's selectors with the supplied target.
///
/// @param selector the unevaluated target selector list
/// @param optional whether a missing target is allowed
/// @param span     the complete `@extend` span
@ApiStatus.Internal
@NotNullByDefault
public record ExtendRule(
        Interpolation selector,
        boolean optional,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable extend rule.
    public ExtendRule {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the extend-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitExtendRule(this);
    }
}
