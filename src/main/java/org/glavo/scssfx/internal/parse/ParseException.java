// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Reports an internal parse failure associated with an exact source span.
///
/// Parser entry points must translate this exception into the checked public
/// compilation failure model before returning to callers.
@NotNullByDefault
final class ParseException extends RuntimeException {
    /// The serialization version of this exception representation.
    private static final long serialVersionUID = 1L;

    /// The source range associated with the parse failure.
    private final SourceSpan span;

    /// Creates a parse failure.
    ///
    /// @param message the human-readable failure message
    /// @param span the source range associated with the failure
    ParseException(String message, SourceSpan span) {
        super(message);
        this.span = Objects.requireNonNull(span, "span");
    }

    /// Returns the source range associated with this failure.
    ///
    /// @return the exact failure span
    SourceSpan span() {
        return span;
    }
}
