// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Associates a Sass variable value with the source that produced it.
///
/// @param value      the immutable variable value
/// @param originSpan the source range from which the value originated
@ApiStatus.Internal
@NotNullByDefault
public record VariableBinding(SassValue value, SourceSpan originSpan) {
    /// Creates a non-null variable binding.
    public VariableBinding {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(originSpan, "originSpan");
    }
}
