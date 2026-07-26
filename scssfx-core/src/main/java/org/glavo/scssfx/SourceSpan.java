// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Objects;

/// Describes a half-open range in a stylesheet source.
///
/// @param url the source URL, or {@code null} when the source has no stable URL
/// @param start the inclusive start location
/// @param end the exclusive end location
/// @param text the exact UTF-16 text covered by the range
@NotNullByDefault
public record SourceSpan(
        @Nullable URI url,
        SourceLocation start,
        SourceLocation end,
        String text
) {
    /// Creates a source span after validating its locations and captured text.
    ///
    /// @throws IllegalArgumentException if the end precedes the start or the
    /// captured text length differs from the offset range
    public SourceSpan {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(text, "text");

        var length = end.offset() - start.offset();
        if (length < 0) {
            throw new IllegalArgumentException("end must not precede start");
        }
        if (end.line() < start.line()
                || end.line() == start.line() && end.column() < start.column()) {
            throw new IllegalArgumentException(
                    "end line and column must not precede start line and column"
            );
        }
        if (text.length() != length) {
            throw new IllegalArgumentException(
                    "text length must equal the difference between end and start offsets"
            );
        }
    }
}
