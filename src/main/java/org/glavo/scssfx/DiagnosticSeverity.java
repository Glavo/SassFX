// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the significance and handling of a compiler diagnostic.
@NotNullByDefault
public enum DiagnosticSeverity {
    /// Reports a condition that prevents successful compilation.
    ERROR,

    /// Reports a recoverable condition that may indicate unintended behavior.
    WARNING,

    /// Reports use of behavior scheduled for removal or incompatibility.
    DEPRECATION,

    /// Reports diagnostic information requested for debugging.
    DEBUG
}
