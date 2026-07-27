// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.SassDeprecation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Describes a deprecation produced while resolving one stylesheet load.
///
/// @param deprecation the deprecation registry entry
/// @param message the complete diagnostic message
@ApiStatus.Internal
@NotNullByDefault
public record ImportDeprecation(
        SassDeprecation deprecation,
        String message
) {
    /// Creates an immutable import deprecation.
    public ImportDeprecation {
        Objects.requireNonNull(deprecation, "deprecation");
        Objects.requireNonNull(message, "message");
    }
}
