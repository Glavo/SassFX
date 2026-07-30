// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.bss;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.css.JavaFXValueFunction;
import org.glavo.sassfx.internal.value.ListSeparator;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Parses JavaFX border-style declarations retained in the evaluated Sass value model.
///
/// JavaFX represents a border style as a layered sequence of four-sided style
/// records. This parser reconstructs its documented dash, phase, stroke-type,
/// line-join, and line-cap grammar without loading JavaFX classes.
@NotNullByDefault
final class JavaFXBorderStyleParser {
    /// Matches one finite decimal CSS number followed by an optional unit.
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)([%a-zA-Z]+)?"
    );

    /// Prevents instantiation.
    private JavaFXBorderStyleParser() {
    }

    /// Parses one JavaFX border-style declaration into normalized layers.
    ///
    /// @param value the evaluated Sass declaration value
    /// @param span  the source range associated with the value
    /// @return immutable four-sided border-style layers in source order
    /// @throws BssSerializeException if the value cannot be represented by JavaFX's grammar
    static @Unmodifiable List<BorderStyleLayer> parseLayers(SassValue value, SourceSpan span) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        var values = layeredValues(value, span);
        var layers = new ArrayList<BorderStyleLayer>(values.size());
        for (var layer : values) {
            layers.add(parseLayer(layer, span));
        }
        return List.copyOf(layers);
    }

    /// Returns top-level border-style layer values.
    ///
    /// A non-comma Sass list remains one layer so its space-separated sides can be parsed together.
    ///
    /// @param value the evaluated Sass declaration value
    /// @param span  the source range associated with the value
    /// @return immutable source-order layer values
    /// @throws BssSerializeException if the layer list is malformed
    private static @Unmodifiable List<SassValue> layeredValues(SassValue value, SourceSpan span) {
        if (!(value instanceof SassList list)) {
            return List.of(value);
        }
        if (list.hasBrackets() || list.contents().isEmpty()) {
            throw invalidBorderStyle(span);
        }
        if (list.separator() == ListSeparator.COMMA) {
            return List.copyOf(list.contents());
        }
        if (list.separator() == ListSeparator.SPACE) {
            return List.of(value);
        }
        throw invalidBorderStyle(span);
    }

    /// Parses one comma-separated border-style layer.
    ///
    /// @param value the evaluated layer value
    /// @param span  the source range associated with the value
    /// @return a normalized four-sided style layer
    /// @throws BssSerializeException if the layer grammar is invalid
    private static BorderStyleLayer parseLayer(SassValue value, SourceSpan span) {
        var tokens = spaceSeparatedValues(value, span);
        var supplied = new ArrayList<BorderStyle>(4);
        var index = 0;
        while (index < tokens.size()) {
            if (supplied.size() == 4) {
                throw invalidBorderStyle(span);
            }
            var parsed = parseStyle(tokens, index, span);
            supplied.add(parsed.style());
            index = parsed.nextIndex();
        }
        return new BorderStyleLayer(expandFourSidedStyles(supplied));
    }

    /// Returns the top-level space-separated values for one border-style layer.
    ///
    /// @param value the evaluated layer value
    /// @param span  the source range associated with the value
    /// @return immutable source-order style tokens
    /// @throws BssSerializeException if the Sass value shape is not a space list
    private static @Unmodifiable List<SassValue> spaceSeparatedValues(SassValue value, SourceSpan span) {
        if (!(value instanceof SassList list)) {
            return List.of(value);
        }
        if (list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()) {
            throw invalidBorderStyle(span);
        }
        return List.copyOf(list.contents());
    }

    /// Parses one JavaFX {@code <border-style>} from a token sequence.
    ///
    /// @param tokens     all tokens in the current layer
    /// @param startIndex the index of the dash-style token
    /// @param span       the source range associated with the value
    /// @return the parsed style and the first unconsumed token index
    /// @throws BssSerializeException if the style grammar is invalid
    private static ParsedBorderStyle parseStyle(
            List<SassValue> tokens,
            int startIndex,
            SourceSpan span
    ) {
        var index = startIndex;
        var dashStyle = parseDashStyle(tokens.get(index++), span);
        @Nullable BorderStyleSize phase = null;
        @Nullable String strokeType = null;
        @Nullable String lineJoin = null;
        @Nullable BorderStyleSize miterLimit = null;
        @Nullable String lineCap = null;

        if (index < tokens.size() && isKeyword(tokens.get(index), "phase")) {
            index++;
            if (index == tokens.size()) {
                throw invalidBorderStyle(span);
            }
            phase = parseSize(tokens.get(index++), span);
        }

        if (index < tokens.size()) {
            @Nullable String candidateStrokeType = strokeType(tokens.get(index));
            if (candidateStrokeType != null) {
                strokeType = candidateStrokeType;
                index++;
            }
        }

        if (index < tokens.size() && isKeyword(tokens.get(index), "line-join")) {
            index++;
            if (index == tokens.size()) {
                throw invalidBorderStyle(span);
            }
            lineJoin = lineJoin(tokens.get(index++), span);
            if (lineJoin.equals("miter")
                    && index < tokens.size()
                    && isSize(tokens.get(index))) {
                miterLimit = parseSize(tokens.get(index++), span);
            }
        }

        if (index < tokens.size() && isKeyword(tokens.get(index), "line-cap")) {
            index++;
            if (index == tokens.size()) {
                throw invalidBorderStyle(span);
            }
            lineCap = lineCap(tokens.get(index++), span);
        }

        return new ParsedBorderStyle(
                new BorderStyle(dashStyle, phase, strokeType, lineJoin, miterLimit, lineCap),
                index
        );
    }

    /// Parses one JavaFX dash-style token.
    ///
    /// @param value the evaluated Sass token
    /// @param span  the source range associated with the value
    /// @return a simple JavaFX dash keyword or a segments sequence
    /// @throws BssSerializeException if the dash-style token is unsupported
    private static DashStyle parseDashStyle(SassValue value, SourceSpan span) {
        @Nullable String text = unquotedText(value);
        if (text == null) {
            throw invalidBorderStyle(span);
        }
        var trimmed = text.trim();
        if (isSegmentsInvocation(trimmed)) {
            return parseSegments(trimmed, span);
        }
        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "none", "hidden", "dotted", "dashed", "solid" -> new KeywordDashStyle(trimmed);
            default -> throw invalidBorderStyle(span);
        };
    }

    /// Returns whether one text token is a complete {@code segments(...)} invocation.
    ///
    /// @param text the candidate function text
    /// @return whether the text begins with the JavaFX segments function name
    private static boolean isSegmentsInvocation(String text) {
        @Nullable var name = JavaFXValueFunction.invocationName(text);
        return name != null
                && name.regionMatches(true, 0, "segments", 0, "segments".length());
    }

    /// Parses one JavaFX {@code segments(...)} dash-style invocation.
    ///
    /// @param text the complete CSS function text
    /// @param span the source range associated with the value
    /// @return a sequence-backed dash style
    /// @throws BssSerializeException if the function syntax is malformed
    private static SegmentsDashStyle parseSegments(String text, SourceSpan span) {
        var invocation = parseFunctionInvocation(text, span);
        if (!invocation.name().regionMatches(
                true,
                0,
                "segments",
                0,
                "segments".length()
        )) {
            throw invalidBorderStyle(span);
        }
        var arguments = splitTopLevelCommas(invocation.arguments(), span);
        var sizes = new ArrayList<BorderStyleSize>(arguments.size());
        for (var argument : arguments) {
            sizes.add(parseTextSize(argument, span));
        }
        return new SegmentsDashStyle(sizes);
    }

    /// Parses one style size from the evaluated Sass value model.
    ///
    /// @param value the evaluated size or unquoted lookup token
    /// @param span  the source range associated with the value
    /// @return a raw size or deferred JavaFX property lookup
    /// @throws BssSerializeException if the value is not JavaFX size syntax
    private static BorderStyleSize parseSize(SassValue value, SourceSpan span) {
        if (value instanceof SassNumber number) {
            return new RawBorderStyleSize(number);
        }
        @Nullable String text = unquotedText(value);
        if (text == null) {
            throw invalidBorderStyle(span);
        }
        return parseTextSize(text, span);
    }

    /// Parses one style size from raw CSS function text.
    ///
    /// @param text the raw size or lookup text
    /// @param span the source range associated with the value
    /// @return a raw size or deferred JavaFX property lookup
    /// @throws BssSerializeException if the text is not JavaFX size syntax
    private static BorderStyleSize parseTextSize(String text, SourceSpan span) {
        @Nullable SassNumber number = tryParseSize(text);
        if (number != null) {
            return new RawBorderStyleSize(number);
        }
        var trimmed = text.trim();
        if (isLookupIdentifier(trimmed)) {
            return new LookupBorderStyleSize(trimmed);
        }
        throw invalidBorderStyle(span);
    }

    /// Returns whether one token would be accepted by JavaFX's {@code parseSize} method.
    ///
    /// @param value the candidate token
    /// @return whether the token is a raw size or an identifier lookup
    private static boolean isSize(SassValue value) {
        if (value instanceof SassNumber) {
            return true;
        }
        @Nullable String text = unquotedText(value);
        return text != null && (tryParseSize(text) != null || isLookupIdentifier(text.trim()));
    }

    /// Returns one normalized JavaFX stroke-type enum spelling.
    ///
    /// @param value the candidate token
    /// @return the enum spelling, or {@code null} when the token is not a stroke type
    private static @Nullable String strokeType(SassValue value) {
        @Nullable String keyword = normalizedKeyword(value);
        return switch (keyword == null ? "" : keyword) {
            case "centered", "inside", "outside" -> keyword;
            default -> null;
        };
    }

    /// Parses one JavaFX stroke-line-join enum spelling.
    ///
    /// @param value the candidate token
    /// @param span  the source range associated with the value
    /// @return the normalized enum spelling
    /// @throws BssSerializeException if the token is not a supported line join
    private static String lineJoin(SassValue value, SourceSpan span) {
        @Nullable String keyword = normalizedKeyword(value);
        if (keyword == null) {
            throw invalidBorderStyle(span);
        }
        return switch (keyword) {
            case "miter", "bevel", "round" -> keyword;
            default -> throw invalidBorderStyle(span);
        };
    }

    /// Parses one JavaFX stroke-line-cap enum spelling.
    ///
    /// @param value the candidate token
    /// @param span  the source range associated with the value
    /// @return the normalized enum spelling
    /// @throws BssSerializeException if the token is not a supported line cap
    private static String lineCap(SassValue value, SourceSpan span) {
        @Nullable String keyword = normalizedKeyword(value);
        if (keyword == null) {
            throw invalidBorderStyle(span);
        }
        return switch (keyword) {
            case "square", "butt", "round" -> keyword;
            default -> throw invalidBorderStyle(span);
        };
    }

    /// Returns whether one Sass value is an unquoted keyword with the supplied spelling.
    ///
    /// @param value   the candidate Sass value
    /// @param expected the expected keyword spelling
    /// @return whether the unquoted value equals the expected keyword ignoring case
    private static boolean isKeyword(SassValue value, String expected) {
        @Nullable String text = unquotedText(value);
        return text != null && text.equalsIgnoreCase(expected);
    }

    /// Returns an unquoted Sass string's normalized keyword spelling.
    ///
    /// @param value the candidate Sass value
    /// @return the lower-case keyword, or {@code null} when the value is not unquoted text
    private static @Nullable String normalizedKeyword(SassValue value) {
        @Nullable String text = unquotedText(value);
        return text == null ? null : text.trim().toLowerCase(Locale.ROOT);
    }

    /// Returns the text of one unquoted Sass string.
    ///
    /// @param value the candidate Sass value
    /// @return the unquoted text, or {@code null} for all other value forms
    private static @Nullable String unquotedText(SassValue value) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            return null;
        }
        return string.text();
    }

    /// Expands one to four supplied styles using JavaFX's four-sided shorthand rules.
    ///
    /// @param supplied the one to four source-order styles
    /// @return top, right, bottom, and left styles
    /// @throws BssSerializeException if the supplied style count is invalid
    private static @Unmodifiable List<BorderStyle> expandFourSidedStyles(List<BorderStyle> supplied) {
        return switch (supplied.size()) {
            case 1 -> List.of(supplied.get(0), supplied.get(0), supplied.get(0), supplied.get(0));
            case 2 -> List.of(supplied.get(0), supplied.get(1), supplied.get(0), supplied.get(1));
            case 3 -> List.of(supplied.get(0), supplied.get(1), supplied.get(2), supplied.get(1));
            case 4 -> List.copyOf(supplied);
            default -> throw new IllegalArgumentException("border style count must be between one and four");
        };
    }

    /// Parses one complete CSS function invocation.
    ///
    /// @param text the raw unquoted CSS text
    /// @param span the source range associated with the value
    /// @return the function name and body without outer parentheses
    /// @throws BssSerializeException if the function syntax is malformed
    private static FunctionInvocation parseFunctionInvocation(String text, SourceSpan span) {
        var trimmed = text.trim();
        var opening = trimmed.indexOf('(');
        if (opening <= 0) {
            throw invalidBorderStyle(span);
        }
        var name = trimmed.substring(0, opening).trim();
        if (name.isEmpty()) {
            throw invalidBorderStyle(span);
        }
        var closing = matchingParenthesis(trimmed, opening, span);
        if (closing != trimmed.length() - 1) {
            throw invalidBorderStyle(span);
        }
        return new FunctionInvocation(name, trimmed.substring(opening + 1, closing));
    }

    /// Returns the matching closing parenthesis for one function invocation.
    ///
    /// @param text    the complete function text
    /// @param opening the opening-parenthesis offset
    /// @param span    the source range associated with the value
    /// @return the matching closing-parenthesis offset
    /// @throws BssSerializeException if nesting or quoting is malformed
    private static int matchingParenthesis(String text, int opening, SourceSpan span) {
        var depth = 0;
        var quote = '\0';
        var escaped = false;
        for (var index = opening; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quote != '\0') {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
                if (depth < 0) {
                    throw invalidBorderStyle(span);
                }
            }
        }
        throw invalidBorderStyle(span);
    }

    /// Splits one function body on commas outside nested functions and strings.
    ///
    /// @param text the raw function body
    /// @param span the source range associated with the value
    /// @return immutable non-empty arguments in source order
    /// @throws BssSerializeException if quoting, nesting, or arguments are malformed
    private static @Unmodifiable List<String> splitTopLevelCommas(String text, SourceSpan span) {
        var values = new ArrayList<String>();
        var start = 0;
        var depth = 0;
        var quote = '\0';
        var escaped = false;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quote != '\0') {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth < 0) {
                    throw invalidBorderStyle(span);
                }
            } else if (character == ',' && depth == 0) {
                addArgument(values, text.substring(start, index), span);
                start = index + 1;
            }
        }
        if (quote != '\0' || depth != 0) {
            throw invalidBorderStyle(span);
        }
        addArgument(values, text.substring(start), span);
        return List.copyOf(values);
    }

    /// Adds one non-empty trimmed function argument.
    ///
    /// @param values    the accumulating function arguments
    /// @param candidate the raw argument text
    /// @param span      the source range associated with the value
    /// @throws BssSerializeException if the candidate is empty
    private static void addArgument(List<String> values, String candidate, SourceSpan span) {
        var trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw invalidBorderStyle(span);
        }
        values.add(trimmed);
    }

    /// Parses one simple finite CSS size without throwing on syntax mismatch.
    ///
    /// @param text the candidate raw size text
    /// @return the parsed Sass number, or {@code null} when the text is not finite size syntax
    private static @Nullable SassNumber tryParseSize(String text) {
        Matcher matcher = SIZE_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        final double number;
        try {
            number = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (!Double.isFinite(number)) {
            return null;
        }
        @Nullable String unit = matcher.group(2);
        return SassNumber.of(number, unit);
    }

    /// Returns whether one text token is a CSS identifier usable for a JavaFX lookup.
    ///
    /// @param text the candidate token
    /// @return whether the token uses the supported identifier subset
    private static boolean isLookupIdentifier(String text) {
        var length = text.length();
        if (length == 0) {
            return false;
        }
        var index = 0;
        if (text.charAt(index) == '-') {
            index++;
            if (index == length) {
                return false;
            }
            if (text.charAt(index) == '-') {
                index++;
                if (index == length) {
                    return false;
                }
            }
        }
        if (!isCssIdentifierStart(text.charAt(index))) {
            return false;
        }
        for (index++; index < length; index++) {
            if (!isCssIdentifierPart(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether one character can begin the supported CSS identifier subset.
    ///
    /// @param character the candidate character
    /// @return whether the character can begin an identifier
    private static boolean isCssIdentifierStart(char character) {
        return character == '_' || character == '\\' || Character.isLetter(character) || character >= 0x80;
    }

    /// Returns whether one character can continue the supported CSS identifier subset.
    ///
    /// @param character the candidate character
    /// @return whether the character can continue an identifier
    private static boolean isCssIdentifierPart(char character) {
        return isCssIdentifierStart(character) || Character.isDigit(character) || character == '-';
    }

    /// Creates the standard border-style serialization failure.
    ///
    /// @param span the source range associated with the value
    /// @return a source-associated serialization failure
    private static BssSerializeException invalidBorderStyle(SourceSpan span) {
        return new BssSerializeException(
                "BSS border styles require JavaFX dash styles with optional phase, stroke, join, and cap clauses.",
                span,
                null
        );
    }

    /// Represents one JavaFX dash-style payload.
    @NotNullByDefault
    sealed interface DashStyle permits KeywordDashStyle, SegmentsDashStyle {
    }

    /// Represents one JavaFX keyword-backed dash style.
    ///
    /// @param keyword the source dash-style keyword
    @NotNullByDefault
    record KeywordDashStyle(String keyword) implements DashStyle {
        /// Creates an immutable keyword dash style.
        KeywordDashStyle {
            keyword = requireNonEmpty(keyword, "keyword");
        }
    }

    /// Represents one JavaFX {@code segments(...)} dash style.
    ///
    /// @param segments the non-empty segment sizes in source order
    @NotNullByDefault
    record SegmentsDashStyle(@Unmodifiable List<BorderStyleSize> segments) implements DashStyle {
        /// Creates an immutable segments dash style.
        SegmentsDashStyle {
            segments = List.copyOf(segments);
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("segments must not be empty");
            }
        }
    }

    /// Represents one raw or lookup JavaFX border-style size.
    @NotNullByDefault
    sealed interface BorderStyleSize permits RawBorderStyleSize, LookupBorderStyleSize {
    }

    /// Represents one raw JavaFX border-style size.
    ///
    /// @param value the raw Sass number
    @NotNullByDefault
    record RawBorderStyleSize(SassNumber value) implements BorderStyleSize {
        /// Creates an immutable raw border-style size.
        RawBorderStyleSize {
            value = Objects.requireNonNull(value, "value");
        }
    }

    /// Represents one JavaFX property lookup used as a border-style size.
    ///
    /// @param key the unquoted property lookup key
    @NotNullByDefault
    record LookupBorderStyleSize(String key) implements BorderStyleSize {
        /// Creates an immutable lookup border-style size.
        LookupBorderStyleSize {
            key = requireNonEmpty(key, "key");
        }
    }

    /// Represents one JavaFX border stroke style.
    ///
    /// @param dashStyle  the required dash-style payload
    /// @param phase      the optional dash phase
    /// @param strokeType the optional stroke-type enum spelling
    /// @param lineJoin   the optional line-join enum spelling
    /// @param miterLimit the optional miter limit
    /// @param lineCap    the optional line-cap enum spelling
    @NotNullByDefault
    record BorderStyle(
            DashStyle dashStyle,
            @Nullable BorderStyleSize phase,
            @Nullable String strokeType,
            @Nullable String lineJoin,
            @Nullable BorderStyleSize miterLimit,
            @Nullable String lineCap
    ) {
        /// Creates an immutable border stroke style.
        BorderStyle {
            dashStyle = Objects.requireNonNull(dashStyle, "dashStyle");
            if (miterLimit != null && !"miter".equals(lineJoin)) {
                throw new IllegalArgumentException("a miter limit requires a miter line join");
            }
        }
    }

    /// Represents one normalized four-sided JavaFX border-style layer.
    ///
    /// @param styles the top, right, bottom, and left styles
    @NotNullByDefault
    record BorderStyleLayer(@Unmodifiable List<BorderStyle> styles) {
        /// Creates an immutable four-sided border-style layer.
        BorderStyleLayer {
            styles = List.copyOf(styles);
            if (styles.size() != 4) {
                throw new IllegalArgumentException("border style layers must contain exactly four styles");
            }
        }
    }

    /// Stores a parsed border style with its first unconsumed token index.
    ///
    /// @param style     the parsed border style
    /// @param nextIndex the first unconsumed token index
    @NotNullByDefault
    private record ParsedBorderStyle(BorderStyle style, int nextIndex) {
        /// Creates an immutable parsed border-style result.
        ParsedBorderStyle {
            style = Objects.requireNonNull(style, "style");
            if (nextIndex < 0) {
                throw new IllegalArgumentException("nextIndex must not be negative");
            }
        }
    }

    /// Represents one complete raw CSS function invocation.
    ///
    /// @param name      the function name without its opening parenthesis
    /// @param arguments the source body without outer parentheses
    @NotNullByDefault
    private record FunctionInvocation(String name, String arguments) {
        /// Creates an immutable raw function invocation.
        FunctionInvocation {
            name = Objects.requireNonNull(name, "name");
            arguments = Objects.requireNonNull(arguments, "arguments");
        }
    }

    /// Validates one required non-empty string component.
    ///
    /// @param value the candidate string
    /// @param name  the component name used in the failure message
    /// @return the validated input string
    /// @throws IllegalArgumentException if the string is empty
    private static String requireNonEmpty(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
