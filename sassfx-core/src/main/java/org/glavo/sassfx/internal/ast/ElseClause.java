// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// The unconditional `@else` branch of an `@if` rule.
///
/// @param children the statements executed when every preceding clause is falsey
@ApiStatus.Internal
@NotNullByDefault
public record ElseClause(@Unmodifiable List<SassStatement> children) {
    /// Creates an immutable else clause.
    public ElseClause {
        children = List.copyOf(children);
    }
}
