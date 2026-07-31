// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/// Parses JavaFX properties composed of one-to-four-value side shorthands.
///
/// The parser covers padding, region and border-image insets, border widths,
/// border-image widths, and border-image slices. It preserves property lookup
/// identifiers and rejects values that OpenJFX would silently truncate after
/// four sizes or after a border-image `fill` marker.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXFourSidedValueParser {
    /// Contains every non-time unit accepted by OpenJFX's size parser.
    private static final @Unmodifiable Set<String> SIZE_UNITS = Set.of(
            "%",
            "cm",
            "deg",
            "em",
            "ex",
            "grad",
            "in",
            "mm",
            "pc",
            "pt",
            "px",
            "rad",
            "turn"
    );

    /// Prevents instantiation.
    private JavaFXFourSidedValueParser() {
    }

    /// Parses a recognized four-sided JavaFX property.
    ///
    /// Global declaration keywords must be handled before this method. The
    /// property name must already be normalized to lowercase.
    ///
    /// @param property the normalized declaration name
    /// @param text     the complete value without `!important`
    /// @param span     the source range associated with the value
    /// @return the parsed layers, or `null` when the property uses another
    /// grammar
    /// @throws CssSerializeException if a recognized property has an invalid,
    /// empty, or silently truncated value
    public static @Nullable Value parse(
            String property,
            String text,
            SourceSpan span
    ) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(span, "span");

        final boolean layered;
        switch (property) {
            case "-fx-background-insets",
                 "-fx-border-insets",
                 "-fx-border-width",
                 "-fx-border-image-insets",
                 "-fx-border-image-slice",
                 "-fx-border-image-width" -> layered = true;
            case "-fx-padding",
                 "-fx-label-padding",
                 "-fx-opaque-insets" -> layered = false;
            default -> {
                return null;
            }
        }
        var slices = property.equals("-fx-border-image-slice");
        return parseLayers(property, text, span, layered, slices);
    }

    /// Parses a complete sequence of four-sided layers.
    ///
    /// @param property      the normalized declaration name
    /// @param text          the complete declaration value
    /// @param span          the source range associated with the value
    /// @param allowMultiple whether comma-separated layers are accepted
    /// @param slices        whether a trailing `fill` marker is accepted
    /// @return parsed ordinary layers or border-image slice layers
    /// @throws CssSerializeException if the value is malformed
    private static Value parseLayers(
            String property,
            String text,
            SourceSpan span,
            boolean allowMultiple,
            boolean slices
    ) {
        var sizeLayers = new ArrayList<FourSides>();
        var sliceLayers = new ArrayList<BorderImageSlice>();
        var index = 0;
        while (true) {
            index = triviaEnd(text, index, property, slices, span);
            if (index == text.length()) {
                throw invalidValue(property, slices, span);
            }

            var supplied = new ArrayList<SizeValue>(4);
            var fill = false;
            while (true) {
                index = triviaEnd(text, index, property, slices, span);
                if (index == text.length() || text.charAt(index) == ',') {
                    if (supplied.isEmpty()) {
                        throw invalidValue(property, slices, span);
                    }
                    var sides = new FourSides(expand(supplied));
                    if (slices) {
                        sliceLayers.add(new BorderImageSlice(sides, fill));
                    } else {
                        sizeLayers.add(sides);
                    }
                    if (index == text.length()) {
                        return slices
                                ? new SliceLayers(sliceLayers)
                                : new SizeLayers(sizeLayers);
                    }
                    if (!allowMultiple) {
                        throw invalidValue(property, false, span);
                    }
                    index++;
                    break;
                }
                if (fill) {
                    throw invalidValue(property, true, span);
                }

                var identifierEnd = JavaFXCssLexer.identifierEnd(text, index);
                if (identifierEnd > index) {
                    var identifier = text.substring(index, identifierEnd);
                    if (slices
                            && !supplied.isEmpty()
                            && identifier.equalsIgnoreCase("fill")) {
                        fill = true;
                    } else {
                        addSize(
                                supplied,
                                new LookupSize(identifier),
                                property,
                                slices,
                                span
                        );
                    }
                    index = identifierEnd;
                    continue;
                }

                var numberEnd = JavaFXCssLexer.numberEnd(text, index);
                if (numberEnd <= index) {
                    throw invalidValue(property, slices, span);
                }
                addSize(
                        supplied,
                        parseRawSize(text.substring(index, numberEnd), property, slices, span),
                        property,
                        slices,
                        span
                );
                index = numberEnd;
            }
        }
    }

    /// Skips JavaFX whitespace and comments or reports consuming trivia.
    ///
    /// @param text     the complete declaration value
    /// @param start    the inclusive trivia start
    /// @param property the normalized declaration name
    /// @param slices   whether the property accepts slice markers
    /// @param span     the source range associated with the value
    /// @return the first non-trivia offset
    /// @throws CssSerializeException if a comment consumes the value boundary
    private static int triviaEnd(
            String text,
            int start,
            String property,
            boolean slices,
            SourceSpan span
    ) {
        var end = JavaFXCssLexer.triviaEnd(text, start);
        if (end < 0) {
            throw invalidValue(property, slices, span);
        }
        return end;
    }

    /// Appends one side value while enforcing the four-value limit.
    ///
    /// @param values   the current source-order side values
    /// @param value    the next parsed size or lookup
    /// @param property the normalized declaration name
    /// @param slices   whether the property accepts slice markers
    /// @param span     the source range associated with the value
    /// @throws CssSerializeException if the layer already has four values
    private static void addSize(
            List<SizeValue> values,
            SizeValue value,
            String property,
            boolean slices,
            SourceSpan span
    ) {
        if (values.size() == 4) {
            throw invalidValue(property, slices, span);
        }
        values.add(value);
    }

    /// Parses one numeric JavaFX size token.
    ///
    /// @param token    the complete number token
    /// @param property the normalized declaration name
    /// @param slices   whether the property accepts slice markers
    /// @param span     the source range associated with the value
    /// @return the finite magnitude and normalized unit
    /// @throws CssSerializeException if the token is a time or non-finite size
    private static RawSize parseRawSize(
            String token,
            String property,
            boolean slices,
            SourceSpan span
    ) {
        var unitStart = token.length();
        while (unitStart > 0) {
            var character = token.charAt(unitStart - 1);
            if (character == '%'
                    || character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z') {
                unitStart--;
            } else {
                break;
            }
        }
        var value = Double.parseDouble(token.substring(0, unitStart));
        @Nullable var unit = unitStart == token.length()
                ? null
                : token.substring(unitStart).toLowerCase(Locale.ROOT);
        if (!Double.isFinite(value)
                || unit != null && !SIZE_UNITS.contains(unit)) {
            throw invalidValue(property, slices, span);
        }
        return new RawSize(value, unit);
    }

    /// Expands a one-to-four-value shorthand to top, right, bottom, and left.
    ///
    /// @param supplied the source-order side values
    /// @return the four expanded side values
    /// @throws IllegalArgumentException if the supplied count is outside one
    /// through four
    private static @Unmodifiable List<SizeValue> expand(
            List<SizeValue> supplied
    ) {
        return switch (supplied.size()) {
            case 1 -> List.of(
                    supplied.get(0),
                    supplied.get(0),
                    supplied.get(0),
                    supplied.get(0)
            );
            case 2 -> List.of(
                    supplied.get(0),
                    supplied.get(1),
                    supplied.get(0),
                    supplied.get(1)
            );
            case 3 -> List.of(
                    supplied.get(0),
                    supplied.get(1),
                    supplied.get(2),
                    supplied.get(1)
            );
            case 4 -> List.copyOf(supplied);
            default -> throw new IllegalArgumentException(
                    "side value count must be between one and four"
            );
        };
    }

    /// Creates a source-associated four-sided value failure.
    ///
    /// @param property the normalized declaration name
    /// @param slices   whether the property accepts slice markers
    /// @param span     the source range associated with the invalid value
    /// @return the exception to throw
    private static CssSerializeException invalidValue(
            String property,
            boolean slices,
            SourceSpan span
    ) {
        var requirement = slices
                ? "one to four size values and an optional trailing fill marker per layer."
                : "one to four size or property-lookup values per layer.";
        return new CssSerializeException(
                "JavaFX " + property + " requires " + requirement,
                span,
                null
        );
    }

    /// Identifies one parsed four-sided property representation.
    @NotNullByDefault
    public sealed interface Value permits SizeLayers, SliceLayers {
    }

    /// Stores ordinary four-sided layers in source order.
    ///
    /// @param layers the non-empty immutable layer list
    @NotNullByDefault
    public record SizeLayers(@Unmodifiable List<FourSides> layers)
            implements Value {
        /// Copies and validates the supplied layers.
        public SizeLayers {
            layers = List.copyOf(layers);
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("layers must not be empty");
            }
        }
    }

    /// Stores border-image slice layers in source order.
    ///
    /// @param layers the non-empty immutable slice-layer list
    @NotNullByDefault
    public record SliceLayers(@Unmodifiable List<BorderImageSlice> layers)
            implements Value {
        /// Copies and validates the supplied layers.
        public SliceLayers {
            layers = List.copyOf(layers);
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("layers must not be empty");
            }
        }
    }

    /// Stores one expanded top, right, bottom, and left value set.
    ///
    /// @param values the four immutable side values in side order
    @NotNullByDefault
    public record FourSides(@Unmodifiable List<SizeValue> values) {
        /// Copies and validates one expanded side set.
        public FourSides {
            values = List.copyOf(values);
            if (values.size() != 4) {
                throw new IllegalArgumentException(
                        "four-sided values must contain exactly four sizes"
                );
            }
        }
    }

    /// Stores one border-image slice layer.
    ///
    /// @param sizes the four expanded slice sizes
    /// @param fill  whether the image center is filled
    @NotNullByDefault
    public record BorderImageSlice(FourSides sizes, boolean fill) {
        /// Validates one slice layer.
        public BorderImageSlice {
            Objects.requireNonNull(sizes, "sizes");
        }
    }

    /// Identifies a numeric size or deferred property lookup.
    @NotNullByDefault
    public sealed interface SizeValue permits RawSize, LookupSize {
    }

    /// Stores one finite JavaFX size.
    ///
    /// @param value the finite numeric magnitude
    /// @param unit  the normalized unit, or `null` for a unitless size
    @NotNullByDefault
    public record RawSize(double value, @Nullable String unit)
            implements SizeValue {
        /// Validates one immutable size.
        public RawSize {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("value must be finite");
            }
            if (unit != null && !SIZE_UNITS.contains(unit)) {
                throw new IllegalArgumentException(
                        "unsupported size unit: " + unit
                );
            }
        }
    }

    /// Stores one JavaFX property lookup used in a size position.
    ///
    /// @param key the non-empty lookup identifier
    @NotNullByDefault
    public record LookupSize(String key) implements SizeValue {
        /// Validates one immutable lookup.
        public LookupSize {
            Objects.requireNonNull(key, "key");
            if (!JavaFXCssLexer.isIdentifier(key)) {
                throw new IllegalArgumentException(
                        "key must be a JavaFX identifier"
                );
            }
        }
    }
}
