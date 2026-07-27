// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the Sass-compatible plain CSS identifier grammar.
@NotNullByDefault
final class CssIdentifierParserTest {
    /// Verifies ordinary, custom-property-like, and non-ASCII identifiers.
    @Test
    void parsesNameCodePoints() {
        assertEquals("foo", CssIdentifierParser.parse("foo"));
        assertEquals("-foo", CssIdentifierParser.parse("-foo"));
        assertEquals("--foo", CssIdentifierParser.parse("--foo"));
        assertEquals("_foo", CssIdentifierParser.parse("_foo"));
        assertEquals("föö", CssIdentifierParser.parse("föö"));
    }

    /// Verifies normalization of underscores for Sass identifiers.
    @Test
    void normalizesUnderscores() {
        assertEquals(
                "-foo-bar",
                CssIdentifierParser.parse("_foo_bar", true, false)
        );
    }

    /// Verifies hexadecimal and punctuation escape normalization.
    @Test
    void parsesEscapes() {
        assertEquals("foo", CssIdentifierParser.parse("\\66 oo"));
        assertEquals("\\31 foo", CssIdentifierParser.parse("\\31 foo"));
        assertEquals("foo\\.bar", CssIdentifierParser.parse("foo\\2e bar"));
        assertEquals("foo\\.bar", CssIdentifierParser.parse("foo\\.bar"));
        assertEquals("\uD800", CssIdentifierParser.parse("\\d800"));
    }

    /// Verifies identifier detection without exposing parse failures.
    @Test
    void detectsIdentifiers() {
        assertTrue(CssIdentifierParser.isIdentifier("valid-name"));
        assertTrue(CssIdentifierParser.isIdentifier("--"));
        assertFalse(CssIdentifierParser.isIdentifier(""));
        assertFalse(CssIdentifierParser.isIdentifier("-"));
        assertFalse(CssIdentifierParser.isIdentifier("1name"));
        assertFalse(CssIdentifierParser.isIdentifier("name!"));
    }

    /// Verifies the CSS would-start-an-identifier predicate.
    @Test
    void detectsIdentifierStarts() {
        assertTrue(lookingAt("name"));
        assertTrue(lookingAt("-name"));
        assertTrue(lookingAt("--"));
        assertTrue(lookingAt("\\31 name"));
        assertFalse(lookingAt("-1"));
        assertFalse(lookingAt(".name"));
    }

    /// Verifies unit parsing stops before subtraction-like hyphens.
    @Test
    void stopsUnitBeforeHyphenAndDigit() {
        var scanner = new SourceScanner(new SourceFile("px-2px", null));

        assertEquals("px", CssIdentifierParser.parse(scanner, false, true));
        assertEquals(2, scanner.position());
    }

    /// Verifies malformed and out-of-range escapes.
    @Test
    void rejectsInvalidEscapes() {
        assertThrows(ParseException.class, () -> CssIdentifierParser.parse("\\"));
        assertThrows(ParseException.class, () -> CssIdentifierParser.parse("\\\n"));
        assertThrows(ParseException.class, () -> CssIdentifierParser.parse("\\110000"));
    }

    /// Returns whether the supplied text starts with an identifier.
    ///
    /// @param text the source text to inspect
    /// @return whether the identifier-start predicate succeeds
    private static boolean lookingAt(String text) {
        return CssIdentifierParser.lookingAtIdentifier(
                new SourceScanner(new SourceFile(text, null))
        );
    }
}
