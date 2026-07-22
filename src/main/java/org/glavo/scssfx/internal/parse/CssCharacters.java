// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.jetbrains.annotations.NotNullByDefault;

/// Provides character predicates used by the Sass and CSS parsers.
///
/// Unless stated otherwise, inputs are Unicode code points or the sentinel
/// [#END_OF_INPUT].
@NotNullByDefault
final class CssCharacters {
    /// The sentinel returned when no UTF-16 code unit remains.
    static final int END_OF_INPUT = -1;

    /// The greatest code point allowed by CSS Syntax Level 3.
    static final int MAX_ALLOWED_CODE_POINT = 0x10FFFF;

    /// Prevents instantiation.
    private CssCharacters() {
    }

    /// Returns whether the value is an ASCII letter.
    ///
    /// @param character the value to inspect
    /// @return whether the value is an ASCII letter
    static boolean isAlphabetic(int character) {
        return character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z';
    }

    /// Returns whether the value is an ASCII decimal digit.
    ///
    /// @param character the value to inspect
    /// @return whether the value is an ASCII decimal digit
    static boolean isDigit(int character) {
        return character >= '0' && character <= '9';
    }

    /// Returns whether the value may begin an unescaped CSS name.
    ///
    /// @param character the value to inspect
    /// @return whether the value is a name-start code point
    static boolean isNameStart(int character) {
        return character == '_' || isAlphabetic(character) || character >= 0x80;
    }

    /// Returns whether the value may occur after the start of a CSS name.
    ///
    /// @param character the value to inspect
    /// @return whether the value is a name code point
    static boolean isName(int character) {
        return isNameStart(character) || isDigit(character) || character == '-';
    }

    /// Returns whether the value is an ASCII hexadecimal digit.
    ///
    /// @param character the value to inspect
    /// @return whether the value is a hexadecimal digit
    static boolean isHex(int character) {
        return isDigit(character)
                || character >= 'a' && character <= 'f'
                || character >= 'A' && character <= 'F';
    }

    /// Returns the numeric value of an ASCII hexadecimal digit.
    ///
    /// @param character the hexadecimal digit
    /// @return a value from zero through fifteen
    /// @throws IllegalArgumentException if {@code character} is not hexadecimal
    static int hexValue(int character) {
        if (isDigit(character)) {
            return character - '0';
        }
        if (character >= 'A' && character <= 'F') {
            return 10 + character - 'A';
        }
        if (character >= 'a' && character <= 'f') {
            return 10 + character - 'a';
        }
        throw new IllegalArgumentException("Not a hexadecimal digit: " + character);
    }

    /// Returns the lowercase ASCII hexadecimal digit for a nibble.
    ///
    /// @param value a value from zero through fifteen
    /// @return the corresponding lowercase hexadecimal code point
    /// @throws IllegalArgumentException if {@code value} is outside the nibble range
    static int hexCharacter(int value) {
        if (value < 0 || value >= 16) {
            throw new IllegalArgumentException("Not a hexadecimal nibble: " + value);
        }
        return value < 10 ? '0' + value : 'a' + value - 10;
    }

    /// Returns whether the value is a CSS newline code unit.
    ///
    /// @param character the value to inspect
    /// @return whether the value is LF, CR, or form feed
    static boolean isNewline(int character) {
        return character == '\n' || character == '\r' || character == '\f';
    }

    /// Returns whether the value is a CSS space or tab code unit.
    ///
    /// @param character the value to inspect
    /// @return whether the value is a space or horizontal tab
    static boolean isSpaceOrTab(int character) {
        return character == ' ' || character == '\t';
    }

    /// Returns whether the value is CSS whitespace.
    ///
    /// @param character the value to inspect
    /// @return whether the value is CSS whitespace
    static boolean isWhitespace(int character) {
        return isSpaceOrTab(character) || isNewline(character);
    }
}
