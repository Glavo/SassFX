// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.callable;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// A function that may be invoked during Sass evaluation.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface Callable permits BuiltInCallable, PlainCssCallable {
    /// Returns the normalized callable name used for lookup.
    ///
    /// @return the hyphenated name
    String name();
}
