// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                        a c, a d, b c, b d {
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
                        :nth-child(2n+1 of .parent) {
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

    /// Emits a UTF-8 BOM for non-ASCII compressed CSS when requested.
    @Test
    void emitsBomForCompressedCss() throws Exception {
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString("a { content: \"你好\"; }", Syntax.SCSS),
                new CssTarget(OutputStyle.COMPRESSED, true)
        );

        assertEquals("\uFEFFa{content:\"你好\"}", result.output());
    }

    /// Compiles a JavaFX CSS target without a JavaFX runtime dependency.
    @Test
    void compilesJavaFXCssTargets() throws Exception {
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString("a { -fx-text-fill: #f00; }", Syntax.SCSS),
                new JavaFXCssTarget(
                        JavaFXTarget.JAVAFX27,
                        OutputStyle.COMPRESSED
                )
        );

        assertEquals("a{-fx-text-fill:red}", result.output());
        assertNull(result.sourceMap());
    }

    /// Restores legacy JavaFX gradient grouping only for JavaFX CSS targets.
    @Test
    void serializesDirectLegacyJavaFXGradients() throws Exception {
        var source = SassSource.fromString(
                """
                        Pane {
                          -fx-background-color:
                              linear (0%,0%) to (100%,100%) stops (0,red) (1,blue) repeat,
                              radial focus-angle 45deg center (50%,50%) 25px stops (0,white) (1,black) no-cycle;
                          -fx-text-fill:
                              ladder -fx-base stops (0,black) (1,derive(-fx-base, 20%));
                        }
                        """,
                Syntax.SCSS
        );
        var compiler = new SassCompiler();
        var expanded = compiler.compile(
                source,
                new JavaFXCssTarget(
                        JavaFXTarget.JAVAFX27,
                        OutputStyle.EXPANDED
                )
        ).output();
        var compressed = compiler.compile(
                source,
                new JavaFXCssTarget(
                        JavaFXTarget.JAVAFX27,
                        OutputStyle.COMPRESSED
                )
        ).output();
        var ordinaryCss = compiler.compile(
                source,
                new CssTarget(OutputStyle.EXPANDED, true)
        ).output();

        assertTrue(expanded.contains(
                "linear (0%, 0%) to (100%, 100%)"
        ));
        assertTrue(expanded.contains(
                "radial focus-angle 45deg center (50%, 50%)"
        ));
        assertTrue(compressed.contains(
                "stops (0, red) (1, blue) repeat"
        ));
        assertTrue(compressed.contains(
                "stops (0, white) (1, black) no-cycle"
        ));
        assertTrue(expanded.contains(
                "ladder -fx-base stops (0, black) (1, derive(-fx-base, 20%))"
        ));
        assertFalse(ordinaryCss.contains("linear ("));
        assertFalse(ordinaryCss.contains("radial focus-angle 45deg center ("));
        assertFalse(ordinaryCss.contains("ladder -fx-base stops ("));
    }

    /// Preserves declaration meaning across JavaFX compatibility levels.
    @Test
    void validatesVersionSpecificJavaFXDeclarations() throws Exception {
        var compiler = new SassCompiler();
        var transitionSource = SassSource.fromString(
                "Pane { transition: -fx-opacity 100ms linear; }",
                Syntax.SCSS
        );
        var javaFX17 = new JavaFXCssTarget(
                JavaFXTarget.JAVAFX17,
                OutputStyle.COMPRESSED
        );
        var javaFX27 = new JavaFXCssTarget(
                JavaFXTarget.JAVAFX27,
                OutputStyle.COMPRESSED
        );

        var transitionFailure = assertThrows(
                SassCompilationException.class,
                () -> compiler.compile(transitionSource, javaFX17)
        );
        assertEquals(
                "JavaFX 17 CSS does not support property transition.",
                transitionFailure.getMessage()
        );
        assertEquals(
                "Pane{transition:-fx-opacity 100ms linear}",
                compiler.compile(transitionSource, javaFX27).output()
        );

        var redBlendMode = SassSource.fromString(
                "Pane { -fx-blend-mode: red; }",
                Syntax.SCSS
        );
        var blendFailure = assertThrows(
                SassCompilationException.class,
                () -> compiler.compile(redBlendMode, javaFX17)
        );
        assertEquals(
                "JavaFX 17 CSS does not support -fx-blend-mode value red.",
                blendFailure.getMessage()
        );
        assertEquals(
                "Pane{-fx-blend-mode:red}",
                compiler.compile(redBlendMode, javaFX27).output()
        );

        assertEquals(
                "Pane{-fx-blend-mode:multiply}",
                compiler.compile(
                        SassSource.fromString(
                                "Pane { -fx-blend-mode: multiply; }",
                                Syntax.SCSS
                        ),
                        javaFX17
                ).output()
        );
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
                                  border: 1px
                                    style: solid
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
                          border: 1px;
                          border-style: solid;
                        }
                        .item:hover {
                          color: blue;
                        }""",
                result.output()
        );
    }

    /// Treats spaced {@code + a} as a next-sibling combinator, not an include.
    @Test
    void treatsSpacedPlusAsSelectorCombinatorInIndentedSass() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @mixin a
                                  b: c
                                d
                                  + a
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );
        assertEquals("", result.output().strip());
    }

    /// Compiles indented control flow, content blocks, and valued nested properties.
    @Test
    void compilesIndentedControlFlowAndContentBlocks() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                =paint($color)
                                  @content
                                  color: $color
                                $on: true
                                .item
                                  @if $on
                                    +paint(red)
                                      opacity: 1
                                  @else
                                    color: blue
                                  @for $i from 1 through 2
                                    .n-#{$i}
                                      order: $i
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .item {
                          opacity: 1;
                          color: red;
                        }
                        .item .n-1 {
                          order: 1;
                        }
                        .item .n-2 {
                          order: 2;
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
                        .item,
                        .other {
                          padding: 1px 2px;
                          margin: 4px;
                        }""",
                result.output()
        );
    }

    /// Compiles multiline selector interpolation and normalizes loud comments.
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
                          * note */
                        }""",
                result.output()
        );
    }

    /// Preserves physical indentation after an escaped newline in a quoted string.
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
                          content: "prefix     item";
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
        assertEquals("Expected \".", quoteFailure.getMessage());
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
        assertEquals("expected \")\".", delimiterFailure.getMessage());
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

        assertEquals("Inconsistent indentation, expected 4 spaces.", failure.getMessage());
    }

    /// Compiles JavaFX 27 media conditions and rejects them for JavaFX 17.
    @Test
    void compilesMediaRulesForCssAndJavaFXCssTargets() throws Exception {
        var source = """
                $minimum: 600px;
                @media (min-width: #{$minimum}) {
                  Pane {
                    -fx-opacity: 1;
                  }
                }
                Pane {
                  @media (orientation: landscape) {
                    -fx-opacity: 0.5;
                  }
                }
                """;

        var css = compile(source);
        assertEquals(
                """
                        @media (min-width: 600px) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }
                        @media (orientation: landscape) {
                          Pane {
                            -fx-opacity: 0.5;
                          }
                        }""",
                css.output()
        );

        var javaFX = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                new JavaFXCssTarget(
                        JavaFXTarget.JAVAFX27,
                        OutputStyle.COMPRESSED
                )
        );
        assertEquals(
                "@media (min-width: 600px){Pane{-fx-opacity:1}}"
                        + "@media (orientation: landscape){Pane{-fx-opacity:0.5}}",
                javaFX.output()
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(source, Syntax.SCSS),
                        new JavaFXCssTarget(
                                JavaFXTarget.JAVAFX17,
                                OutputStyle.COMPRESSED
                        )
                )
        );
        assertTrue(failure.getMessage().contains("JavaFX 17"), failure.getMessage());
    }

    /// Validates transition functions after SassScript evaluation and serialization.
    @Test
    void compilesEvaluatedJavaFXTransitions() throws Exception {
        var source = """
                $duration: 120ms;
                $curve: cubic-bezier(0.1, -2, 0.9, 3);
                Pane {
                  transition: -fx-opacity $duration $curve,
                              -fx-rotate ($duration * 2) steps(3, jump-both) 10ms !important;
                  transition-timing-function: linear(0, .1 25%, .75 50%, 1);
                }
                """;

        var result = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                new JavaFXCssTarget(
                        JavaFXTarget.JAVAFX27,
                        OutputStyle.COMPRESSED
                )
        );
        assertTrue(
                result.output().contains(
                        "cubic-bezier(0.1, -2, 0.9, 3)"
                ),
                result.output()
        );
        assertTrue(
                result.output().contains(
                        "linear(0, 0.1 25%, 0.75 50%, 1)"
                ),
                result.output()
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(source, Syntax.SCSS),
                        new JavaFXCssTarget(
                                JavaFXTarget.JAVAFX25,
                                OutputStyle.COMPRESSED
                        )
                )
        );
        assertTrue(failure.getMessage().contains("cubic-bezier"), failure.getMessage());
    }

    /// Compiles supports rules for CSS and rejects them for JavaFX CSS.
    @Test
    void compilesSupportsRulesForCssAndJavaFXCssTargets() throws Exception {
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

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(source, Syntax.SCSS),
                        new JavaFXCssTarget(
                                JavaFXTarget.JAVAFX27,
                                OutputStyle.COMPRESSED
                        )
                )
        );
        assertTrue(failure.getMessage().contains("@supports"), failure.getMessage());
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
                        @supports ((display: grid) and selector(.button)) or (not ((display: flex) and (--theme: dark))) {
                          Pane {
                            -fx-opacity: 1;
                          }
                        }""",
                result.output()
        );
    }

    /// Serializes custom-property supports declarations with CSS-like spacing.
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
                        @supports (--theme: dark) {
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
                        @supports ((display: grid) and selector(.button)) or (not (--theme: dark)) {
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
                "Expected \"and\".",
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

    /// Hoists merged nested media without repeating its enclosing media rule.
    @Test
    void hoistsMergedNestedMediaWithoutRepeatingOuterMedia() throws Exception {
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
                            color: three;
                          }
                        }
                        @media screen and (min-width: 600px), print and (min-width: 600px) {
                          .button {
                            color: two;
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

    /// Rejects media types because JavaFX accepts only media conditions.
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

        assertEquals("Expected '('", failure.getMessage());
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
    void rejectsUnsupportedAtRulesAndUnsafeConditionalNesting() throws Exception {
        // Nested @font-face in style rules bubbles to the stylesheet root (dart-sass).
        assertEquals(
                """
                        Pane {
                          -fx-opacity: 1;
                        }
                        @font-face {
                          src: local(Example);
                        }
                        """.strip(),
                compile("Pane { -fx-opacity: 1; @font-face { src: local(Example); } }")
                        .output()
                        .replace("\r\n", "\n")
                        .strip()
        );
        assertEquals(
                "This at-rule is not allowed here.",
                assertCompilationFailure(
                        "Pane { font: { @supports (display: grid) {} } }"
                ).getMessage()
        );
        assertEquals(
                "Expected @supports condition.",
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

    /// Rejects native CSS nesting that JavaFX would silently discard.
    @Test
    void serializesNativeCssNestingForJavaFXCss() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
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
                )
        );

        assertTrue(failure.getMessage().contains("nested style"), failure.getMessage());
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

    /// Generates a version-3 source map for CSS output and rejects BSS maps.
    @Test
    void generatesSourceMapsForCssAndRejectsBssMaps() throws Exception {
        var compiler = new SassCompiler();
        var source = SassSource.fromString("a { color: red; }", Syntax.SCSS);
        var result = compiler.compile(
                source,
                CssTarget.DEFAULT,
                new CompileOptions(true, java.util.List.of())
        );
        assertEquals(
                """
                        a {
                          color: red;
                        }""",
                result.output()
        );
        assertEquals(true, result.sourceMap() != null);
        assertTrue(result.sourceMap().json().contains("\"version\":3"));

        assertEquals(
                "Source map generation isn't supported for BSS output.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compiler.compile(
                                source,
                                BssTarget.DEFAULT,
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
