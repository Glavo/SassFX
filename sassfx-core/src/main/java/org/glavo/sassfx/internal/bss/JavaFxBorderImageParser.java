// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.bss;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.ListSeparator;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Parses JavaFX border-image declarations retained in the evaluated Sass value model.
///
/// The parser reconstructs JavaFX's layered four-sided size shorthands, border-image
/// slice records, and property lookup treatment without loading JavaFX classes.
@NotNullByDefault
final class JavaFxBorderImageParser {
    /// Prevents instantiation.
    private JavaFxBorderImageParser() {
    }

    /// Parses JavaFX border-image inset layers.
    ///
    /// @param value the evaluated Sass declaration value
    /// @param span  the source range associated with the value
    /// @return immutable expanded four-sided inset layers in source order
    /// @throws BssSerializeException if the value cannot be represented by JavaFX's grammar
    static @Unmodifiable List<FourSidedSizes> parseInsetLayers(SassValue value, SourceSpan span) {
        return parseFourSidedLayers(value, span, "BSS border-image insets require one to four JavaFX sizes per layer.");
    }

    /// Parses JavaFX border-image width layers.
    ///
    /// JavaFX represents {@code auto} as an identifier-backed parsed value, so
    /// this method retains it as a lookup-style size rather than replacing it.
    ///
    /// @param value the evaluated Sass declaration value
    /// @param span  the source range associated with the value
    /// @return immutable expanded four-sided width layers in source order
    /// @throws BssSerializeException if the value cannot be represented by JavaFX's grammar
    static @Unmodifiable List<FourSidedSizes> parseWidthLayers(SassValue value, SourceSpan span) {
        return parseFourSidedLayers(value, span, "BSS border-image widths require one to four JavaFX sizes per layer.");
    }

    /// Parses JavaFX border-image slice layers.
    ///
    /// @param value the evaluated Sass declaration value
    /// @param span  the source range associated with the value
    /// @return immutable expanded slice records in source order
    /// @throws BssSerializeException if the value cannot be represented by JavaFX's grammar
    static @Unmodifiable List<BorderImageSlice> parseSliceLayers(SassValue value, SourceSpan span) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        var values = layerValues(value, span, "BSS border-image slices require one or more comma-separated layers.");
        var layers = new ArrayList<BorderImageSlice>(values.size());
        for (var layer : values) {
            layers.add(parseSliceLayer(layer, span));
        }
        return List.copyOf(layers);
    }

    /// Parses four-sided size layers with one-to-four-value shorthand expansion.
    ///
    /// @param value   the evaluated Sass declaration value
    /// @param span    the source range associated with the value
    /// @param message the failure message for an invalid layer
    /// @return immutable expanded four-sided layers in source order
    /// @throws BssSerializeException if a layer is not one to four JavaFX sizes
    private static @Unmodifiable List<FourSidedSizes> parseFourSidedLayers(
            SassValue value,
            SourceSpan span,
            String message
    ) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(message, "message");
        var values = layerValues(value, span, message);
        var layers = new ArrayList<FourSidedSizes>(values.size());
        for (var layer : values) {
            layers.add(parseFourSidedLayer(layer, span, message));
        }
        return List.copyOf(layers);
    }

    /// Returns the top-level comma-separated declaration layers.
    ///
    /// @param value   the evaluated Sass declaration value
    /// @param span    the source range associated with the value
    /// @param message the failure message for an invalid outer list
    /// @return immutable source-order layer values
    /// @throws BssSerializeException if the outer list is bracketed or empty
    private static @Unmodifiable List<SassValue> layerValues(
            SassValue value,
            SourceSpan span,
            String message
    ) {
        if (!(value instanceof SassList list) || list.separator() != ListSeparator.COMMA) {
            return List.of(value);
        }
        if (list.hasBrackets() || list.contents().isEmpty()) {
            throw invalidBorderImage(span, message);
        }
        return List.copyOf(list.contents());
    }

    /// Parses one four-sided border-image layer.
    ///
    /// @param value   the layer value
    /// @param span    the source range associated with the value
    /// @param message the failure message for an invalid layer
    /// @return an expanded top, right, bottom, and left layer
    /// @throws BssSerializeException if the layer is not one to four sizes
    private static FourSidedSizes parseFourSidedLayer(
            SassValue value,
            SourceSpan span,
            String message
    ) {
        var components = spaceSeparatedValues(value, span, message, 4);
        var supplied = new ArrayList<SizeValue>(components.size());
        for (var component : components) {
            supplied.add(parseSize(component, span, message));
        }
        return new FourSidedSizes(expandFourSidedSizes(supplied));
    }

    /// Parses one border-image slice layer with its optional trailing fill marker.
    ///
    /// @param value the layer value
    /// @param span  the source range associated with the value
    /// @return an expanded slice record
    /// @throws BssSerializeException if the layer does not contain one to four sizes and an optional fill marker
    private static BorderImageSlice parseSliceLayer(SassValue value, SourceSpan span) {
        var message = "BSS border-image slices require one to four JavaFX sizes followed by an optional fill marker.";
        var components = spaceSeparatedValues(value, span, message, 5);
        var count = components.size();
        var fill = count > 1 && isKeyword(components.get(count - 1), "fill");
        var sizeCount = fill ? count - 1 : count;
        if (sizeCount == 0 || sizeCount > 4) {
            throw invalidBorderImage(span, message);
        }
        var supplied = new ArrayList<SizeValue>(sizeCount);
        for (var index = 0; index < sizeCount; index++) {
            supplied.add(parseSize(components.get(index), span, message));
        }
        return new BorderImageSlice(new FourSidedSizes(expandFourSidedSizes(supplied)), fill);
    }

    /// Returns one to a caller-defined maximum number of unbracketed space-separated values.
    ///
    /// @param value     the layer value
    /// @param span      the source range associated with the value
    /// @param message   the failure message for an invalid value shape
    /// @param maxValues the inclusive maximum number of values
    /// @return immutable source-order layer components
    /// @throws BssSerializeException if the value shape is not representable
    private static @Unmodifiable List<SassValue> spaceSeparatedValues(
            SassValue value,
            SourceSpan span,
            String message,
            int maxValues
    ) {
        if (!(value instanceof SassList list)) {
            return List.of(value);
        }
        if (list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()
                || list.contents().size() > maxValues) {
            throw invalidBorderImage(span, message);
        }
        return List.copyOf(list.contents());
    }

    /// Parses one raw JavaFX size or property lookup identifier.
    ///
    /// @param value   the evaluated source component
    /// @param span    the source range associated with the value
    /// @param message the failure message for an invalid component
    /// @return a raw size or deferred property lookup
    /// @throws BssSerializeException if the component is not JavaFX size syntax
    private static SizeValue parseSize(SassValue value, SourceSpan span, String message) {
        if (value instanceof SassNumber number) {
            return new RawSizeValue(number);
        }
        if (value instanceof SassString string
                && !string.hasQuotes()
                && isLookupIdentifier(string.text())) {
            return new LookupSizeValue(string.text());
        }
        throw invalidBorderImage(span, message);
    }

    /// Returns whether one Sass value is an unquoted keyword with the supplied spelling.
    ///
    /// @param value    the candidate Sass value
    /// @param expected the expected keyword spelling
    /// @return whether the unquoted value equals the expected keyword ignoring case
    private static boolean isKeyword(SassValue value, String expected) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            return false;
        }
        return string.text().equalsIgnoreCase(expected);
    }

    /// Expands one to four supplied sizes using JavaFX's four-sided shorthand rules.
    ///
    /// @param supplied the one to four source-order values
    /// @return top, right, bottom, and left values
    /// @throws IllegalArgumentException if the supplied size count is invalid
    private static @Unmodifiable List<SizeValue> expandFourSidedSizes(List<SizeValue> supplied) {
        return switch (supplied.size()) {
            case 1 -> List.of(supplied.get(0), supplied.get(0), supplied.get(0), supplied.get(0));
            case 2 -> List.of(supplied.get(0), supplied.get(1), supplied.get(0), supplied.get(1));
            case 3 -> List.of(supplied.get(0), supplied.get(1), supplied.get(2), supplied.get(1));
            case 4 -> List.copyOf(supplied);
            default -> throw new IllegalArgumentException("border-image size count must be between one and four");
        };
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

    /// Creates a source-associated border-image serialization failure.
    ///
    /// @param span    the source range associated with the value
    /// @param message the caller-facing failure message
    /// @return a source-associated serialization failure
    private static BssSerializeException invalidBorderImage(SourceSpan span, String message) {
        return new BssSerializeException(message, span, null);
    }

    /// Represents one raw JavaFX size or deferred property lookup.
    @NotNullByDefault
    sealed interface SizeValue permits RawSizeValue, LookupSizeValue {
    }

    /// Represents one raw JavaFX size.
    ///
    /// @param value the raw Sass number
    @NotNullByDefault
    record RawSizeValue(SassNumber value) implements SizeValue {
        /// Creates an immutable raw size value.
        RawSizeValue {
            value = Objects.requireNonNull(value, "value");
        }
    }

    /// Represents one JavaFX property lookup used where a size is required.
    ///
    /// @param key the unquoted property lookup key
    @NotNullByDefault
    record LookupSizeValue(String key) implements SizeValue {
        /// Creates an immutable lookup size value.
        LookupSizeValue {
            key = requireNonEmpty(key, "key");
        }
    }

    /// Represents one expanded top, right, bottom, and left size layer.
    ///
    /// @param values the four side values in top, right, bottom, left order
    @NotNullByDefault
    record FourSidedSizes(@Unmodifiable List<SizeValue> values) {
        /// Creates an immutable four-sided size layer.
        FourSidedSizes {
            values = List.copyOf(values);
            if (values.size() != 4) {
                throw new IllegalArgumentException("border-image layers must contain exactly four sizes");
            }
        }
    }

    /// Represents one JavaFX border-image slice layer.
    ///
    /// @param sizes the four expanded slice sizes
    /// @param fill  whether the image center is filled
    @NotNullByDefault
    record BorderImageSlice(FourSidedSizes sizes, boolean fill) {
        /// Creates an immutable border-image slice layer.
        BorderImageSlice {
            sizes = Objects.requireNonNull(sizes, "sizes");
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
