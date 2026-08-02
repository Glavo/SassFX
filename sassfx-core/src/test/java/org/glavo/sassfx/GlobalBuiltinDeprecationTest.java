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

/// Verifies Dart Sass global-builtin deprecation routing.
@NotNullByDefault
final class GlobalBuiltinDeprecationTest {
    /// Reports same-name and renamed replacements across every built-in module.
    @Test
    void reportsAliasesAcrossModules() throws Exception {
        var result = compile(
                """
                        .result {
                          color-channel: red(red);
                          color-adjust: adjust-hue(red, 10deg);
                          math-same: ceil(1.2);
                          math-renamed: comparable(1px, 2px);
                          math-unitless: unitless(1);
                          list-same: nth(a b, 1);
                          list-renamed: list-separator(a b);
                          map-renamed: map-get((answer: 42), answer);
                          string-same: quote(text);
                          string-renamed: str-length("text");
                          selector-same: is-superselector(".a", ".a");
                          selector-renamed: selector-parse(".a");
                          meta-same: type-of(1);
                        }
                        """,
                verboseDiagnostics()
        );

        assertEquals(
                List.of(
                        "color.red",
                        "color.adjust",
                        "math.ceil",
                        "math.compatible",
                        "math.is-unitless",
                        "list.nth",
                        "list.separator",
                        "map.get",
                        "string.quote",
                        "string.length",
                        "selector.is-superselector",
                        "selector.parse",
                        "meta.type-of"
                ),
                replacements(result)
        );
        assertTrue(globalWarnings(result).stream().allMatch(diagnostic ->
                diagnostic.message().endsWith(
                        "More info and automated migrator: "
                                + "https://sass-lang.com/d/import"
                )
        ));
    }

    /// Keeps module entry points free of global-builtin diagnostics.
    @Test
    void doesNotWarnForModuleCalls() throws Exception {
        var result = compile(
                """
                        @use "sass:color";
                        @use "sass:list";
                        @use "sass:map";
                        @use "sass:math";
                        @use "sass:meta";
                        @use "sass:selector";
                        @use "sass:string";
                        .result {
                          color: color.mix(red, blue);
                          list: list.nth(a b, 1);
                          map: map.get((answer: 42), answer);
                          math: math.abs(-1%);
                          meta: meta.type-of(1);
                          selector: selector.parse(".a");
                          string: string.length("text");
                        }
                        """
        );

        assertTrue(globalWarnings(result).isEmpty());
    }

    /// Distinguishes plain-CSS overloads from deprecated Sass implementations.
    @Test
    void reportsOnlySassConditionalBranches() throws Exception {
        var result = compile(
                """
                        @use "sass:math";
                        .css {
                          invert: invert(50%);
                          grayscale: grayscale(50%);
                          saturate: saturate(50%);
                          alpha: alpha(opacity=50);
                          opacity: opacity(50%);
                        }
                        .sass {
                          invert: invert(red);
                          grayscale: grayscale(red);
                          saturate: saturate(red, 10);
                          alpha: alpha(red);
                          opacity: opacity(red);
                          absolute: abs(-1);
                          percentage: abs(-1%);
                          module-percentage: math.abs(-1%);
                        }
                        """,
                verboseDiagnostics()
        );

        assertEquals(
                List.of(
                        "color.invert",
                        "color.grayscale",
                        "color.adjust",
                        "color.alpha",
                        "color.opacity"
                ),
                replacements(result)
        );
        var percentageWarnings = result.diagnostics().stream()
                .filter(diagnostic -> "abs-percent".equals(diagnostic.code()))
                .toList();
        assertEquals(1, percentageWarnings.size());
        assertEquals(
                """
                        Passing percentage units to the global abs() function is deprecated.
                        In the future, this will emit a CSS abs() function to be resolved by the browser.
                        To preserve current behavior: math.abs(-1%)
                        To emit a CSS abs() now: abs(#{-1%})
                        More info: https://sass-lang.com/d/abs-percent""",
                percentageWarnings.get(0).message()
        );
    }

    /// Preserves global metadata on first-class references but not module references.
    @Test
    void reportsThroughFirstClassGlobalReference() throws Exception {
        var result = compile(
                """
                        @use "sass:list";
                        @use "sass:meta";
                        $global: meta.get-function("length");
                        $module: meta.get-function("length", $module: "list");
                        $absolute: meta.get-function("abs");
                        .result {
                          global: meta.call($global, a b);
                          module: meta.call($module, a b);
                          absolute: meta.call($absolute, -1);
                        }
                        """,
                verboseDiagnostics()
        );

        assertEquals(
                List.of("list.length", "math.abs"),
                replacements(result)
        );
    }

    /// Orders global and function-specific diagnostics like wrapped Dart callbacks.
    @Test
    void ordersCombinedDeprecations() throws Exception {
        var result = compile(
                """
                        .result {
                          channel: red(red);
                          feature: feature-exists("at-error");
                          adjusted: lighten(black, 10);
                        }
                        """,
                verboseDiagnostics()
        );

        assertEquals(
                List.of(
                        "global-builtin",
                        "color-functions",
                        "global-builtin",
                        "feature-exists",
                        "global-builtin",
                        "color-functions"
                ),
                result.diagnostics().stream()
                        .map(Diagnostic::code)
                        .toList()
        );
    }

    /// Applies silence and fatal policy to wrapped and conditional aliases.
    @Test
    void appliesDiagnosticPolicy() throws Exception {
        var source = """
                .result {
                  list: nth(a b, 1);
                  filter: opacity(red);
                }
                """;
        var silenced = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(SassDeprecation.GLOBAL_BUILTIN),
                Set.of(),
                Set.of()
        );
        assertTrue(globalWarnings(compile(source, silenced)).isEmpty());

        var fatal = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(SassDeprecation.GLOBAL_BUILTIN),
                Set.of()
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(source, fatal)
        );
        assertEquals("global-builtin", failure.primaryDiagnostic().code());
        assertTrue(failure.getMessage().contains(
                "global-builtin deprecation to be fatal"
        ));
    }

    /// Extracts replacement names from global-builtin messages.
    ///
    /// @param result the compilation result
    /// @return replacements in reporting order
    private static @Unmodifiable List<String> replacements(
            CompileResult<String> result
    ) {
        return globalWarnings(result).stream()
                .map(diagnostic -> {
                    var message = diagnostic.message();
                    var start = message.indexOf("Use ") + 4;
                    var end = message.indexOf(" instead.", start);
                    return message.substring(start, end);
                })
                .toList();
    }

    /// Returns diagnostics carrying the global-builtin identifier.
    ///
    /// @param result the completed compilation
    /// @return immutable matching diagnostics in delivery order
    private static @Unmodifiable List<Diagnostic> globalWarnings(
            CompileResult<String> result
    ) {
        return result.diagnostics().stream()
                .filter(diagnostic ->
                        "global-builtin".equals(diagnostic.code())
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

    /// Compiles SCSS with default diagnostic processing.
    ///
    /// @param source SCSS source text
    /// @return the completed CSS result
    /// @throws IOException if an imported source cannot be read
    /// @throws SassCompilationException if compilation fails
    private static CompileResult<String> compile(
            String source
    ) throws IOException, SassCompilationException {
        return compile(source, SassDiagnosticOptions.DEFAULT);
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
                CompileOptions.DEFAULT.withDiagnosticOptions(diagnostics)
        );
    }
}
