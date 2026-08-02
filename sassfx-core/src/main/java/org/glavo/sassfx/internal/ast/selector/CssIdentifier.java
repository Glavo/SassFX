// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a decoded selector identifier with its canonical Sass serialization.
///
/// {@code value} is used for semantic comparisons. {@code css} is a legal
/// selector spelling that preserves that value at an identifier boundary.
///
/// @param value the decoded identifier value
/// @param css   the canonical selector serialization of {@code value}
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
        if (!css.equals(serializeSelector(value))) {
            throw new IllegalArgumentException("css must be a canonical CSS serialization");
        }
    }

    /// Creates a CSS identifier from its decoded value.
    ///
    /// @param value the decoded identifier value
    /// @return an identifier with a canonical CSS-safe serialization
    /// @throws IllegalArgumentException if {@code value} is empty
    public static CssIdentifier of(String value) {
        var normalized = normalize(Objects.requireNonNull(value, "value"));
        return new CssIdentifier(normalized, serializeSelector(normalized));
    }

    /// Converts arbitrary text to a CSS identifier using minimal hexadecimal escapes.
    ///
    /// This implements the string utility used for generated identifier text.
    /// Selector AST serialization uses [#toCssString()] so it can preserve Sass's
    /// canonical simple escapes and mandatory hexadecimal terminators.
    ///
    /// @param value the text to convert
    /// @return a syntactically valid CSS identifier spelling
    /// @throws IllegalArgumentException if {@code value} is empty or contains
    ///                                  a NUL or an unpaired surrogate
    public static String toCssIdentifier(String value) {
        return serializeGenerated(Objects.requireNonNull(value, "value"));
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

    /// Replaces values that CSS preprocessing cannot represent with U+FFFD.
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

    /// Serializes a decoded identifier using Sass selector escape conventions.
    ///
    /// @param value the normalized decoded identifier value
    /// @return the canonical selector spelling
    private static String serializeSelector(String value) {
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

            if (isNameCodePoint(codePoint)
                    && !(first && isDigit(codePoint))
                    && !(secondAfterHyphen && isDigit(codePoint))) {
                if (first && codePoint == '-' && codePointCount == 1) {
                    result.append("\\-");
                } else {
                    result.appendCodePoint(codePoint);
                }
            } else if (first && isDigit(codePoint)
                    || secondAfterHyphen && isDigit(codePoint)) {
                appendSelectorHexEscape(result, codePoint);
            } else if (isPrintable(codePoint)) {
                result.append('\\').appendCodePoint(codePoint);
            } else {
                appendSelectorHexEscape(result, codePoint);
            }

            offset += Character.charCount(codePoint);
            codePointIndex++;
        }
        return result.toString();
    }

    /// Serializes arbitrary text as a generated CSS identifier.
    ///
    /// @param value the decoded identifier value
    /// @return a syntactically valid CSS identifier spelling
    /// @throws IllegalArgumentException if {@code value} is empty or contains
    ///                                  a NUL or an unpaired surrogate
    private static String serializeGenerated(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value must not be empty");
        }

        var result = new StringBuilder();
        var codePointIndex = 0;
        var codePointCount = value.codePointCount(0, value.length());
        for (var offset = 0; offset < value.length(); ) {
            var character = value.charAt(offset);
            if (character == 0) {
                throw new IllegalArgumentException(
                        "NUL cannot be represented as a CSS identifier"
                );
            }
            if (Character.isSurrogate(character)
                    && (offset + 1 >= value.length()
                    || !Character.isSurrogatePair(
                            character,
                            value.charAt(offset + 1)
                    ))) {
                throw new IllegalArgumentException(
                        "An unpaired surrogate cannot be represented as a CSS identifier"
                );
            }

            var codePoint = value.codePointAt(offset);
            var first = codePointIndex == 0;
            var secondAfterHyphen = codePointIndex == 1 && value.charAt(0) == '-';
            var nextOffset = offset + Character.charCount(codePoint);
            var nextCodePoint = nextOffset < value.length()
                    ? value.codePointAt(nextOffset)
                    : -1;

            if (isNameCodePoint(codePoint)
                    && !isPrivateUse(codePoint)
                    && !(first && isDigit(codePoint))
                    && !(secondAfterHyphen && isDigit(codePoint))) {
                if (first && codePoint == '-' && codePointCount == 1) {
                    appendGeneratedHexEscape(result, codePoint, nextCodePoint);
                } else {
                    result.appendCodePoint(codePoint);
                }
            } else {
                appendGeneratedHexEscape(result, codePoint, nextCodePoint);
            }

            offset = nextOffset;
            codePointIndex++;
        }
        return result.toString();
    }

    /// Appends a hexadecimal CSS escape and any required terminator.
    ///
    /// @param result    the destination buffer
    /// @param codePoint the escaped code point
    /// @param nextCodePoint the following code point, or `-1` at end of input
    private static void appendGeneratedHexEscape(
            StringBuilder result,
            int codePoint,
            int nextCodePoint
    ) {
        result.append('\\').append(Integer.toHexString(codePoint));
        if (isHexDigit(nextCodePoint)) {
            result.append(' ');
        }
    }

    /// Appends a hexadecimal selector escape with a mandatory terminator.
    ///
    /// @param result the destination buffer
    /// @param codePoint the escaped code point
    private static void appendSelectorHexEscape(StringBuilder result, int codePoint) {
        result.append('\\').append(Integer.toHexString(codePoint)).append(' ');
    }

    /// Returns whether CSS preprocessing replaces one code point.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether the code point cannot be represented directly
    private static boolean isInvalidCodePoint(int codePoint) {
        return codePoint == 0
                || codePoint >= Character.MIN_SURROGATE
                && codePoint <= Character.MAX_SURROGATE;
    }

    /// Returns whether a code point belongs to a Unicode private-use area.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether the code point must be escaped for stable CSS output
    private static boolean isPrivateUse(int codePoint) {
        return codePoint >= 0xE000 && codePoint <= 0xF8FF
                || codePoint >= 0xF0000 && codePoint <= 0xFFFFF
                || codePoint >= 0x100000 && codePoint <= 0x10FFFF;
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

    /// Returns whether a code point can use a one-code-point selector escape.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether a simple escape is valid
    private static boolean isPrintable(int codePoint) {
        return codePoint >= 0x20 && codePoint != 0x7F;
    }

    /// Returns whether a code point is an ASCII hexadecimal digit.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether an immediately preceding hexadecimal escape needs a
    /// terminator
    private static boolean isHexDigit(int codePoint) {
        return isDigit(codePoint)
                || codePoint >= 'A' && codePoint <= 'F'
                || codePoint >= 'a' && codePoint <= 'f';
    }

    /// Returns whether a code point is an ASCII decimal digit.
    ///
    /// @param codePoint the code point to inspect
    /// @return whether the code point is a digit
    private static boolean isDigit(int codePoint) {
        return codePoint >= '0' && codePoint <= '9';
    }
}
