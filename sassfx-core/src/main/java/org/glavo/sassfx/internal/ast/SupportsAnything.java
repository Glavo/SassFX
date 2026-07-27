// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a parenthesized support test whose contents are not a Sass
/// declaration or known function.
///
/// @param contents the raw interpolated contents between parentheses
/// @param span the source range covering the complete condition
@ApiStatus.Internal
@NotNullByDefault
public record SupportsAnything(
        Interpolation contents,
        SourceSpan span
) implements SupportsCondition {
    /// Creates an opaque support test.
    public SupportsAnything {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(span, "span");
    }

    /// Returns a source-like representation of the opaque test.
    ///
    /// @return the contents surrounded by parentheses
    @Override
    public String toString() {
        return "(" + contents + ")";
    }
}
