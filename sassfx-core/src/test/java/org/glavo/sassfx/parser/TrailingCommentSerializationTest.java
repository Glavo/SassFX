// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.parser;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch36 silent-comment and moz-document residual fixes.
@NotNullByDefault
final class TrailingCommentSerializationTest {
    /// Custom properties keep trailing silent comments in indented syntax.
    @Test
    void customPropertyKeepsTrailingSilentCommentInSass() throws Exception {
        var output = compile(
                """
                        a
                          --b: c // comment
                        """,
                Syntax.SASS
        );
        assertTrue(output.contains("--b: c // comment"), output);
    }

    /// {@code @-moz-document} drops trailing loud comments before the block.
    @Test
    void mozDocumentDropsTrailingLoudComment() throws Exception {
        assertEquals(
                "@-moz-document url-prefix(a) {}",
                compile("@-moz-document url-prefix(a) /**/\n  {}", Syntax.SCSS).strip()
        );
    }

    /// {@code @for} still strips trailing silent comments before synthetic braces.
    @Test
    void forStillStripsTrailingSilentBeforeBraces() throws Exception {
        assertEquals(
                "",
                compile(
                        """
                                @for $i from 1 through 2 //
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
