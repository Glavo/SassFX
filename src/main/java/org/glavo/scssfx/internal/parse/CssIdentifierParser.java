// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;

/// Parses plain CSS identifiers using the lexical rules shared by Sass.
@NotNullByDefault
final class CssIdentifierParser {
    /// Prevents instantiation.
    private CssIdentifierParser() {
    }

    /// Parses an entire string as a plain CSS identifier.
    ///
    /// @param text the identifier source text
    /// @return the normalized identifier representation
    /// @throws ParseException if the text is not exactly one identifier
    static String parse(String text) {
        return parse(text, false, false);
    }

    /// Parses an entire string as a plain CSS identifier.
    ///
    /// @param text the identifier source text
    /// @param normalize whether underscores are normalized to hyphens
    /// @param unit whether a hyphen before a dot or digit ends the identifier
    /// @return the normalized identifier representation
    /// @throws ParseException if the text is not exactly one identifier
    static String parse(String text, boolean normalize, boolean unit) {
        var scanner = new SourceScanner(new SourceFile(text, null));
        var result = parse(scanner, normalize, unit);
        if (!scanner.isDone()) {
            throw scanner.error("Expected end of input.");
        }
        return result;
    }

    /// Returns whether an entire string is a valid plain CSS identifier.
    ///
    /// @param text the text to inspect
    /// @return whether parsing the complete text succeeds
    static boolean isIdentifier(String text) {
        try {
            parse(text);
            return true;
        } catch (ParseException ignored) {
            return false;
        }
    }

    /// Parses an identifier from the current scanner position.
    ///
    /// @param scanner the source scanner
    /// @param normalize whether underscores are normalized to hyphens
    /// @param unit whether a hyphen before a dot or digit ends the identifier
    /// @return the parsed identifier representation
    /// @throws ParseException if an identifier does not begin at the current position
    static String parse(SourceScanner scanner, boolean normalize, boolean unit) {
        var text = new StringBuilder();
        if (scanner.scan('-')) {
            text.append('-');
            if (scanner.scan('-')) {
                text.append('-');
                appendBody(scanner, text, normalize, unit);
                return text.toString();
            }
        }

        var next = scanner.peek();
        if (next == '_' && normalize) {
            scanner.read();
            text.append('-');
        } else if (CssCharacters.isNameStart(next)) {
            text.append((char) scanner.read());
        } else if (next == '\\') {
            text.append(escape(scanner, true));
        } else {
            throw scanner.error("Expected identifier.");
        }

        appendBody(scanner, text, normalize, unit);
        return text.toString();
    }

    /// Returns whether an identifier begins at the scanner position.
    ///
    /// This implements the CSS would-start-an-identifier algorithm while
    /// assuming that every backslash begins an escape.
    ///
    /// @param scanner the source scanner
    /// @return whether a plain CSS identifier begins here
    static boolean lookingAtIdentifier(SourceScanner scanner) {
        return lookingAtIdentifier(scanner, 0);
    }

    /// Returns whether an identifier begins at a future scanner position.
    ///
    /// @param scanner the source scanner
    /// @param forward the nonnegative number of UTF-16 code units to look ahead
    /// @return whether a plain CSS identifier begins at the selected position
    /// @throws IllegalArgumentException if {@code forward} is negative
    static boolean lookingAtIdentifier(SourceScanner scanner, int forward) {
        var first = scanner.peek(forward);
        if (CssCharacters.isNameStart(first) || first == '\\') {
            return true;
        }
        if (first != '-') {
            return false;
        }

        var second = scanner.peek(forward + 1);
        return CssCharacters.isNameStart(second) || second == '\\' || second == '-';
    }

    /// Parses one or more identifier-body code units.
    ///
    /// @param scanner the source scanner
    /// @param normalize whether underscores are normalized to hyphens
    /// @param unit whether a hyphen before a dot or digit ends the identifier
    /// @return the parsed identifier-body representation
    /// @throws ParseException if no identifier-body text begins at the current position
    static String parseBody(SourceScanner scanner, boolean normalize, boolean unit) {
        var text = new StringBuilder();
        appendBody(scanner, text, normalize, unit);
        if (text.length() == 0) {
            throw scanner.error("Expected identifier body.");
        }
        return text.toString();
    }

    /// Appends the identifier body at the current scanner position.
    ///
    /// @param scanner the source scanner
    /// @param text the destination buffer
    /// @param normalize whether underscores are normalized to hyphens
    /// @param unit whether a hyphen before a dot or digit ends the identifier
    private static void appendBody(
            SourceScanner scanner,
            StringBuilder text,
            boolean normalize,
            boolean unit
    ) {
        while (true) {
            var next = scanner.peek();
            if (next == '-' && unit) {
                var after = scanner.peek(1);
                if (after == '.' || CssCharacters.isDigit(after)) {
                    return;
                }
                text.append((char) scanner.read());
            } else if (next == '_' && normalize) {
                scanner.read();
                text.append('-');
            } else if (CssCharacters.isName(next)) {
                text.append((char) scanner.read());
            } else if (next == '\\') {
                text.append(escape(scanner, false));
            } else {
                return;
            }
        }
    }

    /// Consumes an escape and returns its normalized identifier text.
    ///
    /// @param scanner the source scanner
    /// @param identifierStart whether the escape occurs at the identifier start
    /// @return the normalized escaped text
    /// @throws ParseException if the escape is incomplete or contains an invalid code point
    static String escape(SourceScanner scanner, boolean identifierStart) {
        var start = scanner.position();
        scanner.expect('\\');

        var next = scanner.peek();
        if (next == CssCharacters.END_OF_INPUT || CssCharacters.isNewline(next)) {
            throw scanner.error("Expected escape sequence.");
        }

        int value;
        if (CssCharacters.isHex(next)) {
            value = 0;
            for (var count = 0; count < 6 && CssCharacters.isHex(scanner.peek()); count++) {
                value = value * 16 + CssCharacters.hexValue(scanner.read());
            }
            if (CssCharacters.isWhitespace(scanner.peek())) {
                scanner.read();
            }
        } else {
            value = scanner.read();
        }

        if (identifierStart
                ? CssCharacters.isNameStart(value)
                : CssCharacters.isName(value)) {
            if (value > CssCharacters.MAX_ALLOWED_CODE_POINT) {
                throw scanner.error(
                        "Invalid Unicode code point.",
                        start,
                        scanner.position() - start
                );
            }
            return value <= Character.MAX_VALUE
                    ? Character.toString((char) value)
                    : new String(Character.toChars(value));
        }

        if (value <= 0x1F || value == 0x7F
                || identifierStart && CssCharacters.isDigit(value)) {
            var result = new StringBuilder().append('\\');
            if (value > 0xF) {
                result.append((char) CssCharacters.hexCharacter(value >> 4));
            }
            result.append((char) CssCharacters.hexCharacter(value & 0xF));
            return result.append(' ').toString();
        }

        return new String(new char[]{'\\', (char) value});
    }
}
