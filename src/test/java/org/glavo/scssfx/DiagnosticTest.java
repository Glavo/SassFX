// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

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
        assertThrows(UnsupportedOperationException.class, () -> exception.diagnostics().clear());
    }

    /// Verifies that a compilation failure must contain a diagnostic.
    @Test
    void rejectsEmptyDiagnostics() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SassCompilationException(List.of())
        );
    }
}
