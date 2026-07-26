// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.source;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies transformed-source projection and generated-range recovery.
@NotNullByDefault
final class SourceProjectionTest {
    /// Projects original, replacement, and synthetic segments independently.
    @Test
    void projectsSegmentKinds() {
        var original = new SourceFile("=accent\n  color: red", URI.create("memory:test.sass"));
        var transformed = new MappedSourceBuilder(original)
                .appendReplacement("@mixin ", 0, 1)
                .appendOriginal(1, 7)
                .appendSynthetic(" {", 7)
                .appendReplacement("\n", 7, 10)
                .appendOriginal(10, original.length())
                .appendSynthetic(";}", original.length())
                .build();

        var replacement = transformed.span(0, 7);
        assertEquals("=", replacement.text());
        assertEquals(0, replacement.start().offset());
        assertEquals(1, replacement.end().offset());

        var name = transformed.span(7, 13);
        assertEquals("accent", name.text());
        assertEquals(1, name.start().offset());

        var synthetic = transformed.span(13, 15);
        assertEquals("", synthetic.text());
        assertEquals(7, synthetic.start().offset());

        var declaration = transformed.span(16, 26);
        assertEquals("color: red", declaration.text());
        assertEquals(10, declaration.start().offset());
    }

    /// Recovers generated coordinates by span identity for parser composition.
    @Test
    void retainsGeneratedRanges() {
        var original = new SourceFile("$x: 1", null);
        var transformed = new MappedSourceBuilder(original)
                .appendOriginal(0, original.length())
                .appendSynthetic(";", original.length())
                .build();
        var span = transformed.span(0, 2);

        assertEquals(0, transformed.generatedStartOffset(span));
        assertEquals(2, transformed.generatedEndOffset(span));

        var identity = original.span(1, 3);
        assertEquals(1, original.generatedStartOffset(identity));
        assertEquals(3, original.generatedEndOffset(identity));
    }
}
