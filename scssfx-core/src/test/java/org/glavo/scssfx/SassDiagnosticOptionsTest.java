// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies logger delivery and deprecation processing options.
@NotNullByDefault
final class SassDiagnosticOptionsTest {
    /// Delivers Sass-compatible logger payloads while retaining diagnostics.
    @Test
    void deliversStructuredLoggerEvents() throws Exception {
        var events = new ArrayList<SassLogEvent>();
        var options = options(new SassDiagnosticOptions(events::add));

        var result = compile(
                """
                        @debug "debug";
                        @warn blue;
                        a { value: (1/2); }
                        """,
                options
        );

        assertEquals(3, result.diagnostics().size());
        assertEquals(3, events.size());
        assertEquals(DiagnosticSeverity.DEBUG, events.get(0).diagnostic().severity());
        assertNotNull(events.get(0).diagnostic().span());
        assertTrue(events.get(0).sassTrace().isEmpty());
        assertEquals(DiagnosticSeverity.WARNING, events.get(1).diagnostic().severity());
        assertEquals(null, events.get(1).diagnostic().span());
        assertFalse(events.get(1).sassTrace().isEmpty());
        assertEquals(SassDeprecation.SLASH_DIV, events.get(2).deprecation());
        assertEquals("slash-div", events.get(2).diagnostic().code());
        assertFalse(events.get(2).sassTrace().isEmpty());
    }

    /// Warns for named colors only in interpolation that produces CSS syntax.
    @Test
    void warnsForNamedColorInterpolationInCssContexts() throws Exception {
        var result = compile(
                """
                        $ordinary-string: "#{white}";
                        /* #{gray} */
                        #{blue} { #{red}: value; }
                        @unknown #{green};
                        @unknown #{rgba(0, 0, 0, 0)};
                        @media (#{lime}) { a { value: ok; } }
                        """,
                options(SassDiagnosticOptions.DEFAULT)
        );

        var warnings = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.message().startsWith(
                        "You probably don't mean to use the color value"
                ))
                .toList();
        assertEquals(5, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).message().contains("color value blue"));
        assertTrue(warnings.get(1).message().contains("color value red"));
        assertTrue(warnings.get(2).message().contains("color value green"));
        assertTrue(warnings.get(3).message().contains("color value transparent"));
        assertTrue(warnings.get(4).message().contains("color value lime"));
        assertTrue(warnings.stream().allMatch(
                diagnostic -> diagnostic.severity()
                        == DiagnosticSeverity.WARNING
        ));
    }

    /// Silences selected deprecations and promotes fatal categories to errors.
    @Test
    void appliesSilenceAndFatalPrecedence() throws Exception {
        var silenced = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                false,
                Set.of(SassDeprecation.SLASH_DIV),
                Set.of(),
                Set.of()
        );
        assertTrue(
                compile("a { value: (1/2); }", options(silenced))
                        .diagnostics()
                        .isEmpty()
        );

        var fatal = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                false,
                Set.of(SassDeprecation.SLASH_DIV),
                Set.of(SassDeprecation.SLASH_DIV),
                Set.of()
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("a { value: (1/2); }", options(fatal))
        );

        assertEquals("slash-div", failure.primaryDiagnostic().code());
        assertTrue(failure.getMessage().contains("deprecation to be fatal"));
        assertNotNull(failure.primaryDiagnostic().span());
        assertEquals(
                "Ignoring setting to silence slash-div deprecation, since it "
                        + "has also been made fatal.",
                failure.diagnostics().get(1).message()
        );
    }

    /// Processes evaluation-time function-unit deprecations through the same
    /// silence and fatal pipeline as parser diagnostics.
    @Test
    void processesFunctionUnitDeprecations() throws Exception {
        var ordinary = compile(
                "@use 'sass:math'; a { value: math.random(1px); }",
                options(SassDiagnosticOptions.DEFAULT)
        );
        assertEquals(1, ordinary.diagnostics().size());
        var warning = ordinary.diagnostics().get(0);
        assertEquals("function-units", warning.code());
        assertTrue(warning.message().contains(
                "math.random() will no longer ignore $limit units (1px)"
        ));
        assertTrue(warning.message().contains(
                "math.random(math.div($limit, 1px)) * 1px"
        ));

        var silenced = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                false,
                Set.of(SassDeprecation.FUNCTION_UNITS),
                Set.of(),
                Set.of()
        );
        assertTrue(compile(
                "@use 'sass:math'; a { value: math.random(1px); }",
                options(silenced)
        ).diagnostics().isEmpty());

        var fatal = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                false,
                Set.of(),
                Set.of(SassDeprecation.FUNCTION_UNITS),
                Set.of()
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        "@use 'sass:math'; a { value: math.random(1px); }",
                        options(fatal)
                )
        );
        assertEquals("function-units", failure.primaryDiagnostic().code());
        assertTrue(failure.getMessage().contains(
                "function-units deprecation to be fatal"
        ));
    }

    /// Limits each deprecation category to five events and summarizes omissions.
    @Test
    void limitsRepeatedDeprecationsAndSupportsVerboseMode() throws Exception {
        var source = repeatedSlashDivision(7);
        var limited = compile(source, options(SassDiagnosticOptions.DEFAULT));

        assertEquals(6, limited.diagnostics().size());
        assertEquals(
                5,
                limited.diagnostics().stream()
                        .filter(diagnostic -> "slash-div".equals(diagnostic.code()))
                        .count()
        );
        assertEquals(
                "2 repetitive deprecation warnings omitted.\n"
                        + "Run in verbose mode to see all warnings.",
                limited.diagnostics().get(5).message()
        );

        var verbose = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(),
                Set.of()
        );
        var complete = compile(source, options(verbose));
        assertEquals(7, complete.diagnostics().size());
        assertTrue(complete.diagnostics().stream().allMatch(
                diagnostic -> "slash-div".equals(diagnostic.code())
        ));
    }

    /// Omits repetition summaries when compilation fails.
    @Test
    void doesNotSummarizeFailedCompilation() {
        var source = repeatedSlashDivision(7) + "@error failed;";
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(source, options(SassDiagnosticOptions.DEFAULT))
        );

        assertEquals(6, failure.diagnostics().size());
        assertEquals(DiagnosticSeverity.ERROR, failure.diagnostics().get(0).severity());
        assertFalse(failure.diagnostics().stream().anyMatch(
                diagnostic -> diagnostic.message().contains("warnings omitted")
        ));
    }

    /// Reports obsolete and contradictory option selections as ordinary warnings.
    @Test
    void validatesDeprecationSelections() throws Exception {
        var events = new ArrayList<SassLogEvent>();
        var diagnosticOptions = new SassDiagnosticOptions(
                events::add,
                false,
                false,
                Set.of(
                        SassDeprecation.USER_AUTHORED,
                        SassDeprecation.MIXED_DECLS
                ),
                Set.of(SassDeprecation.TYPE_FUNCTION),
                Set.of(SassDeprecation.IMPORT)
        );
        var result = compile("", options(diagnosticOptions));

        assertEquals(4, result.diagnostics().size());
        assertEquals(4, events.size());
        assertTrue(result.diagnostics().stream().allMatch(
                diagnostic -> diagnostic.severity() == DiagnosticSeverity.WARNING
        ));
        assertTrue(result.diagnostics().stream().anyMatch(
                diagnostic -> diagnostic.message().contains(
                        "User-authored deprecations should not be silenced"
                )
        ));
        assertTrue(result.diagnostics().stream().anyMatch(
                diagnostic -> diagnostic.message().contains(
                        "import is not a future deprecation"
                )
        ));
    }

    /// Exposes the complete pinned deprecation registry metadata.
    @Test
    void exposesPinnedDeprecationMetadata() {
        assertEquals(
                SassDeprecation.ADJACENT_COMPOUNDS,
                SassDeprecation.fromId("adjacent-compounds")
        );
        assertEquals(null, SassDeprecation.fromId("unknown"));
        assertEquals(
                SassDeprecationStatus.OBSOLETE,
                SassDeprecation.MIXED_DECLS.status()
        );
        assertEquals("1.92.0", SassDeprecation.MIXED_DECLS.obsoleteIn());
        assertTrue(
                SassDeprecation.forVersion(1, 79, 0)
                        .contains(SassDeprecation.COLOR_FUNCTIONS)
        );
        assertFalse(
                SassDeprecation.forVersion(1, 79, 0)
                        .contains(SassDeprecation.IMPORT)
        );
        assertFalse(
                SassDeprecation.forVersion(1, 79, 0)
                        .contains(SassDeprecation.MIXED_DECLS)
        );
        assertNotNull(SassDeprecation.GLOBAL_BUILTIN.description());
    }

    /// Keeps repetition state isolated across concurrent compilations.
    @Test
    void isolatesConcurrentCompilationState() throws Exception {
        var events = new ConcurrentLinkedQueue<SassLogEvent>();
        var sharedOptions = options(new SassDiagnosticOptions(events::add));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(
                    () -> compile(repeatedSlashDivision(6), sharedOptions)
            );
            var second = executor.submit(
                    () -> compile(repeatedSlashDivision(6), sharedOptions)
            );

            assertEquals(6, first.get(10, TimeUnit.SECONDS).diagnostics().size());
            assertEquals(6, second.get(10, TimeUnit.SECONDS).diagnostics().size());
            assertEquals(12, events.size());
        } finally {
            executor.shutdownNow();
        }
    }

    /// Propagates logger runtime failures without swallowing their identity.
    @Test
    void propagatesLoggerFailure() {
        var expected = new IllegalStateException("logger failed");
        var diagnosticOptions = new SassDiagnosticOptions(event -> {
            throw expected;
        });

        var actual = assertThrows(
                IllegalStateException.class,
                () -> compile("@debug value;", options(diagnosticOptions))
        );
        assertEquals(expected, actual);
    }

    /// Creates source containing the requested number of distinct slash divisions.
    private static String repeatedSlashDivision(int count) {
        var source = new StringBuilder();
        for (var index = 0; index < count; index++) {
            source.append("$value-")
                    .append(index)
                    .append(": (1/2);\n");
        }
        return source.toString();
    }

    /// Creates compile options with explicit diagnostic processing.
    private static CompileOptions options(SassDiagnosticOptions diagnostics) {
        return new CompileOptions(
                false,
                List.of(),
                null,
                List.of(),
                List.of(),
                diagnostics
        );
    }

    /// Compiles SCSS using the supplied options.
    private static CompileResult<String> compile(
            String source,
            CompileOptions options
    ) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT,
                options
        );
    }
}
