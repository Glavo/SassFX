// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Selects the textual formatting applied to generated CSS.
@NotNullByDefault
public enum OutputStyle {
    /// Emits human-readable CSS with indentation and line breaks.
    EXPANDED,

    /// Emits compact CSS with insignificant whitespace removed.
    COMPRESSED
}
