// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value.color;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;
import java.util.Objects;

/// Controls how two polar hues are mixed during Color Level 4 interpolation.
@ApiStatus.Internal
@NotNullByDefault
public enum HueInterpolationMethod {
    /// Uses the shorter arc between the two hues.
    SHORTER("shorter"),

    /// Uses the longer arc between the two hues.
    LONGER("longer"),

    /// Always increases hue from the first color toward the second.
    INCREASING("increasing"),

    /// Always decreases hue from the first color toward the second.
    DECREASING("decreasing");

    /// The CSS keyword for this method.
    private final String name;

    /// Creates one hue interpolation method.
    ///
    /// @param name the CSS keyword
    HueInterpolationMethod(String name) {
        this.name = name;
    }

    /// Returns the CSS keyword.
    ///
    /// @return the keyword
    public String methodName() {
        return name;
    }

    /// Parses a CSS hue interpolation keyword.
    ///
    /// @param name the keyword
    /// @return the method
    /// @throws IllegalArgumentException if the keyword is unknown
    public static HueInterpolationMethod fromName(String name) {
        Objects.requireNonNull(name, "name");
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "shorter" -> SHORTER;
            case "longer" -> LONGER;
            case "increasing" -> INCREASING;
            case "decreasing" -> DECREASING;
            default -> throw new IllegalArgumentException(
                    "Unknown hue interpolation method " + name + "."
            );
        };
    }

    /// Returns the CSS keyword.
    ///
    /// @return the keyword
    @Override
    public String toString() {
        return name;
    }
}
