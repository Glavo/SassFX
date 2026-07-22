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
    /// @return the raw text or a double-quoted escaped string
    @Override
    public String toCssString() {
        return toCssString(true);
    }

    /// Returns this string with optional removal of surrounding quotes.
    ///
    /// @param quote whether a quoted string retains quotes
    /// @return the serialized string
    @Override
    public String toCssString(boolean quote) {
        if (!hasQuotes || !quote) {
            return text;
        }

        var result = new StringBuilder(text.length() + 2).append('"');
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (character == '\\' || character == '"') {
                result.append('\\');
            }
            if (character == '\n' || character == '\r' || character == '\f') {
                result.append('\\').append(Integer.toHexString(character)).append(' ');
            } else {
                result.append(character);
            }
        }
        return result.append('"').toString();
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
