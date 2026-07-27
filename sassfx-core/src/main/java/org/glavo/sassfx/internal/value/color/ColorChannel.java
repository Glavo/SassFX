// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value.color;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Metadata about one channel of a known [ColorSpace].
@ApiStatus.Internal
@NotNullByDefault
public sealed interface ColorChannel permits ColorChannel.Linear, ColorChannel.Polar {
    /// The alpha channel shared by every color space.
    Linear ALPHA = new Linear("alpha", 0.0, 1.0, false, false, false, null);

    /// The polar hue channel shared by HSL, HWB, LCH, and OKLCH.
    Polar HUE = new Polar("hue", "deg");

    /// Returns the CSS channel name.
    ///
    /// @return the channel name
    String name();

    /// Returns whether this channel is a polar angle.
    ///
    /// @return whether the channel represents an angle
    boolean isPolarAngle();

    /// Returns the unit conventionally associated with this channel.
    ///
    /// @return the unit, or {@code null} when unitless values are conventional
    @Nullable String associatedUnit();

    /// Returns whether this channel is analogous to {@code other} for missing-channel
    /// interpolation.
    ///
    /// @param other the other channel
    /// @return whether the channels are analogous
    default boolean isAnalogous(ColorChannel other) {
        return switch (name()) {
            case "red", "x" -> "red".equals(other.name()) || "x".equals(other.name());
            case "green", "y" -> "green".equals(other.name()) || "y".equals(other.name());
            case "blue", "z" -> "blue".equals(other.name()) || "z".equals(other.name());
            case "chroma", "saturation" ->
                    "chroma".equals(other.name()) || "saturation".equals(other.name());
            case "lightness" -> "lightness".equals(other.name());
            case "hue" -> "hue".equals(other.name());
            default -> false;
        };
    }

    /// A linear (non-polar) color channel.
    ///
    /// @param name the channel name
    /// @param min the conventional minimum used for percentages and gamut checks
    /// @param max the conventional maximum used for percentages and gamut checks
    /// @param requiresPercent whether unitless values are forbidden for this channel
    /// @param lowerClamped whether global constructors clamp values below {@code min}
    /// @param upperClamped whether global constructors clamp values above {@code max}
    /// @param associatedUnit the conventional unit, or {@code null}
    record Linear(
            String name,
            double min,
            double max,
            boolean requiresPercent,
            boolean lowerClamped,
            boolean upperClamped,
            @Nullable String associatedUnit
    ) implements ColorChannel {
        /// Creates a linear channel that is conventionally unitless.
        ///
        /// @param name the channel name
        /// @param min the minimum
        /// @param max the maximum
        /// @return the channel
        public static Linear of(String name, double min, double max) {
            return of(name, min, max, false, false, false, null);
        }

        /// Creates a linear channel with explicit clamps and units.
        ///
        /// @param name the channel name
        /// @param min the minimum
        /// @param max the maximum
        /// @param requiresPercent whether percentages are required
        /// @param lowerClamped whether the lower bound is clamped
        /// @param upperClamped whether the upper bound is clamped
        /// @param associatedUnit the conventional unit, or {@code null}
        /// @return the channel
        public static Linear of(
                String name,
                double min,
                double max,
                boolean requiresPercent,
                boolean lowerClamped,
                boolean upperClamped,
                @Nullable String associatedUnit
        ) {
            var unit = associatedUnit;
            if (unit == null && min == 0.0 && max == 100.0) {
                unit = "%";
            }
            return new Linear(name, min, max, requiresPercent, lowerClamped, upperClamped, unit);
        }

        /// Creates a percentage-convention channel without forcing percent syntax.
        ///
        /// @param name the channel name
        /// @param min the minimum
        /// @param max the maximum
        /// @param lowerClamped whether the lower bound is clamped
        /// @param upperClamped whether the upper bound is clamped
        /// @return the channel
        public static Linear percent(
                String name,
                double min,
                double max,
                boolean lowerClamped,
                boolean upperClamped
        ) {
            return new Linear(name, min, max, false, lowerClamped, upperClamped, "%");
        }

        @Override
        public boolean isPolarAngle() {
            return false;
        }
    }

    /// A polar-angle color channel.
    ///
    /// @param name the channel name
    /// @param associatedUnit the conventional angle unit
    record Polar(String name, String associatedUnit) implements ColorChannel {
        @Override
        public boolean isPolarAngle() {
            return true;
        }
    }
}
