// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a functional pseudo-selector argument parsed as a selector list.
///
/// @param selectors the recursively parsed selector list
@ApiStatus.Internal
@NotNullByDefault
public record SelectorPseudoArgument(SelectorList selectors) implements PseudoArgument {
    /// Creates one selector-list pseudo-selector argument.
    public SelectorPseudoArgument {
        Objects.requireNonNull(selectors, "selectors");
    }

    @Override
    public String toCssString() {
        return selectors.toCssString();
    }

    @Override
    public boolean containsParentSelector() {
        return selectors.containsParentSelector();
    }

    @Override
    public int parentSelectorCount() {
        return selectors.parentSelectorCount();
    }

    @Override
    public boolean hasParentSelectorSuffix() {
        return selectors.hasParentSelectorSuffix();
    }

    @Override
    public boolean hasUnresolvedParentReference() {
        return selectors.hasUnresolvedParentReference();
    }

    @Override
    public SelectorPseudoArgument replaceParentSelectors(SelectorList parent) {
        return new SelectorPseudoArgument(
                selectors.replaceParentSelectors(Objects.requireNonNull(parent, "parent"))
        );
    }
}
