// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Represents an immutable quoted or unquoted Sass string.
///
/// Quoting affects serialization but not Sass equality or hashing.
///
/// @param text      the semantic contents without surrounding quotes
/// @param hasQuotes whether serialization emits quotes
@ApiStatus.Internal
@NotNullByDefault
public record SassString(String text, boolean hasQuotes) implements SassValue {
    /// Creates a Sass string.
    public SassString {
        Objects.requireNonNull(text, "text");
    }

    /// Returns whether this is an unquoted empty string.
    ///
    /// @return whether CSS list serialization omits this string
    @Override
    public boolean isBlank() {
        return !hasQuotes && text.isEmpty();
    }

    /// Returns whether this unquoted string is a special CSS number function call.
    ///
    /// @return whether color constructors must preserve this as plain CSS
    @Override
    public boolean isSpecialNumber() {
        if (hasQuotes || text.length() < "min(_)".length()) {
            return false;
        }
        var lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("calc(")
                || lower.startsWith("clamp(")
                || lower.startsWith("min(")
                || lower.startsWith("max(")
                || lower.startsWith("var(")
                || lower.startsWith("env(")
                || lower.startsWith("attr(")
                || lower.startsWith("if(");
    }

    /// Returns whether this unquoted string is a special CSS variable function call.
    ///
    /// @return whether the string may expand to multiple arguments after substitution
    @Override
    public boolean isSpecialVariable() {
        if (hasQuotes || text.length() < "var(_)".length()) {
            return false;
        }
        var lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("var(")
                || lower.startsWith("attr(")
                || lower.startsWith("if(");
    }

    /// Returns this string.
    ///
    /// @return this string
    @Override
    public SassString assertString() {
        return this;
    }

    /// Concatenates another value while retaining this string's quote style.
    ///
    /// @param other the appended value
    /// @return the concatenated string
    /// @throws SassValueException if a non-string operand cannot be represented in CSS
    @Override
    public SassString plus(SassValue other) {
        return new SassString(
                text + (other instanceof SassString string
                        ? string.text
                        : other.toCssString()),
                hasQuotes
        );
    }

    /// Returns this string's CSS representation.
    ///
    /// @return the raw text or a quoted escaped string with dart-sass quote choice
    @Override
    public String toCssString() {
        return toCssString(true);
    }

    /// Returns this string with optional removal of surrounding quotes.
    ///
    /// Quoted strings choose single or double quotes the way dart-sass does:
    /// prefer double quotes, but switch to single quotes when the text contains
    /// double quotes and no single quotes. When both quote characters appear,
    /// double quotes are forced and interior doubles are escaped.
    ///
    /// @param quote whether a quoted string retains quotes
    /// @return the serialized string
    @Override
    public String toCssString(boolean quote) {
        return serialize(quote, false);
    }

    /// Returns this string with configurable quoting and output compaction.
    ///
    /// Compressed CSS emits private-use characters literally. Expanded CSS
    /// escapes every code point in a private-use plane so glyph-font values
    /// remain distinguishable.
    ///
    /// @param quote whether a quoted string retains quotes
    /// @param compressed whether optional escaping is omitted
    /// @return the serialized CSS string
    @Override
    public String toCssString(boolean quote, boolean compressed) {
        return serialize(quote, compressed);
    }

    /// Serializes this string for one CSS layout mode.
    ///
    /// @param quote whether a quoted string retains quotes
    /// @param compressed whether private-use escapes are omitted
    /// @return the serialized CSS string
    private String serialize(boolean quote, boolean compressed) {
        if (!hasQuotes || !quote) {
            // Unquoted emission folds newlines the way dart-sass
            // {@code _visitUnquotedString} does: each LF becomes a single space
            // and spaces immediately after that newline are dropped. Private-use
            // code points are escaped for glyph-font readability.
            var folded = foldUnquotedNewlines(text);
            return compressed ? folded : escapePrivateUseOnly(folded);
        }

        var includesSingleQuote = false;
        var includesDoubleQuote = false;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (character == '\'') {
                includesSingleQuote = true;
            } else if (character == '"') {
                includesDoubleQuote = true;
            }
        }
        // Prefer double quotes unless the text contains double quotes only.
        var quoteChar = includesDoubleQuote && !includesSingleQuote ? '\'' : '"';
        var forceDouble = includesDoubleQuote && includesSingleQuote;
        if (forceDouble) {
            quoteChar = '"';
        }

        var result = new StringBuilder(text.length() + 2).append(quoteChar);
        result.append(escapeSpecialCodePoints(text, quoteChar, !compressed));
        return result.append(quoteChar).toString();
    }

    /// Folds LF and following spaces for unquoted CSS string emission.
    ///
    /// @param text the raw unquoted string text
    /// @return text with newlines collapsed to single spaces
    private static String foldUnquotedNewlines(String text) {
        if (text.indexOf('\n') < 0) {
            return text;
        }
        var result = new StringBuilder(text.length());
        var afterNewline = false;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (character == '\n') {
                result.append(' ');
                afterNewline = true;
            } else if (character == ' ') {
                if (!afterNewline) {
                    result.append(' ');
                }
            } else {
                afterNewline = false;
                result.append(character);
            }
        }
        return result.toString();
    }

    /// Escapes private-use code points only (for unquoted CSS emission).
    private static String escapePrivateUseOnly(String text) {
        var result = new StringBuilder(text.length());
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (isPrivateUseBmp(character)) {
                index = appendHexEscape(result, character, text, index);
                continue;
            }
            if (Character.isHighSurrogate(character) && index + 1 < text.length()) {
                var low = text.charAt(index + 1);
                if (Character.isLowSurrogate(low)) {
                    int codePoint = Character.toCodePoint(character, low);
                    if (isPrivateUseSupplementary(codePoint)) {
                        index = appendHexEscape(result, codePoint, text, index + 1);
                        continue;
                    }
                }
            }
            result.append(character);
        }
        return result.toString();
    }

    /// Escapes controls, DEL, private-use code points, and optionally a quote
    /// character for CSS emission inside quoted strings.
    ///
    /// @param text      the semantic string text
    /// @param quoteChar the surrounding quote to backslash-escape, or {@code '\0'}
    /// @param escapePrivateUse whether code points in private-use planes are escaped
    /// @return the escaped body text without surrounding quotes
    private static String escapeSpecialCodePoints(
            String text,
            char quoteChar,
            boolean escapePrivateUse
    ) {
        var result = new StringBuilder(text.length());
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quoteChar != '\0' && (character == '\\' || character == quoteChar)) {
                result.append('\\').append(character);
                continue;
            }
            // Escape ASCII controls (except tab) and DEL the way dart-sass does.
            // Tab remains literal inside quoted strings.
            if (character != '\t' && (character < 0x20 || character == 0x7F)) {
                index = appendHexEscape(result, character, text, index);
                continue;
            }
            // Expanded mode prints Private Use Area code points as escape
            // codes so glyph-font code points stay distinguishable (dart-sass).
            if (escapePrivateUse && isPrivateUseBmp(character)) {
                index = appendHexEscape(result, character, text, index);
                continue;
            }
            if (Character.isHighSurrogate(character) && index + 1 < text.length()) {
                var low = text.charAt(index + 1);
                if (Character.isLowSurrogate(low)) {
                    int codePoint = Character.toCodePoint(character, low);
                    if (escapePrivateUse && isPrivateUseSupplementary(codePoint)) {
                        index = appendHexEscape(result, codePoint, text, index + 1);
                        continue;
                    }
                }
            }
            result.append(character);
        }
        return result.toString();
    }

    /// Appends a CSS hex escape for {@code codePoint} and returns the last source
    /// index consumed (for surrogate pairs this is the low surrogate index).
    private static int appendHexEscape(
            StringBuilder result,
            int codePoint,
            String text,
            int lastIndex
    ) {
        result.append('\\').append(Integer.toHexString(codePoint));
        if (lastIndex + 1 < text.length()) {
            var next = text.charAt(lastIndex + 1);
            if (isHexDigit(next) || next == ' ' || next == '\t') {
                result.append(' ');
            }
        }
        return lastIndex;
    }

    /// Returns whether {@code character} is a BMP private-use code unit.
    private static boolean isPrivateUseBmp(char character) {
        return character >= 0xE000 && character <= 0xF8FF;
    }

    /// Returns whether {@code codePoint} is in a supplementary private-use plane.
    private static boolean isPrivateUseSupplementary(int codePoint) {
        return codePoint >= 0xF0000 && codePoint <= 0xFFFFF
                || codePoint >= 0x100000 && codePoint <= 0x10FFFF;
    }

    /// Returns whether {@code character} is an ASCII hexadecimal digit.
    ///
    /// @param character the character to test
    /// @return whether it is {@code 0-9}, {@code a-f}, or {@code A-F}
    private static boolean isHexDigit(char character) {
        return character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f'
                || character >= 'A' && character <= 'F';
    }

    /// Compares semantic text while ignoring quote style.
    ///
    /// @param other the object to compare
    /// @return whether the semantic contents are equal
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof SassString string && text.equals(string.text);
    }

    /// Returns the semantic text hash.
    ///
    /// @return the string hash
    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /// Returns the inspect-mode Sass representation.
    ///
    /// @return the same representation as [#toCssString()]
    @Override
    public String toString() {
        return toCssString();
    }
}
