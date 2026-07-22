// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the public string-to-expanded-CSS compilation path.
@NotNullByDefault
final class SassCompilerTest {
    /// Compiles variables, arithmetic, nesting, nested properties, and comments.
    @Test
    void compilesSupportedScssSubsetToExpandedCss() throws Exception {
        var result = compile(
                """
                        /* loud */
                        $color: #f00;
                        a {
                          color: $color;
                          width: 1px + 2px;
                          b { margin: 1; }
                          font: {
                            family: serif;
                            size: 12px;
                          }
                        }
                        /*! preserved */
                        """
        );

        assertEquals(
                """
                        /* loud */
                        a {
                          color: #f00;
                          width: 3px;
                        }
                        a b {
                          margin: 1;
                        }
                        a {
                          font-family: serif;
                          font-size: 12px;
                        }

                        /*! preserved */""",
                result.output()
        );
        assertNull(result.sourceMap());
        assertEquals(Set.of(), result.loadedUrls());
        assertTrue(result.diagnostics().isEmpty());
    }

    /// Compiles parent-selector references and comma nesting with string rules.
    @Test
    void nestsParentSelectorsAndSelectorLists() throws Exception {
        var result = compile(
                """
                        a, b {
                          c, d { color: red; }
                        }
                        .parent {
                          &-child { color: blue; }
                          &:hover { color: green; }
                        }
                        """
        );

        assertEquals(
                """
                        a c, b c, a d, b d {
                          color: red;
                        }

                        .parent-child {
                          color: blue;
                        }
                        .parent:hover {
                          color: green;
                        }""",
                result.output()
        );
    }

    /// Emits a charset prefix for non-ASCII expanded CSS when requested.
    @Test
    void emitsCharsetForNonAsciiExpandedCss() throws Exception {
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString("a { content: \"你好\"; }", Syntax.SCSS),
                new CssTarget(OutputStyle.EXPANDED, true)
        );

        assertEquals(
                """
                        @charset "UTF-8";
                        a {
                          content: "你好";
                        }""",
                result.output()
        );
    }

    /// Records the canonical URL of a string source in loaded URLs.
    @Test
    void recordsCanonicalUrlForStringSources() throws Exception {
        var url = URI.create("memory:style.scss");
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString("a { color: red; }", Syntax.SCSS, url),
                CssTarget.DEFAULT
        );

        assertEquals(Set.of(url), result.loadedUrls());
    }

    /// Compiles a file source and records its absolute file URL.
    @Test
    void compilesFileSources(@TempDir Path directory) throws Exception {
        var path = directory.resolve("style.scss");
        Files.writeString(path, "a { color: blue; }");

        var compiler = new SassCompiler();
        var result = compiler.compile(SassSource.fromFile(path), CssTarget.DEFAULT);

        assertEquals(
                """
                        a {
                          color: blue;
                        }""",
                result.output()
        );
        assertEquals(Set.of(path.toAbsolutePath().normalize().toUri()), result.loadedUrls());
    }

    /// Propagates parse failures as structured compilation exceptions.
    @Test
    void propagatesParseFailures() {
        var failure = assertCompilationFailure("a { color: ; }");
        assertEquals(DiagnosticSeverity.ERROR, failure.primaryDiagnostic().severity());
        assertTrue(Objects.requireNonNull(failure.primaryDiagnostic().span()).text().length() > 0);
    }

    /// Propagates evaluation failures with exact spans.
    @Test
    void propagatesEvaluationFailures() {
        var failure = assertCompilationFailure("a { color: $missing; }");
        assertEquals("Undefined variable.", failure.getMessage());
        assertEquals("$missing", Objects.requireNonNull(failure.primaryDiagnostic().span()).text());
        assertEquals("root stylesheet", failure.sassTrace().get(0).member());
    }

    /// Propagates serialization failures for values that are not plain CSS.
    @Test
    void propagatesSerializationFailures() {
        var failure = assertCompilationFailure("a { color: (a: 1); }");
        assertEquals("(a: 1) isn't a valid CSS value.", failure.getMessage());
        assertEquals("(a: 1)", Objects.requireNonNull(failure.primaryDiagnostic().span()).text());
    }

    /// Rejects unsupported syntaxes instead of silently degrading.
    @Test
    void rejectsUnsupportedSyntaxes() {
        var compiler = new SassCompiler();
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compiler.compile(
                        SassSource.fromString("a\n  color: red", Syntax.SASS),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("Indented Sass syntax isn't supported.", failure.getMessage());
    }

    /// Rejects non-CSS targets and compressed CSS for this vertical path.
    @Test
    void rejectsUnsupportedTargetsAndOptions() {
        var compiler = new SassCompiler();
        var source = SassSource.fromString("a { color: red; }", Syntax.SCSS);

        assertEquals(
                "JavaFX CSS output isn't supported.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compiler.compile(source, JavaFxCssTarget.DEFAULT)
                ).getMessage()
        );
        assertEquals(
                "Compressed CSS output isn't supported.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compiler.compile(source, new CssTarget(OutputStyle.COMPRESSED, true))
                ).getMessage()
        );
        assertEquals(
                "Source map generation isn't supported.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compiler.compile(
                                source,
                                CssTarget.DEFAULT,
                                new CompileOptions(true, java.util.List.of())
                        )
                ).getMessage()
        );
    }

    /// Surfaces root file IO failures as IOException rather than compilation errors.
    @Test
    void surfacesMissingFileAsIoException(@TempDir Path directory) {
        var missing = directory.resolve("missing.scss");
        var compiler = new SassCompiler();
        assertThrows(
                IOException.class,
                () -> compiler.compile(SassSource.fromFile(missing), CssTarget.DEFAULT)
        );
    }

    /// Preserves evaluation warnings and deprecations on successful compilation.
    @Test
    void preservesEvaluationDiagnostics() throws Exception {
        var result = compile(
                """
                        a {
                          $x: 1 !global;
                          width: (1/2);
                        }
                        """
        );

        assertEquals(
                """
                        a {
                          width: 0.5;
                        }""",
                result.output()
        );
        assertEquals(2, result.diagnostics().size());
        assertEquals(DiagnosticSeverity.DEPRECATION, result.diagnostics().get(0).severity());
        assertEquals("new-global", result.diagnostics().get(0).code());
        assertEquals(DiagnosticSeverity.DEPRECATION, result.diagnostics().get(1).severity());
        assertEquals("slash-div", result.diagnostics().get(1).code());
    }

    /// Compiles a string source with default options.
    ///
    /// @param source the SCSS source text
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static CompileResult<String> compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
    }

    /// Compiles invalid source and returns the structured failure.
    ///
    /// @param source the invalid SCSS source text
    /// @return the compilation exception
    private static SassCompilationException assertCompilationFailure(String source) {
        var compiler = new SassCompiler();
        return assertThrows(
                SassCompilationException.class,
                () -> compiler.compile(
                        SassSource.fromString(source, Syntax.SCSS),
                        CssTarget.DEFAULT
                )
        );
    }
}
