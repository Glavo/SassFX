// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents one plain-text or expression part of an interpolation.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface InterpolationPart
        permits TextInterpolationPart, ExpressionInterpolationPart {
}
