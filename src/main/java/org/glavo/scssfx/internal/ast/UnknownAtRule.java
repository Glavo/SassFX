// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Represents a plain-CSS at-rule without compiler-specific semantics.
///
/// The name may contain interpolation (for example {@code @#{function}}) so
/// that CSS at-rules discovered only after evaluation still participate in
/// plain-CSS emission.
///
/// @param name the at-rule name without the leading at sign
/// @param value the raw interpolated prelude
/// @param children the block children, or {@code null} for a semicolon-terminated rule
/// @param span the complete at-rule source range
@ApiStatus.Internal
@NotNullByDefault
public record UnknownAtRule(
        Interpolation name,
        Interpolation value,
        @Nullable @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable unknown at-rule.
    public UnknownAtRule {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (children != null) {
            children = List.copyOf(children);
        }
        Objects.requireNonNull(span, "span");
    }

    /// Creates an unknown at-rule with a plain name.
    ///
    /// @param plainName the decoded at-rule name without {@code @}
    /// @param value     the prelude
    /// @param children  the children, or {@code null}
    /// @param span      the full span
    /// @return the rule
    public static UnknownAtRule plain(
            String plainName,
            Interpolation value,
            @Nullable List<SassStatement> children,
            SourceSpan span
    ) {
        Objects.requireNonNull(plainName, "plainName");
        if (plainName.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        return new UnknownAtRule(
                Interpolation.plain(plainName, span),
                value,
                children,
                span
        );
    }

    /// Returns whether this rule owns a block.
    ///
    /// @return whether [#children()] is non-null
    public boolean hasChildren() {
        return children != null;
    }

    /// Dispatches this statement to the visitor.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R> the result type
    /// @return the visitor result
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitUnknownAtRule(this);
    }
}
