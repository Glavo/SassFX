// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.color.ColorChannel;
import org.glavo.sassfx.internal.value.color.ColorConversions;
import org.glavo.sassfx.internal.value.color.ColorSpace;
import org.glavo.sassfx.internal.value.color.GamutMapMethod;
import org.glavo.sassfx.internal.value.color.HueInterpolationMethod;
import org.glavo.sassfx.internal.value.color.InterpolationMethod;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Represents an immutable Sass color in a known CSS Color Level 4 space.
///
/// Legacy RGB, HSL, and HWB colors remain fully supported. Modern spaces store
/// their native channels and convert through the Color 4 XYZ hub when needed.
/// The optional source format affects serialization but not value equality or
/// hashing, and is retained only for legacy RGB colors.
@ApiStatus.Internal
@NotNullByDefault
public final class SassColor implements SassValue {
    /// Contains the complete case-insensitive CSS named-color table as packed
    /// RGBA values.
    private static final @Unmodifiable Map<String, Integer> NAMED_RGBA =
            createNamedColors();

    /// Contains the alphabetically first CSS name for each opaque named RGB
    /// value.
    private static final @Unmodifiable Map<Integer, String> CANONICAL_NAMES_BY_RGB =
            createCanonicalNames();

    /// Contains this color's space.
    private final ColorSpace space;

    /// Contains the first channel, or {@code null} when missing.
    private final @Nullable Double channel0;

    /// Contains the second channel, or {@code null} when missing.
    private final @Nullable Double channel1;

    /// Contains the third channel, or {@code null} when missing.
    private final @Nullable Double channel2;

    /// Contains the alpha channel, or {@code null} when missing.
    private final @Nullable Double alpha;

    /// Contains the preferred expanded serialization format for legacy RGB.
    private final @Nullable ColorFormat format;

    /// Creates a color after channel preprocessing for polar spaces.
    ///
    /// @param space the color space
    /// @param channel0 the first channel, or {@code null} when missing
    /// @param channel1 the second channel, or {@code null} when missing
    /// @param channel2 the third channel, or {@code null} when missing
    /// @param alpha the alpha channel between zero and one, or {@code null} when missing
    /// @param format the preferred expanded format, or {@code null}
    private SassColor(
            ColorSpace space,
            @Nullable Double channel0,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double alpha,
            @Nullable ColorFormat format
    ) {
        if (alpha != null && !(alpha >= 0.0 && alpha <= 1.0) && !SassFuzzy.equals(alpha, 0.0)
                && !SassFuzzy.equals(alpha, 1.0)) {
            // Allow fuzzy endpoints while rejecting clearly out-of-range values.
            if (alpha < 0.0 || alpha > 1.0) {
                throw new IllegalArgumentException("alpha must be between 0 and 1");
            }
        }
        if (format != null && space != ColorSpace.RGB) {
            throw new IllegalArgumentException("format is only valid for rgb colors");
        }
        if (space == ColorSpace.LMS) {
            throw new IllegalArgumentException("lms is an internal intermediate space");
        }
        this.space = space;
        this.channel0 = channel0;
        this.channel1 = channel1;
        this.channel2 = channel2;
        this.alpha = alpha;
        this.format = format;
    }

    /// Creates a color in an arbitrary known space after polar-channel normalization.
    ///
    /// @param space the color space
    /// @param channel0 the first channel, or {@code null} when missing
    /// @param channel1 the second channel, or {@code null} when missing
    /// @param channel2 the third channel, or {@code null} when missing
    /// @param alpha the alpha channel, or {@code null} when missing
    /// @return the color
    /// @throws IllegalArgumentException if {@code alpha} is outside its range
    public static SassColor forSpace(
            ColorSpace space,
            @Nullable Double channel0,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double alpha
    ) {
        Objects.requireNonNull(space, "space");
        return switch (space) {
            case HSL -> raw(
                    space,
                    normalizeHue(channel0, channel1 != null && channel1 < 0.0 && !SassFuzzy.equals(channel1, 0.0)),
                    channel1 == null ? null : Math.abs(channel1),
                    channel2,
                    alpha,
                    null
            );
            case HWB -> raw(
                    space,
                    normalizeHue(channel0, false),
                    channel1,
                    channel2,
                    alpha,
                    null
            );
            case LCH, OKLCH -> raw(
                    space,
                    channel0,
                    channel1 == null ? null : Math.abs(channel1),
                    normalizeHue(channel2, channel1 != null && channel1 < 0.0 && !SassFuzzy.equals(channel1, 0.0)),
                    alpha,
                    null
            );
            default -> raw(space, channel0, channel1, channel2, alpha, null);
        };
    }

    /// Creates a color without polar-channel preprocessing.
    private static SassColor raw(
            ColorSpace space,
            @Nullable Double channel0,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double alpha,
            @Nullable ColorFormat format
    ) {
        if (alpha != null) {
            if (Double.isNaN(alpha) || alpha < 0.0 || alpha > 1.0) {
                // Mirror previous strict validation for ordinary constructors.
                if (!(alpha >= 0.0 && alpha <= 1.0)) {
                    throw new IllegalArgumentException("alpha must be between 0 and 1");
                }
            }
        }
        return new SassColor(space, channel0, channel1, channel2, alpha, format);
    }

    /// Creates a color in the legacy RGB color space.
    ///
    /// RGB channels are retained without clamping so later evaluation can
    /// represent out-of-gamut values.
    ///
    /// @param red the red channel
    /// @param green the green channel
    /// @param blue the blue channel
    /// @param alpha the alpha channel between zero and one
    /// @param format the preferred expanded format, or {@code null}
    /// @return the RGB color
    /// @throws IllegalArgumentException if {@code alpha} is outside its range
    public static SassColor rgb(
            double red,
            double green,
            double blue,
            double alpha,
            @Nullable ColorFormat format
    ) {
        return raw(ColorSpace.RGB, red, green, blue, alpha, format);
    }

    /// Creates a color in the legacy RGB color space with optional missing channels.
    ///
    /// @param red the red channel, or {@code null} when missing
    /// @param green the green channel, or {@code null} when missing
    /// @param blue the blue channel, or {@code null} when missing
    /// @param alpha the alpha channel, or {@code null} when missing
    /// @return the RGB color
    public static SassColor rgb(
            @Nullable Double red,
            @Nullable Double green,
            @Nullable Double blue,
            @Nullable Double alpha
    ) {
        return forSpace(ColorSpace.RGB, red, green, blue, alpha);
    }

    /// Creates a color in the legacy HSL color space.
    ///
    /// @param hue the hue in degrees
    /// @param saturation the saturation percentage
    /// @param lightness the lightness percentage
    /// @param alpha the alpha channel between zero and one
    /// @return the HSL color
    /// @throws IllegalArgumentException if {@code alpha} is outside its range
    public static SassColor hsl(
            double hue,
            double saturation,
            double lightness,
            double alpha
    ) {
        return forSpace(ColorSpace.HSL, hue, saturation, lightness, alpha);
    }

    /// Creates a color in the legacy HWB color space.
    ///
    /// @param hue the hue in degrees
    /// @param whiteness the whiteness percentage
    /// @param blackness the blackness percentage
    /// @param alpha the alpha channel between zero and one
    /// @return the HWB color
    /// @throws IllegalArgumentException if {@code alpha} is outside its range
    public static SassColor hwb(
            double hue,
            double whiteness,
            double blackness,
            double alpha
    ) {
        return forSpace(ColorSpace.HWB, hue, whiteness, blackness, alpha);
    }

    /// Creates a color in the OKLab color space.
    ///
    /// @param lightness the lightness channel
    /// @param a the a channel
    /// @param b the b channel
    /// @param alpha the alpha channel between zero and one
    /// @return the OKLab color
    public static SassColor oklab(double lightness, double a, double b, double alpha) {
        return forSpace(ColorSpace.OKLAB, lightness, a, b, alpha);
    }

    /// Creates a color in the OKLCH color space.
    ///
    /// @param lightness the lightness channel
    /// @param chroma the chroma channel
    /// @param hue the hue in degrees
    /// @param alpha the alpha channel between zero and one
    /// @return the OKLCH color
    public static SassColor oklch(double lightness, double chroma, double hue, double alpha) {
        return forSpace(ColorSpace.OKLCH, lightness, chroma, hue, alpha);
    }

    /// Creates a color in the CIE Lab color space.
    ///
    /// @param lightness the lightness channel
    /// @param a the a channel
    /// @param b the b channel
    /// @param alpha the alpha channel between zero and one
    /// @return the Lab color
    public static SassColor lab(double lightness, double a, double b, double alpha) {
        return forSpace(ColorSpace.LAB, lightness, a, b, alpha);
    }

    /// Creates a color in the CIE LCH color space.
    ///
    /// @param lightness the lightness channel
    /// @param chroma the chroma channel
    /// @param hue the hue in degrees
    /// @param alpha the alpha channel between zero and one
    /// @return the LCH color
    public static SassColor lch(double lightness, double chroma, double hue, double alpha) {
        return forSpace(ColorSpace.LCH, lightness, chroma, hue, alpha);
    }

    /// Creates a color in the sRGB color space.
    ///
    /// @param red the red channel
    /// @param green the green channel
    /// @param blue the blue channel
    /// @param alpha the alpha channel between zero and one
    /// @return the sRGB color
    public static SassColor srgb(double red, double green, double blue, double alpha) {
        return forSpace(ColorSpace.SRGB, red, green, blue, alpha);
    }

    /// Creates a color in the XYZ D65 color space.
    ///
    /// @param x the X channel
    /// @param y the Y channel
    /// @param z the Z channel
    /// @param alpha the alpha channel between zero and one
    /// @return the XYZ D65 color
    public static SassColor xyzD65(double x, double y, double z, double alpha) {
        return forSpace(ColorSpace.XYZ_D65, x, y, z, alpha);
    }

    /// Resolves a CSS named color and preserves the supplied source spelling.
    ///
    /// @param name the color name
    /// @param span the source range containing the name
    /// @return the resolved color, or {@code null} if the name is unknown
    public static @Nullable SassColor named(String name, SourceSpan span) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
        @Nullable Integer rgba = NAMED_RGBA.get(name.toLowerCase(Locale.ROOT));
        if (rgba == null) {
            return null;
        }
        return rgb(
                (double) (rgba >>> 24 & 0xff),
                (double) (rgba >>> 16 & 0xff),
                (double) (rgba >>> 8 & 0xff),
                (rgba & 0xff) / 255.0,
                new SpanColorFormat(span)
        );
    }

    /// Returns this color.
    ///
    /// @return this color
    @Override
    public SassColor assertColor() {
        return this;
    }

    /// Returns this color's space.
    ///
    /// @return the color space
    public ColorSpace space() {
        return space;
    }

    /// Returns whether this color uses a legacy RGB, HSL, or HWB space.
    ///
    /// @return whether the color is legacy
    public boolean isLegacy() {
        return space.isLegacy();
    }

    /// Returns the canonical CSS name for this color when one exists.
    ///
    /// Equivalent opaque named colors use the alphabetically first name, and
    /// transparent black uses {@code transparent}. Other colors return
    /// {@code null}.
    ///
    /// @return the canonical name, or {@code null}
    public @Nullable String canonicalName() {
        var rgb = toSpace(ColorSpace.RGB, false);
        if (!canUseHex(rgb)) {
            return null;
        }
        if (SassFuzzy.equals(alpha(), 1.0)) {
            return CANONICAL_NAMES_BY_RGB.get(rgb.packedRgb());
        }
        return SassFuzzy.equals(alpha(), 0.0)
                && rgb.packedRgb() == 0
                ? "transparent"
                : null;
    }

    /// Returns the first channel, treating missing values as zero.
    ///
    /// @return the first channel
    public double channel0() {
        return channel0 != null ? channel0 : 0.0;
    }

    /// Returns the second channel, treating missing values as zero.
    ///
    /// @return the second channel
    public double channel1() {
        return channel1 != null ? channel1 : 0.0;
    }

    /// Returns the third channel, treating missing values as zero.
    ///
    /// @return the third channel
    public double channel2() {
        return channel2 != null ? channel2 : 0.0;
    }

    /// Returns the first channel, or {@code null} when missing.
    ///
    /// @return the first channel or {@code null}
    public @Nullable Double channel0OrNull() {
        return channel0;
    }

    /// Returns the second channel, or {@code null} when missing.
    ///
    /// @return the second channel or {@code null}
    public @Nullable Double channel1OrNull() {
        return channel1;
    }

    /// Returns the third channel, or {@code null} when missing.
    ///
    /// @return the third channel or {@code null}
    public @Nullable Double channel2OrNull() {
        return channel2;
    }

    /// Returns whether the first channel is missing.
    ///
    /// @return whether channel 0 is missing
    public boolean isChannel0Missing() {
        return channel0 == null;
    }

    /// Returns whether the second channel is missing.
    ///
    /// @return whether channel 1 is missing
    public boolean isChannel1Missing() {
        return channel1 == null;
    }

    /// Returns whether the third channel is missing.
    ///
    /// @return whether channel 2 is missing
    public boolean isChannel2Missing() {
        return channel2 == null;
    }

    /// Returns whether any color or alpha channel is missing.
    ///
    /// @return whether a missing channel is present
    public boolean hasMissingChannel() {
        return channel0 == null || channel1 == null || channel2 == null || alpha == null;
    }

    /// Returns the named channel value, treating missing values as zero.
    ///
    /// @param channel the channel name
    /// @return the channel value
    /// @throws SassValueException if the channel name is unknown for this space
    public double channel(String channel) {
        Objects.requireNonNull(channel, "channel");
        var channels = space.channels();
        if (channel.equals(channels.get(0).name())) {
            return channel0();
        }
        if (channel.equals(channels.get(1).name())) {
            return channel1();
        }
        if (channel.equals(channels.get(2).name())) {
            return channel2();
        }
        if ("alpha".equals(channel)) {
            return alpha();
        }
        throw new SassValueException(
                "Color " + this + " doesn't have a channel named \"" + channel + "\"."
        );
    }

    /// Returns whether the named channel is missing.
    ///
    /// @param channel the channel name
    /// @return whether the channel is missing
    /// @throws SassValueException if the channel name is unknown for this space
    public boolean isChannelMissing(String channel) {
        Objects.requireNonNull(channel, "channel");
        var channels = space.channels();
        if (channel.equals(channels.get(0).name())) {
            return channel0 == null;
        }
        if (channel.equals(channels.get(1).name())) {
            return channel1 == null;
        }
        if (channel.equals(channels.get(2).name())) {
            return channel2 == null;
        }
        if ("alpha".equals(channel)) {
            return alpha == null;
        }
        throw new SassValueException(
                "Color " + this + " doesn't have a channel named \"" + channel + "\"."
        );
    }

    /// Returns the red channel of the RGB conversion of this color.
    ///
    /// @return the un-clamped channel value
    /// @throws SassValueException if this color is not legacy
    public double red() {
        return legacyChannel(ColorSpace.RGB, "red");
    }

    /// Returns the green channel of the RGB conversion of this color.
    ///
    /// @return the un-clamped channel value
    /// @throws SassValueException if this color is not legacy
    public double green() {
        return legacyChannel(ColorSpace.RGB, "green");
    }

    /// Returns the blue channel of the RGB conversion of this color.
    ///
    /// @return the un-clamped channel value
    /// @throws SassValueException if this color is not legacy
    public double blue() {
        return legacyChannel(ColorSpace.RGB, "blue");
    }

    /// Returns the alpha channel, treating a missing alpha as zero.
    ///
    /// @return a value between zero and one
    public double alpha() {
        return alpha != null ? alpha : 0.0;
    }

    /// Returns the alpha channel, or {@code null} when missing.
    ///
    /// @return the alpha channel or {@code null}
    public @Nullable Double alphaOrNull() {
        return alpha;
    }

    /// Returns the preferred expanded serialization format.
    ///
    /// @return the format, or {@code null} when no source format is retained
    public @Nullable ColorFormat format() {
        return format;
    }

    /// Returns this color without a retained source spelling.
    ///
    /// Generated transparent colors then serialize as {@code rgba(...)} rather
    /// than the CSS keyword {@code transparent}. Non-source formats and colors
    /// without a format are returned unchanged.
    ///
    /// @return this color, or an equivalent color without {@link SpanColorFormat}
    public SassColor withoutSourceSpelling() {
        if (!(format instanceof SpanColorFormat)) {
            return this;
        }
        return raw(space, channel0, channel1, channel2, alpha, null);
    }

    /// Returns this color's HSL hue in degrees.
    ///
    /// @return the normalized hue between zero inclusive and 360 exclusive
    /// @throws SassValueException if this color is not legacy
    public double hue() {
        return legacyChannel(ColorSpace.HSL, "hue");
    }

    /// Returns this color's HSL saturation as a percentage.
    ///
    /// @return the non-negative saturation percentage
    /// @throws SassValueException if this color is not legacy
    public double saturation() {
        return legacyChannel(ColorSpace.HSL, "saturation");
    }

    /// Returns this color's HSL lightness as a percentage.
    ///
    /// @return the lightness percentage
    /// @throws SassValueException if this color is not legacy
    public double lightness() {
        return legacyChannel(ColorSpace.HSL, "lightness");
    }

    /// Returns this color's HWB whiteness as a percentage.
    ///
    /// @return the whiteness percentage
    /// @throws SassValueException if this color is not legacy
    public double whiteness() {
        return legacyChannel(ColorSpace.HWB, "whiteness");
    }

    /// Returns this color's HWB blackness as a percentage.
    ///
    /// @return the blackness percentage
    /// @throws SassValueException if this color is not legacy
    public double blackness() {
        return legacyChannel(ColorSpace.HWB, "blackness");
    }

    /// Returns whether this color is in-gamut for its space.
    ///
    /// Unbounded spaces always return {@code true}. Missing channels are treated
    /// as zero for the gamut check.
    ///
    /// @return whether every linear channel lies within its conventional range
    public boolean isInGamut() {
        if (!space.isBounded()) {
            return true;
        }
        return isChannelInGamut(channel0(), space.channels().get(0))
                && isChannelInGamut(channel1(), space.channels().get(1))
                && isChannelInGamut(channel2(), space.channels().get(2));
    }

    /// Converts this color to {@code dest}.
    ///
    /// When {@code legacyMissing} is {@code false} and the converted color is
    /// legacy, missing channels are replaced with zero.
    ///
    /// @param dest the destination space
    /// @param legacyMissing whether missing channels may remain on legacy results
    /// @return the converted color
    public SassColor toSpace(ColorSpace dest, boolean legacyMissing) {
        Objects.requireNonNull(dest, "dest");
        if (space == dest) {
            return this;
        }
        var converted = ColorConversions.convert(
                space,
                dest,
                channel0,
                channel1,
                channel2,
                alpha
        );
        var result = forSpace(
                dest,
                converted.channel0(),
                converted.channel1(),
                converted.channel2(),
                converted.alpha()
        );
        // When legacyMissing is false (sniffed legacy adjust/scale/change), fill
        // missing channels with zero so multi-channel adjustments on blacks/greys
        // still succeed, matching dart-sass.
        if (!legacyMissing
                && result.isLegacy()
                && result.hasMissingChannel()) {
            return forSpace(
                    dest,
                    result.channel0OrNull() == null ? 0.0 : result.channel0OrNull(),
                    result.channel1OrNull() == null ? 0.0 : result.channel1OrNull(),
                    result.channel2OrNull() == null ? 0.0 : result.channel2OrNull(),
                    result.alphaOrNull()
            );
        }
        return result;
    }

    /// Converts this color to {@code dest}, preserving missing legacy channels.
    ///
    /// @param dest the destination space
    /// @return the converted color
    public SassColor toSpace(ColorSpace dest) {
        return toSpace(dest, true);
    }

    /// Returns a copy of this color that is in-gamut for its space.
    ///
    /// @param method the gamut-mapping algorithm
    /// @return this color when already in-gamut, otherwise the mapped color
    public SassColor toGamut(GamutMapMethod method) {
        Objects.requireNonNull(method, "method");
        return isInGamut() ? this : method.map(this);
    }

    /// Returns whether the named channel is powerless in this color.
    ///
    /// @param channel the channel name
    /// @return whether the channel is powerless
    /// @throws SassValueException if the channel name is unknown for this space
    public boolean isChannelPowerless(String channel) {
        Objects.requireNonNull(channel, "channel");
        var channels = space.channels();
        if (channel.equals(channels.get(0).name())) {
            return isChannel0Powerless();
        }
        if (channel.equals(channels.get(1).name())) {
            return false;
        }
        if (channel.equals(channels.get(2).name())) {
            return isChannel2Powerless();
        }
        if ("alpha".equals(channel)) {
            return false;
        }
        throw new SassValueException(
                "Color " + this + " doesn't have a channel named \"" + channel + "\"."
        );
    }

    /// Returns whether the first channel is powerless.
    private boolean isChannel0Powerless() {
        return switch (space) {
            case HSL -> SassFuzzy.equals(channel1(), 0.0);
            case HWB -> SassFuzzy.greaterThanOrEquals(channel1() + channel2(), 100.0);
            default -> false;
        };
    }

    /// Returns whether the third channel is powerless.
    private boolean isChannel2Powerless() {
        return switch (space) {
            case LCH, OKLCH -> SassFuzzy.equals(channel1(), 0.0);
            default -> false;
        };
    }

    /// Interpolates this color with {@code other} using a Color Level 4 method.
    ///
    /// @param other the second color
    /// @param method the interpolation method
    /// @param weight the contribution of this color between zero and one
    /// @param legacyMissing whether missing channels may remain on legacy results
    /// @return the interpolated color in this color's original space
    /// @throws IllegalArgumentException if {@code weight} is outside its range
    public SassColor interpolate(
            SassColor other,
            InterpolationMethod method,
            double weight,
            boolean legacyMissing
    ) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(method, "method");
        if (!(weight >= 0.0 && weight <= 1.0)) {
            throw new IllegalArgumentException("weight must be between 0 and 1");
        }
        if (SassFuzzy.equals(weight, 0.0)) {
            return other.toSpace(space, legacyMissing);
        }
        if (SassFuzzy.equals(weight, 1.0)) {
            return this;
        }

        var color1 = toSpace(method.space());
        var color2 = other.toSpace(method.space());
        var missing1_0 = isAnalogousChannelMissing(this, color1, 0);
        var missing1_1 = isAnalogousChannelMissing(this, color1, 1);
        var missing1_2 = isAnalogousChannelMissing(this, color1, 2);
        var missing2_0 = isAnalogousChannelMissing(other, color2, 0);
        var missing2_1 = isAnalogousChannelMissing(other, color2, 1);
        var missing2_2 = isAnalogousChannelMissing(other, color2, 2);

        var channel1_0 = (missing1_0 ? color2 : color1).channel0();
        var channel1_1 = (missing1_1 ? color2 : color1).channel1();
        var channel1_2 = (missing1_2 ? color2 : color1).channel2();
        var channel2_0 = (missing2_0 ? color1 : color2).channel0();
        var channel2_1 = (missing2_1 ? color1 : color2).channel1();
        var channel2_2 = (missing2_2 ? color1 : color2).channel2();
        var alpha1 = alphaOrNull() != null ? alpha() : other.alpha();
        var alpha2 = other.alphaOrNull() != null ? other.alpha() : alpha();

        var thisMultiplier = (alphaOrNull() != null ? alpha() : 1.0) * weight;
        var otherMultiplier = (other.alphaOrNull() != null ? other.alpha() : 1.0) * (1.0 - weight);
        @Nullable Double mixedAlpha = isAlphaMissing() && other.isAlphaMissing()
                ? null
                : alpha1 * weight + alpha2 * (1.0 - weight);
        var denominator = mixedAlpha != null ? mixedAlpha : 1.0;
        @Nullable Double mixed0 = missing1_0 && missing2_0
                ? null
                : (channel1_0 * thisMultiplier + channel2_0 * otherMultiplier) / denominator;
        @Nullable Double mixed1 = missing1_1 && missing2_1
                ? null
                : (channel1_1 * thisMultiplier + channel2_1 * otherMultiplier) / denominator;
        @Nullable Double mixed2 = missing1_2 && missing2_2
                ? null
                : (channel1_2 * thisMultiplier + channel2_2 * otherMultiplier) / denominator;

        SassColor mixed = switch (method.space()) {
            case HSL, HWB -> forSpace(
                    method.space(),
                    missing1_0 && missing2_0
                            ? null
                            : interpolateHues(
                            channel1_0,
                            channel2_0,
                            Objects.requireNonNull(method.hue(), "hue"),
                            weight
                    ),
                    mixed1,
                    mixed2,
                    mixedAlpha
            );
            case LCH, OKLCH -> forSpace(
                    method.space(),
                    mixed0,
                    mixed1,
                    missing1_2 && missing2_2
                            ? null
                            : interpolateHues(
                            channel1_2,
                            channel2_2,
                            Objects.requireNonNull(method.hue(), "hue"),
                            weight
                    ),
                    mixedAlpha
            );
            default -> forSpace(method.space(), mixed0, mixed1, mixed2, mixedAlpha);
        };
        return mixed.toSpace(space, legacyMissing);
    }

    /// Returns whether alpha is missing.
    ///
    /// @return whether this color has no alpha-channel value
    public boolean isAlphaMissing() {
        return alpha == null;
    }

    /// Returns whether {@code output}'s channel should be treated as missing because
    /// an analogous channel was missing on {@code original}.
    private static boolean isAnalogousChannelMissing(
            SassColor original,
            SassColor output,
            int outputChannelIndex
    ) {
        @Nullable Double outputChannel = switch (outputChannelIndex) {
            case 0 -> output.channel0OrNull();
            case 1 -> output.channel1OrNull();
            default -> output.channel2OrNull();
        };
        if (outputChannel == null) {
            return true;
        }
        if (original == output) {
            return false;
        }
        var outputInfo = output.space().channels().get(outputChannelIndex);
        for (var originalChannel : original.space().channels()) {
            if (outputInfo.isAnalogous(originalChannel)
                    && original.isChannelMissing(originalChannel.name())) {
                return true;
            }
        }
        return false;
    }

    /// Interpolates two hues according to {@code method}.
    private static double interpolateHues(
            double hue1,
            double hue2,
            HueInterpolationMethod method,
            double weight
    ) {
        var left = hue1;
        var right = hue2;
        switch (method) {
            case SHORTER -> {
                var delta = right - left;
                if (delta > 180.0) {
                    left += 360.0;
                } else if (delta < -180.0) {
                    right += 360.0;
                }
            }
            case LONGER -> {
                var delta = right - left;
                if (delta > 0.0 && delta < 180.0) {
                    right += 360.0;
                } else if (delta > -180.0 && delta <= 0.0) {
                    left += 360.0;
                }
            }
            case INCREASING -> {
                if (right < left) {
                    right += 360.0;
                }
            }
            case DECREASING -> {
                if (left < right) {
                    left += 360.0;
                }
            }
        }
        return left * weight + right * (1.0 - weight);
    }

    /// Mixes this color with another color using the legacy RGB algorithm.
    ///
    /// Both colors are converted to RGB. Source serialization metadata is not
    /// retained.
    ///
    /// @param other the second color
    /// @param firstWeight the contribution of this color between zero and one
    /// @return the mixed RGB color
    /// @throws IllegalArgumentException if {@code firstWeight} is outside its range
    public SassColor mixedWith(SassColor other, double firstWeight) {
        Objects.requireNonNull(other, "other");
        if (!(firstWeight >= 0.0 && firstWeight <= 1.0)) {
            throw new IllegalArgumentException("firstWeight must be between 0 and 1");
        }
        // Degenerate weights return one input, but drop source spellings so named
        // {@code transparent} becomes generated {@code rgba(0, 0, 0, 0)} while
        // inverted HSL/HWB colors keep their destination space serialization.
        if (SassFuzzy.equals(firstWeight, 1.0)) {
            return withoutSourceSpelling();
        }
        if (SassFuzzy.equals(firstWeight, 0.0)) {
            return other.withoutSourceSpelling();
        }

        var left = toSpace(ColorSpace.RGB, false);
        var right = other.toSpace(ColorSpace.RGB, false);
        var normalizedWeight = firstWeight * 2.0 - 1.0;
        var alphaDistance = left.alpha() - right.alpha();
        var combinedWeight = normalizedWeight * alphaDistance == -1.0
                ? normalizedWeight
                : (normalizedWeight + alphaDistance)
                / (1.0 + normalizedWeight * alphaDistance);
        var weight1 = (combinedWeight + 1.0) / 2.0;
        var weight2 = 1.0 - weight1;
        return rgb(
                left.channel0() * weight1 + right.channel0() * weight2,
                left.channel1() * weight1 + right.channel1() * weight2,
                left.channel2() * weight1 + right.channel2() * weight2,
                left.alpha() * firstWeight + right.alpha() * (1.0 - firstWeight),
                null
        );
    }

    /// Returns the RGB complement of this color without preserving source format.
    ///
    /// Missing RGB channels are rejected because Sass does not yet define how
    /// to invert them.
    ///
    /// @return the color with every RGB channel subtracted from 255
    /// @throws SassValueException if an RGB channel is missing
    public SassColor inverted() {
        var rgb = toSpace(ColorSpace.RGB, false);
        if (rgb.isChannel0Missing()) {
            throw missingChannelInvertError(rgb, "red");
        }
        if (rgb.isChannel1Missing()) {
            throw missingChannelInvertError(rgb, "green");
        }
        if (rgb.isChannel2Missing()) {
            throw missingChannelInvertError(rgb, "blue");
        }
        return SassColor.rgb(
                255.0 - rgb.channel0(),
                255.0 - rgb.channel1(),
                255.0 - rgb.channel2(),
                rgb.alpha(),
                null
        ).toSpace(space, false);
    }

    /// Builds the dart-sass missing-channel diagnostic for invert.
    private static SassValueException missingChannelInvertError(SassColor color, String channel) {
        return new SassValueException(
                "$" + channel + ": Because the CSS working group is still deciding on the "
                        + "best behavior, Sass doesn't currently support modifying missing "
                        + "channels (color: " + color.toCssString() + ")."
        );
    }

    /// Returns this color with zero chroma/saturation in the original space.
    ///
    /// Legacy colors convert through HSL. Modern colors convert through OKLCH so
    /// rectangular and polar Color 4 spaces keep their native appearance.
    ///
    /// @return the grayscale color without source serialization metadata
    public SassColor grayscale() {
        if (isLegacy()) {
            var hsl = toSpace(ColorSpace.HSL, false);
            return forSpace(
                    ColorSpace.HSL,
                    hsl.channel0OrNull(),
                    0.0,
                    hsl.channel2OrNull(),
                    hsl.alphaOrNull()
            ).toSpace(space, false);
        }
        var oklch = toSpace(ColorSpace.OKLCH);
        return forSpace(
                ColorSpace.OKLCH,
                oklch.channel0OrNull(),
                0.0,
                oklch.channel2OrNull(),
                oklch.alphaOrNull()
        ).toSpace(space);
    }

    /// Returns a legacy color with selected HSL channels replaced.
    ///
    /// Unspecified channels keep their current HSL values after conversion.
    ///
    /// @param hue the replacement hue in degrees, or {@code null} to keep the current hue
    /// @param saturation the replacement saturation percentage, or {@code null}
    /// @param lightness the replacement lightness percentage, or {@code null}
    /// @param alpha the replacement alpha, or {@code null}
    /// @return the rewritten RGB color in the HSL-equivalent legacy form
    /// @throws SassValueException if this color is not legacy
    public SassColor changeHsl(
            @Nullable Double hue,
            @Nullable Double saturation,
            @Nullable Double lightness,
            @Nullable Double alpha
    ) {
        if (!isLegacy()) {
            throw new SassValueException(
                    "changeHsl() is only supported for legacy colors. Please use "
                            + "color.change() instead with an explicit $space argument."
            );
        }
        var hsl = toSpace(ColorSpace.HSL, false);
        // Convert back into the original legacy space so CSS serialization keeps
        // the caller's RGB/HWB spelling preferences (names/hex), matching dart-sass.
        return SassColor.hsl(
                hue != null ? hue : hsl.channel0(),
                saturation != null ? saturation : hsl.channel1(),
                lightness != null ? lightness : hsl.channel2(),
                alpha != null ? alpha : hsl.alpha()
        ).toSpace(space, false);
    }

    /// Returns a copy of this color with a replacement alpha channel.
    ///
    /// @param alpha the replacement alpha between zero and one
    /// @return the color with the same space and non-alpha channels
    public SassColor changeAlpha(double alpha) {
        return forSpace(space, channel0, channel1, channel2, alpha);
    }

    /// Returns the HSL complement of this color in the original space.
    ///
    /// @return the color with its hue rotated by 180 degrees
    public SassColor complemented() {
        var hsl = toSpace(ColorSpace.HSL, false);
        return forSpace(
                ColorSpace.HSL,
                hsl.channel0() + 180.0,
                hsl.channel1OrNull(),
                hsl.channel2OrNull(),
                hsl.alphaOrNull()
        ).toSpace(space, false);
    }

    /// Concatenates only with strings; color arithmetic is undefined in modern Sass.
    ///
    /// @param other the right operand
    /// @return the concatenated string when {@code other} is a string
    /// @throws SassValueException when the operand is not a string
    @Override
    public SassValue plus(SassValue other) {
        if (other instanceof SassString) {
            return SassValue.super.plus(other);
        }
        throw undefinedColorOperation("+", other);
    }

    /// Rejects color−color and color−number arithmetic.
    ///
    /// Other operands keep the default hyphen-joined string form used by
    /// legacy slash/list-style pair fixtures.
    ///
    /// @param other the right operand
    /// @return the difference string for non-numeric operands
    /// @throws SassValueException when subtracting a color or number
    @Override
    public SassValue minus(SassValue other) {
        if (other instanceof SassColor || other instanceof SassNumber) {
            throw undefinedColorOperation("-", other);
        }
        return SassValue.super.minus(other);
    }

    /// Rejects color multiplication.
    ///
    /// @param other the right operand
    /// @return never
    /// @throws SassValueException always
    @Override
    public SassValue times(SassValue other) {
        throw undefinedColorOperation("*", other);
    }

    /// Rejects color÷color and color÷number; other operands keep slash-join form.
    ///
    /// Mixed-pair parser fixtures still expect {@code #AAA/itpl}-style slash
    /// strings when the right-hand side is not a color or number.
    ///
    /// @param other the right operand
    /// @return the slash-joined string for non-numeric operands
    /// @throws SassValueException when dividing by a color or number
    @Override
    public SassValue dividedBy(SassValue other) {
        if (other instanceof SassColor || other instanceof SassNumber) {
            throw undefinedColorOperation("/", other);
        }
        return SassValue.super.dividedBy(other);
    }

    /// Rejects color modulo.
    ///
    /// @param other the right operand
    /// @return never
    /// @throws SassValueException always
    @Override
    public SassValue modulo(SassValue other) {
        throw undefinedColorOperation("%", other);
    }

    /// Builds the dart-sass undefined-operation message for this color.
    ///
    /// @param operator the operator spelling
    /// @param other    the right operand
    /// @return the operation failure
    private SassValueException undefinedColorOperation(String operator, SassValue other) {
        return new SassValueException(
                "Undefined operation \"" + this + " " + operator + " " + other + "\"."
        );
    }

    /// Returns a legacy channel after converting this color into {@code targetSpace}.
    private double legacyChannel(ColorSpace targetSpace, String channel) {
        if (!isLegacy()) {
            throw new SassValueException(
                    "color." + channel + "() is only supported for legacy colors. Please use "
                            + "color.channel() instead with an explicit $space argument."
            );
        }
        return toSpace(targetSpace).channel(channel);
    }

    /// Returns whether a linear channel value is inside its conventional gamut.
    private static boolean isChannelInGamut(double value, ColorChannel channel) {
        if (!(channel instanceof ColorChannel.Linear linear)) {
            return true;
        }
        return SassFuzzy.lessThanOrEquals(value, linear.max())
                && SassFuzzy.greaterThanOrEquals(value, linear.min());
    }

    /// Normalizes one hue to the half-open degree range, optionally inverted.
    ///
    /// Matches dart-sass {@code (hue % 360 + 360 + (invert ? 180 : 0)) % 360}.
    /// The extra {@code + 360} before the second modulo is required for bit-exact
    /// agreement on extreme Lab→LCH values, not only for negative-hue wrapping.
    private static @Nullable Double normalizeHue(@Nullable Double hue, boolean invert) {
        if (hue == null) {
            return null;
        }
        var normalized = (hue % 360.0 + 360.0 + (invert ? 180.0 : 0.0)) % 360.0;
        // Canonicalize -0.0 so serialization and equality stay stable.
        return normalized == 0.0 ? 0.0 : normalized;
    }

    /// Compares semantic channels using Sass numeric fuzzy equality while
    /// ignoring source format.
    ///
    /// Legacy colors compare as RGB after conversion. Modern colors compare
    /// channel-for-channel within the same space.
    ///
    /// @param other the object to compare
    /// @return whether the colors are equal under Sass semantics
    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SassColor color)) {
            return false;
        }
        if (isLegacy()) {
            if (!color.isLegacy()) {
                return false;
            }
            if (!SassFuzzy.equalsNullable(alpha, color.alpha)) {
                return false;
            }
            if (space == color.space) {
                return SassFuzzy.equalsNullable(channel0, color.channel0)
                        && SassFuzzy.equalsNullable(channel1, color.channel1)
                        && SassFuzzy.equalsNullable(channel2, color.channel2);
            }
            return toSpace(ColorSpace.RGB).equals(color.toSpace(ColorSpace.RGB));
        }
        return space == color.space
                && SassFuzzy.equalsNullable(channel0, color.channel0)
                && SassFuzzy.equalsNullable(channel1, color.channel1)
                && SassFuzzy.equalsNullable(channel2, color.channel2)
                && SassFuzzy.equalsNullable(alpha, color.alpha);
    }

    /// Returns a fuzzy semantic channel hash that ignores source format.
    ///
    /// @return the color hash
    @Override
    public int hashCode() {
        if (isLegacy()) {
            var rgb = toSpace(ColorSpace.RGB);
            return SassFuzzy.hashCode(rgb.channel0())
                    ^ SassFuzzy.hashCode(rgb.channel1())
                    ^ SassFuzzy.hashCode(rgb.channel2())
                    ^ SassFuzzy.hashCode(alpha());
        }
        return space.hashCode()
                ^ SassFuzzy.hashCode(channel0())
                ^ SassFuzzy.hashCode(channel1())
                ^ SassFuzzy.hashCode(channel2())
                ^ SassFuzzy.hashCode(alpha());
    }

    /// Returns this color's CSS representation for stylesheet emission.
    ///
    /// Expanded CSS follows dart-sass serialization: complete legacy colors use
    /// the shortest compatible form, out-of-gamut legacy colors serialize as
    /// {@code hsl()}/{@code hsla()}, out-of-range Lab-family colors may use
    /// {@code color-mix()}, and channel numbers are truncated to
    /// [SassNumber#PRECISION] digits.
    ///
    /// @return the CSS color text
    @Override
    public String toCssString() {
        return serialize(true);
    }

    /// Returns the inspect-mode Sass representation of this color.
    ///
    /// Source-backed legacy RGB literals retain their original spelling.
    /// Inspect mode preserves full channel precision and does not rewrite
    /// out-of-range Lab-family colors as {@code color-mix()}.
    ///
    /// @return the Sass color source
    @Override
    public String toString() {
        return serialize(false);
    }

    /// Serializes this color for CSS emission or inspect output.
    ///
    /// @param css whether CSS emission rules and precision should be used
    /// @return the serialized color
    private String serialize(boolean css) {
        if (format instanceof SpanColorFormat sourceFormat) {
            return sourceFormat.original();
        }
        if (isLegacy()
                && !isChannel0Missing()
                && !isChannel1Missing()
                && !isChannel2Missing()
                && !isAlphaMissing()) {
            return serializeLegacy(css);
        }
        return switch (space) {
            case RGB -> "rgb("
                    + formatChannel(channel0, null, css) + " "
                    + formatChannel(channel1, null, css) + " "
                    + formatChannel(channel2, null, css)
                    + formatSlashAlpha(css)
                    + ")";
            case HSL, HWB -> space.spaceName() + "("
                    + formatChannel(channel0, "deg", css) + " "
                    + formatChannel(channel1, "%", css) + " "
                    + formatChannel(channel2, "%", css)
                    + formatSlashAlpha(css)
                    + ")";
            case LAB, LCH, OKLAB, OKLCH -> serializeLabFamily(css);
            default -> serializeColorFunction(this, css);
        };
    }

    /// Serializes a complete legacy RGB, HSL, or HWB color.
    ///
    /// @param css whether CSS emission rules should be used
    /// @return the serialized legacy color
    private String serializeLegacy(boolean css) {
        // rgb()/rgba() constructors keep function form even when channels are
        // out of gamut after clamping (dart-sass ColorFormat.rgbFunction).
        if (format == RgbFunctionColorFormat.INSTANCE) {
            return serializeRgbFunction(css);
        }

        // Out-of-gamut colors can only be represented accurately as HSL in CSS.
        if (css && !isInGamut()) {
            return serializeHslFunction(css);
        }

        if (space == ColorSpace.HSL) {
            return serializeHslFunction(css);
        }
        if (!css && space == ColorSpace.HWB) {
            return serializeHwbFunction(css);
        }

        var opaque = SassFuzzy.equals(alpha(), 1.0);

        // Always emit generated transparent colors in rgba format.
        if (opaque) {
            var rgb = toSpace(ColorSpace.RGB, false);
            if (canUseHex(rgb)) {
                var packed = rgb.packedRgb();
                @Nullable String name = CANONICAL_NAMES_BY_RGB.get(packed);
                if (name != null) {
                    return name;
                }
                var result = new StringBuilder("#");
                appendHexByte(result, packed >>> 16);
                appendHexByte(result, packed >>> 8);
                appendHexByte(result, packed);
                return result.toString();
            }
        }

        // HWB that cannot become a named/hex color serializes as HSL so the
        // author's polar intent remains clearer than an RGB triple.
        if (space == ColorSpace.HWB) {
            return serializeHslFunction(css);
        }
        return serializeRgbFunction(css);
    }

    /// Serializes this color as a legacy {@code rgb()}/{@code rgba()} function.
    private String serializeRgbFunction(boolean css) {
        var opaque = SassFuzzy.equals(alpha(), 1.0);
        var rgb = toSpace(ColorSpace.RGB, false);
        return (opaque ? "rgb(" : "rgba(")
                + formatNumber(rgb.channel0(), css) + ", "
                + formatNumber(rgb.channel1(), css) + ", "
                + formatNumber(rgb.channel2(), css)
                + (opaque ? ")" : ", " + formatNumber(alpha(), css) + ")");
    }

    /// Serializes this color as a legacy {@code hsl()}/{@code hsla()} function.
    private String serializeHslFunction(boolean css) {
        var opaque = SassFuzzy.equals(alpha(), 1.0);
        var hsl = toSpace(ColorSpace.HSL, false);
        return (opaque ? "hsl(" : "hsla(")
                + formatChannel(hsl.channel0OrNull(), null, css) + ", "
                + formatChannel(hsl.channel1OrNull(), "%", css) + ", "
                + formatChannel(hsl.channel2OrNull(), "%", css)
                + (opaque ? ")" : ", " + formatNumber(alpha(), css) + ")");
    }

    /// Serializes this color as a modern {@code hwb()} function for inspect mode.
    private String serializeHwbFunction(boolean css) {
        var hwb = toSpace(ColorSpace.HWB, false);
        var builder = new StringBuilder("hwb(")
                .append(formatNumber(hwb.channel0(), css))
                .append(' ')
                .append(formatNumber(hwb.channel1(), css))
                .append("% ")
                .append(formatNumber(hwb.channel2(), css))
                .append('%');
        if (!SassFuzzy.equals(alpha(), 1.0)) {
            builder.append(" / ").append(formatNumber(alpha(), css));
        }
        return builder.append(')').toString();
    }

    /// Serializes Lab, OKLab, LCH, or OKLCH colors, including CSS color-mix fallback.
    private String serializeLabFamily(boolean css) {
        var polar = space == ColorSpace.LCH || space == ColorSpace.OKLCH;
        var lightnessMax = space == ColorSpace.OKLAB || space == ColorSpace.OKLCH ? 1.0 : 100.0;
        var lightnessOutOfRange = channel0 != null
                && !SassFuzzy.inRange(channel0, 0.0, lightnessMax);
        var chromaNegative = polar
                && channel1 != null
                && SassFuzzy.lessThan(channel1, 0.0);

        // color-mix() preserves the authored space for complete out-of-range colors.
        if (css
                && lightnessOutOfRange
                && !isChannel1Missing()
                && !isChannel2Missing()) {
            return "color-mix(in " + space.spaceName() + ", "
                    + serializeColorFunction(toSpace(ColorSpace.XYZ_D65, false), css)
                    + " 100%, black)";
        }
        if (css
                && chromaNegative
                && !isChannel0Missing()
                && !isChannel1Missing()) {
            return "color-mix(in " + space.spaceName() + ", "
                    + serializeColorFunction(toSpace(ColorSpace.XYZ_D65, false), css)
                    + " 100%, black)";
        }

        var builder = new StringBuilder(space.spaceName()).append('(');
        // Relative color syntax for incomplete out-of-range Lab-family colors.
        if (css && (lightnessOutOfRange || chromaNegative)) {
            builder.append("from black ");
        }
        if (channel0 == null) {
            builder.append("none");
        } else {
            var max = ((ColorChannel.Linear) space.channels().get(0)).max();
            builder.append(formatNumber(channel0 * 100.0 / max, css)).append('%');
        }
        builder.append(' ')
                .append(formatChannel(channel1, null, css))
                .append(' ');
        if (polar) {
            builder.append(formatChannel(channel2, "deg", css));
        } else {
            builder.append(formatChannel(channel2, null, css));
        }
        builder.append(formatSlashAlpha(css)).append(')');
        return builder.toString();
    }

    /// Serializes {@code color} using the {@code color()} function syntax.
    private static String serializeColorFunction(SassColor color, boolean css) {
        return "color("
                + color.space.spaceName() + " "
                + formatChannel(color.channel0, null, css) + " "
                + formatChannel(color.channel1, null, css) + " "
                + formatChannel(color.channel2, null, css)
                + color.formatSlashAlpha(css)
                + ")";
    }

    /// Formats an optional alpha suffix using slash syntax.
    private String formatSlashAlpha(boolean css) {
        if (alpha == null) {
            return " / none";
        }
        if (SassFuzzy.equals(alpha, 1.0)) {
            return "";
        }
        return " / " + formatNumber(alpha, css);
    }

    /// Formats one channel that may be missing, optionally with a unit.
    private static String formatChannel(
            @Nullable Double channel,
            @Nullable String unit,
            boolean css
    ) {
        if (channel == null) {
            return "none";
        }
        // Non-finite channel values serialize as calculations so units attach
        // inside calc(...), e.g. calc(NaN * 1%) rather than calc(NaN)%.
        if (!Double.isFinite(channel) && unit != null) {
            return SassNumber.of(channel, unit).toCssString();
        }
        return formatNumber(channel, css) + (unit == null ? "" : unit);
    }

    /// Packs integral in-range RGB channels.
    ///
    /// @return the packed RGB value, or {@code -1} if hexadecimal output is
    /// unavailable
    private int packedRgb() {
        if (!canUseHex(this)) {
            return -1;
        }
        return (int) Math.rint(channel0()) << 16
                | (int) Math.rint(channel1()) << 8
                | (int) Math.rint(channel2());
    }

    /// Returns whether this RGB color can be written as a hex code.
    private static boolean canUseHex(SassColor rgb) {
        return canUseHexForChannel(rgb.channel0())
                && canUseHexForChannel(rgb.channel1())
                && canUseHexForChannel(rgb.channel2());
    }

    /// Returns whether a channel is a fuzzy integer in the byte range.
    private static boolean canUseHexForChannel(double value) {
        return SassFuzzy.isInt(value)
                && SassFuzzy.greaterThanOrEquals(value, 0.0)
                && SassFuzzy.lessThan(value, 256.0);
    }

    /// Appends one lowercase hexadecimal byte.
    private static void appendHexByte(StringBuilder target, int value) {
        target.append(Character.forDigit(value >>> 4 & 0xf, 16));
        target.append(Character.forDigit(value & 0xf, 16));
    }

    /// Formats a color channel for inspect or CSS emission.
    private static String formatNumber(double value, boolean css) {
        return SassNumber.formatNumber(value, css);
    }

    /// Creates the immutable named-color lookup table.
    private static @Unmodifiable Map<String, Integer> createNamedColors() {
        var table = """
                yellowgreen=9ACD32FF
                yellow=FFFF00FF
                whitesmoke=F5F5F5FF
                white=FFFFFFFF
                wheat=F5DEB3FF
                violet=EE82EEFF
                turquoise=40E0D0FF
                transparent=00000000
                tomato=FF6347FF
                thistle=D8BFD8FF
                teal=008080FF
                tan=D2B48CFF
                steelblue=4682B4FF
                springgreen=00FF7FFF
                snow=FFFAFAFF
                slategrey=708090FF
                slategray=708090FF
                slateblue=6A5ACDFF
                skyblue=87CEEBFF
                silver=C0C0C0FF
                sienna=A0522DFF
                seashell=FFF5EEFF
                seagreen=2E8B57FF
                sandybrown=F4A460FF
                salmon=FA8072FF
                saddlebrown=8B4513FF
                royalblue=4169E1FF
                rosybrown=BC8F8FFF
                red=FF0000FF
                rebeccapurple=663399FF
                purple=800080FF
                powderblue=B0E0E6FF
                plum=DDA0DDFF
                pink=FFC0CBFF
                peru=CD853FFF
                peachpuff=FFDAB9FF
                papayawhip=FFEFD5FF
                palevioletred=DB7093FF
                paleturquoise=AFEEEEFF
                palegreen=98FB98FF
                palegoldenrod=EEE8AAFF
                orchid=DA70D6FF
                orangered=FF4500FF
                orange=FFA500FF
                olivedrab=6B8E23FF
                olive=808000FF
                oldlace=FDF5E6FF
                navy=000080FF
                navajowhite=FFDEADFF
                moccasin=FFE4B5FF
                mistyrose=FFE4E1FF
                mintcream=F5FFFAFF
                midnightblue=191970FF
                mediumvioletred=C71585FF
                mediumturquoise=48D1CCFF
                mediumspringgreen=00FA9AFF
                mediumslateblue=7B68EEFF
                mediumseagreen=3CB371FF
                mediumpurple=9370DBFF
                mediumorchid=BA55D3FF
                mediumblue=0000CDFF
                mediumaquamarine=66CDAAFF
                maroon=800000FF
                magenta=FF00FFFF
                linen=FAF0E6FF
                limegreen=32CD32FF
                lime=00FF00FF
                lightyellow=FFFFE0FF
                lightsteelblue=B0C4DEFF
                lightslategrey=778899FF
                lightslategray=778899FF
                lightskyblue=87CEFAFF
                lightseagreen=20B2AAFF
                lightsalmon=FFA07AFF
                lightpink=FFB6C1FF
                lightgrey=D3D3D3FF
                lightgreen=90EE90FF
                lightgray=D3D3D3FF
                lightgoldenrodyellow=FAFAD2FF
                lightcyan=E0FFFFFF
                lightcoral=F08080FF
                lightblue=ADD8E6FF
                lemonchiffon=FFFACDFF
                lawngreen=7CFC00FF
                lavenderblush=FFF0F5FF
                lavender=E6E6FAFF
                khaki=F0E68CFF
                ivory=FFFFF0FF
                indigo=4B0082FF
                indianred=CD5C5CFF
                hotpink=FF69B4FF
                honeydew=F0FFF0FF
                grey=808080FF
                greenyellow=ADFF2FFF
                green=008000FF
                gray=808080FF
                goldenrod=DAA520FF
                gold=FFD700FF
                ghostwhite=F8F8FFFF
                gainsboro=DCDCDCFF
                fuchsia=FF00FFFF
                forestgreen=228B22FF
                floralwhite=FFFAF0FF
                firebrick=B22222FF
                dodgerblue=1E90FFFF
                dimgrey=696969FF
                dimgray=696969FF
                deepskyblue=00BFFFFF
                deeppink=FF1493FF
                darkviolet=9400D3FF
                darkturquoise=00CED1FF
                darkslategrey=2F4F4FFF
                darkslategray=2F4F4FFF
                darkslateblue=483D8BFF
                darkseagreen=8FBC8FFF
                darksalmon=E9967AFF
                darkred=8B0000FF
                darkorchid=9932CCFF
                darkorange=FF8C00FF
                darkolivegreen=556B2FFF
                darkmagenta=8B008BFF
                darkkhaki=BDB76BFF
                darkgrey=A9A9A9FF
                darkgreen=006400FF
                darkgray=A9A9A9FF
                darkgoldenrod=B8860BFF
                darkcyan=008B8BFF
                darkblue=00008BFF
                cyan=00FFFFFF
                crimson=DC143CFF
                cornsilk=FFF8DCFF
                cornflowerblue=6495EDFF
                coral=FF7F50FF
                chocolate=D2691EFF
                chartreuse=7FFF00FF
                cadetblue=5F9EA0FF
                burlywood=DEB887FF
                brown=A52A2AFF
                blueviolet=8A2BE2FF
                blue=0000FFFF
                blanchedalmond=FFEBCDFF
                black=000000FF
                bisque=FFE4C4FF
                beige=F5F5DCFF
                azure=F0FFFFFF
                aquamarine=7FFFD4FF
                aqua=00FFFFFF
                antiquewhite=FAEBD7FF
                aliceblue=F0F8FFFF
                """;

        var result = new HashMap<String, Integer>();
        for (var entry : table.split("\n")) {
            var separator = entry.indexOf('=');
            var name = entry.substring(0, separator);
            var rgba = (int) Long.parseLong(entry.substring(separator + 1), 16);
            result.put(name, rgba);
        }
        if (result.size() != 149) {
            throw new ExceptionInInitializerError("named-color table is incomplete");
        }
        return Map.copyOf(result);
    }

    /// Creates the canonical reverse lookup for opaque named colors.
    private static @Unmodifiable Map<Integer, String> createCanonicalNames() {
        var result = new HashMap<Integer, String>();
        for (var entry : NAMED_RGBA.entrySet()) {
            var rgba = entry.getValue();
            if ((rgba & 0xff) != 0xff) {
                continue;
            }
            result.merge(
                    rgba >>> 8,
                    entry.getKey(),
                    (first, second) -> first.compareTo(second) <= 0 ? first : second
            );
        }
        return Map.copyOf(result);
    }
}
