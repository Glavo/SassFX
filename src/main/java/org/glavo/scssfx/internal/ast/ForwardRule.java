// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Re-exports selected public members of another stylesheet module.
///
/// @param url                      the unresolved module URL string
/// @param prefix                   the normalized export prefix, or {@code null}
/// @param shownMixinsAndFunctions  the callable allowlist, or {@code null}
/// @param shownVariables           the variable allowlist, or {@code null}
/// @param hiddenMixinsAndFunctions the callable blocklist, or {@code null}
/// @param hiddenVariables          the variable blocklist, or {@code null}
/// @param configuration            configured variables in source order
/// @param span                     the complete {@code @forward} span
@ApiStatus.Internal
@NotNullByDefault
public record ForwardRule(
        String url,
        @Nullable String prefix,
        @Nullable @Unmodifiable Set<String> shownMixinsAndFunctions,
        @Nullable @Unmodifiable Set<String> shownVariables,
        @Nullable @Unmodifiable Set<String> hiddenMixinsAndFunctions,
        @Nullable @Unmodifiable Set<String> hiddenVariables,
        @Unmodifiable List<ConfiguredVariable> configuration,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable forward rule.
    ///
    /// @throws IllegalArgumentException if the URL or prefix is empty, the
    /// filter pairs are incomplete, or both an allowlist and blocklist exist
    public ForwardRule {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(span, "span");
        if (url.isEmpty()) {
            throw new IllegalArgumentException("url must not be empty");
        }
        if (prefix != null && prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be empty");
        }
        if ((shownMixinsAndFunctions == null) != (shownVariables == null)) {
            throw new IllegalArgumentException("shown member sets must be supplied together");
        }
        if ((hiddenMixinsAndFunctions == null) != (hiddenVariables == null)) {
            throw new IllegalArgumentException("hidden member sets must be supplied together");
        }
        if (shownMixinsAndFunctions != null && hiddenMixinsAndFunctions != null) {
            throw new IllegalArgumentException("show and hide filters are mutually exclusive");
        }
        shownMixinsAndFunctions = immutableSet(shownMixinsAndFunctions);
        shownVariables = immutableSet(shownVariables);
        hiddenMixinsAndFunctions = immutableSet(hiddenMixinsAndFunctions);
        hiddenVariables = immutableSet(hiddenVariables);
        configuration = List.copyOf(configuration);
    }

    /// Creates a plain forward rule without a prefix, member filter, or configuration.
    ///
    /// @param url  the unresolved module URL string
    /// @param span the complete {@code @forward} span
    public ForwardRule(String url, SourceSpan span) {
        this(url, null, null, null, null, null, List.of(), span);
    }

    /// Returns an immutable copy of a nullable member set.
    ///
    /// @param values the source set, or {@code null}
    /// @return an immutable set, or {@code null}
    private static @Nullable @Unmodifiable Set<String> immutableSet(
            @Nullable Set<String> values
    ) {
        return values == null ? null : Set.copyOf(values);
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
