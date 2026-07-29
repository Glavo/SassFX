// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.function;

import org.glavo.sassfx.internal.callable.BuiltInCallable;
import org.glavo.sassfx.internal.value.ListSeparator;
import org.glavo.sassfx.internal.value.RgbFunctionColorFormat;
import org.glavo.sassfx.internal.value.SassColor;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.glavo.sassfx.internal.value.SassValueException;
import org.glavo.sassfx.internal.value.color.ColorChannel;
import org.glavo.sassfx.internal.value.color.ColorSpace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Parses CSS Color Level 4 channel lists used by global color constructors.
@ApiStatus.Internal
@NotNullByDefault
final class CssColorChannels {
    /// Prevents instantiation.
    private CssColorChannels() {
    }

    /// Spaces that emit comma-separated special-number fallbacks for broader CSS support.
    private static final java.util.Set<ColorSpace> SPECIAL_COMMA_SPACES =
            java.util.Set.of(ColorSpace.RGB, ColorSpace.HSL);

    /// Creates a color from a single channels argument for a known polar/Lab space.
    ///
    /// When channels contain special CSS number forms such as {@code calc()} or
    /// {@code var()}, returns an unquoted plain-CSS function call instead.
    ///
    /// @param functionName the constructor name
    /// @param channels the space-separated channel list, optionally slash-alpha
    /// @param space the destination color space
    /// @return the constructed color or plain-CSS function string
    static SassValue parseFixedSpace(
            String functionName,
            SassValue channels,
            ColorSpace space
    ) {
        return parseFixedSpace(null, functionName, channels, space);
    }

    /// Creates a fixed-space color and reports legacy channel-unit usage.
    ///
    /// @param context the invocation receiving deprecations, or {@code null}
    /// @param functionName the constructor name
    /// @param channels the space-separated channel list, optionally slash-alpha
    /// @param space the destination color space
    /// @return the constructed color or plain-CSS function string
    static SassValue parseFixedSpace(
            @Nullable BuiltInCallable.Context context,
            String functionName,
            SassValue channels,
            ColorSpace space
    ) {
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(space, "space");
        return parseChannels(
                context,
                functionName,
                channels,
                space,
                "channels"
        );
    }

    /// Creates a color from {@code color($description)}.
    ///
    /// @param description the space name followed by channels
    /// @return the constructed color or plain-CSS function string
    static SassValue parseColorDescription(SassValue description) {
        Objects.requireNonNull(description, "description");
        return parseChannels(
                null,
                "color",
                description,
                null,
                "description"
        );
    }

    /// Parses a channels argument, preserving special CSS number forms.
    private static SassValue parseChannels(
            @Nullable BuiltInCallable.Context context,
            String functionName,
            SassValue input,
            @Nullable ColorSpace fixedSpace,
            String argumentName
    ) {
        if (input.isSpecialVariable()) {
            return functionString(functionName, List.of(input));
        }
        // Slash-separated channel/alpha lists must contain exactly two elements.
        // Reject other arities before the soft special-number fallback.
        if (input instanceof SassList slashList
                && slashList.separator() == ListSeparator.SLASH) {
            int size = slashList.asList().size();
            if (size != 2) {
                throw new SassValueException(
                        "$" + argumentName + ": Only 2 slash-separated elements allowed, but "
                                + size + (size == 1 ? " was" : " were") + " passed."
                );
            }
        }
        @Nullable SlashChannels parsed = tryParseSlashChannels(input);
        if (parsed == null) {
            return functionString(functionName, List.of(input));
        }
        // Reject bracketed/comma lists before interpreting channels. After a
        // slash-alpha has already been split off, only space separators remain
        // valid on the channel portion.
        assertCommonListStyle(parsed.channels(), argumentName, parsed.alpha() == null);
        var components = expandSpaceList(parsed.channels());
        if (components.isEmpty()) {
            throw new SassValueException(
                    "$" + argumentName + ": Color component list may not be empty."
            );
        }
        // Relative color syntax: lab(from ...) is preserved as plain CSS.
        if (components.get(0) instanceof SassString first
                && !first.hasQuotes()
                && "from".equalsIgnoreCase(first.text())) {
            return functionString(functionName, List.of(input));
        }
        if (parsed.channels().isSpecialVariable()) {
            return functionString(functionName, List.of(input));
        }

        @Nullable ColorSpace space = fixedSpace;
        List<SassValue> channels;
        if (space == null) {
            SassValue first = components.get(0);
            if (!(first instanceof SassString spaceName)) {
                throw new SassValueException(
                        "$" + argumentName + ": " + first + " is not a string."
                );
            }
            if (spaceName.hasQuotes()) {
                throw new SassValueException(
                        "$" + argumentName + ": Expected " + first
                                + " to be an unquoted string."
                );
            }
            if (spaceName.isSpecialVariable()) {
                return functionString(functionName, List.of(input));
            }
            try {
                space = ColorSpace.fromName(spaceName.text());
            } catch (IllegalArgumentException exception) {
                throw new SassValueException("$" + argumentName + ": " + exception.getMessage());
            }
            if (space == ColorSpace.RGB
                    || space == ColorSpace.HSL
                    || space == ColorSpace.HWB
                    || space == ColorSpace.LAB
                    || space == ColorSpace.LCH
                    || space == ColorSpace.OKLAB
                    || space == ColorSpace.OKLCH) {
                throw new SassValueException(
                        "$" + argumentName + ": The color() function doesn't support the color space "
                                + space + ". Use the " + space.spaceName() + "() function instead."
                );
            }
            channels = components.subList(1, components.size());
        } else {
            channels = components;
        }

        for (var index = 0; index < channels.size(); index++) {
            SassValue channel = channels.get(index);
            if (!channel.isSpecialNumber()
                    && !(channel instanceof SassNumber)
                    && !isNone(channel)) {
                String channelName = index < space.channels().size()
                        ? space.channels().get(index).name() + " channel"
                        : "channel " + (index + 1);
                // Parenthesize nested multi-element lists in channel diagnostics.
                String shown = channel instanceof SassList nested && !nested.hasBrackets()
                        && nested.asList().size() > 1
                        ? "(" + channel + ")"
                        : channel.toString();
                throw new SassValueException(
                        "$" + argumentName + ": Expected " + channelName
                                + " to be a number, was " + shown + "."
                );
            }
        }

        @Nullable SassValue alphaValue = parsed.alpha();
        if (alphaValue != null && alphaValue.isSpecialNumber()) {
            return specialNumberFallback(functionName, space, channels, alphaValue, input);
        }
        if (channels.stream().anyMatch(SassValue::isSpecialNumber)) {
            return specialNumberFallback(functionName, space, channels, alphaValue, input);
        }

        return colorFromChannels(
                context,
                space,
                new SassList(channels, ListSeparator.SPACE, false),
                alphaValue,
                space == ColorSpace.RGB,
                argumentName,
                input
        );
    }

    /// Asserts a color-channel list uses space (or optionally slash) separators.
    private static void assertCommonListStyle(
            SassValue value,
            String argumentName,
            boolean allowSlash
    ) {
        if (!(value instanceof SassList list)) {
            return;
        }
        if (list.hasBrackets()) {
            throw new SassValueException(
                    "$" + argumentName + ": Expected an unbracketed list, was " + list
            );
        }
        if (list.separator() == ListSeparator.COMMA) {
            // After slash-alpha is split off, only space separators remain valid.
            String expected = allowSlash
                    ? "a space- or slash-separated list"
                    : "a space-separated list";
            throw new SassValueException(
                    "$" + argumentName + ": Expected " + expected + ", was ("
                            + list + ")"
            );
        }
        if (!allowSlash && list.separator() == ListSeparator.SLASH) {
            throw new SassValueException(
                    "$" + argumentName + ": Expected a space-separated list, was ("
                            + list + ")"
            );
        }
    }

    /// Returns a plain-CSS function call for special-number channel lists.
    private static SassString specialNumberFallback(
            String functionName,
            ColorSpace space,
            List<SassValue> channels,
            @Nullable SassValue alphaValue,
            SassValue originalInput
    ) {
        if (channels.size() == 3 && SPECIAL_COMMA_SPACES.contains(space)) {
            var args = new ArrayList<>(channels);
            if (alphaValue != null) {
                args.add(alphaValue);
            }
            return functionString(functionName, args);
        }
        // Rebuild modern space/channel syntax. Prefer the original slash-alpha
        // text when available so var()/calc() fallbacks keep dart-sass spacing.
        if (!"color".equals(functionName) || channels.isEmpty()) {
            return functionString(functionName, List.of(originalInput));
        }
        var builder = new StringBuilder(functionName).append('(')
                .append(space.spaceName());
        for (var channel : channels) {
            builder.append(' ').append(channel.toCssString());
        }
        if (alphaValue != null) {
            // dart-sass omits spaces around '/' for special-number color().
            builder.append('/').append(alphaValue.toCssString());
        }
        return new SassString(builder.append(')').toString(), false);
    }

    /// Builds an unquoted plain-CSS function call string.
    static SassString functionString(String name, List<SassValue> arguments) {
        var builder = new StringBuilder(name).append('(');
        for (var index = 0; index < arguments.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(arguments.get(index).toCssString());
        }
        return new SassString(builder.append(')').toString(), false);
    }

    /// Parses slash-separated alpha, or returns {@code null} when the form is not a
    /// channel list Sass can further interpret.
    private static @Nullable SlashChannels tryParseSlashChannels(SassValue input) {
        try {
            return parseSlashChannels(input);
        } catch (SassValueException ignored) {
            return null;
        }
    }

    /// Splits an optional slash-separated alpha from a channel list.
    private static SlashChannels parseSlashChannels(SassValue input) {
        if (input instanceof SassList list && list.separator() == ListSeparator.SLASH) {
            var elements = list.asList();
            if (elements.size() != 2) {
                throw new SassValueException(
                        "Only 2 slash-separated elements allowed, but " + elements.size()
                                + " were passed."
                );
            }
            return new SlashChannels(elements.get(0), elements.get(1));
        }
        if (input instanceof SassList list && list.separator() == ListSeparator.SPACE) {
            var elements = new ArrayList<>(list.asList());
            if (!elements.isEmpty()) {
                SassValue last = elements.get(elements.size() - 1);
                if (last instanceof SassNumber number
                        && number.slashNumerator() != null
                        && number.slashDenominator() != null) {
                    elements.set(elements.size() - 1, number.slashNumerator());
                    return new SlashChannels(
                            new SassList(elements, ListSeparator.SPACE, false),
                            number.slashDenominator()
                    );
                }
                // Space lists may end with a nested slash pair when the right
                // operand was non-numeric ({@code 50% / none}, {@code 3% / var(--a)}).
                if (last instanceof SassList nested
                        && nested.separator() == ListSeparator.SLASH
                        && !nested.hasBrackets()
                        && nested.asList().size() == 2) {
                    elements.set(elements.size() - 1, nested.asList().get(0));
                    return new SlashChannels(
                            new SassList(elements, ListSeparator.SPACE, false),
                            nested.asList().get(1)
                    );
                }
                if (last instanceof SassString string
                        && !string.hasQuotes()) {
                    // Split only at the first slash so nested forms such as
                    // {@code 3/calc(var(--a) / 2)} keep the calc body intact.
                    var text = string.text();
                    var slash = text.indexOf('/');
                    if (slash > 0 && slash < text.length() - 1) {
                        elements.set(
                                elements.size() - 1,
                                parseNumberOrString(text.substring(0, slash))
                        );
                        return new SlashChannels(
                                new SassList(elements, ListSeparator.SPACE, false),
                                parseNumberOrString(text.substring(slash + 1))
                        );
                    }
                }
            }
        }
        if (input instanceof SassNumber number
                && number.slashNumerator() != null
                && number.slashDenominator() != null) {
            return new SlashChannels(number.slashNumerator(), number.slashDenominator());
        }
        return new SlashChannels(input, null);
    }

    /// Parses a slash-separated channel token as a number when possible.
    ///
    /// @param text the token text
    /// @return a number preserving its unit, or an unquoted string
    private static SassValue parseNumberOrString(String text) {
        // Recover units that appear in slash-presented strings such as "50%/none".
        for (var index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '+' || ch == '-' || ch == '.' || (ch >= '0' && ch <= '9')) {
                continue;
            }
            if (index == 0) {
                break;
            }
            try {
                double value = Double.parseDouble(text.substring(0, index));
                String unit = text.substring(index);
                return SassNumber.of(value, unit);
            } catch (NumberFormatException ignored) {
                return new SassString(text, false);
            }
        }
        try {
            return SassNumber.of(Double.parseDouble(text), null);
        } catch (NumberFormatException ignored) {
            return new SassString(text, false);
        }
    }

    /// Constructs a color after validating and normalizing three channel
    /// values and an optional alpha channel.
    ///
    /// @param context the callable context used for deprecations, or `null`
    /// @param space the destination color space
    /// @param channelsValue the supplied channel value
    /// @param alphaValue the optional alpha value
    /// @param fromRgbFunction whether legacy RGB clamping applies
    /// @param argumentName the argument name used by diagnostics
    /// @param originalInput the original value displayed by diagnostics
    /// @return the constructed color
    private static SassColor colorFromChannels(
            @Nullable BuiltInCallable.Context context,
            ColorSpace space,
            SassValue channelsValue,
            @Nullable SassValue alphaValue,
            boolean fromRgbFunction,
            String argumentName,
            SassValue originalInput
    ) {
        var channels = expandSpaceList(channelsValue);
        if (channels.size() != 3) {
            // dart-sass reports the original input value and the post-space
            // channel count (e.g. color(srgb 0.1 0.2) → "(srgb 0.1 0.2) has 2").
            String shown = originalInput.toString();
            if (!(originalInput instanceof SassList list && list.hasBrackets())
                    && !(shown.startsWith("(") || shown.startsWith("["))) {
                // Multi-element space lists print without parens from toString;
                // wrap them when they contain spaces so diagnostics match dart-sass.
                if (shown.contains(" ") || channels.isEmpty()) {
                    // color(srgb) shows bare "srgb" with zero channels.
                    if (channels.isEmpty() && "description".equals(argumentName)) {
                        shown = space.spaceName();
                    } else if (!channels.isEmpty()) {
                        shown = "(" + shown + ")";
                    }
                }
            }
            throw new SassValueException(
                    "$" + argumentName + ": The " + space.spaceName()
                            + " color space has 3 channels but "
                            + shown + " has " + channels.size() + "."
            );
        }
        @Nullable Double alpha = 1.0;
        if (alphaValue != null) {
            if (isNone(alphaValue)) {
                alpha = null;
            } else if (alphaValue instanceof SassNumber number) {
                // CSS clamps non-finite alpha: +infinity → 1, -infinity/NaN → 0.
                alpha = clampLikeCss(alphaValue(number), 0.0, 1.0);
            } else {
                // Slash-alpha is still part of the $channels/$description argument
                // for fixed-space constructors (dart-sass: "$channels: c is not a number.").
                throw new SassValueException(
                        "$" + argumentName + ": " + alphaValue + " is not a number."
                );
            }
        }

        @Nullable Double channel0 = channelNumber(
                context,
                channels.get(0),
                space,
                space.channels().get(0)
        );
        @Nullable Double channel1 = channelNumber(
                context,
                channels.get(1),
                space,
                space.channels().get(1)
        );
        @Nullable Double channel2 = channelNumber(
                context,
                channels.get(2),
                space,
                space.channels().get(2)
        );
        if (fromRgbFunction || space == ColorSpace.RGB) {
            // Legacy RGB constructors clamp channels into 0..255 like CSS Color 3.
            double red = clampLikeCss(channel0 != null ? channel0 : 0.0, 0.0, 255.0);
            double green = clampLikeCss(channel1 != null ? channel1 : 0.0, 0.0, 255.0);
            double blue = clampLikeCss(channel2 != null ? channel2 : 0.0, 0.0, 255.0);
            double opaqueAlpha = alpha != null ? alpha : 1.0;
            if (channel0 == null || channel1 == null || channel2 == null || alpha == null) {
                return SassColor.rgb(
                        channel0 == null ? null : red,
                        channel1 == null ? null : green,
                        channel2 == null ? null : blue,
                        alpha
                );
            }
            return SassColor.rgb(red, green, blue, opaqueAlpha, RgbFunctionColorFormat.INSTANCE);
        }
        if (space == ColorSpace.HWB) {
            @Nullable Double whiteness = channel1;
            @Nullable Double blackness = channel2;
            if (whiteness != null && blackness != null && whiteness + blackness > 100.0) {
                var sum = whiteness + blackness;
                whiteness = whiteness / sum * 100.0;
                blackness = blackness / sum * 100.0;
            }
            return SassColor.forSpace(ColorSpace.HWB, channel0, whiteness, blackness, alpha);
        }
        if (space == ColorSpace.HSL) {
            // Legacy/CSS hsl() clamps negative and NaN saturation to 0% rather
            // than applying forSpace's hue-flip absolute-value normalization.
            // Lightness NaN is retained so serialization can emit calc(NaN).
            @Nullable Double saturation = channel1;
            if (saturation != null
                    && (Double.isNaN(saturation) || saturation < 0.0)) {
                saturation = 0.0;
            }
            return SassColor.forSpace(ColorSpace.HSL, channel0, saturation, channel2, alpha);
        }
        return SassColor.forSpace(space, channel0, channel1, channel2, alpha);
    }

    /// Converts one color channel to the destination space's numeric scale.
    ///
    /// @param context the callable context used for deprecations, or `null`
    /// @param value the supplied channel
    /// @param space the destination color space
    /// @param channel the destination channel descriptor
    /// @return the channel magnitude, or `null` for `none`
    private static @Nullable Double channelNumber(
            @Nullable BuiltInCallable.Context context,
            SassValue value,
            ColorSpace space,
            ColorChannel channel
    ) {
        if (isNone(value)) {
            return null;
        }
        if (!(value instanceof SassNumber number)) {
            throw new SassValueException(
                    "Expected " + channel.name() + " channel to be a number, was " + value + "."
            );
        }
        if (channel.isPolarAngle()) {
            if (context != null
                    && (space == ColorSpace.HSL || space == ColorSpace.HWB)) {
                return BuiltInFunctions.angleValue(
                        context,
                        number,
                        channel.name()
                );
            }
            return hueDegrees(number);
        }
        if (!(channel instanceof ColorChannel.Linear linear)) {
            throw new SassValueException("Unknown channel " + channel.name() + ".");
        }
        // HWB whiteness/blackness strictly require "%". HSL saturation/lightness
        // still accept unitless magnitudes during the function-units deprecation
        // period (matching dart-sass).
        if (linear.requiresPercent()) {
            boolean percent = number.numeratorUnits().equals(List.of("%"))
                    && number.denominatorUnits().isEmpty();
            if (percent) {
                return number.value();
            }
            String name = channel.name();
            if (context != null
                    && space == ColorSpace.HSL
                    && ("saturation".equals(name)
                    || "lightness".equals(name))) {
                BuiltInFunctions.checkPercent(context, number, name);
                return number.value();
            }
            throw new SassValueException(
                    "$" + name + ": Expected " + number + " to have unit \"%\"."
            );
        }
        // Matches dart-sass {@code _percentageOrUnitless}: unitless is native
        // scale, and percentages scale {@code 0%..100%} onto {@code 0..max}.
        double magnitude;
        if (number.isUnitless()) {
            magnitude = number.value();
        } else if (number.numeratorUnits().equals(List.of("%"))
                && number.denominatorUnits().isEmpty()) {
            magnitude = linear.max() * number.value() / 100.0;
        } else {
            throw new SassValueException(
                    "$" + channel.name() + ": Expected " + number
                            + " to have unit \"%\" or no units."
            );
        }
        // Global constructors clamp bounded Lab-family channels (for example
        // lab(101) → 100 and lab(-1) → 0) like dart-sass clampLikeCss.
        if (linear.lowerClamped() && linear.upperClamped()) {
            return clampLikeCss(magnitude, linear.min(), linear.max());
        }
        if (linear.lowerClamped()) {
            return Double.isNaN(magnitude) ? linear.min() : Math.max(linear.min(), magnitude);
        }
        if (linear.upperClamped()) {
            return Double.isNaN(magnitude) ? magnitude : Math.min(linear.max(), magnitude);
        }
        return magnitude;
    }

    /// Returns the elements of an unbracketed space list or a singleton value.
    ///
    /// @param value the channel collection
    /// @return the channel elements
    private static List<SassValue> expandSpaceList(SassValue value) {
        if (value instanceof SassList list
                && (list.separator() == ListSeparator.SPACE
                || list.separator() == ListSeparator.UNDECIDED)
                && !list.hasBrackets()) {
            return list.asList();
        }
        return List.of(value);
    }

    /// Returns whether a value is the unquoted CSS `none` keyword.
    ///
    /// @param value the value to inspect
    /// @return whether it represents a missing channel
    private static boolean isNone(SassValue value) {
        return value instanceof SassString string
                && !string.hasQuotes()
                && "none".equalsIgnoreCase(string.text());
    }

    /// Converts a unitless or percentage alpha number to its fractional scale.
    ///
    /// @param number the supplied number
    /// @return the scaled magnitude
    private static double alphaValue(SassNumber number) {
        if (number.isUnitless()) {
            return number.value();
        }
        if (number.numeratorUnits().equals(List.of("%")) && number.denominatorUnits().isEmpty()) {
            return number.value() / 100.0;
        }
        throw new SassValueException(
                "$alpha: Expected " + number + " to have unit \"%\" or no units."
        );
    }

    /// Converts a polar hue number to degrees.
    ///
    /// Unitless values are accepted as degrees. Angle units
    /// ({@code deg}, {@code grad}, {@code rad}, {@code turn}) are coerced.
    /// Other units (including {@code %}) are rejected to match dart-sass.
    ///
    /// @param number the hue channel value
    /// @return the hue in degrees
    private static double hueDegrees(SassNumber number) {
        if (number.isUnitless()) {
            return number.value();
        }
        try {
            return number.coerce(List.of("deg"), List.of()).value();
        } catch (SassValueException exception) {
            throw new SassValueException(
                    "$hue: Expected " + number
                            + " to have an angle unit (deg, grad, rad, turn)."
            );
        }
    }

    /// Clamps a finite value to an inclusive range.
    ///
    /// @param value the value
    /// @param min the lower bound
    /// @param max the upper bound
    /// @return the clamped value
    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    /// Clamps like CSS: {@code NaN} becomes the lower bound.
    private static double clampLikeCss(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return clamp(value, min, max);
    }

    /// Contains channels split around a CSS slash separator.
    ///
    /// @param channels the channels before the slash
    /// @param alpha the value after the slash, or `null`
    private record SlashChannels(
            SassValue channels,
            @Nullable SassValue alpha
    ) {
    }
}
