// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.source;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the generated parser-input range that produced a source span.
///
/// @param start the inclusive generated offset
/// @param end the exclusive generated offset
@ApiStatus.Internal
@NotNullByDefault
record GeneratedRange(int start, int end) {
    /// Validates one half-open generated range.
    GeneratedRange {
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative");
        }
        if (end < start) {
            throw new IllegalArgumentException("end must not precede start");
        }
    }
}
