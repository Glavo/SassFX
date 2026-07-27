// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies how elements in a public Sass list are separated.
@NotNullByDefault
public enum SassListSeparator {
    /// Space-separated elements.
    SPACE,

    /// Comma-separated elements.
    COMMA,

    /// Slash-separated elements.
    SLASH,

    /// An empty or singleton list without an established separator.
    UNDECIDED
}
