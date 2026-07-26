// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Stores JavaFX media-query expressions in their binary-serializable form.
///
/// @param alternatives the expressions joined by the query-list comma operator
@ApiStatus.Internal
@NotNullByDefault
public record JavaFXMediaQuery(
        @Unmodifiable List<Expression> alternatives
) {
    /// Creates an immutable media-query list.
    public JavaFXMediaQuery {
        alternatives = List.copyOf(alternatives);
    }

    /// Identifies one JavaFX media-query expression.
    @NotNullByDefault
    public sealed interface Expression permits
            Feature,
            Negation,
            Conjunction,
            Disjunction,
            Range {
    }

    /// Stores a discrete JavaFX media feature.
    ///
    /// @param name  the lowercase feature name
    /// @param value the lowercase feature value, or `null` for boolean context
    @NotNullByDefault
    public record Feature(String name, @Nullable String value)
            implements Expression {
        /// Validates the feature name.
        public Feature {
            Objects.requireNonNull(name, "name");
        }
    }

    /// Stores the logical negation of an expression.
    ///
    /// @param expression the negated expression
    @NotNullByDefault
    public record Negation(Expression expression) implements Expression {
        /// Validates the nested expression.
        public Negation {
            Objects.requireNonNull(expression, "expression");
        }
    }

    /// Stores the logical conjunction of two expressions.
    ///
    /// @param left  the left operand
    /// @param right the right operand
    @NotNullByDefault
    public record Conjunction(Expression left, Expression right)
            implements Expression {
        /// Validates both operands.
        public Conjunction {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    /// Stores the logical disjunction of two expressions.
    ///
    /// @param left  the left operand
    /// @param right the right operand
    @NotNullByDefault
    public record Disjunction(Expression left, Expression right)
            implements Expression {
        /// Validates both operands.
        public Disjunction {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    /// Stores one side of a JavaFX range media expression.
    ///
    /// @param comparison the comparison applied to the runtime feature value
    /// @param name       the lowercase feature name
    /// @param value      the finite comparison value
    /// @param unit       the JavaFX size unit, or `null` for a unitless number
    @NotNullByDefault
    public record Range(
            Comparison comparison,
            String name,
            double value,
            @Nullable Unit unit
    ) implements Expression {
        /// Validates the range expression.
        public Range {
            Objects.requireNonNull(comparison, "comparison");
            Objects.requireNonNull(name, "name");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("value must be finite");
            }
        }
    }

    /// Identifies a JavaFX range comparison and its BSS query tag.
    @NotNullByDefault
    public enum Comparison {
        /// Tests equality.
        EQUAL(6, 0),
        /// Tests whether the runtime value is greater.
        GREATER(7, 1),
        /// Tests whether the runtime value is greater than or equal.
        GREATER_OR_EQUAL(8, 1),
        /// Tests whether the runtime value is less.
        LESS(9, -1),
        /// Tests whether the runtime value is less than or equal.
        LESS_OR_EQUAL(10, -1);

        /// Contains the BSS media-query type tag.
        private final int binaryTag;

        /// Contains the interval direction.
        private final int direction;

        /// Creates a comparison.
        ///
        /// @param binaryTag the BSS query type tag
        /// @param direction the interval direction
        Comparison(int binaryTag, int direction) {
            this.binaryTag = binaryTag;
            this.direction = direction;
        }

        /// Returns the BSS media-query type tag.
        ///
        /// @return a value from `6` through `10`
        public int binaryTag() {
            return binaryTag;
        }

        /// Returns the comparison with its operands reversed.
        ///
        /// @return the reversed comparison
        public Comparison flipped() {
            return switch (this) {
                case EQUAL -> EQUAL;
                case GREATER -> LESS;
                case GREATER_OR_EQUAL -> LESS_OR_EQUAL;
                case LESS -> GREATER;
                case LESS_OR_EQUAL -> GREATER_OR_EQUAL;
            };
        }

        /// Returns whether another comparison can bound the same interval.
        ///
        /// @param other the second comparison
        /// @return whether both comparisons have the same nonzero direction
        public boolean hasSameDirection(Comparison other) {
            return direction != 0 && direction == other.direction;
        }
    }

    /// Identifies JavaFX size units by their BSS enum ordinal.
    @NotNullByDefault
    public enum Unit {
        /// Percentage units.
        PERCENT(0),
        /// Inch units.
        IN(1),
        /// Centimeter units.
        CM(2),
        /// Millimeter units.
        MM(3),
        /// Font-relative em units.
        EM(4),
        /// Font-relative ex units.
        EX(5),
        /// Point units.
        PT(6),
        /// Pica units.
        PC(7),
        /// Pixel units.
        PX(8);

        /// Contains the JavaFX `SizeUnits` ordinal persisted by BSS.
        private final int binaryOrdinal;

        /// Creates a size unit.
        ///
        /// @param binaryOrdinal the JavaFX enum ordinal
        Unit(int binaryOrdinal) {
            this.binaryOrdinal = binaryOrdinal;
        }

        /// Returns the JavaFX `SizeUnits` ordinal persisted by BSS.
        ///
        /// @return a value from `0` through `8`
        public int binaryOrdinal() {
            return binaryOrdinal;
        }
    }
}
