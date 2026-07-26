// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Invokes the content block supplied by a mixin include.
///
/// @param arguments the arguments passed to {@code @content}
/// @param span      the complete content-rule span
@ApiStatus.Internal
@NotNullByDefault
public record ContentRule(
        ArgumentList arguments,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable content rule.
    public ContentRule {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the content-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitContentRule(this);
    }
}
