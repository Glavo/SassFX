// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Marks source formats that affect expanded color serialization.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface ColorFormat permits SpanColorFormat {
}
