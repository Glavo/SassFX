// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Represents an immutable Sass number with numerator and denominator units.
///
/// Convertible CSS units are simplified, compared, and hashed through a
/// canonical unit for their dimension.
@ApiStatus.Internal
@NotNullByDefault
public final class SassNumber implements SassValue {
    /// Maximum decimal places written for CSS number serialization, matching dart-sass.
    public static final int PRECISION = 10;

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

    /// Returns Sass source that removes this number's units from a variable.
    ///
    /// The expression preserves the variable's numeric magnitude. When
    /// {@code targetUnit} is non-null, the expression then applies that unit.
    /// The variable name must not include the leading dollar sign.
    ///
    /// @param name the variable name without a leading dollar sign
    /// @param targetUnit the unit to apply after removing existing units, or
    ///                   {@code null} for a unitless result
    /// @return the conversion expression
    public String unitSuggestion(
            String name,
            @Nullable String targetUnit
    ) {
        Objects.requireNonNull(name, "name");
        var result = new StringBuilder("$").append(name);
        for (var unit : denominatorUnits) {
            result.append(" * 1").append(unit);
        }
        for (var unit : numeratorUnits) {
            result.append(" / 1").append(unit);
        }
        if (targetUnit != null) {
            result.append(" * 1").append(targetUnit);
        }
        return numeratorUnits.isEmpty()
                ? result.toString()
                : "calc(" + result + ")";
    }

    /// Returns whether this number has no units.
    ///
    /// @return whether both unit lists are empty
    public boolean isUnitless() {
        return numeratorUnits.isEmpty() && denominatorUnits.isEmpty();
    }

    /// Returns this number.
    ///
    /// @return this number
    @Override
    public SassNumber assertNumber() {
        return this;
    }

    /// Returns this magnitude when it is a Sass integer.
    ///
    /// @return the integer magnitude
    /// @throws SassValueException if the magnitude is not an integer
    public int assertInt() {
        @Nullable Integer integer = SassFuzzy.asInt(value);
        if (integer == null) {
            throw new SassValueException(this + " is not an int.");
        }
        return integer;
    }

    /// Returns this number when it has no units.
    ///
    /// @return this unitless number
    /// @throws SassValueException if this number has units
    public SassNumber assertNoUnits() {
        if (!isUnitless()) {
            throw new SassValueException("Expected " + this + " to have no units.");
        }
        return this;
    }

    /// Returns this magnitude after validating a fuzzy-inclusive range.
    ///
    /// Values that are Sass-fuzzy-equal to either endpoint are normalized to
    /// that endpoint. The number's units are not converted or otherwise
    /// validated by this method.
    ///
    /// @param minimum the inclusive lower bound
    /// @param maximum the inclusive upper bound
    /// @return this magnitude, or a normalized endpoint
    /// @throws IllegalArgumentException if the supplied bounds are unordered or non-finite
    /// @throws SassValueException if this magnitude lies outside the range
    public double valueInRange(double minimum, double maximum) {
        if (!(Double.isFinite(minimum) && Double.isFinite(maximum) && minimum <= maximum)) {
            throw new IllegalArgumentException("range bounds must be finite and ordered");
        }
        if (SassFuzzy.equals(value, minimum)) {
            return minimum;
        }
        if (SassFuzzy.equals(value, maximum)) {
            return maximum;
        }
        if (value > minimum && value < maximum) {
            return value;
        }
        var unit = unitString();
        throw new SassValueException(
                "Expected " + this + " to be within " + formatNumber(minimum) + unit
                        + " and " + formatNumber(maximum) + unit + "."
        );
    }

    /// Returns this magnitude after validating a fuzzy-inclusive range, with a
    /// parameter-name prefix on the failure message.
    ///
    /// @param minimum the inclusive lower bound
    /// @param maximum the inclusive upper bound
    /// @param name    the parameter name used in the failure message
    /// @return this magnitude, or a normalized endpoint
    /// @throws SassValueException if this magnitude lies outside the range
    public double valueInRange(double minimum, double maximum, String name) {
        try {
            return valueInRange(minimum, maximum);
        } catch (SassValueException exception) {
            throw new SassValueException(
                    "$" + name + ": " + Objects.requireNonNull(exception.getMessage(), "range message")
            );
        }
    }

    /// Returns this magnitude after validating a fuzzy-inclusive range and writes
    /// an explicit unit on the diagnostic bounds.
    ///
    /// This matches dart-sass {@code valueInRangeWithUnit} and exists so callers
    /// such as {@code opacify()} can report unitless bounds even when the
    /// argument still carries a deprecated unit.
    ///
    /// @param minimum the inclusive lower bound
    /// @param maximum the inclusive upper bound
    /// @param name    the parameter name used in the failure message
    /// @param unit    the unit text written after each bound (may be empty)
    /// @return this magnitude, or a normalized endpoint
    /// @throws SassValueException if this magnitude lies outside the range
    public double valueInRangeWithUnit(
            double minimum,
            double maximum,
            String name,
            String unit
    ) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unit, "unit");
        if (!(Double.isFinite(minimum) && Double.isFinite(maximum) && minimum <= maximum)) {
            throw new IllegalArgumentException("range bounds must be finite and ordered");
        }
        if (SassFuzzy.equals(value, minimum)) {
            return minimum;
        }
        if (SassFuzzy.equals(value, maximum)) {
            return maximum;
        }
        if (value > minimum && value < maximum) {
            return value;
        }
        throw new SassValueException(
                "$" + name + ": Expected " + this + " to be within "
                        + formatNumber(minimum) + unit + " and "
                        + formatNumber(maximum) + unit + "."
        );
    }

    /// Returns the unit string used by Sass math functions.
    ///
    /// Matches dart-sass {@code _unitString}: single denominators use
    /// {@code unit^-1}, multiple denominators use {@code (a*b)^-1}, and mixed
    /// units use {@code n1*n2/(d1*d2)} when more than one denominator is present.
    ///
    /// @return the unit text, or the empty string when unitless
    public String unitString() {
        if (isUnitless()) {
            return "";
        }
        if (numeratorUnits.isEmpty()) {
            if (denominatorUnits.size() == 1) {
                return denominatorUnits.get(0) + "^-1";
            }
            return "(" + String.join("*", denominatorUnits) + ")^-1";
        }
        var numerators = String.join("*", numeratorUnits);
        if (denominatorUnits.isEmpty()) {
            return numerators;
        }
        if (denominatorUnits.size() == 1) {
            return numerators + "/" + denominatorUnits.get(0);
        }
        return numerators + "/(" + String.join("*", denominatorUnits) + ")";
    }

    /// Returns whether this number is unit-compatible with another number.
    ///
    /// @param other the other number
    /// @return whether the numbers may participate in the same math operation
    public boolean isComparableTo(SassNumber other) {
        Objects.requireNonNull(other, "other");
        if (isUnitless() || other.isUnitless()) {
            return true;
        }
        try {
            coerceValueTo(other, numeratorUnits, denominatorUnits);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /// Returns whether this number has multiple numerators or any denominators.
    ///
    /// Complex units cannot participate in CSS calculations.
    ///
    /// @return whether the unit structure is too complex for calculations
    public boolean hasComplexUnits() {
        return numeratorUnits.size() > 1 || !denominatorUnits.isEmpty();
    }

    /// Browser-known unit compatibility groups used by CSS calculations.
    private static final @Unmodifiable List<Set<String>> KNOWN_UNIT_COMPATIBILITY = List.of(
            Set.of(
                    "em", "rem", "ex", "rex", "cap", "rcap", "ch", "rch", "ic", "ric", "lh",
                    "rlh", "vw", "lvw", "svw", "dvw", "vh", "lvh", "svh", "dvh", "vi", "lvi",
                    "svi", "dvi", "vb", "lvb", "svb", "dvb", "vmin", "lvmin", "svmin",
                    "dvmin", "vmax", "lvmax", "svmax", "dvmax", "cqw", "cqh", "cqi", "cqb",
                    "cqmin", "cqmax", "cm", "mm", "q", "in", "pt", "pc", "px"
            ),
            Set.of("deg", "grad", "rad", "turn"),
            Set.of("s", "ms"),
            Set.of("hz", "khz"),
            Set.of("dpi", "dpcm", "dppx")
    );

    /// Maps lowercase unit names to the set of units known compatible with them.
    private static final @Unmodifiable Map<String, Set<String>> KNOWN_COMPATIBILITIES_BY_UNIT;

    static {
        var map = new java.util.HashMap<String, Set<String>>();
        for (var set : KNOWN_UNIT_COMPATIBILITY) {
            for (var unit : set) {
                map.put(unit, set);
            }
        }
        KNOWN_COMPATIBILITIES_BY_UNIT = java.util.Collections.unmodifiableMap(map);
    }

    /// Returns whether CSS may still be able to combine this number with another.
    ///
    /// Percentages and unknown units are treated as possibly compatible. Known
    /// units of different dimensions (for example {@code px} and {@code s}) are not.
    ///
    /// @param other the other number
    /// @return whether the pair is not known to be incompatible
    public boolean hasPossiblyCompatibleUnits(SassNumber other) {
        Objects.requireNonNull(other, "other");
        if (isUnitless() || other.isUnitless()) {
            return isUnitless() && other.isUnitless();
        }
        if (hasComplexUnits() || other.hasComplexUnits()) {
            return false;
        }
        if (numeratorUnits.size() != 1 || other.numeratorUnits.size() != 1) {
            return false;
        }
        var unit = numeratorUnits.get(0).toLowerCase(Locale.ROOT);
        var otherUnit = other.numeratorUnits.get(0).toLowerCase(Locale.ROOT);
        if (unit.equals(otherUnit)) {
            return true;
        }
        @Nullable Set<String> known = KNOWN_COMPATIBILITIES_BY_UNIT.get(unit);
        if (known == null) {
            // Unknown units (including %) are possibly compatible with anything.
            return true;
        }
        return known.contains(otherUnit)
                || !KNOWN_COMPATIBILITIES_BY_UNIT.containsKey(otherUnit);
    }

    /// Returns this magnitude converted into another number's units when needed.
    ///
    /// @param other the number providing target units for comparison or min/max
    /// @return the magnitude expressed in {@code other}'s units when both are unitful
    public double valueInUnitsOf(SassNumber other) {
        if (isUnitless() || other.isUnitless()) {
            return value;
        }
        try {
            return coerceValueTo(this, other.numeratorUnits, other.denominatorUnits);
        } catch (IllegalArgumentException ignored) {
            throw new SassValueException(this + " and " + other + " have incompatible units.");
        }
    }

    /// Returns this number coerced into the requested unit structure.
    ///
    /// A unitless number adopts the target units without changing magnitude.
    /// Compatible unitful numbers are converted; incompatible structures fail.
    ///
    /// @param targetNumerators   the required numerator units
    /// @param targetDenominators the required denominator units
    /// @return the coerced number
    /// @throws SassValueException if the units are incompatible
    public SassNumber coerce(
            List<String> targetNumerators,
            List<String> targetDenominators
    ) {
        Objects.requireNonNull(targetNumerators, "targetNumerators");
        Objects.requireNonNull(targetDenominators, "targetDenominators");
        if (isUnitless()) {
            return withUnits(value, targetNumerators, targetDenominators);
        }
        if (targetNumerators.isEmpty() && targetDenominators.isEmpty()) {
            return withUnits(value, List.of(), List.of());
        }
        try {
            return withUnits(
                    coerceValueTo(this, targetNumerators, targetDenominators),
                    targetNumerators,
                    targetDenominators
            );
        } catch (IllegalArgumentException ignored) {
            // Match dart-sass SassNumber.coerce: report the missing target unit
            // rather than a generic pairwise incompatibility message.
            if (targetNumerators.isEmpty() && targetDenominators.isEmpty()) {
                throw new SassValueException("Expected " + this + " to have no units.");
            }
            if (targetNumerators.size() == 1 && targetDenominators.isEmpty()) {
                throw new SassValueException(
                        "Expected " + this + " to have unit " + targetNumerators.get(0) + "."
                );
            }
            throw new SassValueException(
                    "Expected " + this + " to have units "
                            + withUnits(0, targetNumerators, targetDenominators) + "."
            );
        }
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
        if (other instanceof SassColor || other instanceof SassCalculation) {
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
        if (other instanceof SassColor || other instanceof SassCalculation) {
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
        // Calculations and other special numbers join as slash-separated CSS
        // text so color channel alpha forms such as {@code 0.3 / calc(1px + 1%)}
        // reach constructors as unquoted strings instead of hard errors.
        if (other.isSpecialNumber()) {
            return SassValue.super.dividedBy(other);
        }
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
    /// Finite unitless or single-unit values are truncated to at most
    /// [#PRECISION] digits after the decimal point.
    ///
    /// @return the CSS number or calculation
    @Override
    public String toCssString() {
        return serialize(true);
    }

    /// Serializes this number as a calculation operand.
    ///
    /// Non-finite values use bare CSS keywords ({@code infinity},
    /// {@code -infinity}, {@code NaN}) so they can appear inside an outer
    /// {@code calc()} without nesting {@code calc(calc(...))}. Unitful
    /// non-finite numbers expand as {@code infinity * 1px}-style products.
    ///
    /// @return the calculation-operand CSS text
    public String toCalculationCssString() {
        if (slashNumerator != null) {
            return toCssString();
        }
        if (Double.isFinite(value)) {
            return toCssString();
        }
        var result = new StringBuilder();
        if (Double.isNaN(value)) {
            result.append("NaN");
        } else {
            result.append(value > 0 ? "infinity" : "-infinity");
        }
        for (var unit : numeratorUnits) {
            result.append(" * 1").append(unit);
        }
        for (var unit : denominatorUnits) {
            result.append(" / 1").append(unit);
        }
        return result.toString();
    }

    /// Returns whether calculation serialization is a multi-token product/quotient
    /// that may need parentheses under a surrounding {@code /} operator.
    ///
    /// @return whether {@link #toCalculationCssString()} embeds {@code *} or {@code /}
    public boolean isCompoundCalculationOperand() {
        if (slashNumerator != null) {
            return false;
        }
        if (Double.isFinite(value)) {
            return false;
        }
        return !numeratorUnits.isEmpty() || !denominatorUnits.isEmpty();
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
        return serialize(false);
    }

    /// Serializes this number in Sass inspect or expanded CSS form.
    ///
    /// @param css whether CSS precision truncation should be applied
    /// @return the number or an unsimplified calculation
    private String serialize(boolean css) {
        if (slashNumerator != null) {
            return slashNumerator.serialize(css) + "/"
                    + Objects.requireNonNull(slashDenominator, "slash denominator").serialize(css);
        }
        if (Double.isFinite(value)
                && numeratorUnits.size() <= 1
                && denominatorUnits.isEmpty()) {
            return formatNumber(value, css)
                    + (numeratorUnits.isEmpty() ? "" : numeratorUnits.get(0));
        }

        var result = new StringBuilder("calc(");
        var firstAdditionalNumerator = 0;
        if (Double.isFinite(value)) {
            result.append(formatNumber(value, css));
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
        return formatNumber(number, false);
    }

    /// Formats a finite number for inspect or CSS emission.
    ///
    /// CSS emission writes at most [#PRECISION] digits after the decimal point
    /// and uses half-up rounding, matching dart-sass. Inspect mode retains the
    /// full {@link Double} precision as a plain decimal string.
    ///
    /// @param number the number to format
    /// @param css    whether CSS precision truncation should be applied
    /// @return the formatted number text
    public static String formatNumber(double number, boolean css) {
        if (!Double.isFinite(number)) {
            // CSS Color 4 serializes non-finite channel magnitudes as calculations.
            if (Double.isNaN(number)) {
                return "calc(NaN)";
            }
            return number > 0 ? "calc(infinity)" : "calc(-infinity)";
        }
        if (css && SassFuzzy.isInt(number)) {
            return BigDecimal.valueOf(Math.rint(number)).toBigInteger().toString();
        }
        if (!css) {
            var decimal = BigDecimal.valueOf(number).stripTrailingZeros();
            return decimal.signum() == 0 ? "0" : decimal.toPlainString();
        }
        var decimal = BigDecimal.valueOf(number)
                .setScale(PRECISION, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (decimal.scale() < 0) {
            decimal = decimal.setScale(0);
        }
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
