// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.glavo.scssfx.SassCompilationException;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Formats Sass failures as a stylesheet that displays the error in a browser.
@NotNullByDefault
final class CliErrorCss {
    /// Prevents instantiation.
    private CliErrorCss() {
    }

    /// Returns a complete error stylesheet for one compilation failure.
    ///
    /// The comment always uses ASCII frame glyphs. The displayed CSS string
    /// preserves the formatter's Unicode selection, while both representations
    /// omit terminal styling.
    ///
    /// @param failure the Sass compilation failure
    /// @param printer the formatter carrying glyph and source settings
    /// @return the error stylesheet without a trailing newline
    static String format(
            SassCompilationException failure,
            DiagnosticPrinter printer
    ) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(printer, "printer");
        var plainPrinter = printer.withoutColor();
        var message = plainPrinter.format(failure);
        var comment = plainPrinter.withAsciiGlyphs()
                .format(failure)
                .replace("*/", "*∕")
                .replace("\r\n", "\n")
                .replace("\n", "\n * ");
        var string = cssString(message);
        return """
                /* %s */

                body::before {
                  font-family: "Source Code Pro", "SF Mono", Monaco, Inconsolata, "Fira Mono",
                      "Droid Sans Mono", monospace, monospace;
                  white-space: pre;
                  display: block;
                  padding: 1em;
                  margin-bottom: 1em;
                  border-bottom: 2px solid black;
                  content: %s;
                }""".formatted(comment, string);
    }

    /// Serializes a diagnostic as a quoted Sass string.
    ///
    /// The quote choice, control-character escapes, private-use escapes, and
    /// non-ASCII escaping match Dart Sass's expanded value serialization.
    ///
    /// @param value the diagnostic text
    /// @return a single- or double-quoted CSS string
    private static String cssString(String value) {
        var quote = value.indexOf('"') >= 0 && value.indexOf('\'') < 0
                ? '\''
                : '"';
        var serialized = new StringBuilder(value.length() + 2).append(quote);
        for (var offset = 0; offset < value.length(); ) {
            var codePoint = value.codePointAt(offset);
            var charCount = Character.charCount(codePoint);
            if (codePoint == quote || codePoint == '\\') {
                serialized.append('\\').appendCodePoint(codePoint);
            } else if (isControl(codePoint) || isPrivateUse(codePoint)) {
                appendHexEscape(
                        serialized,
                        codePoint,
                        value,
                        offset + charCount
                );
            } else {
                serialized.appendCodePoint(codePoint);
            }
            offset += charCount;
        }
        serialized.append(quote);

        var result = new StringBuilder(serialized.length());
        serialized.codePoints().forEach(codePoint -> {
            if (codePoint > 0x7f) {
                result.append('\\')
                        .append(Integer.toHexString(codePoint))
                        .append(' ');
            } else {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    /// Returns whether a code point is serialized as an unprintable ASCII
    /// escape.
    ///
    /// @param codePoint the Unicode code point
    /// @return {@code true} for U+0000–U+001F and U+007F
    private static boolean isControl(int codePoint) {
        return codePoint >= 0 && codePoint <= 0x1f || codePoint == 0x7f;
    }

    /// Returns whether a code point belongs to a Unicode private-use area.
    ///
    /// @param codePoint the Unicode code point
    /// @return {@code true} for a BMP or supplementary private-use code point
    private static boolean isPrivateUse(int codePoint) {
        return codePoint >= 0xe000 && codePoint <= 0xf8ff
                || codePoint >= 0xf0000 && codePoint <= 0xffffd
                || codePoint >= 0x100000 && codePoint <= 0x10fffd;
    }

    /// Appends a hexadecimal escape with a disambiguating space when required.
    ///
    /// @param result the destination
    /// @param codePoint the escaped code point
    /// @param source the complete unescaped string
    /// @param nextOffset the UTF-16 offset following the escaped code point
    private static void appendHexEscape(
            StringBuilder result,
            int codePoint,
            String source,
            int nextOffset
    ) {
        result.append('\\').append(Integer.toHexString(codePoint));
        if (nextOffset >= source.length()) {
            return;
        }
        var next = source.charAt(nextOffset);
        if (isHexDigit(next) || next == ' ' || next == '\t') {
            result.append(' ');
        }
    }

    /// Returns whether a character is an ASCII hexadecimal digit.
    ///
    /// @param character the character
    /// @return {@code true} for {@code 0}–{@code 9}, {@code A}–{@code F}, or
    /// {@code a}–{@code f}
    private static boolean isHexDigit(char character) {
        return character >= '0' && character <= '9'
                || character >= 'A' && character <= 'F'
                || character >= 'a' && character <= 'f';
    }
}
