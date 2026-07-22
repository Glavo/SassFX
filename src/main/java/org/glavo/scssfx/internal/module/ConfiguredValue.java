// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Stores one evaluated module configuration value and its source locations.
///
/// @param value             the value evaluated in the configuring stylesheet
/// @param configurationSpan the complete configuration entry span
/// @param originSpan        the expression span that produced the value
@ApiStatus.Internal
@NotNullByDefault
public record ConfiguredValue(
        SassValue value,
        SourceSpan configurationSpan,
        SourceSpan originSpan
) {
    /// Creates a configuration value.
    public ConfiguredValue {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(configurationSpan, "configurationSpan");
        Objects.requireNonNull(originSpan, "originSpan");
    }
}
