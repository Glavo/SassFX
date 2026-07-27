// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Verifies structured diagnostic codes render without throw-site string branches.
@NotNullByDefault
final class DiagnosticCodeTest {
    @Test
    void rendersIndentedNestingWithoutHeader() {
        assertEquals(
                "Indented Sass statements must be nested below a block header.",
                DiagnosticMessages.render(DiagnosticCode.INDENTED_NESTING_WITHOUT_HEADER)
        );
    }

    @Test
    void errorDiagnosticCarriesCodeName() {
        var diagnostic = DiagnosticMessages.error(
                DiagnosticCode.EVALUATION_ERROR,
                null,
                "boom"
        );
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
        assertEquals("boom", diagnostic.message());
        assertEquals("EVALUATION_ERROR", diagnostic.code());
    }

    @Test
    void parseFailuresExposeCodeThroughCompiler() {
        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                """
                                          b: c
                                        """,
                                Syntax.SASS
                        ),
                        CssTarget.DEFAULT
                )
        );
        assertEquals(
                "INDENTED_NESTING_WITHOUT_HEADER",
                failure.primaryDiagnostic().code()
        );
    }
}
