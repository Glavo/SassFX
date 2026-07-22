// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Contains a version 3 source map encoded as JSON.
///
/// @param json the complete source-map JSON document
@NotNullByDefault
public record SourceMap(String json) {
    /// Creates source-map data.
    public SourceMap {
        Objects.requireNonNull(json, "json");
    }
}
