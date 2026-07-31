// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceLocation;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.glavo.sassfx.internal.css.JavaFXFontFaceParser.Source;
import static org.glavo.sassfx.internal.css.JavaFXFontFaceParser.SourceType.LOCAL;
import static org.glavo.sassfx.internal.css.JavaFXFontFaceParser.SourceType.REFERENCE;
import static org.glavo.sassfx.internal.css.JavaFXFontFaceParser.SourceType.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies JavaFX font-face source parsing and descriptor persistence text.
@NotNullByDefault
final class JavaFXFontFaceParserTest {
    /// Parses URL, local, and reference sources around JavaFX trivia.
    @Test
    void parsesSupportedSourcesWithTrivia() {
        var text = """
                /**/ url("fonts/example\\-regular.woff2") /**/
                FoRmAt(/**/"woff2"/**/) /**/, // separator
                local(/**/"Example Local"/**/) /**/, ExampleReference /**/
                """;

        assertEquals(
                List.of(
                        new Source(URL, "fonts/example-regular.woff2", "woff2"),
                        new Source(LOCAL, "Example Local", null),
                        new Source(REFERENCE, "ExampleReference", null)
                ),
                JavaFXFontFaceParser.parseSources(text, span(text))
        );
    }

    /// Preserves ordinary-string backslashes and consumes escaped URL newlines.
    @Test
    void followsLegacyStringAndUrlEscapeRules() {
        var text = "url(font\\\r\n\nfile.woff), local(\"Example\\Path\")";

        assertEquals(
                List.of(
                        new Source(URL, "fontfile.woff", null),
                        new Source(LOCAL, "Example\\Path", null)
                ),
                JavaFXFontFaceParser.parseSources(text, span(text))
        );
    }

    /// Produces the token concatenation persisted by JavaFX font-face BSS.
    @Test
    void normalizesStoredDescriptorValues() {
        var value = "/**/ Oracle /**/ Sans // line\n ! /**/ ImPoRtAnT";
        assertEquals(
                "OracleSans!important",
                JavaFXFontFaceParser.storedDescriptorValue(value, span(value))
        );

        var urlValue = "url(\"meta\\-data\") /**/ suffix";
        assertEquals(
                "meta-datasuffix",
                JavaFXFontFaceParser.storedDescriptorValue(
                        urlValue,
                        span(urlValue)
                )
        );
    }

    /// Rejects descriptor URL tokens that consume structure or have no resource.
    ///
    /// @param value the unsafe descriptor text
    @ParameterizedTest
    @ValueSource(strings = {
            "url()",
            "url(\"\")",
            "url(font.woff",
            "url(\"font.woff\" suffix)"
    })
    void rejectsUnsafeDescriptorUrls(String value) {
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXFontFaceParser.storedDescriptorValue(
                        value,
                        span(value)
                )
        );
    }

    /// Rejects source forms that JavaFX rejects or silently reinterprets.
    ///
    /// @param value the unsupported source-list text
    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "url()",
            "URL(font.woff)",
            "local()",
            "local(Example Local)",
            "url(font.woff) format()",
            "url(font.woff) format(woff2 extra)",
            "url(font.woff) local(Example)",
            "url(font.woff),",
            "custom(Example)",
            "10",
            "// consumes the rule",
            "local(Example) /* unterminated"
    })
    void rejectsUnsafeSources(String value) {
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXFontFaceParser.parseSources(value, span(value))
        );
    }

    /// Creates a synthetic source span over complete in-memory text.
    ///
    /// @param text the represented source text
    /// @return a span from offset zero through the text length
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
