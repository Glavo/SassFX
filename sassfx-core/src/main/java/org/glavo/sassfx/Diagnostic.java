// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Describes a compiler message and its optional source association.
///
/// @param severity the significance of the message
/// @param message the human-readable message
/// @param span the associated source range, or {@code null} when unavailable
/// @param code a stable machine-readable identifier, or {@code null} when none is assigned
@NotNullByDefault
public record Diagnostic(
        DiagnosticSeverity severity,
        String message,
        @Nullable SourceSpan span,
        @Nullable String code
) {
    /// Creates a diagnostic.
    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }
}
