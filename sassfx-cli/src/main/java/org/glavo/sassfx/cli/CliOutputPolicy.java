// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Contains output behavior resolved after input planning.
///
/// @param sourceMap whether compiler source-map generation is enabled
/// @param sourceMapUrlMode the source URL representation
/// @param embedSources whether source contents are included in maps
/// @param embedSourceMap whether the map is embedded in textual output
/// @param errorCss whether Sass failures emit an error stylesheet
@NotNullByDefault
record CliOutputPolicy(
        boolean sourceMap,
        CliSourceMap.UrlMode sourceMapUrlMode,
        boolean embedSources,
        boolean embedSourceMap,
        boolean errorCss
) {
    /// Creates a resolved immutable output policy.
    CliOutputPolicy {
        Objects.requireNonNull(sourceMapUrlMode, "sourceMapUrlMode");
    }
}
