// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies structural construction through the public Sass value API.
@NotNullByDefault
final class SassValueApiTest {
    /// Snapshots argument-list contents and exposes keywords with explicit
    /// access tracking.
    @Test
    void constructsArgumentLists() {
        var positional = new ArrayList<SassValue>(
                List.of(SassValue.number(1))
        );
        var keywords = new LinkedHashMap<String, SassValue>();
        keywords.put("tone", SassValue.string("blue", false));

        var arguments = SassValue.argumentList(
                positional,
                SassListSeparator.COMMA,
                keywords
        );
        positional.clear();
        keywords.clear();

        assertEquals(SassValueType.ARGUMENT_LIST, arguments.type());
        assertEquals(List.of(SassValue.number(1)), arguments.asList());
        assertEquals(
                SassValue.string("blue", false),
                arguments.keywordContents().get("tone")
        );
        assertEquals(arguments.keywordContents(), arguments.keywords());
        assertThrows(
                UnsupportedOperationException.class,
                arguments.keywordContents()::clear
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SassValue.argumentList(
                        List.of(SassValue.number(1), SassValue.number(2)),
                        SassListSeparator.UNDECIDED,
                        Map.of()
                )
        );
    }

    /// Simplifies numeric operations and preserves unresolved calculation
    /// structure.
    @Test
    void constructsCalculations() {
        var simplified = SassValue.calculation(
                "calc",
                List.of(new SassCalculationValue.Operation(
                        SassCalculationValue.Operator.PLUS,
                        new SassCalculationValue.Value(
                                SassValue.number(1, "px")
                        ),
                        new SassCalculationValue.Value(
                                SassValue.number(2, "px")
                        )
                ))
        );
        assertEquals(SassValueType.NUMBER, simplified.type());
        assertEquals(3.0, simplified.numberValue());
        assertEquals(List.of("px"), simplified.numeratorUnits());

        var operation = new SassCalculationValue.Operation(
                SassCalculationValue.Operator.PLUS,
                new SassCalculationValue.StringValue("var(--size)"),
                new SassCalculationValue.Value(SassValue.number(2, "px"))
        );
        var calculation = SassValue.calculation(
                "calc",
                List.of(operation)
        );
        assertEquals(SassValueType.CALCULATION, calculation.type());
        assertEquals("calc", calculation.calculationName());
        assertEquals(List.of(operation), calculation.calculationArguments());
        assertEquals(
                "calc(var(--size) + 2px)",
                calculation.toCssString()
        );

        assertThrows(
                SassValueException.class,
                () -> SassValue.calculation(
                        "min",
                        List.of(
                                new SassCalculationValue.Value(
                                        SassValue.number(1, "px")
                                ),
                                new SassCalculationValue.Value(
                                        SassValue.number(2, "s")
                                )
                        )
                )
        );
    }

    /// Validates signatures explicitly and creates callable first-class
    /// function values only inside an active compilation.
    @Test
    void constructsFunctionValues() throws Exception {
        SassCustomFunction.validateSignature("double($value)");
        assertThrows(
                IllegalArgumentException.class,
                () -> SassCustomFunction.validateSignature("broken($value")
        );
        assertThrows(
                IllegalStateException.class,
                () -> SassValue.function(new SassCustomFunction(
                        "identity($value)",
                        arguments -> arguments.get(0)
                ))
        );

        var factory = new SassCustomFunction(
                "make-double()",
                ignored -> SassValue.function(new SassCustomFunction(
                        "double($value)",
                        nestedArguments -> SassValue.number(
                                nestedArguments.get(0).numberValue() * 2
                        )
                ))
        );
        var output = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @use "sass:meta";
                                .result { value: meta.call(make-double(), 4); }
                                """,
                        Syntax.SCSS,
                        URI.create("memory:///function-value.scss")
                ),
                CssTarget.DEFAULT,
                new CompileOptions(
                        false,
                        List.of(),
                        null,
                        List.of(),
                        List.of(factory)
                )
        ).output();

        assertEquals(
                """
                        .result {
                          value: 8;
                        }""",
                output
        );
    }

    /// Resolves public color-space names and exposes missing channels without
    /// substituting zero.
    @Test
    void exposesColorWireValues() {
        assertEquals(
                SassColorSpace.XYZ_D65,
                SassColorSpace.fromCssName("XYZ-D65")
        );
        var color = SassValue.color(
                SassColorSpace.OKLCH,
                null,
                0.2,
                30.0,
                null
        );
        assertNull(color.colorChannelOrNull(0));
        assertEquals(0.2, color.colorChannelOrNull(1));
        assertEquals(30.0, color.colorChannelOrNull(2));
        assertNull(color.colorChannelOrNull(3));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> color.colorChannelOrNull(4)
        );
    }
}
