// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes a compiler output representation and its result type.
///
/// @param <T> the type produced for this target
@NotNullByDefault
public sealed interface OutputTarget<T> permits CssTarget, JavaFxCssTarget, BssTarget {
}
