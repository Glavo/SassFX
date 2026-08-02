// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies common Sass value semantics, immutable collection storage, and unit arithmetic.
@NotNullByDefault
final class SassValueTest {
    /// Verifies the singleton boolean and null values and Sass truthiness rules.
    @Test
    void usesSingletonTruthiness() {
        assertSame(SassBoolean.TRUE, SassBoolean.of(true));
        assertSame(SassBoolean.FALSE, SassBoolean.of(false));
        assertTrue(SassBoolean.TRUE.isTruthy());
        assertFalse(SassBoolean.FALSE.isTruthy());
        assertFalse(SassNull.NULL.isTruthy());
        assertTrue(SassNumber.of(0, null).isTruthy());
        assertTrue(new SassString("", false).isTruthy());

        assertSame(SassBoolean.FALSE, SassBoolean.TRUE.unaryNot());
        assertSame(SassBoolean.TRUE, SassBoolean.FALSE.unaryNot());
        assertSame(SassBoolean.TRUE, SassNull.NULL.unaryNot());
        assertSame(SassBoolean.FALSE, SassNumber.of(0, null).unaryNot());
    }

    /// Verifies that string quoting affects CSS output but not equality or hashing.
    @Test
    void comparesStringsIndependentlyOfQuotes() {
        var quoted = new SassString("alpha", true);
        var unquoted = new SassString("alpha", false);

        assertEquals(quoted, unquoted);
        assertEquals(quoted.hashCode(), unquoted.hashCode());
        assertEquals("\"alpha\"", quoted.toCssString());
        assertEquals("alpha", unquoted.toCssString());
        assertNotEquals(quoted, new SassString("beta", true));
    }

    /// Verifies the universal Sass list view for atomic, list, and map values.
    @Test
    void viewsEveryValueAsAList() {
        var atom = SassNumber.of(1, null);
        assertEquals(List.of(atom), atom.asList());
        assertEquals(1, atom.lengthAsList());
        assertEquals(ListSeparator.UNDECIDED, atom.separator());
        assertFalse(atom.hasBrackets());
        assertThrows(UnsupportedOperationException.class, () -> atom.asList().clear());

        var list = new SassList(
                List.of(atom, SassBoolean.TRUE),
                ListSeparator.COMMA,
                true
        );
        assertEquals(list.contents(), list.asList());
        assertEquals(2, list.lengthAsList());
        assertEquals(ListSeparator.COMMA, list.separator());
        assertTrue(list.hasBrackets());

        var key = new SassString("key", false);
        var value = SassNumber.of(2, null);
        var map = new SassMap(Map.of(key, value));
        assertEquals(1, map.lengthAsList());
        assertEquals(ListSeparator.COMMA, map.separator());
        var pair = assertInstanceOf(SassList.class, map.asList().get(0));
        assertEquals(ListSeparator.SPACE, pair.separator());
        assertFalse(pair.hasBrackets());
        assertEquals(List.of(key, value), pair.contents());
        assertThrows(UnsupportedOperationException.class, () -> map.asList().clear());
    }

    /// Verifies that Sass lists and maps copy inputs and expose immutable contents.
    @Test
    void copiesListAndMapContents() {
        var first = new SassString("first", false);
        var second = new SassString("second", false);
        var listInput = new ArrayList<SassValue>(List.of(first, second));
        var list = new SassList(listInput, ListSeparator.SPACE, false);
        listInput.clear();

        assertEquals(List.of(first, second), list.contents());
        assertThrows(UnsupportedOperationException.class, () -> list.contents().clear());

        var mapInput = new LinkedHashMap<SassValue, SassValue>();
        mapInput.put(first, SassNumber.of(1, null));
        mapInput.put(second, SassNumber.of(2, null));
        var map = new SassMap(mapInput);
        mapInput.clear();

        assertEquals(List.of(first, second), List.copyOf(map.contents().keySet()));
        assertEquals(SassNumber.of(1, null), map.contents().get(first));
        assertEquals(SassNumber.of(2, null), map.contents().get(second));
        assertThrows(UnsupportedOperationException.class, () -> map.contents().clear());
    }

    /// Verifies Sass equality and hashing between empty lists and empty maps.
    @Test
    void equatesEmptyListsAndMaps() {
        var list = new SassList(List.of(), ListSeparator.UNDECIDED, false);
        var map = new SassMap(Map.of());

        assertEquals(list, map);
        assertEquals(map, list);
        assertEquals(list.hashCode(), map.hashCode());
    }

    /// Verifies that empty lists and maps reject CSS serialization.
    @Test
    void rejectsValuesThatCannotBeSerializedAsCss() {
        var emptyList = new SassList(List.of(), ListSeparator.UNDECIDED, false);
        var map = new SassMap(Map.of(
                new SassString("key", false),
                SassNumber.of(1, null)
        ));

        assertEquals(
                "() isn't a valid CSS value.",
                assertThrows(SassValueException.class, emptyList::toCssString).getMessage()
        );
        assertEquals(
                "(key: 1) isn't a valid CSS value.",
                assertThrows(SassValueException.class, map::toCssString).getMessage()
        );
    }

    /// Verifies blank values are omitted without leaving CSS list separators.
    @Test
    void omitsBlankCssListElements() {
        var emptyString = new SassString("", false);
        var quotedEmptyString = new SassString("", true);
        var nestedBlank = new SassList(
                List.of(SassNull.NULL, emptyString),
                ListSeparator.SPACE,
                false
        );
        var contents = new SassList(
                List.of(
                        SassNull.NULL,
                        emptyString,
                        nestedBlank,
                        new SassString("value", false)
                ),
                ListSeparator.COMMA,
                false
        );

        assertTrue(SassNull.NULL.isBlank());
        assertTrue(emptyString.isBlank());
        assertTrue(nestedBlank.isBlank());
        assertFalse(quotedEmptyString.isBlank());
        assertFalse(new SassList(List.of(), ListSeparator.UNDECIDED, true).isBlank());
        assertEquals("value", contents.toCssString());
        assertEquals(
                "",
                new SassList(
                        List.of(SassNull.NULL),
                        ListSeparator.UNDECIDED,
                        false
                ).toCssString()
        );
        assertEquals(
                "[\"\"]",
                new SassList(
                        List.of(SassNull.NULL, quotedEmptyString),
                        ListSeparator.COMMA,
                        true
                ).toCssString()
        );
    }

    /// Verifies inspect serialization retains singleton list separators.
    @Test
    void serializesListSeparatorsForInspection() {
        var one = SassNumber.of(1, null);
        var two = SassNumber.of(2, null);

        assertEquals(
                "1, 2",
                new SassList(List.of(one, two), ListSeparator.COMMA, false).toString()
        );
        assertEquals(
                "(1/)",
                new SassList(List.of(one), ListSeparator.SLASH, false).toString()
        );
        assertEquals(
                "[1,]",
                new SassList(List.of(one), ListSeparator.COMMA, true).toString()
        );
        assertEquals(
                "[1/]",
                new SassList(List.of(one), ListSeparator.SLASH, true).toString()
        );
        assertEquals(
                "()",
                new SassList(List.of(), ListSeparator.UNDECIDED, false).toString()
        );
    }

    /// Verifies complex-unit and non-finite numbers use CSS calculations.
    @Test
    void serializesCalculatedNumbers() {
        var complexNumber = SassNumber.withUnits(
                1,
                List.of("px", "em"),
                List.of("s", "turn")
        );

        assertEquals(
                "calc(1px * 1em / 1s / 1turn)",
                complexNumber.toCssString()
        );
        assertEquals("calc(infinity)", SassNumber.of(Double.POSITIVE_INFINITY, null).toString());
        var unitfulInfinity = SassNumber.of(Double.NEGATIVE_INFINITY, "px");
        assertEquals(
                "calc(-infinity * 1px)",
                unitfulInfinity.toCssString()
        );
        assertEquals(
                "var(--c) / (-infinity * 1px)",
                new CalculationOperation(
                        CalculationOperator.DIVIDED_BY,
                        new SassString("var(--c)", false),
                        unitfulInfinity
                ).toCssString()
        );
        assertEquals("calc(NaN)", SassNumber.of(Double.NaN, null).toCssString());
    }

    /// Verifies fuzzy numeric range checks normalize endpoint-adjacent values.
    @Test
    void validatesFuzzyNumberRanges() {
        assertEquals(0.0, SassNumber.of(-0.000000000001, "%").valueInRange(0, 100));
        assertEquals(100.0, SassNumber.of(100.000000000001, "%").valueInRange(0, 100));
        assertEquals(
                "Expected 101% to be within 0% and 100%.",
                assertThrows(
                        SassValueException.class,
                        () -> SassNumber.of(101, "%").valueInRange(0, 100)
                ).getMessage()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SassNumber.of(1, null).valueInRange(2, 1)
        );
    }

    /// Verifies equality and hashing across canonical length and time units.
    @Test
    void comparesConvertibleNumbersCanonically() {
        var inch = SassNumber.of(1, "in");
        var pixels = SassNumber.of(96, "px");
        var second = SassNumber.of(1, "s");
        var milliseconds = SassNumber.of(1000, "ms");

        assertEquals(inch, pixels);
        assertEquals(inch.hashCode(), pixels.hashCode());
        assertEquals(second, milliseconds);
        assertEquals(second.hashCode(), milliseconds.hashCode());
        assertNotEquals(inch, second);
    }

    /// Verifies arithmetic and ordering for compatible units while retaining left units.
    @Test
    void operatesOnCompatibleUnits() {
        var inch = SassNumber.of(1, "in");
        var pixels = SassNumber.of(96, "px");
        var seconds = SassNumber.of(2, "s");
        var milliseconds = SassNumber.of(500, "ms");

        assertEquals(SassNumber.of(2, "in"), inch.plus(pixels));
        assertEquals(SassNumber.of(1.5, "s"), seconds.minus(milliseconds));
        assertSame(SassBoolean.TRUE, inch.greaterThan(SassNumber.of(95, "px")));
        assertSame(SassBoolean.FALSE, inch.lessThan(pixels));
        assertSame(
                SassBoolean.TRUE,
                SassNumber.of(1000, "ms").lessThanOrEquals(SassNumber.of(1, "s"))
        );
        assertEquals(
                "1in and 1s have incompatible units.",
                assertThrows(
                        SassValueException.class,
                        () -> inch.plus(SassNumber.of(1, "s"))
                ).getMessage()
        );
    }

    /// Verifies unitless left operands adopt units from compatible right operands.
    @Test
    void adoptsRightUnitsForUnitlessArithmetic() {
        assertEquals(
                SassNumber.of(3, "px"),
                SassNumber.of(1, null).plus(SassNumber.of(2, "px"))
        );
        assertEquals(
                SassNumber.of(-1, "px"),
                SassNumber.of(1, null).minus(SassNumber.of(2, "px"))
        );
        assertEquals(
                SassNumber.of(1, "px"),
                SassNumber.of(5, null).modulo(SassNumber.of(2, "px"))
        );
    }

    /// Verifies multiplication and division combine, convert, and cancel units.
    @Test
    void cancelsUnitsDuringMultiplicationAndDivision() {
        var product = assertInstanceOf(
                SassNumber.class,
                SassNumber.of(2, "px").times(SassNumber.of(3, "s"))
        );
        assertEquals(6.0, product.value());
        assertEquals(List.of("px", "s"), product.numeratorUnits());
        assertEquals(List.of(), product.denominatorUnits());

        var reduced = assertInstanceOf(
                SassNumber.class,
                product.dividedBy(SassNumber.of(2, "s"))
        );
        assertEquals(SassNumber.of(3, "px"), reduced);

        var convertedRatio = assertInstanceOf(
                SassNumber.class,
                SassNumber.of(1, "in").dividedBy(SassNumber.of(96, "px"))
        );
        assertEquals(SassNumber.of(1, null), convertedRatio);
        assertTrue(convertedRatio.isUnitless());
    }

    /// Verifies unit cancellation order and positive-zero modulo normalization.
    @Test
    void preservesCancellationOrderAndNormalizesModuloZero() {
        var simplified = SassNumber.withUnits(
                1,
                List.of("in"),
                List.of("px", "cm")
        );
        assertEquals(96.0, simplified.value());
        assertEquals(List.of(), simplified.numeratorUnits());
        assertEquals(List.of("cm"), simplified.denominatorUnits());
        assertEquals("calc(96 / 1cm)", simplified.toCssString());

        var remainder = assertInstanceOf(
                SassNumber.class,
                SassNumber.of(-4, null).modulo(SassNumber.of(2, null))
        );
        assertEquals(
                Double.doubleToRawLongBits(0.0),
                Double.doubleToRawLongBits(remainder.value())
        );
    }

    /// Builds migration expressions that remove and optionally replace units.
    @Test
    void suggestsUnitConversions() {
        assertEquals(
                "calc($value / 1px)",
                SassNumber.of(1, "px").unitSuggestion("value", null)
        );
        assertEquals(
                "$value * 1s",
                SassNumber.withUnits(
                        1,
                        List.of(),
                        List.of("s")
                ).unitSuggestion("value", null)
        );
        assertEquals(
                "calc($value * 1s / 1px * 1%)",
                SassNumber.withUnits(
                        1,
                        List.of("px"),
                        List.of("s")
                ).unitSuggestion("value", "%")
        );
    }

    /// Verifies that canonical value equality provides the map duplicate-key foundation.
    @Test
    void usesCanonicalEqualityForMapKeys() {
        var inch = SassNumber.of(1, "in");
        var pixels = SassNumber.of(96, "px");
        var first = new SassString("first", false);
        var replacement = new SassString("replacement", false);
        var contents = new LinkedHashMap<SassValue, SassValue>();

        contents.put(inch, first);
        contents.put(pixels, replacement);
        var map = new SassMap(contents);

        assertEquals(1, contents.size());
        assertEquals(1, map.contents().size());
        assertEquals(replacement, map.contents().get(inch));
        assertEquals(replacement, map.contents().get(pixels));
    }
}
