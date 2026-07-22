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

    /// Creates a compilation exception without an underlying cause.
    ///
    /// @param diagnostics the nonempty diagnostics associated with the failure
    /// @throws IllegalArgumentException if {@code diagnostics} is empty
    public SassCompilationException(List<? extends Diagnostic> diagnostics) {
        this(diagnostics, null);
    }

    /// Creates a compilation exception with an optional underlying cause.
    ///
    /// @param diagnostics the nonempty diagnostics associated with the failure
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if {@code diagnostics} is empty
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            @Nullable Throwable cause
    ) {
        super(messageOf(diagnostics), cause);
        this.diagnostics = List.copyOf(diagnostics);
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

    /// Returns the exception message derived from the first diagnostic.
    ///
    /// @param diagnostics the diagnostics to inspect
    /// @return the primary diagnostic message
    /// @throws IllegalArgumentException if {@code diagnostics} is empty
    private static String messageOf(List<? extends Diagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.isEmpty()) {
            throw new IllegalArgumentException("diagnostics must not be empty");
        }
        return Objects.requireNonNull(diagnostics.get(0), "diagnostics[0]").message();
    }
}
