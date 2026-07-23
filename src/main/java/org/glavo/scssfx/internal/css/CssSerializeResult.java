// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.SourceMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Contains serialized CSS text and an optional source map.
///
/// @param css       the generated CSS document
/// @param sourceMap the version-3 source map, or {@code null}
@ApiStatus.Internal
@NotNullByDefault
public record CssSerializeResult(String css, @Nullable SourceMap sourceMap) {
    /// Creates a serialization result.
    public CssSerializeResult {
        Objects.requireNonNull(css, "css");
    }
}
