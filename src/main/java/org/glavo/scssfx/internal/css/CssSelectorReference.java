// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Holds a selector value shared by structural copies of one CSS style rule.
///
/// Updating this reference changes the selector observed by every style rule
/// that retains it. [#copy()] creates an independent reference for deep CSS
/// copies whose later extension state must remain isolated.
@ApiStatus.Internal
@NotNullByDefault
public final class CssSelectorReference {
    /// Contains the current selector value.
    private CssValue<SelectorList> value;

    /// Creates a selector reference with the supplied initial value.
    ///
    /// @param value the initial selector value
    public CssSelectorReference(CssValue<SelectorList> value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /// Returns the current selector value.
    ///
    /// @return the current selector value
    public CssValue<SelectorList> value() {
        return value;
    }

    /// Replaces the selector value observed through this reference.
    ///
    /// @param value the replacement selector value
    public void set(CssValue<SelectorList> value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /// Creates an independent reference initialized with the current value.
    ///
    /// Subsequent updates to either reference do not affect the other.
    ///
    /// @return an independent selector reference
    public CssSelectorReference copy() {
        return new CssSelectorReference(value);
    }
}
