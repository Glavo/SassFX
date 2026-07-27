// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.parser;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies indented {@code @supports} keeps author whitespace after open parens.
@NotNullByDefault
final class SupportsWhitespacePreserveTest {
    @Test
    void preservesIndentAfterOpenParenInSupportsFunction() throws Exception {
        var css = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @supports a(
                                  b)
                                  c
                                    d: e
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        @supports a(
                          b) {
                          c {
                            d: e;
                          }
                        }""",
                css
        );
    }

    @Test
    void preservesIndentBeforeCloseParenInSupportsFunction() throws Exception {
        var css = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @supports a(b
                                  )
                                  c
                                    d: e
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        @supports a(b
                          ) {
                          c {
                            d: e;
                          }
                        }""",
                css
        );
    }
}
