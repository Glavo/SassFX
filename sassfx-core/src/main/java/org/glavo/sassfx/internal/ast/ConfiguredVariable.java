// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Configures one variable in a module loaded by a {@code @use} or {@code @forward} rule.
///
/// @param name       the normalized variable name without its dollar sign
/// @param expression the value supplied by the containing configuration
/// @param nameSpan   the source range occupied by the variable name, including its dollar sign
/// @param span       the source range occupied by the complete configuration entry
/// @param guarded    whether an outer configuration may replace this value
@ApiStatus.Internal
@NotNullByDefault
public record ConfiguredVariable(
        String name,
        SassExpression expression,
        SourceSpan nameSpan,
        SourceSpan span,
        boolean guarded
) {
    /// Creates an immutable configured variable.
    ///
    /// @throws IllegalArgumentException if {@code name} is empty, {@code nameSpan}
    /// does not contain a variable name, or a child span is outside {@code span}
    public ConfiguredVariable {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(nameSpan, "nameSpan");
        Objects.requireNonNull(span, "span");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (!nameSpan.text().startsWith("$") || nameSpan.text().length() == 1) {
            throw new IllegalArgumentException(
                    "nameSpan must include a variable name after the leading dollar sign"
            );
        }
        if (!contains(span, nameSpan)) {
            throw new IllegalArgumentException("nameSpan must be contained by span");
        }
        if (!contains(span, expression.span())) {
            throw new IllegalArgumentException("expression span must be contained by span");
        }
        if (nameSpan.end().offset() > expression.span().start().offset()) {
            throw new IllegalArgumentException("nameSpan must not overlap the expression span");
        }
    }

    /// Creates an unguarded configured variable.
    ///
    /// @param name       the normalized variable name without its dollar sign
    /// @param expression the configured expression
    /// @param nameSpan   the source range occupied by the variable name
    /// @param span       the source range occupied by the complete entry
    public ConfiguredVariable(
            String name,
            SassExpression expression,
            SourceSpan nameSpan,
            SourceSpan span
    ) {
        this(name, expression, nameSpan, span, false);
    }

    /// Returns a normalized Sass representation of this configuration entry.
    ///
    /// @return the variable name and configured expression
    @Override
    public String toString() {
        return "$" + name + ": " + expression + (guarded ? " !default" : "");
    }

    /// Returns whether an inner span is a text-consistent subrange of an outer span.
    ///
    /// @param outer the prospective containing span
    /// @param inner the prospective contained span
    /// @return whether the spans have consistent URLs, offsets, and text
    private static boolean contains(SourceSpan outer, SourceSpan inner) {
        if (!Objects.equals(outer.url(), inner.url())
                || inner.start().offset() < outer.start().offset()
                || inner.end().offset() > outer.end().offset()) {
            return false;
        }
        var relativeStart = inner.start().offset() - outer.start().offset();
        return outer.text().regionMatches(relativeStart, inner.text(), 0, inner.text().length());
    }
}
