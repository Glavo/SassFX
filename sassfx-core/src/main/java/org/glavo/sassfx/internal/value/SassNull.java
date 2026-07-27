// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents the single Sass `null` value.
@ApiStatus.Internal
@NotNullByDefault
public enum SassNull implements SassValue {
    /// Contains the Sass null singleton.
    NULL;

    /// Returns false because Sass null is falsey.
    ///
    /// @return the false truthiness value
    @Override
    public boolean isTruthy() {
        return false;
    }

    /// Returns true because Sass null is omitted from CSS lists.
    ///
    /// @return the blank-state value, `true`
    @Override
    public boolean isBlank() {
        return true;
    }

    /// Returns true because Sass null is falsey.
    ///
    /// @return the true singleton, [SassBoolean#TRUE]
    @Override
    public SassBoolean unaryNot() {
        return SassBoolean.TRUE;
    }

    /// Returns the empty CSS representation of null.
    ///
    /// @return the empty string
    @Override
    public String toCssString() {
        return "";
    }

    /// Returns the inspect-mode Sass spelling.
    ///
    /// @return the Sass spelling, `null`
    @Override
    public String toString() {
        return "null";
    }
}
