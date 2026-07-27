// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/// Provides Sass-compatible fuzzy equality and hashing for double values.
@ApiStatus.Internal
@NotNullByDefault
public final class SassFuzzy {
    /// Contains the maximum distance between fuzzy-equal values.
    private static final double EPSILON = 1e-11;

    /// Contains the multiplier used to assign values to fuzzy hash buckets.
    private static final double INVERSE_EPSILON = 1e11;

    /// Prevents instantiation.
    private SassFuzzy() {
    }

    /// Returns whether two values are equal under Sass numeric semantics.
    ///
    /// @param first  the first value
    /// @param second the second value
    /// @return whether the values are fuzzy equal
    public static boolean equals(double first, double second) {
        if (first == second) {
            return true;
        }
        return Math.abs(first - second) <= EPSILON
                && bucket(first).equals(bucket(second));
    }

    /// Returns whether two nullable values are equal under Sass numeric semantics.
    ///
    /// @param first  the first value, or {@code null}
    /// @param second the second value, or {@code null}
    /// @return whether both are null or both are fuzzy equal
    public static boolean equalsNullable(@Nullable Double first, @Nullable Double second) {
        if (first == null) {
            return second == null;
        }
        return second != null && equals(first, second);
    }

    /// Returns whether {@code first} is greater than or fuzzy-equal to {@code second}.
    ///
    /// @param first  the first value
    /// @param second the second value
    /// @return whether {@code first >= second} under Sass fuzzy semantics
    public static boolean greaterThanOrEquals(double first, double second) {
        return first > second || equals(first, second);
    }

    /// Returns whether {@code first} is less than or fuzzy-equal to {@code second}.
    ///
    /// @param first  the first value
    /// @param second the second value
    /// @return whether {@code first <= second} under Sass fuzzy semantics
    public static boolean lessThanOrEquals(double first, double second) {
        return first < second || equals(first, second);
    }

    /// Returns whether {@code first} is strictly less than {@code second} under Sass
    /// fuzzy semantics.
    ///
    /// @param first  the first value
    /// @param second the second value
    /// @return whether {@code first < second} and the values are not fuzzy equal
    public static boolean lessThan(double first, double second) {
        return first < second && !equals(first, second);
    }

    /// Returns whether {@code value} lies within {@code [min, max]} under Sass fuzzy
    /// semantics.
    ///
    /// @param value the value to test
    /// @param min   the inclusive minimum
    /// @param max   the inclusive maximum
    /// @return whether the value is inside the range
    public static boolean inRange(double value, double min, double max) {
        return greaterThanOrEquals(value, min) && lessThanOrEquals(value, max);
    }

    /// Returns a hash consistent with Sass fuzzy equality.
    ///
    /// @param value the value to hash
    /// @return the fuzzy hash
    public static int hashCode(double value) {
        return Double.isFinite(value)
                ? bucket(value).hashCode()
                : Double.hashCode(value);
    }

    /// Returns whether a finite value is fuzzy-equal to an integer.
    ///
    /// @param value the value to test
    /// @return whether the value is an integer under Sass numeric semantics
    public static boolean isInt(double value) {
        return Double.isFinite(value) && equals(value, Math.rint(value));
    }

    /// Returns the nearest integer when {@code value} is fuzzy-equal to one.
    ///
    /// @param value the value to convert
    /// @return the integer, or {@code null} when the value is not an integer
    public static @Nullable Integer asInt(double value) {
        if (!isInt(value)) {
            return null;
        }
        var rounded = Math.rint(value);
        if (rounded > Integer.MAX_VALUE || rounded < Integer.MIN_VALUE) {
            return null;
        }
        return (int) rounded;
    }

    /// Returns the arbitrary-precision Sass fuzzy bucket.
    ///
    /// @param value a finite value
    /// @return the rounded bucket
    private static BigInteger bucket(double value) {
        var scaled = value * INVERSE_EPSILON;
        var decimal = Double.isFinite(scaled)
                ? BigDecimal.valueOf(scaled)
                : BigDecimal.valueOf(value).movePointRight(11);
        return decimal.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
    }
}
