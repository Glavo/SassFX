// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.ByteBuffer;
import java.util.Objects;

/// Configures JavaFX binary stylesheet output.
///
/// @param compatibility the JavaFX compatibility level that determines the BSS version
@NotNullByDefault
public record BssTarget(JavaFxCompatibility compatibility) implements OutputTarget<ByteBuffer> {
    /// The default target compatible with JavaFX 17 BSS version 6.
    public static final BssTarget DEFAULT = new BssTarget(JavaFxCompatibility.JAVA_FX_17);

    /// Creates a binary stylesheet output target.
    public BssTarget {
        Objects.requireNonNull(compatibility, "compatibility");
    }

    /// Returns the binary stylesheet format version selected by this target.
    ///
    /// @return the positive BSS version number
    public int version() {
        return compatibility.bssVersion();
    }
}
