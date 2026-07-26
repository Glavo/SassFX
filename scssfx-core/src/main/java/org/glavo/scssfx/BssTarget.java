// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.util.Objects;

/// Configures JavaFX binary stylesheet output.
///
/// A successful compilation produces a read-only buffer whose position is zero
/// and whose remaining bytes contain one complete BSS document. The compiler
/// writes the format directly without loading JavaFX classes. Compilation may
/// fail with [SassCompilationException] when evaluated CSS requires a BSS
/// construct outside the supported subset.
///
/// Media rules are encoded for JavaFX 25 and later. A retained `@import`
/// currently fails because BSS v9 embeds the resolved imported stylesheet
/// rather than preserving its URL, and this target does not yet define a
/// resource-resolution contract.
///
/// @param compatibility the JavaFX compatibility level that determines the BSS version
@NotNullByDefault
public record BssTarget(JavaFXCompatibility compatibility)
        implements OutputTarget<@Unmodifiable ByteBuffer> {
    /// The default target compatible with JavaFX 17 BSS version 6.
    public static final BssTarget DEFAULT = new BssTarget(JavaFXCompatibility.JAVAFX17);

    /// Creates a binary stylesheet output target.
    public BssTarget {
        Objects.requireNonNull(compatibility, "compatibility");
    }

    /// Returns the binary stylesheet format version selected by this target.
    ///
    /// @return a value from `5` through `9`
    public int bssVersion() {
        return compatibility.bssVersion();
    }

    /// Returns the binary stylesheet format version selected by this target.
    ///
    /// @deprecated Use [#bssVersion()] to distinguish the BSS format version
    /// from [JavaFXCompatibility#version()].
    ///
    /// @return a value from `5` through `9`
    @Deprecated
    public int version() {
        return bssVersion();
    }
}
