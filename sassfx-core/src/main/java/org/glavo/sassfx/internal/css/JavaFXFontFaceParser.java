// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Parses the JavaFX `@font-face` value grammar shared by CSS and BSS output.
///
/// JavaFX stores URL, local, and identifier-reference sources separately. It
/// also stores non-`src` descriptors as concatenated lexer token text rather
/// than as their original CSS spelling. This class models both behaviors
/// without loading JavaFX classes.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXFontFaceParser {
    /// Prevents instantiation.
    private JavaFXFontFaceParser() {
    }

    /// Parses one comma-separated JavaFX `src` descriptor value.
    ///
    /// URL resources are decoded according to JavaFX's legacy URL lexer but
    /// remain unresolved. Callers producing BSS must resolve them against the
    /// stylesheet URL before writing the binary source entry.
    ///
    /// @param text the evaluated CSS descriptor value
    /// @param span the source range associated with the value
    /// @return immutable JavaFX font sources in declaration order
    /// @throws CssSerializeException if the value would be rejected, partially
    /// consumed, or silently reinterpreted by JavaFX
    public static @Unmodifiable List<Source> parseSources(
            String text,
            SourceSpan span
    ) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(span, "span");

        var sources = new ArrayList<Source>();
        var index = skipTrivia(text, 0, span);
        if (index == text.length()) {
            throw invalidSource(span);
        }

        while (true) {
            var identifierEnd = JavaFXCssLexer.identifierEnd(text, index);
            if (identifierEnd == index) {
                throw invalidSource(span);
            }
            var name = text.substring(index, identifierEnd);
            if (identifierEnd < text.length()
                    && text.charAt(identifierEnd) == '(') {
                if (name.equals("url")) {
                    var url = parseUrl(text, identifierEnd + 1, span);
                    index = skipTrivia(text, url.end(), span);
                    @Nullable String format = null;
                    var formatEnd = JavaFXCssLexer.identifierEnd(text, index);
                    if (formatEnd > index
                            && formatEnd - index == "format".length()
                            && text.regionMatches(
                            true,
                            index,
                            "format",
                            0,
                            "format".length()
                    )
                            && formatEnd < text.length()
                            && text.charAt(formatEnd) == '(') {
                        var parsedFormat = parseSimpleArgument(
                                text,
                                formatEnd + 1,
                                span
                        );
                        format = parsedFormat.value();
                        index = parsedFormat.end();
                    }
                    sources.add(new Source(SourceType.URL, url.value(), format));
                } else if (name.equalsIgnoreCase("local")) {
                    var local = parseSimpleArgument(
                            text,
                            identifierEnd + 1,
                            span
                    );
                    sources.add(new Source(SourceType.LOCAL, local.value(), null));
                    index = local.end();
                } else {
                    throw invalidSource(span);
                }
            } else {
                sources.add(new Source(SourceType.REFERENCE, name, null));
                index = identifierEnd;
            }

            index = skipTrivia(text, index, span);
            if (index == text.length()) {
                return List.copyOf(sources);
            }
            if (text.charAt(index) != ',') {
                throw invalidSource(span);
            }
            index = skipTrivia(text, index + 1, span);
            if (index == text.length()) {
                throw invalidSource(span);
            }
        }
    }

    /// Returns the descriptor text persisted by JavaFX's font-face model.
    ///
    /// JavaFX removes whitespace and comments between tokens, normalizes an
    /// importance token to lowercase, and stores the decoded resource text of
    /// each URL token without its `url(...)` wrapper.
    ///
    /// @param text the emitted non-`src` descriptor value
    /// @param span the source range associated with the value
    /// @return the exact descriptor text stored in JavaFX BSS
    /// @throws CssSerializeException if JavaFX cannot safely tokenize and
    /// persist the value
    public static String storedDescriptorValue(String text, SourceSpan span) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(span, "span");
        if (!JavaFXCssLexer.isTokenizableValue(text)) {
            throw new CssSerializeException(
                    "JavaFX CSS cannot tokenize this declaration value.",
                    span,
                    null
            );
        }

        var result = new StringBuilder(text.length());
        var index = 0;
        while (index < text.length()) {
            var triviaEnd = skipTrivia(text, index, span);
            if (triviaEnd > index) {
                index = triviaEnd;
                continue;
            }

            var character = text.charAt(index);
            if (character == '\'' || character == '"') {
                var end = text.indexOf(character, index + 1);
                if (end < 0) {
                    throw invalidDescriptor(span);
                }
                result.append(text, index, end + 1);
                index = end + 1;
                continue;
            }
            if (character == '!') {
                var end = JavaFXCssLexer.importanceEnd(text, index);
                if (end < 0) {
                    throw invalidDescriptor(span);
                }
                result.append("!important");
                index = end;
                continue;
            }

            var identifierEnd = JavaFXCssLexer.identifierEnd(text, index);
            if (identifierEnd == index + "url".length()
                    && text.startsWith("url", index)
                    && identifierEnd < text.length()
                    && text.charAt(identifierEnd) == '(') {
                var url = parseUrl(text, identifierEnd + 1, span);
                result.append(url.value());
                index = url.end();
                continue;
            }

            result.append(character);
            index++;
        }
        return result.toString();
    }

    /// Parses one quoted string or identifier followed by a closing parenthesis.
    ///
    /// @param text  the complete source-list text
    /// @param start the first offset inside the function
    /// @param span  the source range associated with the value
    /// @return the parsed argument and offset after its closing parenthesis
    /// @throws CssSerializeException if the argument contains another token
    private static ParsedValue parseSimpleArgument(
            String text,
            int start,
            SourceSpan span
    ) {
        var index = skipTrivia(text, start, span);
        if (index == text.length()) {
            throw invalidSource(span);
        }

        String value;
        var first = text.charAt(index);
        if (first == '\'' || first == '"') {
            var end = text.indexOf(first, index + 1);
            if (end < 0) {
                throw invalidSource(span);
            }
            value = text.substring(index + 1, end);
            index = end + 1;
        } else {
            var end = JavaFXCssLexer.identifierEnd(text, index);
            if (end == index) {
                throw invalidSource(span);
            }
            value = text.substring(index, end);
            index = end;
        }

        index = skipTrivia(text, index, span);
        if (index == text.length() || text.charAt(index) != ')') {
            throw invalidSource(span);
        }
        return new ParsedValue(value, index + 1);
    }

    /// Decodes one exact lowercase JavaFX URL token.
    ///
    /// @param text  the complete descriptor text
    /// @param start the first offset inside `url(`
    /// @param span  the source range associated with the value
    /// @return the decoded resource and offset after the closing parenthesis
    /// @throws CssSerializeException if the URL token is empty or malformed
    private static ParsedValue parseUrl(
            String text,
            int start,
            SourceSpan span
    ) {
        var index = skipWhitespace(text, start);
        if (index == text.length()) {
            throw invalidUrl(span);
        }

        var resource = new StringBuilder();
        var first = text.charAt(index);
        if (first == '\'' || first == '"') {
            var quote = first;
            index++;
            var closed = false;
            while (index < text.length()) {
                var character = text.charAt(index++);
                if (character == quote) {
                    closed = true;
                    break;
                }
                if (isNewline(character)) {
                    throw invalidUrl(span);
                }
                if (character == '\\') {
                    index = appendUrlEscape(text, index, resource, span);
                } else {
                    resource.append(character);
                }
            }
            if (!closed) {
                throw invalidUrl(span);
            }
            index = skipWhitespace(text, index);
            if (index == text.length() || text.charAt(index) != ')') {
                throw invalidUrl(span);
            }
            index++;
        } else {
            var closed = false;
            while (index < text.length()) {
                var character = text.charAt(index++);
                if (JavaFXCssLexer.isWhitespace(character)) {
                    continue;
                }
                if (character == ')') {
                    closed = true;
                    break;
                }
                if (character == '\\') {
                    index = appendUrlEscape(text, index, resource, span);
                    continue;
                }
                if (character == '\'' || character == '"' || character == '(') {
                    throw invalidUrl(span);
                }
                resource.append(character);
            }
            if (!closed) {
                throw invalidUrl(span);
            }
        }

        if (resource.isEmpty()) {
            throw invalidUrl(span);
        }
        return new ParsedValue(resource.toString(), index);
    }

    /// Appends one JavaFX URL escape and returns the next unread offset.
    ///
    /// @param text     the complete descriptor text
    /// @param index    the offset immediately after the escape marker
    /// @param resource the decoded resource destination
    /// @param span     the source range associated with the value
    /// @return the next unread offset
    /// @throws CssSerializeException if the escape marker ends the value
    private static int appendUrlEscape(
            String text,
            int index,
            StringBuilder resource,
            SourceSpan span
    ) {
        if (index == text.length()) {
            throw invalidUrl(span);
        }
        if (isNewline(text.charAt(index))) {
            do {
                index++;
            } while (index < text.length() && isNewline(text.charAt(index)));
            return index;
        }
        resource.append(text.charAt(index));
        return index + 1;
    }

    /// Skips JavaFX whitespace and comments.
    ///
    /// @param text  the complete descriptor text
    /// @param start the first candidate offset
    /// @param span  the source range associated with the value
    /// @return the first non-trivia offset or the text length
    /// @throws CssSerializeException if a line comment consumes the remainder
    private static int skipTrivia(String text, int start, SourceSpan span) {
        var end = JavaFXCssLexer.triviaEnd(text, start);
        if (end < 0) {
            throw new CssSerializeException(
                    "A JavaFX @font-face comment must end before the stylesheet does.",
                    span,
                    null
            );
        }
        return end;
    }

    /// Skips JavaFX CSS whitespace.
    ///
    /// @param text  the complete descriptor text
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

    /// Returns whether one character is a JavaFX URL newline.
    ///
    /// @param character the character to inspect
    /// @return whether the character is a carriage return or line feed
    private static boolean isNewline(char character) {
        return character == '\r' || character == '\n';
    }

    /// Creates the standard failure for an unsupported source list.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated serialization failure
    private static CssSerializeException invalidSource(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX @font-face src requires comma-separated url(...), "
                        + "local(...), or identifier sources.",
                span,
                null
        );
    }

    /// Creates the standard failure for an invalid descriptor value.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated serialization failure
    private static CssSerializeException invalidDescriptor(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX CSS cannot tokenize this declaration value.",
                span,
                null
        );
    }

    /// Creates the standard failure for an unsafe JavaFX URL token.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated serialization failure
    private static CssSerializeException invalidUrl(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX @font-face requires a nonempty, closed url(...) token.",
                span,
                null
        );
    }

    /// Identifies JavaFX's persisted font-source variants.
    @NotNullByDefault
    public enum SourceType {
        /// Identifies an external URL font resource.
        URL,

        /// Identifies a local installed font family.
        LOCAL,

        /// Identifies another font-face declaration by name.
        REFERENCE
    }

    /// Stores one parsed JavaFX font source.
    ///
    /// @param type   the JavaFX source kind
    /// @param source the decoded URL resource, local family, or reference name
    /// @param format the optional URL font format, or `null`
    @NotNullByDefault
    public record Source(
            SourceType type,
            String source,
            @Nullable String format
    ) {
        /// Validates one immutable parsed source.
        public Source {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(source, "source");
        }
    }

    /// Stores one parsed value and its following offset.
    ///
    /// @param value the decoded value
    /// @param end   the exclusive end offset
    @NotNullByDefault
    private record ParsedValue(String value, int end) {
        /// Validates one parser result.
        private ParsedValue {
            Objects.requireNonNull(value, "value");
        }
    }
}
