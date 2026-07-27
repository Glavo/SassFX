// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Objects;

import static org.glavo.sassfx.JavaFXFeature.CONDITIONAL_STYLESHEET_IMPORTS;

/// Stores one parsed JavaFX stylesheet import.
///
/// @param resource   the decoded nonempty resource
/// @param conditions the optional media-query condition list
@ApiStatus.Internal
@NotNullByDefault
public record JavaFXCssImport(String resource, JavaFXMediaQuery conditions) {
    /// Validates parsed import components.
    public JavaFXCssImport {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(conditions, "conditions");
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("resource must not be empty");
        }
    }

    /// Parses one retained CSS import for a JavaFX release.
    ///
    /// @param cssImport    the retained import
    /// @param compatibility the selected JavaFX release
    /// @return the decoded resource and parsed conditions
    /// @throws CssSerializeException if the import grammar or condition is invalid
    public static JavaFXCssImport parse(
            CssImport cssImport,
            JavaFXTarget compatibility
    ) {
        Objects.requireNonNull(cssImport, "cssImport");
        Objects.requireNonNull(compatibility, "compatibility");
        var argument = cssImport.argument();
        var span = cssImport.span();
        var start = skipTrivia(argument, 0, span);
        if (start >= argument.length()) {
            throw failure("JavaFX CSS requires an @import URL.", span);
        }

        var first = argument.charAt(start);
        int end;
        String resource;
        if (first == '\'' || first == '"') {
            var decoded = new StringBuilder();
            end = appendQuoted(argument, start, first, decoded, span);
            resource = decoded.toString();
        } else if (start + 3 < argument.length()
                && argument.regionMatches(true, start, "url", 0, 3)
                && argument.charAt(start + 3) == '(') {
            var decoded = new StringBuilder();
            end = appendUrl(argument, start + 4, decoded, span);
            resource = decoded.toString().strip();
        } else {
            throw failure(
                    "JavaFX CSS requires @import to begin with a string or url() token.",
                    span
            );
        }
        if (resource.isEmpty()) {
            throw failure("JavaFX CSS requires a nonempty @import URL.", span);
        }

        var conditionStart = skipTrivia(argument, end, span);
        var condition = argument.substring(conditionStart).strip();
        if (condition.isEmpty()) {
            return new JavaFXCssImport(resource, new JavaFXMediaQuery(List.of()));
        }
        if (!compatibility.supports(CONDITIONAL_STYLESHEET_IMPORTS)) {
            throw failure(
                    "JavaFX " + compatibility.version()
                            + " CSS supports only unconditional @import rules.",
                    span
            );
        }
        return new JavaFXCssImport(
                resource,
                JavaFXMediaQueryValidator.parse(condition, span, compatibility)
        );
    }

    /// Decodes a quoted string and returns the offset after its closing quote.
    ///
    /// @param text   the complete import argument
    /// @param start  the opening quote offset
    /// @param quote  the opening quote character
    /// @param result the decoded resource destination
    /// @param span   the import source range
    /// @return the offset immediately following the closing quote
    private static int appendQuoted(
            String text,
            int start,
            char quote,
            StringBuilder result,
            SourceSpan span
    ) {
        for (var index = start + 1; index < text.length(); index++) {
            var current = text.charAt(index);
            if (current == quote) {
                return index + 1;
            }
            if (current == '\n' || current == '\r' || current == '\f') {
                throw failure("JavaFX CSS requires a closed @import string.", span);
            }
            if (current == '\\') {
                index = appendEscape(text, index, result);
            } else {
                result.append(current);
            }
        }
        throw failure("JavaFX CSS requires a closed @import string.", span);
    }

    /// Decodes a URL function and returns the offset after its closing parenthesis.
    ///
    /// @param text   the complete import argument
    /// @param start  the first offset inside `url(`
    /// @param result the decoded resource destination
    /// @param span   the import source range
    /// @return the offset immediately following the closing parenthesis
    private static int appendUrl(
            String text,
            int start,
            StringBuilder result,
            SourceSpan span
    ) {
        var index = skipWhitespace(text, start);
        if (index < text.length()
                && (text.charAt(index) == '\'' || text.charAt(index) == '"')) {
            var quote = text.charAt(index);
            index = appendQuoted(text, index, quote, result, span);
            index = skipWhitespace(text, index);
            if (index >= text.length() || text.charAt(index) != ')') {
                throw failure("JavaFX CSS requires a closed @import url() token.", span);
            }
            return index + 1;
        }
        var depth = 1;
        while (index < text.length()) {
            var current = text.charAt(index);
            if (current == '\\') {
                index = appendEscape(text, index, result);
            } else if (current == '(') {
                depth++;
                result.append(current);
            } else if (current == ')') {
                if (--depth == 0) {
                    return index + 1;
                }
                result.append(current);
            } else {
                result.append(current);
            }
            index++;
        }
        throw failure("JavaFX CSS requires a closed @import url() token.", span);
    }

    /// Appends one CSS escape and returns the final consumed offset.
    ///
    /// @param text   the complete import argument
    /// @param slash  the escape marker offset
    /// @param result the decoded resource destination
    /// @return the final offset consumed by the escape
    private static int appendEscape(String text, int slash, StringBuilder result) {
        if (slash + 1 >= text.length()) {
            return slash;
        }
        var index = slash + 1;
        var next = text.charAt(index);
        if (isHex(next)) {
            var value = 0;
            var count = 0;
            while (index < text.length() && count < 6 && isHex(text.charAt(index))) {
                value = value * 16 + Character.digit(text.charAt(index), 16);
                index++;
                count++;
            }
            if (index < text.length() && isWhitespace(text.charAt(index))) {
                if (text.charAt(index) == '\r'
                        && index + 1 < text.length()
                        && text.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                index--;
            }
            result.appendCodePoint(
                    value == 0 || value > Character.MAX_CODE_POINT
                            ? 0xfffd
                            : value
            );
            return index;
        }
        if (next == '\r'
                && index + 1 < text.length()
                && text.charAt(index + 1) == '\n') {
            return index + 1;
        }
        if (next != '\n' && next != '\r' && next != '\f') {
            result.append(next);
        }
        return index;
    }

    /// Skips whitespace and block comments.
    ///
    /// @param text  the complete import argument
    /// @param start the first candidate offset
    /// @param span  the import source range
    /// @return the first non-trivia offset or the text length
    private static int skipTrivia(String text, int start, SourceSpan span) {
        var index = start;
        while (true) {
            index = skipWhitespace(text, index);
            if (index + 1 >= text.length()
                    || text.charAt(index) != '/'
                    || text.charAt(index + 1) != '*') {
                return index;
            }
            var end = text.indexOf("*/", index + 2);
            if (end < 0) {
                throw failure("JavaFX CSS requires a closed @import comment.", span);
            }
            index = end + 2;
        }
    }

    /// Skips CSS whitespace.
    ///
    /// @param text  the text to inspect
    /// @param start the first candidate offset
    /// @return the first non-whitespace offset or the text length
    private static int skipWhitespace(String text, int start) {
        var index = start;
        while (index < text.length() && isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /// Returns whether a character is CSS whitespace.
    ///
    /// @param value the character to inspect
    /// @return whether the character is CSS whitespace
    private static boolean isWhitespace(char value) {
        return value == ' '
                || value == '\t'
                || value == '\n'
                || value == '\r'
                || value == '\f';
    }

    /// Returns whether a character is a hexadecimal digit.
    ///
    /// @param value the character to inspect
    /// @return whether the character is a hexadecimal digit
    private static boolean isHex(char value) {
        return Character.digit(value, 16) >= 0;
    }

    /// Creates a source-associated import failure.
    ///
    /// @param message the diagnostic message
    /// @param span    the import source range
    /// @return the serialization failure
    private static CssSerializeException failure(String message, SourceSpan span) {
        return new CssSerializeException(message, span, null);
    }
}
