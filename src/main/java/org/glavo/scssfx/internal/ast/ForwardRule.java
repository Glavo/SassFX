// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Re-exports the public members of another stylesheet module.
///
/// @param url  the unresolved module URL string
/// @param span the complete `@forward` span
@ApiStatus.Internal
@NotNullByDefault
public record ForwardRule(String url, SourceSpan span) implements SassStatement {
    /// Creates a plain forward rule without a prefix, member filter, or configuration.
    ///
    /// @throws IllegalArgumentException if {@code url} is empty
    public ForwardRule {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(span, "span");
        if (url.isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
    }

    /// Dispatches this statement to the forward-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitForwardRule(this);
    }
}
