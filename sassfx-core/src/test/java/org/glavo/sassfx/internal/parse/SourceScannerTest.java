// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies restorable UTF-16 scanning and parse-error spans.
@NotNullByDefault
final class SourceScannerTest {
    /// Verifies code-unit lookahead, scanning, and restoration.
    @Test
    void scansAndRestoresPosition() {
        var source = new SourceFile("ab\nc", URI.create("file:///style.scss"));
        var scanner = new SourceScanner(source);

        assertEquals('a', scanner.peek());
        assertEquals('b', scanner.peek(1));
        assertEquals(CssCharacters.END_OF_INPUT, scanner.peek(4));
        assertTrue(scanner.scan('a'));
        var afterA = scanner.state();
        assertTrue(scanner.scan("b\n"));
        assertEquals('c', scanner.read());
        assertTrue(scanner.isDone());

        scanner.restore(afterA);
        assertEquals(1, scanner.position());
        assertFalse(scanner.scan('a'));
        assertEquals("b\n", scanner.substring(1, 3));
    }

    /// Verifies exact source spans between captured states.
    @Test
    void createsSpanBetweenStates() {
        var source = new SourceFile("a😀b", URI.create("file:///style.scss"));
        var scanner = new SourceScanner(source);
        scanner.read();
        var start = scanner.state();
        scanner.read();
        scanner.read();

        var span = scanner.spanFrom(start);
        assertEquals("😀", span.text());
        assertEquals(1, span.start().column());
        assertEquals(3, span.end().column());
    }

    /// Verifies mismatch and end-of-input failure ranges.
    @Test
    void reportsParseFailureSpans() {
        var scanner = new SourceScanner(new SourceFile("x", null));
        var mismatch = assertThrows(ParseException.class, () -> scanner.expect('y'));
        assertEquals("x", mismatch.span().text());

        scanner.read();
        var end = assertThrows(ParseException.class, scanner::read);
        assertEquals("", end.span().text());
        assertEquals(1, end.span().start().offset());
    }

    /// Verifies bounds on lookahead and restored state.
    @Test
    void rejectsInvalidScannerBounds() {
        var scanner = new SourceScanner(new SourceFile("x", null));

        assertThrows(IllegalArgumentException.class, () -> scanner.peek(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> scanner.restore(new ScannerState(2)));
    }
}
