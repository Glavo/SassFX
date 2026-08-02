// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.evaluate;

import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.SassStackFrame;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.diagnostic.DiagnosticCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/// Reports an internal Sass evaluation failure with structured source context.
///
/// Compiler entry points must translate this unchecked internal failure into
/// the checked public compilation-failure model.
@ApiStatus.Internal
@NotNullByDefault
public final class EvaluationException extends RuntimeException {
    /// Contains the serialization version of this exception representation.
    @Serial
    private static final long serialVersionUID = 1L;

    /// Contains the primary error diagnostic.
    private final Diagnostic primaryDiagnostic;

    /// Contains auxiliary labeled source ranges.
    private final @Unmodifiable List<RelatedSpan> relatedSpans;

    /// Contains the Sass call trace from the failure site outward.
    private final @Unmodifiable List<SassStackFrame> sassTrace;

    /// Creates a root-stylesheet evaluation failure.
    ///
    /// @param message the human-readable failure message
    /// @param span    the primary failure range
    public EvaluationException(String message, SourceSpan span) {
        this(message, span, List.of(), null);
    }

    /// Creates a root-stylesheet evaluation failure with related ranges.
    ///
    /// @param message      the human-readable failure message
    /// @param span         the primary failure range
    /// @param relatedSpans auxiliary labeled ranges
    /// @param cause        the underlying value-layer failure, or {@code null}
    public EvaluationException(
            String message,
            SourceSpan span,
            List<? extends RelatedSpan> relatedSpans,
            @Nullable Throwable cause
    ) {
        this(
                new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        Objects.requireNonNull(message, "message"),
                        span,
                        classifyMessage(message).name()
                ),
                relatedSpans,
                List.of(new SassStackFrame("root stylesheet", span)),
                cause
        );
    }

    /// Maps a pre-rendered value-layer message onto a stable diagnostic code.
    private static DiagnosticCode classifyMessage(String message) {
        Objects.requireNonNull(message, "message");
        if (message.startsWith("Undefined operation ")) {
            return DiagnosticCode.UNDEFINED_OPERATION;
        }
        return DiagnosticCode.EVALUATION_ERROR;
    }

    /// Creates an evaluation failure with explicit diagnostic and trace data.
    ///
    /// @param primaryDiagnostic the primary error diagnostic
    /// @param relatedSpans      auxiliary labeled ranges
    /// @param sassTrace         the Sass call trace from the failure site outward
    /// @param cause             the underlying failure, or {@code null}
    /// @throws IllegalArgumentException if the primary diagnostic is not an error
    public EvaluationException(
            Diagnostic primaryDiagnostic,
            List<? extends RelatedSpan> relatedSpans,
            List<? extends SassStackFrame> sassTrace,
            @Nullable Throwable cause
    ) {
        super(Objects.requireNonNull(primaryDiagnostic, "primaryDiagnostic").message(), cause);
        if (primaryDiagnostic.severity() != DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException("primaryDiagnostic must have ERROR severity");
        }
        this.primaryDiagnostic = primaryDiagnostic;
        this.relatedSpans = List.copyOf(relatedSpans);
        this.sassTrace = List.copyOf(sassTrace);
    }

    /// Returns the primary error diagnostic.
    ///
    /// @return the error diagnostic
    public Diagnostic primaryDiagnostic() {
        return primaryDiagnostic;
    }

    /// Returns auxiliary labeled source ranges.
    ///
    /// @return an immutable list in reporting order
    public @Unmodifiable List<RelatedSpan> relatedSpans() {
        return relatedSpans;
    }

    /// Returns the Sass call trace.
    ///
    /// @return an immutable list from the failure site outward
    public @Unmodifiable List<SassStackFrame> sassTrace() {
        return sassTrace;
    }
}
