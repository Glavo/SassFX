// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value.color;

import org.glavo.sassfx.internal.value.SassColor;
import org.glavo.sassfx.internal.value.SassFuzzy;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Color Level 4 interpolation and gamut-mapping helpers.
@NotNullByDefault
final class ColorInterpolationTest {
    /// Verifies RGB and polar interpolation produce midpoints in the source space.
    @Test
    void interpolatesOpaqueColorsInRgbAndOklch() {
        var red = SassColor.rgb(255, 0, 0, 1, null);
        var blue = SassColor.rgb(0, 0, 255, 1, null);

        var rgbMix = red.interpolate(blue, InterpolationMethod.of(ColorSpace.RGB), 0.5, false);
        assertEquals(ColorSpace.RGB, rgbMix.space());
        assertTrue(SassFuzzy.equals(rgbMix.channel0(), 127.5));
        assertTrue(SassFuzzy.equals(rgbMix.channel1(), 0.0));
        assertTrue(SassFuzzy.equals(rgbMix.channel2(), 127.5));

        var oklchMix = red.interpolate(
                blue,
                new InterpolationMethod(ColorSpace.OKLCH, HueInterpolationMethod.SHORTER),
                0.5,
                false
        );
        assertEquals(ColorSpace.RGB, oklchMix.space());
        assertTrue(oklchMix.channel0() > 0.0);
        assertTrue(oklchMix.channel2() > 0.0);
    }

    /// Verifies hue interpolation methods choose opposite arcs.
    @Test
    void interpolatesHuesWithShorterAndLongerArcs() {
        var yellow = SassColor.hsl(60, 100, 50, 1);
        var magenta = SassColor.hsl(300, 100, 50, 1);

        var shorter = yellow.interpolate(
                magenta,
                new InterpolationMethod(ColorSpace.HSL, HueInterpolationMethod.SHORTER),
                0.5,
                false
        ).toSpace(ColorSpace.HSL);
        var longer = yellow.interpolate(
                magenta,
                new InterpolationMethod(ColorSpace.HSL, HueInterpolationMethod.LONGER),
                0.5,
                false
        ).toSpace(ColorSpace.HSL);

        assertTrue(SassFuzzy.equals(shorter.channel0(), 0.0)
                || Math.abs(shorter.channel0() - 0.0) < 1e-9
                || Math.abs(shorter.channel0() - 360.0) < 1e-9
                || Math.abs(shorter.channel0() - 180.0) > 90.0);
        // Longer arc should land near 180deg for 60↔300.
        assertTrue(Math.abs(longer.channel0() - 180.0) < 1.0
                || Math.abs(((longer.channel0() + 360.0) % 360.0) - 180.0) < 1.0);
    }

    /// Verifies clip and local-minde gamut mapping return in-gamut colors.
    @Test
    void mapsOutOfGamutColorsWithClipAndLocalMinde() {
        var wide = SassColor.forSpace(ColorSpace.DISPLAY_P3, 1.2, -0.1, 0.0, 1.0);
        assertFalse(wide.isInGamut());

        var clipped = wide.toGamut(GamutMapMethod.CLIP);
        assertTrue(clipped.isInGamut());
        assertTrue(SassFuzzy.equals(clipped.channel0(), 1.0));
        assertTrue(SassFuzzy.equals(clipped.channel1(), 0.0));

        var mapped = wide.toGamut(GamutMapMethod.LOCAL_MINDE);
        assertTrue(mapped.isInGamut());
        assertEquals(ColorSpace.DISPLAY_P3, mapped.space());
    }
}
