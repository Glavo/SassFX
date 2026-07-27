// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies every Dart Sass function-units deprecation family.
@NotNullByDefault
final class FunctionUnitsDeprecationTest {
    /// Reports unitful indexes for both list access and replacement.
    @Test
    void reportsUnitfulListIndexes() throws Exception {
        var result = compile(
                """
                        @use "sass:list";
                        .result {
                          first: list.nth(a b, 1px);
                          changed: list.set-nth(a b, 2px, c);
                        }
                        """,
                verboseDiagnostics()
        );

        var warnings = functionUnitWarnings(result);
        assertEquals(2, warnings.size());
        assertEquals(
                """
                        $n: Passing a number with unit px is deprecated.

                        To preserve current behavior: calc($n / 1px)

                        More info: https://sass-lang.com/d/function-units""",
                warnings.get(0).message()
        );
        assertEquals(warnings.get(0).message(), warnings.get(1).message());
        assertTrue(result.output().contains("first: a;"));
        assertTrue(result.output().contains("changed: a c;"));
    }

    /// Reports non-canonical HSL and HWB constructor channel units.
    @Test
    void reportsLegacyConstructorUnits() throws Exception {
        var result = compile(
                """
                        .result {
                          multi: hsl(60in, 50, 30px);
                          single: hsl(60in 50 30px);
                          hwb: hwb(1in, 20%, 30%);
                        }
                        """,
                verboseDiagnostics()
        );

        var warnings = functionUnitWarnings(result);
        assertEquals(7, warnings.size());
        assertEquals(2, countContaining(
                warnings,
                "$hue: Passing a unit other than deg (60in)"
        ));
        assertEquals(2, countContaining(
                warnings,
                "$saturation: Passing a number without unit % (50)"
        ));
        assertEquals(2, countContaining(
                warnings,
                "$lightness: Passing a number without unit % (30px)"
        ));
        assertEquals(1, countContaining(
                warnings,
                "$hue: Passing a unit other than deg (1in)"
        ));
        assertTrue(warnings.stream().allMatch(diagnostic ->
                diagnostic.message().contains(
                        "https://sass-lang.com/d/function-units"
                )
        ));
    }

    /// Reports legacy color operation weights, channels, and alpha units.
    @Test
    void reportsLegacyColorOperationUnits() throws Exception {
        var result = compile(
                """
                        @use "sass:color";
                        .result {
                          mix: color.mix(red, blue, 50px);
                          invert: color.invert(red, 10);
                          adjust: color.adjust(
                              red,
                              $hue: 60in,
                              $saturation: -10,
                              $lightness: 10in,
                              $alpha: 10%
                          );
                          change: color.change(
                              red,
                              $hue: 30in,
                              $saturation: 20,
                              $lightness: 40px,
                              $alpha: 0.5px
                          );
                          legacy-hue: adjust-hue(red, 90in);
                        }
                        """,
                verboseDiagnostics()
        );

        var warnings = functionUnitWarnings(result);
        assertEquals(11, warnings.size());
        assertEquals(2, countContaining(
                warnings,
                "$weight: Passing a number without unit %"
        ));
        assertEquals(3, countContaining(
                warnings,
                "Passing a unit other than deg"
        ));
        assertEquals(2, countContaining(
                warnings,
                "$saturation: Passing a number without unit %"
        ));
        assertEquals(2, countContaining(
                warnings,
                "$lightness: Passing a number without unit %"
        ));
        assertEquals(1, countContaining(
                warnings,
                "$alpha: Passing a number with unit %"
        ));
        assertEquals(1, countContaining(
                warnings,
                "$alpha: Passing a unit other than % (0.5px)"
        ));
    }

    /// Applies silence and fatal policy to every newly contextualized family.
    @Test
    void appliesDiagnosticPolicy() throws Exception {
        var source = """
                @use "sass:list";
                @use "sass:color";
                .result {
                  list: list.nth(a b, 1px);
                  color: color.change(red, $alpha: 0.5px);
                }
                """;
        var silenced = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(SassDeprecation.FUNCTION_UNITS),
                Set.of(),
                Set.of()
        );
        assertTrue(functionUnitWarnings(
                compile(source, silenced)
        ).isEmpty());

        var fatal = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(SassDeprecation.FUNCTION_UNITS),
                Set.of()
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(source, fatal)
        );
        assertEquals(
                "function-units",
                failure.primaryDiagnostic().code()
        );
        assertTrue(failure.getMessage().contains(
                "function-units deprecation to be fatal"
        ));
    }

    /// Returns diagnostics carrying the function-units identifier.
    ///
    /// @param result the completed compilation
    /// @return immutable matching diagnostics in delivery order
    private static @Unmodifiable List<Diagnostic> functionUnitWarnings(
            CompileResult<String> result
    ) {
        return result.diagnostics().stream()
                .filter(diagnostic ->
                        "function-units".equals(diagnostic.code())
                )
                .toList();
    }

    /// Counts diagnostics whose messages contain one fragment.
    ///
    /// @param diagnostics diagnostics to search
    /// @param fragment required message fragment
    /// @return number of matching diagnostics
    private static int countContaining(
            @Unmodifiable List<Diagnostic> diagnostics,
            String fragment
    ) {
        return (int) diagnostics.stream()
                .filter(diagnostic ->
                        diagnostic.message().contains(fragment)
                )
                .count();
    }

    /// Creates verbose diagnostic options without logger side effects.
    ///
    /// @return options that retain every deprecation occurrence
    private static SassDiagnosticOptions verboseDiagnostics() {
        return new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    /// Compiles SCSS with explicit diagnostic processing.
    ///
    /// @param source SCSS source text
    /// @param diagnostics diagnostic processing configuration
    /// @return the completed CSS result
    /// @throws IOException if an imported source cannot be read
    /// @throws SassCompilationException if compilation fails
    private static CompileResult<String> compile(
            String source,
            SassDiagnosticOptions diagnostics
    ) throws IOException, SassCompilationException {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT,
                new CompileOptions(
                        false,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        diagnostics
                )
        );
    }
}
