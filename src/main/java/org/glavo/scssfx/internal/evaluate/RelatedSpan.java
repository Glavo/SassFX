// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Associates an auxiliary source range with its diagnostic label.
///
/// @param span  the related source range
/// @param label the role of the range in the failure
@ApiStatus.Internal
@NotNullByDefault
public record RelatedSpan(SourceSpan span, String label) {
    /// Creates a related source range.
    ///
    /// @throws IllegalArgumentException if {@code label} is blank
    public RelatedSpan {
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
