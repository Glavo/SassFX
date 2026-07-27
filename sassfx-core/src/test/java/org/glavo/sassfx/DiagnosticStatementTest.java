// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Sass diagnostic statement semantics through the public compiler API.
@NotNullByDefault
final class DiagnosticStatementTest {
    /// Reports debug values in inspect mode and warning values in CSS mode.
    @Test
    void reportsDebugAndWarningValuesInOrder() throws Exception {
        var result = compile(
                """
                        @debug "quoted";
                        @debug null;
                        @debug (key: value);
                        @warn unquoted;
                        @warn #abc;
                        @warn null;
                        a { color: red; }
                        """,
                Syntax.SCSS,
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        a {
                          color: red;
                        }""",
                result.output()
        );
        assertDiagnosticMessages(
                result.diagnostics(),
                List.of(
                        DiagnosticSeverity.DEBUG,
                        DiagnosticSeverity.DEBUG,
                        DiagnosticSeverity.DEBUG,
                        DiagnosticSeverity.WARNING,
                        DiagnosticSeverity.WARNING,
                        DiagnosticSeverity.WARNING
                ),
                List.of("quoted", "null", "(key: value)", "unquoted", "#abc", "")
        );
        assertEquals("@debug \"quoted\"", Objects.requireNonNull(result.diagnostics().get(0).span()).text());
        assertEquals("@warn null", Objects.requireNonNull(result.diagnostics().get(5).span()).text());
    }

    /// Executes diagnostic statements in functions, mixins, content blocks, and control flow.
    @Test
    void executesDiagnosticStatementsInNestedContexts() throws Exception {
        var result = compile(
                """
                        @function measure($value) {
                          @debug $value;
                          @if $value {
                            @warn function;
                          }
                          @return $value;
                        }

                        @mixin emit {
                          @debug mixin;
                          @content;
                        }

                        .box {
                          @include emit {
                            @warn content;
                            width: measure(2px);
                          }
                        }
                        """,
                Syntax.SCSS,
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        .box {
                          width: 2px;
                        }""",
                result.output()
        );
        assertDiagnosticMessages(
                result.diagnostics(),
                List.of(
                        DiagnosticSeverity.DEBUG,
                        DiagnosticSeverity.WARNING,
                        DiagnosticSeverity.DEBUG,
                        DiagnosticSeverity.WARNING
                ),
                List.of("mixin", "content", "2px", "function")
        );
    }

    /// Retains earlier diagnostics when an error rule terminates evaluation.
    @Test
    void terminatesWithInspectMessageAndRetainsEarlierDiagnostics() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                @debug before;
                                @warn first;
                                @error "fatal";
                                @debug after;
                                """,
                        Syntax.SCSS,
                        CssTarget.DEFAULT
                )
        );

        assertEquals("\"fatal\"", failure.getMessage());
        assertEquals("@error \"fatal\"", Objects.requireNonNull(failure.primaryDiagnostic().span()).text());
        assertDiagnosticMessages(
                failure.diagnostics(),
                List.of(
                        DiagnosticSeverity.ERROR,
                        DiagnosticSeverity.DEBUG,
                        DiagnosticSeverity.WARNING
                ),
                List.of("\"fatal\"", "before", "first")
        );
    }

    /// Associates warning serialization failures with the warning expression.
    @Test
    void rejectsNonCssWarningValuesAtExpressionSpan() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("@warn (key: value);", Syntax.SCSS, CssTarget.DEFAULT)
        );

        assertTrue(failure.getMessage().contains("isn't a valid CSS value"));
        assertEquals("(key: value)", Objects.requireNonNull(failure.primaryDiagnostic().span()).text());
    }

    /// Executes module-level diagnostics only during the first module load.
    @Test
    void reportsModuleDiagnosticsOnlyOnce(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        var messages = directory.resolve("_messages.scss");
        Files.writeString(
                input,
                """
                        @use "messages" as first;
                        @use "messages" as second;
                        """
        );
        Files.writeString(
                messages,
                """
                        @debug module;
                        @warn "loaded";
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(input),
                CssTarget.DEFAULT
        );

        assertEquals("", result.output());
        assertDiagnosticMessages(
                result.diagnostics(),
                List.of(DiagnosticSeverity.DEBUG, DiagnosticSeverity.WARNING),
                List.of("module", "loaded")
        );
        assertEquals(2, result.loadedUrls().size());
        assertTrue(result.loadedUrls().contains(input.toAbsolutePath().normalize().toUri()));
        assertTrue(result.loadedUrls().contains(messages.toAbsolutePath().normalize().toUri()));
    }

    /// Supports diagnostic statements in indentation-based Sass input.
    @Test
    void reportsDiagnosticsFromIndentedSass() throws Exception {
        var result = compile(
                """
                        @debug "sass"
                        @warn null
                        a
                          color: red
                        """,
                Syntax.SASS,
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        a {
                          color: red;
                        }""",
                result.output()
        );
        assertDiagnosticMessages(
                result.diagnostics(),
                List.of(DiagnosticSeverity.DEBUG, DiagnosticSeverity.WARNING),
                List.of("sass", "")
        );
    }

    /// Preserves diagnostics when compiling a supported JavaFX BSS stylesheet.
    @Test
    void reportsDiagnosticsForBssOutput() throws Exception {
        var result = compile(
                """
                        @debug bss;
                        Pane { -fx-opacity: 0.5; }
                        """,
                Syntax.SCSS,
                new BssTarget(JavaFXTarget.JAVAFX27)
        );

        assertEquals(1, result.diagnostics().size());
        assertEquals(DiagnosticSeverity.DEBUG, result.diagnostics().get(0).severity());
        assertEquals("bss", result.diagnostics().get(0).message());
        assertTrue(result.output().hasRemaining());
    }

    /// Retains earlier diagnostics when BSS serialization rejects evaluated CSS.
    @Test
    void retainsDiagnosticsForBssSerializationFailure() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                @warn before-bss;
                                Pane + .button { -fx-opacity: 0.5; }
                                """,
                        Syntax.SCSS,
                        new BssTarget(JavaFXTarget.JAVAFX27)
                )
        );

        assertTrue(failure.getMessage().contains("supports only descendant and child"));
        assertDiagnosticMessages(
                failure.diagnostics(),
                List.of(DiagnosticSeverity.ERROR, DiagnosticSeverity.WARNING),
                List.of(failure.getMessage(), "before-bss")
        );
    }

    /// Compiles a string source for the requested syntax and output target.
    ///
    /// @param source the complete Sass source
    /// @param syntax the source syntax
    /// @param target the output target
    /// @param <T>    the output representation
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static <T> CompileResult<T> compile(
            String source,
            Syntax syntax,
            OutputTarget<T> target
    ) throws Exception {
        return new SassCompiler().compile(SassSource.fromString(source, syntax), target);
    }

    /// Verifies ordered diagnostic severities and messages.
    ///
    /// @param diagnostics the actual diagnostics
    /// @param severities  the expected severities
    /// @param messages    the expected messages
    private static void assertDiagnosticMessages(
            List<Diagnostic> diagnostics,
            List<DiagnosticSeverity> severities,
            List<String> messages
    ) {
        assertEquals(severities.size(), diagnostics.size());
        assertEquals(severities.size(), messages.size());
        for (var index = 0; index < diagnostics.size(); index++) {
            assertEquals(severities.get(index), diagnostics.get(index).severity());
            assertEquals(messages.get(index), diagnostics.get(index).message());
        }
    }
}
