// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Conditions used by the modern CSS-style [IfExpression].
///
/// In addition to plain-CSS function conditions, this supports the Sass-only
/// {@code sass(...)} form that evaluates to a compile-time boolean.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface IfConditionExpression permits
        IfConditionExpression.Parenthesized,
        IfConditionExpression.Negation,
        IfConditionExpression.Operation,
        IfConditionExpression.Function,
        IfConditionExpression.Sass,
        IfConditionExpression.Raw {
    /// Returns the source span covering this condition.
    ///
    /// @return the condition span
    SourceSpan span();

    /// Returns whether this condition may expand to multiple tokens later.
    ///
    /// Arbitrary substitutions such as {@code var()} force the surrounding
    /// condition into a raw CSS form when mixed with other tokens.
    ///
    /// @return whether this is an arbitrary substitution
    default boolean isArbitrarySubstitution() {
        return false;
    }

    /// A parenthesized condition group.
    ///
    /// @param expression the nested condition
    /// @param span the complete parenthesized span
    record Parenthesized(IfConditionExpression expression, SourceSpan span)
            implements IfConditionExpression {
        /// Creates a parenthesized condition.
        public Parenthesized {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(span, "span");
        }

        @Override
        public String toString() {
            return "(" + expression + ")";
        }
    }

    /// A {@code not}-prefixed condition.
    ///
    /// @param expression the negated condition
    /// @param span the complete negation span
    record Negation(IfConditionExpression expression, SourceSpan span)
            implements IfConditionExpression {
        /// Creates a negated condition.
        public Negation {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(span, "span");
        }

        @Override
        public String toString() {
            return "not " + expression;
        }
    }

    /// A boolean operator joining condition groups.
    enum BooleanOperator {
        /// Logical conjunction.
        AND("and"),
        /// Logical disjunction.
        OR("or");

        private final String cssName;

        BooleanOperator(String cssName) {
            this.cssName = cssName;
        }

        /// Returns the CSS operator spelling.
        ///
        /// @return {@code and} or {@code or}
        public String cssName() {
            return cssName;
        }

        @Override
        public String toString() {
            return cssName;
        }
    }

    /// A sequence of groups joined by a single boolean operator.
    ///
    /// @param expressions the joined groups; must contain at least two entries
    /// @param operator the shared operator
    /// @param span the complete operation span
    record Operation(
            @Unmodifiable List<IfConditionExpression> expressions,
            BooleanOperator operator,
            SourceSpan span
    ) implements IfConditionExpression {
        /// Creates a boolean condition operation.
        public Operation {
            expressions = List.copyOf(expressions);
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(span, "span");
            if (expressions.size() < 2) {
                throw new IllegalArgumentException("operation requires at least two expressions");
            }
        }

        @Override
        public String toString() {
            var builder = new StringBuilder();
            for (var index = 0; index < expressions.size(); index++) {
                if (index > 0) {
                    builder.append(' ').append(operator).append(' ');
                }
                builder.append(expressions.get(index));
            }
            return builder.toString();
        }
    }

    /// A plain-CSS function-style condition such as {@code css()} or {@code var(...)}.
    ///
    /// @param name the function name interpolation
    /// @param arguments the raw argument interpolation
    /// @param span the complete function span
    record Function(Interpolation name, Interpolation arguments, SourceSpan span)
            implements IfConditionExpression {
        /// Creates a function condition.
        public Function {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(arguments, "arguments");
            Objects.requireNonNull(span, "span");
        }

        @Override
        public boolean isArbitrarySubstitution() {
            @Nullable String plain = name.asPlain();
            if (plain == null) {
                return false;
            }
            var lower = plain.toLowerCase(Locale.ROOT);
            return "if".equals(lower)
                    || "var".equals(lower)
                    || "attr".equals(lower)
                    || plain.startsWith("--");
        }

        @Override
        public String toString() {
            return name + "(" + arguments + ")";
        }
    }

    /// A compile-time Sass condition written as {@code sass(...)}.
    ///
    /// @param expression the SassScript expression inside {@code sass()}
    /// @param span the complete {@code sass(...)} span
    record Sass(SassExpression expression, SourceSpan span) implements IfConditionExpression {
        /// Creates a Sass condition.
        public Sass {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(span, "span");
        }

        @Override
        public String toString() {
            return "sass(" + expression + ")";
        }
    }

    /// Raw condition text, possibly containing interpolations or substitution tokens.
    ///
    /// @param text the raw condition interpolation
    record Raw(Interpolation text) implements IfConditionExpression {
        /// Creates a raw condition.
        public Raw {
            Objects.requireNonNull(text, "text");
        }

        @Override
        public SourceSpan span() {
            return text.span();
        }

        @Override
        public boolean isArbitrarySubstitution() {
            return true;
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
