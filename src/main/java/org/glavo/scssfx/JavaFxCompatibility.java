// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Selects a JavaFX CSS and binary stylesheet compatibility level.
@NotNullByDefault
public enum JavaFXCompatibility {
    /// Targets the binary stylesheet format used by JavaFX 17.
    JAVA_FX_17(6),

    /// Targets the binary stylesheet format used by JavaFX 27.
    JAVA_FX_27(9);

    /// The binary stylesheet format version associated with this compatibility level.
    private final int bssVersion;

    /// Creates a compatibility level for the given binary stylesheet version.
    ///
    /// @param bssVersion the binary stylesheet format version
    JavaFXCompatibility(int bssVersion) {
        this.bssVersion = bssVersion;
    }

    /// Returns the binary stylesheet format version.
    ///
    /// @return the positive BSS version number
    public int bssVersion() {
        return bssVersion;
    }
}
