// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Represents an ordered Sass map literal.
///
/// Pairs are retained in a list because distinct key expressions may evaluate
/// to the same Sass value. Duplicate evaluated keys are diagnosed later.
///
/// @param pairs the key-value pairs in source order
/// @param span the source range including the surrounding parentheses
@ApiStatus.Internal
@NotNullByDefault
public record MapExpression(
        @Unmodifiable List<MapEntry> pairs,
        SourceSpan span
) implements SassExpression {
    /// Creates an immutable map expression.
    public MapExpression {
        pairs = List.copyOf(pairs);
        Objects.requireNonNull(span, "span");
    }

    /// Returns a normalized Sass source representation of this map.
    ///
    /// @return the comma-separated pairs surrounded by parentheses
    @Override
    public String toString() {
        var result = new StringBuilder("(");
        for (var index = 0; index < pairs.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            var pair = pairs.get(index);
            result.append(pair.key()).append(": ").append(pair.value());
        }
        return result.append(')').toString();
    }
}
