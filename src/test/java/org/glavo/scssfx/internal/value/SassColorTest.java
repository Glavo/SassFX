// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies immutable RGB color values and their source-format metadata.
@NotNullByDefault
final class SassColorTest {
    /// Verifies RGB channels, alpha, and an opaque hexadecimal source format are retained.
    @Test
    void createsOpaqueRgbColorsWithSourceFormat() {
        var source = new SourceFile("#0a141e", null);
        var format = new SpanColorFormat(source.span(0, 7));
        var color = SassColor.rgb(10, 20, 30, 1, format);

        assertEquals(10.0, color.red());
        assertEquals(20.0, color.green());
        assertEquals(30.0, color.blue());
        assertEquals(1.0, color.alpha());
        assertEquals(format, color.format());
        assertEquals("#0a141e", format.original());
        assertEquals("#0a141e", color.toString());
    }

    /// Verifies translucent RGB colors omit opaque hexadecimal source-format metadata.
    @Test
    void createsTranslucentRgbColorsWithoutSourceFormat() {
        var color = SassColor.rgb(10, 20, 30, 0.4, null);

        assertEquals(0.4, color.alpha());
        assertNull(color.format());
    }

    /// Verifies out-of-gamut channels are retained while alpha remains constrained.
    @Test
    void retainsOutOfGamutChannelsAndRejectsInvalidAlpha() {
        var color = SassColor.rgb(-1, 256, 0.5, 1, null);

        assertEquals(-1.0, color.red());
        assertEquals(256.0, color.green());
        assertEquals(0.5, color.blue());
        assertEquals("rgb(-1, 256, 0.5)", color.toString());
        assertThrows(IllegalArgumentException.class, () -> SassColor.rgb(0, 0, 0, -0.1, null));
        assertThrows(IllegalArgumentException.class, () -> SassColor.rgb(0, 0, 0, 1.1, null));
    }

    /// Verifies inspect serialization retains large integral channels and
    /// expands exponent notation.
    @Test
    void serializesInspectNumbersWithoutNarrowingOrExponents() {
        var color = SassColor.rgb(1e20, 1e-7, -1e20, 1, null);

        assertEquals(
                "rgb(100000000000000000000, 0.0000001, -100000000000000000000)",
                color.toString()
        );
    }

    /// Verifies legacy mixing accounts for alpha differences and discards source format.
    @Test
    void mixesLegacyRgbColorsWithAlphaAwareWeights() {
        var source = new SourceFile("red", null);
        var formattedRed = SassColor.rgb(
                255,
                0,
                0,
                1,
                new SpanColorFormat(source.span(0, 3))
        );
        var midpoint = formattedRed.mixedWith(SassColor.rgb(0, 0, 255, 1, null), 0.5);

        assertEquals(127.5, midpoint.red());
        assertEquals(0.0, midpoint.green());
        assertEquals(127.5, midpoint.blue());
        assertEquals(1.0, midpoint.alpha());
        assertNull(midpoint.format());

        var alphaAware = SassColor.rgb(255, 0, 0, 0.25, null)
                .mixedWith(SassColor.rgb(0, 0, 255, 0.75, null), 0.5);
        assertEquals(63.75, alphaAware.red());
        assertEquals(0.0, alphaAware.green());
        assertEquals(191.25, alphaAware.blue());
        assertEquals(0.5, alphaAware.alpha());

        var transparentMix = SassColor.rgb(0, 0, 0, 0, null)
                .mixedWith(SassColor.rgb(255, 0, 0, 1, null), 0.5);
        assertEquals(255.0, transparentMix.red());
        assertEquals(0.0, transparentMix.green());
        assertEquals(0.0, transparentMix.blue());
        assertEquals(0.5, transparentMix.alpha());

        var transparentEndpoint = SassColor.rgb(255, 0, 0, 0, null)
                .mixedWith(SassColor.rgb(0, 0, 255, 1, null), 1.0);
        assertEquals(255.0, transparentEndpoint.red());
        assertEquals(0.0, transparentEndpoint.green());
        assertEquals(0.0, transparentEndpoint.blue());
        assertEquals(0.0, transparentEndpoint.alpha());
        assertThrows(
                IllegalArgumentException.class,
                () -> formattedRed.mixedWith(midpoint, -0.01)
        );
    }

    /// Verifies inversion and HSL-derived legacy transforms preserve the expected channels.
    @Test
    void transformsLegacyRgbColorsThroughHslWithoutPreservingFormats() {
        var source = new SourceFile("#123", null);
        var input = SassColor.rgb(17, 34, 51, 0.4, new SpanColorFormat(source.span(0, 4)));
        var inverted = input.inverted();

        assertEquals(238.0, inverted.red());
        assertEquals(221.0, inverted.green());
        assertEquals(204.0, inverted.blue());
        assertEquals(0.4, inverted.alpha());
        assertNull(inverted.format());

        var red = SassColor.rgb(255, 0, 0, 1, null);
        assertEquals(0.0, red.hue());
        assertEquals(100.0, red.saturation());
        assertEquals(50.0, red.lightness());

        var grayscale = red.grayscale();
        assertEquals(127.5, grayscale.red());
        assertEquals(127.5, grayscale.green());
        assertEquals(127.5, grayscale.blue());
        assertEquals(1.0, grayscale.alpha());

        var complement = red.complemented();
        assertEquals(0.0, complement.red());
        assertEquals(255.0, complement.green());
        assertEquals(255.0, complement.blue());
    }

    /// Verifies channel equality and hashing use the same Sass fuzzy buckets
    /// and ignore source format.
    @Test
    void comparesAndHashesChannelsWithSassFuzzySemantics() {
        var source = new SourceFile("red", null);
        var first = SassColor.rgb(
                0.3,
                10,
                20,
                0.4,
                new SpanColorFormat(source.span(0, 3))
        );
        var equivalent = SassColor.rgb(0.300000000001, 10, 20, 0.400000000001, null);
        var different = SassColor.rgb(0.30000000002, 10, 20, 0.4, null);
        var positiveZero = SassColor.rgb(0.0, 10, 20, 0.4, null);
        var negativeZero = SassColor.rgb(-0.0, 10, 20, 0.4, null);

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, different);
        assertEquals(positiveZero, negativeZero);
        assertEquals(positiveZero.hashCode(), negativeZero.hashCode());
    }
}
