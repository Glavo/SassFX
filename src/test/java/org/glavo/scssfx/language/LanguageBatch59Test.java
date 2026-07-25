// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.language;

import org.glavo.scssfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Language-gap probes for the batch59 wave.
@NotNullByDefault
final class LanguageBatch59Test {

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }

    @Test
    void bareTopLevelParentSelectorIsPreserved() throws Exception {
        var css = compile("& {a: b}", Syntax.SCSS);
        assertTrue(css.contains("&"), css);
        assertTrue(css.contains("a: b"), css);
    }

    @Test
    void parentInsideUnknownAtRuleIsPreserved() throws Exception {
        var css = compile(
                """
                        @a {
                          & {b: c}
                        }
                        """,
                Syntax.SCSS
        );
        assertTrue(css.contains("&"), css);
        assertTrue(css.contains("b: c"), css);
    }

    @Test
    void contentAllowsTrailingLoudComment() throws Exception {
        assertEquals(
                "",
                compile("@mixin a {@content() /**/}", Syntax.SCSS).strip()
        );
    }

    @Test
    void unterminatedBlockUsesLowercaseExpectedBrace() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("div {\n", Syntax.SCSS)
        );
        assertEquals("expected \"}\".", failure.primaryDiagnostic().message());
    }

    @Test
    void unmatchedTopLevelBraceReportsUnmatched() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                .curly {
                                  --prop: };
                                }
                                """,
                        Syntax.SCSS
                )
        );
        assertEquals("unmatched \"}\".", failure.primaryDiagnostic().message());
    }

    @Test
    void indentedElseIfContinuesCondition() throws Exception {
        assertEquals(
                "",
                compile(
                        """
                                @if true
                                @else if
                                  true
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void indentedImportantContinuesAfterBang() throws Exception {
        assertEquals(
                "a {\n  b: c !important;\n}",
                compile(
                        """
                                a
                                  b: c!
                                    important
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void indentedIncludeUsingContinuesParameterList() throws Exception {
        assertEquals(
                "",
                compile(
                        """
                                @mixin a
                                  @content
                                @include a() using
                                  ()
                                """,
                        Syntax.SASS
                ).strip()
        );
    }
}
