// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Associates a plain-CSS value with its direct source range and mapping origin.
///
/// @param value         the concrete value
/// @param span          the direct source range associated with this use of the value
/// @param sourceMapSpan the source range from which the serialized value originated
/// @param <T>           the value type
@ApiStatus.Internal
@NotNullByDefault
public record CssValue<T>(T value, SourceSpan span, SourceSpan sourceMapSpan) {
    /// Creates a CSS value whose direct source range is also its mapping origin.
    ///
    /// @param value the concrete value
    /// @param span  the direct source range and mapping origin
    public CssValue(T value, SourceSpan span) {
        this(value, span, span);
    }

    /// Creates a CSS value with independently tracked direct and mapping ranges.
    public CssValue {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(sourceMapSpan, "sourceMapSpan");
    }

    /// Returns the inspect representation of the contained value.
    ///
    /// @return the value's string form
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
