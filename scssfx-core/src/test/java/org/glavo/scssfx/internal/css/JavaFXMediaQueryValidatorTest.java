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
