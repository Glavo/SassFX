// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.source;

import org.glavo.scssfx.SourceLocation;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies UTF-16 source indexing across supported line endings.
@NotNullByDefault
final class SourceFileTest {
    /// Verifies line starts and text for LF, CRLF, and lone CR terminators.
    @Test
    void indexesAllLineTerminators() {
        var source = new SourceFile("a😀b\r\nx\ry\n", URI.create("file:///style.scss"));

        assertEquals(10, source.length());
        assertEquals(4, source.lineCount());
        assertEquals(List.of("a😀b", "x", "y", ""), List.of(
                source.lineText(0),
                source.lineText(1),
                source.lineText(2),
                source.lineText(3)
        ));
        assertEquals(new SourceLocation(0, 3, 3), source.locationAt(3));
        assertEquals(new SourceLocation(1, 0, 6), source.locationAt(6));
        assertEquals(new SourceLocation(2, 0, 8), source.locationAt(8));
        assertEquals(new SourceLocation(3, 0, 10), source.locationAt(10));
    }

    /// Verifies that spans preserve exact supplementary Unicode text and URL.
    @Test
    void createsExactSpan() {
        var url = URI.create("file:///style.scss");
        var source = new SourceFile("a😀b", url);
        var span = source.span(1, 3);

        assertEquals(url, span.url());
        assertEquals(new SourceLocation(0, 1, 1), span.start());
        assertEquals(new SourceLocation(0, 3, 3), span.end());
        assertEquals("😀", span.text());
    }

    /// Verifies indexing behavior for an empty source.
    @Test
    void indexesEmptySource() {
        var source = new SourceFile("", null);

        assertEquals(0, source.length());
        assertEquals(1, source.lineCount());
        assertEquals("", source.lineText(0));
        assertEquals(new SourceLocation(0, 0, 0), source.locationAt(0));
    }

    /// Verifies offset, line, and range bounds.
    @Test
    void rejectsInvalidBounds() {
        var source = new SourceFile("abc", null);

        assertThrows(IndexOutOfBoundsException.class, () -> source.locationAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> source.locationAt(4));
        assertThrows(IndexOutOfBoundsException.class, () -> source.lineText(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> source.lineText(1));
        assertThrows(IllegalArgumentException.class, () -> source.span(2, 1));
    }
}
