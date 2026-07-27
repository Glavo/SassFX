// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.glavo.scssfx.internal.value.SassArgumentList;
import org.glavo.scssfx.internal.value.SassBoolean;
import org.glavo.scssfx.internal.value.SassCalculation;
import org.glavo.scssfx.internal.value.SassColor;
import org.glavo.scssfx.internal.value.SassFunction;
import org.glavo.scssfx.internal.value.SassList;
import org.glavo.scssfx.internal.value.SassMap;
import org.glavo.scssfx.internal.value.SassMixin;
import org.glavo.scssfx.internal.value.SassNull;
import org.glavo.scssfx.internal.value.SassNumber;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValueException;
import org.glavo.scssfx.internal.value.color.ColorSpace;
import org.jetbrains.annotations.ApiStatus;
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
/// scalar, list, and map values can also be constructed using this class's
/// factories. A value may be returned directly from a callback without
/// conversion.
@NotNullByDefault
public final class SassValue {
    /// The public wrapper for Sass null.
    private static final SassValue NULL = new SassValue(SassNull.NULL);

    /// Contains the evaluator value represented by this public view.
    private final org.glavo.scssfx.internal.value.SassValue value;

    /// Creates a public wrapper around one evaluator value.
    private SassValue(org.glavo.scssfx.internal.value.SassValue value) {
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
        var internal = new ArrayList<org.glavo.scssfx.internal.value.SassValue>(
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
                        org.glavo.scssfx.internal.value.SassValue,
                        org.glavo.scssfx.internal.value.SassValue
                        >(contents.size());
        for (var entry : contents.entrySet()) {
            internal.put(
                    Objects.requireNonNull(entry.getKey(), "map key").value,
                    Objects.requireNonNull(entry.getValue(), "map value").value
            );
        }
        return new SassValue(new SassMap(internal));
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
                (ColorSpace) space.bridgeToInternal(),
                channel1,
                channel2,
                channel3,
                alpha
        ));
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
        return SassColorSpace.bridgeFromInternal(
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

    /// Returns the CSS representation of this value.
    ///
    /// @return the CSS text
    /// @throws IllegalStateException if this value cannot be represented in CSS
    public String toCssString() {
        try {
            return value.toCssString();
        } catch (org.glavo.scssfx.internal.value.SassValueException failure) {
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

    /// Wraps an evaluator value for an internal callback bridge.
    ///
    /// @param value an internal evaluator value
    /// @return its public immutable view
    /// @throws IllegalArgumentException if the object is not an evaluator value
    @ApiStatus.Internal
    public static SassValue bridgeFromInternal(Object value) {
        if (!(value instanceof org.glavo.scssfx.internal.value.SassValue sassValue)) {
            throw new IllegalArgumentException("value must be an internal Sass value");
        }
        return wrap(sassValue);
    }

    /// Returns the evaluator value used by the internal callback bridge.
    ///
    /// @return the internal immutable value
    @ApiStatus.Internal
    public Object bridgeToInternal() {
        return value;
    }

    /// Returns a cached or newly allocated wrapper.
    private static SassValue wrap(
            org.glavo.scssfx.internal.value.SassValue value
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
    private static org.glavo.scssfx.internal.value.ListSeparator toInternalSeparator(
            SassListSeparator separator
    ) {
        return switch (Objects.requireNonNull(separator, "separator")) {
            case SPACE -> org.glavo.scssfx.internal.value.ListSeparator.SPACE;
            case COMMA -> org.glavo.scssfx.internal.value.ListSeparator.COMMA;
            case SLASH -> org.glavo.scssfx.internal.value.ListSeparator.SLASH;
            case UNDECIDED -> org.glavo.scssfx.internal.value.ListSeparator.UNDECIDED;
        };
    }

    /// Converts an evaluator list separator to the public representation.
    private static SassListSeparator fromInternalSeparator(
            org.glavo.scssfx.internal.value.ListSeparator separator
    ) {
        return switch (separator) {
            case SPACE -> SassListSeparator.SPACE;
            case COMMA -> SassListSeparator.COMMA;
            case SLASH -> SassListSeparator.SLASH;
            case UNDECIDED -> SassListSeparator.UNDECIDED;
        };
    }
}
