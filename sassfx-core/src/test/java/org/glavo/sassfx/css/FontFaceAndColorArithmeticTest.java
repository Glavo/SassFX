// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.css;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch40 color-arithmetic rejection, font-face bubbling, and at-root
/// comment handling.
@NotNullByDefault
final class FontFaceAndColorArithmeticTest {
    @Test
    void rejectsColorArithmetic() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("$v: #abc + #123;", Syntax.SCSS)
        );
        assertTrue(
                failure.getMessage().contains("Undefined operation \"#abc + #123\"."),
                failure.getMessage()
        );
        assertEquals("UNDEFINED_OPERATION", failure.primaryDiagnostic().code());
        failure = assertThrows(
                SassCompilationException.class,
                () -> compile("$v: #abc - #123;", Syntax.SCSS)
        );
        assertTrue(failure.getMessage().contains("Undefined operation"), failure.getMessage());
        assertEquals("UNDEFINED_OPERATION", failure.primaryDiagnostic().code());
    }

    @Test
    void bubblesFontFaceFromStyleRules() throws Exception {
        assertEquals(
                """
                        a {
                          b: c;
                        }
                        @font-face {
                          d: e;
                        }
                        """.strip(),
                compile(
                        """
                                a {
                                  b: c;
                                  @font-face { d: e }
                                }
                                """,
                        Syntax.SCSS
                ).strip()
        );
        assertEquals(
                """
                        @font-face {
                          e: f;
                        }
                        a b c {
                          g: h;
                        }
                        """.strip(),
                compile("a { b { c { @font-face { e: f } g: h; } } }", Syntax.SCSS).strip()
        );
    }

    @Test
    void allowsCommentsInsideAtRootQueries() throws Exception {
        assertEquals(
                "",
                compile("@at-root (/**/ without: media) {}", Syntax.SCSS).strip()
        );
        assertEquals(
                "",
                compile("@at-root (without /**/ : media) {}", Syntax.SCSS).strip()
        );
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
