// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.callable;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents an unknown function serialized as plain CSS text.
///
/// @param name the function name retained for CSS serialization
@ApiStatus.Internal
@NotNullByDefault
public record PlainCssCallable(String name) implements Callable {
    /// Creates a plain-CSS callable.
    public PlainCssCallable {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
    }
}
