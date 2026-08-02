// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Ports the implementation-specific output cases from Dart Sass 1.102.0's
/// `compressed_test.dart` and `output_test.dart`.
@NotNullByDefault
final class DartSassOutputCompatibilityTest {
    /// Verifies compressed style-rule, selector, declaration, and value output.
    ///
    /// @return one dynamic test for each upstream behavior
    @TestFactory
    Stream<DynamicTest> compressedStyleAndValueCases() {
        return dynamicTests(List.of(
                compressed(
                        "style rule whitespace",
                        "a {x: y}",
                        "a{x:y}"
                ),
                compressed(
                        "selector descendant whitespace",
                        "a b .c {x: y}",
                        "a b .c{x:y}"
                ),
                compressed(
                        "selector comma whitespace",
                        "a, b, .c {x: y}",
                        "a,b,.c{x:y}"
                ),
                compressed(
                        "selector newlines",
                        "a,\nb,\n.c {x: y}",
                        "a,b,.c{x:y}"
                ),
                compressed(
                        "child combinator whitespace",
                        "a > b {x: y}",
                        "a>b{x:y}"
                ),
                compressed(
                        "next-sibling combinator whitespace",
                        "a + b {x: y}",
                        "a+b{x:y}"
                ),
                compressed(
                        "subsequent-sibling combinator whitespace",
                        "a ~ b {x: y}",
                        "a~b{x:y}"
                ),
                compressed(
                        "nth-child required whitespace",
                        "a:nth-child(2n of b) {x: y}",
                        "a:nth-child(2n of b){x:y}"
                ),
                compressed(
                        "nth-child comma whitespace",
                        "a:nth-child(2n of b, c) {x: y}",
                        "a:nth-child(2n of b,c){x:y}"
                ),
                compressed(
                        "quoted attribute modifier whitespace",
                        "[a=\" \" b] {x: y}",
                        "[a=\" \"b]{x:y}"
                ),
                compressed(
                        "unquoted attribute modifier whitespace",
                        "[a=\"b\"c] {x: y}",
                        "[a=b c]{x:y}"
                ),
                compressed(
                        "declaration semicolons",
                        "a {q: r; s: t}",
                        "a{q:r;s:t}"
                ),
                compressed(
                        "multiline custom property whitespace",
                        """
                                a {
                                  --foo: {
                                    q: r;
                                    b {
                                      s: t;
                                    }
                                  }
                                }
                                """,
                        "a{--foo: { q: r; b { s: t; } } }"
                ),
                compressed(
                        "single-line custom property whitespace",
                        "a {\n  --foo: a   b\t\tc;\n}",
                        "a{--foo: a b\tc}"
                ),
                compressed(
                        "custom property semicolons",
                        """
                                a {
                                  --foo: {
                                    a: b;
                                  };
                                  --bar: x y;
                                  --baz: q r;
                                }
                                """,
                        "a{--foo: { a: b; };--bar: x y;--baz: q r}"
                ),
                compressed("unitless leading zero", "a {b: 0.123}", "a{b:.123}"),
                compressed("dimension leading zero", "a {b: 0.123px}", "a{b:.123px}"),
                compressed("comma list whitespace", "a {b: x, y, z}", "a{b:x,y,z}"),
                compressed(
                        "slash list whitespace",
                        "@use \"sass:list\";\na {b: list.slash(x, y, z)}",
                        "a{b:x/y/z}"
                ),
                compressed("space list whitespace", "a {b: x y z}", "a{b:x y z}"),
                compressed("short color name", "a {b: #f00}", "a{b:red}"),
                compressed("terse color hex", "a {b: white}", "a{b:#fff}"),
                compressed("verbose color hex", "a {b: darkgoldenrod}", "a{b:#b8860b}"),
                compressed(
                        "rgba color",
                        "a {b: rgba(255, 0, 0, 0.5)}",
                        "a{b:rgba(255,0,0,.5)}"
                ),
                compressed("unnamed color", "a {b: #cc3232}", "a{b:#cc3232}")
        ));
    }

    /// Verifies private-use characters remain literal in compressed output.
    ///
    /// @return one dynamic test for each upstream private-use code point
    @TestFactory
    Stream<DynamicTest> compressedPrivateUseCharacters() {
        return Stream.of(
                0xE000,
                0xF000,
                0xF8FF,
                0xF0000,
                0xFABCD,
                0xFFFFD,
                0x100000,
                0x10ABCD,
                0x10FFFD
        ).map(codePoint -> DynamicTest.dynamicTest(
                "U+" + Integer.toHexString(codePoint).toUpperCase(),
                () -> assertCompiled(
                        "a{b:" + Character.toString(codePoint) + "}",
                        "a {b: \\" + Integer.toHexString(codePoint) + "}",
                        Syntax.SCSS,
                        OutputStyle.COMPRESSED
                )
        ));
    }

    /// Verifies compressed top-level rules, at-rules, imports, and comments.
    ///
    /// @return one dynamic test for each upstream behavior
    @TestFactory
    Stream<DynamicTest> compressedAtRuleAndCommentCases() {
        return dynamicTests(List.of(
                compressed(
                        "top-level at-rules",
                        "@foo; @bar; @baz;",
                        "@foo;@bar;@baz"
                ),
                compressed(
                        "top-level style rules",
                        "a {b: c} x {y: z}",
                        "a{b:c}x{y:z}"
                ),
                compressed(
                        "supports condition whitespace",
                        "@supports (display: flex) {a {b: c}}",
                        "@supports(display: flex){a{b:c}}"
                ),
                compressed(
                        "supports negation whitespace",
                        "@supports not (display: flex) {a {b: c}}",
                        "@supports not (display: flex){a{b:c}}"
                ),
                compressed(
                        "media feature whitespace",
                        "@media (min-width: 900px) {a {b: c}}",
                        "@media(min-width: 900px){a{b:c}}"
                ),
                compressed(
                        "media type whitespace",
                        "@media screen {a {b: c}}",
                        "@media screen{a{b:c}}"
                ),
                compressed(
                        "media and whitespace",
                        """
                                @media screen and (min-width: 900px) and (max-width: 100px) {
                                  a {b: c}
                                }
                                """,
                        "@media screen and (min-width: 900px)and (max-width: 100px){a{b:c}}"
                ),
                compressed(
                        "media or whitespace",
                        """
                                @media (min-width: 900px) or (max-width: 100px) or (print) {
                                  a {b: c}
                                }
                                """,
                        "@media(min-width: 900px)or (max-width: 100px)or (print){a{b:c}}"
                ),
                compressed(
                        "media not whitespace",
                        "@media not (min-width: 900px) {a {b: c}}",
                        "@media not (min-width: 900px){a{b:c}}"
                ),
                compressed(
                        "media modifier whitespace",
                        "@media only screen {a {b: c}}",
                        "@media only screen{a{b:c}}"
                ),
                compressed(
                        "keyframes selector whitespace",
                        "@keyframes a {from {a: b}}",
                        "@keyframes a{from{a:b}}"
                ),
                compressed(
                        "keyframes comma whitespace",
                        "@keyframes a {from, to {a: b}}",
                        "@keyframes a{from,to{a:b}}"
                ),
                compressed("import string", "@import \"foo.css\";", "@import\"foo.css\""),
                compressed("import unquoted URL", "@import url(foo.css);", "@import\"foo.css\""),
                compressed("import quoted URL", "@import url(\"foo.css\");", "@import\"foo.css\""),
                compressed(
                        "import media query",
                        "@import \"foo.css\" screen;",
                        "@import\"foo.css\"screen"
                ),
                compressed(
                        "import supports condition",
                        "@import \"foo.css\" supports(display: flex);",
                        "@import\"foo.css\"supports(display: flex)"
                ),
                compressed("standalone silent comment", "/* foo bar */", ""),
                compressed(
                        "nested silent comment",
                        "a {b: c; /* foo bar */ d: e;}",
                        "a{b:c;d:e}"
                ),
                compressed("silent-comment-only parent", "a {/* foo bar */}", ""),
                compressed(
                        "multiple-silent-comment-only parent",
                        "a {/* foo bar */ /* baz bang */}",
                        ""
                ),
                compressed("preserved standalone loud comment", "/*! foo bar */", "/*! foo bar */"),
                compressed(
                        "adjacent loud comments",
                        "/*! foo */\n/*! bar */",
                        "/*! foo *//*! bar */"
                ),
                compressed(
                        "nested loud comment",
                        "a { /*! foo bar */ }",
                        "a{/*! foo bar */}"
                )
        ));
    }

    /// Verifies private-use escaping and newline normalization in expanded
    /// output.
    @Test
    void expandedPrivateUseCharactersAndCommentNewlines() throws Exception {
        for (var codePoint : List.of(
                0xE000,
                0xF000,
                0xF8FF,
                0xF0000,
                0xFABCD,
                0xFFFFD,
                0x100000,
                0x10ABCD,
                0x10FFFD,
                0xFFFFE,
                0xFFFFF,
                0x10FFFE,
                0x10FFFF
        )) {
            var escape = "\\" + Integer.toHexString(codePoint);
            assertCompiled(
                    "a {\n  b: " + escape + ";\n}",
                    "a {b: " + escape + "}",
                    Syntax.SCSS,
                    OutputStyle.EXPANDED
            );
        }

        assertCompiled(
                "a {\n  b: \"\\e000 a\";\n}",
                "a {b: '\\e000 a'}",
                Syntax.SCSS,
                OutputStyle.EXPANDED
        );
        assertCompiled(
                "a {\n  b: \"\\e000  \";\n}",
                "a {b: '\\e000  '}",
                Syntax.SCSS,
                OutputStyle.EXPANDED
        );
        assertCompiled(
                "/* foo\n * bar */",
                "/* foo\r\n * bar */",
                Syntax.SCSS,
                OutputStyle.EXPANDED
        );
        assertCompiled(
                "/* foo\n * bar */",
                "/*\r\n  foo\r\n  bar",
                Syntax.SASS,
                OutputStyle.EXPANDED
        );
    }

    /// Verifies large-number formatting and complex-unit inspection.
    ///
    /// @return one dynamic test for each upstream behavior
    @TestFactory
    Stream<DynamicTest> expandedNumberAndUnitCases() {
        return dynamicTests(List.of(
                expanded(
                        "integer at least 1e21",
                        "a {b: 1e21}",
                        "a {\n  b: 1000000000000000000000;\n}"
                ),
                expanded(
                        "integer below 1e21",
                        "a {b: 1e20}",
                        "a {\n  b: 100000000000000000000;\n}"
                ),
                expanded(
                        "infinite floating-point number",
                        "a {b: 1e999}",
                        "a {\n  b: calc(infinity);\n}"
                ),
                expanded(
                        "floating-point number at least 1e21",
                        "a {b: 1.01e21}",
                        "a {\n  b: 1010000000000000000000;\n}"
                ),
                expanded(
                        "floating-point number below 1e21",
                        "a {b: 1.01e20}",
                        "a {\n  b: 101000000000000000000;\n}"
                ),
                expanded(
                        "top-level complex units",
                        "@use 'sass:meta';\na {b: meta.inspect(1px * 1em)};",
                        "a {\n  b: calc(1px * 1em);\n}"
                ),
                expanded(
                        "complex units in calc",
                        "@use 'sass:meta';\na {b: meta.inspect(calc(1px * 1em))};",
                        "a {\n  b: calc(1px * 1em);\n}"
                ),
                expanded(
                        "complex units nested in calc",
                        "@use 'sass:meta';\na {b: meta.inspect(calc(c / (1px * 1em)))};",
                        "a {\n  b: calc(c / (1px * 1em));\n}"
                ),
                expanded(
                        "complex numerator and denominator units",
                        """
                                @use 'sass:math';
                                @use 'sass:meta';
                                a {b: meta.inspect(1px * math.div(math.div(1em, 1s), 1x))};
                                """,
                        "a {\n  b: calc(1px * 1em / 1s / 1x);\n}"
                ),
                expanded(
                        "complex denominator-only units",
                        """
                                @use 'sass:math';
                                @use 'sass:meta';
                                a {b: meta.inspect(math.div(math.div(1, 1s), 1x))};
                                """,
                        "a {\n  b: calc(1 / 1s / 1x);\n}"
                )
        ));
    }

    /// Verifies every trailing loud-comment placement from the upstream output
    /// suite.
    ///
    /// @return one dynamic test for each upstream behavior
    @TestFactory
    Stream<DynamicTest> trailingLoudCommentCases() {
        return dynamicTests(List.of(
                expanded(
                        "comment after open block",
                        "selector { /* please don't move me */\n  name: value;\n}",
                        "selector { /* please don't move me */\n  name: value;\n}"
                ),
                expanded(
                        "comment after multiline-selector open block",
                        "selector1,\nselector2 { /* please don't move me */\n  name: value;\n}",
                        "selector1,\nselector2 { /* please don't move me */\n  name: value;\n}"
                ),
                expanded(
                        "comment after close block",
                        "selector {\n  name: value;\n} /* please don't move me */",
                        "selector {\n  name: value;\n} /* please don't move me */"
                ),
                expanded(
                        "comment as only block content",
                        "selector {\n  /* please don't move me */\n}",
                        "selector {\n  /* please don't move me */\n}"
                ),
                expanded(
                        "comment as inline-only block content",
                        "selector { /* please don't move me */ }",
                        "selector { /* please don't move me */ }"
                ),
                expanded(
                        "two comments in empty block",
                        "selector { /* please don't move me */ /* please don't move me */ }",
                        "selector { /* please don't move me */ /* please don't move me */\n}"
                ),
                expanded(
                        "two comments after declaration",
                        "selector { margin: 1px; /* please don't move me */ /* please don't move me */ }",
                        "selector {\n  margin: 1px; /* please don't move me */ /* please don't move me */\n}"
                ),
                expanded(
                        "comments after declarations",
                        """
                                selector {
                                  name1: value1; /* please don't move me 1 */
                                  name2: value2; /* please don't move me 2 */
                                  name3: value3; /* please don't move me 3 */
                                }
                                """,
                        """
                                selector {
                                  name1: value1; /* please don't move me 1 */
                                  name2: value2; /* please don't move me 2 */
                                  name3: value3; /* please don't move me 3 */
                                }"""
                ),
                expanded(
                        "comments after unknown at-rules",
                        """
                                selector {
                                  @rule1; /* please don't move me 1 */
                                  @rule2; /* please don't move me 2 */
                                  @rule3; /* please don't move me 3 */
                                }
                                """,
                        """
                                selector {
                                  @rule1; /* please don't move me 1 */
                                  @rule2; /* please don't move me 2 */
                                  @rule3; /* please don't move me 3 */
                                }"""
                ),
                expanded(
                        "comment after top-level statement",
                        "@rule; /* please don't move me */",
                        "@rule; /* please don't move me */"
                ),
                expanded(
                        "left brace in selector",
                        """
                                @rule1;
                                @rule2;
                                selector[href*="{"]
                                { /* please don't move me */ }

                                @rule3;
                                """,
                        """
                                @rule1;
                                @rule2;
                                selector[href*="{"] { /* please don't move me */ }

                                @rule3;"""
                ),
                expanded(
                        "empty loud-comment mixin with spacing",
                        """
                                @mixin loudComment {
                                  /* ... */
                                }
                                selector {
                                  @include loudComment;
                                }
                                """,
                        "selector {\n  /* ... */\n}"
                ),
                expanded(
                        "empty loud-comment mixin without spacing",
                        "@mixin loudComment{/* ... */}\nselector {@include loudComment;}",
                        "selector {\n  /* ... */\n}"
                ),
                expanded(
                        "loud-comment mixin with declaration",
                        """
                                @mixin loudComment {
                                  margin: 1px; /* mixin */
                                } /* mixin-out */
                                selector {
                                  @include loudComment; /* selector */
                                }
                                """,
                        """
                                /* mixin-out */
                                selector {
                                  margin: 1px; /* mixin */
                                  /* selector */
                                }"""
                ),
                expanded(
                        "nested loud comments with declarations",
                        """
                                foo { /* foo */
                                  padding: 1px; /* foo padding */
                                  bar { /* bar */
                                    padding: 2px; /* bar padding */
                                    baz { /* baz */
                                      padding: 3px; /* baz padding */
                                      margin: 3px; /* baz margin */
                                    } /* baz end */
                                    biz { /* biz */
                                      padding: 3px; /* biz padding */
                                      margin: 3px; /* biz margin */
                                    } /* biz end */
                                    margin: 2px; /* bar margin */
                                  } /* bar end */
                                  margin: 1px; /* foo margin */
                                } /* foo end */
                                """,
                        """
                                foo { /* foo */
                                  padding: 1px; /* foo padding */
                                }
                                foo bar { /* bar */
                                  padding: 2px; /* bar padding */
                                }
                                foo bar baz { /* baz */
                                  padding: 3px; /* baz padding */
                                  margin: 3px; /* baz margin */
                                }
                                foo bar {
                                  /* baz end */
                                }
                                foo bar biz { /* biz */
                                  padding: 3px; /* biz padding */
                                  margin: 3px; /* biz margin */
                                }
                                foo bar {
                                  /* biz end */
                                  margin: 2px; /* bar margin */
                                }
                                foo {
                                  /* bar end */
                                  margin: 1px; /* foo margin */
                                } /* foo end */"""
                ),
                expanded(
                        "nested loud comments without declarations",
                        """
                                foo { /* foo */
                                  bar { /* bar */
                                    baz { /* baz */
                                    } /* baz end */
                                    biz { /* biz */
                                    } /* biz end */
                                  } /* bar end */
                                } /* foo end */
                                """,
                        """
                                foo { /* foo */ }
                                foo bar { /* bar */ }
                                foo bar baz { /* baz */ }
                                foo bar {
                                  /* baz end */
                                }
                                foo bar biz { /* biz */ }
                                foo bar {
                                  /* biz end */
                                }
                                foo {
                                  /* bar end */
                                } /* foo end */"""
                )
        ));
    }

    /// Creates one compressed compilation case.
    ///
    /// @param name the dynamic-test display name
    /// @param source the SCSS source
    /// @param expected the expected CSS
    /// @return the compilation case
    private static CompilationCase compressed(
            String name,
            String source,
            String expected
    ) {
        return new CompilationCase(
                name,
                source,
                expected,
                Syntax.SCSS,
                OutputStyle.COMPRESSED
        );
    }

    /// Creates one expanded SCSS compilation case.
    ///
    /// @param name the dynamic-test display name
    /// @param source the SCSS source
    /// @param expected the expected CSS
    /// @return the compilation case
    private static CompilationCase expanded(
            String name,
            String source,
            String expected
    ) {
        return new CompilationCase(
                name,
                source,
                expected,
                Syntax.SCSS,
                OutputStyle.EXPANDED
        );
    }

    /// Converts compilation cases into executable JUnit tests.
    ///
    /// @param cases the cases to execute
    /// @return the dynamic tests
    private static Stream<DynamicTest> dynamicTests(List<CompilationCase> cases) {
        return cases.stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.name(),
                () -> assertCompiled(
                        testCase.expected(),
                        testCase.source(),
                        testCase.syntax(),
                        testCase.style()
                )
        ));
    }

    /// Compiles source and compares normalized line endings with expected CSS.
    ///
    /// @param expected the expected CSS
    /// @param source the Sass source
    /// @param syntax the input syntax
    /// @param style the output style
    private static void assertCompiled(
            String expected,
            String source,
            Syntax syntax,
            OutputStyle style
    ) throws Exception {
        var actual = new SassCompiler()
                .compile(
                        SassSource.fromString(source, syntax),
                        new CssTarget(style, false)
                )
                .output()
                .replace("\r\n", "\n");
        assertEquals(expected.replace("\r\n", "\n"), actual);
    }

    /// Describes one self-contained compiler-output assertion.
    ///
    /// @param name the dynamic-test display name
    /// @param source the Sass source
    /// @param expected the expected CSS
    /// @param syntax the input syntax
    /// @param style the output style
    private record CompilationCase(
            String name,
            String source,
            String expected,
            Syntax syntax,
            OutputStyle style
    ) {
    }
}
