// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Identifies one active member in a Sass evaluation call trace.
///
/// @param member the member description, such as a mixin, function, or root stylesheet
/// @param span the source range from which the member was invoked
@NotNullByDefault
public record SassStackFrame(String member, SourceSpan span) {
    /// Creates a Sass call-trace frame.
    ///
    /// @throws IllegalArgumentException if {@code member} is blank
    public SassStackFrame {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(span, "span");
        if (member.isBlank()) {
            throw new IllegalArgumentException("member must not be blank");
        }
    }
}
