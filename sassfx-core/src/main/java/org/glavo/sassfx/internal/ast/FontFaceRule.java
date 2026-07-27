// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Declares one top-level CSS {@code @font-face} rule.
///
/// The body is retained as ordinary Sass statements so variables, control flow,
/// and mixin expansion can produce descriptor declarations during evaluation.
/// Style rules are not permitted in the body.
///
/// @param children the statements in the font-face block
/// @param span     the source range from {@code @font-face} through its closing brace
@ApiStatus.Internal
@NotNullByDefault
public record FontFaceRule(
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable font-face rule.
    public FontFaceRule {
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the font-face visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitFontFaceRule(this);
    }

    /// Returns a Sass source representation of this rule.
    ///
    /// @return the {@code @font-face} rule and its child statements
    @Override
    public String toString() {
        return "@font-face {" + String.join(
                " ",
                children.stream().map(Object::toString).toList()
        ) + "}";
    }
}
