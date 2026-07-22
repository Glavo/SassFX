// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Loads another stylesheet as a module.
///
/// @param url       the unresolved module URL string
/// @param namespace the module namespace, or {@code null} for {@code as *}
/// @param span      the complete `@use` span
@ApiStatus.Internal
@NotNullByDefault
public record UseRule(
        String url,
        @Nullable String namespace,
        SourceSpan span
) implements SassStatement {
    /// Creates a use rule.
    ///
    /// @throws IllegalArgumentException if {@code url} is empty or
    /// {@code namespace} is empty
    public UseRule {
        Objects.requireNonNull(url, "url");
        if (url.isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        if (namespace != null && namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the use-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitUseRule(this);
    }
}
