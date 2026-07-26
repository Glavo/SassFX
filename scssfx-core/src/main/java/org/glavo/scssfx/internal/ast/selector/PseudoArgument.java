// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents the grammar-specific content of a functional pseudo selector.
///
/// Implementations retain either opaque CSS argument text, a recursively
/// parsed selector list, or the formula and optional selector list of an
/// {@code nth-*} pseudo selector.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface PseudoArgument permits
        RawPseudoArgument,
        SelectorPseudoArgument,
        NthPseudoArgument {
    /// Returns the CSS spelling of this argument without enclosing parentheses.
    ///
    /// @return the serialized argument text
    String toCssString();

    /// Returns whether this argument contains a parent-selector reference.
    ///
    /// @return whether {@code &} occurs in the represented argument
    boolean containsParentSelector();

    /// Returns the number of structurally represented parent-selector nodes.
    ///
    /// Raw argument text is not counted because it cannot be substituted
    /// safely.
    ///
    /// @return the recursive parent-selector count
    int parentSelectorCount();

    /// Returns whether a structurally represented parent selector has a suffix.
    ///
    /// @return whether one represented {@code &} has an identifier suffix
    boolean hasParentSelectorSuffix();

    /// Returns whether an opaque argument contains a parent marker that cannot
    /// be substituted structurally.
    ///
    /// @return whether parent replacement must fail explicitly
    boolean hasUnresolvedParentReference();

    /// Replaces structurally represented parent selectors with {@code parent}.
    ///
    /// Selector-list arguments retain branches that do not contain
    /// {@code &}. Opaque arguments with a parent marker cause a value error
    /// instead of being emitted unresolved.
    ///
    /// @param parent the selector list that replaces parent selectors
    /// @return this argument with recursive parent references resolved
    /// @throws SassValueException if an opaque argument contains {@code &}
    PseudoArgument replaceParentSelectors(SelectorList parent);
}
