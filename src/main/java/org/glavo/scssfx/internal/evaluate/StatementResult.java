// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents control flow returned by statement evaluation.
@ApiStatus.Internal
@NotNullByDefault
public enum StatementResult {
    /// Continues evaluation with the next statement.
    CONTINUE
}
