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

/// Verifies every Dart Sass color-functions deprecation family.
@NotNullByDefault
final class ColorFunctionsDeprecationTest {
    /// Reports global and module channel readers with namespace-specific guidance.
    @Test
    void reportsLegacyChannelReaders() throws Exception {
        var result = compile(
                """
                        @use "sass:color";
                        .result {
                          global-red: red(#123456);
                          global-green: green(#123456);
                          global-blue: blue(#123456);
                          global-hue: hue(#123456);
                          global-saturation: saturation(#123456);
                          global-lightness: lightness(#123456);
                          module-red: color.red(#123456);
                          module-green: color.green(#123456);
                          module-blue: color.blue(#123456);
                          module-hue: color.hue(#123456);
                          module-saturation: color.saturation(#123456);
                          module-lightness: color.lightness(#123456);
                          module-whiteness: color.whiteness(#123456);
                          module-blackness: color.blackness(#123456);
                        }
                        """,
                verboseDiagnostics()
        );

        var warnings = colorFunctionWarnings(result);
        assertEquals(14, warnings.size());
        assertEquals(
                """
                        red() is deprecated. Suggestion:

                        color.channel($color, "red", $space: rgb)

                        More info: https://sass-lang.com/d/color-functions""",
                warnings.get(0).message()
        );
        assertEquals(
                """
                        color.red() is deprecated. Suggestion:

                        color.channel($color, "red", $space: rgb)

                        More info: https://sass-lang.com/d/color-functions""",
                warnings.get(6).message()
        );
        assertEquals(
                """
                        color.whiteness() is deprecated. Suggestion:

                        color.channel($color, "whiteness", $space: hwb)

                        More info: https://sass-lang.com/d/color-functions""",
                warnings.get(12).message()
        );
        assertTrue(result.output().contains("global-red: 18;"));
        assertTrue(result.output().contains("module-blackness:"));
    }

    /// Reports all nine legacy adjustment functions with migration suggestions.
    @Test
    void reportsLegacyColorAdjustments() throws Exception {
        var result = compile(
                """
                        .result {
                          hue: adjust-hue(red, 30deg);
                          lighter: lighten(black, 10);
                          darker: darken(white, 10);
                          saturated: saturate(hsl(0, 0%, 50%), 10);
                          desaturated: desaturate(hsl(0, 100%, 50%), 10);
                          opaque: opacify(rgba(0, 0, 0, 0), 0.1);
                          fade-in: fade-in(rgba(0, 0, 0, 0), 0.1);
                          transparent: transparentize(black, 0.1);
                          fade-out: fade-out(black, 0.1);
                        }
                        """,
                verboseDiagnostics()
        );

        var warnings = colorFunctionWarnings(result);
        assertEquals(9, warnings.size());
        assertEquals(
                """
                        adjust-hue() is deprecated. Suggestion:

                        color.adjust($color, $hue: 30deg)

                        More info: https://sass-lang.com/d/color-functions""",
                warnings.get(0).message()
        );
        assertEquals(
                """
                        lighten() is deprecated. Suggestions:

                        color.scale($color, $lightness: 10%)
                        color.adjust($color, $lightness: 10%)

                        More info: https://sass-lang.com/d/color-functions""",
                warnings.get(1).message()
        );
        assertEquals(
                """
                        darken() is deprecated. Suggestions:

                        color.scale($color, $lightness: -10%)
                        color.adjust($color, $lightness: -10%)

                        More info: https://sass-lang.com/d/color-functions""",
                warnings.get(2).message()
        );
        assertEquals(
                """
                        opacify() is deprecated. Suggestions:

                        color.scale($color, $alpha: 10%)
                        color.adjust($color, $alpha: 0.1)

                        More info: https://sass-lang.com/d/color-functions""",
                warnings.get(5).message()
        );
    }

    /// Preserves CSS filter and unknown-function branches without false warnings.
    @Test
    void preservesPlainCssBranches() throws Exception {
        var result = compile(
                """
                        .result {
                          filter: saturate(50%);
                          unknown: whiteness(red);
                        }
                        """,
                verboseDiagnostics()
        );

        assertTrue(colorFunctionWarnings(result).isEmpty());
        assertTrue(result.output().contains("filter: saturate(50%);"));
        assertTrue(result.output().contains("unknown: whiteness(red);"));
    }

    /// Applies silence and fatal policy to channel and adjustment diagnostics.
    @Test
    void appliesDiagnosticPolicy() throws Exception {
        var source = """
                @use "sass:color";
                .result {
                  channel: color.red(red);
                  adjusted: lighten(black, 10);
                }
                """;
        var silenced = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(SassDeprecation.COLOR_FUNCTIONS),
                Set.of(),
                Set.of()
        );
        assertTrue(colorFunctionWarnings(
                compile(source, silenced)
        ).isEmpty());

        var fatal = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(SassDeprecation.COLOR_FUNCTIONS),
                Set.of()
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(source, fatal)
        );
        assertEquals(
                "color-functions",
                failure.primaryDiagnostic().code()
        );
        assertTrue(failure.getMessage().contains(
                "color-functions deprecation to be fatal"
        ));
    }

    /// Returns diagnostics carrying the color-functions identifier.
    ///
    /// @param result the completed compilation
    /// @return immutable matching diagnostics in delivery order
    private static @Unmodifiable List<Diagnostic> colorFunctionWarnings(
            CompileResult<String> result
    ) {
        return result.diagnostics().stream()
                .filter(diagnostic ->
                        "color-functions".equals(diagnostic.code())
                )
                .toList();
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
