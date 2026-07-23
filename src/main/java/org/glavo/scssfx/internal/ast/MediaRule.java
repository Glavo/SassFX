// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Declares one Sass {@code @media} rule.
///
/// The query remains interpolated until evaluation so SassScript values can
/// contribute media types and conditions. Its body retains the enclosing
/// statement context, allowing nested style rules and nested media rules to
/// be resolved before CSS serialization.
///
/// @param query    the interpolated media-query list
/// @param children the statements in the media block
/// @param span     the source range from {@code @media} through its closing brace
@ApiStatus.Internal
@NotNullByDefault
public record MediaRule(
        Interpolation query,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable media rule.
    public MediaRule {
        Objects.requireNonNull(query, "query");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the media-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitMediaRule(this);
    }

    /// Returns a Sass source representation of this rule.
    ///
    /// @return the {@code @media} rule and its child statements
    @Override
    public String toString() {
        return "@media " + query + " {" + String.join(
                " ",
                children.stream().map(Object::toString).toList()
        ) + "}";
    }
}
