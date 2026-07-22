// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Represents a Sass list literal.
///
/// @param contents    the list elements in source order
/// @param separator   the list separator
/// @param hasBrackets whether the list is enclosed in square brackets
/// @param span        the source range occupied by the complete list
@ApiStatus.Internal
@NotNullByDefault
public record ListExpression(
        @Unmodifiable List<SassExpression> contents,
        ListSeparator separator,
        boolean hasBrackets,
        SourceSpan span
) implements SassExpression {
    /// Creates a list expression.
    ///
    /// @throws IllegalArgumentException if an undecided separator is used for multiple elements
    public ListExpression {
        contents = List.copyOf(contents);
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(span, "span");
        if (separator == ListSeparator.UNDECIDED && contents.size() > 1) {
            throw new IllegalArgumentException(
                    "a list with multiple elements must have an explicit separator"
            );
        }
    }

    /// Returns whether an expression is an unbracketed list with multiple elements.
    ///
    /// @param expression the expression to inspect
    /// @return whether the expression requires grouping in an operator position
    static boolean isUnbracketedMultiElement(SassExpression expression) {
        return expression instanceof ListExpression list
                && !list.hasBrackets
                && list.contents.size() > 1;
    }

    /// Returns a Sass source representation of this list.
    ///
    /// @return the list source with disambiguating parentheses when necessary
    @Override
    public String toString() {
        var result = new StringBuilder();
        if (hasBrackets) {
            result.append('[');
        } else if (contents.isEmpty()
                || contents.size() == 1 && separator == ListSeparator.COMMA) {
            result.append('(');
        }

        var delimiter = switch (separator) {
            case COMMA -> ", ";
            case SLASH -> " / ";
            case SPACE, UNDECIDED -> " ";
        };
        for (var index = 0; index < contents.size(); index++) {
            if (index > 0) {
                result.append(delimiter);
            }
            var element = contents.get(index);
            result.append(elementRequiresParentheses(element) ? "(" + element + ")" : element);
        }

        if (hasBrackets) {
            result.append(']');
        } else if (contents.isEmpty()) {
            result.append(')');
        } else if (contents.size() == 1 && separator == ListSeparator.COMMA) {
            result.append(",)");
        }
        return result.toString();
    }

    /// Returns whether a list element requires grouping parentheses.
    ///
    /// @param expression the element to inspect
    /// @return whether parentheses are required
    private boolean elementRequiresParentheses(SassExpression expression) {
        if (expression instanceof UnaryOperationExpression unary
                && (unary.operator() == UnaryOperator.PLUS
                || unary.operator() == UnaryOperator.MINUS)) {
            return separator == ListSeparator.SPACE;
        }
        if (!(expression instanceof ListExpression list)
                || list.hasBrackets
                || list.contents.size() < 2) {
            return false;
        }
        return separator == ListSeparator.COMMA
                ? list.separator == ListSeparator.COMMA
                : list.separator != ListSeparator.UNDECIDED;
    }
}
