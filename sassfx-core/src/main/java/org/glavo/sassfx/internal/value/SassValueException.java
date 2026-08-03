// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.glavo.sassfx.internal.diagnostic.DiagnosticCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.Serial;
import java.util.Objects;

/// Reports an internal Sass value-operation failure without source context.
///
/// This exception does not carry source information. An evaluator must attach
/// the span of the expression that invoked the operation before exposing the
/// failure outside the value layer.
@ApiStatus.Internal
@NotNullByDefault
public final class SassValueException
        extends org.glavo.sassfx.SassValueException {
    /// Contains the serialization version of this exception representation.
    @Serial
    private static final long serialVersionUID = 1L;

    /// Contains the machine-readable category of this failure.
    private final DiagnosticCode diagnosticCode;

    /// Creates a value-operation failure.
    ///
    /// @param message the human-readable Sass failure message
    public SassValueException(String message) {
        this(message, DiagnosticCode.EVALUATION_ERROR);
    }

    /// Creates a value-operation failure with a stable diagnostic category.
    ///
    /// @param message the human-readable Sass failure message
    /// @param diagnosticCode the machine-readable failure category
    public SassValueException(
            String message,
            DiagnosticCode diagnosticCode
    ) {
        super(message);
        this.diagnosticCode = Objects.requireNonNull(
                diagnosticCode,
                "diagnosticCode"
        );
    }

    /// Creates an undefined-operation failure independently of its wording.
    ///
    /// @param message the human-readable Sass failure message
    /// @return the categorized value-operation failure
    public static SassValueException undefinedOperation(String message) {
        return new SassValueException(
                message,
                DiagnosticCode.UNDEFINED_OPERATION
        );
    }

    /// Returns the machine-readable failure category.
    ///
    /// @return the diagnostic code propagated by the evaluator
    public DiagnosticCode diagnosticCode() {
        return diagnosticCode;
    }
}
