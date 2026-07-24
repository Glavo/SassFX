// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value.color;

import org.glavo.scssfx.internal.value.SassFuzzy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Conversion helpers and matrices for CSS Color Level 4 color spaces.
///
/// Algorithms and matrix values follow the CSS Color 4 specification and the
/// Dart Sass 1.101.3 reference implementation.
@ApiStatus.Internal
@NotNullByDefault
public final class ColorConversions {
    /// The D50 white point components.
    static final double[] D50 = {
            0.3457 / 0.3585,
            1.00000,
            (1.0 - 0.3457 - 0.3585) / 0.3585
    };

    /// Lab conversion kappa constant.
    static final double LAB_KAPPA = 24389.0 / 27.0;

    /// Lab conversion epsilon constant.
    static final double LAB_EPSILON = 216.0 / 24389.0;

    private static final double[] LMS_TO_OKLAB = {
            0.21045426830931400, 0.79361777470230540, -0.00407204301161930,
            1.97799853243116840, -2.42859224204858000, 0.45059370961741100,
            0.02590404246554780, 0.78277171245752960, -0.80867575492307740
    };

    private static final double[] OKLAB_TO_LMS = {
            1.00000000000000020, 0.39633777737617490, 0.21580375730991360,
            0.99999999999999980, -0.10556134581565854, -0.06385417282581334,
            0.99999999999999990, -0.08948417752981180, -1.29148554801940940
    };

    private static final double[] LINEAR_SRGB_TO_XYZ_D65 = {
            0.41239079926595950, 0.35758433938387796, 0.18048078840183430,
            0.21263900587151036, 0.71516867876775590, 0.07219231536073371,
            0.01933081871559185, 0.11919477979462598, 0.95053215224966060
    };

    private static final double[] XYZ_D65_TO_LINEAR_SRGB = {
            3.24096994190452130, -1.53738317757009350, -0.49861076029300330,
            -0.96924363628087980, 1.87596750150772060, 0.04155505740717561,
            0.05563007969699360, -0.20397695888897657, 1.05697151424287860
    };

    private static final double[] LINEAR_DISPLAY_P3_TO_XYZ_D65 = {
            0.48657094864821626, 0.26566769316909294, 0.19821728523436250,
            0.22897456406974884, 0.69173852183650620, 0.07928691409374500,
            0.00000000000000000, 0.04511338185890257, 1.04394436890097570
    };

    private static final double[] XYZ_D65_TO_LINEAR_DISPLAY_P3 = {
            2.49349691194142450, -0.93138361791912360, -0.40271078445071684,
            -0.82948896956157490, 1.76266406031834680, 0.02362468584194359,
            0.03584583024378433, -0.07617238926804170, 0.95688452400768730
    };

    private static final double[] LINEAR_A98_RGB_TO_XYZ_D65 = {
            0.57666904291013080, 0.18555823790654627, 0.18822864623499472,
            0.29734497525053616, 0.62736356625546600, 0.07529145849399789,
            0.02703136138641237, 0.07068885253582714, 0.99133753683763890
    };

    private static final double[] XYZ_D65_TO_LINEAR_A98_RGB = {
            2.04158790381074600, -0.56500697427885960, -0.34473135077832950,
            -0.96924363628087980, 1.87596750150772060, 0.04155505740717561,
            0.01344428063203102, -0.11836239223101823, 1.01517499439120540
    };

    private static final double[] LINEAR_REC2020_TO_XYZ_D65 = {
            0.63695804830129130, 0.14461690358620838, 0.16888097516417205,
            0.26270021201126703, 0.67799807151887100, 0.05930171646986194,
            0.00000000000000000, 0.02807269304908750, 1.06098505771079090
    };

    private static final double[] XYZ_D65_TO_LINEAR_REC2020 = {
            1.71665118797126760, -0.35567078377639240, -0.25336628137365980,
            -0.66668435183248900, 1.61648123663493900, 0.01576854581391113,
            0.01763985744531091, -0.04277061325780865, 0.94210312123547400
    };

    private static final double[] LINEAR_PROPHOTO_RGB_TO_XYZ_D65 = {
            0.75559074229692100, 0.11271984265940525, 0.08214534209534540,
            0.26832184357857190, 0.71511525666179120, 0.01656289975963685,
            0.00391597276242580, -0.01293344283684181, 1.09807522083429450
    };

    private static final double[] XYZ_D65_TO_LINEAR_PROPHOTO_RGB = {
            1.40319046337749790, -0.22301514479051668, -0.10160668507413790,
            -0.52623840216330720, 1.48163196292346440, 0.01701879027252688,
            -0.01120226528622150, 0.01824640347962099, 0.91124722749150480
    };

    private static final double[] XYZ_D65_TO_XYZ_D50 = {
            1.04792979254499660, 0.02294687060160952, -0.05019226628920519,
            0.02962780877005567, 0.99043442675388000, -0.01707379906341879,
            -0.00924304064620452, 0.01505519149029816, 0.75187428142813700
    };

    private static final double[] XYZ_D50_TO_XYZ_D65 = {
            0.95547342148807520, -0.02309845494876452, 0.06325924320057065,
            -0.02836970933386358, 1.00999539808130410, 0.02104144119191730,
            0.01231401486448199, -0.02050764929889898, 1.33036592624212400
    };

    private static final double[] XYZ_D65_TO_LMS = {
            0.81902243799670300, 0.36190626005289034, -0.12887378152098788,
            0.03298365393238846, 0.92928686158634330, 0.03614466635064235,
            0.04817718935962420, 0.26423953175273080, 0.63354782846943080
    };

    private static final double[] LMS_TO_XYZ_D65 = {
            1.22687987584592430, -0.55781499446021710, 0.28139104566596460,
            -0.04057574521480084, 1.11228680328031730, -0.07171105806551635,
            -0.07637293667466007, -0.42149333240224324, 1.58692401983678180
    };

    /// Direct LMS → linear sRGB matrix from dart-sass (avoids LMS→XYZ→sRGB drift).
    private static final double[] LMS_TO_LINEAR_SRGB = {
            4.07674163607595800, -3.30771153925806200, 0.23096990318210417,
            -1.26843797328503200, 2.60975734928768900, -0.34131937600265710,
            -0.00419607613867551, -0.70341861793593630, 1.70761469407461200
    };

    private static final double REC2020_ALPHA = 1.09929682680944;
    private static final double REC2020_BETA = 0.018053968510807;

    /// Prevents instantiation.
    private ColorConversions() {
    }

    /// Converts one color from {@code source} into {@code dest}.
    ///
    /// Missing channels are treated as zero for the conversion arithmetic and
    /// remain missing in the destination when the corresponding source channel
    /// was missing and the destination is a linear RGB or XYZ space that maps
    /// channels positionally. Polar and Lab conversions apply the CSS Color 4
    /// powerless-component rules for hue and chroma. Legacy RGB/HSL/HWB and
    /// unit-interval sRGB conversions use direct algorithms so common legacy
    /// round-trips retain Sass-compatible endpoints.
    ///
    /// @param source the source space
    /// @param dest the destination space
    /// @param channel0 the first channel, or {@code null} when missing
    /// @param channel1 the second channel, or {@code null} when missing
    /// @param channel2 the third channel, or {@code null} when missing
    /// @param alpha the alpha channel, or {@code null} when missing
    /// @return the converted channels and alpha
    public static ConvertedChannels convert(
            ColorSpace source,
            ColorSpace dest,
            @Nullable Double channel0,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double alpha
    ) {
        if (source == dest) {
            return new ConvertedChannels(channel0, channel1, channel2, alpha);
        }

        // Keep legacy and sRGB exchanges on the short path used by Dart Sass.
        if (source == ColorSpace.RGB && dest == ColorSpace.SRGB) {
            return scaleRgb(channel0, channel1, channel2, alpha, 1.0 / 255.0);
        }
        if (source == ColorSpace.SRGB && dest == ColorSpace.RGB) {
            return scaleRgb(channel0, channel1, channel2, alpha, 255.0);
        }
        if (source == ColorSpace.RGB && (dest == ColorSpace.HSL || dest == ColorSpace.HWB)) {
            return srgbToPolar(
                    dest,
                    (channel0 != null ? channel0 : 0.0) / 255.0,
                    (channel1 != null ? channel1 : 0.0) / 255.0,
                    (channel2 != null ? channel2 : 0.0) / 255.0,
                    alpha
            );
        }
        if (source == ColorSpace.SRGB && (dest == ColorSpace.HSL || dest == ColorSpace.HWB)) {
            return srgbToPolar(
                    dest,
                    channel0 != null ? channel0 : 0.0,
                    channel1 != null ? channel1 : 0.0,
                    channel2 != null ? channel2 : 0.0,
                    alpha
            );
        }

        return switch (source) {
            case HSL -> convertFromHsl(dest, channel0, channel1, channel2, alpha);
            case HWB -> convertFromHwb(dest, channel0, channel1, channel2, alpha);
            case LAB -> convertFromLab(dest, channel0, channel1, channel2, alpha);
            case LCH -> convertFromLch(dest, channel0, channel1, channel2, alpha);
            case OKLAB -> convertFromOklab(dest, channel0, channel1, channel2, alpha);
            case OKLCH -> convertFromOklch(dest, channel0, channel1, channel2, alpha);
            default -> convertLinearHub(source, dest, channel0, channel1, channel2, alpha);
        };
    }

    /// Scales RGB-like channels by a constant factor while preserving missingness.
    private static ConvertedChannels scaleRgb(
            @Nullable Double channel0,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double alpha,
            double factor
    ) {
        return new ConvertedChannels(
                channel0 == null ? null : channel0 * factor,
                channel1 == null ? null : channel1 * factor,
                channel2 == null ? null : channel2 * factor,
                alpha
        );
    }

    /// Converts from a linear-capable source space through the XYZ-D65 hub.
    private static ConvertedChannels convertLinearHub(
            ColorSpace source,
            ColorSpace dest,
            @Nullable Double channel0,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double alpha
    ) {
        var missing0 = channel0 == null;
        var missing1 = channel1 == null;
        var missing2 = channel2 == null;
        double[] xyz = toXyzD65(source, channel0 != null ? channel0 : 0.0,
                channel1 != null ? channel1 : 0.0,
                channel2 != null ? channel2 : 0.0);
        return fromXyzD65(dest, xyz[0], xyz[1], xyz[2], alpha, missing0, missing1, missing2);
    }

    /// Converts channels from HSL into {@code dest}.
    private static ConvertedChannels convertFromHsl(
            ColorSpace dest,
            @Nullable Double hue,
            @Nullable Double saturation,
            @Nullable Double lightness,
            @Nullable Double alpha
    ) {
        var missingHue = hue == null;
        var missingChroma = saturation == null;
        var missingLightness = lightness == null;
        var scaledHue = ((hue != null ? hue : 0.0) / 360.0) % 1.0;
        if (scaledHue < 0.0) {
            scaledHue += 1.0;
        }
        var scaledSaturation = (saturation != null ? saturation : 0.0) / 100.0;
        var scaledLightness = (lightness != null ? lightness : 0.0) / 100.0;
        var m2 = scaledLightness <= 0.5
                ? scaledLightness * (scaledSaturation + 1.0)
                : scaledLightness + scaledSaturation - scaledLightness * scaledSaturation;
        var m1 = scaledLightness * 2.0 - m2;
        var red = hueToRgb(m1, m2, scaledHue + 1.0 / 3.0);
        var green = hueToRgb(m1, m2, scaledHue);
        var blue = hueToRgb(m1, m2, scaledHue - 1.0 / 3.0);
        if (dest == ColorSpace.SRGB) {
            return new ConvertedChannels(red, green, blue, alpha);
        }
        if (dest == ColorSpace.RGB) {
            return new ConvertedChannels(
                    normalizeRgbEndpoint(red * 255.0),
                    normalizeRgbEndpoint(green * 255.0),
                    normalizeRgbEndpoint(blue * 255.0),
                    alpha
            );
        }
        if (dest == ColorSpace.HWB) {
            return srgbToPolar(ColorSpace.HWB, red, green, blue, alpha);
        }
        if (dest == ColorSpace.HSL) {
            return new ConvertedChannels(hue, saturation, lightness, alpha);
        }
        return convertSrgbWithMissing(
                dest,
                red,
                green,
                blue,
                alpha,
                missingLightness,
                missingChroma,
                missingHue
        );
    }

    /// Converts channels from HWB into {@code dest}.
    private static ConvertedChannels convertFromHwb(
            ColorSpace dest,
            @Nullable Double hue,
            @Nullable Double whiteness,
            @Nullable Double blackness,
            @Nullable Double alpha
    ) {
        var scaledHue = ((hue != null ? hue : 0.0) % 360.0) / 360.0;
        if (scaledHue < 0.0) {
            scaledHue += 1.0;
        }
        var scaledWhiteness = (whiteness != null ? whiteness : 0.0) / 100.0;
        var scaledBlackness = (blackness != null ? blackness : 0.0) / 100.0;
        var sum = scaledWhiteness + scaledBlackness;
        if (sum > 1.0) {
            scaledWhiteness /= sum;
            scaledBlackness /= sum;
        }
        var factor = 1.0 - scaledWhiteness - scaledBlackness;
        var red = hueToRgb(0.0, 1.0, scaledHue + 1.0 / 3.0) * factor + scaledWhiteness;
        var green = hueToRgb(0.0, 1.0, scaledHue) * factor + scaledWhiteness;
        var blue = hueToRgb(0.0, 1.0, scaledHue - 1.0 / 3.0) * factor + scaledWhiteness;
        if (dest == ColorSpace.SRGB) {
            return new ConvertedChannels(red, green, blue, alpha);
        }
        if (dest == ColorSpace.RGB) {
            return new ConvertedChannels(
                    normalizeRgbEndpoint(red * 255.0),
                    normalizeRgbEndpoint(green * 255.0),
                    normalizeRgbEndpoint(blue * 255.0),
                    alpha
            );
        }
        if (dest == ColorSpace.HSL) {
            var polar = srgbToPolar(ColorSpace.HSL, red, green, blue, alpha);
            // Preserve missing HWB hue as missing HSL hue.
            return new ConvertedChannels(
                    hue == null ? null : polar.channel0(),
                    polar.channel1(),
                    polar.channel2(),
                    alpha
            );
        }
        return convertSrgbWithMissing(
                dest,
                red,
                green,
                blue,
                alpha,
                false,
                false,
                hue == null
        );
    }

    /// Normalizes RGB endpoints that differ only by Sass numeric fuzziness.
    private static double normalizeRgbEndpoint(double channel) {
        if (SassFuzzy.equals(channel, 0.0)) {
            return 0.0;
        }
        if (SassFuzzy.equals(channel, 255.0)) {
            return 255.0;
        }
        return channel;
    }

    /// Converts channels from Lab into {@code dest}.
    private static ConvertedChannels convertFromLab(
            ColorSpace dest,
            @Nullable Double lightness,
            @Nullable Double a,
            @Nullable Double b,
            @Nullable Double alpha
    ) {
        return convertFromLab(
                dest,
                lightness,
                a,
                b,
                alpha,
                false,
                false
        );
    }

    /// Converts channels from Lab into {@code dest}, optionally marking polar
    /// missingness that originated in LCH/OKLCH.
    private static ConvertedChannels convertFromLab(
            ColorSpace dest,
            @Nullable Double lightness,
            @Nullable Double a,
            @Nullable Double b,
            @Nullable Double alpha,
            boolean missingChroma,
            boolean missingHue
    ) {
        if (dest == ColorSpace.LCH) {
            return labToLch(
                    ColorSpace.LCH,
                    lightness,
                    a,
                    b,
                    alpha,
                    missingChroma || (a == null && b == null),
                    missingHue
            );
        }
        if (dest == ColorSpace.LAB) {
            // Zero or missing lightness makes a/b powerless → missing.
            var powerlessAB = lightness == null || SassFuzzy.equals(lightness, 0.0);
            return new ConvertedChannels(
                    lightness,
                    a == null || powerlessAB ? null : a,
                    b == null || powerlessAB ? null : b,
                    alpha
            );
        }
        var missingLightness = lightness == null;
        var missingA = a == null;
        var missingB = b == null;
        var l = lightness != null ? lightness : 0.0;
        var f1 = (l + 16.0) / 116.0;
        var x = convertLabFToXorZ((a != null ? a : 0.0) / 500.0 + f1) * D50[0];
        var y = (l > LAB_KAPPA * LAB_EPSILON
                ? Math.pow((l + 16.0) / 116.0, 3.0)
                : l / LAB_KAPPA) * D50[1];
        var z = convertLabFToXorZ(f1 - (b != null ? b : 0.0) / 200.0) * D50[2];
        return fromXyzD50(
                dest,
                x,
                y,
                z,
                alpha,
                missingLightness,
                missingChroma,
                missingHue,
                missingA,
                missingB
        );
    }

    /// Converts channels from LCH into {@code dest}.
    private static ConvertedChannels convertFromLch(
            ColorSpace dest,
            @Nullable Double lightness,
            @Nullable Double chroma,
            @Nullable Double hue,
            @Nullable Double alpha
    ) {
        if (dest == ColorSpace.LCH) {
            var powerlessHue = chroma == null || SassFuzzy.equals(chroma, 0.0);
            return new ConvertedChannels(
                    lightness,
                    chroma == null ? null : Math.abs(chroma),
                    hue == null || powerlessHue ? null : hue,
                    alpha
            );
        }
        // LCH → rectangular Lab uses computed a/b (0 when chroma/hue are missing).
        // Only polar missingness is forwarded for LCH/OKLCH destinations.
        var hueRadians = Math.toRadians(hue != null ? hue : 0.0);
        var c = chroma != null ? chroma : 0.0;
        return convertFromLab(
                dest,
                lightness,
                c * Math.cos(hueRadians),
                c * Math.sin(hueRadians),
                alpha,
                chroma == null,
                hue == null
        );
    }

    /// Converts channels from OKLab into {@code dest}.
    private static ConvertedChannels convertFromOklab(
            ColorSpace dest,
            @Nullable Double lightness,
            @Nullable Double a,
            @Nullable Double b,
            @Nullable Double alpha
    ) {
        return convertFromOklab(
                dest,
                lightness,
                a,
                b,
                alpha,
                false,
                false
        );
    }

    /// Converts channels from OKLab into {@code dest}, optionally marking polar
    /// missingness that originated in OKLCH.
    private static ConvertedChannels convertFromOklab(
            ColorSpace dest,
            @Nullable Double lightness,
            @Nullable Double a,
            @Nullable Double b,
            @Nullable Double alpha,
            boolean missingChroma,
            boolean missingHue
    ) {
        if (dest == ColorSpace.OKLCH) {
            return labToLch(
                    ColorSpace.OKLCH,
                    lightness,
                    a,
                    b,
                    alpha,
                    missingChroma || (a == null && b == null),
                    missingHue
            );
        }
        if (dest == ColorSpace.OKLAB) {
            var powerlessAB = lightness == null || SassFuzzy.equals(lightness, 0.0);
            return new ConvertedChannels(
                    lightness,
                    a == null || powerlessAB ? null : a,
                    b == null || powerlessAB ? null : b,
                    alpha
            );
        }
        var missingLightness = lightness == null;
        var missingA = a == null;
        var missingB = b == null;
        var l = lightness != null ? lightness : 0.0;
        var aa = a != null ? a : 0.0;
        var bb = b != null ? b : 0.0;
        var long_ = Math.pow(OKLAB_TO_LMS[0] * l + OKLAB_TO_LMS[1] * aa + OKLAB_TO_LMS[2] * bb, 3.0);
        var medium = Math.pow(OKLAB_TO_LMS[3] * l + OKLAB_TO_LMS[4] * aa + OKLAB_TO_LMS[5] * bb, 3.0);
        var short_ = Math.pow(OKLAB_TO_LMS[6] * l + OKLAB_TO_LMS[7] * aa + OKLAB_TO_LMS[8] * bb, 3.0);
        return fromLms(
                dest,
                long_,
                medium,
                short_,
                alpha,
                missingLightness,
                missingChroma,
                missingHue,
                missingA,
                missingB
        );
    }

    /// Converts channels from OKLCH into {@code dest}.
    private static ConvertedChannels convertFromOklch(
            ColorSpace dest,
            @Nullable Double lightness,
            @Nullable Double chroma,
            @Nullable Double hue,
            @Nullable Double alpha
    ) {
        if (dest == ColorSpace.OKLCH) {
            var powerlessHue = chroma == null || SassFuzzy.equals(chroma, 0.0);
            return new ConvertedChannels(
                    lightness,
                    chroma == null ? null : Math.abs(chroma),
                    hue == null || powerlessHue ? null : hue,
                    alpha
            );
        }
        var hueRadians = Math.toRadians(hue != null ? hue : 0.0);
        var c = chroma != null ? chroma : 0.0;
        // Polar → rectangular OKLab keeps computed a/b (including zeros for black)
        // rather than applying same-space powerless-channel nulling.
        if (dest == ColorSpace.OKLAB) {
            return new ConvertedChannels(
                    lightness,
                    c * Math.cos(hueRadians),
                    c * Math.sin(hueRadians),
                    alpha
            );
        }
        return convertFromOklab(
                dest,
                lightness,
                c * Math.cos(hueRadians),
                c * Math.sin(hueRadians),
                alpha,
                chroma == null,
                hue == null
        );
    }

    /// Converts XYZ-D50 channels into {@code dest}, preserving Lab/OKLab missingness.
    private static ConvertedChannels fromXyzD50(
            ColorSpace dest,
            double x,
            double y,
            double z,
            @Nullable Double alpha,
            boolean missingLightness,
            boolean missingChroma,
            boolean missingHue,
            boolean missingA,
            boolean missingB
    ) {
        if (dest == ColorSpace.LAB || dest == ColorSpace.LCH) {
            return fromXyzD50ToLab(
                    dest,
                    x,
                    y,
                    z,
                    alpha,
                    missingChroma,
                    missingHue,
                    missingLightness,
                    missingA,
                    missingB
            );
        }
        if (dest == ColorSpace.XYZ_D50) {
            return new ConvertedChannels(x, y, z, alpha);
        }
        // Bridge through XYZ-D65 for remaining spaces while keeping missing flags.
        double[] d65 = multiply(XYZ_D50_TO_XYZ_D65, x, y, z);
        return fromXyzD65(
                dest,
                d65[0],
                d65[1],
                d65[2],
                alpha,
                false,
                false,
                false,
                missingLightness,
                missingChroma,
                missingHue,
                missingA,
                missingB
        );
    }

    /// Converts LMS channels into {@code dest}, preserving OKLab missingness.
    private static ConvertedChannels fromLms(
            ColorSpace dest,
            double long_,
            double medium,
            double short_,
            @Nullable Double alpha,
            boolean missingLightness,
            boolean missingChroma,
            boolean missingHue,
            boolean missingA,
            boolean missingB
    ) {
        if (dest == ColorSpace.OKLAB || dest == ColorSpace.OKLCH) {
            return fromLmsToOklab(
                    dest,
                    long_,
                    medium,
                    short_,
                    alpha,
                    missingChroma,
                    missingHue,
                    missingLightness,
                    missingA,
                    missingB
            );
        }
        if (dest == ColorSpace.LMS) {
            return new ConvertedChannels(long_, medium, short_, alpha);
        }
        // Prefer the dart-sass direct LMS→linear sRGB path for sRGB-family destinations
        // so extreme and near-white round-trips match their floating-point results.
        if (dest == ColorSpace.SRGB
                || dest == ColorSpace.SRGB_LINEAR
                || dest == ColorSpace.RGB
                || dest == ColorSpace.HSL
                || dest == ColorSpace.HWB) {
            double[] linear = multiply(LMS_TO_LINEAR_SRGB, long_, medium, short_);
            return switch (dest) {
                case SRGB_LINEAR -> new ConvertedChannels(linear[0], linear[1], linear[2], alpha);
                case RGB -> new ConvertedChannels(
                        srgbFromLinear(linear[0]) * 255.0,
                        srgbFromLinear(linear[1]) * 255.0,
                        srgbFromLinear(linear[2]) * 255.0,
                        alpha
                );
                case HSL, HWB -> {
                    var polar = srgbToPolar(
                            dest,
                            srgbFromLinear(linear[0]),
                            srgbFromLinear(linear[1]),
                            srgbFromLinear(linear[2]),
                            alpha
                    );
                    if (dest == ColorSpace.HSL) {
                        yield new ConvertedChannels(
                                missingHue || polar.channel0() == null ? null : polar.channel0(),
                                missingChroma ? null : polar.channel1(),
                                missingLightness ? null : polar.channel2(),
                                alpha
                        );
                    }
                    yield new ConvertedChannels(
                            missingHue || polar.channel0() == null ? null : polar.channel0(),
                            polar.channel1(),
                            polar.channel2(),
                            alpha
                    );
                }
                default -> new ConvertedChannels(
                        srgbFromLinear(linear[0]),
                        srgbFromLinear(linear[1]),
                        srgbFromLinear(linear[2]),
                        alpha
                );
            };
        }
        double[] xyz = multiply(LMS_TO_XYZ_D65, long_, medium, short_);
        return fromXyzD65(
                dest,
                xyz[0],
                xyz[1],
                xyz[2],
                alpha,
                false,
                false,
                false,
                missingLightness,
                missingChroma,
                missingHue,
                missingA,
                missingB
        );
    }

    /// Converts Lab-like a/b channels into polar LCH/OKLCH form.
    static ConvertedChannels labToLch(
            ColorSpace dest,
            @Nullable Double lightness,
            @Nullable Double a,
            @Nullable Double b,
            @Nullable Double alpha,
            boolean missingChroma,
            boolean missingHue
    ) {
        var chroma = Math.sqrt(Math.pow(a != null ? a : 0.0, 2.0)
                + Math.pow(b != null ? b : 0.0, 2.0));
        @Nullable Double hue = missingHue || SassFuzzy.equals(chroma, 0.0)
                ? null
                : Math.toDegrees(Math.atan2(b != null ? b : 0.0, a != null ? a : 0.0));
        if (hue != null && hue < 0.0) {
            hue += 360.0;
        }
        return new ConvertedChannels(
                lightness,
                missingChroma ? null : chroma,
                hue,
                alpha
        );
    }

    /// Converts one XYZ-D65 color into {@code dest}.
    private static ConvertedChannels fromXyzD65(
            ColorSpace dest,
            double x,
            double y,
            double z,
            @Nullable Double alpha,
            boolean missing0,
            boolean missing1,
            boolean missing2
    ) {
        return fromXyzD65(
                dest,
                x,
                y,
                z,
                alpha,
                missing0,
                missing1,
                missing2,
                false,
                false,
                false,
                false,
                false
        );
    }

    /// Converts one XYZ-D65 color into {@code dest} with Lab/OKLab missing flags.
    private static ConvertedChannels fromXyzD65(
            ColorSpace dest,
            double x,
            double y,
            double z,
            @Nullable Double alpha,
            boolean missing0,
            boolean missing1,
            boolean missing2,
            boolean missingLightness,
            boolean missingChroma,
            boolean missingHue,
            boolean missingA,
            boolean missingB
    ) {
        return switch (dest) {
            case XYZ_D65 -> new ConvertedChannels(
                    missing0 ? null : x,
                    missing1 ? null : y,
                    missing2 ? null : z,
                    alpha
            );
            case XYZ_D50 -> {
                double[] d50 = multiply(XYZ_D65_TO_XYZ_D50, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : d50[0],
                        missing1 ? null : d50[1],
                        missing2 ? null : d50[2],
                        alpha
                );
            }
            case LMS -> {
                double[] lms = multiply(XYZ_D65_TO_LMS, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : lms[0],
                        missing1 ? null : lms[1],
                        missing2 ? null : lms[2],
                        alpha
                );
            }
            case LAB, LCH -> {
                double[] d50 = multiply(XYZ_D65_TO_XYZ_D50, x, y, z);
                yield fromXyzD50ToLab(
                        dest,
                        d50[0],
                        d50[1],
                        d50[2],
                        alpha,
                        missingChroma,
                        missingHue,
                        missingLightness,
                        missingA,
                        missingB
                );
            }
            case OKLAB, OKLCH -> {
                double[] lms = multiply(XYZ_D65_TO_LMS, x, y, z);
                yield fromLmsToOklab(
                        dest,
                        lms[0],
                        lms[1],
                        lms[2],
                        alpha,
                        missingChroma,
                        missingHue,
                        missingLightness,
                        missingA,
                        missingB
                );
            }
            case HSL, HWB -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_SRGB, x, y, z);
                double red = srgbFromLinear(linear[0]);
                double green = srgbFromLinear(linear[1]);
                double blue = srgbFromLinear(linear[2]);
                var polar = srgbToPolar(dest, red, green, blue, alpha);
                if (dest == ColorSpace.HSL) {
                    yield new ConvertedChannels(
                            missingHue || polar.channel0() == null ? null : polar.channel0(),
                            missingChroma ? null : polar.channel1(),
                            missingLightness ? null : polar.channel2(),
                            alpha
                    );
                }
                yield new ConvertedChannels(
                        missingHue || polar.channel0() == null ? null : polar.channel0(),
                        polar.channel1(),
                        polar.channel2(),
                        alpha
                );
            }
            case RGB -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_SRGB, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : srgbFromLinear(linear[0]) * 255.0,
                        missing1 ? null : srgbFromLinear(linear[1]) * 255.0,
                        missing2 ? null : srgbFromLinear(linear[2]) * 255.0,
                        alpha
                );
            }
            case SRGB -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_SRGB, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : srgbFromLinear(linear[0]),
                        missing1 ? null : srgbFromLinear(linear[1]),
                        missing2 ? null : srgbFromLinear(linear[2]),
                        alpha
                );
            }
            case SRGB_LINEAR -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_SRGB, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : linear[0],
                        missing1 ? null : linear[1],
                        missing2 ? null : linear[2],
                        alpha
                );
            }
            case DISPLAY_P3 -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_DISPLAY_P3, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : srgbFromLinear(linear[0]),
                        missing1 ? null : srgbFromLinear(linear[1]),
                        missing2 ? null : srgbFromLinear(linear[2]),
                        alpha
                );
            }
            case DISPLAY_P3_LINEAR -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_DISPLAY_P3, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : linear[0],
                        missing1 ? null : linear[1],
                        missing2 ? null : linear[2],
                        alpha
                );
            }
            case A98_RGB -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_A98_RGB, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : a98FromLinear(linear[0]),
                        missing1 ? null : a98FromLinear(linear[1]),
                        missing2 ? null : a98FromLinear(linear[2]),
                        alpha
                );
            }
            case PROPHOTO_RGB -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_PROPHOTO_RGB, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : prophotoFromLinear(linear[0]),
                        missing1 ? null : prophotoFromLinear(linear[1]),
                        missing2 ? null : prophotoFromLinear(linear[2]),
                        alpha
                );
            }
            case REC2020 -> {
                double[] linear = multiply(XYZ_D65_TO_LINEAR_REC2020, x, y, z);
                yield new ConvertedChannels(
                        missing0 ? null : rec2020FromLinear(linear[0]),
                        missing1 ? null : rec2020FromLinear(linear[1]),
                        missing2 ? null : rec2020FromLinear(linear[2]),
                        alpha
                );
            }
        };
    }

    /// Converts one XYZ-D50 color into Lab or LCH.
    private static ConvertedChannels fromXyzD50ToLab(
            ColorSpace dest,
            double x,
            double y,
            double z,
            @Nullable Double alpha,
            boolean missingChroma,
            boolean missingHue,
            boolean missingLightness,
            boolean missingA,
            boolean missingB
    ) {
        var f0 = convertXyzComponentToLabF(x / D50[0]);
        var f1 = convertXyzComponentToLabF(y / D50[1]);
        var f2 = convertXyzComponentToLabF(z / D50[2]);
        // Only drop channels that were missing on the source; computed zeros from
        // black stay as 0 rather than becoming CSS missing components.
        @Nullable Double lightness = missingLightness ? null : 116.0 * f1 - 16.0;
        var a = 500.0 * (f0 - f1);
        var b = 200.0 * (f1 - f2);
        if (dest == ColorSpace.LAB) {
            // missingA/missingB apply only to rectangular Lab destinations.
            return new ConvertedChannels(
                    lightness,
                    missingA ? null : a,
                    missingB ? null : b,
                    alpha
            );
        }
        // Polar LCH always receives computed a/b (dart-sass LMS/XYZ path).
        return labToLch(
                ColorSpace.LCH,
                lightness,
                a,
                b,
                alpha,
                missingChroma,
                missingHue
        );
    }

    /// Converts LMS into OKLab or OKLCH.
    private static ConvertedChannels fromLmsToOklab(
            ColorSpace dest,
            double long_,
            double medium,
            double short_,
            @Nullable Double alpha,
            boolean missingChroma,
            boolean missingHue,
            boolean missingLightness,
            boolean missingA,
            boolean missingB
    ) {
        var longScaled = cubeRootPreservingSign(long_);
        var mediumScaled = cubeRootPreservingSign(medium);
        var shortScaled = cubeRootPreservingSign(short_);
        @Nullable Double lightness = missingLightness
                ? null
                : LMS_TO_OKLAB[0] * longScaled
                + LMS_TO_OKLAB[1] * mediumScaled
                + LMS_TO_OKLAB[2] * shortScaled;
        var a = LMS_TO_OKLAB[3] * longScaled
                + LMS_TO_OKLAB[4] * mediumScaled
                + LMS_TO_OKLAB[5] * shortScaled;
        var b = LMS_TO_OKLAB[6] * longScaled
                + LMS_TO_OKLAB[7] * mediumScaled
                + LMS_TO_OKLAB[8] * shortScaled;
        if (dest == ColorSpace.OKLAB) {
            return new ConvertedChannels(
                    lightness,
                    missingA ? null : a,
                    missingB ? null : b,
                    alpha
            );
        }
        // Polar OKLCH always receives computed a/b (dart-sass does not null them).
        return labToLch(
                ColorSpace.OKLCH,
                lightness,
                a,
                b,
                alpha,
                missingChroma,
                missingHue
        );
    }

    /// Converts sRGB channels into {@code dest} while preserving analogous missingness.
    private static ConvertedChannels convertSrgbWithMissing(
            ColorSpace dest,
            double red,
            double green,
            double blue,
            @Nullable Double alpha,
            boolean missingLightness,
            boolean missingChroma,
            boolean missingHue
    ) {
        if (dest == ColorSpace.SRGB) {
            return new ConvertedChannels(red, green, blue, alpha);
        }
        if (dest == ColorSpace.HSL || dest == ColorSpace.HWB) {
            var polar = srgbToPolar(dest, red, green, blue, alpha);
            if (dest == ColorSpace.HSL) {
                return new ConvertedChannels(
                        missingHue ? null : polar.channel0(),
                        missingChroma ? null : polar.channel1(),
                        missingLightness ? null : polar.channel2(),
                        alpha
                );
            }
            return new ConvertedChannels(
                    missingHue ? null : polar.channel0(),
                    polar.channel1(),
                    polar.channel2(),
                    alpha
            );
        }
        double[] xyz = toXyzD65(ColorSpace.SRGB, red, green, blue);
        // Missing HSL/HWB chroma maps to polar chroma, not rectangular a/b.
        // Rectangular destinations keep computed zeros from sat=0.
        return switch (dest) {
            case LAB -> {
                double[] d50 = multiply(XYZ_D65_TO_XYZ_D50, xyz[0], xyz[1], xyz[2]);
                yield fromXyzD50ToLab(
                        dest,
                        d50[0],
                        d50[1],
                        d50[2],
                        alpha,
                        false,
                        false,
                        missingLightness,
                        false,
                        false
                );
            }
            case LCH -> {
                double[] d50 = multiply(XYZ_D65_TO_XYZ_D50, xyz[0], xyz[1], xyz[2]);
                yield fromXyzD50ToLab(
                        dest,
                        d50[0],
                        d50[1],
                        d50[2],
                        alpha,
                        missingChroma,
                        missingHue,
                        missingLightness,
                        false,
                        false
                );
            }
            case OKLAB -> {
                double[] lms = multiply(XYZ_D65_TO_LMS, xyz[0], xyz[1], xyz[2]);
                yield fromLmsToOklab(
                        dest,
                        lms[0],
                        lms[1],
                        lms[2],
                        alpha,
                        false,
                        false,
                        missingLightness,
                        false,
                        false
                );
            }
            case OKLCH -> {
                double[] lms = multiply(XYZ_D65_TO_LMS, xyz[0], xyz[1], xyz[2]);
                yield fromLmsToOklab(
                        dest,
                        lms[0],
                        lms[1],
                        lms[2],
                        alpha,
                        missingChroma,
                        missingHue,
                        missingLightness,
                        false,
                        false
                );
            }
            default -> convert(ColorSpace.SRGB, dest, red, green, blue, alpha);
        };
    }

    /// Converts one sRGB triple into HSL or HWB.
    private static ConvertedChannels srgbToPolar(
            ColorSpace dest,
            double red,
            double green,
            double blue,
            @Nullable Double alpha
    ) {
        var max = Math.max(Math.max(red, green), blue);
        var min = Math.min(Math.min(red, green), blue);
        var delta = max - min;
        double hue;
        if (SassFuzzy.equals(delta, 0.0)) {
            hue = 0.0;
        } else if (max == red || SassFuzzy.equals(max, red)) {
            hue = 60.0 * (((green - blue) / delta) % 6.0);
        } else if (max == green || SassFuzzy.equals(max, green)) {
            hue = 60.0 * ((blue - red) / delta + 2.0);
        } else {
            hue = 60.0 * ((red - green) / delta + 4.0);
        }
        if (hue < 0.0) {
            hue += 360.0;
        }
        if (SassFuzzy.equals(hue, 360.0) || SassFuzzy.equals(hue, 0.0)) {
            hue = 0.0;
        }

        if (dest == ColorSpace.HSL) {
            var lightness = (min + max) / 2.0;
            // Fuzzy endpoints keep pure white/black from floating conversion noise
            // at sat=0 (most sRGB-family whites). Exact == alone yields spurious
            // high saturation when max/min straddle 1.0 by epsilon.
            var saturation = SassFuzzy.equals(lightness, 0.0) || SassFuzzy.equals(lightness, 1.0)
                    ? 0.0
                    : 100.0 * (max - lightness) / Math.min(lightness, 1.0 - lightness);
            if (saturation < 0.0) {
                hue = (hue + 180.0) % 360.0;
                saturation = Math.abs(saturation);
            }
            // Zero saturation makes hue powerless; convert it to a missing channel
            // like dart-sass so later adjustments can reject modifying it.
            @Nullable Double resolvedHue = SassFuzzy.equals(Math.abs(saturation), 0.0)
                    ? null
                    : hue;
            return new ConvertedChannels(
                    resolvedHue,
                    Math.abs(saturation),
                    lightness * 100.0,
                    alpha
            );
        }

        var whiteness = min * 100.0;
        var blackness = 100.0 - max * 100.0;
        @Nullable Double missingHue = SassFuzzy.greaterThanOrEquals(whiteness + blackness, 100.0)
                ? null
                : hue;
        return new ConvertedChannels(missingHue, whiteness, blackness, alpha);
    }

    /// Converts source channels into XYZ D65.
    private static double[] toXyzD65(
            ColorSpace source,
            double channel0,
            double channel1,
            double channel2
    ) {
        return switch (source) {
            case RGB -> {
                double[] linear = {
                        srgbToLinear(channel0 / 255.0),
                        srgbToLinear(channel1 / 255.0),
                        srgbToLinear(channel2 / 255.0)
                };
                yield multiply(LINEAR_SRGB_TO_XYZ_D65, linear[0], linear[1], linear[2]);
            }
            case SRGB -> multiply(
                    LINEAR_SRGB_TO_XYZ_D65,
                    srgbToLinear(channel0),
                    srgbToLinear(channel1),
                    srgbToLinear(channel2)
            );
            case SRGB_LINEAR -> multiply(LINEAR_SRGB_TO_XYZ_D65, channel0, channel1, channel2);
            case DISPLAY_P3 -> multiply(
                    LINEAR_DISPLAY_P3_TO_XYZ_D65,
                    srgbToLinear(channel0),
                    srgbToLinear(channel1),
                    srgbToLinear(channel2)
            );
            case DISPLAY_P3_LINEAR -> multiply(
                    LINEAR_DISPLAY_P3_TO_XYZ_D65,
                    channel0,
                    channel1,
                    channel2
            );
            case A98_RGB -> multiply(
                    LINEAR_A98_RGB_TO_XYZ_D65,
                    a98ToLinear(channel0),
                    a98ToLinear(channel1),
                    a98ToLinear(channel2)
            );
            case PROPHOTO_RGB -> multiply(
                    LINEAR_PROPHOTO_RGB_TO_XYZ_D65,
                    prophotoToLinear(channel0),
                    prophotoToLinear(channel1),
                    prophotoToLinear(channel2)
            );
            case REC2020 -> multiply(
                    LINEAR_REC2020_TO_XYZ_D65,
                    rec2020ToLinear(channel0),
                    rec2020ToLinear(channel1),
                    rec2020ToLinear(channel2)
            );
            case XYZ_D65 -> new double[]{channel0, channel1, channel2};
            case XYZ_D50 -> multiply(XYZ_D50_TO_XYZ_D65, channel0, channel1, channel2);
            case LMS -> multiply(LMS_TO_XYZ_D65, channel0, channel1, channel2);
            case HSL, HWB, LAB, LCH, OKLAB, OKLCH -> throw new IllegalStateException(
                    "Polar/Lab sources must not enter the linear hub directly."
            );
        };
    }

    /// Multiplies a row-major 3x3 matrix by a column vector.
    private static double[] multiply(double[] matrix, double x, double y, double z) {
        return new double[]{
                matrix[0] * x + matrix[1] * y + matrix[2] * z,
                matrix[3] * x + matrix[4] * y + matrix[5] * z,
                matrix[6] * x + matrix[7] * y + matrix[8] * z
        };
    }

    /// Converts a legacy HSL/HWB hue position into one RGB channel.
    static double hueToRgb(double m1, double m2, double hue) {
        if (hue < 0.0) {
            hue += 1.0;
        }
        if (hue > 1.0) {
            hue -= 1.0;
        }
        if (hue < 1.0 / 6.0) {
            return m1 + (m2 - m1) * hue * 6.0;
        }
        if (hue < 0.5) {
            return m2;
        }
        if (hue < 2.0 / 3.0) {
            return m1 + (m2 - m1) * (2.0 / 3.0 - hue) * 6.0;
        }
        return m1;
    }

    /// Converts a gamma-encoded sRGB/display-p3 channel into linear light.
    static double srgbToLinear(double channel) {
        var abs = Math.abs(channel);
        return abs <= 0.04045
                ? channel / 12.92
                : Math.signum(channel) * Math.pow((abs + 0.055) / 1.055, 2.4);
    }

    /// Converts a linear sRGB/display-p3 channel into gamma-encoded form.
    static double srgbFromLinear(double channel) {
        var abs = Math.abs(channel);
        return abs <= 0.0031308
                ? channel * 12.92
                : Math.signum(channel) * (1.055 * Math.pow(abs, 1.0 / 2.4) - 0.055);
    }

    /// Converts a gamma-encoded a98-rgb channel into linear light.
    static double a98ToLinear(double channel) {
        return Math.signum(channel) * Math.pow(Math.abs(channel), 563.0 / 256.0);
    }

    /// Converts a linear a98-rgb channel into gamma-encoded form.
    static double a98FromLinear(double channel) {
        return Math.signum(channel) * Math.pow(Math.abs(channel), 256.0 / 563.0);
    }

    /// Converts a gamma-encoded prophoto-rgb channel into linear light.
    static double prophotoToLinear(double channel) {
        var abs = Math.abs(channel);
        return abs <= 16.0 / 512.0
                ? channel / 16.0
                : Math.signum(channel) * Math.pow(abs, 1.8);
    }

    /// Converts a linear prophoto-rgb channel into gamma-encoded form.
    static double prophotoFromLinear(double channel) {
        var abs = Math.abs(channel);
        return abs >= 1.0 / 512.0
                ? Math.signum(channel) * Math.pow(abs, 1.0 / 1.8)
                : 16.0 * channel;
    }

    /// Converts a gamma-encoded rec2020 channel into linear light.
    static double rec2020ToLinear(double channel) {
        var abs = Math.abs(channel);
        return abs < REC2020_BETA * 4.5
                ? channel / 4.5
                : Math.signum(channel) * Math.pow((abs + REC2020_ALPHA - 1.0) / REC2020_ALPHA, 1.0 / 0.45);
    }

    /// Converts a linear rec2020 channel into gamma-encoded form.
    static double rec2020FromLinear(double channel) {
        var abs = Math.abs(channel);
        return abs > REC2020_BETA
                ? Math.signum(channel) * (REC2020_ALPHA * Math.pow(abs, 0.45) - (REC2020_ALPHA - 1.0))
                : 4.5 * channel;
    }

    /// Converts a Lab f-component into an XYZ X or Z channel.
    private static double convertLabFToXorZ(double component) {
        var cubed = Math.pow(component, 3.0);
        return cubed > LAB_EPSILON ? cubed : (116.0 * component - 16.0) / LAB_KAPPA;
    }

    /// Converts one XYZ component into the intermediate Lab f value.
    private static double convertXyzComponentToLabF(double component) {
        return component > LAB_EPSILON
                ? Math.cbrt(component)
                : (LAB_KAPPA * component + 16.0) / 116.0;
    }

    /// Returns the signed cube root of a value.
    private static double cubeRootPreservingSign(double value) {
        return Math.signum(value) * Math.cbrt(Math.abs(value));
    }

    /// Holds three converted color channels and alpha.
    ///
    /// @param channel0 the first destination channel, or {@code null} when missing
    /// @param channel1 the second destination channel, or {@code null} when missing
    /// @param channel2 the third destination channel, or {@code null} when missing
    /// @param alpha the alpha channel, or {@code null} when missing
    public record ConvertedChannels(
            @Nullable Double channel0,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double alpha
    ) {
    }
}
