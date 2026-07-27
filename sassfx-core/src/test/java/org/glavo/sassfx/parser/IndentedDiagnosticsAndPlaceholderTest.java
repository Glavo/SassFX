// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.parser;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies indented-Sass diagnostics and placeholder selector boundaries.
@NotNullByDefault
final class IndentedDiagnosticsAndPlaceholderTest {

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }

    @Test
    void placeholderParentSuffixIsAllowed() throws Exception {
        var css = compile(
                """
                        %foo {
                          &bar {
                            display: block;
                          }
                          &.bar {
                            display: block;
                          }
                        }
                        zoo {
                          @extend %foo;
                        }
                        """,
                Syntax.SCSS
        );
        assertTrue(css.contains("zoo.bar"), css);
        assertTrue(css.contains("display: block"), css);
    }

    @Test
    void inconsistentIndentationUsesDartSassWording() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                a
                                    b: c
                                 d: e
                                """,
                        Syntax.SASS
                )
        );
        assertEquals(
                "Inconsistent indentation, expected 4 spaces.",
                failure.primaryDiagnostic().message()
        );
        assertEquals(
                "INDENTED_INCONSISTENT_INDENT",
                failure.primaryDiagnostic().code()
        );
    }

    @Test
    void keywordMapKeyDiagnosticParenthesizesComplexKeys() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                @use "sass:meta";
                                $id: meta.inspect((a#b:c)...)
                                """,
                        Syntax.SCSS
                )
        );
        var message = failure.primaryDiagnostic().message();
        assertTrue(
                message.contains("Variable keyword argument map must have string keys."),
                message
        );
        assertTrue(message.contains("(a #b) is not a string in (a #b: c)."), message);
    }

    @Test
    void unmatchedCloseParenInIndentedSelector() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                a:is(b))
                                  c: d
                                """,
                        Syntax.SASS
                )
        );
        assertEquals("Unexpected \")\".", failure.primaryDiagnostic().message());
    }

    @Test
    void rawNewlineInSassStringReportsExpectedQuote() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                a
                                  b: 'line1
                                      line2'
                                """,
                        Syntax.SASS
                )
        );
        assertEquals("Expected '.", failure.primaryDiagnostic().message());
    }
}
