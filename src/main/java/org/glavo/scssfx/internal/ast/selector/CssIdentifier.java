// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a decoded CSS identifier with a canonical CSS serialization.
///
/// {@code value} is used for semantic comparisons. {@code css} is a legal
/// identifier spelling that preserves that value at an identifier boundary.
///
/// @param value the decoded identifier value
/// @param css   the canonical CSS-safe serialization of {@code value}
@ApiStatus.Internal
@NotNullByDefault
public record CssIdentifier(String value, String css) {
    /// Creates one CSS identifier.
    ///
    /// @throws IllegalArgumentException if either value is empty or
    ///                                  {@code css} is not its canonical serialization
    public CssIdentifier {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(css, "css");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value must not be empty");
        }
        if (!css.equals(serialize(value))) {
            throw new IllegalArgumentException("css must be a canonical CSS serialization");
        }
    }

    /// Creates a CSS identifier from its decoded value.
    ///
    /// @param value the decoded identifier value
    /// @return an identifier with a canonical CSS-safe serialization
    public static CssIdentifier of(String value) {
        var normalized = normalize(Objects.requireNonNull(value, "value"));
        return new CssIdentifier(normalized, serialize(normalized));
    }

    /// Returns a new identifier formed by semantic concatenation.
    ///
    /// @param suffix the identifier suffix
    /// @return the concatenated identifier
    public CssIdentifier append(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return of(value + suffix.value);
    }

    /// Returns whether this identifier has the same decoded value as another.
    ///
    /// @param other the identifier to compare
    /// @return whether both values are equal
    public boolean hasSameValue(CssIdentifier other) {
        return value.equals(Objects.requireNonNull(other, "other").value);
    }

    /// Returns the canonical CSS identifier spelling.
    ///
    /// @return the CSS serialization
    public String toCssString() {
        return css;
    }

    /// Replaces values that CSS cannot represent with the replacement character.
    ///
    /// @param value the decoded identifier value
    /// @return a value containing only CSS-representable Unicode code points
    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        var result = new StringBuilder(value.length());
        for (var offset = 0; offset < value.length(); ) {
            var codePoint = value.codePointAt(offset);
            result.appendCodePoint(
                    isInvalidCodePoint(codePoint) ? 0xFFFD : codePoint
            );
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    /// Serializes one decoded identifier without relying on its original source.
    ///
    /// @param value the decoded identifier value
    /// @return a syntactically valid CSS identifier spelling
    private static String serialize(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value must not be empty");
        }

        var result = new StringBuilder();
        var codePointIndex = 0;
        var codePointCount = value.codePointCount(0, value.length());
        for (var offset = 0; offset < value.length(); ) {
            var codePoint = value.codePointAt(offset);
            var first = codePointIndex == 0;
            var secondAfterHyphen = codePointIndex == 1 && value.charAt(0) == '-';

            if (isInvalidCodePoint(codePoint)) {
                appendHexEscape(result, 0xFFFD);
            } else if (isNameCodePoint(codePoint)
                    && !(first && isDigit(codePoint))
                    && !(secondAfterHyphen && isDigit(codePoint))) {
                if (first && codePoint == '-' && codePointCount == 1) {
                    result.append("\\-");
                } else {
                    result.appendCodePoint(codePoint);
                }
            } else if (first && isDigit(codePoint)
                    || secondAfterHyphen && isDigit(codePoint)) {
                appendHexEscape(result, codePoint);
            } else if (isPrintable(codePoint)) {
                result.append('\\').appendCodePoint(codePoint);
            } else {
                appendHexEscape(result, codePoint);
            }

            offset += Character.charCount(codePoint);
            codePointIndex++;
        }
        return result.toString();
    }

    /// Appends a hexadecimal CSS escape with a mandatory terminator.
    ///
    /// @param result    the destination buffer
    /// @param codePoint the escaped code point
    private static void appendHexEscape(StringBuilder result, int codePoint) {
        result.append('\\').append(Integer.toHexString(codePoint)).append(' ');
    }

    /// Returns whether CSS must replace one code point during preprocessing.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether the code point cannot be represented by CSS text
    private static boolean isInvalidCodePoint(int codePoint) {
        return codePoint == 0
                || codePoint >= Character.MIN_SURROGATE
                && codePoint <= Character.MAX_SURROGATE;
    }

    /// Returns whether a code point is valid in an unescaped CSS name.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether the code point may be written literally
    private static boolean isNameCodePoint(int codePoint) {
        return codePoint == '-'
                || codePoint == '_'
                || isDigit(codePoint)
                || codePoint >= 'A' && codePoint <= 'Z'
                || codePoint >= 'a' && codePoint <= 'z'
                || codePoint >= 0x80;
    }

    /// Returns whether a code point can be escaped using a single literal unit.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether a simple escape is valid
    private static boolean isPrintable(int codePoint) {
        return codePoint >= 0x20 && codePoint != 0x7F;
    }

    /// Returns whether a code point is an ASCII decimal digit.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether the code point is a digit
    private static boolean isDigit(int codePoint) {
        return codePoint >= '0' && codePoint <= '9';
    }
}
