package org.glavo.scssfx.selector;

import org.glavo.scssfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies multi-line selector lists preserve line breaks after commas.
@NotNullByDefault
final class SelectorLineBreakTest {
    /// Compiles a multi-line selector list nested under {@code @media}.
    @Test
    void mediaMultilineSelectorPreservesLineBreak() throws Exception {
        var css = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @media a {
                                  b,
                                  a { c: d }
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        @media a {
                          b,
                          a {
                            c: d;
                          }
                        }""",
                css
        );
    }

    /// Compiles nested multi-line selectors under media.
    @Test
    void mediaNestedMultilineSelectors() throws Exception {
        var css = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @media a {
                                  b {
                                    c,
                                    d { e: f }
                                  }
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        @media a {
                          b c,
                          b d {
                            e: f;
                          }
                        }""",
                css
        );
    }
}
