// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value.color;

import org.glavo.scssfx.internal.value.SassList;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Describes the Color Level 4 interpolation method used by {@code color.mix()}.
///
/// @param space the interpolation color space
/// @param hue the polar hue method, or {@code null} for rectangular spaces
@ApiStatus.Internal
@NotNullByDefault
public record InterpolationMethod(
        ColorSpace space,
        @Nullable HueInterpolationMethod hue
) {
    /// Creates an interpolation method with polar-space defaults.
    ///
    /// Polar spaces default to {@link HueInterpolationMethod#SHORTER} when
    /// {@code hue} is omitted. Rectangular spaces reject a non-null hue.
    public InterpolationMethod {
        Objects.requireNonNull(space, "space");
        if (space.isPolar()) {
            if (hue == null) {
                hue = HueInterpolationMethod.SHORTER;
            }
        } else if (hue != null) {
            throw new IllegalArgumentException(
                    "Hue interpolation method may not be set for rectangular color space "
                            + space + "."
            );
        }
    }

    /// Creates an interpolation method that uses the default hue strategy for
    /// polar spaces.
    ///
    /// @param space the interpolation space
    /// @return the method
    public static InterpolationMethod of(ColorSpace space) {
        return new InterpolationMethod(space, null);
    }

    /// Parses a SassScript interpolation method that does not begin with {@code in}.
    ///
    /// Accepts either an unquoted space name or a space-separated list of the
    /// form {@code <space> <hue-method> hue}.
    ///
    /// @param value the Sass value
    /// @param argumentName the parameter name used in diagnostics, without {@code $}
    /// @return the parsed method
    /// @throws SassValueException if {@code value} is not a valid method
    public static InterpolationMethod fromValue(SassValue value, String argumentName) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(argumentName, "argumentName");
        List<SassValue> parts = value instanceof SassList list
                ? list.asList()
                : List.of(value);
        if (parts.isEmpty()) {
            throw new SassValueException(
                    "$" + argumentName
                            + ": Expected a color interpolation method, got an empty list."
            );
        }

        var space = spaceName(parts.get(0), argumentName);
        if (parts.size() == 1) {
            return of(space);
        }
        if (parts.size() != 3) {
            if (parts.size() == 2) {
                throw new SassValueException(
                        "$" + argumentName + ": Expected unquoted string \"hue\" after "
                                + value + "."
                );
            }
            throw new SassValueException(
                    "$" + argumentName + ": Expected nothing after \"hue\" in " + value + "."
            );
        }

        var hueMethod = hueName(parts.get(1), argumentName);
        var hueKeyword = parts.get(2);
        if (!(hueKeyword instanceof SassString hueString)
                || hueString.hasQuotes()
                || !"hue".equals(hueString.text().toLowerCase(Locale.ROOT))) {
            throw new SassValueException(
                    "$" + argumentName + ": Expected unquoted string \"hue\" at the end of "
                            + value + ", was " + hueKeyword + "."
            );
        }
        if (!space.isPolar()) {
            throw new SassValueException(
                    "$" + argumentName + ": Hue interpolation method \"" + hueMethod
                            + " hue\" may not be set for rectangular color space " + space + "."
            );
        }
        return new InterpolationMethod(space, hueMethod);
    }

    /// Parses one unquoted color-space name.
    private static ColorSpace spaceName(SassValue value, String argumentName) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            throw new SassValueException(
                    "$" + argumentName + ": " + value + " is not an unquoted string."
            );
        }
        try {
            return ColorSpace.fromName(string.text());
        } catch (IllegalArgumentException exception) {
            throw new SassValueException("$" + argumentName + ": " + exception.getMessage());
        }
    }

    /// Parses one unquoted hue interpolation keyword.
    private static HueInterpolationMethod hueName(SassValue value, String argumentName) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            throw new SassValueException(
                    "$" + argumentName + ": " + value + " is not an unquoted string."
            );
        }
        try {
            return HueInterpolationMethod.fromName(string.text());
        } catch (IllegalArgumentException exception) {
            throw new SassValueException(
                    "$" + argumentName + ": Unknown hue interpolation method " + value + "."
            );
        }
    }

    /// Returns the CSS-like inspect form of this method.
    ///
    /// @return the inspect text
    @Override
    public String toString() {
        return hue == null ? space.spaceName() : space.spaceName() + " " + hue + " hue";
    }
}
