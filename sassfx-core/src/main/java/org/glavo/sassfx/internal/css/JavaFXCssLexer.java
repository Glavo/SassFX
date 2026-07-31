// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Objects;
import java.util.Set;

/// Provides the lexical primitives shared by JavaFX's legacy CSS parser.
///
/// JavaFX stylesheet parsing uses an ASCII-only identifier grammar rather than
/// the identifier grammar from current CSS Syntax. Strings and URL payloads
/// remain opaque and may contain non-ASCII characters. This class models that
/// boundary without loading JavaFX classes.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXCssLexer {
    /// Contains the case-insensitive units recognized after JavaFX numbers.
    private static final @Unmodifiable Set<String> UNITS = Set.of(
            "%",
            "cm",
            "deg",
            "em",
            "ex",
            "grad",
            "in",
            "mm",
            "ms",
            "pc",
            "pt",
            "px",
            "rad",
            "s",
            "turn"
    );

    /// Prevents instantiation.
    private JavaFXCssLexer() {
    }

    /// Returns the end of one JavaFX identifier token.
    ///
    /// An identifier begins with an optional single hyphen followed by `_` or
    /// an ASCII letter. Its remaining characters may additionally contain
    /// ASCII digits and hyphens.
    ///
    /// @param text  the complete text containing the candidate
    /// @param start the candidate's inclusive start offset
    /// @return the exclusive identifier end, or `start` when no identifier
    /// starts at that offset
    /// @throws IndexOutOfBoundsException if `start` is negative or greater than
    /// the text length
    public static int identifierEnd(String text, int start) {
        Objects.requireNonNull(text, "text");
        if (start < 0 || start > text.length()) {
            throw new IndexOutOfBoundsException("start: " + start);
        }
        if (start == text.length()) {
            return start;
        }

        var index = start;
        if (text.charAt(index) == '-') {
            index++;
            if (index == text.length()
                    || !isIdentifierStart(text.charAt(index))) {
                return start;
            }
        } else if (!isIdentifierStart(text.charAt(index))) {
            return start;
        }

        index++;
        while (index < text.length() && isNameCharacter(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /// Returns whether the complete text is one JavaFX identifier token.
    ///
    /// @param text the identifier candidate
    /// @return whether the complete candidate follows JavaFX's identifier
    /// grammar
    public static boolean isIdentifier(String text) {
        Objects.requireNonNull(text, "text");
        return !text.isEmpty() && identifierEnd(text, 0) == text.length();
    }

    /// Returns whether the complete text is one JavaFX hash-token name.
    ///
    /// Hash names use identifier continuation characters but may begin with an
    /// ASCII digit or hyphen.
    ///
    /// @param text the candidate without its leading `#`
    /// @return whether JavaFX can include the complete text in one hash token
    public static boolean isHashName(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return false;
        }
        for (var index = 0; index < text.length(); index++) {
            if (!isNameCharacter(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether a complete declaration value stays within JavaFX's
    /// legacy lexical forms.
    ///
    /// This checks tokenization only. Property-specific value grammar is
    /// validated separately. Non-ASCII characters and backslash escapes are
    /// accepted inside strings and exact lowercase `url(...)` tokens, but not
    /// in identifiers or other unquoted value text.
    ///
    /// @param text the emitted declaration value
    /// @return whether the value contains no token rejected by JavaFX's legacy
    /// lexer
    public static boolean isTokenizableValue(String text) {
        Objects.requireNonNull(text, "text");
        var index = 0;
        while (index < text.length()) {
            var triviaEnd = triviaEnd(text, index);
            if (triviaEnd < 0) {
                return false;
            }
            if (triviaEnd > index) {
                index = triviaEnd;
                continue;
            }
            var character = text.charAt(index);
            if (character == '\'' || character == '"') {
                index = stringEnd(text, index, character);
                if (index < 0) {
                    return false;
                }
                continue;
            }
            if (character == '!') {
                index = importanceEnd(text, index);
                if (index < 0) {
                    return false;
                }
                continue;
            }
            if (character == '#') {
                var end = index + 1;
                while (end < text.length()
                        && isNameCharacter(text.charAt(end))) {
                    end++;
                }
                if (end == index + 1) {
                    return false;
                }
                index = end;
                continue;
            }

            var identifierEnd = identifierEnd(text, index);
            if (identifierEnd > index) {
                if (identifierEnd < text.length()
                        && text.charAt(identifierEnd) == '('
                        && identifierEnd == index + 3
                        && text.regionMatches(
                        true,
                        index,
                        "url",
                        0,
                        "url".length()
                )) {
                    if (!text.startsWith("url", index)) {
                        return false;
                    }
                    index = urlEnd(text, identifierEnd + 1);
                    if (index < 0) {
                        return false;
                    }
                } else {
                    index = identifierEnd;
                }
                continue;
            }

            var numberEnd = numberEnd(text, index);
            if (numberEnd > index) {
                index = numberEnd;
                continue;
            }
            if (numberEnd < 0) {
                return false;
            }

            if (isPunctuation(character)) {
                index++;
                continue;
            }
            return false;
        }
        return true;
    }

    /// Returns whether a character is JavaFX CSS whitespace.
    ///
    /// @param value the character to inspect
    /// @return whether the character is space, tab, line feed, carriage return,
    /// or form feed
    public static boolean isWhitespace(char value) {
        return value == ' '
                || value == '\t'
                || value == '\n'
                || value == '\r'
                || value == '\f';
    }

    /// Returns the end of JavaFX whitespace and comment trivia.
    ///
    /// A line comment must reach a line-feed character. Without it, OpenJFX
    /// consumes the remainder of the stylesheet, including the declaration
    /// and rule terminators that follow a serialized value.
    ///
    /// @param text  the complete text containing the trivia
    /// @param start the inclusive offset at which trivia may begin
    /// @return the first non-trivia offset, or `-1` for a comment that would
    /// consume the remainder of the stylesheet
    /// @throws IndexOutOfBoundsException if `start` is negative or greater than
    /// the text length
    public static int triviaEnd(String text, int start) {
        Objects.requireNonNull(text, "text");
        if (start < 0 || start > text.length()) {
            throw new IndexOutOfBoundsException("start: " + start);
        }

        var index = start;
        while (index < text.length()) {
            if (isWhitespace(text.charAt(index))) {
                index++;
                continue;
            }
            if (index + 1 >= text.length() || text.charAt(index) != '/') {
                break;
            }
            var next = text.charAt(index + 1);
            if (next == '*') {
                var end = text.indexOf("*/", index + 2);
                if (end < 0) {
                    return -1;
                }
                index = end + 2;
                continue;
            }
            if (next == '/') {
                var end = text.indexOf('\n', index + 2);
                if (end < 0) {
                    return -1;
                }
                index = end + 1;
                continue;
            }
            break;
        }
        return index;
    }

    /// Returns whether one character can begin an identifier after an optional
    /// leading hyphen.
    ///
    /// @param value the character to inspect
    /// @return whether the character is `_` or an ASCII letter
    private static boolean isIdentifierStart(char value) {
        return value == '_'
                || value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z';
    }

    /// Returns whether one character may continue an identifier or hash name.
    ///
    /// @param value the character to inspect
    /// @return whether the character is `_`, `-`, an ASCII letter, or an ASCII
    /// digit
    private static boolean isNameCharacter(char value) {
        return isIdentifierStart(value)
                || value == '-'
                || value >= '0' && value <= '9';
    }

    /// Returns the end of one JavaFX string token.
    ///
    /// The legacy lexer treats the first matching quote as the end and does not
    /// interpret CSS escapes in ordinary string tokens.
    ///
    /// @param text  the complete value text
    /// @param start the opening quote offset
    /// @param quote the opening quote character
    /// @return the offset after the closing quote, or `-1` when absent
    private static int stringEnd(String text, int start, char quote) {
        var end = text.indexOf(quote, start + 1);
        return end < 0 ? -1 : end + 1;
    }

    /// Returns the end of one JavaFX `!important` token.
    ///
    /// JavaFX whitespace and comments may separate `!` from the
    /// case-insensitive `important` keyword.
    ///
    /// @param text  the complete value text
    /// @param start the `!` offset
    /// @return the offset after `important`, or `-1` when the token is malformed
    /// @throws IndexOutOfBoundsException if `start` is negative or not less
    /// than the text length
    public static int importanceEnd(String text, int start) {
        Objects.requireNonNull(text, "text");
        Objects.checkIndex(start, text.length());
        if (text.charAt(start) != '!') {
            return -1;
        }
        var index = triviaEnd(text, start + 1);
        if (index < 0) {
            return -1;
        }
        return index + "important".length() <= text.length()
                && text.regionMatches(
                true,
                index,
                "important",
                0,
                "important".length()
        )
                ? index + "important".length()
                : -1;
    }

    /// Returns the end of one exact lowercase JavaFX URL token.
    ///
    /// @param text  the complete value text
    /// @param start the first character after `url(`
    /// @return the offset after the closing parenthesis, or `-1` when the URL
    /// token is malformed
    private static int urlEnd(String text, int start) {
        var index = start;
        while (index < text.length() && isWhitespace(text.charAt(index))) {
            index++;
        }
        if (index < text.length()
                && (text.charAt(index) == '\'' || text.charAt(index) == '"')) {
            var quote = text.charAt(index++);
            while (index < text.length() && text.charAt(index) != quote) {
                var character = text.charAt(index++);
                if (character == '\r'
                        || character == '\n') {
                    return -1;
                }
                if (character == '\\' && index < text.length()) {
                    if (text.charAt(index) == '\r'
                            || text.charAt(index) == '\n') {
                        while (index < text.length()
                                && (text.charAt(index) == '\r'
                                || text.charAt(index) == '\n')) {
                            index++;
                        }
                    } else {
                        index++;
                    }
                }
            }
            if (index == text.length()) {
                return -1;
            }
            index++;
            while (index < text.length() && isWhitespace(text.charAt(index))) {
                index++;
            }
            return index < text.length() && text.charAt(index) == ')'
                    ? index + 1
                    : -1;
        }

        while (index < text.length()) {
            var character = text.charAt(index++);
            if (character == ')') {
                return index;
            }
            if (character == '\'' || character == '"' || character == '(') {
                return -1;
            }
            if (character == '\\' && index < text.length()) {
                if (text.charAt(index) == '\r'
                        || text.charAt(index) == '\n') {
                    while (index < text.length()
                            && (text.charAt(index) == '\r'
                            || text.charAt(index) == '\n')) {
                        index++;
                    }
                } else {
                    index++;
                }
            }
        }
        return -1;
    }

    /// Returns the end of one JavaFX number token.
    ///
    /// @param text  the complete value text
    /// @param start the candidate start offset
    /// @return the exclusive number end, `start` when no number starts there,
    /// or `-1` when a number begins but has an unsupported suffix
    private static int numberEnd(String text, int start) {
        var index = start;
        if (text.charAt(index) == '+' || text.charAt(index) == '-') {
            index++;
            if (index == text.length()) {
                return start;
            }
        }

        var integerStart = index;
        while (index < text.length() && isAsciiDigit(text.charAt(index))) {
            index++;
        }
        var hasIntegerDigits = index > integerStart;
        if (index < text.length() && text.charAt(index) == '.') {
            if (index + 1 >= text.length()
                    || !isAsciiDigit(text.charAt(index + 1))) {
                return hasIntegerDigits ? -1 : start;
            }
            index += 2;
            while (index < text.length() && isAsciiDigit(text.charAt(index))) {
                index++;
            }
        } else if (!hasIntegerDigits) {
            return start;
        }

        var unitStart = index;
        while (index < text.length()) {
            var character = text.charAt(index);
            if (character == '%'
                    || character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z') {
                index++;
            } else {
                break;
            }
        }
        if (unitStart == index) {
            return index;
        }
        var unit = text.substring(unitStart, index);
        return UNITS.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(unit))
                ? index
                : -1;
    }

    /// Returns whether one character is an ASCII digit.
    ///
    /// @param value the character to inspect
    /// @return whether the character is between `0` and `9`
    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    /// Returns whether one character is emitted as a standalone JavaFX token.
    ///
    /// @param value the character to inspect
    /// @return whether the character is supported punctuation
    private static boolean isPunctuation(char value) {
        return switch (value) {
            case '/', '>', '<', '=', '{', '}', ';', ':', '*', '(', ')',
                 ',', '.', '@' -> true;
            default -> false;
        };
    }
}
