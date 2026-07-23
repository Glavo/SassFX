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

/// Verifies the public string CSS compilation paths.
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

    /// Compiles parent selectors nested inside selector-taking pseudo arguments.
    @Test
    void compilesRecursivePseudoParentSelectors() throws Exception {
        var result = compile(
                """
                        .parent {
                          :not(&) { color: red; }
                          :nth-child(2n + 1 of &) { color: blue; }
                          :has(> &) { color: green; }
                        }
                        """
        );

        assertEquals(
                """
                        :not(.parent) {
                          color: red;
                        }
                        :nth-child(2n + 1 of .parent) {
                          color: blue;
                        }
                        :has(> .parent) {
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

    /// Compiles compressed CSS while omitting ordinary comments.
    @Test
    void compilesCompressedCssAndRetainsPreservedComments() throws Exception {
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString(
                        """
                                /* omitted */
                                .empty {
                                  /* omitted */
                                }
                                a {
                                  color: red;
                                  /*! retained */
                                  margin: 0;
                                }
                                """,
                        Syntax.SCSS
                ),
                new CssTarget(OutputStyle.COMPRESSED, true)
        );

        assertEquals("a{color:red;/*! retained */margin:0}", result.output());
    }

    /// Omits charset declarations from compressed CSS output.
    @Test
    void omitsCharsetForCompressedCss() throws Exception {
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString("a { content: \"你好\"; }", Syntax.SCSS),
                new CssTarget(OutputStyle.COMPRESSED, true)
        );

        assertEquals("a{content:\"你好\"}", result.output());
    }

    /// Compiles a JavaFX CSS target without a JavaFX runtime dependency.
    @Test
    void compilesJavaFxCssTargets() throws Exception {
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString("a { -fx-text-fill: #f00; }", Syntax.SCSS),
                new JavaFXCssTarget(
                        JavaFXCompatibility.JAVA_FX_27,
                        OutputStyle.COMPRESSED
                )
        );

        assertEquals("a{-fx-text-fill:#f00}", result.output());
        assertNull(result.sourceMap());
    }

    /// Compiles top-level font-face rules with evaluated Sass descriptor values.
    @Test
    void compilesFontFaceRules() throws Exception {
        var result = compile(
                """
                        $family: "Example";
                        @font-face {
                          font-family: $family;
                          font-weight: 600;
                          src: url("https://example.invalid/fonts/example.woff2") format("woff2"), local("Example Local"), ExampleReference;
                        }
                        Pane {
                          -fx-font-family: $family;
                        }
                        """
        );

        assertEquals(
                """
                        @font-face {
                          font-family: "Example";
                          font-weight: 600;
                          src: url("https://example.invalid/fonts/example.woff2") format("woff2"), local("Example Local"), ExampleReference;
                        }

                        Pane {
                          -fx-font-family: "Example";
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

    /// Compiles the basic indentation-based Sass syntax.
    @Test
    void compilesIndentedSassSyntax() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                .item
                                  color: red
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .item {
                          color: red;
                        }""",
                result.output()
        );
    }

    /// Compiles nested selectors, nested properties, variables, and indented mixins.
    @Test
    void compilesNestedIndentedSassSyntax() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                $color: red
                                =accent($value)
                                  color: $value
                                .item
                                  +accent($color)
                                  font:
                                    family: serif
                                  &:hover
                                    color: blue
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .item {
                          color: red;
                          font-family: serif;
                        }
                        .item:hover {
                          color: blue;
                        }""",
                result.output()
        );
    }

    /// Compiles media rules for standard and JavaFX textual CSS targets.
    @Test
    void compilesMediaRulesForCssAndJavaFxCssTargets() throws Exception {
        var source = """
                $medium: screen;
                @media #{$medium} and (min-width: 600px) {
                  Pane {
                    -fx-opacity: 1;
                  }
                }
                Pane {
                  @media (hover) {
                    -fx-opacity: 0.5;
                  }
                }
                """;

        var css = compile(source);
        assertEquals(
                """
                        @media screen and (min-width: 600px) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }

                        @media (hover) {
                          Pane {
                            -fx-opacity: 0.5;
                          }
                        }""",
                css.output()
        );

        var javaFx = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                new JavaFXCssTarget(
                        JavaFXCompatibility.JAVA_FX_27,
                        OutputStyle.COMPRESSED
                )
        );
        assertEquals(
                "@media screen and (min-width: 600px){Pane{-fx-opacity:1}}"
                        + "@media(hover){Pane{-fx-opacity:0.5}}",
                javaFx.output()
        );
    }

    /// Compiles supports rules for standard and JavaFX textual CSS targets.
    @Test
    void compilesSupportsRulesForCssAndJavaFxCssTargets() throws Exception {
        var source = """
                $display: grid;
                @supports (display: #{$display}) {
                  Pane {
                    -fx-opacity: 1;
                  }
                }
                Pane {
                  @supports not (display: block) {
                    -fx-opacity: 0.5;
                  }
                }
                """;

        var css = compile(source);
        assertEquals(
                """
                        @supports (display: grid) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }

                        @supports not (display: block) {
                          Pane {
                            -fx-opacity: 0.5;
                          }
                        }""",
                css.output()
        );

        var javaFx = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                new JavaFXCssTarget(
                        JavaFXCompatibility.JAVA_FX_27,
                        OutputStyle.COMPRESSED
                )
        );
        assertEquals(
                "@supports(display: grid){Pane{-fx-opacity:1}}"
                        + "@supports not (display: block){Pane{-fx-opacity:0.5}}",
                javaFx.output()
        );
    }

    /// Evaluates SassScript values in supports declarations and preserves
    /// boolean grouping during canonical serialization.
    @Test
    void evaluatesStructuredSupportsConditions() throws Exception {
        var result = compile(
                """
                        $display: grid;
                        @supports (display: $display) and selector(.button)
                                or not ((display: flex) and (--theme: dark)) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }
                        """
        );

        assertEquals(
                """
                        @supports ((display: grid) and selector(.button)) or (not ((display: flex) and (--theme:dark))) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }""",
                result.output()
        );
    }

    /// Serializes custom-property supports declarations without a colon space.
    @Test
    void serializesCustomPropertySupportsDeclarations() throws Exception {
        var result = compile(
                """
                        $value: dark;
                        @supports (--theme: #{$value}) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }
                        """
        );

        assertEquals(
                """
                        @supports (--theme:dark) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }""",
                result.output()
        );
    }
    /// Merges compatible nested media queries while preserving source order.
    @Test
    void mergesNestedMediaQueriesAndResumesOuterRulesInOrder() throws Exception {
        var result = compile(
                """
                        @media screen, print {
                          .button {
                            color: one;
                            @media (min-width: 600px) {
                              color: two;
                            }
                            color: three;
                          }
                        }
                        """
        );

        assertEquals(
                """
                        @media screen, print {
                          .button {
                            color: one;
                          }
                        }
                        @media screen and (min-width: 600px), print and (min-width: 600px) {
                          .button {
                            color: two;
                          }
                        }
                        @media screen, print {
                          .button {
                            color: three;
                          }
                        }""",
                result.output()
        );
    }

    /// Retains an unrepresentable nested media intersection structurally.
    @Test
    void retainsUnrepresentableNestedMediaQueries() throws Exception {
        var result = compile(
                """
                        @media (hover) or (pointer: fine) {
                          .button {
                            @media (min-width: 30px) {
                              -fx-opacity: 1;
                            }
                          }
                        }
                        """
        );

        assertEquals(
                """
                        @media (hover) or (pointer: fine) {
                          @media (min-width: 30px) {
                            .button {
                              -fx-opacity: 1;
                            }
                          }
                        }""",
                result.output()
        );
    }

    /// Retains nested supports rules and resumes outer declarations in source order.
    @Test
    void retainsNestedSupportsRulesAndResumesOuterRulesInOrder() throws Exception {
        var result = compile(
                """
                        @media screen {
                          .button {
                            -fx-opacity: 0.25;
                            @supports (display: grid) {
                              -fx-opacity: 0.5;
                              @supports selector(.button:hover) {
                                -fx-opacity: 0.75;
                              }
                            }
                            -fx-opacity: 1;
                          }
                        }
                        """
        );

        assertEquals(
                """
                        @media screen {
                          .button {
                            -fx-opacity: 0.25;
                          }
                          @supports (display: grid) {
                            .button {
                              -fx-opacity: 0.5;
                            }
                            @supports selector(.button:hover) {
                              .button {
                                -fx-opacity: 0.75;
                              }
                            }
                          }
                          .button {
                            -fx-opacity: 1;
                          }
                        }""",
                result.output()
        );
    }

    /// Rejects media rules for BSS until their binary representation is implemented.
    @Test
    void rejectsMediaRulesForBss() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "@media screen { Pane { -fx-opacity: 1; } }",
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals("BSS output doesn't support @media rules.", failure.getMessage());
    }

    /// Rejects supports rules for BSS until their binary representation is implemented.
    @Test
    void rejectsSupportsRulesForBss() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "@supports (display: grid) { Pane { -fx-opacity: 1; } }",
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals("BSS output doesn't support @supports rules.", failure.getMessage());
    }

    /// Rejects unsupported at-rules and conditional contexts with unsafe CSS semantics.
    @Test
    void rejectsUnsupportedAtRulesAndUnsafeConditionalNesting() {
        assertEquals(
                "This stylesheet statement is not available.",
                assertCompilationFailure("@import \"theme.css\";").getMessage()
        );
        assertEquals(
                "This at-rule may only be used at the stylesheet root.",
                assertCompilationFailure("Pane { @font-face { src: local(Example); } }").getMessage()
        );
        assertEquals(
                "Supports rules may not be used within nested declarations.",
                assertCompilationFailure(
                        "Pane { font: { @supports (display: grid) {} } }"
                ).getMessage()
        );
        assertEquals(
                "Expected supports condition.",
                assertCompilationFailure(
                        "$condition: \"\"; @supports #{$condition} { Pane {} }"
                ).getMessage()
        );
        assertEquals(
                "@font-face rules may only be used at the stylesheet root.",
                assertCompilationFailure(
                        "@supports (display: grid) { @font-face { src: local(Example); } }"
                ).getMessage()
        );
    }

    /// Rejects unsupported source-map generation.
    @Test
    void rejectsUnsupportedOptions() {
        var compiler = new SassCompiler();
        var source = SassSource.fromString("a { color: red; }", Syntax.SCSS);

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
