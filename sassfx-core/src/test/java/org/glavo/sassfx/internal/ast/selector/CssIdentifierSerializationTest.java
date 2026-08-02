// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies the CSS identifier serialization cases inherited from Dart Sass
/// 1.102.0's string utility tests.
@NotNullByDefault
final class CssIdentifierSerializationTest {
    /// Leaves valid name-start and name code points unescaped.
    @Test
    void preservesValidIdentifierCodePoints() {
        for (var value : List.of(
                "--",
                "q",
                "E",
                "_",
                "ä",
                "👭",
                "-q",
                "-E",
                "-_",
                "-ä",
                "-👭",
                "aq",
                "aE",
                "a4",
                "a_",
                "a-",
                "aä",
                "a👭",
                "--q",
                "--E",
                "--4",
                "--_",
                "---",
                "--ä",
                "--👭"
        )) {
            assertEquals(value, CssIdentifier.toCssIdentifier(value), value);
        }
    }

    /// Escapes invalid starts, punctuation, and private-use code points using
    /// minimal lowercase hexadecimal escapes.
    @Test
    void escapesIdentifierCodePoints() {
        var privateBmp = String.valueOf((char) 0xEABC);
        var privateSupplementary = new String(Character.toChars(0xFABCD));

        assertEquals("\\2d", css("-"));
        assertEquals("\\34", css("4"));
        assertEquals("\\25", css("%"));
        assertEquals("\\eabc", css(privateBmp));
        assertEquals("\\fabcd", css(privateSupplementary));
        assertEquals("-\\34", css("-4"));
        assertEquals("-\\25", css("-%"));
        assertEquals("-\\eabc", css("-" + privateBmp));
        assertEquals("-\\fabcd", css("-" + privateSupplementary));
        assertEquals("a\\25", css("a%"));
        assertEquals("a\\eabc", css("a" + privateBmp));
        assertEquals("a\\fabcd", css("a" + privateSupplementary));
        assertEquals("--\\25", css("--%"));
        assertEquals("--\\eabc", css("--" + privateBmp));
        assertEquals("--\\fabcd", css("--" + privateSupplementary));
    }

    /// Rejects empty identifiers, NUL, and unpaired UTF-16 surrogates at every
    /// identifier position.
    @Test
    void rejectsUnrepresentableIdentifiers() {
        var invalidCharacters = List.of(
                String.valueOf((char) 0),
                String.valueOf((char) 0xDABC),
                String.valueOf((char) 0xDCDE)
        );

        assertThrows(IllegalArgumentException.class, () -> CssIdentifier.toCssIdentifier(""));
        for (var character : invalidCharacters) {
            for (var value : List.of(
                    character,
                    "-" + character,
                    "a" + character,
                    "--" + character,
                    "a" + character + "b"
            )) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CssIdentifier.toCssIdentifier(value),
                        value
                );
            }
        }
    }

    /// Adds a hexadecimal escape terminator only when the following code point
    /// could extend the escape.
    @Test
    void terminatesHexadecimalEscapesOnlyWhenRequired() {
        assertEquals("\\20 1", css(" 1"));
        assertEquals("\\20 b", css(" b"));
        assertEquals("\\20 B", css(" B"));
        assertEquals("\\20", css(" "));
        assertEquals("\\20g", css(" g"));
        assertEquals("\\20G", css(" G"));
        assertEquals("\\20-", css(" -"));
        assertEquals("\\20ä", css(" ä"));
        assertEquals("\\20\\20", css("  "));
    }

    /// Returns the canonical CSS spelling of one semantic identifier.
    ///
    /// @param value the semantic identifier text
    /// @return the CSS identifier spelling
    private static String css(String value) {
        return CssIdentifier.toCssIdentifier(value);
    }
}
