// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Receives deprecations at their import-resolution trigger point.
@ApiStatus.Internal
@FunctionalInterface
@NotNullByDefault
public interface ImportDeprecationHandler {
    /// Reports one import deprecation.
    ///
    /// @param deprecation the deprecation to report
    /// @param dependency whether the resolution belongs to a dependency
    void report(ImportDeprecation deprecation, boolean dependency);
}
