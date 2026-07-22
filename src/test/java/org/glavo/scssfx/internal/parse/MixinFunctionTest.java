// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.glavo.scssfx.internal.ast.FunctionRule;
import org.glavo.scssfx.internal.ast.IncludeRule;
import org.glavo.scssfx.internal.ast.MixinRule;
import org.glavo.scssfx.internal.ast.ReturnRule;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies mixin/function parsing and end-to-end evaluation.
@NotNullByDefault
final class MixinFunctionTest {
    /// Verifies mixin, include, function, and return parse shapes.
    @Test
    void parsesMixinFunctionAndInclude() {
        var stylesheet = parse(
                """
                        @mixin m($x, $y: 1) { a { color: $x; } }
                        @include m(red);
                        @function double($n) { @return $n * 2; }
                        """
        );
        var mixin = assertInstanceOf(MixinRule.class, stylesheet.children().get(0));
        assertEquals("m", mixin.name());
        assertEquals(2, mixin.parameters().parameters().size());
        assertNull(mixin.parameters().parameters().get(0).defaultValue());
        assertTrue(mixin.parameters().parameters().get(1).defaultValue() != null);

        var include = assertInstanceOf(IncludeRule.class, stylesheet.children().get(1));
        assertEquals("m", include.name());
        assertEquals(1, include.arguments().positional().size());

        var function = assertInstanceOf(FunctionRule.class, stylesheet.children().get(2));
        assertEquals("double", function.name());
        assertInstanceOf(ReturnRule.class, function.children().get(0));
    }

    /// Compiles a mixin with parameters and defaults.
    @Test
    void compilesMixinWithDefaults() throws Exception {
        assertEquals(
                """
                        a {
                          color: red;
                        }

                        a {
                          color: blue;
                        }""",
                compile(
                        """
                                @mixin colorize($c: red) {
                                  a { color: $c; }
                                }
                                @include colorize;
                                @include colorize(blue);
                                """
                )
        );
    }

    /// Compiles mixin content blocks.
    @Test
    void compilesMixinContent() throws Exception {
        assertEquals(
                """
                        a {
                          color: red;
                        }""",
                compile(
                        """
                                @mixin wrapper {
                                  a { @content; }
                                }
                                @include wrapper {
                                  color: red;
                                }
                                """
                )
        );
    }

    /// Compiles user-defined functions, including defaults and control-flow returns.
    @Test
    void compilesUserFunctions() throws Exception {
        assertEquals(
                """
                        a {
                          width: 4px;
                          height: 4px;
                          order: 2;
                        }""",
                compile(
                        """
                                @function double($n) {
                                  @return $n * 2;
                                }
                                @function pick($flag, $a: 1px, $b: 2px) {
                                  @if $flag {
                                    @return $a;
                                  }
                                  @return $b;
                                }
                                a {
                                  width: double(2px);
                                  height: pick(false, 3px, 4px);
                                  order: pick(true, 2);
                                }
                                """
                )
        );
    }

    /// Verifies user functions shadow built-ins and unknown functions stay plain CSS.
    @Test
    void prefersUserFunctionsOverBuiltIns() throws Exception {
        assertEquals(
                """
                        a {
                          z-index: 99;
                          filter: blur(2px);
                        }""",
                compile(
                        """
                                @function length($list) {
                                  @return 99;
                                }
                                a {
                                  z-index: length(a b c);
                                  filter: blur(2px);
                                }
                                """
                )
        );
    }

    /// Verifies closure captures shared environment frames.
    @Test
    void capturesSharedClosureFrames() throws Exception {
        assertEquals(
                """
                        a {
                          z-index: 2;
                        }""",
                compile(
                        """
                                $x: 1;
                                @mixin m {
                                  a { z-index: $x; }
                                }
                                $x: 2;
                                @include m;
                                """
                )
        );
    }

    /// Verifies structured failures for undefined mixins and missing returns.
    @Test
    void reportsCallableFailures() {
        var undefined = assertThrows(
                SassCompilationException.class,
                () -> compile("@include missing;")
        );
        assertEquals("Undefined mixin.", undefined.getMessage());

        var noReturn = assertThrows(
                SassCompilationException.class,
                () -> compile("@function f() { $x: 1; } a { b: f(); }")
        );
        assertEquals("Function finished without @return.", noReturn.getMessage());

        var missingArg = assertThrows(
                SassCompilationException.class,
                () -> compile("@mixin m($x) {} @include m;")
        );
        assertEquals("Missing argument $x.", missingArg.getMessage());
    }

    /// Rejects `@content` and `@return` outside their legal contexts.
    @Test
    void rejectsIllegalContexts() {
        assertThrows(ParseException.class, () -> parse("@content;"));
        assertThrows(ParseException.class, () -> parse("@return 1;"));
        assertThrows(ParseException.class, () -> parse("@function f() { a { b: c; } }"));
    }

    /// Compiles SCSS source to expanded CSS text.
    ///
    /// @param source the SCSS source
    /// @return the CSS output
    /// @throws Exception if compilation fails unexpectedly
    private static String compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        ).output();
    }

    /// Parses an SCSS stylesheet.
    ///
    /// @param source the source text
    /// @return the parsed stylesheet
    private static org.glavo.scssfx.internal.ast.Stylesheet parse(String source) {
        return new ScssParser(new SourceFile(source, null)).parse();
    }
}
