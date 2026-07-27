// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Describes one non-error event delivered to a [SassLogger].
///
/// @param diagnostic the processed diagnostic
/// @param sassTrace the immutable Sass call trace from the event site outward
/// @param deprecation the typed deprecation, or {@code null} for ordinary
///                    warnings and debug messages
@NotNullByDefault
public record SassLogEvent(
        Diagnostic diagnostic,
        @Unmodifiable List<SassStackFrame> sassTrace,
        @Nullable SassDeprecation deprecation
) {
    /// Creates an immutable log event.
    public SassLogEvent {
        Objects.requireNonNull(diagnostic, "diagnostic");
        sassTrace = List.copyOf(sassTrace);
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException(
                    "logger events must not contain error diagnostics"
            );
        }
        if ((diagnostic.severity() == DiagnosticSeverity.DEPRECATION)
                != (deprecation != null)) {
            throw new IllegalArgumentException(
                    "deprecation metadata must match diagnostic severity"
            );
        }
    }
}
