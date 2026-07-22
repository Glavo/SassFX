// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Represents an immutable Sass number with numerator and denominator units.
///
/// Convertible CSS units are simplified, compared, and hashed through a
/// canonical unit for their dimension.
@ApiStatus.Internal
@NotNullByDefault
public final class SassNumber implements SassValue {
    /// Contains conversion metadata for compatible CSS units.
    private static final @Unmodifiable Map<String, UnitDefinition> CONVERTIBLE_UNITS = Map.ofEntries(
            Map.entry("in", new UnitDefinition("in", 1.0)),
            Map.entry("cm", new UnitDefinition("in", 1.0 / 2.54)),
            Map.entry("pc", new UnitDefinition("in", 1.0 / 6.0)),
            Map.entry("mm", new UnitDefinition("in", 1.0 / 25.4)),
            Map.entry("q", new UnitDefinition("in", 1.0 / 101.6)),
            Map.entry("pt", new UnitDefinition("in", 1.0 / 72.0)),
            Map.entry("px", new UnitDefinition("in", 1.0 / 96.0)),
            Map.entry("deg", new UnitDefinition("deg", 1.0)),
            Map.entry("grad", new UnitDefinition("deg", 0.9)),
            Map.entry("rad", new UnitDefinition("deg", 180.0 / Math.PI)),
            Map.entry("turn", new UnitDefinition("deg", 360.0)),
            Map.entry("s", new UnitDefinition("s", 1.0)),
            Map.entry("ms", new UnitDefinition("s", 1.0 / 1000.0)),
            Map.entry("Hz", new UnitDefinition("Hz", 1.0)),
            Map.entry("kHz", new UnitDefinition("Hz", 1000.0)),
            Map.entry("dpi", new UnitDefinition("dpi", 1.0)),
            Map.entry("dpcm", new UnitDefinition("dpi", 2.54)),
            Map.entry("dppx", new UnitDefinition("dpi", 96.0))
    );

    /// Contains the numeric magnitude.
    private final double value;

    /// Contains units multiplied into the value.
    private final @Unmodifiable List<String> numeratorUnits;

    /// Contains units dividing the value.
    private final @Unmodifiable List<String> denominatorUnits;

    /// Contains the original slash numerator, or {@code null} when absent.
    private final @Nullable SassNumber slashNumerator;

    /// Contains the original slash denominator, or {@code null} when absent.
    private final @Nullable SassNumber slashDenominator;

    /// Creates a number after copying, validating, and simplifying its units.
    ///
    /// @param value            the numeric magnitude
    /// @param numeratorUnits   units multiplied into the value
    /// @param denominatorUnits units dividing the value
    /// @param slashNumerator   the original slash numerator, or {@code null}
    /// @param slashDenominator the original slash denominator, or {@code null}
    private SassNumber(
            double value,
            List<String> numeratorUnits,
            List<String> denominatorUnits,
            @Nullable SassNumber slashNumerator,
            @Nullable SassNumber slashDenominator
    ) {
        if ((slashNumerator == null) != (slashDenominator == null)) {
            throw new IllegalArgumentException(
                    "slashNumerator and slashDenominator must be present together"
            );
        }
        var numerators = validatedUnits(numeratorUnits, "numeratorUnits");
        var unsimplifiedDenominators = validatedUnits(
                denominatorUnits,
                "denominatorUnits"
        );
        var denominators = new ArrayList<String>(unsimplifiedDenominators.size());
        var simplifiedValue = value;
        for (var denominator : unsimplifiedDenominators) {
            var simplifiedAway = false;
            for (var numeratorIndex = 0; numeratorIndex < numerators.size();
                 numeratorIndex++) {
                var factor = conversionFactor(
                        denominator,
                        numerators.get(numeratorIndex)
                );
                if (factor == null) {
                    continue;
                }
                simplifiedValue *= factor;
                numerators.remove(numeratorIndex);
                simplifiedAway = true;
                break;
            }
            if (!simplifiedAway) {
                denominators.add(denominator);
            }
        }

        this.value = simplifiedValue;
        this.numeratorUnits = List.copyOf(numerators);
        this.denominatorUnits = List.copyOf(denominators);
        this.slashNumerator = slashNumerator;
        this.slashDenominator = slashDenominator;
    }

    /// Creates a literal-compatible number with at most one numerator unit.
    ///
    /// @param value the numeric magnitude
    /// @param unit  the numerator unit, or {@code null} for a unitless number
    /// @return the number
    /// @throws IllegalArgumentException if {@code unit} is empty
    public static SassNumber of(double value, @Nullable String unit) {
        if (unit != null && unit.isEmpty()) {
            throw new IllegalArgumentException("unit must not be empty");
        }
        return new SassNumber(
                value,
                unit == null ? List.of() : List.of(unit),
                List.of(),
                null,
                null
        );
    }

    /// Creates a number with arbitrary numerator and denominator units.
    ///
    /// Convertible numerator-denominator pairs are cancelled in denominator
    /// order while scaling the magnitude to preserve their semantic value.
    ///
    /// @param value            the numeric magnitude
    /// @param numeratorUnits   units multiplied into the value
    /// @param denominatorUnits units dividing the value
    /// @return the simplified number
    /// @throws IllegalArgumentException if a unit is empty
    public static SassNumber withUnits(
            double value,
            List<String> numeratorUnits,
            List<String> denominatorUnits
    ) {
        return new SassNumber(value, numeratorUnits, denominatorUnits, null, null);
    }

    /// Returns the numeric magnitude.
    ///
    /// @return the magnitude
    public double value() {
        return value;
    }

    /// Returns the numerator units.
    ///
    /// @return an immutable list in multiplication order
    public @Unmodifiable List<String> numeratorUnits() {
        return numeratorUnits;
    }

    /// Returns the denominator units.
    ///
    /// @return an immutable list in multiplication order
    public @Unmodifiable List<String> denominatorUnits() {
        return denominatorUnits;
    }

    /// Returns whether this number has no units.
    ///
    /// @return whether both unit lists are empty
    public boolean isUnitless() {
        return numeratorUnits.isEmpty() && denominatorUnits.isEmpty();
    }

    /// Returns the original numerator of a slash-separated number.
    ///
    /// @return the slash numerator, or {@code null} when this is not slash-presented
    public @Nullable SassNumber slashNumerator() {
        return slashNumerator;
    }

    /// Returns the original denominator of a slash-separated number.
    ///
    /// @return the slash denominator, or {@code null} when this is not slash-presented
    public @Nullable SassNumber slashDenominator() {
        return slashDenominator;
    }

    /// Returns this semantic number with slash-division presentation metadata.
    ///
    /// @param numerator   the original numerator
    /// @param denominator the original denominator
    /// @return a semantically equal slash-presented number
    public SassNumber withSlash(SassNumber numerator, SassNumber denominator) {
        return new SassNumber(
                value,
                numeratorUnits,
                denominatorUnits,
                Objects.requireNonNull(numerator, "numerator"),
                Objects.requireNonNull(denominator, "denominator")
        );
    }

    /// Returns this number without slash-division presentation metadata.
    ///
    /// @return this number when no metadata exists, otherwise a metadata-free copy
    @Override
    public SassNumber withoutSlash() {
        return slashNumerator == null
                ? this
                : new SassNumber(value, numeratorUnits, denominatorUnits, null, null);
    }

    /// Compares this number with another compatible number.
    ///
    /// @param other the right operand
    /// @return whether this number is greater
    /// @throws SassValueException if the operand is not a compatible number
    @Override
    public SassBoolean greaterThan(SassValue other) {
        var right = compatibleValue(other, ">");
        return SassBoolean.of(value > right && !SassFuzzy.equals(value, right));
    }

    /// Compares this number with another compatible number.
    ///
    /// @param other the right operand
    /// @return whether this number is greater or fuzzy equal
    /// @throws SassValueException if the operand is not a compatible number
    @Override
    public SassBoolean greaterThanOrEquals(SassValue other) {
        var right = compatibleValue(other, ">=");
        return SassBoolean.of(value > right || SassFuzzy.equals(value, right));
    }

    /// Compares this number with another compatible number.
    ///
    /// @param other the right operand
    /// @return whether this number is less
    /// @throws SassValueException if the operand is not a compatible number
    @Override
    public SassBoolean lessThan(SassValue other) {
        var right = compatibleValue(other, "<");
        return SassBoolean.of(value < right && !SassFuzzy.equals(value, right));
    }

    /// Compares this number with another compatible number.
    ///
    /// @param other the right operand
    /// @return whether this number is less or fuzzy equal
    /// @throws SassValueException if the operand is not a compatible number
    @Override
    public SassBoolean lessThanOrEquals(SassValue other) {
        var right = compatibleValue(other, "<=");
        return SassBoolean.of(value < right || SassFuzzy.equals(value, right));
    }

    /// Adds another number or uses the generic Sass concatenation fallback.
    ///
    /// @param other the right operand
    /// @return the sum or concatenated string
    /// @throws SassValueException if numeric units are incompatible
    @Override
    public SassValue plus(SassValue other) {
        if (other instanceof SassNumber number) {
            return withCompatibleUnits(
                    value + compatibleValue(number, "+"),
                    number
            );
        }
        if (other instanceof SassColor) {
            throw undefinedOperation("+", other);
        }
        return SassValue.super.plus(other);
    }

    /// Subtracts another number or uses the generic Sass hyphen fallback.
    ///
    /// @param other the right operand
    /// @return the difference or concatenated string
    /// @throws SassValueException if numeric units are incompatible
    @Override
    public SassValue minus(SassValue other) {
        if (other instanceof SassNumber number) {
            return withCompatibleUnits(
                    value - compatibleValue(number, "-"),
                    number
            );
        }
        if (other instanceof SassColor) {
            throw undefinedOperation("-", other);
        }
        return SassValue.super.minus(other);
    }

    /// Multiplies this number by another number and combines their units.
    ///
    /// @param other the right operand
    /// @return the product
    /// @throws SassValueException if the operand is not a number
    @Override
    public SassValue times(SassValue other) {
        if (!(other instanceof SassNumber number)) {
            throw undefinedOperation("*", other);
        }
        var numerators = new ArrayList<>(numeratorUnits);
        numerators.addAll(number.numeratorUnits);
        var denominators = new ArrayList<>(denominatorUnits);
        denominators.addAll(number.denominatorUnits);
        return withUnits(value * number.value, numerators, denominators);
    }

    /// Divides this number by another number and combines reciprocal units.
    ///
    /// Non-number operands use the generic slash-string fallback.
    ///
    /// @param other the right operand
    /// @return the quotient or slash-separated string
    /// @throws SassValueException if a fallback operand cannot be represented in CSS
    @Override
    public SassValue dividedBy(SassValue other) {
        if (!(other instanceof SassNumber number)) {
            return SassValue.super.dividedBy(other);
        }
        var numerators = new ArrayList<>(numeratorUnits);
        numerators.addAll(number.denominatorUnits);
        var denominators = new ArrayList<>(denominatorUnits);
        denominators.addAll(number.numeratorUnits);
        return withUnits(value / number.value, numerators, denominators);
    }

    /// Computes the Sass floored-division remainder.
    ///
    /// @param other the right operand
    /// @return a number with the left units, or the right units when the left is unitless
    /// @throws SassValueException if the operand is not a compatible number
    @Override
    public SassValue modulo(SassValue other) {
        if (!(other instanceof SassNumber number)) {
            throw undefinedOperation("%", other);
        }
        return withCompatibleUnits(
                moduloLikeSass(value, compatibleValue(number, "%")),
                number
        );
    }

    /// Returns this immutable number unchanged.
    ///
    /// @return this number
    @Override
    public SassNumber unaryPlus() {
        return this;
    }

    /// Returns a number with the negated magnitude and the same units.
    ///
    /// @return the negated number
    @Override
    public SassNumber unaryMinus() {
        return withValue(-value);
    }

    /// Returns this number's CSS representation.
    ///
    /// Complex units and non-finite values are represented as calculations.
    ///
    /// @return the CSS number or calculation
    @Override
    public String toCssString() {
        return serialize();
    }

    /// Compares magnitudes using Sass fuzzy equality and requires identical units.
    ///
    /// @param other the object to compare
    /// @return whether values and units are semantically equal
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof SassNumber number
                && numeratorUnits.size() == number.numeratorUnits.size()
                && denominatorUnits.size() == number.denominatorUnits.size()
                && canonicalUnits(numeratorUnits).equals(canonicalUnits(number.numeratorUnits))
                && canonicalUnits(denominatorUnits).equals(canonicalUnits(number.denominatorUnits))
                && SassFuzzy.equals(canonicalValue(), number.canonicalValue());
    }

    /// Returns a hash consistent with fuzzy equality and unit identity.
    ///
    /// @return the semantic number hash
    @Override
    public int hashCode() {
        return SassFuzzy.hashCode(canonicalValue());
    }

    /// Returns the inspect-mode Sass representation.
    ///
    /// @return the serialized number or calculation
    @Override
    public String toString() {
        return serialize();
    }

    /// Serializes this number in Sass inspect and expanded CSS form.
    ///
    /// @return the number or an unsimplified calculation
    private String serialize() {
        if (slashNumerator != null) {
            return slashNumerator.serialize() + "/"
                    + Objects.requireNonNull(slashDenominator, "slash denominator").serialize();
        }
        if (Double.isFinite(value)
                && numeratorUnits.size() <= 1
                && denominatorUnits.isEmpty()) {
            return formatNumber(value)
                    + (numeratorUnits.isEmpty() ? "" : numeratorUnits.get(0));
        }

        var result = new StringBuilder("calc(");
        var firstAdditionalNumerator = 0;
        if (Double.isFinite(value)) {
            result.append(formatNumber(value));
            if (!numeratorUnits.isEmpty()) {
                result.append(numeratorUnits.get(0));
                firstAdditionalNumerator = 1;
            }
        } else if (Double.isNaN(value)) {
            result.append("NaN");
        } else {
            result.append(value > 0 ? "infinity" : "-infinity");
        }
        for (var index = firstAdditionalNumerator;
             index < numeratorUnits.size(); index++) {
            result.append(" * 1").append(numeratorUnits.get(index));
        }
        for (var unit : denominatorUnits) {
            result.append(" / 1").append(unit);
        }
        return result.append(')').toString();
    }

    /// Returns a compatible result with the units selected by Sass arithmetic.
    ///
    /// A unitless left operand adopts a unitful right operand's units. Other
    /// operations retain the left operand's units.
    ///
    /// @param newValue the result magnitude
    /// @param other    the compatible right operand
    /// @return the result with the required units
    private SassNumber withCompatibleUnits(double newValue, SassNumber other) {
        return isUnitless() && !other.isUnitless()
                ? other.withValue(newValue)
                : withValue(newValue);
    }

    /// Returns a copy with a replacement magnitude.
    ///
    /// @param newValue the replacement magnitude
    /// @return the number with unchanged units
    private SassNumber withValue(double newValue) {
        return new SassNumber(newValue, numeratorUnits, denominatorUnits, null, null);
    }

    /// Returns the compatible right magnitude or throws an operation failure.
    ///
    /// Unitless operands may be coerced to or from any units without changing
    /// their magnitude. Compatible units are converted to this number's units.
    ///
    /// @param other    the right operand
    /// @param operator the operator used in the failure message
    /// @return the right magnitude
    /// @throws SassValueException if the operand is not a compatible number
    private double compatibleValue(SassValue other, String operator) {
        if (!(other instanceof SassNumber number)) {
            throw undefinedOperation(operator, other);
        }
        if (isUnitless() || number.isUnitless()) {
            return number.value;
        }
        try {
            return coerceValueTo(number, numeratorUnits, denominatorUnits);
        } catch (IllegalArgumentException ignored) {
            throw new SassValueException(this + " and " + number + " have incompatible units.");
        }
    }

    /// Creates the standard undefined-operation failure for this number.
    ///
    /// @param operator the Sass operator spelling
    /// @param other    the right operand
    /// @return the operation failure
    private SassValueException undefinedOperation(String operator, SassValue other) {
        return new SassValueException(
                "Undefined operation \"" + this + " " + operator + " " + other + "\"."
        );
    }

    /// Copies and validates one unit list.
    ///
    /// @param units the source units
    /// @param name  the parameter name used by null checks
    /// @return a mutable validated copy
    /// @throws IllegalArgumentException if a unit is empty
    private static ArrayList<String> validatedUnits(List<String> units, String name) {
        Objects.requireNonNull(units, name);
        var result = new ArrayList<String>(units.size());
        for (var unit : units) {
            Objects.requireNonNull(unit, "unit");
            if (unit.isEmpty()) {
                throw new IllegalArgumentException("units must not be empty");
            }
            result.add(unit);
        }
        return result;
    }

    /// Converts a number magnitude to target numerator and denominator units.
    ///
    /// @param source             the number being converted
    /// @param targetNumerators   the required numerator units
    /// @param targetDenominators the required denominator units
    /// @return the converted magnitude
    /// @throws IllegalArgumentException if the unit structures are incompatible
    private static double coerceValueTo(
            SassNumber source,
            List<String> targetNumerators,
            List<String> targetDenominators
    ) {
        if (source.numeratorUnits.size() != targetNumerators.size()
                || source.denominatorUnits.size() != targetDenominators.size()) {
            throw new IllegalArgumentException("incompatible unit counts");
        }

        var result = source.value;
        var remainingNumerators = new ArrayList<>(source.numeratorUnits);
        for (var target : targetNumerators) {
            var match = conversionMatch(target, remainingNumerators);
            if (match == null) {
                throw new IllegalArgumentException("incompatible numerator units");
            }
            result *= match.factor;
            remainingNumerators.remove(match.index);
        }

        var remainingDenominators = new ArrayList<>(source.denominatorUnits);
        for (var target : targetDenominators) {
            var match = conversionMatch(target, remainingDenominators);
            if (match == null) {
                throw new IllegalArgumentException("incompatible denominator units");
            }
            result /= match.factor;
            remainingDenominators.remove(match.index);
        }
        return result;
    }

    /// Finds the first source unit convertible to a target unit.
    ///
    /// @param targetUnit  the requested target unit
    /// @param sourceUnits the remaining source units
    /// @return the conversion match, or {@code null} when none is compatible
    private static @Nullable ConversionMatch conversionMatch(
            String targetUnit,
            List<String> sourceUnits
    ) {
        for (var index = 0; index < sourceUnits.size(); index++) {
            var factor = conversionFactor(targetUnit, sourceUnits.get(index));
            if (factor != null) {
                return new ConversionMatch(index, factor);
            }
        }
        return null;
    }

    /// Returns the factor that converts a magnitude from one unit to another.
    ///
    /// @param targetUnit the resulting unit
    /// @param sourceUnit the current unit
    /// @return the factor multiplied into the source magnitude, or {@code null}
    private static @Nullable Double conversionFactor(String targetUnit, String sourceUnit) {
        if (targetUnit.equals(sourceUnit)) {
            return 1.0;
        }
        @Nullable UnitDefinition target = CONVERTIBLE_UNITS.get(targetUnit);
        @Nullable UnitDefinition source = CONVERTIBLE_UNITS.get(sourceUnit);
        return target != null && source != null
                && target.canonicalUnit.equals(source.canonicalUnit)
                ? source.canonicalScale / target.canonicalScale
                : null;
    }

    /// Returns a sorted canonical representation of one unit list.
    ///
    /// @param units the units to canonicalize
    /// @return canonical units sorted by code-unit order
    private static @Unmodifiable List<String> canonicalUnits(List<String> units) {
        return units.stream()
                .map(unit -> {
                    @Nullable UnitDefinition definition = CONVERTIBLE_UNITS.get(unit);
                    return definition == null ? unit : definition.canonicalUnit;
                })
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /// Returns this magnitude expressed in canonical units.
    ///
    /// @return the canonical magnitude
    private double canonicalValue() {
        var result = value;
        for (var unit : numeratorUnits) {
            @Nullable UnitDefinition definition = CONVERTIBLE_UNITS.get(unit);
            if (definition != null) {
                result *= definition.canonicalScale;
            }
        }
        for (var unit : denominatorUnits) {
            @Nullable UnitDefinition definition = CONVERTIBLE_UNITS.get(unit);
            if (definition != null) {
                result /= definition.canonicalScale;
            }
        }
        return result;
    }

    /// Computes a remainder using Sass floored-division semantics.
    ///
    /// @param left  the dividend
    /// @param right the divisor
    /// @return the Sass remainder
    private static double moduloLikeSass(double left, double right) {
        if (Double.isInfinite(left) || right == 0.0) {
            return Double.NaN;
        }
        if (Double.isInfinite(right)) {
            return Math.copySign(1.0, left) == Math.copySign(1.0, right)
                    ? left
                    : Double.NaN;
        }
        var result = left % right;
        if (result == 0.0) {
            return 0.0;
        }
        return Math.copySign(1.0, result) == Math.copySign(1.0, right)
                ? result
                : result + right;
    }

    /// Formats a number without an unnecessary decimal suffix or exponent.
    ///
    /// @param number the number to format
    /// @return the inspect representation
    private static String formatNumber(double number) {
        if (!Double.isFinite(number)) {
            return Double.toString(number);
        }
        var decimal = BigDecimal.valueOf(number).stripTrailingZeros();
        return decimal.signum() == 0 ? "0" : decimal.toPlainString();
    }

    /// Describes a unit's canonical dimension and scale.
    ///
    /// @param canonicalUnit  the canonical unit name for the dimension
    /// @param canonicalScale the magnitude in canonical units for one unit
    private record UnitDefinition(String canonicalUnit, double canonicalScale) {
        /// Creates validated unit conversion metadata.
        private UnitDefinition {
            Objects.requireNonNull(canonicalUnit, "canonicalUnit");
        }
    }

    /// Describes one compatible source-unit match.
    ///
    /// @param index  the matched index in the source list
    /// @param factor the magnitude conversion factor
    private record ConversionMatch(int index, double factor) {
        /// Creates a conversion match.
        private ConversionMatch {
        }
    }
}
