// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the lifecycle state of a Sass deprecation category.
@NotNullByDefault
public enum SassDeprecationStatus {
    /// The behavior currently emits a deprecation warning.
    ACTIVE,

    /// The behavior has been removed and no longer emits the warning.
    OBSOLETE,

    /// The warning requires explicit future-deprecation opt-in.
    FUTURE,

    /// The warning is emitted by user-authored integration code.
    USER
}
