// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.util.Objects;

/// Configures JavaFX binary stylesheet output.
///
/// A successful compilation produces a read-only buffer whose position is zero
/// and whose remaining bytes contain one complete BSS document.
///
/// @param compatibility the JavaFX compatibility level that determines the BSS version
@NotNullByDefault
public record BssTarget(JavaFXCompatibility compatibility)
        implements OutputTarget<@Unmodifiable ByteBuffer> {
    /// The default target compatible with JavaFX 17 BSS version 6.
    public static final BssTarget DEFAULT = new BssTarget(JavaFXCompatibility.JAVA_FX_17);

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
