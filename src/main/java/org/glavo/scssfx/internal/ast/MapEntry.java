// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Contains one unevaluated Sass map key-value pair.
///
/// @param key the key expression
/// @param value the value expression
@ApiStatus.Internal
@NotNullByDefault
public record MapEntry(SassExpression key, SassExpression value) {
    /// Creates a map entry.
    public MapEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }
}
