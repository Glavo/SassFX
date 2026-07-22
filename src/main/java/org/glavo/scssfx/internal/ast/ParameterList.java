// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/// Declares the parameters accepted by a mixin or function.
///
/// @param parameters the parameters in source order
/// @param span       the source range covering the complete parameter list
@ApiStatus.Internal
@NotNullByDefault
public record ParameterList(
        @Unmodifiable List<Parameter> parameters,
        SourceSpan span
) implements SassNode {
    /// Creates an immutable parameter list.
    ///
    /// @throws IllegalArgumentException if a parameter name is duplicated
    public ParameterList {
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(span, "span");
        var names = new HashSet<String>();
        for (var parameter : parameters) {
            if (!names.add(parameter.name())) {
                throw new IllegalArgumentException(
                        "duplicate parameter name: " + parameter.name()
                );
            }
        }
    }

    /// Creates an empty parameter list.
    ///
    /// @param span the source range of the empty parentheses, or a synthetic span
    /// @return the empty list
    public static ParameterList empty(SourceSpan span) {
        return new ParameterList(List.of(), span);
    }

    /// Returns whether this list declares no parameters.
    ///
    /// @return whether the parameter list is empty
    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
