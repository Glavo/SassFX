package org.glavo.scssfx.css;

import org.glavo.scssfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies custom-property value whitespace matches dart-sass.
@NotNullByDefault
final class CustomPropertySerializationTest {
    /// Trailing newline before semicolon becomes a space.
    @Test
    void trailingNewlineBecomesSpaceBeforeSemicolon() throws Exception {
        var css = compile("""
                a {
                  --b: c
                ;
                }
                """);
        assertEquals("""
                a {
                  --b: c ;
                }""", css);
    }

    /// Value ending before the style-rule brace keeps a trailing space.
    @Test
    void trailingBeforeClosingBrace() throws Exception {
        var css = compile("""
                a {
                  --b: c
                }
                """);
        assertEquals("""
                a {
                  --b: c ;
                }""", css);
    }

    /// Multi-line brace values reindent relative to the declaration.
    @Test
    void bracketedValueReindents() throws Exception {
        var css = compile("""
                a {
                  --b: {
                    c: d
                  }
                }
                """);
        assertEquals("""
                a {
                  --b: {
                    c: d
                  } ;
                }""", css);
    }

    private static String compile(String scss) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(scss, Syntax.SCSS), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
