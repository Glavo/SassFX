// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.bss;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.css.JavaFXLegacyGradient;
import org.glavo.sassfx.internal.css.JavaFXValueFunction;
import org.glavo.sassfx.internal.value.SassColor;
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

/// Parses the JavaFX paint subset retained as Sass values and CSS function text.
///
/// The evaluator preserves unknown CSS functions as unquoted [SassString]
/// values. This parser reconstructs JavaFX solid, derived, and ladder colors,
/// property lookups, gradients, and image patterns needed by layered and scalar
/// paint declarations, including `region(...)` selector references, without
/// loading JavaFX classes at compilation time.
@NotNullByDefault
final class JavaFXPaintParser {
    /// Matches one finite decimal CSS number followed by an optional unit.
    private static final Pattern SIZE_PATTERN = Pattern.compile(
            "([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)([%a-zA-Z]+)?"
    );

    /// Contains JavaFX's non-repeating gradient cycle-method spelling.
    private static final String NO_CYCLE = "NO_CYCLE";

    /// Contains JavaFX's repeating gradient cycle-method spelling.
    private static final String REPEAT = "REPEAT";

    /// Contains JavaFX's reflecting gradient cycle-method spelling.
    private static final String REFLECT = "REFLECT";

    /// Prefixes the selector reference stored by JavaFX's `region(...)` parser.
    private static final String REGION_REFERENCE_PREFIX = "SPECIAL-REGION-URL:";

    /// Contains the zero-percent gradient coordinate.
    private static final SassNumber ZERO_PERCENT = SassNumber.of(0.0, "%");

    /// Contains the hundred-percent gradient coordinate.
    private static final SassNumber ONE_HUNDRED_PERCENT = SassNumber.of(100.0, "%");

    /// Prevents instantiation.
    private JavaFXPaintParser() {
    }

    /// Parses one BSS paint declaration value.
    ///
    /// @param value the evaluated Sass value
    /// @param span  the source range associated with the value
    /// @return the normalized solid or gradient paint
    /// @throws BssSerializeException if the value is not a supported JavaFX paint
    static Paint parse(SassValue value, SourceSpan span) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        if (value instanceof SassColor color) {
            return new SolidPaint(color);
        }
        @Nullable var legacyGradient = JavaFXLegacyGradient.serialize(value);
        if (legacyGradient != null) {
            return parseTextPaint(legacyGradient, span);
        }
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            throw invalidPaint(span);
        }
        return parseTextPaint(string.text(), span);
    }

    /// Returns whether a value is one of JavaFX's paint function forms.
    ///
    /// Solid Sass colors and property lookup identifiers do not need this
    /// predicate because the generic declaration encoder already preserves
    /// their BSS representation.
    ///
    /// @param value the evaluated Sass value
    /// @return whether the value begins with a supported unquoted paint function
    static boolean isPaintFunction(SassValue value) {
        if (JavaFXLegacyGradient.serialize(value) != null) {
            return true;
        }
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            return false;
        }
        var text = string.text().stripLeading();
        var parenthesis = text.indexOf('(');
        if (parenthesis <= 0) {
            return false;
        }
        @Nullable var function = JavaFXValueFunction.fromName(
                text.substring(0, parenthesis).trim()
        );
        if (function == null) {
            return false;
        }
        return switch (function) {
            case RGB,
                 HSB,
                 DERIVE,
                 LINEAR_GRADIENT,
                 RADIAL_GRADIENT,
                 IMAGE_PATTERN,
                 REPEATING_IMAGE_PATTERN,
                 LADDER,
                 REGION -> true;
            default -> false;
        };
    }

    /// Parses one unquoted JavaFX paint value.
    ///
    /// @param text the preserved CSS text
    /// @param span the source range associated with the value
    /// @return the normalized lookup, gradient, or image-pattern paint
    /// @throws BssSerializeException if the text is not a supported JavaFX paint
    private static Paint parseTextPaint(String text, SourceSpan span) {
        var trimmed = text.trim();
        if (isLookupIdentifier(trimmed)) {
            return new LookupPaint(trimmed);
        }
        if (beginsLegacyGradient(trimmed, "linear")) {
            return parseLegacyLinearGradient(trimmed, span);
        }
        if (beginsLegacyGradient(trimmed, "radial")) {
            return parseLegacyRadialGradient(trimmed, span);
        }
        if (beginsLegacyGradient(trimmed, "ladder")) {
            return parseLegacyLadderColor(trimmed, span);
        }
        var function = parseFunctionInvocation(trimmed, span);
        @Nullable var functionKind = JavaFXValueFunction.fromName(function.name());
        if (functionKind == null) {
            throw invalidPaint(span);
        }
        return switch (functionKind) {
            case RGB, HSB -> new SolidPaint(parseColorFunction(function, span));
            case LINEAR_GRADIENT -> parseLinearGradient(function.arguments(), span);
            case RADIAL_GRADIENT -> parseRadialGradient(function.arguments(), span);
            case IMAGE_PATTERN -> parseImagePattern(function.arguments(), span);
            case REPEATING_IMAGE_PATTERN ->
                    parseRepeatingImagePattern(function.arguments(), span);
            case DERIVE -> parseDerivedColor(function.arguments(), span);
            case LADDER -> parseLadderColor(function.arguments(), span);
            case REGION -> parseRegionReference(function.arguments(), span);
            default -> throw invalidPaint(span);
        };
    }

    /// Returns whether text begins with one legacy gradient keyword followed
    /// by a separate token.
    ///
    /// @param text the complete paint text
    /// @param keyword the lower-case legacy gradient keyword
    /// @return whether the legacy grammar should parse the text
    private static boolean beginsLegacyGradient(
            String text,
            String keyword
    ) {
        return text.length() > keyword.length()
                && text.regionMatches(true, 0, keyword, 0, keyword.length())
                && Character.isWhitespace(text.charAt(keyword.length()));
    }

    /// Parses JavaFX's deprecated `linear (...) to (...) stops ...` grammar.
    ///
    /// @param text the complete legacy gradient text
    /// @param span the source range associated with the declaration
    /// @return the normalized linear gradient
    /// @throws BssSerializeException if the legacy grammar is invalid
    private static LinearGradientPaint parseLegacyLinearGradient(
            String text,
            SourceSpan span
    ) {
        var cursor = new LegacyGradientCursor(text, span);
        cursor.requireKeyword("linear");
        var start = parseLegacyPoint(cursor.parenthesized(), span);
        cursor.requireKeyword("to");
        var end = parseLegacyPoint(cursor.parenthesized(), span);
        cursor.requireKeyword("stops");
        var stops = parseLegacyStops(cursor, span);
        var cycleMethod = parseLegacyCycleMethod(cursor, span);
        return new LinearGradientPaint(
                start.x(),
                start.y(),
                end.x(),
                end.y(),
                cycleMethod,
                stops,
                true
        );
    }

    /// Parses JavaFX's deprecated `radial ... stops ...` grammar.
    ///
    /// @param text the complete legacy gradient text
    /// @param span the source range associated with the declaration
    /// @return the normalized radial gradient
    /// @throws BssSerializeException if the legacy grammar is invalid
    private static RadialGradientPaint parseLegacyRadialGradient(
            String text,
            SourceSpan span
    ) {
        var cursor = new LegacyGradientCursor(text, span);
        cursor.requireKeyword("radial");
        @Nullable GradientSize focusAngle = null;
        @Nullable GradientSize focusDistance = null;
        @Nullable GradientSize centerX = null;
        @Nullable GradientSize centerY = null;
        if (cursor.consumeKeyword("focus-angle")) {
            focusAngle = parseGradientSize(cursor.token(), span);
        }
        if (cursor.consumeKeyword("focus-distance")) {
            focusDistance = parseGradientSize(cursor.token(), span);
        }
        if (cursor.consumeKeyword("center")) {
            var center = parseLegacyPoint(cursor.parenthesized(), span);
            centerX = center.x();
            centerY = center.y();
        }
        var radius = parseGradientSize(cursor.token(), span);
        cursor.requireKeyword("stops");
        var stops = parseLegacyStops(cursor, span);
        var cycleMethod = parseLegacyCycleMethod(cursor, span);
        return new RadialGradientPaint(
                focusAngle,
                focusDistance,
                centerX,
                centerY,
                radius,
                cycleMethod,
                stops,
                true
        );
    }

    /// Parses JavaFX's deprecated `ladder color stops ...` grammar.
    ///
    /// @param text the complete legacy ladder text
    /// @param span the source range associated with the declaration
    /// @return the normalized ladder color
    /// @throws BssSerializeException if the legacy grammar is invalid
    private static LadderPaint parseLegacyLadderColor(
            String text,
            SourceSpan span
    ) {
        var cursor = new LegacyGradientCursor(text, span);
        cursor.requireKeyword("ladder");
        var base = parseColorPaint(cursor.valueBeforeStopsKeyword(), span);
        var stops = parseLegacyStops(cursor, span);
        cursor.requireEnd();
        return new LadderPaint(base, stops);
    }

    /// Parses a comma-separated legacy gradient point.
    ///
    /// @param text the point body without parentheses
    /// @param span the source range associated with the declaration
    /// @return the two parsed coordinates
    /// @throws BssSerializeException if the point is malformed
    private static LegacyPoint parseLegacyPoint(
            String text,
            SourceSpan span
    ) {
        var components = splitTopLevelCommas(text, span);
        if (components.size() < 2) {
            throw invalidPaint(span);
        }
        return new LegacyPoint(
                parseGradientSize(components.get(0), span),
                parseGradientSize(components.get(1), span)
        );
    }

    /// Parses consecutive legacy `(offset, color)` stops.
    ///
    /// @param cursor the cursor positioned at the first stop
    /// @param span the source range associated with the declaration
    /// @return immutable stops in source order
    /// @throws BssSerializeException if no stop or an invalid stop is present
    private static @Unmodifiable List<GradientStop> parseLegacyStops(
            LegacyGradientCursor cursor,
            SourceSpan span
    ) {
        var result = new ArrayList<GradientStop>();
        while (cursor.nextIsParenthesized()) {
            var components = splitTopLevelCommas(
                    cursor.parenthesized(),
                    span
            );
            if (components.size() < 2) {
                throw invalidPaint(span);
            }
            result.add(new GradientStop(
                    parseGradientSize(components.get(0), span),
                    parseGradientColor(components.get(1), span)
            ));
        }
        if (result.isEmpty()) {
            throw invalidPaint(span);
        }
        return List.copyOf(result);
    }

    /// Parses the optional trailing legacy cycle method and requires EOF.
    ///
    /// @param cursor the cursor after the final stop
    /// @param span the source range associated with the declaration
    /// @return the normalized cycle-method constant
    /// @throws BssSerializeException if trailing text is invalid
    private static String parseLegacyCycleMethod(
            LegacyGradientCursor cursor,
            SourceSpan span
    ) {
        var cycleMethod = NO_CYCLE;
        if (cursor.hasMore()) {
            var token = cursor.token();
            if (token.equalsIgnoreCase("no-cycle")) {
                cycleMethod = NO_CYCLE;
            } else {
                @Nullable var parsed = gradientCycleMethod(token);
                if (parsed == null) {
                    throw invalidPaint(span);
                }
                cycleMethod = parsed;
            }
        }
        if (cursor.hasMore()) {
            throw invalidPaint(span);
        }
        return cycleMethod;
    }

    /// Parses JavaFX's `region("selector")` paint reference.
    ///
    /// JavaFX reads only the first token, requires it to be quoted, removes
    /// only the surrounding quote characters, and retains the token's
    /// remaining spelling, including CSS escape sequences. Later tokens and
    /// comma-separated arguments are ignored.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the normalized selector reference
    /// @throws BssSerializeException if the first token is not a quoted string
    private static RegionReferencePaint parseRegionReference(
            String arguments,
            SourceSpan span
    ) {
        var text = arguments.stripLeading();
        if (text.isEmpty()) {
            throw invalidPaint(span);
        }
        var quote = text.charAt(0);
        if (quote != '\'' && quote != '"') {
            throw invalidPaint(span);
        }
        var escaped = false;
        for (var index = 1; index < text.length(); index++) {
            var character = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == quote) {
                return new RegionReferencePaint(
                        REGION_REFERENCE_PREFIX + text.substring(1, index)
                );
            }
        }
        throw invalidPaint(span);
    }

    /// Parses JavaFX's `derive(color, brightness)` color function.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the normalized derived color
    /// @throws BssSerializeException if the grammar is invalid
    private static DerivedPaint parseDerivedColor(
            String arguments,
            SourceSpan span
    ) {
        var values = splitTopLevelCommas(arguments, span);
        if (values.size() != 2) {
            throw invalidPaint(span);
        }
        return new DerivedPaint(
                parseColorPaint(values.get(0), span),
                parseSize(values.get(1), span)
        );
    }

    /// Parses JavaFX's `ladder(color, stops...)` color function.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the normalized ladder color
    /// @throws BssSerializeException if the grammar is invalid
    private static LadderPaint parseLadderColor(
            String arguments,
            SourceSpan span
    ) {
        var values = splitTopLevelCommas(arguments, span);
        if (values.size() < 3) {
            throw invalidPaint(span);
        }
        return new LadderPaint(
                parseColorPaint(values.get(0), span),
                parseGradientStops(values.subList(1, values.size()), span)
        );
    }

    /// Parses JavaFX's {@code image-pattern(...)} grammar.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the normalized image-pattern paint
    /// @throws BssSerializeException if the grammar is invalid
    private static ImagePatternPaint parseImagePattern(String arguments, SourceSpan span) {
        var argumentsList = splitTopLevelCommas(arguments, span);
        if (argumentsList.size() != 1 && argumentsList.size() != 5 && argumentsList.size() != 6) {
            throw invalidPaint(span);
        }
        var resource = parseImagePatternResource(argumentsList.get(0), span);
        if (argumentsList.size() == 1) {
            return new ImagePatternPaint(resource, null, null, null, null, null);
        }
        @Nullable Boolean proportional = argumentsList.size() == 6
                ? Boolean.parseBoolean(argumentsList.get(5).trim())
                : null;
        return new ImagePatternPaint(
                resource,
                parseImagePatternSize(argumentsList.get(1), span),
                parseImagePatternSize(argumentsList.get(2), span),
                parseImagePatternSize(argumentsList.get(3), span),
                parseImagePatternSize(argumentsList.get(4), span),
                proportional
        );
    }

    /// Parses JavaFX's {@code repeating-image-pattern(...)} grammar.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the normalized repeating image-pattern paint
    /// @throws BssSerializeException if the grammar is invalid
    private static RepeatingImagePatternPaint parseRepeatingImagePattern(
            String arguments,
            SourceSpan span
    ) {
        var argumentsList = splitTopLevelCommas(arguments, span);
        if (argumentsList.size() != 1) {
            throw invalidPaint(span);
        }
        return new RepeatingImagePatternPaint(parseImagePatternResource(argumentsList.get(0), span));
    }

    /// Parses the URI token used by one JavaFX image pattern.
    ///
    /// JavaFX retains direct string-token spelling, including surrounding
    /// quotes, but decodes a nested {@code url(...)} token through its URL
    /// converter before storing it in BSS.
    ///
    /// @param argument the complete first image-pattern argument
    /// @param span     the source range associated with the declaration
    /// @return the JavaFX URI token text
    /// @throws BssSerializeException if the URI token is not supported
    private static String parseImagePatternResource(String argument, SourceSpan span) {
        var trimmed = argument.trim();
        if (trimmed.regionMatches(true, 0, "url(", 0, 4)) {
            return BssSerializer.urlResource(trimmed, span);
        }
        if (isQuotedCssString(trimmed) || isLookupIdentifier(trimmed)) {
            return trimmed;
        }
        throw invalidPaint(span);
    }

    /// Parses one JavaFX image-pattern coordinate or dimension.
    ///
    /// @param argument the complete size argument
    /// @param span     the source range associated with the declaration
    /// @return a raw size or a deferred property lookup
    /// @throws BssSerializeException if the size grammar is invalid
    private static ImagePatternSize parseImagePatternSize(String argument, SourceSpan span) {
        @Nullable SassNumber rawSize = tryParseSize(argument);
        if (rawSize != null) {
            return new RawImagePatternSize(rawSize);
        }
        var trimmed = argument.trim();
        if (isLookupIdentifier(trimmed)) {
            return new LookupImagePatternSize(trimmed);
        }
        throw invalidPaint(span);
    }

    /// Parses one JavaFX gradient coordinate, radius, angle, or stop offset.
    ///
    /// @param argument the complete size token
    /// @param span     the source range associated with the declaration
    /// @return a raw size or a deferred property lookup
    /// @throws BssSerializeException if the size grammar is invalid
    private static GradientSize parseGradientSize(
            String argument,
            SourceSpan span
    ) {
        @Nullable SassNumber rawSize = tryParseSize(argument);
        if (rawSize != null) {
            return new RawGradientSize(rawSize);
        }
        var trimmed = argument.trim();
        if (isLookupIdentifier(trimmed)) {
            return new LookupGradientSize(trimmed);
        }
        throw invalidPaint(span);
    }

    /// Parses JavaFX's {@code linear-gradient(...)} grammar.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the normalized linear gradient
    /// @throws BssSerializeException if the grammar is invalid
    private static LinearGradientPaint parseLinearGradient(String arguments, SourceSpan span) {
        var argumentsList = splitTopLevelCommas(arguments, span);
        if (argumentsList.size() < 2) {
            throw invalidPaint(span);
        }

        var direction = defaultLinearDirection();
        var index = 0;
        var firstComponents = splitComponents(argumentsList.get(0), span);
        if (!firstComponents.isEmpty() && equalsKeyword(firstComponents.get(0), "from")) {
            direction = parseExplicitLinearDirection(firstComponents, span);
            index++;
        } else if (!firstComponents.isEmpty() && equalsKeyword(firstComponents.get(0), "to")) {
            direction = parseKeywordLinearDirection(firstComponents, span);
            index++;
        }

        var cycleMethod = NO_CYCLE;
        if (index < argumentsList.size()) {
            @Nullable String parsedCycleMethod = gradientCycleMethod(argumentsList.get(index));
            if (parsedCycleMethod != null) {
                cycleMethod = parsedCycleMethod;
                index++;
            }
        }

        var stops = parseGradientStops(argumentsList.subList(index, argumentsList.size()), span);
        return new LinearGradientPaint(
                new RawGradientSize(direction.startX()),
                new RawGradientSize(direction.startY()),
                new RawGradientSize(direction.endX()),
                new RawGradientSize(direction.endY()),
                cycleMethod,
                stops,
                false
        );
    }

    /// Parses JavaFX's {@code radial-gradient(...)} grammar.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the normalized radial gradient
    /// @throws BssSerializeException if the grammar is invalid
    private static RadialGradientPaint parseRadialGradient(String arguments, SourceSpan span) {
        var argumentsList = splitTopLevelCommas(arguments, span);
        if (argumentsList.size() < 3) {
            throw invalidPaint(span);
        }

        var index = 0;
        @Nullable SassNumber focusAngle = null;
        @Nullable SassNumber focusDistance = null;
        @Nullable SassNumber centerX = null;
        @Nullable SassNumber centerY = null;

        if (index < argumentsList.size()
                && beginsWithKeyword(argumentsList.get(index), "focus-angle", span)) {
            focusAngle = parseSingleKeywordSize(argumentsList.get(index), "focus-angle", span);
            validateFocusAngle(focusAngle, span);
            index++;
        }
        if (index < argumentsList.size()
                && beginsWithKeyword(argumentsList.get(index), "focus-distance", span)) {
            focusDistance = parseSingleKeywordSize(argumentsList.get(index), "focus-distance", span);
            if (!"%".equals(sizeUnit(focusDistance))) {
                throw invalidPaint(span);
            }
            index++;
        }
        if (index < argumentsList.size()
                && beginsWithKeyword(argumentsList.get(index), "center", span)) {
            var components = splitComponents(argumentsList.get(index), span);
            if (components.size() != 3) {
                throw invalidPaint(span);
            }
            centerX = parseSize(components.get(1), span);
            centerY = parseSize(components.get(2), span);
            index++;
        }

        if (index >= argumentsList.size()
                || !beginsWithKeyword(argumentsList.get(index), "radius", span)) {
            throw invalidPaint(span);
        }
        var radius = parseSingleKeywordSize(argumentsList.get(index), "radius", span);
        index++;

        var cycleMethod = NO_CYCLE;
        if (index < argumentsList.size()) {
            @Nullable String parsedCycleMethod = gradientCycleMethod(argumentsList.get(index));
            if (parsedCycleMethod != null) {
                cycleMethod = parsedCycleMethod;
                index++;
            }
        }

        var stops = parseGradientStops(argumentsList.subList(index, argumentsList.size()), span);
        return new RadialGradientPaint(
                focusAngle == null ? null : new RawGradientSize(focusAngle),
                focusDistance == null
                        ? null
                        : new RawGradientSize(focusDistance),
                centerX == null ? null : new RawGradientSize(centerX),
                centerY == null ? null : new RawGradientSize(centerY),
                new RawGradientSize(radius),
                cycleMethod,
                stops,
                false
        );
    }

    /// Returns JavaFX's default top-to-bottom linear-gradient direction.
    ///
    /// @return the default coordinate pair
    private static LinearDirection defaultLinearDirection() {
        return new LinearDirection(
                ZERO_PERCENT,
                ZERO_PERCENT,
                ZERO_PERCENT,
                ONE_HUNDRED_PERCENT
        );
    }

    /// Parses the {@code from x y to x y} linear-gradient direction form.
    ///
    /// @param components the whitespace-separated first argument components
    /// @param span       the source range associated with the declaration
    /// @return the explicit coordinate direction
    /// @throws BssSerializeException if the grammar is invalid
    private static LinearDirection parseExplicitLinearDirection(
            List<String> components,
            SourceSpan span
    ) {
        if (components.size() != 6 || !equalsKeyword(components.get(3), "to")) {
            throw invalidPaint(span);
        }
        return new LinearDirection(
                parseSize(components.get(1), span),
                parseSize(components.get(2), span),
                parseSize(components.get(4), span),
                parseSize(components.get(5), span)
        );
    }

    /// Parses the {@code to side-or-corner} linear-gradient direction form.
    ///
    /// @param components the whitespace-separated first argument components
    /// @param span       the source range associated with the declaration
    /// @return the keyword-derived coordinate direction
    /// @throws BssSerializeException if the grammar is invalid
    private static LinearDirection parseKeywordLinearDirection(
            List<String> components,
            SourceSpan span
    ) {
        if (components.size() < 2 || components.size() > 3) {
            throw invalidPaint(span);
        }

        @Nullable String horizontal = null;
        @Nullable String vertical = null;
        for (var index = 1; index < components.size(); index++) {
            var side = components.get(index).toLowerCase(Locale.ROOT);
            switch (side) {
                case "left", "right" -> {
                    if (horizontal != null) {
                        throw invalidPaint(span);
                    }
                    horizontal = side;
                }
                case "top", "bottom" -> {
                    if (vertical != null) {
                        throw invalidPaint(span);
                    }
                    vertical = side;
                }
                default -> throw invalidPaint(span);
            }
        }

        var startX = "left".equals(horizontal) ? ONE_HUNDRED_PERCENT : ZERO_PERCENT;
        var endX = "right".equals(horizontal) ? ONE_HUNDRED_PERCENT : ZERO_PERCENT;
        var startY = "top".equals(vertical) ? ONE_HUNDRED_PERCENT : ZERO_PERCENT;
        var endY = "bottom".equals(vertical) ? ONE_HUNDRED_PERCENT : ZERO_PERCENT;
        return new LinearDirection(startX, startY, endX, endY);
    }

    /// Parses a single keyword-and-size radial-gradient argument.
    ///
    /// @param argument the complete comma-separated argument
    /// @param keyword  the required keyword
    /// @param span     the source range associated with the declaration
    /// @return the parsed size
    /// @throws BssSerializeException if the grammar is invalid
    private static SassNumber parseSingleKeywordSize(
            String argument,
            String keyword,
            SourceSpan span
    ) {
        var components = splitComponents(argument, span);
        if (components.size() != 2 || !equalsKeyword(components.get(0), keyword)) {
            throw invalidPaint(span);
        }
        return parseSize(components.get(1), span);
    }

    /// Validates a JavaFX radial focus angle size.
    ///
    /// JavaFX accepts angular units and its historical pixel/unitless form.
    ///
    /// @param angle the parsed angle size
    /// @param span  the source range associated with the declaration
    /// @throws BssSerializeException if the unit is not accepted by JavaFX
    private static void validateFocusAngle(SassNumber angle, SourceSpan span) {
        switch (sizeUnit(angle)) {
            case "", "px", "deg", "rad", "grad", "turn" -> {
            }
            default -> throw invalidPaint(span);
        }
    }

    /// Returns the JavaFX cycle method for an argument, if it is one.
    ///
    /// @param argument the complete comma-separated argument
    /// @return the JavaFX enum spelling, or {@code null} for a color stop
    private static @Nullable String gradientCycleMethod(String argument) {
        return switch (argument.trim().toLowerCase(Locale.ROOT)) {
            case "repeat" -> REPEAT;
            case "reflect" -> REFLECT;
            default -> null;
        };
    }
    /// Parses and normalizes JavaFX color stops.
    ///
    /// The normalization follows JavaFX's CSS parser: omitted endpoints
    /// become zero and one hundred percent, descending offsets are clamped,
    /// and omitted intermediate offsets are evenly distributed.
    ///
    /// @param arguments the remaining comma-separated color-stop arguments
    /// @param span      the source range associated with the declaration
    /// @return normalized color stops in source order
    /// @throws BssSerializeException if fewer than two valid stops are present
    private static @Unmodifiable List<GradientStop> parseGradientStops(
            List<String> arguments,
            SourceSpan span
    ) {
        if (arguments.size() < 2) {
            throw invalidPaint(span);
        }

        var rawStops = new ArrayList<RawGradientStop>(arguments.size());
        for (var argument : arguments) {
            rawStops.add(parseGradientStop(argument, span));
        }
        return normalizeGradientStops(rawStops);
    }

    /// Parses one JavaFX {@code <color-stop>} argument.
    ///
    /// @param argument the complete comma-separated color-stop argument
    /// @param span     the source range associated with the declaration
    /// @return the unnormalized color stop
    /// @throws BssSerializeException if the color-stop grammar is invalid
    private static RawGradientStop parseGradientStop(String argument, SourceSpan span) {
        var components = splitComponents(argument, span);
        if (components.isEmpty() || components.size() > 2) {
            throw invalidPaint(span);
        }
        var color = parseGradientColor(components.get(0), span);
        @Nullable SassNumber offset = components.size() == 2
                ? parseSize(components.get(1), span)
                : null;
        return new RawGradientStop(color, offset);
    }

    /// Normalizes omitted and descending gradient offsets.
    ///
    /// @param rawStops the unnormalized source-order stops
    /// @return immutable normalized stops
    private static @Unmodifiable List<GradientStop> normalizeGradientStops(
            List<RawGradientStop> rawStops
    ) {
        var positions = new ArrayList<@Nullable SassNumber>(rawStops.size());
        for (var stop : rawStops) {
            positions.add(stop.offset());
        }
        positions.set(0, positions.get(0) == null ? ZERO_PERCENT : positions.get(0));
        var lastIndex = positions.size() - 1;
        positions.set(
                lastIndex,
                positions.get(lastIndex) == null ? ONE_HUNDRED_PERCENT : positions.get(lastIndex)
        );

        @Nullable SassNumber maximum = null;
        for (var index = 1; index < positions.size(); index++) {
            @Nullable SassNumber previous = positions.get(index - 1);
            if (previous == null) {
                continue;
            }
            if (maximum == null || maximum.value() < previous.value()) {
                maximum = previous;
            }
            @Nullable SassNumber current = positions.get(index);
            if (current != null && current.value() < maximum.value()) {
                positions.set(index, maximum);
            }
        }

        @Nullable SassNumber preceding = null;
        var firstMissing = -1;
        for (var index = 0; index < positions.size(); index++) {
            @Nullable SassNumber current = positions.get(index);
            if (current == null) {
                if (firstMissing == -1) {
                    firstMissing = index;
                }
                continue;
            }
            if (firstMissing != -1) {
                if (preceding == null) {
                    throw new AssertionError("the first gradient stop must have an offset");
                }
                var missingCount = index - firstMissing;
                var value = preceding.value();
                var increment = (current.value() - value) / (missingCount + 1);
                while (firstMissing < index) {
                    value += increment;
                    positions.set(firstMissing++, sizeWithUnit(value, current));
                }
            }
            preceding = current;
        }

        var normalized = new ArrayList<GradientStop>(rawStops.size());
        for (var index = 0; index < rawStops.size(); index++) {
            @Nullable SassNumber offset = positions.get(index);
            if (offset == null) {
                throw new AssertionError("gradient stop normalization left an offset unset");
            }
            normalized.add(new GradientStop(
                    new RawGradientSize(offset),
                    rawStops.get(index).color()
            ));
        }
        return List.copyOf(normalized);
    }

    /// Creates a simple Sass size using another size's unit spelling.
    ///
    /// @param value    the new numeric value
    /// @param template the source size supplying the unit
    /// @return a size using the template unit
    private static SassNumber sizeWithUnit(double value, SassNumber template) {
        return SassNumber.of(value, template.isUnitless() ? null : template.unitString());
    }

    /// Returns whether an argument begins with one keyword.
    ///
    /// @param argument the complete comma-separated argument
    /// @param keyword  the expected keyword
    /// @param span     the source range associated with the declaration
    /// @return whether the first component equals the keyword ignoring case
    /// @throws BssSerializeException if the argument has malformed nesting
    private static boolean beginsWithKeyword(String argument, String keyword, SourceSpan span) {
        var components = splitComponents(argument, span);
        return !components.isEmpty() && equalsKeyword(components.get(0), keyword);
    }

    /// Compares one CSS keyword ignoring ASCII case.
    ///
    /// @param value   the candidate keyword
    /// @param keyword the expected keyword
    /// @return whether the words are equal ignoring case
    private static boolean equalsKeyword(String value, String keyword) {
        return value.equalsIgnoreCase(keyword);
    }

    /// Parses one complete CSS function invocation.
    ///
    /// @param text the raw unquoted function text
    /// @param span the source range associated with the declaration
    /// @return the function name and body
    /// @throws BssSerializeException if the text is not one complete function
    private static FunctionInvocation parseFunctionInvocation(String text, SourceSpan span) {
        var trimmed = text.trim();
        var opening = trimmed.indexOf('(');
        if (opening <= 0) {
            throw invalidPaint(span);
        }
        var name = trimmed.substring(0, opening).trim();
        if (name.isEmpty()) {
            throw invalidPaint(span);
        }
        var closing = matchingParenthesis(trimmed, opening, span);
        if (closing != trimmed.length() - 1) {
            throw invalidPaint(span);
        }
        return new FunctionInvocation(name, trimmed.substring(opening + 1, closing));
    }

    /// Returns the matching close parenthesis for one open parenthesis.
    ///
    /// @param text    the complete raw CSS text
    /// @param opening the offset of the opening parenthesis
    /// @param span    the source range associated with the declaration
    /// @return the matching close-parenthesis offset
    /// @throws BssSerializeException if quoting or nesting is malformed
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
                    throw invalidPaint(span);
                }
            }
        }
        throw invalidPaint(span);
    }
    /// Splits a function body on commas outside nested functions and strings.
    ///
    /// @param text the raw function body
    /// @param span the source range associated with the declaration
    /// @return immutable non-empty comma-separated arguments
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
                    throw invalidPaint(span);
                }
            } else if (character == ',' && depth == 0) {
                addTrimmedArgument(values, text.substring(start, index), span);
                start = index + 1;
            }
        }
        if (quote != '\0' || depth != 0) {
            throw invalidPaint(span);
        }
        addTrimmedArgument(values, text.substring(start), span);
        return List.copyOf(values);
    }

    /// Adds one non-empty comma-separated argument.
    ///
    /// @param values   the accumulating argument list
    /// @param candidate the raw argument text
    /// @param span     the source range associated with the declaration
    /// @throws BssSerializeException if the argument is blank
    private static void addTrimmedArgument(
            List<String> values,
            String candidate,
            SourceSpan span
    ) {
        var trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw invalidPaint(span);
        }
        values.add(trimmed);
    }

    /// Splits one grammar argument on top-level whitespace.
    ///
    /// @param text the complete argument
    /// @param span the source range associated with the declaration
    /// @return immutable non-empty components
    /// @throws BssSerializeException if quoting, nesting, or commas are malformed
    private static @Unmodifiable List<String> splitComponents(String text, SourceSpan span) {
        var components = new ArrayList<String>();
        var current = new StringBuilder();
        var depth = 0;
        var quote = '\0';
        var escaped = false;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quote != '\0') {
                current.append(character);
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
                current.append(character);
            } else if (character == '(') {
                depth++;
                current.append(character);
            } else if (character == ')') {
                depth--;
                if (depth < 0) {
                    throw invalidPaint(span);
                }
                current.append(character);
            } else if (character == ',' && depth == 0) {
                throw invalidPaint(span);
            } else if (depth == 0 && Character.isWhitespace(character)) {
                addComponent(components, current);
            } else {
                current.append(character);
            }
        }
        if (quote != '\0' || depth != 0) {
            throw invalidPaint(span);
        }
        addComponent(components, current);
        return List.copyOf(components);
    }

    /// Adds a non-empty top-level component.
    ///
    /// @param components the accumulating component list
    /// @param current    the current component builder
    private static void addComponent(List<String> components, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        components.add(current.toString());
        current.setLength(0);
    }
    /// Parses one CSS gradient color, preserving recursive JavaFX color forms.
    ///
    /// @param text the raw color text
    /// @param span the source range associated with the declaration
    /// @return a solid, lookup, derived, or ladder color
    /// @throws BssSerializeException if the color syntax is not supported
    private static ColorPaint parseGradientColor(String text, SourceSpan span) {
        return parseColorPaint(text, span);
    }

    /// Parses one JavaFX value whose runtime type is `Color`.
    ///
    /// @param text the raw color text
    /// @param span the source range associated with the declaration
    /// @return a solid, lookup, derived, or ladder color
    /// @throws BssSerializeException if the color syntax is unsupported
    static ColorPaint parseColorPaint(String text, SourceSpan span) {
        var trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw invalidPaint(span);
        }
        @Nullable SassColor named = SassColor.named(trimmed, span);
        if (named != null) {
            return new SolidPaint(named);
        }
        if (trimmed.startsWith("#")
                || trimmed.length() > 2
                && trimmed.charAt(0) == '0'
                && (trimmed.charAt(1) == 'x' || trimmed.charAt(1) == 'X')) {
            return new SolidPaint(parseColor(trimmed, span));
        }
        if (isLookupIdentifier(trimmed)) {
            return new LookupPaint(trimmed);
        }
        var function = parseFunctionInvocation(trimmed, span);
        @Nullable var functionKind = JavaFXValueFunction.fromName(function.name());
        if (functionKind == null) {
            throw invalidPaint(span);
        }
        return switch (functionKind) {
            case RGB, HSB -> new SolidPaint(parseColorFunction(function, span));
            case DERIVE -> parseDerivedColor(function.arguments(), span);
            case LADDER -> parseLadderColor(function.arguments(), span);
            default -> throw invalidPaint(span);
        };
    }

    /// Attempts to parse one concrete JavaFX color without accepting lookups.
    ///
    /// @param text the candidate color text
    /// @param span the source range associated with the declaration
    /// @return the parsed solid color, or {@code null} when the text is not a
    ///         concrete JavaFX color
    static @Nullable SolidPaint tryParseSolidColor(String text, SourceSpan span) {
        try {
            var color = parseColorPaint(text, span);
            return color instanceof SolidPaint solid ? solid : null;
        } catch (BssSerializeException ignored) {
            return null;
        }
    }

    /// Parses one concrete CSS color usable as a JavaFX gradient stop.
    ///
    /// @param text the raw color text
    /// @param span the source range associated with the declaration
    /// @return the parsed legacy RGB color
    /// @throws BssSerializeException if the color syntax is not supported
    private static SassColor parseColor(String text, SourceSpan span) {
        var trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw invalidPaint(span);
        }

        @Nullable SassColor named = SassColor.named(trimmed, span);
        if (named != null) {
            return named;
        }
        if (trimmed.startsWith("#")) {
            return parseHexColor(trimmed, 1, span);
        }
        if (trimmed.length() > 2
                && trimmed.charAt(0) == '0'
                && (trimmed.charAt(1) == 'x' || trimmed.charAt(1) == 'X')) {
            return parseHexColor(trimmed, 2, span);
        }
        if (trimmed.indexOf('(') > 0) {
            return parseColorFunction(trimmed, span);
        }
        throw invalidPaint(span);
    }

    /// Parses one hexadecimal CSS color.
    ///
    /// @param text        the complete source text
    /// @param prefixLength the number of prefix characters before the digits
    /// @param span        the source range associated with the declaration
    /// @return the decoded RGB color
    /// @throws BssSerializeException if the hexadecimal form is malformed
    private static SassColor parseHexColor(String text, int prefixLength, SourceSpan span) {
        var digits = text.substring(prefixLength);
        int red;
        int green;
        int blue;
        var alpha = 255;
        switch (digits.length()) {
            case 3 -> {
                red = hexDigit(digits.charAt(0), span) * 17;
                green = hexDigit(digits.charAt(1), span) * 17;
                blue = hexDigit(digits.charAt(2), span) * 17;
            }
            case 4 -> {
                red = hexDigit(digits.charAt(0), span) * 17;
                green = hexDigit(digits.charAt(1), span) * 17;
                blue = hexDigit(digits.charAt(2), span) * 17;
                alpha = hexDigit(digits.charAt(3), span) * 17;
            }
            case 6, 8 -> {
                red = hexByte(digits, 0, span);
                green = hexByte(digits, 2, span);
                blue = hexByte(digits, 4, span);
                if (digits.length() == 8) {
                    alpha = hexByte(digits, 6, span);
                }
            }
            default -> throw invalidPaint(span);
        }
        return SassColor.rgb(red, green, blue, alpha / 255.0, null);
    }

    /// Parses one pair of hexadecimal digits.
    ///
    /// @param text  the complete hexadecimal digit sequence
    /// @param index the offset of the first digit
    /// @param span  the source range associated with the declaration
    /// @return the byte represented by the two digits
    /// @throws BssSerializeException if either digit is invalid
    private static int hexByte(String text, int index, SourceSpan span) {
        return hexDigit(text.charAt(index), span) * 16 + hexDigit(text.charAt(index + 1), span);
    }

    /// Parses one hexadecimal digit.
    ///
    /// @param character the source character
    /// @param span      the source range associated with the declaration
    /// @return the digit value between zero and fifteen
    /// @throws BssSerializeException if the character is not hexadecimal
    private static int hexDigit(char character, SourceSpan span) {
        var digit = Character.digit(character, 16);
        if (digit < 0) {
            throw invalidPaint(span);
        }
        return digit;
    }

    /// Parses one functional CSS color.
    ///
    /// @param text the complete function text
    /// @param span the source range associated with the declaration
    /// @return the decoded RGB color
    /// @throws BssSerializeException if the function is not a supported color
    private static SassColor parseColorFunction(String text, SourceSpan span) {
        return parseColorFunction(parseFunctionInvocation(text, span), span);
    }

    /// Parses one classified functional JavaFX color.
    ///
    /// @param function the parsed function invocation
    /// @param span     the source range associated with the declaration
    /// @return the decoded RGB color
    /// @throws BssSerializeException if the function is not a supported color
    private static SassColor parseColorFunction(
            FunctionInvocation function,
            SourceSpan span
    ) {
        var components = colorFunctionComponents(function.arguments(), span);
        @Nullable var functionKind = JavaFXValueFunction.fromName(function.name());
        if (functionKind == null) {
            throw invalidPaint(span);
        }
        return switch (functionKind) {
            case RGB -> parseRgbColor(components, span);
            case HSB -> parseHsbColor(components, span);
            default -> throw invalidPaint(span);
        };
    }

    /// Returns normalized positional components of a functional CSS color.
    ///
    /// @param arguments the function body without its parentheses
    /// @param span      the source range associated with the declaration
    /// @return the three required components and optional alpha component
    /// @throws BssSerializeException if comma and slash syntax is malformed
    private static @Unmodifiable List<String> colorFunctionComponents(
            String arguments,
            SourceSpan span
    ) {
        var commaSeparated = splitTopLevelCommas(arguments, span);
        if (commaSeparated.size() > 1) {
            return commaSeparated;
        }

        var components = new ArrayList<>(splitComponents(commaSeparated.get(0), span));
        var slashIndex = components.indexOf("/");
        if (slashIndex != -1) {
            if (slashIndex != 3 || components.size() != 5) {
                throw invalidPaint(span);
            }
            components.remove(slashIndex);
        }
        return List.copyOf(components);
    }

    /// Parses an {@code rgb(...)} or {@code rgba(...)} color.
    ///
    /// @param components the three RGB channels and optional alpha component
    /// @param span       the source range associated with the declaration
    /// @return the decoded RGB color
    /// @throws BssSerializeException if a channel is invalid
    private static SassColor parseRgbColor(List<String> components, SourceSpan span) {
        if (components.size() != 3 && components.size() != 4) {
            throw invalidPaint(span);
        }
        var alpha = components.size() == 4 ? parseAlpha(components.get(3), span) : 1.0;
        return SassColor.rgb(
                parseRgbChannel(components.get(0), span),
                parseRgbChannel(components.get(1), span),
                parseRgbChannel(components.get(2), span),
                alpha,
                null
        );
    }

    /// Parses JavaFX's {@code hsb(...)} or {@code hsba(...)} color.
    ///
    /// @param components the hue, saturation, brightness, and optional alpha
    /// @param span       the source range associated with the declaration
    /// @return the decoded RGB color
    /// @throws BssSerializeException if a component has the wrong unit or arity
    private static SassColor parseHsbColor(List<String> components, SourceSpan span) {
        if (components.size() != 3 && components.size() != 4) {
            throw invalidPaint(span);
        }
        var hue = parseUnitlessNumber(components.get(0), span);
        var saturation = parseClampedPercentage(components.get(1), span);
        var brightness = parseClampedPercentage(components.get(2), span);
        var alpha = components.size() == 4
                ? clampUnitInterval(parseUnitlessNumber(components.get(3), span))
                : 1.0;

        var normalizedHue = ((hue % 360.0) + 360.0) % 360.0 / 360.0;
        double red;
        double green;
        double blue;
        if (saturation == 0.0) {
            red = brightness;
            green = brightness;
            blue = brightness;
        } else {
            var sectorValue = (normalizedHue - Math.floor(normalizedHue)) * 6.0;
            var fraction = sectorValue - Math.floor(sectorValue);
            var minimum = brightness * (1.0 - saturation);
            var descending = brightness * (1.0 - saturation * fraction);
            var ascending = brightness * (1.0 - saturation * (1.0 - fraction));
            switch ((int) sectorValue) {
                case 0 -> {
                    red = brightness;
                    green = ascending;
                    blue = minimum;
                }
                case 1 -> {
                    red = descending;
                    green = brightness;
                    blue = minimum;
                }
                case 2 -> {
                    red = minimum;
                    green = brightness;
                    blue = ascending;
                }
                case 3 -> {
                    red = minimum;
                    green = descending;
                    blue = brightness;
                }
                case 4 -> {
                    red = ascending;
                    green = minimum;
                    blue = brightness;
                }
                case 5 -> {
                    red = brightness;
                    green = minimum;
                    blue = descending;
                }
                default -> throw new AssertionError("normalized hue produced an invalid sector");
            }
        }
        return SassColor.rgb(red * 255.0, green * 255.0, blue * 255.0, alpha, null);
    }

    /// Parses one finite unitless number.
    ///
    /// @param text the raw numeric token
    /// @param span the source range associated with the declaration
    /// @return the numeric value
    /// @throws BssSerializeException if the token has a unit
    private static double parseUnitlessNumber(String text, SourceSpan span) {
        var number = parseSize(text, span);
        if (!number.isUnitless()) {
            throw invalidPaint(span);
        }
        return number.value();
    }

    /// Parses and clamps one JavaFX HSB percentage.
    ///
    /// @param text the raw percentage token
    /// @param span the source range associated with the declaration
    /// @return the normalized value in the unit interval
    /// @throws BssSerializeException if the token is not a percentage
    private static double parseClampedPercentage(String text, SourceSpan span) {
        var number = parseSize(text, span);
        if (number.numeratorUnits().size() != 1
                || !number.numeratorUnits().get(0).equals("%")
                || !number.denominatorUnits().isEmpty()) {
            throw invalidPaint(span);
        }
        return clampUnitInterval(number.value() / 100.0);
    }

    /// Clamps one finite number to the closed unit interval.
    ///
    /// @param value the finite input value
    /// @return `0`, `1`, or the unchanged in-range value
    private static double clampUnitInterval(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /// Parses one RGB color channel.
    ///
    /// @param text the raw channel text
    /// @param span the source range associated with the declaration
    /// @return the channel between zero and 255
    /// @throws BssSerializeException if the unit or range is invalid
    private static double parseRgbChannel(String text, SourceSpan span) {
        var size = parseSize(text, span);
        var channel = switch (sizeUnit(size)) {
            case "" -> size.value();
            case "%" -> size.value() * 255.0 / 100.0;
            default -> throw invalidPaint(span);
        };
        if (channel < 0.0 || channel > 255.0) {
            throw invalidPaint(span);
        }
        return channel;
    }

    /// Parses one CSS alpha component.
    ///
    /// @param text the raw alpha text
    /// @param span the source range associated with the declaration
    /// @return the alpha value between zero and one
    /// @throws BssSerializeException if the unit or range is invalid
    private static double parseAlpha(String text, SourceSpan span) {
        var size = parseSize(text, span);
        var alpha = switch (sizeUnit(size)) {
            case "" -> size.value();
            case "%" -> size.value() / 100.0;
            default -> throw invalidPaint(span);
        };
        if (alpha < 0.0 || alpha > 1.0) {
            throw invalidPaint(span);
        }
        return alpha;
    }

    /// Parses one simple CSS size token.
    ///
    /// @param text the raw size text
    /// @param span the source range associated with the declaration
    /// @return a finite unitless or one-unit Sass number
    /// @throws BssSerializeException if the token is not a finite CSS number
    static SassNumber parseSize(String text, SourceSpan span) {
        @Nullable SassNumber size = tryParseSize(text);
        if (size == null) {
            throw invalidPaint(span);
        }
        return size;
    }

    /// Parses one simple CSS size without reporting a syntax failure.
    ///
    /// @param text the raw size text
    /// @return the parsed size, or {@code null} when the text is not finite size syntax
    static @Nullable SassNumber tryParseSize(String text) {
        Matcher matcher = SIZE_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        final double value;
        try {
            value = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (!Double.isFinite(value)) {
            return null;
        }
        @Nullable String unit = matcher.group(2);
        return SassNumber.of(value, unit);
    }

    /// Returns whether one token has complete direct CSS string syntax.
    ///
    /// @param text the candidate token
    /// @return whether the token begins and ends with the same quote character
    private static boolean isQuotedCssString(String text) {
        if (text.length() < 2) {
            return false;
        }
        var quote = text.charAt(0);
        return (quote == '\'' || quote == '"') && text.charAt(text.length() - 1) == quote;
    }

    /// Returns whether one token is an identifier usable for a JavaFX lookup.
    ///
    /// @param text the candidate token
    /// @return whether the token uses the supported CSS identifier subset
    static boolean isLookupIdentifier(String text) {
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

    /// Returns the lower-case simple-unit spelling for one parsed size.
    ///
    /// @param size the parsed simple size
    /// @return the empty string for a unitless size, otherwise its unit
    private static String sizeUnit(SassNumber size) {
        if (!size.denominatorUnits().isEmpty() || size.numeratorUnits().size() > 1) {
            throw new AssertionError("gradient parser sizes must have at most one numerator unit");
        }
        if (size.isUnitless()) {
            return "";
        }
        return size.numeratorUnits().get(0).toLowerCase(Locale.ROOT);
    }

    /// Creates the standard unsupported-paint failure.
    ///
    /// @param span the source range associated with the declaration
    /// @return the serialization failure
    private static BssSerializeException invalidPaint(SourceSpan span) {
        return new BssSerializeException(
                "BSS paint values require solid, derived, or ladder colors,"
                        + " property lookups, JavaFX gradients, image patterns,"
                        + " or region references.",
                span,
                null
        );
    }

    /// Contains one point from a legacy JavaFX gradient.
    ///
    /// @param x the horizontal coordinate
    /// @param y the vertical coordinate
    @NotNullByDefault
    private record LegacyPoint(GradientSize x, GradientSize y) {
        /// Creates a validated legacy point.
        private LegacyPoint {
            x = Objects.requireNonNull(x, "x");
            y = Objects.requireNonNull(y, "y");
        }
    }

    /// Scans the token sequence used by deprecated JavaFX gradient syntax.
    @NotNullByDefault
    private static final class LegacyGradientCursor {
        /// Contains the complete legacy paint text.
        private final String text;

        /// Contains the source span used for syntax failures.
        private final SourceSpan span;

        /// Contains the next unconsumed UTF-16 offset.
        private int index;

        /// Creates a cursor at the beginning of a legacy paint.
        ///
        /// @param text the complete paint text
        /// @param span the source range associated with the declaration
        private LegacyGradientCursor(String text, SourceSpan span) {
            this.text = Objects.requireNonNull(text, "text");
            this.span = Objects.requireNonNull(span, "span");
        }

        /// Consumes one case-insensitive standalone keyword when present.
        ///
        /// @param keyword the expected keyword
        /// @return whether the keyword was consumed
        private boolean consumeKeyword(String keyword) {
            Objects.requireNonNull(keyword, "keyword");
            skipWhitespace();
            if (!text.regionMatches(
                    true,
                    index,
                    keyword,
                    0,
                    keyword.length()
            )) {
                return false;
            }
            var end = index + keyword.length();
            if (end < text.length()
                    && !Character.isWhitespace(text.charAt(end))
                    && text.charAt(end) != '(') {
                return false;
            }
            index = end;
            return true;
        }

        /// Requires and consumes one case-insensitive standalone keyword.
        ///
        /// @param keyword the required keyword
        /// @throws BssSerializeException if the keyword is absent
        private void requireKeyword(String keyword) {
            if (!consumeKeyword(keyword)) {
                throw invalidPaint(span);
            }
        }

        /// Returns and consumes one non-whitespace token.
        ///
        /// @return the token spelling
        /// @throws BssSerializeException if no ordinary token is present
        private String token() {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) == '(') {
                throw invalidPaint(span);
            }
            var start = index;
            while (index < text.length()
                    && !Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            return text.substring(start, index);
        }

        /// Returns text through the next standalone `stops` keyword and consumes it.
        ///
        /// Parenthesized functions and quoted strings are scanned as part of
        /// the preceding value, so their contents cannot terminate the scan.
        ///
        /// @return the non-empty text before the keyword
        /// @throws BssSerializeException if the keyword is absent or the value
        ///                               is empty or unbalanced
        private String valueBeforeStopsKeyword() {
            skipWhitespace();
            var start = index;
            var depth = 0;
            var quote = '\0';
            var escaped = false;
            while (index < text.length()) {
                var character = text.charAt(index);
                if (quote != '\0') {
                    index++;
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
                    index++;
                    continue;
                }
                if (character == '(') {
                    depth++;
                    index++;
                    continue;
                }
                if (character == ')') {
                    if (depth == 0) {
                        throw invalidPaint(span);
                    }
                    depth--;
                    index++;
                    continue;
                }
                if (depth == 0
                        && text.regionMatches(
                                true,
                                index,
                                "stops",
                                0,
                                "stops".length()
                        )
                        && index > start
                        && Character.isWhitespace(text.charAt(index - 1))) {
                    var end = index + "stops".length();
                    if (end == text.length()
                            || Character.isWhitespace(text.charAt(end))
                            || text.charAt(end) == '(') {
                        var value = text.substring(start, index).trim();
                        if (value.isEmpty()) {
                            throw invalidPaint(span);
                        }
                        index = end;
                        return value;
                    }
                }
                index++;
            }
            throw invalidPaint(span);
        }

        /// Returns and consumes one balanced parenthesized value.
        ///
        /// Nested functions and quoted strings remain part of the returned
        /// body.
        ///
        /// @return the text between the outer parentheses
        /// @throws BssSerializeException if the value is absent or unbalanced
        private String parenthesized() {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != '(') {
                throw invalidPaint(span);
            }
            var start = ++index;
            var depth = 1;
            var quote = '\0';
            var escaped = false;
            while (index < text.length()) {
                var character = text.charAt(index++);
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
                } else if (character == ')' && --depth == 0) {
                    return text.substring(start, index - 1);
                }
            }
            throw invalidPaint(span);
        }

        /// Returns whether the next non-whitespace token is parenthesized.
        ///
        /// @return whether another legacy point or stop follows
        private boolean nextIsParenthesized() {
            skipWhitespace();
            return index < text.length() && text.charAt(index) == '(';
        }

        /// Returns whether any non-whitespace text remains.
        ///
        /// @return whether another token follows
        private boolean hasMore() {
            skipWhitespace();
            return index < text.length();
        }

        /// Requires that no non-whitespace text remains.
        ///
        /// @throws BssSerializeException if another token follows
        private void requireEnd() {
            if (hasMore()) {
                throw invalidPaint(span);
            }
        }

        /// Advances past whitespace at the current position.
        private void skipWhitespace() {
            while (index < text.length()
                    && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
    }

    /// Represents one paint that can be serialized by the BSS paint converters.
    @NotNullByDefault
    sealed interface Paint permits ColorPaint, LinearGradientPaint, RadialGradientPaint,
            ImagePatternPaint, RepeatingImagePatternPaint, RegionReferencePaint {
    }

    /// Represents one paint whose JavaFX runtime type is `Color`.
    @NotNullByDefault
    sealed interface ColorPaint extends Paint permits
            SolidPaint,
            LookupPaint,
            DerivedPaint,
            LadderPaint {
    }

    /// Represents one solid JavaFX color paint.
    ///
    /// @param color the legacy RGB color
    @NotNullByDefault
    record SolidPaint(SassColor color) implements ColorPaint {
        /// Creates an immutable solid color paint.
        SolidPaint {
            color = Objects.requireNonNull(color, "color");
        }
    }

    /// Represents one JavaFX property lookup used as a paint.
    ///
    /// @param key the unquoted property key
    @NotNullByDefault
    record LookupPaint(String key) implements ColorPaint {
        /// Creates an immutable lookup paint.
        LookupPaint {
            key = requireNonEmpty(key, "key");
        }
    }

    /// Represents one JavaFX `derive(...)` color.
    ///
    /// @param base       the input color
    /// @param brightness the brightness adjustment
    @NotNullByDefault
    record DerivedPaint(ColorPaint base, SassNumber brightness)
            implements ColorPaint {
        /// Creates an immutable derived color.
        DerivedPaint {
            base = Objects.requireNonNull(base, "base");
            brightness = Objects.requireNonNull(brightness, "brightness");
        }
    }

    /// Represents one JavaFX `ladder(...)` color.
    ///
    /// @param base  the input color
    /// @param stops the normalized ladder stops
    @NotNullByDefault
    record LadderPaint(
            ColorPaint base,
            @Unmodifiable List<GradientStop> stops
    ) implements ColorPaint {
        /// Creates an immutable ladder color.
        LadderPaint {
            base = Objects.requireNonNull(base, "base");
            stops = List.copyOf(stops);
            if (stops.isEmpty()) {
                throw new IllegalArgumentException("a ladder requires at least one stop");
            }
        }
    }

    /// Represents one JavaFX linear gradient paint.
    ///
    /// @param startX      the start x coordinate
    /// @param startY      the start y coordinate
    /// @param endX        the end x coordinate
    /// @param endY        the end y coordinate
    /// @param cycleMethod the JavaFX {@code CycleMethod} enum spelling
    /// @param stops       the normalized color stops
    /// @param legacySyntax whether the deprecated token-series grammar was used
    @NotNullByDefault
    record LinearGradientPaint(
            GradientSize startX,
            GradientSize startY,
            GradientSize endX,
            GradientSize endY,
            String cycleMethod,
            @Unmodifiable List<GradientStop> stops,
            boolean legacySyntax
    ) implements Paint {
        /// Creates an immutable linear gradient paint.
        LinearGradientPaint {
            startX = Objects.requireNonNull(startX, "startX");
            startY = Objects.requireNonNull(startY, "startY");
            endX = Objects.requireNonNull(endX, "endX");
            endY = Objects.requireNonNull(endY, "endY");
            cycleMethod = Objects.requireNonNull(cycleMethod, "cycleMethod");
            stops = List.copyOf(stops);
        }
    }

    /// Represents one JavaFX radial gradient paint.
    ///
    /// @param focusAngle    the optional focus angle
    /// @param focusDistance the optional focus distance
    /// @param centerX       the optional center x coordinate
    /// @param centerY       the optional center y coordinate
    /// @param radius        the required radius
    /// @param cycleMethod   the JavaFX {@code CycleMethod} enum spelling
    /// @param stops         the normalized color stops
    /// @param legacySyntax  whether the deprecated token-series grammar was used
    @NotNullByDefault
    record RadialGradientPaint(
            @Nullable GradientSize focusAngle,
            @Nullable GradientSize focusDistance,
            @Nullable GradientSize centerX,
            @Nullable GradientSize centerY,
            GradientSize radius,
            String cycleMethod,
            @Unmodifiable List<GradientStop> stops,
            boolean legacySyntax
    ) implements Paint {
        /// Creates an immutable radial gradient paint.
        RadialGradientPaint {
            radius = Objects.requireNonNull(radius, "radius");
            cycleMethod = Objects.requireNonNull(cycleMethod, "cycleMethod");
            stops = List.copyOf(stops);
        }
    }

    /// Represents one JavaFX image-pattern paint.
    ///
    /// @param resource     the URI token text stored by JavaFX
    /// @param x            the optional x coordinate
    /// @param y            the optional y coordinate
    /// @param width        the optional pattern width
    /// @param height       the optional pattern height
    /// @param proportional the optional explicit proportional flag
    @NotNullByDefault
    record ImagePatternPaint(
            String resource,
            @Nullable ImagePatternSize x,
            @Nullable ImagePatternSize y,
            @Nullable ImagePatternSize width,
            @Nullable ImagePatternSize height,
            @Nullable Boolean proportional
    ) implements Paint {
        /// Creates an immutable image-pattern paint.
        ImagePatternPaint {
            resource = requireNonEmpty(resource, "resource");
            var suppliedSizeCount = (x == null ? 0 : 1)
                    + (y == null ? 0 : 1)
                    + (width == null ? 0 : 1)
                    + (height == null ? 0 : 1);
            if (suppliedSizeCount != 0 && suppliedSizeCount != 4) {
                throw new IllegalArgumentException("image pattern geometry must be all present or all absent");
            }
            if (suppliedSizeCount == 0 && proportional != null) {
                throw new IllegalArgumentException("an image pattern flag requires geometry");
            }
        }

        /// Returns whether this pattern includes explicit geometry values.
        ///
        /// @return whether x, y, width, and height are all present
        boolean hasGeometry() {
            return x != null;
        }
    }

    /// Represents one JavaFX repeating-image-pattern paint.
    ///
    /// @param resource the URI token text stored by JavaFX
    @NotNullByDefault
    record RepeatingImagePatternPaint(String resource) implements Paint {
        /// Creates an immutable repeating image-pattern paint.
        RepeatingImagePatternPaint {
            resource = requireNonEmpty(resource, "resource");
        }
    }

    /// Represents one JavaFX `region("selector")` paint reference.
    ///
    /// @param value the prefixed selector reference stored by JavaFX
    @NotNullByDefault
    record RegionReferencePaint(String value) implements Paint {
        /// Creates an immutable region reference.
        RegionReferencePaint {
            value = requireNonEmpty(value, "value");
        }
    }

    /// Represents one JavaFX image-pattern size value.
    @NotNullByDefault
    sealed interface ImagePatternSize permits RawImagePatternSize, LookupImagePatternSize {
    }

    /// Represents one concrete JavaFX image-pattern size.
    ///
    /// @param size the raw JavaFX size
    @NotNullByDefault
    record RawImagePatternSize(SassNumber size) implements ImagePatternSize {
        /// Creates an immutable raw image-pattern size.
        RawImagePatternSize {
            size = Objects.requireNonNull(size, "size");
        }
    }

    /// Represents one JavaFX property lookup used as an image-pattern size.
    ///
    /// @param key the unquoted property key
    @NotNullByDefault
    record LookupImagePatternSize(String key) implements ImagePatternSize {
        /// Creates an immutable lookup image-pattern size.
        LookupImagePatternSize {
            key = requireNonEmpty(key, "key");
        }
    }

    /// Represents one JavaFX gradient size value.
    @NotNullByDefault
    sealed interface GradientSize permits RawGradientSize, LookupGradientSize {
    }

    /// Represents one concrete JavaFX gradient size.
    ///
    /// @param size the raw JavaFX size
    @NotNullByDefault
    record RawGradientSize(SassNumber size) implements GradientSize {
        /// Creates an immutable raw gradient size.
        RawGradientSize {
            size = Objects.requireNonNull(size, "size");
        }
    }

    /// Represents one JavaFX property lookup used as a gradient size.
    ///
    /// @param key the unquoted property key
    @NotNullByDefault
    record LookupGradientSize(String key) implements GradientSize {
        /// Creates an immutable lookup gradient size.
        LookupGradientSize {
            key = requireNonEmpty(key, "key");
        }
    }

    /// Represents one normalized gradient color stop.
    ///
    /// @param offset the normalized stop offset
    /// @param color  the stop color
    @NotNullByDefault
    record GradientStop(GradientSize offset, ColorPaint color) {
        /// Creates an immutable gradient color stop.
        GradientStop {
            offset = Objects.requireNonNull(offset, "offset");
            color = Objects.requireNonNull(color, "color");
        }
    }

    /// Represents one source-order color stop before offset normalization.
    ///
    /// @param color  the stop color
    /// @param offset the optional source offset
    @NotNullByDefault
    private record RawGradientStop(ColorPaint color, @Nullable SassNumber offset) {
        /// Creates an immutable raw gradient color stop.
        RawGradientStop {
            color = Objects.requireNonNull(color, "color");
        }
    }

    /// Represents a four-coordinate linear-gradient direction.
    ///
    /// @param startX the start x coordinate
    /// @param startY the start y coordinate
    /// @param endX   the end x coordinate
    /// @param endY   the end y coordinate
    @NotNullByDefault
    private record LinearDirection(
            SassNumber startX,
            SassNumber startY,
            SassNumber endX,
            SassNumber endY
    ) {
        /// Creates an immutable linear-gradient direction.
        LinearDirection {
            startX = Objects.requireNonNull(startX, "startX");
            startY = Objects.requireNonNull(startY, "startY");
            endX = Objects.requireNonNull(endX, "endX");
            endY = Objects.requireNonNull(endY, "endY");
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
}
