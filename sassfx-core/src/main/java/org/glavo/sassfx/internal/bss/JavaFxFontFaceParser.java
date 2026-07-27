// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.bss;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Parses the JavaFX {@code @font-face src} subset stored by BSS.
///
/// JavaFX persists URL, local, and identifier-reference sources separately.
/// This parser accepts only those forms and resolves URL sources before BSS is
/// written, matching JavaFX's stylesheet-relative URL conversion without
/// loading JavaFX classes at compilation time.
@NotNullByDefault
final class JavaFxFontFaceParser {
    /// Prevents instantiation.
    private JavaFxFontFaceParser() {
    }

    /// Parses one comma-separated JavaFX {@code src} descriptor value.
    ///
    /// @param text the evaluated CSS descriptor value
    /// @param span the source range associated with the value
    /// @return immutable JavaFX font source snapshots in declaration order
    /// @throws BssSerializeException if a source form cannot be represented by BSS
    static @Unmodifiable List<Source> parseSources(String text, SourceSpan span) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(span, "span");

        var sources = new ArrayList<Source>();
        var index = skipWhitespace(text, 0);
        if (index == text.length()) {
            throw invalidSource(span);
        }

        while (true) {
            var sourceStart = index;
            var identifierEnd = identifierEnd(text, index);
            if (identifierEnd == index) {
                throw invalidSource(span);
            }
            var name = text.substring(index, identifierEnd);
            if (identifierEnd < text.length() && text.charAt(identifierEnd) == '(') {
                var close = closingParenthesis(text, identifierEnd, span);
                var arguments = text.substring(identifierEnd + 1, close);
                index = close + 1;
                if (name.equalsIgnoreCase("url")) {
                    var resource = BssSerializer.urlResource(
                            text.substring(sourceStart, index),
                            span
                    );
                    index = skipWhitespace(text, index);
                    @Nullable String format = null;
                    var formatEnd = identifierEnd(text, index);
                    if (formatEnd > index
                            && text.regionMatches(true, index, "format", 0, formatEnd - index)
                            && formatEnd - index == "format".length()
                            && formatEnd < text.length()
                            && text.charAt(formatEnd) == '(') {
                        var formatClose = closingParenthesis(text, formatEnd, span);
                        format = formatValue(
                                text.substring(formatEnd + 1, formatClose),
                                span
                        );
                        index = formatClose + 1;
                    }
                    sources.add(new Source(SourceType.URL, resolveUrl(resource, span), format));
                } else if (name.equalsIgnoreCase("local")) {
                    sources.add(new Source(
                            SourceType.LOCAL,
                            localValue(arguments, span),
                            null
                    ));
                } else {
                    throw invalidSource(span);
                }
            } else {
                sources.add(new Source(SourceType.REFERENCE, name, null));
                index = identifierEnd;
            }

            index = skipWhitespace(text, index);
            if (index == text.length()) {
                return List.copyOf(sources);
            }
            if (text.charAt(index) != ',') {
                throw invalidSource(span);
            }
            index = skipWhitespace(text, index + 1);
            if (index == text.length()) {
                throw invalidSource(span);
            }
        }
    }

    /// Skips CSS whitespace starting at {@code index}.
    ///
    /// @param text the parsed descriptor text
    /// @param index the first candidate character
    /// @return the first non-whitespace index, or {@code text.length()}
    private static int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /// Returns the index immediately after one supported CSS identifier.
    ///
    /// @param text the parsed descriptor text
    /// @param index the identifier start index
    /// @return the first index after the identifier, or {@code index} when absent
    private static int identifierEnd(String text, int index) {
        if (index == text.length() || !isIdentifierStart(text.charAt(index))) {
            return index;
        }
        index++;
        while (index < text.length() && isIdentifierPart(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /// Returns whether one character may begin a supported CSS identifier.
    ///
    /// @param character the character to inspect
    /// @return whether the character may begin an identifier
    private static boolean isIdentifierStart(char character) {
        return character == '-' || character == '_' || Character.isLetter(character);
    }

    /// Returns whether one character may continue a supported CSS identifier.
    ///
    /// @param character the character to inspect
    /// @return whether the character may continue an identifier
    private static boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || Character.isDigit(character);
    }

    /// Finds the closing parenthesis for a JavaFX source function.
    ///
    /// @param text the parsed descriptor text
    /// @param opening the opening parenthesis index
    /// @param span the source range associated with the value
    /// @return the closing parenthesis index
    /// @throws BssSerializeException if the function is unterminated
    private static int closingParenthesis(String text, int opening, SourceSpan span) {
        var depth = 1;
        @Nullable Character quote = null;
        for (var index = opening + 1; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quote != null) {
                if (character == '\\') {
                    index++;
                } else if (character == quote) {
                    quote = null;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return index;
            }
        }
        throw invalidSource(span);
    }

    /// Parses a JavaFX {@code local(...)} argument.
    ///
    /// @param arguments the text inside the function parentheses
    /// @param span the source range associated with the value
    /// @return the local installed font name
    /// @throws BssSerializeException if the value is not one quoted string or identifier
    private static String localValue(String arguments, SourceSpan span) {
        var trimmed = arguments.trim();
        if (trimmed.length() >= 2
                && (trimmed.charAt(0) == '\'' || trimmed.charAt(0) == '"')
                && trimmed.charAt(trimmed.length() - 1) == trimmed.charAt(0)) {
            return quotedValue(trimmed, span);
        }
        var end = identifierEnd(trimmed, 0);
        if (end != trimmed.length()) {
            throw invalidSource(span);
        }
        return trimmed;
    }

    /// Parses a JavaFX {@code format(...)} argument.
    ///
    /// @param arguments the text inside the function parentheses
    /// @param span the source range associated with the value
    /// @return the unquoted format spelling
    /// @throws BssSerializeException if the format is not one string or identifier
    private static String formatValue(String arguments, SourceSpan span) {
        var trimmed = arguments.trim();
        if (trimmed.isEmpty()) {
            throw invalidSource(span);
        }
        if (trimmed.length() >= 2
                && (trimmed.charAt(0) == '\'' || trimmed.charAt(0) == '"')
                && trimmed.charAt(trimmed.length() - 1) == trimmed.charAt(0)) {
            return quotedValue(trimmed, span);
        }
        var end = identifierEnd(trimmed, 0);
        if (end != trimmed.length()) {
            throw invalidSource(span);
        }
        return trimmed;
    }

    /// Parses one quoted JavaFX local-font or format value.
    ///
    /// @param value the text inside a function call
    /// @param span the source range associated with the value
    /// @return the text without its surrounding quotes
    /// @throws BssSerializeException if the value is not one simple quoted string
    private static String quotedValue(String value, SourceSpan span) {
        var trimmed = value.trim();
        if (trimmed.length() < 2) {
            throw invalidSource(span);
        }
        var quote = trimmed.charAt(0);
        if ((quote != '\'' && quote != '"') || trimmed.charAt(trimmed.length() - 1) != quote) {
            throw invalidSource(span);
        }
        var result = new StringBuilder(trimmed.length() - 2);
        for (var index = 1; index < trimmed.length() - 1; index++) {
            var character = trimmed.charAt(index);
            if (character == '\\') {
                throw invalidSource(span);
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    /// Resolves one JavaFX URL source to the spelling persisted in BSS.
    ///
    /// Relative URLs require a non-opaque stylesheet URL. Class-loader and
    /// absolute-path resolution are intentionally rejected because BSS would
    /// otherwise depend on the JavaFX runtime environment that consumes it.
    ///
    /// @param resource the decoded {@code url(...)} resource text
    /// @param span the source range associated with the value
    /// @return the resolved external URL spelling
    /// @throws BssSerializeException if JavaFX URL conversion is not deterministic here
    private static String resolveUrl(String resource, SourceSpan span) {
        try {
            var resourceUri = new URI(resource);
            if (resourceUri.isAbsolute()) {
                return resourceUri.toURL().toExternalForm();
            }
            if (resourceUri.getPath() == null || resourceUri.getPath().startsWith("/")) {
                throw invalidSource(span);
            }
            @Nullable URI stylesheetUrl = span.url();
            if (stylesheetUrl == null || stylesheetUrl.isOpaque()) {
                throw invalidSource(span);
            }
            return stylesheetUrl.resolve(resourceUri).toURL().toExternalForm();
        } catch (URISyntaxException | MalformedURLException failure) {
            throw invalidSource(span);
        }
    }

    /// Creates the standard BSS failure for an unsupported font-face source.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated serialization failure
    private static BssSerializeException invalidSource(SourceSpan span) {
        return new BssSerializeException(
                "BSS @font-face src requires URL, local(...), or identifier sources.",
                span,
                null
        );
    }

    /// Identifies JavaFX's persisted font-source variants.
    @NotNullByDefault
    enum SourceType {
        /// Identifies an external URL font resource.
        URL,

        /// Identifies a local installed font family.
        LOCAL,

        /// Identifies another font-face declaration by name.
        REFERENCE
    }

    /// Stores one BSS-ready JavaFX font source.
    ///
    /// @param type   the persisted JavaFX source kind
    /// @param source the resolved URL, local family name, or reference name
    /// @param format the optional URL font format, or {@code null}
    @NotNullByDefault
    record Source(SourceType type, String source, @Nullable String format) {
        /// Creates one immutable font-source snapshot.
        Source {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(source, "source");
        }
    }
}
