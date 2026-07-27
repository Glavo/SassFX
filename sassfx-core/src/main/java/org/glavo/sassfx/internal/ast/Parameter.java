// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Declares one parameter of a mixin or function.
///
/// @param name         the normalized parameter name without a dollar sign
/// @param defaultValue the default expression, or {@code null} when required
/// @param span         the source range covering the parameter declaration
@ApiStatus.Internal
@NotNullByDefault
public record Parameter(
        String name,
        @Nullable SassExpression defaultValue,
        SourceSpan span
) {
    /// Creates an immutable parameter.
    ///
    /// @throws IllegalArgumentException if {@code name} is empty
    public Parameter {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }
}
