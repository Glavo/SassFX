// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Represents an {@code nth-child} or {@code nth-last-child} pseudo argument.
///
/// @param formula   the An+B formula without an {@code of} selector list
/// @param selectors the optional selector list after {@code of}
@ApiStatus.Internal
@NotNullByDefault
public record NthPseudoArgument(
        String formula,
        @Nullable SelectorList selectors
) implements PseudoArgument {
    /// Creates one nth pseudo-selector argument.
    ///
    /// @throws IllegalArgumentException if {@code formula} is empty
    public NthPseudoArgument {
        Objects.requireNonNull(formula, "formula");
        if (formula.isEmpty()) {
            throw new IllegalArgumentException("formula must not be empty");
        }
    }

    @Override
    public String toCssString() {
        return selectors == null ? formula : formula + " of " + selectors.toCssString();
    }

    @Override
    public boolean containsParentSelector() {
        return RawPseudoArgument.containsParentMarker(formula)
                || selectors != null && selectors.containsParentSelector();
    }

    @Override
    public int parentSelectorCount() {
        return selectors == null ? 0 : selectors.parentSelectorCount();
    }

    @Override
    public boolean hasParentSelectorSuffix() {
        return selectors != null && selectors.hasParentSelectorSuffix();
    }

    @Override
    public boolean hasUnresolvedParentReference() {
        return RawPseudoArgument.containsParentMarker(formula)
                || selectors != null && selectors.hasUnresolvedParentReference();
    }

    @Override
    public NthPseudoArgument replaceParentSelectors(SelectorList parent) {
        Objects.requireNonNull(parent, "parent");
        if (RawPseudoArgument.containsParentMarker(formula)) {
            throw new SassValueException(
                    "Parent selectors in nth pseudo formulas aren't supported."
            );
        }
        return selectors == null ? this : new NthPseudoArgument(
                formula,
                selectors.replaceParentSelectors(parent)
        );
    }
}
