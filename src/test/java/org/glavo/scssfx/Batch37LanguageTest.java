// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch37 charset, source-map comment, and pseudo-argument fixes.
@NotNullByDefault
final class Batch37LanguageTest {
    @Test
    void discardsCharsetRule() throws Exception {
        assertEquals(
                "a {\n  b: c;\n}".strip(),
                compile("@charset\n  \"a\";\na {b: c}", Syntax.SCSS).strip()
        );
    }

    @Test
    void dropsSourceMapComments() throws Exception {
        assertFalse(
                compile("a {b: c}\n/*# sourceMappingURL=whatever */\n", Syntax.SCSS)
                        .contains("sourceMappingURL")
        );
        assertFalse(
                compile("/*# sourceURL=whatever */\na {b: c}\n", Syntax.SCSS)
                        .contains("sourceURL")
        );
    }

    @Test
    void stripsOuterWhitespaceInRawPseudoArguments() throws Exception {
        assertEquals(
                "a:b(c) {\n  d: e;\n}".strip(),
                compile("a:b(\n  c)\n  d: e\n", Syntax.SASS).strip()
        );
        assertEquals(
                "a:b(c) {\n  d: e;\n}".strip(),
                compile("a:b(c\n  )\n  d: e\n", Syntax.SASS).strip()
        );
    }

    @Test
    void unterminatedLoudCommentSaysExpectedMoreInput() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("a {b: /*", Syntax.SCSS)
        );
        assertTrue(failure.getMessage().contains("expected more input"), failure.getMessage());
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
