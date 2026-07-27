// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a declaration support test such as `(display: grid)`.
///
/// @param name the SassScript expression producing the property name
/// @param value the SassScript expression producing the property value
/// @param customProperty whether the declaration name is a custom property
/// @param span the source range covering the complete condition
@ApiStatus.Internal
@NotNullByDefault
public record SupportsDeclaration(
        SassExpression name,
        SassExpression value,
        boolean customProperty,
        SourceSpan span
) implements SupportsCondition {
    /// Creates a declaration support test.
    public SupportsDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(span, "span");
    }

    /// Returns a source-like representation of the declaration test.
    ///
    /// @return the declaration surrounded by parentheses
    @Override
    public String toString() {
        return "(" + name + (customProperty ? ":" : ": ") + value + ")";
    }
}
