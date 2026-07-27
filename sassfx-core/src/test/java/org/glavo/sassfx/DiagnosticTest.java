// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies structured compilation diagnostics.
@NotNullByDefault
final class DiagnosticTest {
    /// Verifies exception message, cause, ordering, and defensive copying.
    @Test
    void preservesStructuredDiagnostics() {
        var primary = new Diagnostic(DiagnosticSeverity.ERROR, "Expected expression.", null, "parse");
        var secondary = new Diagnostic(DiagnosticSeverity.WARNING, "Secondary.", null, null);
        var source = new ArrayList<>(List.of(primary, secondary));
        var cause = new IllegalStateException("cause");

        var exception = new SassCompilationException(source, cause);
        source.clear();

        assertEquals("Expected expression.", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertSame(primary, exception.primaryDiagnostic());
        assertEquals(List.of(primary, secondary), exception.diagnostics());
        assertEquals(List.of(), exception.sassTrace());
        assertThrows(UnsupportedOperationException.class, () -> exception.diagnostics().clear());
    }

    /// Verifies automatic root-stylesheet trace construction.
    @Test
    void derivesRootStylesheetTrace() {
        var span = new SourceSpan(
                URI.create("file:///style.scss"),
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, 1, 1),
                "x"
        );
        var diagnostic = new Diagnostic(DiagnosticSeverity.ERROR, "Error.", span, null);
        var exception = new SassCompilationException(List.of(diagnostic));

        assertEquals(
                List.of(new SassStackFrame("root stylesheet", span)),
                exception.sassTrace()
        );
    }

    /// Verifies that a compilation failure must begin with an error diagnostic.
    @Test
    void rejectsInvalidPrimaryDiagnostics() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SassCompilationException(List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SassCompilationException(List.of(
                        new Diagnostic(DiagnosticSeverity.WARNING, "Warning.", null, null)
                ))
        );
    }
}
