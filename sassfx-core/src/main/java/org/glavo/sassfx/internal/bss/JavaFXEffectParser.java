// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.bss;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.css.JavaFXValueFunction;
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
import java.util.function.Predicate;

/// Parses JavaFX shadow effect functions without loading JavaFX classes.
///
/// OpenJFX represents both supported shadow functions as six nested parsed
/// values: blur type, color, radius, spread or choke, x offset, and y offset.
@NotNullByDefault
final class JavaFXEffectParser {
    /// Prevents instantiation.
    private JavaFXEffectParser() {
    }

    /// Returns whether a value begins with a supported JavaFX effect function.
    ///
    /// @param value the evaluated Sass value
    /// @return whether the value begins with `dropshadow(` or `innershadow(`
    static boolean isEffectFunction(SassValue value) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            return false;
        }
        var text = string.text().stripLeading();
        var parenthesis = text.indexOf('(');
        if (parenthesis <= 0) {
            return false;
        }
        return EffectKind.forFunctionName(text.substring(0, parenthesis).trim()) != null;
    }

    /// Parses one complete JavaFX shadow effect.
    ///
    /// @param value            the evaluated Sass value
    /// @param span             the source range associated with the declaration
    /// @param registeredLookup tests whether a color identifier names a
    ///                         declaration already registered by JavaFX
    /// @return the normalized shadow effect
    /// @throws BssSerializeException if the value does not follow the JavaFX grammar
    static ShadowEffect parse(
            SassValue value,
            SourceSpan span,
            Predicate<String> registeredLookup
    ) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(registeredLookup, "registeredLookup");
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            throw invalidEffect(span);
        }
        var invocation = parseInvocation(string.text(), span);
        var kind = EffectKind.forFunctionName(invocation.name());
        if (kind == null) {
            throw invalidEffect(span);
        }
        var arguments = splitTopLevelCommas(invocation.arguments(), span);
        if (arguments.size() != 6) {
            throw invalidEffect(span);
        }
        return new ShadowEffect(
                kind,
                parseBlurType(arguments.get(0), span),
                JavaFXPaintParser.parseColorPaint(
                        arguments.get(1),
                        span,
                        registeredLookup
                ),
                parseSize(arguments.get(2), span),
                parseSize(arguments.get(3), span),
                parseSize(arguments.get(4), span),
                parseSize(arguments.get(5), span)
        );
    }

    /// Parses one raw or property-lookup-backed JavaFX effect size.
    ///
    /// @param text the raw size token
    /// @param span the source range associated with the declaration
    /// @return the normalized size representation
    /// @throws BssSerializeException if the token is neither a size nor a lookup
    private static EffectSize parseSize(String text, SourceSpan span) {
        @Nullable SassNumber number = JavaFXPaintParser.tryParseSize(text);
        if (number != null) {
            if (!isSupportedEffectSize(number)) {
                throw invalidEffect(span);
            }
            return new RawEffectSize(number);
        }
        var lookup = text.trim();
        if (JavaFXPaintParser.isLookupIdentifier(lookup)) {
            return new LookupEffectSize(lookup);
        }
        throw invalidEffect(span);
    }

    /// Returns whether a number uses JavaFX's effect-size unit set.
    ///
    /// @param number the parsed finite number
    /// @return whether OpenJFX accepts the number as an effect size
    private static boolean isSupportedEffectSize(SassNumber number) {
        if (!number.denominatorUnits().isEmpty()
                || number.numeratorUnits().size() > 1) {
            return false;
        }
        if (number.numeratorUnits().isEmpty()) {
            return true;
        }
        return switch (number.numeratorUnits().get(0).toLowerCase(Locale.ROOT)) {
            case "%", "em", "ex", "px", "cm", "mm", "in", "pt", "pc",
                 "deg", "grad", "rad", "turn" -> true;
            default -> false;
        };
    }

    /// Parses and normalizes one JavaFX blur-type identifier.
    ///
    /// @param text the raw blur-type token
    /// @param span the source range associated with the declaration
    /// @return the JavaFX `BlurType` enum constant spelling
    /// @throws BssSerializeException if the identifier is not supported
    private static String parseBlurType(String text, SourceSpan span) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "gaussian" -> "GAUSSIAN";
            case "one-pass-box" -> "ONE_PASS_BOX";
            case "two-pass-box" -> "TWO_PASS_BOX";
            case "three-pass-box" -> "THREE_PASS_BOX";
            default -> throw invalidEffect(span);
        };
    }

    /// Parses one complete function invocation.
    ///
    /// @param text the raw unquoted function text
    /// @param span the source range associated with the declaration
    /// @return the function name and body
    /// @throws BssSerializeException if the text is not one complete function
    private static FunctionInvocation parseInvocation(String text, SourceSpan span) {
        var trimmed = text.trim();
        var opening = trimmed.indexOf('(');
        if (opening <= 0) {
            throw invalidEffect(span);
        }
        var name = trimmed.substring(0, opening).trim();
        var closing = matchingParenthesis(trimmed, opening, span);
        if (name.isEmpty() || closing != trimmed.length() - 1) {
            throw invalidEffect(span);
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
                    throw invalidEffect(span);
                }
            }
        }
        throw invalidEffect(span);
    }

    /// Splits a function body on commas outside nested functions and strings.
    ///
    /// @param text the raw function body
    /// @param span the source range associated with the declaration
    /// @return immutable non-empty comma-separated arguments
    /// @throws BssSerializeException if quoting, nesting, or arguments are malformed
    private static @Unmodifiable List<String> splitTopLevelCommas(
            String text,
            SourceSpan span
    ) {
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
                    throw invalidEffect(span);
                }
            } else if (character == ',' && depth == 0) {
                addArgument(values, text.substring(start, index), span);
                start = index + 1;
            }
        }
        if (quote != '\0' || depth != 0) {
            throw invalidEffect(span);
        }
        addArgument(values, text.substring(start), span);
        return List.copyOf(values);
    }

    /// Appends one trimmed non-empty function argument.
    ///
    /// @param values the accumulating argument list
    /// @param text   the raw argument text
    /// @param span   the source range associated with the declaration
    /// @throws BssSerializeException if the argument is empty
    private static void addArgument(
            List<String> values,
            String text,
            SourceSpan span
    ) {
        var trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw invalidEffect(span);
        }
        values.add(trimmed);
    }

    /// Creates the standard failure for an unsupported JavaFX effect value.
    ///
    /// @param span the source range associated with the declaration
    /// @return the source-associated failure
    private static BssSerializeException invalidEffect(SourceSpan span) {
        return new BssSerializeException(
                "BSS effects require dropshadow() or innershadow() with a blur"
                        + " type, color, radius, spread or choke, and two offsets.",
                span,
                null
        );
    }

    /// Identifies the converter used for a JavaFX shadow function.
    @NotNullByDefault
    enum EffectKind {
        /// Selects `EffectConverter.DropShadowConverter`.
        DROP_SHADOW(
                JavaFXValueFunction.DROP_SHADOW,
                "javafx.css.converter.EffectConverter$DropShadowConverter"
        ),

        /// Selects `EffectConverter.InnerShadowConverter`.
        INNER_SHADOW(
                JavaFXValueFunction.INNER_SHADOW,
                "javafx.css.converter.EffectConverter$InnerShadowConverter"
        );

        /// Contains the OpenJFX function classifier.
        private final JavaFXValueFunction function;

        /// Contains the JavaFX converter class name.
        private final String converterClass;

        /// Creates one effect kind.
        ///
        /// @param function      the OpenJFX function classifier
        /// @param converterClass the JavaFX converter class name
        EffectKind(JavaFXValueFunction function, String converterClass) {
            this.function = Objects.requireNonNull(function, "function");
            this.converterClass = Objects.requireNonNull(converterClass, "converterClass");
        }

        /// Returns the JavaFX converter class name.
        ///
        /// @return the converter class name
        String converterClass() {
            return converterClass;
        }

        /// Returns the kind for a CSS function name.
        ///
        /// @param name the candidate function name
        /// @return the matching kind, or `null` when unsupported
        private static @Nullable EffectKind forFunctionName(
                String name
        ) {
            @Nullable var function = JavaFXValueFunction.fromName(name);
            for (var kind : values()) {
                if (kind.function == function) {
                    return kind;
                }
            }
            return null;
        }
    }

    /// Holds one normalized JavaFX shadow effect.
    ///
    /// @param kind       the shadow converter kind
    /// @param blurType   the JavaFX `BlurType` enum constant
    /// @param color      the shadow color
    /// @param radius     the blur-kernel radius
    /// @param spreadOrChoke the drop-shadow spread or inner-shadow choke
    /// @param offsetX    the horizontal offset
    /// @param offsetY    the vertical offset
    @NotNullByDefault
    record ShadowEffect(
            EffectKind kind,
            String blurType,
            JavaFXPaintParser.ColorPaint color,
            EffectSize radius,
            EffectSize spreadOrChoke,
            EffectSize offsetX,
            EffectSize offsetY
    ) {
        /// Creates one immutable shadow effect.
        ShadowEffect {
            kind = Objects.requireNonNull(kind, "kind");
            blurType = Objects.requireNonNull(blurType, "blurType");
            color = Objects.requireNonNull(color, "color");
            radius = Objects.requireNonNull(radius, "radius");
            spreadOrChoke = Objects.requireNonNull(spreadOrChoke, "spreadOrChoke");
            offsetX = Objects.requireNonNull(offsetX, "offsetX");
            offsetY = Objects.requireNonNull(offsetY, "offsetY");
        }
    }

    /// Represents one raw or property-lookup-backed effect size.
    @NotNullByDefault
    sealed interface EffectSize permits RawEffectSize, LookupEffectSize {
    }

    /// Holds one concrete JavaFX effect size.
    ///
    /// @param value the finite size value
    @NotNullByDefault
    record RawEffectSize(SassNumber value) implements EffectSize {
        /// Creates one immutable raw effect size.
        RawEffectSize {
            value = Objects.requireNonNull(value, "value");
        }
    }

    /// Holds one JavaFX property lookup used as an effect size.
    ///
    /// @param key the property lookup key
    @NotNullByDefault
    record LookupEffectSize(String key) implements EffectSize {
        /// Creates one immutable effect-size lookup.
        LookupEffectSize {
            key = Objects.requireNonNull(key, "key");
            if (key.isEmpty()) {
                throw new IllegalArgumentException("an effect-size lookup key must not be empty");
            }
        }
    }

    /// Holds one parsed CSS function invocation.
    ///
    /// @param name      the function name
    /// @param arguments the body without parentheses
    @NotNullByDefault
    private record FunctionInvocation(String name, String arguments) {
        /// Creates one immutable invocation.
        private FunctionInvocation {
            name = Objects.requireNonNull(name, "name");
            arguments = Objects.requireNonNull(arguments, "arguments");
        }
    }
}
