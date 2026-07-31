// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceLocation;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies four-sided JavaFX property grammar without loading JavaFX classes.
@NotNullByDefault
final class JavaFXFourSidedValueParserTest {
    /// Expands one through four supplied side values using JavaFX shorthand
    /// rules.
    @Test
    void expandsFourSidedValues() {
        assertEquals(
                List.of(raw(1), raw(1), raw(1), raw(1)),
                firstLayer("-fx-padding", "1px").values()
        );
        assertEquals(
                List.of(raw(1), raw(2), raw(1), raw(2)),
                firstLayer("-fx-label-padding", "1px 2px").values()
        );
        assertEquals(
                List.of(raw(1), raw(2), raw(3), raw(2)),
                firstLayer("-fx-opaque-insets", "1px 2px 3px").values()
        );
        assertEquals(
                List.of(raw(1), raw(2), raw(3), raw(4)),
                firstLayer(
                        "-fx-background-insets",
                        "1px 2px 3px 4px"
                ).values()
        );
    }

    /// Retains comma-separated layers, comments, units, and property lookups.
    @Test
    void parsesLayeredSizesAndLookups() {
        var parsed = assertInstanceOf(
                JavaFXFourSidedValueParser.SizeLayers.class,
                parse(
                        "-fx-border-width",
                        "/**/ -FX-TOP 2% /**/, -3em 4deg 5turn /**/"
                )
        );

        assertEquals(2, parsed.layers().size());
        assertEquals(
                List.of(
                        new JavaFXFourSidedValueParser.LookupSize("-FX-TOP"),
                        raw(2, "%"),
                        new JavaFXFourSidedValueParser.LookupSize("-FX-TOP"),
                        raw(2, "%")
                ),
                parsed.layers().get(0).values()
        );
        assertEquals(
                List.of(
                        raw(-3, "em"),
                        raw(4, "deg"),
                        raw(5, "turn"),
                        raw(4, "deg")
                ),
                parsed.layers().get(1).values()
        );
    }

    /// Accepts every OpenJFX non-time size unit in a four-sided property.
    ///
    /// @param value one supported JavaFX size
    @ParameterizedTest
    @ValueSource(strings = {
            "1",
            "1%",
            "1em",
            "1ex",
            "1px",
            "1cm",
            "1mm",
            "1in",
            "1pt",
            "1pc",
            "1deg",
            "1grad",
            "1rad",
            "1turn"
    })
    void parsesEverySizeUnit(String value) {
        firstLayer("-fx-border-image-width", value);
    }

    /// Parses border-image slices and distinguishes a trailing fill marker
    /// from a lookup named `fill` in the first position.
    @Test
    void parsesBorderImageSlices() {
        var parsed = assertInstanceOf(
                JavaFXFourSidedValueParser.SliceLayers.class,
                parse(
                        "-fx-border-image-slice",
                        "10% fill, fill, -fx-slice FILL"
                )
        );

        assertEquals(3, parsed.layers().size());
        assertEquals(
                new JavaFXFourSidedValueParser.BorderImageSlice(
                        new JavaFXFourSidedValueParser.FourSides(
                                List.of(
                                        raw(10, "%"),
                                        raw(10, "%"),
                                        raw(10, "%"),
                                        raw(10, "%")
                                )
                        ),
                        true
                ),
                parsed.layers().get(0)
        );
        assertEquals(
                new JavaFXFourSidedValueParser.LookupSize("fill"),
                parsed.layers().get(1).sizes().values().get(0)
        );
        assertEquals(
                new JavaFXFourSidedValueParser.LookupSize("-fx-slice"),
                parsed.layers().get(2).sizes().values().get(0)
        );
        assertTrue(parsed.layers().get(2).fill());
    }

    /// Rejects empty values, invalid sizes, surplus terms, and discarded
    /// layers.
    @Test
    void rejectsUnsafeFourSidedValues() {
        var invalid = List.of(
                Map.entry("-fx-padding", ""),
                Map.entry("-fx-padding", "1px 2px 3px 4px 5px"),
                Map.entry("-fx-padding", "1px, 2px"),
                Map.entry("-fx-opaque-insets", "1px, 2px"),
                Map.entry("-fx-background-insets", "1px,,2px"),
                Map.entry("-fx-border-insets", "1px,"),
                Map.entry("-fx-border-width", "1ms"),
                Map.entry("-fx-border-image-insets", "\"-fx-size\""),
                Map.entry("-fx-border-image-width", "1px / 2px"),
                Map.entry("-fx-border-image-slice", "1px fill 2px"),
                Map.entry("-fx-border-image-slice", "1px fill fill"),
                Map.entry("-fx-border-image-slice", "1px 2px 3px 4px 5px")
        );

        for (var declaration : invalid) {
            assertThrows(
                    CssSerializeException.class,
                    () -> parse(declaration.getKey(), declaration.getValue()),
                    declaration.getKey() + ": " + declaration.getValue()
            );
        }
    }

    /// Leaves properties outside the four-sided family to other parsers.
    @Test
    void ignoresOtherProperties() {
        assertNull(parse("-fx-background-radius", "1px"));
        assertNull(parse("-fx-custom", "1px 2px"));
    }

    /// Returns the first parsed ordinary size layer.
    ///
    /// @param property the normalized declaration name
    /// @param value    the declaration value
    /// @return the first expanded layer
    private static JavaFXFourSidedValueParser.FourSides firstLayer(
            String property,
            String value
    ) {
        var parsed = assertInstanceOf(
                JavaFXFourSidedValueParser.SizeLayers.class,
                parse(property, value)
        );
        return parsed.layers().get(0);
    }

    /// Creates one raw pixel size.
    ///
    /// @param value the numeric magnitude
    /// @return the raw size
    private static JavaFXFourSidedValueParser.RawSize raw(double value) {
        return raw(value, "px");
    }

    /// Creates one raw size with the supplied unit.
    ///
    /// @param value the numeric magnitude
    /// @param unit  the normalized unit, or `null`
    /// @return the raw size
    private static JavaFXFourSidedValueParser.RawSize raw(
            double value,
            @Nullable String unit
    ) {
        return new JavaFXFourSidedValueParser.RawSize(value, unit);
    }

    /// Parses one value with a matching synthetic source span.
    ///
    /// @param property the normalized declaration name
    /// @param value    the declaration value
    /// @return the parsed value, or `null`
    private static @Nullable JavaFXFourSidedValueParser.Value parse(
            String property,
            String value
    ) {
        return JavaFXFourSidedValueParser.parse(
                property,
                value,
                span(value)
        );
    }

    /// Creates a synthetic source span for parser diagnostics.
    ///
    /// @param text the covered text
    /// @return the source span
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
