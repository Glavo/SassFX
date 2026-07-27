// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a function support test such as `selector(.button)`.
///
/// @param name the interpolated function name
/// @param arguments the raw interpolated function arguments
/// @param span the source range covering the complete function condition
@ApiStatus.Internal
@NotNullByDefault
public record SupportsFunction(
        Interpolation name,
        Interpolation arguments,
        SourceSpan span
) implements SupportsCondition {
    /// Creates a function support test.
    public SupportsFunction {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(span, "span");
    }

    /// Returns a source-like representation of the function test.
    ///
    /// @return the function call
    @Override
    public String toString() {
        return name + "(" + arguments + ")";
    }
}
