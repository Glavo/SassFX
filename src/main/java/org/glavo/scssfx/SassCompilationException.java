// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Reports checked compilation failure together with structured diagnostics.
@NotNullByDefault
public final class SassCompilationException extends Exception {
    /// The serialization version of this exception representation.
    private static final long serialVersionUID = 1L;

    /// The immutable, nonempty diagnostics associated with the failure.
    private final @Unmodifiable List<Diagnostic> diagnostics;

    /// The immutable Sass call trace associated with the failure.
    private final @Unmodifiable List<SassStackFrame> sassTrace;

    /// Creates a compilation exception without an underlying cause.
    ///
    /// A root-stylesheet trace frame is derived from the primary diagnostic
    /// when it has a source span.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    public SassCompilationException(List<? extends Diagnostic> diagnostics) {
        this(diagnostics, defaultTrace(diagnostics), null);
    }

    /// Creates a compilation exception with an optional underlying cause.
    ///
    /// A root-stylesheet trace frame is derived from the primary diagnostic
    /// when it has a source span.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            @Nullable Throwable cause
    ) {
        this(diagnostics, defaultTrace(diagnostics), cause);
    }

    /// Creates a compilation exception with an explicit Sass call trace.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param sassTrace the Sass call trace from the failure site outward
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the diagnostic list is empty or its
    /// first diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            List<? extends SassStackFrame> sassTrace,
            @Nullable Throwable cause
    ) {
        super(messageOf(diagnostics), cause);
        this.diagnostics = List.copyOf(diagnostics);
        this.sassTrace = List.copyOf(sassTrace);
    }

    /// Returns all diagnostics associated with this failure.
    ///
    /// @return an immutable, nonempty list in reporting order
    public @Unmodifiable List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /// Returns the first diagnostic associated with this failure.
    ///
    /// @return the primary diagnostic
    public Diagnostic primaryDiagnostic() {
        return diagnostics.get(0);
    }

    /// Returns the Sass call trace associated with this failure.
    ///
    /// The first element identifies the innermost active Sass member.
    ///
    /// @return an immutable list ordered from the failure site outward
    public @Unmodifiable List<SassStackFrame> sassTrace() {
        return sassTrace;
    }

    /// Returns the exception message derived from the first diagnostic.
    ///
    /// @param diagnostics the diagnostics to inspect
    /// @return the primary diagnostic message
    /// @throws IllegalArgumentException if {@code diagnostics} is empty
    private static String messageOf(List<? extends Diagnostic> diagnostics) {
        return primaryOf(diagnostics).message();
    }

    /// Returns the validated primary error diagnostic.
    ///
    /// @param diagnostics the diagnostics to inspect
    /// @return the first diagnostic
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    private static Diagnostic primaryOf(List<? extends Diagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.isEmpty()) {
            throw new IllegalArgumentException("diagnostics must not be empty");
        }
        var primary = Objects.requireNonNull(diagnostics.get(0), "diagnostics[0]");
        if (primary.severity() != DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException(
                    "the first diagnostic must have ERROR severity"
            );
        }
        return primary;
    }

    /// Creates a root trace from the primary diagnostic when possible.
    ///
    /// @param diagnostics the diagnostics to inspect
    /// @return an empty trace or one root-stylesheet frame
    private static @Unmodifiable List<SassStackFrame> defaultTrace(
            List<? extends Diagnostic> diagnostics
    ) {
        @Nullable SourceSpan span = primaryOf(diagnostics).span();
        return span == null
                ? List.of()
                : List.of(new SassStackFrame("root stylesheet", span));
    }
}
