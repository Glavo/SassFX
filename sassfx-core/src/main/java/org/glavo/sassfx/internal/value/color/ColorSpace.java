// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value.color;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// A CSS Color Level 4 color space known to Sass.
@ApiStatus.Internal
@NotNullByDefault
public enum ColorSpace {
    /// The legacy RGB color space with channels in {@code 0..255}.
    RGB(
            "rgb",
            true,
            true,
            false,
            List.of(
                    ColorChannel.Linear.of("red", 0, 255, false, true, true, null),
                    ColorChannel.Linear.of("green", 0, 255, false, true, true, null),
                    ColorChannel.Linear.of("blue", 0, 255, false, true, true, null)
            )
    ),

    /// The legacy HSL color space.
    HSL(
            "hsl",
            true,
            true,
            true,
            List.of(
                    ColorChannel.HUE,
                    ColorChannel.Linear.of("saturation", 0, 100, true, true, false, "%"),
                    ColorChannel.Linear.of("lightness", 0, 100, true, false, false, "%")
            )
    ),

    /// The legacy HWB color space.
    HWB(
            "hwb",
            true,
            true,
            true,
            List.of(
                    ColorChannel.HUE,
                    ColorChannel.Linear.of("whiteness", 0, 100, true, false, false, "%"),
                    ColorChannel.Linear.of("blackness", 0, 100, true, false, false, "%")
            )
    ),

    /// The sRGB color space with channels in {@code 0..1}.
    SRGB("srgb", true, false, false, rgbUnitIntervalChannels()),

    /// The linear-light sRGB color space.
    SRGB_LINEAR("srgb-linear", true, false, false, rgbUnitIntervalChannels()),

    /// The display-p3 color space.
    DISPLAY_P3("display-p3", true, false, false, rgbUnitIntervalChannels()),

    /// The linear-light display-p3 color space.
    DISPLAY_P3_LINEAR("display-p3-linear", true, false, false, rgbUnitIntervalChannels()),

    /// The a98-rgb color space.
    A98_RGB("a98-rgb", true, false, false, rgbUnitIntervalChannels()),

    /// The prophoto-rgb color space.
    PROPHOTO_RGB("prophoto-rgb", true, false, false, rgbUnitIntervalChannels()),

    /// The rec2020 color space.
    REC2020("rec2020", true, false, false, rgbUnitIntervalChannels()),

    /// The XYZ D65 color space.
    ///
    /// Serialized as the CSS alias {@code xyz}, matching dart-sass and CSS Color 4.
    XYZ_D65("xyz", false, false, false, xyzChannels()),

    /// The XYZ D50 color space.
    XYZ_D50("xyz-d50", false, false, false, xyzChannels()),

    /// The CIE Lab color space.
    LAB(
            "lab",
            false,
            false,
            false,
            List.of(
                    ColorChannel.Linear.of("lightness", 0, 100, false, true, true, "%"),
                    ColorChannel.Linear.of("a", -125, 125),
                    ColorChannel.Linear.of("b", -125, 125)
            )
    ),

    /// The CIE LCH color space.
    LCH(
            "lch",
            false,
            false,
            true,
            List.of(
                    ColorChannel.Linear.of("lightness", 0, 100, false, true, true, "%"),
                    // Chroma is lower-clamped so color.adjust() cannot produce negatives.
                    ColorChannel.Linear.of("chroma", 0, 150, false, true, false, null),
                    ColorChannel.HUE
            )
    ),

    /// The OKLab color space.
    OKLAB(
            "oklab",
            false,
            false,
            false,
            List.of(
                    ColorChannel.Linear.percent("lightness", 0, 1, true, true),
                    ColorChannel.Linear.of("a", -0.4, 0.4),
                    ColorChannel.Linear.of("b", -0.4, 0.4)
            )
    ),

    /// The OKLCH color space.
    OKLCH(
            "oklch",
            false,
            false,
            true,
            List.of(
                    ColorChannel.Linear.percent("lightness", 0, 1, true, true),
                    // Chroma is lower-clamped so color.adjust() cannot produce negatives.
                    ColorChannel.Linear.of("chroma", 0, 0.4, false, true, false, null),
                    ColorChannel.HUE
            )
    ),

    /// The internal LMS intermediate space used only for OKLab conversions.
    LMS(
            "lms",
            false,
            false,
            false,
            List.of(
                    ColorChannel.Linear.of("long", 0, 1),
                    ColorChannel.Linear.of("medium", 0, 1),
                    ColorChannel.Linear.of("short", 0, 1)
            )
    );

    /// The CSS name of this color space.
    private final String name;

    /// Whether this space has a bounded gamut.
    private final boolean bounded;

    /// Whether this is a pre-Color-4 legacy space.
    private final boolean legacy;

    /// Whether this space uses polar coordinates for one channel.
    private final boolean polar;

    /// The three color channels excluding alpha.
    private final @Unmodifiable List<ColorChannel> channels;

    /// Creates a color space description.
    ///
    /// @param name the CSS name
    /// @param bounded whether the gamut is bounded
    /// @param legacy whether the space is legacy
    /// @param polar whether the space is polar
    /// @param channels the three non-alpha channels
    ColorSpace(
            String name,
            boolean bounded,
            boolean legacy,
            boolean polar,
            List<ColorChannel> channels
    ) {
        this.name = name;
        this.bounded = bounded;
        this.legacy = legacy;
        this.polar = polar;
        this.channels = List.copyOf(channels);
    }

    /// Returns the CSS name of this space.
    ///
    /// @return the space name
    public String spaceName() {
        return name;
    }

    /// Returns whether this space has a bounded gamut.
    ///
    /// @return whether the gamut is bounded
    public boolean isBounded() {
        return bounded;
    }

    /// Returns whether this is a legacy RGB, HSL, or HWB space.
    ///
    /// @return whether the space is legacy
    public boolean isLegacy() {
        return legacy;
    }

    /// Returns whether this space uses a polar coordinate system.
    ///
    /// @return whether the space is polar
    public boolean isPolar() {
        return polar;
    }

    /// Returns the three non-alpha channels of this space.
    ///
    /// @return the immutable channel list
    public @Unmodifiable List<ColorChannel> channels() {
        return channels;
    }

    /// Resolves a known color space by CSS name.
    ///
    /// The comparison is case-insensitive. The alias {@code xyz} maps to
    /// {@link #XYZ_D65}. The internal {@link #LMS} space is not resolved.
    ///
    /// @param name the space name
    /// @return the resolved space
    /// @throws IllegalArgumentException if the name is unknown
    public static ColorSpace fromName(String name) {
        Objects.requireNonNull(name, "name");
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "rgb" -> RGB;
            case "hsl" -> HSL;
            case "hwb" -> HWB;
            case "srgb" -> SRGB;
            case "srgb-linear" -> SRGB_LINEAR;
            case "display-p3" -> DISPLAY_P3;
            case "display-p3-linear" -> DISPLAY_P3_LINEAR;
            case "a98-rgb" -> A98_RGB;
            case "prophoto-rgb" -> PROPHOTO_RGB;
            case "rec2020" -> REC2020;
            case "xyz", "xyz-d65" -> XYZ_D65;
            case "xyz-d50" -> XYZ_D50;
            case "lab" -> LAB;
            case "lch" -> LCH;
            case "oklab" -> OKLAB;
            case "oklch" -> OKLCH;
            default -> throw new IllegalArgumentException("Unknown color space \"" + name + "\".");
        };
    }

    /// Returns the CSS name.
    ///
    /// @return the space name
    @Override
    public String toString() {
        return name;
    }

    /// Returns the shared unit-interval RGB channel list.
    ///
    /// @return the RGB channels
    private static List<ColorChannel> rgbUnitIntervalChannels() {
        return List.of(
                ColorChannel.Linear.of("red", 0, 1),
                ColorChannel.Linear.of("green", 0, 1),
                ColorChannel.Linear.of("blue", 0, 1)
        );
    }

    /// Returns the shared XYZ channel list.
    ///
    /// @return the XYZ channels
    private static List<ColorChannel> xyzChannels() {
        return List.of(
                ColorChannel.Linear.of("x", 0, 1),
                ColorChannel.Linear.of("y", 0, 1),
                ColorChannel.Linear.of("z", 0, 1)
        );
    }
}
