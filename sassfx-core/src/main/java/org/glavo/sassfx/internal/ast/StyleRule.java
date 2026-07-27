// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Applies child statements to elements matching a source selector.
///
/// The selector remains an interpolation until evaluation resolves expressions
/// and the selector grammar is parsed.
///
/// @param selector the unevaluated selector source
/// @param children the statements in the rule block
/// @param span the source range from the selector through the closing brace
@ApiStatus.Internal
@NotNullByDefault
public record StyleRule(
        Interpolation selector,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable style rule.
    public StyleRule {
        Objects.requireNonNull(selector, "selector");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the style-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitStyleRule(this);
    }

    /// Returns the Sass source representation of this rule.
    ///
    /// @return the selector followed by its child block
    @Override
    public String toString() {
        return selector + " {" + String.join(
                " ",
                children.stream().map(Object::toString).toList()
        ) + "}";
    }
}
