// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value.color;

import org.glavo.sassfx.internal.value.SassColor;
import org.glavo.sassfx.internal.value.SassFuzzy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/// Algorithms that map an out-of-gamut color into its space's gamut.
@ApiStatus.Internal
@NotNullByDefault
public enum GamutMapMethod {
    /// Clamps each linear channel to its conventional bounds.
    CLIP("clip") {
        @Override
        public SassColor map(SassColor color) {
            return SassColor.forSpace(
                    color.space(),
                    clampChannel(color.channel0OrNull(), color.space().channels().get(0)),
                    clampChannel(color.channel1OrNull(), color.space().channels().get(1)),
                    clampChannel(color.channel2OrNull(), color.space().channels().get(2)),
                    color.alphaOrNull()
            );
        }
    },

    /// Maps chroma in OKLCH using the local-MINDE improvement from CSS Color 4.
    LOCAL_MINDE("local-minde") {
        /// The just-noticeable OKLab color-difference threshold.
        private static final double JND = 0.02;

        /// The binary-search and threshold comparison tolerance.
        private static final double EPSILON = 0.0001;

        @Override
        public SassColor map(SassColor color) {
            var originOklch = color.toSpace(ColorSpace.OKLCH);
            var lightness = originOklch.channel0OrNull();
            var hue = originOklch.channel2OrNull();
            var alpha = originOklch.alphaOrNull();

            if (SassFuzzy.greaterThanOrEquals(lightness != null ? lightness : 0.0, 1.0)) {
                if (color.isLegacy()) {
                    return SassColor.rgb(255, 255, 255, color.alpha(), null).toSpace(color.space());
                }
                return SassColor.forSpace(color.space(), 1.0, 1.0, 1.0, color.alphaOrNull());
            }
            if (SassFuzzy.lessThanOrEquals(lightness != null ? lightness : 0.0, 0.0)) {
                return SassColor.rgb(0, 0, 0, color.alpha(), null).toSpace(color.space());
            }

            var clipped = CLIP.map(color);
            if (deltaEok(clipped, color) < JND) {
                return clipped;
            }

            var min = 0.0;
            var max = originOklch.channel1();
            var minInGamut = true;
            while (max - min > EPSILON) {
                var chroma = (min + max) / 2.0;
                var current = SassColor.forSpace(ColorSpace.OKLCH, lightness, chroma, hue, alpha)
                        .toSpace(color.space());
                if (minInGamut && current.isInGamut()) {
                    min = chroma;
                    continue;
                }
                clipped = CLIP.map(current);
                var error = deltaEok(clipped, current);
                if (error < JND) {
                    if (JND - error < EPSILON) {
                        return clipped;
                    }
                    minInGamut = false;
                    min = chroma;
                } else {
                    max = chroma;
                }
            }
            return clipped;
        }

        /// Returns the ΔEOK distance between two colors.
        private static double deltaEok(SassColor first, SassColor second) {
            var lab1 = first.toSpace(ColorSpace.OKLAB);
            var lab2 = second.toSpace(ColorSpace.OKLAB);
            return Math.sqrt(
                    Math.pow(lab1.channel0() - lab2.channel0(), 2.0)
                            + Math.pow(lab1.channel1() - lab2.channel1(), 2.0)
                            + Math.pow(lab1.channel2() - lab2.channel2(), 2.0)
            );
        }
    };

    /// The Sass name of this algorithm.
    private final String name;

    /// Creates one gamut-mapping algorithm.
    ///
    /// @param name the Sass name
    GamutMapMethod(String name) {
        this.name = name;
    }

    /// Returns the Sass name.
    ///
    /// @return the algorithm name
    public String methodName() {
        return name;
    }

    /// Maps {@code color} into gamut using this algorithm.
    ///
    /// @param color the color to map
    /// @return the mapped color in the same space
    public abstract SassColor map(SassColor color);

    /// Parses a gamut-mapping algorithm by Sass name.
    ///
    /// @param name the algorithm name
    /// @return the method
    /// @throws IllegalArgumentException if the name is unknown
    public static GamutMapMethod fromName(String name) {
        Objects.requireNonNull(name, "name");
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "clip" -> CLIP;
            case "local-minde" -> LOCAL_MINDE;
            default -> throw new IllegalArgumentException(
                    "Unknown gamut map method \"" + name + "\"."
            );
        };
    }

    /// Clamps one channel value according to its metadata.
    private static @Nullable Double clampChannel(
            @Nullable Double value,
            ColorChannel channel
    ) {
        if (value == null) {
            return null;
        }
        if (channel instanceof ColorChannel.Linear linear) {
            return Math.min(linear.max(), Math.max(linear.min(), value));
        }
        return value;
    }

    /// Returns the Sass name.
    ///
    /// @return the algorithm name
    @Override
    public String toString() {
        return name;
    }
}
