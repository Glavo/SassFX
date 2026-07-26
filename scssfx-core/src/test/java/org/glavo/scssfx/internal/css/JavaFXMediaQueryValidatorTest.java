// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies the JavaFX media-condition subset without loading JavaFX.
@NotNullByDefault
final class JavaFXMediaQueryValidatorTest {
    /// Accepts media-condition forms and features implemented by JavaFX.
    ///
    /// @param query the accepted query-list text
    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "(width: 500px)",
            "(width: 50%)",
            "(min-height: -1%)",
            "(min-width: 20em)",
            "(height >= 480)",
            "(100px < width)",
            "(100px <= width < 1000px)",
            "(1000px > width >= 100px)",
            "(aspect-ratio: 1.5)",
            "(1 < aspect-ratio <= 2)",
            "(orientation: portrait)",
            "(display-mode: fullscreen)",
            "(prefers-color-scheme: dark)",
            "(prefers-reduced-motion)",
            "(prefers-reduced-motion: reduce)",
            "(prefers-reduced-motion: no-preference)",
            "(-fx-prefers-persistent-scrollbars: persistent)",
            "not (orientation: landscape)",
            "(width >= 500px) and (height >= 300px)",
            "(orientation: portrait) or (orientation: landscape)",
            "((width >= 500px) and (height >= 300px))",
            "(width: 10cm), (prefers-color-scheme: light)",
            "/* leading */ (width: 1in) /* trailing */",
            "(width: 1px) // trailing"
    })
    void acceptsSupportedQueries(String query) {
        assertDoesNotThrow(() -> JavaFXMediaQueryValidator.validate(
                query,
                span(query),
                JavaFXCompatibility.JAVAFX27
        ));
    }

    /// Rejects media types, unsupported features, invalid values, and malformed logic.
    ///
    /// @param query the rejected query-list text
    @ParameterizedTest
    @ValueSource(strings = {
            "screen",
            "screen and (width >= 500px)",
            "only screen and (width >= 500px)",
            "(width: 1rem)",
            "(width >= 50%)",
            "(width: 1e2px)",
            "(aspect-ratio: 1.)",
            "(aspect-ratio: 16/9)",
            "(aspect-ratio: 2px)",
            "(orientation)",
            "(orientation: square)",
            "(display-mode: browser)",
            "(prefers-color-scheme)",
            "(prefers-color-scheme: no-preference)",
            "(prefers-reduced-motion: enabled)",
            "(unknown-feature: true)",
            "(min-unknown: 1px)",
            "(100px < width > 200px)",
            "(width >= 500px) and (height >= 300px) or (orientation: portrait)",
            "(width >= 500px),",
            "rgb(1, 2, 3)",
            "(width: calc(1px))",
            "/* unterminated"
    })
    void rejectsUnsupportedQueries(String query) {
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXMediaQueryValidator.validate(
                        query,
                        span(query),
                        JavaFXCompatibility.JAVAFX27
                )
        );
    }

    /// Reports the caller-supplied source span in the primary diagnostic.
    @Test
    void preservesFailureSpan() {
        var query = "screen";
        var span = span(query);
        var failure = assertThrows(
                CssSerializeException.class,
                () -> JavaFXMediaQueryValidator.validate(
                        query,
                        span,
                        JavaFXCompatibility.JAVAFX27
                )
        );

        assertSame(span, failure.primaryDiagnostic().span());
        assertEquals("Expected '('", failure.getMessage());
    }

    /// Applies the JavaFX 25, 26, and 27 media-feature boundaries.
    @Test
    void validatesVersionedMediaFeatures() {
        var preference = "(prefers-color-scheme: dark)";
        assertDoesNotThrow(() -> JavaFXMediaQueryValidator.validate(
                preference,
                span(preference),
                JavaFXCompatibility.JAVAFX25
        ));

        var viewport = "(width >= 500px)";
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXMediaQueryValidator.validate(
                        viewport,
                        span(viewport),
                        JavaFXCompatibility.JAVAFX25
                )
        );
        assertDoesNotThrow(() -> JavaFXMediaQueryValidator.validate(
                viewport,
                span(viewport),
                JavaFXCompatibility.JAVAFX26
        ));

        for (var query : new String[]{
                "(-fx-supports-conditional-feature: scene3d)",
                "(-fx-platform: windows)"
        }) {
            assertThrows(
                    CssSerializeException.class,
                    () -> JavaFXMediaQueryValidator.validate(
                            query,
                            span(query),
                            JavaFXCompatibility.JAVAFX26
                    )
            );
            assertDoesNotThrow(() -> JavaFXMediaQueryValidator.validate(
                    query,
                    span(query),
                    JavaFXCompatibility.JAVAFX27
            ));
        }
    }

    /// Lowers discrete and range syntax to the canonical JavaFX media AST.
    @Test
    void parsesBinaryMediaExpressions() {
        var query = "(min-width: 10em), (400px <= width < 800px)";
        var parsed = JavaFXMediaQueryValidator.parse(
                query,
                span(query),
                JavaFXCompatibility.JAVAFX26
        );

        assertEquals(2, parsed.alternatives().size());
        var minimum = assertInstanceOf(
                JavaFXMediaQuery.Range.class,
                parsed.alternatives().get(0)
        );
        assertEquals(JavaFXMediaQuery.Comparison.GREATER_OR_EQUAL, minimum.comparison());
        assertEquals("width", minimum.name());
        assertEquals(10, minimum.value());
        assertEquals(JavaFXMediaQuery.Unit.EM, minimum.unit());

        var interval = assertInstanceOf(
                JavaFXMediaQuery.Conjunction.class,
                parsed.alternatives().get(1)
        );
        var lower = assertInstanceOf(
                JavaFXMediaQuery.Range.class,
                interval.left()
        );
        var upper = assertInstanceOf(
                JavaFXMediaQuery.Range.class,
                interval.right()
        );
        assertEquals(JavaFXMediaQuery.Comparison.GREATER_OR_EQUAL, lower.comparison());
        assertEquals(400, lower.value());
        assertEquals(JavaFXMediaQuery.Unit.PX, lower.unit());
        assertEquals(JavaFXMediaQuery.Comparison.LESS, upper.comparison());
        assertEquals(800, upper.value());
        assertEquals(JavaFXMediaQuery.Unit.PX, upper.unit());
    }

    /// Preserves boolean-context features as null-valued function expressions.
    @Test
    void parsesBooleanMediaFeature() {
        var query = "(prefers-reduced-motion)";
        var parsed = JavaFXMediaQueryValidator.parse(
                query,
                span(query),
                JavaFXCompatibility.JAVAFX25
        );
        var feature = assertInstanceOf(
                JavaFXMediaQuery.Feature.class,
                parsed.alternatives().get(0)
        );

        assertEquals("prefers-reduced-motion", feature.name());
        assertNull(feature.value());
    }

    /// Creates a source span covering the complete query text.
    ///
    /// @param text the query text
    /// @return the corresponding synthetic span
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
