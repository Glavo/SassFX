// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SassStackFrame;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/// Reports a failure while converting CSS IR into plain CSS text.
///
/// Compiler entry points must translate this unchecked internal failure into
/// the checked public compilation-failure model.
@ApiStatus.Internal
@NotNullByDefault
public final class CssSerializeException extends RuntimeException {
    /// Contains the serialization version of this exception representation.
    @Serial
    private static final long serialVersionUID = 1L;

    /// Contains the primary error diagnostic.
    private final Diagnostic primaryDiagnostic;

    /// Contains the Sass call trace from the failure site outward.
    private final @Unmodifiable List<SassStackFrame> sassTrace;

    /// Creates a root-stylesheet serialization failure.
    ///
    /// @param message the human-readable failure message
    /// @param span    the primary failure range
    /// @param cause   the underlying value-layer failure, or {@code null}
    public CssSerializeException(
            String message,
            SourceSpan span,
            @Nullable Throwable cause
    ) {
        this(
                new Diagnostic(DiagnosticSeverity.ERROR, message, span, null),
                List.of(new SassStackFrame("root stylesheet", span)),
                cause
        );
    }

    /// Creates a serialization failure with explicit diagnostic and trace data.
    ///
    /// @param primaryDiagnostic the primary error diagnostic
    /// @param sassTrace         the Sass call trace from the failure site outward
    /// @param cause             the underlying failure, or {@code null}
    public CssSerializeException(
            Diagnostic primaryDiagnostic,
            List<? extends SassStackFrame> sassTrace,
            @Nullable Throwable cause
    ) {
        super(Objects.requireNonNull(primaryDiagnostic, "primaryDiagnostic").message(), cause);
        if (primaryDiagnostic.severity() != DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException("primaryDiagnostic must have ERROR severity");
        }
        this.primaryDiagnostic = primaryDiagnostic;
        this.sassTrace = List.copyOf(sassTrace);
    }

    /// Returns the primary error diagnostic.
    ///
    /// @return the error diagnostic
    public Diagnostic primaryDiagnostic() {
        return primaryDiagnostic;
    }

    /// Returns the Sass call trace.
    ///
    /// @return an immutable list from the failure site outward
    public @Unmodifiable List<SassStackFrame> sassTrace() {
        return sassTrace;
    }
}
