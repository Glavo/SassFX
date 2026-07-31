// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.glavo.sassfx.JavaFXFeature.ADVANCED_TRANSITION_EASING;

/// Validates the JavaFX transition value grammar without loading JavaFX.
///
/// The accepted grammar follows the transition parser introduced in JavaFX 23.
/// It additionally rejects surplus tokens and function arguments that OpenJFX
/// parses but silently ignores.
@NotNullByDefault
final class JavaFXTransitionValidator {
    /// Contains keyword easing functions shared by JavaFX 23 through 27.
    private static final @Unmodifiable Set<String> EASING_KEYWORDS = Set.of(
            "linear",
            "ease",
            "ease-in",
            "ease-out",
            "ease-in-out",
            "step-start",
            "step-end",
            "-fx-ease-in",
            "-fx-ease-out",
            "-fx-ease-both"
    );

    /// Contains positions accepted by the JavaFX `steps()` parser.
    private static final @Unmodifiable Set<String> STEP_POSITIONS = Set.of(
            "jump-start",
            "jump-end",
            "jump-none",
            "jump-both",
            "start",
            "end"
    );

    /// Prevents instantiation.
    private JavaFXTransitionValidator() {
    }

    /// Validates one transition shorthand or longhand value.
    ///
    /// @param property      the lowercase transition property name
    /// @param value         the declaration value without `!important`
    /// @param span          the source range reported for invalid syntax
    /// @param compatibility the JavaFX release whose grammar is targeted
    /// @throws CssSerializeException if the value cannot be interpreted by the
    /// selected JavaFX release
    static void validate(
            String property,
            String value,
            SourceSpan span,
            JavaFXTarget compatibility
    ) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(compatibility, "compatibility");

        var layers = new Tokenizer(value, span).parse();
        if (isGlobalKeyword(layers)) {
            return;
        }
        if (layers.isEmpty()) {
            throw failure("JavaFX transition values must not be empty.", span);
        }

        switch (property) {
            case "transition" -> validateShorthand(layers, span, compatibility);
            case "transition-delay" -> validateDurations(layers, true, span);
            case "transition-duration" -> validateDurations(layers, false, span);
            case "transition-property" -> validateProperties(layers, span);
            case "transition-timing-function" ->
                    validateEasingLayers(layers, span, compatibility);
            default -> throw new AssertionError("unsupported transition property");
        }
    }

    /// Returns whether JavaFX handles the first token as a global CSS value.
    ///
    /// @param layers the tokenized declaration value
    /// @return whether property-specific parsing is bypassed
    private static boolean isGlobalKeyword(
            @Unmodifiable List<@Unmodifiable List<Token>> layers
    ) {
        if (layers.isEmpty() || layers.get(0).isEmpty()) {
            return false;
        }
        var token = layers.get(0).get(0);
        return token.type() == TokenType.IDENTIFIER
                && (token.text().equalsIgnoreCase("inherit")
                || token.text().equalsIgnoreCase("none")
                || token.text().equalsIgnoreCase("null"));
    }

    /// Validates comma-separated transition-property layers.
    ///
    /// @param layers the comma-separated token layers
    /// @param span   the source range reported for invalid syntax
    private static void validateProperties(
            @Unmodifiable List<@Unmodifiable List<Token>> layers,
            SourceSpan span
    ) {
        for (var layer : layers) {
            if (layer.size() != 1 || !isProperty(layer.get(0))) {
                throw failure(
                        "JavaFX transition-property requires one identifier or string per layer.",
                        span
                );
            }
        }
    }

    /// Validates comma-separated duration or delay layers.
    ///
    /// @param layers        the comma-separated token layers
    /// @param allowNegative whether literal negative times are permitted
    /// @param span          the source range reported for invalid syntax
    private static void validateDurations(
            @Unmodifiable List<@Unmodifiable List<Token>> layers,
            boolean allowNegative,
            SourceSpan span
    ) {
        for (var layer : layers) {
            if (layer.size() != 1 || !isTime(layer.get(0))) {
                throw failure(
                        "JavaFX transition times require one s, ms, or lookup value per layer.",
                        span
                );
            }
            validateTime(layer.get(0), allowNegative, span);
        }
    }

    /// Validates comma-separated transition timing functions.
    ///
    /// @param layers        the comma-separated token layers
    /// @param span          the source range reported for invalid syntax
    /// @param compatibility the selected JavaFX release
    private static void validateEasingLayers(
            @Unmodifiable List<@Unmodifiable List<Token>> layers,
            SourceSpan span,
            JavaFXTarget compatibility
    ) {
        for (var layer : layers) {
            if (layer.size() != 1 || !isEasingCandidate(layer.get(0))) {
                throw failure(
                        "JavaFX transition-timing-function requires one easing function per layer.",
                        span
                );
            }
            validateEasing(layer.get(0), span, compatibility);
        }
    }

    /// Validates comma-separated transition shorthand layers.
    ///
    /// @param layers        the comma-separated token layers
    /// @param span          the source range reported for invalid syntax
    /// @param compatibility the selected JavaFX release
    private static void validateShorthand(
            @Unmodifiable List<@Unmodifiable List<Token>> layers,
            SourceSpan span,
            JavaFXTarget compatibility
    ) {
        for (var layer : layers) {
            if (layer.isEmpty() || layer.size() > 4) {
                throw failure(
                        "Each JavaFX transition layer requires one through four components.",
                        span
                );
            }

            var hasProperty = false;
            var hasDuration = false;
            var hasDelay = false;
            var hasEasing = false;
            for (var token : layer) {
                if (isEasingCandidate(token)) {
                    if (hasEasing) {
                        throw failure(
                                "A JavaFX transition layer cannot contain two easing functions.",
                                span
                        );
                    }
                    validateEasing(token, span, compatibility);
                    hasEasing = true;
                } else if (isProperty(token)) {
                    if (hasProperty) {
                        throw failure(
                                "A JavaFX transition layer cannot contain two properties.",
                                span
                        );
                    }
                    hasProperty = true;
                } else if (isTime(token)) {
                    if (!hasDuration) {
                        validateTime(token, false, span);
                        hasDuration = true;
                    } else if (!hasDelay) {
                        validateTime(token, true, span);
                        hasDelay = true;
                    } else {
                        throw failure(
                                "A JavaFX transition layer cannot contain more than two times.",
                                span
                        );
                    }
                } else {
                    throw failure(
                            "Invalid component in JavaFX transition shorthand.",
                            span
                    );
                }
            }
        }
    }

    /// Returns whether a token is a JavaFX transition property value.
    ///
    /// @param token the token to inspect
    /// @return whether it is a nonempty identifier or string
    private static boolean isProperty(Token token) {
        return (token.type() == TokenType.IDENTIFIER
                || token.type() == TokenType.STRING)
                && !token.text().isEmpty();
    }

    /// Returns whether a token is a JavaFX time or duration lookup.
    ///
    /// @param token the token to inspect
    /// @return whether JavaFX routes it through its duration converter
    private static boolean isTime(Token token) {
        return token.type() == TokenType.TIME
                || token.type() == TokenType.IDENTIFIER;
    }

    /// Validates the sign of one literal duration.
    ///
    /// Identifier values are retained as property lookups, except that JavaFX
    /// interprets `initial` and `inherit` as zero and `indefinite` as positive
    /// infinity.
    ///
    /// @param token         the time or lookup token
    /// @param allowNegative whether a literal negative time is permitted
    /// @param span          the source range reported for an invalid value
    private static void validateTime(
            Token token,
            boolean allowNegative,
            SourceSpan span
    ) {
        if (token.type() == TokenType.TIME
                && !allowNegative
                && numericValue(token, span) < 0.0) {
            throw failure(
                    "JavaFX transition-duration does not accept negative times.",
                    span
            );
        }
    }

    /// Returns whether a token begins a recognized easing function.
    ///
    /// @param token the token to inspect
    /// @return whether the token must be parsed as easing in shorthand
    private static boolean isEasingCandidate(Token token) {
        if (token.type() == TokenType.IDENTIFIER) {
            return EASING_KEYWORDS.contains(token.text());
        }
        return token.type() == TokenType.FUNCTION
                && switch (token.text()) {
                    case "cubic-bezier", "steps", "linear" -> true;
                    default -> false;
                };
    }

    /// Validates one keyword or functional easing value.
    ///
    /// @param token         the easing token
    /// @param span          the source range reported for invalid syntax
    /// @param compatibility the selected JavaFX release
    private static void validateEasing(
            Token token,
            SourceSpan span,
            JavaFXTarget compatibility
    ) {
        if (token.type() == TokenType.IDENTIFIER) {
            if (!EASING_KEYWORDS.contains(token.text())) {
                throw failure("Unsupported JavaFX transition easing keyword.", span);
            }
            return;
        }
        if (token.type() != TokenType.FUNCTION) {
            throw failure("Expected a JavaFX transition easing function.", span);
        }

        switch (token.text()) {
            case "cubic-bezier" -> validateCubicBezier(token, span, compatibility);
            case "steps" -> validateSteps(token, span);
            case "linear" -> validateLinear(token, span, compatibility);
            default -> throw failure("Unsupported JavaFX transition easing function.", span);
        }
    }

    /// Validates a cubic Bézier easing function.
    ///
    /// JavaFX 23 through 25 constrain all four control-point coordinates to
    /// `[0,1]`. JavaFX 26 and later correctly constrain only x1 and x2.
    ///
    /// @param token         the function token
    /// @param span          the source range reported for invalid syntax
    /// @param compatibility the selected JavaFX release
    private static void validateCubicBezier(
            Token token,
            SourceSpan span,
            JavaFXTarget compatibility
    ) {
        var arguments = token.arguments();
        if (arguments.size() != 4) {
            throw failure("JavaFX cubic-bezier() requires exactly four numbers.", span);
        }
        for (var index = 0; index < arguments.size(); index++) {
            var argument = arguments.get(index);
            if (argument.size() != 1
                    || argument.get(0).type() != TokenType.NUMBER) {
                throw failure(
                        "JavaFX cubic-bezier() arguments must be unitless numbers.",
                        span
                );
            }
            var value = numericValue(argument.get(0), span);
            var constrained = compatibility.supports(ADVANCED_TRANSITION_EASING)
                    ? index % 2 == 0
                    : true;
            if (constrained && (value < 0.0 || value > 1.0)) {
                throw failure(
                        "This JavaFX target requires constrained cubic-bezier() coordinates"
                                + " to be between 0 and 1.",
                        span
                );
            }
        }
    }

    /// Validates a step easing function through its runtime constraints.
    ///
    /// @param token the function token
    /// @param span  the source range reported for invalid syntax
    private static void validateSteps(Token token, SourceSpan span) {
        var arguments = token.arguments();
        if (arguments.isEmpty() || arguments.size() > 2
                || arguments.get(0).size() != 1
                || arguments.get(0).get(0).type() != TokenType.NUMBER) {
            throw failure(
                    "JavaFX steps() requires an integer and an optional step position.",
                    span
            );
        }

        int count;
        try {
            count = Integer.parseInt(arguments.get(0).get(0).text());
        } catch (NumberFormatException cause) {
            throw failure(
                    "JavaFX steps() requires an integer step count.",
                    span
            );
        }

        var position = "end";
        if (arguments.size() == 2) {
            var argument = arguments.get(1);
            if (argument.size() != 1
                    || argument.get(0).type() != TokenType.IDENTIFIER
                    || !STEP_POSITIONS.contains(argument.get(0).text())) {
                throw failure("Invalid JavaFX steps() position.", span);
            }
            position = argument.get(0).text();
        }
        if (count <= 0 || position.equals("jump-none") && count <= 1) {
            throw failure(
                    "JavaFX steps() requires a positive count greater than one for jump-none.",
                    span
            );
        }
    }

    /// Validates a piecewise-linear easing function introduced in JavaFX 26.
    ///
    /// @param token         the function token
    /// @param span          the source range reported for invalid syntax
    /// @param compatibility the selected JavaFX release
    private static void validateLinear(
            Token token,
            SourceSpan span,
            JavaFXTarget compatibility
    ) {
        if (!compatibility.supports(ADVANCED_TRANSITION_EASING)) {
            throw failure(
                    "JavaFX " + compatibility.version()
                            + " CSS does not support the linear() timing function.",
                    span
            );
        }

        var pointCount = 0;
        for (var stop : token.arguments()) {
            if (stop.isEmpty() || stop.size() > 3
                    || stop.get(0).type() != TokenType.NUMBER) {
                throw failure(
                        "Each JavaFX linear() stop requires a number and up to two percentages.",
                        span
                );
            }
            numericValue(stop.get(0), span);
            for (var index = 1; index < stop.size(); index++) {
                if (stop.get(index).type() != TokenType.PERCENTAGE) {
                    throw failure(
                            "JavaFX linear() stop positions must be percentages.",
                            span
                    );
                }
                numericValue(stop.get(index), span);
            }
            pointCount += Math.max(1, stop.size() - 1);
        }
        if (pointCount < 2) {
            throw failure("JavaFX linear() requires at least two control points.", span);
        }
    }

    /// Parses a finite numeric token value.
    ///
    /// @param token the number, percentage, or time token
    /// @param span  the source range reported for invalid numeric text
    /// @return the parsed finite value
    private static double numericValue(Token token, SourceSpan span) {
        try {
            var value = Double.parseDouble(token.text());
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Report one stable CSS diagnostic below.
        }
        throw failure("JavaFX transition values require finite numbers.", span);
    }

    /// Creates a transition validation failure.
    ///
    /// @param message the human-readable failure
    /// @param span    the source range associated with the value
    /// @return the exception to throw
    private static CssSerializeException failure(String message, SourceSpan span) {
        return new CssSerializeException(message, span, null);
    }

    /// Identifies one lexical token used by the transition grammar.
    private enum TokenType {
        /// Identifies an identifier or property lookup.
        IDENTIFIER,
        /// Identifies a quoted string.
        STRING,
        /// Identifies a unitless number.
        NUMBER,
        /// Identifies a percentage.
        PERCENTAGE,
        /// Identifies a number with `s` or `ms` units.
        TIME,
        /// Identifies a function with comma-separated argument layers.
        FUNCTION
    }

    /// Stores one transition grammar token.
    ///
    /// @param type      the lexical token type
    /// @param text      the identifier, function name, or numeric portion
    /// @param arguments immutable function arguments; empty for other tokens
    private record Token(
            TokenType type,
            String text,
            @Unmodifiable List<@Unmodifiable List<Token>> arguments
    ) {
        /// Validates token components.
        private Token {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(text, "text");
            arguments = List.copyOf(arguments);
        }
    }

    /// Tokenizes the JavaFX transition lexical subset.
    @NotNullByDefault
    private static final class Tokenizer {
        /// Contains the complete declaration value.
        private final String source;

        /// Contains the source range reported for lexical failures.
        private final SourceSpan span;

        /// Contains the next source offset to consume.
        private int index;

        /// Creates a transition value tokenizer.
        ///
        /// @param source the declaration value
        /// @param span   the source range reported for invalid syntax
        private Tokenizer(String source, SourceSpan span) {
            this.source = source;
            this.span = span;
        }

        /// Tokenizes the complete declaration value.
        ///
        /// @return immutable comma-separated token layers
        private @Unmodifiable List<@Unmodifiable List<Token>> parse() {
            return parseLayers(-1);
        }

        /// Tokenizes layers until an optional closing delimiter.
        ///
        /// @param terminator the closing character, or `-1` at the top level
        /// @return immutable comma-separated token layers
        private @Unmodifiable List<@Unmodifiable List<Token>> parseLayers(
                int terminator
        ) {
            var layers = new ArrayList<List<Token>>();
            var current = new ArrayList<Token>();
            while (true) {
                skipTrivia();
                if (index >= source.length()) {
                    if (terminator >= 0) {
                        throw failure("Unclosed function in JavaFX transition value.", span);
                    }
                    if (!current.isEmpty()) {
                        layers.add(List.copyOf(current));
                    } else if (!layers.isEmpty()) {
                        throw failure("Empty layer in JavaFX transition value.", span);
                    }
                    return immutableLayers(layers);
                }

                var character = source.charAt(index);
                if (terminator >= 0 && character == terminator) {
                    index++;
                    if (!current.isEmpty()) {
                        layers.add(List.copyOf(current));
                    } else if (!layers.isEmpty()) {
                        throw failure("Empty function argument in JavaFX transition value.", span);
                    }
                    return immutableLayers(layers);
                }
                if (character == ',') {
                    if (current.isEmpty()) {
                        throw failure("Empty layer in JavaFX transition value.", span);
                    }
                    layers.add(List.copyOf(current));
                    current.clear();
                    index++;
                    continue;
                }
                current.add(nextToken());
            }
        }

        /// Returns an immutable snapshot of parsed layers.
        ///
        /// @param layers the mutable layer accumulator
        /// @return an immutable nested list
        private static @Unmodifiable List<@Unmodifiable List<Token>> immutableLayers(
                List<List<Token>> layers
        ) {
            return layers.stream().map(List::copyOf).toList();
        }

        /// Reads one non-trivia token.
        ///
        /// @return the parsed token
        private Token nextToken() {
            var character = source.charAt(index);
            if (character == '\'' || character == '"') {
                return quotedToken(character);
            }
            if (startsNumber(index)) {
                return numericToken();
            }
            if (JavaFXCssLexer.identifierEnd(source, index) > index) {
                return identifierOrFunctionToken();
            }
            throw failure(
                    "Unsupported token in JavaFX transition value.",
                    span
            );
        }

        /// Reads one quoted string token.
        ///
        /// @param quote the opening quote character
        /// @return the legacy JavaFX string token
        private Token quotedToken(char quote) {
            var contentStart = ++index;
            var end = source.indexOf(quote, contentStart);
            if (end < 0) {
                throw failure("Unclosed string in JavaFX transition value.", span);
            }
            index = end + 1;
            return new Token(
                    TokenType.STRING,
                    source.substring(contentStart, end),
                    List.of()
            );
        }

        /// Reads an identifier or immediately following function.
        ///
        /// @return the identifier or function token
        private Token identifierOrFunctionToken() {
            var start = index;
            index = JavaFXCssLexer.identifierEnd(source, index);
            var name = source.substring(start, index);
            if (index < source.length() && source.charAt(index) == '(') {
                index++;
                return new Token(
                        TokenType.FUNCTION,
                        name,
                        parseLayers(')')
                );
            }
            return new Token(TokenType.IDENTIFIER, name, List.of());
        }

        /// Reads a number with an optional percentage or time unit.
        ///
        /// @return the numeric token
        private Token numericToken() {
            var start = index;
            if (source.charAt(index) == '+' || source.charAt(index) == '-') {
                index++;
            }
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (index < source.length() && source.charAt(index) == '.') {
                index++;
                while (index < source.length()
                        && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            if (index < source.length()
                    && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                var exponent = index;
                index++;
                if (index < source.length()
                        && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                    index++;
                }
                var digits = index;
                while (index < source.length()
                        && Character.isDigit(source.charAt(index))) {
                    index++;
                }
                if (digits == index) {
                    index = exponent;
                }
            }
            var number = source.substring(start, index);
            if (index < source.length() && source.charAt(index) == '%') {
                index++;
                return new Token(TokenType.PERCENTAGE, number, List.of());
            }

            var unitStart = index;
            while (index < source.length()
                    && Character.isLetter(source.charAt(index))) {
                index++;
            }
            if (unitStart != index) {
                var unit = source.substring(unitStart, index);
                if (unit.equalsIgnoreCase("s") || unit.equalsIgnoreCase("ms")) {
                    return new Token(TokenType.TIME, number, List.of());
                }
                throw failure(
                        "JavaFX transition times support only s and ms units.",
                        span
                );
            }
            return new Token(TokenType.NUMBER, number, List.of());
        }

        /// Returns whether a number begins at an offset.
        ///
        /// @param offset the candidate offset
        /// @return whether the source begins a CSS number
        private boolean startsNumber(int offset) {
            var cursor = offset;
            if (source.charAt(cursor) == '+' || source.charAt(cursor) == '-') {
                cursor++;
                if (cursor >= source.length()) {
                    return false;
                }
            }
            if (Character.isDigit(source.charAt(cursor))) {
                return true;
            }
            return source.charAt(cursor) == '.'
                    && cursor + 1 < source.length()
                    && Character.isDigit(source.charAt(cursor + 1));
        }

        /// Skips JavaFX whitespace and comments.
        private void skipTrivia() {
            var end = JavaFXCssLexer.triviaEnd(source, index);
            if (end < 0) {
                throw failure(
                        "A JavaFX comment must end before the stylesheet does.",
                        span
                );
            }
            index = end;
        }
    }
}
