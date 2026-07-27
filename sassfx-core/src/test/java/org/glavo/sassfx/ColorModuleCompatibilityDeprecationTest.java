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

/// Verifies Dart Sass color-module-compat deprecation routing.
@NotNullByDefault
final class ColorModuleCompatibilityDeprecationTest {
    /// Reports every module fallback while preserving its plain-CSS result.
    @Test
    void reportsAllModuleCompatibilityFallbacks() throws Exception {
        var result = compile(
                """
                        @use "sass:color";
                        .result {
                          invert: color.invert(25%);
                          grayscale: color.grayscale(30%);
                          alpha-single: color.alpha(opacity=50);
                          alpha-rest: color.alpha(opacity=50, style=1);
                          opacity: color.opacity(40%);
                        }
                        """,
                verboseDiagnostics()
        );

        assertEquals(
                """
                        .result {
                          invert: invert(25%);
                          grayscale: grayscale(30%);
                          alpha-single: alpha(opacity=50);
                          alpha-rest: alpha(opacity=50, style=1);
                          opacity: opacity(40%);
                        }""",
                result.output()
        );
        assertEquals(
                List.of(
                        """
                                Passing a number (25%) to color.invert() is deprecated.

                                Recommendation: invert(25%)""",
                        """
                                Passing a number (30%) to color.grayscale() is deprecated.

                                Recommendation: grayscale(30%)""",
                        """
                                Using color.alpha() for a Microsoft filter is deprecated.

                                Recommendation: alpha(opacity=50)""",
                        """
                                Using color.alpha() for a Microsoft filter is deprecated.

                                Recommendation: alpha(opacity=50, style=1)""",
                        """
                                Passing a number (40% to color.opacity() is deprecated.

                                Recommendation: opacity(40%)"""
                ),
                compatibilityWarnings(result).stream()
                        .map(Diagnostic::message)
                        .toList()
        );
    }

    /// Reports special-number invert fallbacks but not color-valued module calls.
    @Test
    void distinguishesCssFallbacksFromColorOperations() throws Exception {
        var result = compile(
                """
                        @use "sass:color";
                        .result {
                          special: color.invert(var(--amount));
                          invert: color.invert(red);
                          grayscale: color.grayscale(red);
                          alpha: color.alpha(rgba(1, 2, 3, 0.4));
                          opacity: color.opacity(rgba(1, 2, 3, 0.4));
                        }
                        """,
                verboseDiagnostics()
        );

        assertEquals(1, compatibilityWarnings(result).size());
        assertEquals(
                """
                        Passing a number (var(--amount)) to color.invert() is deprecated.

                        Recommendation: invert(var(--amount))""",
                compatibilityWarnings(result).get(0).message()
        );
    }

    /// Keeps equivalent global plain-CSS fallbacks outside the module category.
    @Test
    void doesNotReportGlobalCssFallbacks() throws Exception {
        var result = compile(
                """
                        .result {
                          invert: invert(25%);
                          grayscale: grayscale(30%);
                          alpha: alpha(opacity=50);
                          opacity: opacity(40%);
                        }
                        """,
                verboseDiagnostics()
        );

        assertTrue(compatibilityWarnings(result).isEmpty());
    }

    /// Applies silence and fatal policy to every module compatibility warning.
    @Test
    void appliesDiagnosticPolicy() throws Exception {
        var source = """
                @use "sass:color";
                .result {
                  invert: color.invert(25%);
                }
                """;
        var silenced = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(SassDeprecation.COLOR_MODULE_COMPAT),
                Set.of(),
                Set.of()
        );
        assertTrue(compatibilityWarnings(compile(source, silenced)).isEmpty());

        var fatal = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(SassDeprecation.COLOR_MODULE_COMPAT),
                Set.of()
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(source, fatal)
        );
        assertEquals(
                "color-module-compat",
                failure.primaryDiagnostic().code()
        );
        assertTrue(failure.getMessage().contains(
                "color-module-compat deprecation to be fatal"
        ));
    }

    /// Returns diagnostics carrying the color-module-compat identifier.
    ///
    /// @param result the completed compilation
    /// @return immutable matching diagnostics in delivery order
    private static @Unmodifiable List<Diagnostic> compatibilityWarnings(
            CompileResult<String> result
    ) {
        return result.diagnostics().stream()
                .filter(diagnostic ->
                        "color-module-compat".equals(diagnostic.code())
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
