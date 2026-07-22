// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.jetbrains.annotations.NotNullByDefault;

/// Captures a restorable UTF-16 scanner position.
///
/// @param position the zero-based UTF-16 source offset
@NotNullByDefault
record ScannerState(int position) {
    /// Creates scanner state for a nonnegative source offset.
    ///
    /// @throws IllegalArgumentException if {@code position} is negative
    ScannerState {
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
    }
}
