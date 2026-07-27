// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value.color;

import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.glavo.sassfx.internal.value.SassValueException;
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
        // dart-sass always validates the second component as a hue method name
        // before requiring the trailing {@code hue} keyword (or reporting arity).
        var hueMethod = hueName(parts.get(1), argumentName);
        if (parts.size() == 2) {
            // Parenthesize multi-element lists in diagnostics to match dart-sass.
            String shown = value instanceof SassList list && list.asList().size() > 1
                    && !list.hasBrackets()
                    ? "(" + value + ")"
                    : value.toString();
            throw new SassValueException(
                    "$" + argumentName + ": Expected unquoted string \"hue\" after "
                            + shown + "."
            );
        }
        if (parts.size() != 3) {
            throw new SassValueException(
                    "$" + argumentName + ": Expected nothing after \"hue\" in " + value + "."
            );
        }

        var hueKeyword = parts.get(2);
        if (!(hueKeyword instanceof SassString hueString)
                || hueString.hasQuotes()
                || !"hue".equals(hueString.text().toLowerCase(Locale.ROOT))) {
            String shown = value instanceof SassList list && list.asList().size() > 1
                    && !list.hasBrackets()
                    ? "(" + value + ")"
                    : value.toString();
            throw new SassValueException(
                    "$" + argumentName + ": Expected unquoted string \"hue\" at the end of "
                            + shown + ", was " + hueKeyword + "."
            );
        }
        if (!space.isPolar()) {
            // Match dart-sass's enum toString() in this diagnostic:
            // "HueInterpolationMethod.longer hue".
            throw new SassValueException(
                    "$" + argumentName + ": Hue interpolation method \"HueInterpolationMethod."
                            + hueMethod.methodName() + " hue\" may not be set for rectangular "
                            + "color space " + space + "."
            );
        }
        return new InterpolationMethod(space, hueMethod);
    }

    /// Parses one unquoted color-space name.
    private static ColorSpace spaceName(SassValue value, String argumentName) {
        if (!(value instanceof SassString string)) {
            throw new SassValueException(
                    "$" + argumentName + ": " + value + " is not a string."
            );
        }
        if (string.hasQuotes()) {
            throw new SassValueException(
                    "$" + argumentName + ": Expected " + value + " to be an unquoted string."
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
        if (!(value instanceof SassString string)) {
            throw new SassValueException(
                    "$" + argumentName + ": " + diagnosticValue(value) + " is not a string."
            );
        }
        if (string.hasQuotes()) {
            throw new SassValueException(
                    "$" + argumentName + ": Expected " + value
                            + " to be an unquoted string."
            );
        }
        try {
            return HueInterpolationMethod.fromName(string.text());
        } catch (IllegalArgumentException exception) {
            throw new SassValueException(
                    "$" + argumentName + ": Unknown hue interpolation method "
                            + string.text() + "."
            );
        }
    }

    /// Formats a value for dart-sass-style method diagnostics.
    ///
    /// Unbracketed multi-element lists are parenthesized so messages match
    /// {@code (decreasing hue) is not a string}.
    private static String diagnosticValue(SassValue value) {
        if (value instanceof SassList list
                && !list.hasBrackets()
                && list.asList().size() > 1) {
            return "(" + value + ")";
        }
        return value.toString();
    }

    /// Returns the CSS-like inspect form of this method.
    ///
    /// @return the inspect text
    @Override
    public String toString() {
        return hue == null ? space.spaceName() : space.spaceName() + " " + hue + " hue";
    }
}
