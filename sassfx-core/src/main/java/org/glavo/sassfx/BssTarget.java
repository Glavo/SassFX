// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

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
/// Media rules are encoded for JavaFX 25 and later. Retained filesystem
/// `@import` rules are resolved relative to the source containing the import,
/// with [CompileOptions#loadPaths()] used as fallback roots. JavaFX 8 through
/// 26 flatten unconditional imported rules before local rules; JavaFX 27
/// embeds each resolved direct import and its media condition in BSS v9.
/// Imported font faces do not propagate into the importing stylesheet, matching
/// JavaFX stylesheet merging. Non-file URI schemes are not loaded implicitly.
/// Transition declarations are rejected even for JavaFX 23 and later because
/// JavaFX 23 through 27 cannot deserialize their transition-specific converters
/// from BSS.
///
/// @param javaFXTarget the JavaFX release target that determines the BSS version
@NotNullByDefault
public record BssTarget(JavaFXTarget javaFXTarget)
        implements OutputTarget<@Unmodifiable ByteBuffer> {
    /// The default target compatible with JavaFX 17 BSS version 6.
    public static final BssTarget DEFAULT = new BssTarget(JavaFXTarget.JAVAFX17);

    /// Creates a binary stylesheet output target.
    public BssTarget {
        Objects.requireNonNull(javaFXTarget, "javaFXTarget");
    }

    /// Returns the binary stylesheet format version selected by this target.
    ///
    /// @return a value from `5` through `9`
    public int bssVersion() {
        return javaFXTarget.bssVersion();
    }

    /// Returns the binary stylesheet format version selected by this target.
    ///
    /// @deprecated Use [#bssVersion()] to distinguish the BSS format version
    /// from [JavaFXTarget#version()].
    ///
    /// @return a value from `5` through `9`
    @Deprecated
    public int version() {
        return bssVersion();
    }
}
