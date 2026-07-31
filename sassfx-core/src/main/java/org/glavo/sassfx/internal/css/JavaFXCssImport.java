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
/// @param resource   the nonempty resource text produced by JavaFX tokenization
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
    /// @return the tokenized resource text and parsed conditions
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
            var resourceText = new StringBuilder();
            end = appendImportString(
                    argument,
                    start,
                    first,
                    resourceText,
                    span
            );
            resource = resourceText.toString();
        } else if (start + 3 < argument.length()
                && argument.startsWith("url", start)
                && argument.charAt(start + 3) == '(') {
            var resourceText = new StringBuilder();
            end = appendUrl(argument, start + 4, resourceText, span);
            resource = resourceText.toString();
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

    /// Copies a legacy JavaFX string token and returns its closing offset.
    ///
    /// The legacy lexer does not interpret escapes in ordinary strings. A
    /// quote preceded by `\` therefore still closes the token.
    ///
    /// @param text   the complete import argument
    /// @param start  the opening quote offset
    /// @param quote  the opening quote character
    /// @param result the resource-text destination
    /// @param span   the import source range
    /// @return the offset immediately following the closing quote
    private static int appendImportString(
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
            result.append(current);
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
            return appendQuotedUrl(text, index, result, span);
        }
        while (index < text.length()) {
            var current = text.charAt(index++);
            if (JavaFXCssLexer.isWhitespace(current)) {
                continue;
            }
            if (current == ')') {
                return index;
            }
            if (current == '\\') {
                index = appendUrlEscape(text, index, result, span);
                continue;
            }
            if (current == '\'' || current == '"' || current == '(') {
                throw failure(
                        "JavaFX CSS requires a valid @import url() token.",
                        span
                );
            }
            result.append(current);
        }
        throw failure("JavaFX CSS requires a closed @import url() token.", span);
    }

    /// Decodes a quoted JavaFX URL token.
    ///
    /// @param text   the complete import argument
    /// @param start  the opening quote offset
    /// @param result the decoded resource destination
    /// @param span   the import source range
    /// @return the offset immediately following the closing parenthesis
    private static int appendQuotedUrl(
            String text,
            int start,
            StringBuilder result,
            SourceSpan span
    ) {
        var quote = text.charAt(start);
        var index = start + 1;
        while (index < text.length()) {
            var current = text.charAt(index++);
            if (current == quote) {
                index = skipWhitespace(text, index);
                if (index < text.length() && text.charAt(index) == ')') {
                    return index + 1;
                }
                break;
            }
            if (current == '\r' || current == '\n') {
                break;
            }
            if (current == '\\') {
                index = appendUrlEscape(text, index, result, span);
            } else {
                result.append(current);
            }
        }
        throw failure("JavaFX CSS requires a closed @import url() token.", span);
    }

    /// Appends one JavaFX URL escape and returns the next unread offset.
    ///
    /// JavaFX removes one escape marker without interpreting hexadecimal
    /// escapes. Escaped CR and LF characters are consumed without output.
    ///
    /// @param text   the complete import argument
    /// @param index  the offset immediately after the escape marker
    /// @param result the decoded resource destination
    /// @param span   the import source range
    /// @return the next unread offset
    private static int appendUrlEscape(
            String text,
            int index,
            StringBuilder result,
            SourceSpan span
    ) {
        if (index >= text.length()) {
            throw failure("JavaFX CSS requires a valid @import url() escape.", span);
        }
        var escaped = text.charAt(index++);
        if (escaped == '\r') {
            return index < text.length() && text.charAt(index) == '\n'
                    ? index + 1
                    : index;
        }
        if (escaped != '\n') {
            result.append(escaped);
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
        while (index < text.length()
                && JavaFXCssLexer.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
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
