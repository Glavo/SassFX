// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

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
        if (!hasQuotes || !quote) {
            return text;
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
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (character == '\\' || character == quoteChar) {
                result.append('\\').append(character);
                continue;
            }
            // Escape ASCII controls (except tab) and DEL the way dart-sass does.
            // Tab remains literal inside quoted strings.
            if (character != '\t' && (character < 0x20 || character == 0x7F)) {
                result.append('\\').append(Integer.toHexString(character));
                if (index + 1 < text.length()) {
                    var next = text.charAt(index + 1);
                    if (isHexDigit(next) || next == ' ' || next == '\t') {
                        result.append(' ');
                    }
                }
                continue;
            }
            result.append(character);
        }
        return result.append(quoteChar).toString();
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
