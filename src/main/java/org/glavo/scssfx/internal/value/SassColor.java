// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Represents an immutable Sass color in the legacy RGB color space.
///
/// The optional source format affects serialization but not value equality or
/// hashing.
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

    /// Contains the red channel.
    private final double red;

    /// Contains the green channel.
    private final double green;

    /// Contains the blue channel.
    private final double blue;

    /// Contains the alpha channel.
    private final double alpha;

    /// Contains the preferred expanded serialization format.
    private final @Nullable ColorFormat format;

    /// Creates an RGB color after its alpha channel has been validated.
    ///
    /// @param red the red channel
    /// @param green the green channel
    /// @param blue the blue channel
    /// @param alpha the alpha channel between zero and one
    /// @param format the preferred expanded format, or {@code null}
    private SassColor(
            double red,
            double green,
            double blue,
            double alpha,
            @Nullable ColorFormat format
    ) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        this.format = format;
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
    /// or is not a number
    public static SassColor rgb(
            double red,
            double green,
            double blue,
            double alpha,
            @Nullable ColorFormat format
    ) {
        if (!(alpha >= 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("alpha must be between 0 and 1");
        }
        return new SassColor(red, green, blue, alpha, format);
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
                rgba >>> 24 & 0xff,
                rgba >>> 16 & 0xff,
                rgba >>> 8 & 0xff,
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

    /// Returns the red channel.
    ///
    /// @return the un-clamped channel value
    public double red() {
        return red;
    }

    /// Returns the green channel.
    ///
    /// @return the un-clamped channel value
    public double green() {
        return green;
    }

    /// Returns the blue channel.
    ///
    /// @return the un-clamped channel value
    public double blue() {
        return blue;
    }

    /// Returns the alpha channel.
    ///
    /// @return a value between zero and one
    public double alpha() {
        return alpha;
    }

    /// Returns the preferred expanded serialization format.
    ///
    /// @return the format, or {@code null} when no source format is retained
    public @Nullable ColorFormat format() {
        return format;
    }

    /// Returns this color's legacy HSL hue in degrees.
    ///
    /// Achromatic colors return {@code 0} because the legacy RGB model has no
    /// separate missing-hue state.
    ///
    /// @return the normalized hue between zero inclusive and 360 exclusive
    public double hue() {
        var normalizedRed = red / 255.0;
        var normalizedGreen = green / 255.0;
        var normalizedBlue = blue / 255.0;
        var saturation = hslSaturation(normalizedRed, normalizedGreen, normalizedBlue);
        if (SassFuzzy.equals(saturation, 0.0)) {
            return 0.0;
        }
        var hue = hslHue(normalizedRed, normalizedGreen, normalizedBlue);
        if (saturation < 0.0) {
            hue += 180.0;
        }
        return normalizeHue(hue);
    }

    /// Returns this color's legacy HSL saturation as a percentage.
    ///
    /// @return the non-negative saturation percentage
    public double saturation() {
        return Math.abs(hslSaturation(red / 255.0, green / 255.0, blue / 255.0) * 100.0);
    }

    /// Returns this color's legacy HSL lightness as a percentage.
    ///
    /// @return the lightness percentage
    public double lightness() {
        return hslLightness(red / 255.0, green / 255.0, blue / 255.0) * 100.0;
    }

    /// Mixes this color with another legacy RGB color.
    ///
    /// The resulting RGB channel weights account for the alpha difference,
    /// while alpha itself is interpolated directly using {@code firstWeight}.
    /// Source serialization metadata is not retained.
    ///
    /// @param other the second color
    /// @param firstWeight the contribution of this color between zero and one
    /// @return the mixed color
    /// @throws IllegalArgumentException if {@code firstWeight} is outside its range
    public SassColor mixedWith(SassColor other, double firstWeight) {
        Objects.requireNonNull(other, "other");
        if (!(firstWeight >= 0.0 && firstWeight <= 1.0)) {
            throw new IllegalArgumentException("firstWeight must be between 0 and 1");
        }

        var normalizedWeight = firstWeight * 2.0 - 1.0;
        var alphaDistance = alpha - other.alpha;
        var combinedWeight = normalizedWeight * alphaDistance == -1.0
                ? normalizedWeight
                : (normalizedWeight + alphaDistance)
                / (1.0 + normalizedWeight * alphaDistance);
        var weight1 = (combinedWeight + 1.0) / 2.0;
        var weight2 = 1.0 - weight1;
        return rgb(
                red * weight1 + other.red * weight2,
                green * weight1 + other.green * weight2,
                blue * weight1 + other.blue * weight2,
                alpha * firstWeight + other.alpha * (1.0 - firstWeight),
                null
        );
    }

    /// Returns the RGB complement of this color without preserving source format.
    ///
    /// @return the color with every RGB channel subtracted from 255
    public SassColor inverted() {
        return rgb(255.0 - red, 255.0 - green, 255.0 - blue, alpha, null);
    }

    /// Returns this color with zero legacy HSL saturation.
    ///
    /// @return the grayscale color without source serialization metadata
    public SassColor grayscale() {
        return fromHsl(hue(), 0.0, lightness(), alpha);
    }

    /// Returns the legacy HSL complement of this color.
    ///
    /// @return the color with its hue rotated by 180 degrees
    public SassColor complemented() {
        return fromHsl(hue() + 180.0, saturation(), lightness(), alpha);
    }

    /// Converts legacy HSL channels to an RGB color.
    ///
    /// @param hue the hue in degrees
    /// @param saturation the saturation percentage
    /// @param lightness the lightness percentage
    /// @param alpha the alpha channel
    /// @return a format-free RGB color
    private static SassColor fromHsl(
            double hue,
            double saturation,
            double lightness,
            double alpha
    ) {
        var normalizedHue = normalizeHue(hue) / 360.0;
        var normalizedSaturation = saturation / 100.0;
        var normalizedLightness = lightness / 100.0;
        var secondMultiplier = normalizedLightness <= 0.5
                ? normalizedLightness * (normalizedSaturation + 1.0)
                : normalizedLightness + normalizedSaturation
                - normalizedLightness * normalizedSaturation;
        var firstMultiplier = normalizedLightness * 2.0 - secondMultiplier;
        return rgb(
                normalizeRgbEndpoint(
                        hslHueToRgb(firstMultiplier, secondMultiplier, normalizedHue + 1.0 / 3.0)
                                * 255.0
                ),
                normalizeRgbEndpoint(hslHueToRgb(firstMultiplier, secondMultiplier, normalizedHue) * 255.0),
                normalizeRgbEndpoint(
                        hslHueToRgb(firstMultiplier, secondMultiplier, normalizedHue - 1.0 / 3.0)
                                * 255.0
                ),
                alpha,
                null
        );
    }

    /// Returns the legacy HSL hue computed from normalized RGB channels.
    ///
    /// @param red the normalized red channel
    /// @param green the normalized green channel
    /// @param blue the normalized blue channel
    /// @return the unnormalized hue in degrees
    private static double hslHue(double red, double green, double blue) {
        var maximum = Math.max(Math.max(red, green), blue);
        var minimum = Math.min(Math.min(red, green), blue);
        var delta = maximum - minimum;
        if (delta == 0.0) {
            return 0.0;
        }
        if (maximum == red) {
            return 60.0 * (green - blue) / delta + 360.0;
        }
        if (maximum == green) {
            return 60.0 * (blue - red) / delta + 120.0;
        }
        return 60.0 * (red - green) / delta + 240.0;
    }

    /// Returns the legacy HSL saturation computed from normalized RGB channels.
    ///
    /// @param red the normalized red channel
    /// @param green the normalized green channel
    /// @param blue the normalized blue channel
    /// @return the signed saturation fraction
    private static double hslSaturation(double red, double green, double blue) {
        var maximum = Math.max(Math.max(red, green), blue);
        var minimum = Math.min(Math.min(red, green), blue);
        var lightness = (minimum + maximum) / 2.0;
        if (lightness == 0.0 || lightness == 1.0) {
            return 0.0;
        }
        return (maximum - lightness) / Math.min(lightness, 1.0 - lightness);
    }

    /// Returns the legacy HSL lightness computed from normalized RGB channels.
    ///
    /// @param red the normalized red channel
    /// @param green the normalized green channel
    /// @param blue the normalized blue channel
    /// @return the lightness fraction
    private static double hslLightness(double red, double green, double blue) {
        return (Math.min(Math.min(red, green), blue)
                + Math.max(Math.max(red, green), blue)) / 2.0;
    }

    /// Normalizes one hue to the half-open legacy HSL degree range.
    ///
    /// @param hue the hue to normalize
    /// @return a hue between zero inclusive and 360 exclusive
    private static double normalizeHue(double hue) {
        var normalized = hue % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        return normalized == 0.0 ? 0.0 : normalized;
    }

    /// Converts one normalized hue position to a normalized RGB channel.
    ///
    /// @param firstMultiplier the lower HSL conversion multiplier
    /// @param secondMultiplier the upper HSL conversion multiplier
    /// @param hue the normalized hue position
    /// @return the normalized RGB channel
    private static double hslHueToRgb(
            double firstMultiplier,
            double secondMultiplier,
            double hue
    ) {
        if (hue < 0.0) {
            hue += 1.0;
        }
        if (hue > 1.0) {
            hue -= 1.0;
        }
        if (hue < 1.0 / 6.0) {
            return firstMultiplier + (secondMultiplier - firstMultiplier) * hue * 6.0;
        }
        if (hue < 1.0 / 2.0) {
            return secondMultiplier;
        }
        if (hue < 2.0 / 3.0) {
            return firstMultiplier + (secondMultiplier - firstMultiplier)
                    * (2.0 / 3.0 - hue) * 6.0;
        }
        return firstMultiplier;
    }

    /// Normalizes RGB endpoints that differ only by Sass numeric fuzziness.
    ///
    /// @param channel the computed RGB channel
    /// @return zero or 255 for a fuzzy-equal endpoint, otherwise {@code channel}
    private static double normalizeRgbEndpoint(double channel) {
        if (SassFuzzy.equals(channel, 0.0)) {
            return 0.0;
        }
        if (SassFuzzy.equals(channel, 255.0)) {
            return 255.0;
        }
        return channel;
    }

    /// Compares semantic channels using Sass numeric fuzzy equality while
    /// ignoring source format.
    ///
    /// @param other the object to compare
    /// @return whether all four channels are equal
    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SassColor color
                && SassFuzzy.equals(red, color.red)
                && SassFuzzy.equals(green, color.green)
                && SassFuzzy.equals(blue, color.blue)
                && SassFuzzy.equals(alpha, color.alpha);
    }

    /// Returns a fuzzy semantic channel hash that ignores source format.
    ///
    /// @return the color hash
    @Override
    public int hashCode() {
        return SassFuzzy.hashCode(red)
                ^ SassFuzzy.hashCode(green)
                ^ SassFuzzy.hashCode(blue)
                ^ SassFuzzy.hashCode(alpha);
    }

    /// Returns the inspect-mode Sass representation of this color.
    ///
    /// Source-backed literals retain their original spelling. Colors without
    /// a retained format use a canonical name, hexadecimal spelling, or RGB
    /// function.
    ///
    /// @return the Sass color source
    @Override
    public String toString() {
        if (format instanceof SpanColorFormat sourceFormat) {
            return sourceFormat.original();
        }

        var packedRgb = packedRgb();
        var opaque = Double.compare(alpha, 1.0) == 0;
        if (opaque && packedRgb >= 0) {
            @Nullable String name = CANONICAL_NAMES_BY_RGB.get(packedRgb);
            if (name != null) {
                return name;
            }
            var result = new StringBuilder("#");
            appendHexByte(result, packedRgb >>> 16);
            appendHexByte(result, packedRgb >>> 8);
            appendHexByte(result, packedRgb);
            return result.toString();
        }

        return (opaque ? "rgb(" : "rgba(")
                + formatNumber(red) + ", "
                + formatNumber(green) + ", "
                + formatNumber(blue)
                + (opaque ? ")" : ", " + formatNumber(alpha) + ")");
    }

    /// Packs integral in-range RGB channels.
    ///
    /// @return the packed RGB value, or {@code -1} if hexadecimal output is
    /// unavailable
    private int packedRgb() {
        if (!isByte(red) || !isByte(green) || !isByte(blue)) {
            return -1;
        }
        return (int) red << 16 | (int) green << 8 | (int) blue;
    }

    /// Returns whether a channel is an integer in the byte range.
    ///
    /// @param value the channel to test
    /// @return whether the channel can be emitted as one hexadecimal byte
    private static boolean isByte(double value) {
        return value >= 0.0 && value < 256.0 && value == Math.rint(value);
    }

    /// Appends one lowercase hexadecimal byte.
    ///
    /// @param target the destination
    /// @param value the value whose low byte is appended
    private static void appendHexByte(StringBuilder target, int value) {
        target.append(Character.forDigit(value >>> 4 & 0xf, 16));
        target.append(Character.forDigit(value & 0xf, 16));
    }

    /// Formats a literal color channel without an unnecessary decimal suffix.
    ///
    /// @param value the channel value
    /// @return the channel text
    private static String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        var decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        return decimal.signum() == 0 ? "0" : decimal.toPlainString();
    }

    /// Creates the immutable named-color lookup table.
    ///
    /// @return lowercase names mapped to packed RGBA values
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
    ///
    /// @return packed RGB values mapped to their alphabetically first names
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
