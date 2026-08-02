// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.diagnostic.DiagnosticCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.Serial;
import java.util.Objects;

/// Reports an internal parse failure associated with an exact source span.
///
/// Parser entry points must translate this exception into the checked public
/// compilation failure model before returning to callers. Use a specific
/// [DiagnosticCode] whenever callers need to distinguish the failure without
/// inspecting English message text.
@ApiStatus.Internal
@NotNullByDefault
public final class ParseException extends RuntimeException {
    /// The serialization version of this exception representation.
    @Serial
    private static final long serialVersionUID = 1L;

    /// The source range associated with the parse failure.
    private final SourceSpan span;

    /// The stable diagnostic code.
    private final DiagnosticCode code;

    /// Creates a generic parse failure with a pre-rendered message.
    ///
    /// Use [#ParseException(DiagnosticCode, String, SourceSpan)] when a more
    /// specific stable code is available.
    ///
    /// @param message the human-readable failure message
    /// @param span the source range associated with the failure
    public ParseException(String message, SourceSpan span) {
        this(DiagnosticCode.PARSE_ERROR, message, span);
    }

    /// Creates a parse failure with a structured diagnostic code.
    ///
    /// @param code the stable diagnostic code
    /// @param message the human-readable failure message
    /// @param span the source range associated with the failure
    public ParseException(
            DiagnosticCode code,
            String message,
            SourceSpan span
    ) {
        super(Objects.requireNonNull(message, "message"));
        this.span = Objects.requireNonNull(span, "span");
        this.code = Objects.requireNonNull(code, "code");
    }

    /// Returns the source range associated with this failure.
    ///
    /// @return the exact failure span
    public SourceSpan span() {
        return span;
    }

    /// Returns the structured diagnostic code.
    ///
    /// @return the diagnostic code
    public DiagnosticCode code() {
        return code;
    }
}
