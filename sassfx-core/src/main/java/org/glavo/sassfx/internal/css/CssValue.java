// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Associates a plain-CSS value with the source range that produced it.
///
/// @param value the concrete value
/// @param span  the source range associated with the value
/// @param <T>   the value type
@ApiStatus.Internal
@NotNullByDefault
public record CssValue<T>(T value, SourceSpan span) {
    /// Creates a spanned CSS value.
    public CssValue {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }

    /// Returns the inspect representation of the contained value.
    ///
    /// @return the value's string form
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
