// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.css;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies bubbling and serialization of rules with sparse child content.
@NotNullByDefault
final class CssBubbleSerializationTest {

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }

    @Test
    void combinatorOnlyExtendIsDropped() throws Exception {
        var css = compile(
                """
                        a {b: c}
                        + {@extend a}
                        """,
                Syntax.SCSS
        );
        assertEquals("a {\n  b: c;\n}", css.strip());
        assertFalse(css.contains("+"), css);
    }

    @Test
    void mediaStaysInsideKeyframeSelector() throws Exception {
        var css = compile(
                """
                        @keyframes a {
                          to {@media screen {b: c}}
                        }
                        """,
                Syntax.SCSS
        );
        assertTrue(css.contains("to {"), css);
        assertTrue(css.indexOf("to {") < css.indexOf("@media"), css);
        assertFalse(css.matches("(?s).*@media[^{]*\\{[\\s\\S]*to \\{.*"), css);
    }

    @Test
    void bubbledFontFaceCommentOnlyIsCompact() throws Exception {
        var css = compile(
                """
                        a {
                          @font-face {/**/}
                        }
                        """,
                Syntax.SCSS
        );
        assertTrue(
                css.contains("@font-face { /**/ }") || css.contains("@font-face{ /**/ }"),
                css
        );
        assertFalse(css.contains("@font-face {\n"), css);
    }

    @Test
    void bubbledKeyframesCommentOnlyIsCompact() throws Exception {
        var css = compile(
                """
                        a {
                          @keyframes {/**/}
                        }
                        """,
                Syntax.SCSS
        );
        assertTrue(css.contains("@keyframes { /**/ }"), css);
    }
}
