// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch42 percent tokens, trailing-operator continuations, and list
/// trailing-comma handling.
@NotNullByDefault
final class Batch42LanguageTest {
    @Test
    void treatsTrailingPercentAsStringInSpaceList() throws Exception {
        assertEquals(
                "a {\n  b: c %;\n}".strip(),
                compile("a {b: c %}", Syntax.SCSS).strip()
        );
        assertEquals(
                "a {\n  b: c(d %);\n}".strip(),
                compile("a {b: c(d %)}", Syntax.SCSS).strip()
        );
    }

    @Test
    void continuesModuloAcrossIndentedLines() throws Exception {
        assertEquals(
                "a {\n  b: 1;\n}".strip(),
                compile(
                        """
                                a
                                  b: 3 %
                                  2
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void rejectsStringModuloAcrossIndentedLines() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                a
                                  b: c %
                                  d
                                """,
                        Syntax.SASS
                )
        );
        assertTrue(
                failure.getMessage().contains("Undefined operation \"c % d\""),
                failure.getMessage()
        );
    }

    @Test
    void continuesBinaryPlusAcrossLines() throws Exception {
        assertEquals(
                "d {\n  e: bc;\n}".strip(),
                compile(
                        """
                                $a: b +
                                c
                                d
                                  e: $a
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void allowsTrailingCommaInIndentedLists() throws Exception {
        assertEquals(
                "a {\n  b: c, d;\n}".strip(),
                compile(
                        """
                                a
                                  b: c, d,
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void dropsSameLineLoudCommentAfterSemicolon() throws Exception {
        assertEquals(
                "a {\n  b: c;\n  d: e;\n}".strip(),
                compile(
                        """
                                a
                                  b: c; /* f */
                                  d: e
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void nestsBareCombinatorSelectors() throws Exception {
        assertEquals(
                ".a + .c, .a + .b {\n  margin: 10px;\n}".strip(),
                compile(
                        """
                                .a
                                  +
                                    .c, .b
                                      margin: 10px
                                """,
                        Syntax.SASS
                ).strip()
        );
        assertEquals(
                ".a > .c, .a > .b {\n  margin: 10px;\n}".strip(),
                compile(
                        """
                                .a
                                  >
                                    .c, .b
                                      margin: 10px
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void rejectsUnparenthesizedMoreIndentedListContinuation() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                a
                                  b: c,
                                     d,
                                """,
                        Syntax.SASS
                )
        );
        // dart-sass reports expected ":" when the child is reparsed as a nested
        // property; our preprocessor rejects valued-property children earlier.
        assertTrue(
                failure.getMessage().contains("expected \":\"")
                        || failure.getMessage().contains("cannot contain indented children"),
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
