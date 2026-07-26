// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies how ordinary variable assignments interact with global bindings.
@ApiStatus.Internal
@NotNullByDefault
public enum ScopeSemantics {
    /// Prevents ordinary assignments from modifying a binding in the global frame.
    LEXICAL,

    /// Allows ordinary assignments to update an existing global binding.
    FLOW_CONTROL
}
