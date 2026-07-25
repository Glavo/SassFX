// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch53 diagnostics and global round half-away-from-zero.
@NotNullByDefault
final class Batch53LanguageTest {
    @Test
    void globalRoundUsesHalfAwayFromZero() throws Exception {
        assertEquals(
                "a {\n  b: 1;\n}".strip(),
                compile("a { b: round(0.5) }", Syntax.SCSS).strip()
        );
    }

    @Test
    void supportsMixedOperatorsExpectPreviousOperator() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        "@supports (a: b) and (c: d) or (e: f) {@g}",
                        Syntax.SCSS
                )
        );
        assertTrue(failure.getMessage().contains("Expected \"and\"."), failure.getMessage());
    }

    @Test
    void plainCssRejectsEmptyParenthesesAtParseTime() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("a { b: () }", Syntax.CSS)
        );
        assertTrue(failure.getMessage().contains("Expected expression."), failure.getMessage());
    }

    @Test
    void indentedSupportsTrailingAndIsIncomplete() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                @supports (a) and
                                  (b)
                                    c
                                      d: e
                                """,
                        Syntax.SASS
                )
        );
        assertTrue(failure.getMessage().contains("expected \"(\"."), failure.getMessage());
    }

    @Test
    void indentedMediaTrailingAndIsIncomplete() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                @media (a: b) and
                                  (c: d)
                                """,
                        Syntax.SASS
                )
        );
        assertTrue(
                failure.getMessage().contains("expected media condition in parentheses."),
                failure.getMessage()
        );
    }

    @Test
    void rejectsNamespacedPrivateInclude() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("a {@include namespace._member}", Syntax.SCSS)
        );
        assertTrue(
                failure.getMessage().contains(
                        "Private members can't be accessed from outside their modules."
                ),
                failure.getMessage()
        );
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
