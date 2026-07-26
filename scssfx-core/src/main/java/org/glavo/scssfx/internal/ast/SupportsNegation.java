// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a `not` support condition.
///
/// @param condition the condition being negated
/// @param span the source range covering the negation
@ApiStatus.Internal
@NotNullByDefault
public record SupportsNegation(
        SupportsCondition condition,
        SourceSpan span
) implements SupportsCondition {
    /// Creates a negated support condition.
    public SupportsNegation {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(span, "span");
    }

    /// Returns a source-like representation of the negation.
    ///
    /// @return the `not` condition
    @Override
    public String toString() {
        return "not " + condition;
    }
}
