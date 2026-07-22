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

    /// Returns a copy of this selector with {@code suffix} appended when supported.
    ///
    /// @param suffix the identifier suffix
    /// @return the suffixed selector
    /// @throws SassValueException if this selector cannot accept a suffix
    SimpleSelector addSuffix(String suffix);
}
