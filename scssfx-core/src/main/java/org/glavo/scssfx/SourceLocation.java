// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies a position in a stylesheet source.
///
/// Lines and columns are zero-based. The offset and column are measured in
/// UTF-16 code units, matching indices used by [String].
///
/// @param line the zero-based line index
/// @param column the zero-based UTF-16 column index
/// @param offset the zero-based UTF-16 offset from the beginning of the source
@NotNullByDefault
public record SourceLocation(int line, int column, int offset) {
    /// Creates a source location after validating its indices.
    ///
    /// @throws IllegalArgumentException if any index is negative
    public SourceLocation {
        if (line < 0) {
            throw new IllegalArgumentException("line must not be negative");
        }
        if (column < 0) {
            throw new IllegalArgumentException("column must not be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
