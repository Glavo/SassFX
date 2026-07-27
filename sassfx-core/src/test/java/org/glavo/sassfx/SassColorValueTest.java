// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the public Color Level 4 Sass value API.
@NotNullByDefault
final class SassColorValueTest {
    /// Covers every public color-space bridge and its canonical name.
    @Test
    void exposesEveryPublicColorSpace() {
        for (var space : SassColorSpace.values()) {
            var color = SassValue.color(space, 0.1, 0.2, 0.3, 0.4);
            assertEquals(SassValueType.COLOR, color.type());
            assertEquals(space, color.colorSpace());
            assertFalse(color.isColorChannelMissing("alpha"));
            assertEquals(0.4, color.colorChannel("alpha"));
            assertTrue(color.toString().contains(space.cssName()));
        }
        assertEquals(16, SassColorSpace.values().length);
    }

    /// Preserves all missing-channel presence bits without deprecated overloads.
    @Test
    void preservesMissingChannels() {
        var color = SassValue.color(
                SassColorSpace.OKLCH,
                null,
                0.2,
                null,
                null
        );

        assertTrue(color.isColorChannelMissing("lightness"));
        assertFalse(color.isColorChannelMissing("chroma"));
        assertTrue(color.isColorChannelMissing("hue"));
        assertTrue(color.isColorChannelMissing("alpha"));
        assertEquals(0.0, color.colorChannel("lightness"));
        assertEquals(0.2, color.colorChannel("chroma"));
        assertEquals(0.0, color.colorChannel("hue"));
        assertEquals(0.0, color.colorChannel("alpha"));
        assertEquals("oklch(none 0.2 none / none)", color.toString());
    }

    /// Rejects invalid alpha and channel access with public exception types.
    @Test
    void validatesColorOperations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SassValue.color(SassColorSpace.SRGB, 0.0, 0.0, 0.0, 1.1)
        );
        var color = SassValue.color(
                SassColorSpace.SRGB,
                0.0,
                0.0,
                0.0,
                1.0
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> color.colorChannel("lightness")
        );
        assertThrows(
                IllegalStateException.class,
                () -> SassValue.number(1).colorSpace()
        );
    }
}
