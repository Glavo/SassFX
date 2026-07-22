// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents either Sass boolean value.
@ApiStatus.Internal
@NotNullByDefault
public enum SassBoolean implements SassValue {
    /// Represents Sass `true`.
    TRUE(true),

    /// Represents Sass `false`.
    FALSE(false);

    /// Contains the Java boolean represented by this value.
    private final boolean value;

    /// Creates a Sass boolean constant.
    ///
    /// @param value the represented Java boolean
    SassBoolean(boolean value) {
        this.value = value;
    }

    /// Returns the Sass boolean for a Java boolean.
    ///
    /// @param value the Java boolean
    /// @return [#TRUE] or [#FALSE]
    public static SassBoolean of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /// Returns the represented Java boolean.
    ///
    /// @return the boolean value
    public boolean value() {
        return value;
    }

    /// Returns this boolean's truthiness.
    ///
    /// @return the represented boolean
    @Override
    public boolean isTruthy() {
        return value;
    }

    /// Returns the opposite Sass boolean.
    ///
    /// @return the negated value
    @Override
    public SassBoolean unaryNot() {
        return value ? FALSE : TRUE;
    }

    /// Returns the Sass boolean spelling.
    ///
    /// @return `true` or `false`
    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
