// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value.color;

import org.glavo.scssfx.internal.value.SassColor;
import org.glavo.scssfx.internal.value.SassFuzzy;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Color Level 4 space metadata and conversion round-trips.
@NotNullByDefault
final class ColorSpaceModelTest {
    /// Verifies known space names, aliases, and rejection of LMS.
    @Test
    void resolvesKnownColorSpacesByName() {
        assertEquals(ColorSpace.RGB, ColorSpace.fromName("rgb"));
        assertEquals(ColorSpace.XYZ_D65, ColorSpace.fromName("xyz"));
        assertEquals(ColorSpace.XYZ_D65, ColorSpace.fromName("XYZ-D65"));
        assertEquals(ColorSpace.DISPLAY_P3, ColorSpace.fromName("display-p3"));
        assertEquals(ColorSpace.OKLCH, ColorSpace.fromName("oklch"));
        assertThrows(IllegalArgumentException.class, () -> ColorSpace.fromName("lms"));
        assertThrows(IllegalArgumentException.class, () -> ColorSpace.fromName("unknown"));
    }

    /// Verifies legacy RGB colors convert to HSL and modern spaces.
    @Test
    void convertsLegacyRgbThroughHslAndOklab() {
        var red = SassColor.rgb(255, 0, 0, 1, null);

        assertTrue(red.isLegacy());
        assertEquals(ColorSpace.RGB, red.space());
        assertEquals(0.0, red.hue());
        assertEquals(100.0, red.saturation());
        assertEquals(50.0, red.lightness());

        var hsl = red.toSpace(ColorSpace.HSL);
        assertEquals(ColorSpace.HSL, hsl.space());
        assertTrue(SassFuzzy.equals(hsl.channel0(), 0.0));
        assertTrue(SassFuzzy.equals(hsl.channel1(), 100.0));
        assertTrue(SassFuzzy.equals(hsl.channel2(), 50.0));

        var roundTrip = hsl.toSpace(ColorSpace.RGB);
        assertTrue(SassFuzzy.equals(roundTrip.channel0(), 255.0));
        assertTrue(SassFuzzy.equals(roundTrip.channel1(), 0.0));
        assertTrue(SassFuzzy.equals(roundTrip.channel2(), 0.0));

        var oklab = red.toSpace(ColorSpace.OKLAB);
        assertEquals(ColorSpace.OKLAB, oklab.space());
        assertFalse(oklab.isLegacy());
        assertTrue(oklab.channel0() > 0.5);
        assertTrue(oklab.toSpace(ColorSpace.RGB).channel0() > 250.0);
    }

    /// Verifies HWB and Lab conversions preserve red's appearance.
    @Test
    void convertsThroughHwbLabAndXyz() {
        var red = SassColor.rgb(255, 0, 0, 1, null);
        var hwb = red.toSpace(ColorSpace.HWB);
        assertEquals(ColorSpace.HWB, hwb.space());
        assertTrue(SassFuzzy.equals(hwb.channel1(), 0.0));
        assertTrue(SassFuzzy.equals(hwb.channel2(), 0.0));

        var lab = red.toSpace(ColorSpace.LAB);
        assertEquals(ColorSpace.LAB, lab.space());
        assertTrue(lab.channel0() > 40.0);

        var xyz = red.toSpace(ColorSpace.XYZ_D65);
        assertEquals(ColorSpace.XYZ_D65, xyz.space());
        assertTrue(xyz.channel0() > 0.3);

        var back = xyz.toSpace(ColorSpace.RGB);
        assertTrue(SassFuzzy.equals(back.channel0(), 255.0)
                || Math.abs(back.channel0() - 255.0) < 1e-6);
    }

    /// Verifies modern space serialization uses CSS Color 4 syntax.
    @Test
    void serializesModernSpacesWithCssColorSyntax() {
        var oklch = SassColor.oklch(0.628, 0.258, 29.23, 1.0);
        assertTrue(oklch.toString().startsWith("oklch("));
        assertTrue(oklch.toString().contains("%"));

        var displayP3 = SassColor.forSpace(ColorSpace.DISPLAY_P3, 1.0, 0.0, 0.0, 1.0);
        assertEquals("color(display-p3 1 0 0)", displayP3.toString());

        var translucent = SassColor.forSpace(ColorSpace.SRGB, 0.5, 0.25, 0.125, 0.4);
        assertEquals("color(srgb 0.5 0.25 0.125 / 0.4)", translucent.toString());
    }

    /// Verifies gamut checks and missing-channel queries.
    @Test
    void reportsGamutAndMissingChannels() {
        var inGamut = SassColor.srgb(0.5, 0.5, 0.5, 1.0);
        assertTrue(inGamut.isInGamut());

        var outOfGamut = SassColor.srgb(1.5, -0.1, 0.0, 1.0);
        assertFalse(outOfGamut.isInGamut());

        var missing = SassColor.forSpace(ColorSpace.OKLCH, 0.5, null, 120.0, 1.0);
        assertTrue(missing.isChannelMissing("chroma"));
        assertFalse(missing.isChannelMissing("lightness"));
        assertTrue(missing.hasMissingChannel());
    }
}
