// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.evaluate;

import org.glavo.sassfx.SourceLocation;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.diagnostic.DiagnosticCode;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies structured evaluator failure categories.
@NotNullByDefault
final class EvaluationExceptionTest {
    /// An empty span sufficient for diagnostic construction.
    private static final SourceSpan SPAN = new SourceSpan(
            null,
            new SourceLocation(0, 0, 0),
            new SourceLocation(0, 0, 0),
            ""
    );

    /// Keeps diagnostic categories independent of human-readable wording.
    @Test
    void propagatesValueFailureCodeInsteadOfClassifyingMessage() {
        var misleadingMessage = new EvaluationException(
                "Undefined operation wording without a structured cause.",
                SPAN
        );
        assertEquals(
                DiagnosticCode.EVALUATION_ERROR.name(),
                misleadingMessage.primaryDiagnostic().code()
        );

        var cause = SassValueException.undefinedOperation(
                "This wording intentionally omits the historical prefix."
        );
        var categorizedFailure = new EvaluationException(
                cause.getMessage(),
                SPAN,
                List.of(),
                cause
        );
        assertEquals(
                DiagnosticCode.UNDEFINED_OPERATION.name(),
                categorizedFailure.primaryDiagnostic().code()
        );
    }
}
