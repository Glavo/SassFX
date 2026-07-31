// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the lexical boundary imposed by JavaFX's legacy CSS parser.
@NotNullByDefault
final class JavaFXCssLexerTest {
    /// Accepts complete identifiers from JavaFX's ASCII grammar.
    ///
    /// @param text the accepted identifier
    @ParameterizedTest
    @ValueSource(strings = {
            "property",
            "_property",
            "-property",
            "A0_B-c",
            "linear-gradientSuffix"
    })
    void acceptsJavaFXIdentifiers(String text) {
        assertTrue(JavaFXCssLexer.isIdentifier(text));
    }

    /// Rejects identifiers from modern CSS syntax that the JavaFX lexer does
    /// not implement.
    ///
    /// @param text the rejected identifier
    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "-",
            "--custom",
            "0property",
            "property.name",
            "property\\31",
            "属性"
    })
    void rejectsUnsupportedIdentifiers(String text) {
        assertFalse(JavaFXCssLexer.isIdentifier(text));
    }

    /// Distinguishes the broader hash-name continuation grammar.
    @Test
    void validatesHashNames() {
        assertTrue(JavaFXCssLexer.isHashName("123-abc"));
        assertTrue(JavaFXCssLexer.isHashName("--"));
        assertFalse(JavaFXCssLexer.isHashName(""));
        assertFalse(JavaFXCssLexer.isHashName("abc.def"));
        assertFalse(JavaFXCssLexer.isHashName("颜色"));
    }

    /// Returns the exclusive end of an identifier within a larger value.
    @Test
    void locatesIdentifierEnd() {
        assertEquals(10, JavaFXCssLexer.identifierEnd("  -fx-base(", 2));
        assertEquals(0, JavaFXCssLexer.identifierEnd("--custom", 0));
        assertEquals(8, JavaFXCssLexer.identifierEnd("property", 8));
    }

    /// Locates complete JavaFX whitespace and comment trivia.
    @Test
    void locatesTriviaEnd() {
        var text = " \t/* block */// line\nred";
        assertEquals(text.indexOf("red"), JavaFXCssLexer.triviaEnd(text, 0));
        assertEquals(-1, JavaFXCssLexer.triviaEnd("/* unterminated", 0));
        assertEquals(-1, JavaFXCssLexer.triviaEnd("// unterminated", 0));
        assertEquals(-1, JavaFXCssLexer.triviaEnd("// comment\rred", 0));
    }

    /// Locates complete JavaFX importance tokens.
    @Test
    void locatesImportanceEnd() {
        assertEquals(
                "!/**/important".length(),
                JavaFXCssLexer.importanceEnd("!/**/important", 0)
        );
        assertEquals(
                "! // priority\n IMPORTANT".length(),
                JavaFXCssLexer.importanceEnd(
                        "! // priority\n IMPORTANT",
                        0
                )
        );
        assertEquals(-1, JavaFXCssLexer.importanceEnd("!// priority", 0));
        assertEquals(-1, JavaFXCssLexer.importanceEnd("!urgent", 0));
    }

    /// Accepts tokenizable values, including opaque Unicode strings and URLs.
    ///
    /// @param text the tokenizable value
    @ParameterizedTest
    @ValueSource(strings = {
            "red",
            "-fx-base",
            "linear-gradient(red, #123456 50%, blue)",
            "\"字体\"",
            "'π'",
            "\"line\nbreak\"",
            "\"form\ffeed\"",
            "url(字体/icon.svg)",
            "url(\"字体/icon.svg\")",
            "1px 250ms 45deg",
            "red ! /* priority */ IMPORTANT",
            "red/**/blue",
            "red // comment\nblue",
            "red ! // priority\n IMPORTANT"
    })
    void acceptsTokenizableValues(String text) {
        assertTrue(JavaFXCssLexer.isTokenizableValue(text));
    }

    /// Rejects value tokens unavailable from JavaFX's legacy lexer.
    ///
    /// @param text the rejected value
    @ParameterizedTest
    @ValueSource(strings = {
            "π",
            "--custom",
            "\\66 oo",
            "linear-gradientπ(red, blue)",
            "linear-gradient(red, π)",
            "URL(\"icon.png\")",
            "1rem",
            "1e3",
            "[1px 2px]",
            "#",
            "\"unterminated",
            "url(foo(bar))",
            "red /* unterminated",
            "red // unterminated",
            "red // comment\rblue",
            "+"
    })
    void rejectsUntokenizableValues(String text) {
        assertFalse(JavaFXCssLexer.isTokenizableValue(text));
    }
}
