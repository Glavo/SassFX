// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceLocation;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the JavaFX font grammar without loading JavaFX classes.
@NotNullByDefault
final class JavaFXFontParserTest {
    /// Normalizes generic family names while preserving ordinary family tokens.
    @Test
    void parsesFontFamilies() {
        assertEquals("serif", JavaFXFontParser.parseFamily("\"SERIF\"", span("\"SERIF\"")));
        assertEquals("monospace", JavaFXFontParser.parseFamily("MONOSPACE", span("MONOSPACE")));
        assertEquals(
                "\"Example Sans\"",
                JavaFXFontParser.parseFamily(
                        "\"Example Sans\"",
                        span("\"Example Sans\"")
                )
        );
        assertEquals("Example", JavaFXFontParser.parseFamily("Example", span("Example")));
    }

    /// Expands font-size keywords and retains every OpenJFX font-size unit.
    @Test
    void parsesFontSizes() {
        assertEquals(
                new JavaFXFontParser.Size(120.0, "%"),
                JavaFXFontParser.parseSize("large", span("large"))
        );
        assertEquals(
                new JavaFXFontParser.Size(12.0, null),
                JavaFXFontParser.parseSize("12", span("12"))
        );
        assertEquals(
                new JavaFXFontParser.Size(0.5, "em"),
                JavaFXFontParser.parseSize(".5EM", span(".5EM"))
        );
        assertEquals(
                new JavaFXFontParser.Size(45.0, "deg"),
                JavaFXFontParser.parseSize("45deg", span("45deg"))
        );
    }

    /// Accepts every numeric unit routed to OpenJFX's font-size parser.
    ///
    /// @param value one supported numeric size
    @ParameterizedTest
    @ValueSource(strings = {
            "1",
            "1%",
            "1em",
            "1ex",
            "1px",
            "1cm",
            "1mm",
            "1in",
            "1pt",
            "1pc",
            "1deg",
            "1grad",
            "1rad",
            "1turn"
    })
    void parsesEveryFontSizeUnit(String value) {
        JavaFXFontParser.parseSize(value, span(value));
    }

    /// Canonicalizes JavaFX font style and weight tokens for BSS storage.
    @Test
    void parsesFontStyleAndWeight() {
        assertEquals("REGULAR", JavaFXFontParser.parseStyle("normal", span("normal")));
        assertEquals("ITALIC", JavaFXFontParser.parseStyle("oblique", span("oblique")));
        assertEquals("inherit", JavaFXFontParser.parseStyle("inherit", span("inherit")));
        assertEquals("LIGHT", JavaFXFontParser.parseWeight("lighter", span("lighter")));
        assertEquals("SEMI_BOLD", JavaFXFontParser.parseWeight("600", span("600")));
        assertEquals("NORMAL", JavaFXFontParser.parseWeight("inherit", span("inherit")));
    }

    /// Parses all retained and discarded parts of a JavaFX font shorthand.
    @Test
    void parsesFontShorthand() {
        var text = "bold/**/inherit small-caps 14deg / 18grad \"SERIF\"";
        var font = JavaFXFontParser.parseShorthand(text, span(text));

        assertEquals("serif", font.family());
        assertEquals(new JavaFXFontParser.Size(14.0, "deg"), font.size());
        assertEquals("BOLD", font.weight());
        assertEquals("inherit", font.style());
    }

    /// Preserves OpenJFX's token-only check for discarded line heights.
    @Test
    void acceptsIdentifierLineHeight() {
        var text = "12px/not-a-size Example";
        var font = JavaFXFontParser.parseShorthand(text, span(text));

        assertEquals(new JavaFXFontParser.Size(12.0, "px"), font.size());
        assertEquals("Example", font.family());
    }

    /// Recognizes only complete unquoted JavaFX global keywords.
    @Test
    void recognizesGlobalKeywords() {
        assertTrue(JavaFXFontParser.isGlobalKeyword("/**/ INHERIT /**/", span("inherit")));
        assertTrue(JavaFXFontParser.isGlobalKeyword("none", span("none")));
        assertTrue(JavaFXFontParser.isGlobalKeyword("null", span("null")));
        assertFalse(JavaFXFontParser.isGlobalKeyword("\"inherit\"", span("\"inherit\"")));
        assertFalse(JavaFXFontParser.isGlobalKeyword("inherit serif", span("inherit serif")));
    }

    /// Rejects token sequences outside OpenJFX's font shorthand grammar.
    ///
    /// @param value the invalid shorthand
    @ParameterizedTest
    @ValueSource(strings = {
            "Example",
            "italic bold",
            "700 12px Example",
            "inherit bold 12px Example",
            "italic italic 12px Example",
            "italic 12px Example Sans",
            "12px/\"normal\" Example",
            "12px, Example"
    })
    void rejectsInvalidFontShorthand(String value) {
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXFontParser.parseShorthand(value, span(value))
        );
    }

    /// Rejects values outside the individual JavaFX font property grammars.
    @Test
    void rejectsInvalidFontLonghands() {
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXFontParser.parseFamily(
                        "Example Sans",
                        span("Example Sans")
                )
        );
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXFontParser.parseSize("1s", span("1s"))
        );
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXFontParser.parseStyle("\"italic\"", span("\"italic\""))
        );
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXFontParser.parseWeight("100.0", span("100.0"))
        );
    }

    /// Creates a synthetic source span for parser diagnostics.
    ///
    /// @param text the covered text
    /// @return the source span
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
