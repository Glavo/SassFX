// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.DiagnosticCode;
import org.glavo.scssfx.DiagnosticMessages;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;

/// Reports an internal parse failure associated with an exact source span.
///
/// Parser entry points must translate this exception into the checked public
/// compilation failure model before returning to callers. Prefer constructors
/// that accept a [DiagnosticCode] so callers can branch without inspecting
/// English message text.
@ApiStatus.Internal
@NotNullByDefault
public final class ParseException extends RuntimeException {
    /// The serialization version of this exception representation.
    @Serial
    private static final long serialVersionUID = 1L;

    /// The source range associated with the parse failure.
    private final SourceSpan span;

    /// The stable diagnostic code, or {@code null} for legacy string-only sites.
    private final @Nullable DiagnosticCode code;

    /// Creates a parse failure with a pre-rendered message.
    ///
    /// Prefer [#ParseException(DiagnosticCode, SourceSpan, Object...)] for new
    /// call sites.
    ///
    /// @param message the human-readable failure message
    /// @param span the source range associated with the failure
    public ParseException(String message, SourceSpan span) {
        this(DiagnosticCode.PARSE_ERROR, span, message);
    }

    /// Creates a parse failure from a structured diagnostic code.
    ///
    /// @param code the stable diagnostic code
    /// @param span the source range associated with the failure
    /// @param args format arguments for [DiagnosticMessages]
    public ParseException(DiagnosticCode code, SourceSpan span, Object... args) {
        super(DiagnosticMessages.render(
                Objects.requireNonNull(code, "code"),
                args
        ));
        this.span = Objects.requireNonNull(span, "span");
        this.code = code;
    }

    /// Returns the source range associated with this failure.
    ///
    /// @return the exact failure span
    public SourceSpan span() {
        return span;
    }

    /// Returns the structured diagnostic code when one was supplied.
    ///
    /// @return the code, or {@code null} for legacy string-only failures
    public @Nullable DiagnosticCode code() {
        return code;
    }
}
