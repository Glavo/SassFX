// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// A simple selector that cannot be decomposed further.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SimpleSelector permits
        TypeSelector,
        ClassSelector,
        IdSelector,
        UniversalSelector,
        AttributeSelector,
        PseudoSelector,
        PlaceholderSelector,
        ParentSelector,
        OtherSimpleSelector {
    /// Returns the source span associated with this selector.
    ///
    /// @return the selector span
    SourceSpan span();

    /// Returns the CSS text of this simple selector.
    ///
    /// @return the CSS spelling
    String toCssString();

    /// Returns whether this selector contains a parent-selector reference.
    ///
    /// Implementations with recursive selector arguments override this method.
    ///
    /// @return whether this selector represents or contains {@code &}
    default boolean containsParentSelector() {
        return this instanceof ParentSelector;
    }

    /// Returns the number of structurally represented parent selectors.
    ///
    /// @return the number of parent-selector nodes
    default int parentSelectorCount() {
        return this instanceof ParentSelector ? 1 : 0;
    }

    /// Returns whether a represented parent selector has an identifier suffix.
    ///
    /// @return whether a parent selector uses suffix syntax
    default boolean hasParentSelectorSuffix() {
        return this instanceof ParentSelector parent && parent.suffix() != null;
    }

    /// Returns whether this selector contains a parent marker that cannot be
    /// replaced structurally.
    ///
    /// @return whether parent replacement must fail explicitly
    default boolean hasUnresolvedParentReference() {
        return false;
    }

    /// Returns a copy of this selector with {@code suffix} appended when supported.
    ///
    /// @param suffix the identifier suffix
    /// @return the suffixed selector
    /// @throws SassValueException if this selector cannot accept a suffix
    SimpleSelector addSuffix(CssIdentifier suffix);
}
