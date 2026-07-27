// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.selector;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch38 @extend diagnostics and indented selector-comment handling.
@NotNullByDefault
final class ExtendTargetAndSelectorCommentTest {
    @Test
    void rejectsComplexExtendTargetsWithDirectiveMessage() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                a b {
                                  a: b;
                                }
                                c {
                                  @extend a b;
                                }
                                """,
                        Syntax.SCSS
                )
        );
        assertTrue(
                failure.getMessage().contains("complex selectors may not be extended."),
                failure.getMessage()
        );
    }

    @Test
    void rejectsCompoundExtendTargetsWithDirectiveMessage() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                a:hover {
                                  a: b;
                                }
                                b {
                                  @extend a:hover;
                                }
                                """,
                        Syntax.SCSS
                )
        );
        assertTrue(
                failure.getMessage().contains("compound selectors may no longer be extended."),
                failure.getMessage()
        );
        assertTrue(
                failure.getMessage().contains("Consider `@extend a, :hover` instead."),
                failure.getMessage()
        );
    }

    @Test
    void continuesSelectorAfterCommaAndSilentComment() throws Exception {
        assertEquals(
                "a,\nb {\n  x: y;\n}".strip(),
                compile(
                        """
                                a, // comment
                                b
                                  x: y
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void continuesSelectorAfterCommaAndLoudComment() throws Exception {
        assertEquals(
                "a,\nb {\n  x: y;\n}".strip(),
                compile(
                        """
                                a, /* comment */
                                b
                                  x: y
                                """,
                        Syntax.SASS
                ).strip()
        );
        assertEquals(
                "a,\nb {\n  x: y;\n}".strip(),
                compile(
                        """
                                a /* comment */,
                                b
                                  x: y
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void rejectsTextAfterLoudCommentClose() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("/* */ a\n", Syntax.SASS)
        );
        assertTrue(
                failure.getMessage().contains("Unexpected text after end of comment"),
                failure.getMessage()
        );
    }

    @Test
    void discardsTrailingCommentsAfterLoudCommentClose() throws Exception {
        assertEquals(
                "/* */".strip(),
                compile("/* */ /* */\n", Syntax.SASS).strip()
        );
        assertEquals(
                "/* */".strip(),
                compile("/* */ //\n", Syntax.SASS).strip()
        );
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
