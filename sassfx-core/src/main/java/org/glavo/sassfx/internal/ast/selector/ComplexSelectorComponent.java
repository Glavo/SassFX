// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// One compound selector plus the combinators that follow it.
///
/// An empty combinator list means an implicit descendant relationship to the
/// next component.
///
/// @param selector    the compound selector
/// @param combinators the trailing combinators
/// @param span        the component span
@ApiStatus.Internal
@NotNullByDefault
public record ComplexSelectorComponent(
        CompoundSelector selector,
        @Unmodifiable List<Combinator> combinators,
        SourceSpan span
) {
    /// Creates a complex selector component.
    public ComplexSelectorComponent {
        Objects.requireNonNull(selector, "selector");
        combinators = List.copyOf(combinators);
        Objects.requireNonNull(span, "span");
    }

    /// Returns a component with additional trailing combinators.
    ///
    /// @param extra the combinators to append
    /// @return the extended component
    public ComplexSelectorComponent withAdditionalCombinators(List<Combinator> extra) {
        if (extra.isEmpty()) {
            return this;
        }
        var next = new java.util.ArrayList<>(combinators);
        next.addAll(extra);
        return new ComplexSelectorComponent(selector, next, span);
    }
}
