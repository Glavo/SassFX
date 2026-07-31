// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceLocation;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies special JavaFX scalar grammar without loading JavaFX classes.
@NotNullByDefault
final class JavaFXScalarParserTest {
    /// Canonicalizes complete global keywords for every property.
    @Test
    void parsesGlobalKeywords() {
        assertEquals(
                new JavaFXScalarParser.GlobalKeyword("inherit"),
                parse("-fx-opacity", "/**/ InHeRiT /**/")
        );
        assertEquals(
                new JavaFXScalarParser.GlobalKeyword("null"),
                parse("-fx-fill", "NONE")
        );
        assertEquals(
                new JavaFXScalarParser.GlobalKeyword("null"),
                parse("-fx-font", "null")
        );
    }

    /// Rejects suffixes that OpenJFX would ignore after a global keyword.
    ///
    /// @param value the unsafe declaration value
    @ParameterizedTest
    @ValueSource(strings = {
            "inherit red",
            "none, blue",
            "null/**/ 1px"
    })
    void rejectsSurplusGlobalKeywordTerms(String value) {
        assertThrows(
                CssSerializeException.class,
                () -> parse("-fx-custom", value)
        );
    }

    /// Retains one identifier or the unquoted contents of one legacy string.
    @Test
    void parsesStoredStrings() {
        assertEquals(
                new JavaFXScalarParser.StoredString("GRAY"),
                parse("-fx-font-smoothing-type", "/**/ \"GRAY\" /**/")
        );
        assertEquals(
                new JavaFXScalarParser.StoredString(""),
                parse("-fx-blend-mode", "''")
        );
        assertEquals(
                new JavaFXScalarParser.StoredString("MULTIPLY"),
                parse("-fx-blend-mode", "MULTIPLY")
        );
    }

    /// Distinguishes JavaFX 8–17 generic blend parsing from JavaFX 18 storage.
    @Test
    void parsesLegacyBlendStrings() {
        var value = "MULTIPLY";
        assertEquals(
                new JavaFXScalarParser.LegacyString(value),
                JavaFXScalarParser.parse(
                        "-fx-blend-mode",
                        value,
                        span(value),
                        JavaFXTarget.JAVAFX17
                )
        );
        assertEquals(
                new JavaFXScalarParser.StoredString(value),
                JavaFXScalarParser.parse(
                        "-fx-blend-mode",
                        value,
                        span(value),
                        JavaFXTarget.JAVAFX18
                )
        );
        assertNull(JavaFXScalarParser.parse(
                "-fx-blend-mode",
                "#123456",
                span("#123456"),
                JavaFXTarget.JAVAFX17
        ));
    }

    /// Rejects missing, non-string, and surplus stored-string values.
    @Test
    void rejectsInvalidStoredStrings() {
        for (var value : List.of("", "1px", "gray ignored", "\"gray\" ignored")) {
            assertThrows(
                    CssSerializeException.class,
                    () -> parse("-fx-font-smoothing-type", value),
                    value
            );
        }
    }

    /// Canonicalizes every stroke enum and records its BSS enum class.
    @Test
    void parsesStrokeEnums() {
        assertEquals(
                new JavaFXScalarParser.EnumValue(
                        "round",
                        "javafx.scene.shape.StrokeLineCap"
                ),
                parse("-fx-stroke-line-cap", "ROUND")
        );
        assertEquals(
                new JavaFXScalarParser.EnumValue(
                        "bevel",
                        "javafx.scene.shape.StrokeLineJoin"
                ),
                parse("-fx-stroke-line-join", "BeVeL")
        );
        assertEquals(
                new JavaFXScalarParser.EnumValue(
                        "inside",
                        "javafx.scene.shape.StrokeType"
                ),
                parse("-fx-stroke-type", "INSIDE")
        );
    }

    /// Rejects invalid, quoted, and silently truncated stroke enums.
    @Test
    void rejectsInvalidStrokeEnums() {
        for (var value : List.of("triangle", "\"round\"", "round ignored")) {
            assertThrows(
                    CssSerializeException.class,
                    () -> parse("-fx-stroke-line-cap", value),
                    value
            );
        }
        assertThrows(
                CssSerializeException.class,
                () -> parse("-fx-stroke-line-join", "miter 10px")
        );
    }

    /// Parses every JavaFX scalar size unit in a stroke dash sequence.
    @Test
    void parsesStrokeDashArray() {
        assertEquals(
                new JavaFXScalarParser.SizeSequence(List.of(
                        new JavaFXScalarParser.Size(1.0, null),
                        new JavaFXScalarParser.Size(2.0, "%"),
                        new JavaFXScalarParser.Size(-3.5, "em"),
                        new JavaFXScalarParser.Size(4.0, "deg"),
                        new JavaFXScalarParser.Size(5.0, "turn")
                )),
                parse(
                        "-fx-stroke-dash-array",
                        "1/**/2% -3.5EM 4DEG 5turn"
                )
        );
    }

    /// Rejects empty, layered, and non-size stroke dash values.
    @Test
    void rejectsInvalidStrokeDashArrays() {
        for (var value : List.of(
                "",
                "red",
                "1ms",
                "1px, 2px",
                "1px / 2px"
        )) {
            assertThrows(
                    CssSerializeException.class,
                    () -> parse("-fx-stroke-dash-array", value),
                    value
            );
        }
    }

    /// Leaves ordinary non-global properties to the generic value parser.
    @Test
    void ignoresOtherPropertyGrammars() {
        assertNull(parse("-fx-opacity", "0.5"));
        assertNull(parse("-fx-padding", "1px 2px"));
    }

    /// Parses one value with a matching synthetic source span.
    ///
    /// @param property the normalized declaration name
    /// @param value    the declaration value
    /// @return the parsed scalar or `null`
    private static @Nullable JavaFXScalarParser.Value parse(
            String property,
            String value
    ) {
        return JavaFXScalarParser.parse(
                property,
                value,
                span(value),
                JavaFXTarget.JAVAFX27
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
