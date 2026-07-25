// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch54 plain-CSS, selector, supports, and mixin content fixes.
@NotNullByDefault
final class Batch54LanguageTest {
    @Test
    void plainCssRejectsMapLiterals() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("a { x: (y: z) }", Syntax.CSS)
        );
        assertTrue(failure.getMessage().contains("expected \")\"."), failure.getMessage());
    }

    @Test
    void attributeWithoutMatcherExpectsClosingBracket() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("[a b] {c: d}", Syntax.SCSS)
        );
        assertTrue(failure.getMessage().contains("Expected \"]\"."), failure.getMessage());
    }

    @Test
    void varEmptyFallbackInvalidTokens() {
        var braces = assertThrows(
                Exception.class,
                () -> compile("a {b: var(--c, {})}", Syntax.CSS)
        );
        assertTrue(braces.getMessage().contains("Expected expression."), braces.getMessage());
        var empty = assertThrows(
                Exception.class,
                () -> compile("a {b: var(--c, , d)}", Syntax.CSS)
        );
        assertTrue(empty.getMessage().contains("Expected expression."), empty.getMessage());
    }

    @Test
    void keywordRestMapReportsNonStringKey() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        "@function test($args...){@return 1} a{b: test((red: #D700EE)...)}",
                        Syntax.SCSS
                )
        );
        assertTrue(
                failure.getMessage().contains("red is not a string in (red:"),
                failure.getMessage()
        );
    }

    @Test
    void emptyUnknownAtRuleIsBlocklessInIndentedSyntax() throws Exception {
        assertEquals(
                "@supports (a: b) {\n  @d;\n}".strip(),
                compile(
                        """
                                @supports (a: b)
                                  @d
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void plainCssLeadingCombinatorRejectedThroughNestedImport(@TempDir Path directory)
            throws Exception {
        Files.writeString(directory.resolve("plain.css"), "> b {c: d}\n");
        Files.writeString(directory.resolve("input.scss"), "a {@import \"plain\"}\n");
        var failure = assertThrows(
                Exception.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("input.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertTrue(
                failure.getMessage().contains(
                        "Top-level leading combinators aren't allowed in plain CSS."
                ),
                failure.getMessage()
        );
    }

    @Test
    void contentInsideAtRootAcceptsContentBlock() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                @mixin bar() {
                                  @at-root { @content; }
                                }
                                .test {
                                  @include bar() {
                                    color: yellow;
                                  }
                                }
                                """,
                        Syntax.SCSS
                )
        );
        assertTrue(
                failure.getMessage().contains("Declarations may only be used within style rules."),
                failure.getMessage()
        );
    }

    @Test
    void supportsAnythingAllowsSemicolonsAndSymbols() throws Exception {
        assertTrue(
                compile(
                        "@supports (a !&$ZH()&;*{&A}_=-+#/><) {@b}",
                        Syntax.SCSS
                ).contains("@supports (a !&$ZH()&;*{&A}_=-+#/><)")
        );
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
