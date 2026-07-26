// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.JavaFXFeature;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Validates media-query text accepted by the JavaFX CSS parser.
///
/// JavaFX accepts a `media-condition` list rather than the complete web
/// `media-query` grammar. In particular, media types such as `screen` and
/// `print` are not accepted by the supported parser profiles.
@NotNullByDefault
final class JavaFXMediaQueryValidator {
    /// Prevents instantiation.
    private JavaFXMediaQueryValidator() {
    }

    /// Validates a comma-separated JavaFX media-condition list.
    ///
    /// An empty list is accepted because JavaFX represents it as an
    /// always-matching media-query list.
    ///
    /// @param queryList the serialized query-list contents, excluding `@media`
    /// @param span          the source range reported when validation fails
    /// @param compatibility the JavaFX release whose grammar is validated
    /// @throws CssSerializeException if the text is not accepted by JavaFX
    static void validate(
            String queryList,
            SourceSpan span,
            JavaFXCompatibility compatibility
    ) {
        Objects.requireNonNull(queryList, "queryList");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(compatibility, "compatibility");
        new Parser(tokenize(queryList, span), span, compatibility)
                .parseQueryList();
    }

    /// Converts CSS media-condition text into the token subset used by JavaFX.
    ///
    /// @param source the query-list contents
    /// @param span   the source range reported for invalid lexical input
    /// @return the immutable token sequence
    private static @Unmodifiable List<Token> tokenize(String source, SourceSpan span) {
        var tokens = new ArrayList<Token>();
        var index = 0;
        while (index < source.length()) {
            var character = source.charAt(index);
            if (isWhitespace(character)) {
                index++;
                continue;
            }
            if (character == '/' && index + 1 < source.length()) {
                var next = source.charAt(index + 1);
                if (next == '*') {
                    var close = source.indexOf("*/", index + 2);
                    if (close < 0) {
                        throw failure("Unterminated comment in JavaFX media query", span);
                    }
                    index = close + 2;
                    continue;
                }
                if (next == '/') {
                    index += 2;
                    while (index < source.length()
                            && source.charAt(index) != '\r'
                            && source.charAt(index) != '\n') {
                        index++;
                    }
                    continue;
                }
            }
            var punctuation = punctuation(character);
            if (punctuation != null) {
                tokens.add(new Token(punctuation, Character.toString(character)));
                index++;
                continue;
            }
            if (startsNumber(source, index)) {
                var numberStart = index;
                index = consumeNumber(source, index);
                var number = source.substring(numberStart, index);
                if (index < source.length() && source.charAt(index) == '%') {
                    tokens.add(new Token(TokenType.PERCENTAGE, number + "%"));
                    index++;
                    continue;
                }
                var unitStart = index;
                while (index < source.length()
                        && isIdentifierContinue(source.charAt(index))) {
                    index++;
                }
                if (unitStart == index) {
                    tokens.add(new Token(TokenType.NUMBER, number));
                    continue;
                }
                var unit = source.substring(unitStart, index);
                var type = switch (unit.toLowerCase(Locale.ROOT)) {
                    case "cm", "em", "ex", "in", "mm", "pc", "pt", "px" ->
                            TokenType.LENGTH;
                    default -> TokenType.INVALID;
                };
                tokens.add(new Token(type, number + unit));
                continue;
            }
            if (isIdentifierStart(character)) {
                var identifierStart = index;
                index++;
                while (index < source.length()
                        && isIdentifierContinue(source.charAt(index))) {
                    index++;
                }
                var identifier = source.substring(identifierStart, index);
                if (index < source.length() && source.charAt(index) == '(') {
                    tokens.add(new Token(TokenType.INVALID, identifier + "("));
                    index++;
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, identifier));
                }
                continue;
            }
            throw failure(
                    "Unexpected character '" + character + "' in JavaFX media query",
                    span
            );
        }
        return List.copyOf(tokens);
    }

    /// Returns the token type for one punctuation character.
    ///
    /// @param character the character to classify
    /// @return the corresponding token type, or `null` for non-punctuation
    private static @Nullable TokenType punctuation(char character) {
        return switch (character) {
            case '(' -> TokenType.LEFT_PARENTHESIS;
            case ')' -> TokenType.RIGHT_PARENTHESIS;
            case ':' -> TokenType.COLON;
            case ',' -> TokenType.COMMA;
            case '<' -> TokenType.LESS;
            case '>' -> TokenType.GREATER;
            case '=' -> TokenType.EQUALS;
            default -> null;
        };
    }

    /// Returns whether a JavaFX number token begins at an offset.
    ///
    /// @param source the complete query-list contents
    /// @param index  the offset to inspect
    /// @return whether a number begins at the offset
    private static boolean startsNumber(String source, int index) {
        var character = source.charAt(index);
        if (isDigit(character)) {
            return true;
        }
        if (character == '.') {
            return index + 1 < source.length()
                    && isDigit(source.charAt(index + 1));
        }
        if (character != '+' && character != '-') {
            return false;
        }
        if (index + 1 >= source.length()) {
            return false;
        }
        var next = source.charAt(index + 1);
        return isDigit(next)
                || next == '.'
                && index + 2 < source.length()
                && isDigit(source.charAt(index + 2));
    }

    /// Consumes a JavaFX decimal number without exponent notation.
    ///
    /// @param source the complete query-list contents
    /// @param index  the number start offset
    /// @return the first offset after the number
    private static int consumeNumber(String source, int index) {
        if (source.charAt(index) == '+' || source.charAt(index) == '-') {
            index++;
        }
        while (index < source.length() && isDigit(source.charAt(index))) {
            index++;
        }
        if (index + 1 < source.length()
                && source.charAt(index) == '.'
                && isDigit(source.charAt(index + 1))) {
            index++;
            while (index < source.length()
                    && isDigit(source.charAt(index))) {
                index++;
            }
        }
        return index;
    }

    /// Returns whether a character can begin a JavaFX CSS identifier.
    ///
    /// @param character the character to inspect
    /// @return whether it may begin an identifier
    private static boolean isIdentifierStart(char character) {
        return character == '-' || character == '_'
                || character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z';
    }

    /// Returns whether a character can continue a JavaFX CSS identifier.
    ///
    /// @param character the character to inspect
    /// @return whether it may continue an identifier
    private static boolean isIdentifierContinue(char character) {
        return isIdentifierStart(character) || isDigit(character);
    }

    /// Returns whether a character is one of JavaFX CSS's whitespace tokens.
    ///
    /// @param character the character to inspect
    /// @return whether JavaFX treats it as whitespace
    private static boolean isWhitespace(char character) {
        return character == ' ' || character == '\t' || character == '\r'
                || character == '\n' || character == '\f';
    }

    /// Returns whether a character is an ASCII decimal digit.
    ///
    /// @param character the character to inspect
    /// @return whether it is between `0` and `9`
    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    /// Creates a serialization failure associated with the media-rule span.
    ///
    /// @param message the validation failure message
    /// @param span    the source range reported to the caller
    /// @return the exception to throw
    private static CssSerializeException failure(String message, SourceSpan span) {
        return new CssSerializeException(message, span, null);
    }

    /// Identifies tokens consumed by the JavaFX media-condition parser.
    @NotNullByDefault
    private enum TokenType {
        /// An identifier token.
        IDENTIFIER,

        /// A unitless decimal number token.
        NUMBER,

        /// A supported JavaFX length token.
        LENGTH,

        /// A percentage token, which JavaFX media features do not accept.
        PERCENTAGE,

        /// A left parenthesis.
        LEFT_PARENTHESIS,

        /// A right parenthesis.
        RIGHT_PARENTHESIS,

        /// A colon.
        COLON,

        /// A comma.
        COMMA,

        /// A less-than operator.
        LESS,

        /// A greater-than operator.
        GREATER,

        /// An equality operator.
        EQUALS,

        /// A lexically valid CSS token outside JavaFX's accepted subset.
        INVALID
    }

    /// Stores one media-query token.
    ///
    /// @param type the token category
    /// @param text the exact token text
    @NotNullByDefault
    private record Token(TokenType type, String text) {
        /// Validates token components.
        private Token {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(text, "text");
        }
    }

    /// Identifies comparison operators accepted by JavaFX range features.
    @NotNullByDefault
    private enum Comparison {
        /// Strictly less than.
        LESS(-1),

        /// Less than or equal to.
        LESS_OR_EQUAL(-1),

        /// Equal to.
        EQUAL(0),

        /// Greater than or equal to.
        GREATER_OR_EQUAL(1),

        /// Strictly greater than.
        GREATER(1);

        /// Contains the direction used to validate interval expressions.
        private final int direction;

        /// Creates a comparison with its interval direction.
        ///
        /// @param direction negative for less, positive for greater, or zero
        Comparison(int direction) {
            this.direction = direction;
        }

        /// Returns whether another comparison can form a JavaFX interval.
        ///
        /// @param other the second interval comparison
        /// @return whether both operators have the same nonzero direction
        private boolean hasSameDirection(Comparison other) {
            return direction != 0 && direction == other.direction;
        }
    }

    /// Parses and validates the JavaFX media-condition grammar.
    @NotNullByDefault
    private static final class Parser {
        /// Contains the immutable input token sequence.
        private final @Unmodifiable List<Token> tokens;

        /// Contains the source range used by reported failures.
        private final SourceSpan span;

        /// Contains the JavaFX release whose media features are accepted.
        private final JavaFXCompatibility compatibility;

        /// Contains the next token offset.
        private int index;

        /// Creates a parser over an immutable token sequence.
        ///
        /// @param tokens        the token sequence
        /// @param span          the source range used by reported failures
        /// @param compatibility the JavaFX release whose grammar is validated
        private Parser(
                List<Token> tokens,
                SourceSpan span,
                JavaFXCompatibility compatibility
        ) {
            this.tokens = List.copyOf(tokens);
            this.span = span;
            this.compatibility = compatibility;
        }

        /// Parses a comma-separated media-condition list.
        private void parseQueryList() {
            if (tokens.isEmpty()) {
                return;
            }
            parseMediaCondition();
            while (consume(TokenType.COMMA)) {
                if (atEnd()) {
                    fail("Expected media condition after ','");
                }
                parseMediaCondition();
            }
            if (!atEnd()) {
                fail("Unexpected token '" + peek().text() + "' in JavaFX media query");
            }
        }

        /// Parses one condition with `not`, `and`, or `or` logic.
        private void parseMediaCondition() {
            if (consumeIdentifier("not")) {
                parseMediaInParentheses();
                return;
            }

            parseMediaInParentheses();
            if (consumeIdentifier("and")) {
                do {
                    parseMediaInParentheses();
                } while (consumeIdentifier("and"));
                if (peekIdentifier("or")) {
                    fail("JavaFX media conditions cannot mix 'and' and 'or'");
                }
            } else if (consumeIdentifier("or")) {
                do {
                    parseMediaInParentheses();
                } while (consumeIdentifier("or"));
                if (peekIdentifier("and")) {
                    fail("JavaFX media conditions cannot mix 'or' and 'and'");
                }
            }
        }

        /// Parses a nested condition, discrete feature, or range feature.
        private void parseMediaInParentheses() {
            expect(TokenType.LEFT_PARENTHESIS, "Expected '('");

            if (matches(TokenType.IDENTIFIER, TokenType.RIGHT_PARENTHESIS)
                    || matches(TokenType.IDENTIFIER, TokenType.COLON)) {
                parseDiscreteFeature();
                return;
            }
            if (isNumeric(peekType())
                    || peekType() == TokenType.IDENTIFIER && comparisonStarts(1)) {
                parseRangeFeature();
                return;
            }

            parseMediaCondition();
            expect(TokenType.RIGHT_PARENTHESIS, "Expected ')'");
        }

        /// Parses and validates a JavaFX discrete media feature.
        private void parseDiscreteFeature() {
            var name = expect(TokenType.IDENTIFIER, "Expected media feature name")
                    .text()
                    .toLowerCase(Locale.ROOT);
            @Nullable Token value = null;
            if (consume(TokenType.COLON)) {
                if (atEnd()) {
                    fail("Expected media feature value");
                }
                value = consume();
            }
            expect(TokenType.RIGHT_PARENTHESIS, "Expected ')'");
            validateDiscreteFeature(name, value);
        }

        /// Validates one discrete feature name and optional value.
        ///
        /// @param name  the lowercase feature name
        /// @param value the feature value, or `null` for boolean context
        private void validateDiscreteFeature(String name, @Nullable Token value) {
            switch (name) {
                case "min-width", "max-width", "width",
                        "min-height", "max-height", "height" -> {
                    requireFeature(JavaFXFeature.VIEWPORT_MEDIA_QUERIES, name);
                    requireSizeValue(name, value);
                }
                case "min-aspect-ratio", "max-aspect-ratio", "aspect-ratio" -> {
                    requireFeature(JavaFXFeature.VIEWPORT_MEDIA_QUERIES, name);
                    requireAspectRatioValue(name, value);
                }
                case "orientation" -> {
                    requireFeature(JavaFXFeature.VIEWPORT_MEDIA_QUERIES, name);
                    requireIdentifierValue(name, value, "landscape", "portrait");
                }
                case "display-mode" -> {
                    requireFeature(JavaFXFeature.VIEWPORT_MEDIA_QUERIES, name);
                    requireIdentifierValue(name, value, "standalone", "fullscreen");
                }
                case "prefers-color-scheme" -> {
                    requireFeature(JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES, name);
                    requireIdentifierValue(name, value, "light", "dark");
                }
                case "prefers-reduced-motion", "prefers-reduced-transparency",
                        "prefers-reduced-data" -> {
                    requireFeature(JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES, name);
                    requireBooleanPreference(name, value, "reduce");
                }
                case "-fx-prefers-persistent-scrollbars" -> {
                    requireFeature(JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES, name);
                    requireBooleanPreference(name, value, "persistent");
                }
                case "-fx-supports-conditional-feature" -> {
                    requireFeature(JavaFXFeature.CONDITIONAL_MEDIA_FEATURE, name);
                    requireIdentifierValue(
                            name,
                            value,
                            "graphics",
                            "controls",
                            "media",
                            "web",
                            "swt",
                            "swing",
                            "fxml",
                            "scene3d",
                            "effect",
                            "shape-clip",
                            "input-method",
                            "transparent-window",
                            "unified-window",
                            "extended-window",
                            "input-pointer"
                    );
                }
                case "-fx-platform" -> {
                    requireFeature(JavaFXFeature.PLATFORM_MEDIA_FEATURE, name);
                    requireIdentifierValue(
                            name,
                            value,
                            "android",
                            "ios",
                            "linux",
                            "macos",
                            "windows"
                    );
                }
                default -> fail("Unknown JavaFX media feature '" + name + "'");
            }
        }

        /// Requires a media feature to be present in the selected JavaFX release.
        ///
        /// @param feature the required platform capability
        /// @param name    the media feature name reported on failure
        private void requireFeature(JavaFXFeature feature, String name) {
            if (!compatibility.supports(feature)) {
                fail("JavaFX " + compatibility.version()
                        + " CSS does not support media feature '" + name + "'");
            }
        }

        /// Requires a JavaFX discrete size value.
        ///
        /// JavaFX accepts percentages in the colon form even though its range
        /// grammar requires a number or absolute/font-relative length token.
        ///
        /// @param name  the feature name
        /// @param value the supplied value, or `null`
        private void requireSizeValue(String name, @Nullable Token value) {
            if (value == null
                    || !isLength(value)
                    && value.type() != TokenType.PERCENTAGE) {
                fail("Invalid value for JavaFX media feature '" + name + "'");
            }
        }

        /// Requires a unitless number for an aspect-ratio feature.
        ///
        /// @param name  the feature name
        /// @param value the supplied value, or `null`
        private void requireAspectRatioValue(String name, @Nullable Token value) {
            if (value == null || value.type() != TokenType.NUMBER) {
                fail("Invalid value for JavaFX media feature '" + name + "'");
            }
        }

        /// Requires one identifier from an explicit allowed-value list.
        ///
        /// @param name    the feature name
        /// @param value   the supplied value, or `null`
        /// @param allowed the accepted lowercase identifiers
        private void requireIdentifierValue(
                String name,
                @Nullable Token value,
                String... allowed
        ) {
            if (value == null || value.type() != TokenType.IDENTIFIER) {
                fail("Invalid value for JavaFX media feature '" + name + "'");
            }
            var text = value.text().toLowerCase(Locale.ROOT);
            for (var candidate : allowed) {
                if (candidate.equals(text)) {
                    return;
                }
            }
            fail("Unknown value '" + value.text()
                    + "' for JavaFX media feature '" + name + "'");
        }

        /// Validates a boolean preference feature.
        ///
        /// @param name      the feature name
        /// @param value     the supplied value, or `null`
        /// @param trueValue the identifier representing the enabled preference
        private void requireBooleanPreference(
                String name,
                @Nullable Token value,
                String trueValue
        ) {
            if (value == null) {
                return;
            }
            if (value.type() != TokenType.IDENTIFIER) {
                fail("Invalid value for JavaFX media feature '" + name + "'");
            }
            var text = value.text().toLowerCase(Locale.ROOT);
            if (!text.equals(trueValue) && !text.equals("no-preference")) {
                fail("Unknown value '" + value.text()
                        + "' for JavaFX media feature '" + name + "'");
            }
        }

        /// Parses and validates a name-first, value-first, or interval feature.
        private void parseRangeFeature() {
            if (isNumeric(peekType())) {
                var firstValue = consume();
                var firstOperator = parseComparison();
                var name = expect(TokenType.IDENTIFIER, "Expected media feature name")
                        .text()
                        .toLowerCase(Locale.ROOT);
                validateRangeValue(name, firstValue);
                if (comparisonStarts(0)) {
                    var secondOperator = parseComparison();
                    if (!firstOperator.hasSameDirection(secondOperator)) {
                        fail("JavaFX media interval operators must have the same direction");
                    }
                    var secondValue = consumeNumeric("Expected media feature value");
                    validateRangeValue(name, secondValue);
                }
            } else {
                var name = expect(TokenType.IDENTIFIER, "Expected media feature name")
                        .text()
                        .toLowerCase(Locale.ROOT);
                parseComparison();
                validateRangeValue(name, consumeNumeric("Expected media feature value"));
            }
            expect(TokenType.RIGHT_PARENTHESIS, "Expected ')'");
        }

        /// Validates a range feature value against its feature type.
        ///
        /// @param name  the lowercase feature name
        /// @param value the numeric token
        private void validateRangeValue(String name, Token value) {
            requireFeature(JavaFXFeature.VIEWPORT_MEDIA_QUERIES, name);
            switch (name) {
                case "width", "height" -> {
                    if (!isLength(value)) {
                        fail("Invalid value for JavaFX media feature '" + name + "'");
                    }
                }
                case "aspect-ratio" -> {
                    if (value.type() != TokenType.NUMBER) {
                        fail("Invalid value for JavaFX media feature 'aspect-ratio'");
                    }
                }
                default -> fail("Unknown JavaFX range media feature '" + name + "'");
            }
        }

        /// Parses one JavaFX media comparison operator.
        ///
        /// @return the parsed comparison
        private Comparison parseComparison() {
            if (consume(TokenType.GREATER)) {
                return consume(TokenType.EQUALS)
                        ? Comparison.GREATER_OR_EQUAL
                        : Comparison.GREATER;
            }
            if (consume(TokenType.LESS)) {
                return consume(TokenType.EQUALS)
                        ? Comparison.LESS_OR_EQUAL
                        : Comparison.LESS;
            }
            if (consume(TokenType.EQUALS)) {
                return Comparison.EQUAL;
            }
            fail("Expected JavaFX media comparison operator");
            throw new AssertionError();
        }

        /// Consumes and returns a numeric token.
        ///
        /// @param message the failure message when no numeric token is present
        /// @return the consumed token
        private Token consumeNumeric(String message) {
            if (!isNumeric(peekType())) {
                fail(message);
            }
            return consume();
        }

        /// Returns whether a token is a legal width or height value.
        ///
        /// @param token the token to inspect
        /// @return whether it is a unitless number or supported length
        private boolean isLength(Token token) {
            return token.type() == TokenType.NUMBER || token.type() == TokenType.LENGTH;
        }

        /// Returns whether a token type is numeric for range grammar.
        ///
        /// @param type the token type, or `null` at end of input
        /// @return whether it is a number or recognized length
        private boolean isNumeric(@Nullable TokenType type) {
            return type == TokenType.NUMBER || type == TokenType.LENGTH;
        }

        /// Returns whether a comparison begins at a lookahead offset.
        ///
        /// @param offset the offset from the next token
        /// @return whether a comparison operator starts there
        private boolean comparisonStarts(int offset) {
            var type = peekType(offset);
            return type == TokenType.LESS
                    || type == TokenType.GREATER
                    || type == TokenType.EQUALS;
        }

        /// Returns whether the upcoming tokens match two token types.
        ///
        /// @param first  the next expected type
        /// @param second the following expected type
        /// @return whether both types match
        private boolean matches(TokenType first, TokenType second) {
            return peekType() == first && peekType(1) == second;
        }

        /// Returns whether the next token is a specified identifier.
        ///
        /// @param expected the lowercase identifier
        /// @return whether the next token matches
        private boolean peekIdentifier(String expected) {
            return peekType() == TokenType.IDENTIFIER
                    && peek().text().equalsIgnoreCase(expected);
        }

        /// Consumes a specified identifier if present.
        ///
        /// @param expected the lowercase identifier
        /// @return whether the identifier was consumed
        private boolean consumeIdentifier(String expected) {
            if (!peekIdentifier(expected)) {
                return false;
            }
            index++;
            return true;
        }

        /// Consumes a token type if present.
        ///
        /// @param expected the token type
        /// @return whether the token was consumed
        private boolean consume(TokenType expected) {
            if (peekType() != expected) {
                return false;
            }
            index++;
            return true;
        }

        /// Returns and consumes the next token.
        ///
        /// @return the consumed token
        private Token consume() {
            if (atEnd()) {
                fail("Unexpected end of JavaFX media query");
            }
            return tokens.get(index++);
        }

        /// Requires and consumes a token type.
        ///
        /// @param expected the required token type
        /// @param message  the failure message
        /// @return the consumed token
        private Token expect(TokenType expected, String message) {
            if (peekType() != expected) {
                fail(message);
            }
            return tokens.get(index++);
        }

        /// Returns the next token.
        ///
        /// @return the next token
        private Token peek() {
            if (atEnd()) {
                fail("Unexpected end of JavaFX media query");
            }
            return tokens.get(index);
        }

        /// Returns the next token type.
        ///
        /// @return the type, or `null` at end of input
        private @Nullable TokenType peekType() {
            return peekType(0);
        }

        /// Returns a lookahead token type.
        ///
        /// @param offset the nonnegative lookahead offset
        /// @return the type, or `null` beyond the input
        private @Nullable TokenType peekType(int offset) {
            var target = index + offset;
            return target < tokens.size() ? tokens.get(target).type() : null;
        }

        /// Returns whether all input tokens were consumed.
        ///
        /// @return whether parsing reached the end
        private boolean atEnd() {
            return index >= tokens.size();
        }

        /// Throws a serialization failure at the configured source span.
        ///
        /// @param message the validation failure message
        private void fail(String message) {
            throw failure(message, span);
        }
    }
}
