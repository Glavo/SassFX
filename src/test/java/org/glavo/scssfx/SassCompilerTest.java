// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /// Compiles multiline parenthesized values and comma-separated selector lists.
    @Test
    void compilesMultilineIndentedValuesAndSelectors() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                $spacing: (
                                  small: 4px,
                                  large: 8px
                                )
                                .item,
                                .other
                                  padding: (
                                    1px 2px
                                  )
                                  margin: map-get($spacing, small)
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        .item, .other {
                          padding: 1px 2px;
                          margin: 4px;
                        }""",
                result.output()
        );
    }

    /// Compiles multiline selector interpolation and preserves loud comments.
    @Test
    void compilesIndentedInterpolationAndComments() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                $item: item
                                $property: color
                                .#{
                                  $item
                                }
                                  // ignored
                                  #{$property}: red
                                  /* loud
                                     note */
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        .item {
                          color: red;
                          /* loud
                             note */
                        }""",
                result.output()
        );
    }
    /// Compiles escaped cross-line strings and interpolation expressions.
    @Test
    void compilesIndentedCrossLineStringsAndInterpolation() throws Exception {
        var source = String.join(
                "\n",
                "$item: item",
                ".item",
                "  content: \"prefix \\",
                "    #{",
                "      $item",
                "    }\""
        );
        var result = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SASS),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        .item {
                          content: "prefix item";
                        }""",
                result.output()
        );
    }
    /// Treats more-indented and same-level silent-comment lines as one comment.
    @Test
    void compilesIndentedSilentCommentContinuation() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                .item
                                  // first line
                                    more detail
                                  // second line
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

    /// Rejects malformed cross-line strings and mismatched delimiters before SCSS parsing.
    @Test
    void rejectsMalformedIndentedContinuations() {
        var quoteFailure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                ".item\n  content: \\\"unterminated\n",
                                Syntax.SASS
                        ),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("Expected closing quote.", quoteFailure.getMessage());
        assertEquals(
                "  content: \\\"unterminated",
                Objects.requireNonNull(quoteFailure.primaryDiagnostic().span()).text()
        );

        var delimiterFailure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                ".item\n  padding: (1px]\n",
                                Syntax.SASS
                        ),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("Mismatched closing delimiter.", delimiterFailure.getMessage());
    }
    /// Compiles a tab-indented child without dropping its first character.
    @Test
    void compilesTabIndentedSass() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(".item\n\tcolor: red\n", Syntax.SASS),
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

    /// Rejects inconsistent child indentation in indented Sass.
    @Test
    void rejectsInconsistentIndentedSass() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                ".item\n  .first\n    color: red\n   .second\n     color: blue\n",
                                Syntax.SASS
                        ),
                        CssTarget.DEFAULT
                )
        );

        assertEquals("Inconsistent indentation; expected 4 columns.", failure.getMessage());
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
                        @supports ((display: $display) and selector(.button))
                                or (not ((display: flex) and (--theme: dark))) {
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
                        $name: theme;
                        $value: dark;
                        @supports (--#{$name}: #{$value}) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }
                        @supports ("--theme": $value) {
                          Pane {
                            -fx-opacity: 0.5;
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
                        }

                        @supports (--theme: dark) {
                          Pane {
                            -fx-opacity: 0.5;
                          }
                        }""",
                result.output()
        );
    }

    /// Compiles grouped supports conditions written in indented Sass syntax.
    @Test
    void compilesIndentedSassSupportsConditions() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                $display: grid
                                @supports ((display: $display) and selector(.button)) or (not (--theme: dark))
                                  Pane
                                    -fx-opacity: 1
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        @supports ((display: grid) and selector(.button)) or (not (--theme:dark)) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }""",
                result.output()
        );
    }

    /// Rejects supports syntax that violates the boolean-condition grammar.
    @Test
    void rejectsInvalidSupportsConditionGrammar() {
        var mixed = assertCompilationFailure(
                "@supports (display: grid) and (color: red) or (width: 1px) {}"
        );
        assertEquals(
                "Operators may not be mixed without a grouping parenthesis.",
                mixed.getMessage()
        );

        var invalidSources = List.of(
                "@supports (display: grid) and not (color: red) {}",
                "@supports (display: grid) and not(color: red) {}",
                "@supports () {}",
                "@supports selector (.button) {}",
                "@supports (1 + 2) {}",
                "@supports (display +: grid) {}"
        );
        for (var source : invalidSources) {
            assertCompilationFailure(source);
        }
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

    /// Bubbles nested unknown at-rules through style rules and resumes later declarations.
    @Test
    void bubblesNestedUnknownAtRulesAndResumesOuterRulesInOrder() throws Exception {
        var result = compile(
                """
                        .button {
                          color: one;
                          @layer theme {
                            color: two;
                            .icon {
                              color: nested;
                            }
                          }
                          color: three;
                          @keyframes pulse {
                            from { opacity: 0; }
                            to { opacity: 1; }
                          }
                          color: four;
                          @supports (display: grid) {
                            color: five;
                          }
                        }
                        """
        );

        assertEquals(
                """
                        .button {
                          color: one;
                        }
                        @layer theme {
                          .button {
                            color: two;
                          }
                          .button .icon {
                            color: nested;
                          }
                        }
                        .button {
                          color: three;
                        }
                        @keyframes pulse {
                          from {
                            opacity: 0;
                          }
                          to {
                            opacity: 1;
                          }
                        }
                        .button {
                          color: four;
                        }
                        @supports (display: grid) {
                          .button {
                            color: five;
                          }
                        }""",
                result.output()
        );
    }

    /// Serializes plain-CSS nesting through the JavaFX textual CSS target.
    @Test
    void serializesNativeCssNestingForJavaFxCss() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                .parent {
                                  color: blue;
                                  .child {
                                    color: red;
                                  }
                                  &:hover {
                                    color: green;
                                  }
                                }
                                """,
                        Syntax.CSS
                ),
                JavaFXCssTarget.DEFAULT
        );

        assertEquals(
                """
                        .parent {
                          color: blue;
                          .child {
                            color: red;
                          }
                          &:hover {
                            color: green;
                          }
                        }""",
                result.output()
        );
    }

    /// Keeps unknown at-rules nested once native CSS nesting is already active.
    @Test
    void retainsUnknownAtRulesUnderNativeCssNesting() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                .parent {
                                  .child {
                                    color: red;
                                    @layer theme {
                                      color: blue;
                                    }
                                  }
                                }
                                """,
                        Syntax.CSS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        .parent {
                          .child {
                            color: red;
                            @layer theme {
                              color: blue;
                            }
                          }
                        }""",
                result.output()
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
