// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.glavo.sassfx.internal.value.CalculationOperation;
import org.glavo.sassfx.internal.value.CalculationOperator;
import org.glavo.sassfx.internal.value.SassArgumentList;
import org.glavo.sassfx.internal.value.SassBoolean;
import org.glavo.sassfx.internal.value.SassCalculation;
import org.glavo.sassfx.internal.value.SassColor;
import org.glavo.sassfx.internal.value.SassFunction;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassMap;
import org.glavo.sassfx.internal.value.SassMixin;
import org.glavo.sassfx.internal.value.SassNull;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassString;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Provides an immutable public view of any SassScript value.
///
/// Values passed to a [SassCustomFunction] retain their complete Sass identity,
/// including opaque color, calculation, function, and mixin values. Common
/// scalar, collection, color, calculation, and callback-bound function values
/// can also be constructed using this class's factories. A value may be
/// returned directly from a callback without conversion.
@NotNullByDefault
public final class SassValue {
    /// The public wrapper for Sass null.
    private static final SassValue NULL = new SassValue(SassNull.NULL);

    /// Contains the evaluator value represented by this public view.
    private final org.glavo.sassfx.internal.value.SassValue value;

    /// Creates a public wrapper around one evaluator value.
    private SassValue(org.glavo.sassfx.internal.value.SassValue value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /// Returns the singleton Sass null value.
    ///
    /// @return Sass null
    public static SassValue nullValue() {
        return NULL;
    }

    /// Creates a Sass boolean.
    ///
    /// @param value the represented Java boolean
    /// @return the Sass boolean value
    public static SassValue booleanValue(boolean value) {
        return new SassValue(SassBoolean.of(value));
    }

    /// Creates a unitless Sass number.
    ///
    /// @param value the numeric magnitude
    /// @return the Sass number
    public static SassValue number(double value) {
        return new SassValue(SassNumber.of(value, null));
    }

    /// Creates a Sass number with at most one numerator unit.
    ///
    /// @param value the numeric magnitude
    /// @param unit the numerator unit
    /// @return the Sass number
    /// @throws IllegalArgumentException if {@code unit} is empty
    public static SassValue number(double value, String unit) {
        return new SassValue(SassNumber.of(value, Objects.requireNonNull(unit, "unit")));
    }

    /// Creates a Sass number with arbitrary numerator and denominator units.
    ///
    /// @param value the numeric magnitude
    /// @param numeratorUnits units multiplied into the magnitude
    /// @param denominatorUnits units dividing the magnitude
    /// @return the simplified Sass number
    public static SassValue number(
            double value,
            List<String> numeratorUnits,
            List<String> denominatorUnits
    ) {
        return new SassValue(SassNumber.withUnits(
                value,
                numeratorUnits,
                denominatorUnits
        ));
    }

    /// Creates a quoted or unquoted Sass string.
    ///
    /// @param text the semantic text without surrounding quotes
    /// @param quoted whether CSS serialization emits quotes
    /// @return the Sass string
    public static SassValue string(String text, boolean quoted) {
        return new SassValue(new SassString(text, quoted));
    }

    /// Creates an immutable Sass list.
    ///
    /// @param contents the elements in source order
    /// @param separator the element separator
    /// @param bracketed whether CSS serialization uses square brackets
    /// @return the Sass list
    /// @throws IllegalArgumentException if a multi-element list has an
    /// undecided separator
    public static SassValue list(
            List<SassValue> contents,
            SassListSeparator separator,
            boolean bracketed
    ) {
        Objects.requireNonNull(contents, "contents");
        var internal = new ArrayList<org.glavo.sassfx.internal.value.SassValue>(
                contents.size()
        );
        for (var element : contents) {
            internal.add(Objects.requireNonNull(element, "list element").value);
        }
        return new SassValue(new SassList(
                internal,
                toInternalSeparator(separator),
                bracketed
        ));
    }

    /// Creates an immutable insertion-ordered Sass map.
    ///
    /// @param contents entries in iteration order
    /// @return the Sass map
    public static SassValue map(Map<SassValue, SassValue> contents) {
        Objects.requireNonNull(contents, "contents");
        var internal =
                new LinkedHashMap<
                        org.glavo.sassfx.internal.value.SassValue,
                        org.glavo.sassfx.internal.value.SassValue
                        >(contents.size());
        for (var entry : contents.entrySet()) {
            internal.put(
                    Objects.requireNonNull(entry.getKey(), "map key").value,
                    Objects.requireNonNull(entry.getValue(), "map value").value
            );
        }
        return new SassValue(new SassMap(internal));
    }

    /// Creates an immutable Sass argument list.
    ///
    /// Argument lists carry positional rest arguments and leftover keyword
    /// arguments. Calling [#keywords()] on the returned value marks its
    /// keywords as observed; [#keywordContents()] reads them without changing
    /// that state.
    ///
    /// @param contents the positional elements in source order
    /// @param separator the positional element separator
    /// @param keywords leftover keyword arguments without dollar signs
    /// @return the Sass argument list
    /// @throws IllegalArgumentException if a multi-element list has an
    /// undecided separator
    public static SassValue argumentList(
            List<SassValue> contents,
            SassListSeparator separator,
            Map<String, SassValue> keywords
    ) {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(keywords, "keywords");
        if (contents.size() > 1
                && separator == SassListSeparator.UNDECIDED) {
            throw new IllegalArgumentException(
                    "A multi-element argument list must have a separator."
            );
        }

        var internalContents =
                new ArrayList<org.glavo.sassfx.internal.value.SassValue>(
                        contents.size()
                );
        for (var element : contents) {
            internalContents.add(
                    Objects.requireNonNull(
                            element,
                            "argument-list element"
                    ).value
            );
        }
        var internalKeywords =
                new LinkedHashMap<
                        String,
                        org.glavo.sassfx.internal.value.SassValue
                        >(keywords.size());
        for (var entry : keywords.entrySet()) {
            internalKeywords.put(
                    Objects.requireNonNull(
                            entry.getKey(),
                            "argument-list keyword"
                    ),
                    Objects.requireNonNull(
                            entry.getValue(),
                            "argument-list keyword value"
                    ).value
            );
        }
        return new SassValue(new SassArgumentList(
                internalContents,
                toInternalSeparator(separator),
                internalKeywords
        ));
    }

    /// Creates a Sass color in an explicitly selected color space.
    ///
    /// A `null` channel represents the CSS missing-channel value `none`.
    /// Non-alpha channels may be outside the nominal gamut. This modern
    /// factory deliberately requires a color space, so the deprecated
    /// `null-alpha` and legacy `color-4-api` constructor ambiguities do not
    /// apply to the Java API.
    ///
    /// @param space the represented color space
    /// @param channel1 the first channel, or `null` for a missing channel
    /// @param channel2 the second channel, or `null` for a missing channel
    /// @param channel3 the third channel, or `null` for a missing channel
    /// @param alpha the alpha channel, or `null` for a missing channel
    /// @return the Sass color
    /// @throws IllegalArgumentException if alpha is outside zero through one
    public static SassValue color(
            SassColorSpace space,
            @Nullable Double channel1,
            @Nullable Double channel2,
            @Nullable Double channel3,
            @Nullable Double alpha
    ) {
        Objects.requireNonNull(space, "space");
        return new SassValue(SassColor.forSpace(
                space.internal(),
                channel1,
                channel2,
                channel3,
                alpha
        ));
    }

    /// Creates and simplifies a supported CSS calculation.
    ///
    /// The supported names are `calc`, `min`, `max`, and `clamp`. `calc`
    /// requires exactly one argument, `min` and `max` require at least one,
    /// and `clamp` accepts one through three. Simplification may return a
    /// number rather than a value whose type is [SassValueType#CALCULATION].
    ///
    /// @param name the lowercase calculation name
    /// @param arguments the structural calculation arguments
    /// @return the simplified Sass number or calculation
    /// @throws IllegalArgumentException if the name or argument count is
    /// unsupported
    /// @throws SassValueException if the arguments are semantically
    /// incompatible
    public static SassValue calculation(
            String name,
            List<SassCalculationValue> arguments
    ) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
        var internalArguments = new ArrayList<Object>(arguments.size());
        for (var argument : arguments) {
            internalArguments.add(toInternalCalculationValue(
                    Objects.requireNonNull(
                            argument,
                            "calculation argument"
                    )
            ));
        }

        org.glavo.sassfx.internal.value.SassValue result =
                switch (name) {
                    case "calc" -> {
                        if (internalArguments.size() != 1) {
                            throw new IllegalArgumentException(
                                    "calc() requires exactly one argument"
                            );
                        }
                        yield SassCalculation.calc(
                                internalArguments.get(0)
                        );
                    }
                    case "min" -> {
                        if (internalArguments.isEmpty()) {
                            throw new IllegalArgumentException(
                                    "min() requires at least one argument"
                            );
                        }
                        yield SassCalculation.min(internalArguments);
                    }
                    case "max" -> {
                        if (internalArguments.isEmpty()) {
                            throw new IllegalArgumentException(
                                    "max() requires at least one argument"
                            );
                        }
                        yield SassCalculation.max(internalArguments);
                    }
                    case "clamp" -> {
                        if (internalArguments.isEmpty()
                                || internalArguments.size() > 3) {
                            throw new IllegalArgumentException(
                                    "clamp() requires one to three arguments"
                            );
                        }
                        yield SassCalculation.clamp(
                                internalArguments.get(0),
                                internalArguments.size() > 1
                                        ? internalArguments.get(1)
                                        : null,
                                internalArguments.size() > 2
                                        ? internalArguments.get(2)
                                        : null
                        );
                    }
                    default -> throw new IllegalArgumentException(
                            "Unsupported calculation name: " + name
                    );
                };
        return wrap(result);
    }

    /// Creates a first-class function value for the active compilation.
    ///
    /// The returned value may be invoked through `sass:meta`. It remains bound
    /// to the compilation whose callback created it and cannot be reused by a
    /// different compilation.
    ///
    /// @param function the custom function implementation
    /// @return a Sass function value bound to the active compilation
    /// @throws IllegalArgumentException if the signature is invalid
    /// @throws IllegalStateException if no custom function callback is active
    public static SassValue function(SassCustomFunction function) {
        return new SassValue(
                Objects.requireNonNull(function, "function").toFunctionValue()
        );
    }

    /// Returns this value's runtime kind.
    ///
    /// @return the value kind
    public SassValueType type() {
        if (value instanceof SassNull) {
            return SassValueType.NULL;
        }
        if (value instanceof SassBoolean) {
            return SassValueType.BOOLEAN;
        }
        if (value instanceof SassNumber) {
            return SassValueType.NUMBER;
        }
        if (value instanceof SassString) {
            return SassValueType.STRING;
        }
        if (value instanceof SassList) {
            return SassValueType.LIST;
        }
        if (value instanceof SassArgumentList) {
            return SassValueType.ARGUMENT_LIST;
        }
        if (value instanceof SassMap) {
            return SassValueType.MAP;
        }
        if (value instanceof SassColor) {
            return SassValueType.COLOR;
        }
        if (value instanceof SassCalculation) {
            return SassValueType.CALCULATION;
        }
        if (value instanceof SassFunction) {
            return SassValueType.FUNCTION;
        }
        if (value instanceof SassMixin) {
            return SassValueType.MIXIN;
        }
        throw new IllegalStateException(
                "Unsupported internal Sass value: " + value.getClass().getName()
        );
    }

    /// Returns this value's Sass truthiness.
    ///
    /// @return {@code false} only for Sass false and null
    public boolean isTruthy() {
        return value.isTruthy();
    }

    /// Returns the represented boolean.
    ///
    /// @return the Java boolean
    /// @throws IllegalStateException if this is not a boolean
    public boolean booleanValue() {
        return require(SassBoolean.class, SassValueType.BOOLEAN).value();
    }

    /// Returns the represented number's magnitude.
    ///
    /// @return the numeric magnitude after unit simplification
    /// @throws IllegalStateException if this is not a number
    public double numberValue() {
        return require(SassNumber.class, SassValueType.NUMBER).value();
    }

    /// Returns the represented number's numerator units.
    ///
    /// @return an immutable unit list
    /// @throws IllegalStateException if this is not a number
    public @Unmodifiable List<String> numeratorUnits() {
        return require(SassNumber.class, SassValueType.NUMBER).numeratorUnits();
    }

    /// Returns the represented number's denominator units.
    ///
    /// @return an immutable unit list
    /// @throws IllegalStateException if this is not a number
    public @Unmodifiable List<String> denominatorUnits() {
        return require(SassNumber.class, SassValueType.NUMBER).denominatorUnits();
    }

    /// Returns the represented string's semantic text.
    ///
    /// @return text without surrounding quotes
    /// @throws IllegalStateException if this is not a string
    public String stringValue() {
        return require(SassString.class, SassValueType.STRING).text();
    }

    /// Returns whether the represented string is quoted.
    ///
    /// @return whether serialization emits quotes
    /// @throws IllegalStateException if this is not a string
    public boolean isQuoted() {
        return require(SassString.class, SassValueType.STRING).hasQuotes();
    }

    /// Returns this value's list separator.
    ///
    /// Atomic values use [SassListSeparator#UNDECIDED].
    ///
    /// @return the public separator
    public SassListSeparator separator() {
        return fromInternalSeparator(value.separator());
    }

    /// Returns whether this value's list view uses square brackets.
    ///
    /// @return whether the list is bracketed
    public boolean isBracketed() {
        return value.hasBrackets();
    }

    /// Returns the universal Sass list view.
    ///
    /// Atomic values return a singleton list containing this value. Maps return
    /// key-value pair lists.
    ///
    /// @return an immutable list view
    public @Unmodifiable List<SassValue> asList() {
        var result = new ArrayList<SassValue>(value.asList().size());
        for (var element : value.asList()) {
            result.add(wrap(element));
        }
        return List.copyOf(result);
    }

    /// Returns rest keyword arguments and marks them as observed.
    ///
    /// Accessing this method prevents the compiler from reporting those
    /// keywords as unused after the custom callback returns.
    ///
    /// @return an immutable insertion-ordered keyword map without dollar signs
    /// @throws IllegalStateException if this is not an argument list
    public @Unmodifiable Map<String, SassValue> keywords() {
        var arguments = require(
                SassArgumentList.class,
                SassValueType.ARGUMENT_LIST
        );
        var result = new LinkedHashMap<String, SassValue>();
        for (var entry : arguments.keywords().entrySet()) {
            result.put(entry.getKey(), wrap(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns rest keyword arguments without marking them as observed.
    ///
    /// This accessor is intended for adapters that must serialize an argument
    /// list before a remote callback reports whether it consumed the
    /// keywords. Most custom functions should call [#keywords()] instead.
    ///
    /// @return an immutable insertion-ordered keyword map without dollar signs
    /// @throws IllegalStateException if this is not an argument list
    public @Unmodifiable Map<String, SassValue> keywordContents() {
        var arguments = require(
                SassArgumentList.class,
                SassValueType.ARGUMENT_LIST
        );
        var result = new LinkedHashMap<String, SassValue>();
        for (var entry : arguments.keywordsWithoutMarking().entrySet()) {
            result.put(entry.getKey(), wrap(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns this map's insertion-ordered contents.
    ///
    /// @return an immutable map of public Sass values
    /// @throws IllegalStateException if this is not a map
    public @Unmodifiable Map<SassValue, SassValue> mapContents() {
        var map = require(SassMap.class, SassValueType.MAP);
        var result = new LinkedHashMap<SassValue, SassValue>();
        for (var entry : map.contents().entrySet()) {
            result.put(wrap(entry.getKey()), wrap(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns this color's color space.
    ///
    /// @return the represented public color space
    /// @throws IllegalStateException if this is not a color
    public SassColorSpace colorSpace() {
        return SassColorSpace.fromInternal(
                require(SassColor.class, SassValueType.COLOR).space()
        );
    }

    /// Returns a named color channel.
    ///
    /// Missing channels return zero, matching Sass value semantics. Use
    /// [#isColorChannelMissing(String)] to distinguish a missing channel from
    /// an explicit zero. The accepted names are the three channels of
    /// [#colorSpace()] and `alpha`.
    ///
    /// @param name the lowercase channel name
    /// @return the channel value
    /// @throws IllegalStateException if this is not a color
    /// @throws IllegalArgumentException if the channel is unknown in this space
    public double colorChannel(String name) {
        try {
            return require(SassColor.class, SassValueType.COLOR)
                    .channel(Objects.requireNonNull(name, "name"));
        } catch (SassValueException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
    }

    /// Returns whether a named color channel is missing.
    ///
    /// The accepted names are the three channels of [#colorSpace()] and
    /// `alpha`.
    ///
    /// @param name the lowercase channel name
    /// @return whether the channel has the CSS missing value `none`
    /// @throws IllegalStateException if this is not a color
    /// @throws IllegalArgumentException if the channel is unknown in this space
    public boolean isColorChannelMissing(String name) {
        try {
            return require(SassColor.class, SassValueType.COLOR)
                    .isChannelMissing(Objects.requireNonNull(name, "name"));
        } catch (SassValueException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
    }

    /// Returns one color channel while preserving a missing value.
    ///
    /// Indices zero through two address the native channels of
    /// [#colorSpace()]. Index three addresses alpha.
    ///
    /// @param index the zero-based channel index
    /// @return the channel value, or `null` when the channel is missing
    /// @throws IllegalStateException if this is not a color
    /// @throws IndexOutOfBoundsException if {@code index} is outside zero
    /// through three
    public @Nullable Double colorChannelOrNull(int index) {
        var color = require(SassColor.class, SassValueType.COLOR);
        return switch (index) {
            case 0 -> color.channel0OrNull();
            case 1 -> color.channel1OrNull();
            case 2 -> color.channel2OrNull();
            case 3 -> color.alphaOrNull();
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /// Returns this calculation's function name.
    ///
    /// @return the lowercase calculation name
    /// @throws IllegalStateException if this is not a calculation
    public String calculationName() {
        return require(
                SassCalculation.class,
                SassValueType.CALCULATION
        ).name();
    }

    /// Returns this calculation's structural arguments.
    ///
    /// @return an immutable calculation argument list
    /// @throws IllegalStateException if this is not a calculation
    public @Unmodifiable List<SassCalculationValue> calculationArguments() {
        var calculation = require(
                SassCalculation.class,
                SassValueType.CALCULATION
        );
        var result =
                new ArrayList<SassCalculationValue>(
                        calculation.arguments().size()
                );
        for (var argument : calculation.arguments()) {
            result.add(fromInternalCalculationValue(argument));
        }
        return List.copyOf(result);
    }

    /// Returns the CSS representation of this value.
    ///
    /// @return the CSS text
    /// @throws IllegalStateException if this value cannot be represented in CSS
    public String toCssString() {
        try {
            return value.toCssString();
        } catch (org.glavo.sassfx.internal.value.SassValueException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    /// Returns the Sass inspect representation.
    ///
    /// @return the inspect text
    @Override
    public String toString() {
        return value.toString();
    }

    /// Compares the represented Sass values.
    ///
    /// @param other the object to compare
    /// @return whether both wrappers represent equal Sass values
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof SassValue sassValue
                && value.equals(sassValue.value);
    }

    /// Returns the represented Sass value's hash code.
    ///
    /// @return the Sass equality hash
    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /// Returns the evaluator value used by package-local callback adapters.
    ///
    /// @return the represented evaluator value
    org.glavo.sassfx.internal.value.SassValue internalValue() {
        return value;
    }

    /// Returns a cached or newly allocated wrapper.
    static SassValue wrap(
            org.glavo.sassfx.internal.value.SassValue value
    ) {
        return value == SassNull.NULL ? NULL : new SassValue(value);
    }

    /// Casts the represented value or reports a public type mismatch.
    private <T> T require(Class<T> type, SassValueType expected) {
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Expected " + expected + " but was " + type()
            );
        }
        return type.cast(value);
    }

    /// Converts a public list separator to the evaluator representation.
    private static org.glavo.sassfx.internal.value.ListSeparator toInternalSeparator(
            SassListSeparator separator
    ) {
        return switch (Objects.requireNonNull(separator, "separator")) {
            case SPACE -> org.glavo.sassfx.internal.value.ListSeparator.SPACE;
            case COMMA -> org.glavo.sassfx.internal.value.ListSeparator.COMMA;
            case SLASH -> org.glavo.sassfx.internal.value.ListSeparator.SLASH;
            case UNDECIDED -> org.glavo.sassfx.internal.value.ListSeparator.UNDECIDED;
        };
    }

    /// Converts an evaluator list separator to the public representation.
    private static SassListSeparator fromInternalSeparator(
            org.glavo.sassfx.internal.value.ListSeparator separator
    ) {
        return switch (separator) {
            case SPACE -> SassListSeparator.SPACE;
            case COMMA -> SassListSeparator.COMMA;
            case SLASH -> SassListSeparator.SLASH;
            case UNDECIDED -> SassListSeparator.UNDECIDED;
        };
    }

    /// Converts a public calculation value to the evaluator representation.
    private static Object toInternalCalculationValue(
            SassCalculationValue value
    ) {
        if (value instanceof SassCalculationValue.Value wrapped) {
            return wrapped.value().value;
        }
        if (value instanceof SassCalculationValue.StringValue string) {
            return new SassString(string.text(), false);
        }
        if (value instanceof SassCalculationValue.Operation operation) {
            return SassCalculation.operate(
                    switch (operation.operator()) {
                        case PLUS -> CalculationOperator.PLUS;
                        case MINUS -> CalculationOperator.MINUS;
                        case TIMES -> CalculationOperator.TIMES;
                        case DIVIDED_BY -> CalculationOperator.DIVIDED_BY;
                    },
                    toInternalCalculationValue(operation.left()),
                    toInternalCalculationValue(operation.right())
            );
        }
        throw new AssertionError(
                "Unsupported calculation value: "
                        + value.getClass().getName()
        );
    }

    /// Converts an evaluator calculation value to its public representation.
    private static SassCalculationValue fromInternalCalculationValue(
            Object value
    ) {
        if (value instanceof SassNumber number) {
            return new SassCalculationValue.Value(wrap(number));
        }
        if (value instanceof SassCalculation calculation) {
            return new SassCalculationValue.Value(wrap(calculation));
        }
        if (value instanceof SassString string) {
            return new SassCalculationValue.StringValue(string.text());
        }
        if (value instanceof CalculationOperation operation) {
            return new SassCalculationValue.Operation(
                    switch (operation.operator()) {
                        case PLUS -> SassCalculationValue.Operator.PLUS;
                        case MINUS -> SassCalculationValue.Operator.MINUS;
                        case TIMES -> SassCalculationValue.Operator.TIMES;
                        case DIVIDED_BY ->
                                SassCalculationValue.Operator.DIVIDED_BY;
                    },
                    fromInternalCalculationValue(operation.left()),
                    fromInternalCalculationValue(operation.right())
            );
        }
        throw new IllegalStateException(
                "Unsupported internal calculation value: "
                        + value.getClass().getName()
        );
    }
}
