// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// A modern CSS-style {@code if()} expression.
///
/// Each branch pairs an optional condition with a value expression. A
/// {@code null} condition represents the {@code else} branch.
///
/// @param branches the conditional branches in source order
/// @param span the complete {@code if(...)} span
@ApiStatus.Internal
@NotNullByDefault
public record IfExpression(
        @Unmodifiable List<Branch> branches,
        SourceSpan span
) implements SassExpression {
    /// Creates a CSS {@code if()} expression.
    ///
    /// @throws IllegalArgumentException if {@code branches} is empty
    public IfExpression {
        branches = List.copyOf(branches);
        Objects.requireNonNull(span, "span");
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("if() branches may not be empty");
        }
    }

    /// One conditional branch of a CSS {@code if()}.
    ///
    /// @param condition the branch condition, or {@code null} for {@code else}
    /// @param value the value selected when the condition matches
    public record Branch(@Nullable IfConditionExpression condition, SassExpression value) {
        /// Creates one branch.
        public Branch {
            Objects.requireNonNull(value, "value");
        }
    }

    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitIfExpression(this);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder("if(");
        for (var index = 0; index < branches.size(); index++) {
            if (index > 0) {
                builder.append("; ");
            }
            var branch = branches.get(index);
            builder.append(branch.condition() == null ? "else" : branch.condition());
            builder.append(": ").append(branch.value());
        }
        return builder.append(')').toString();
    }
}
