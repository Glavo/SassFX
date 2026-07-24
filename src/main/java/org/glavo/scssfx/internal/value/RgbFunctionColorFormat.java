// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Marks a legacy RGB color that was constructed with the {@code rgb()}/
/// {@code rgba()} functions and must serialize as an RGB function rather than
/// a named color or hex literal.
@ApiStatus.Internal
@NotNullByDefault
public enum RgbFunctionColorFormat implements ColorFormat {
    /// The singleton RGB function format marker.
    INSTANCE
}
