// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// A CSS calculation value such as {@code calc()}, {@code sin()}, or {@code clamp()}.
///
/// Fully simplified calculations return [SassNumber] instead. Remaining
/// calculations are special numbers that serialize as CSS function calls.
@ApiStatus.Internal
@NotNullByDefault
public final class SassCalculation implements SassValue {
    /// The lowercase CSS calculation function name.
    private final String name;

    /// The immutable simplified calculation arguments.
    private final @Unmodifiable List<Object> arguments;

    /// Creates an unresolved calculation.
    ///
    /// @param name the lowercase function name
    /// @param arguments the simplified arguments
    private SassCalculation(String name, List<Object> arguments) {
        this.name = Objects.requireNonNull(name, "name");
        this.arguments = List.copyOf(arguments);
    }

    /// Creates an unsimplified calculation.
    ///
    /// @param name      the calculation name
    /// @param arguments the calculation arguments
    /// @return the calculation
    public static SassCalculation unsimplified(String name, List<Object> arguments) {
        return new SassCalculation(name.toLowerCase(Locale.ROOT), arguments);
    }

    /// Creates a simplified {@code calc()} value.
    ///
    /// @param argument the single argument
    /// @return a number or calculation
    public static SassValue calc(Object argument) {
        Object simplified = simplify(argument);
        if (simplified instanceof SassNumber number) {
            return number;
        }
        if (simplified instanceof SassCalculation calculation) {
            return calculation;
        }
        return new SassCalculation("calc", List.of(simplified));
    }

    /// Creates a simplified {@code min()} value.
    ///
    /// @param arguments the arguments
    /// @return a number or calculation
    public static SassValue min(List<Object> arguments) {
        var args = simplifyAll(arguments);
        if (args.isEmpty()) {
            throw new SassValueException("min() must have at least one argument.");
        }
        @Nullable SassNumber minimum = null;
        for (var arg : args) {
            if (!(arg instanceof SassNumber number)
                    || (minimum != null && !minimum.isComparableTo(number))) {
                minimum = null;
                break;
            }
            if (minimum == null
                    || number.valueInUnitsOf(minimum) < minimum.value()) {
                minimum = number;
            }
        }
        if (minimum != null) {
            return minimum;
        }
        verifyCompatibleNumbers(args);
        return new SassCalculation("min", args);
    }

    /// Creates a simplified {@code max()} value.
    ///
    /// @param arguments the arguments
    /// @return a number or calculation
    public static SassValue max(List<Object> arguments) {
        var args = simplifyAll(arguments);
        if (args.isEmpty()) {
            throw new SassValueException("max() must have at least one argument.");
        }
        @Nullable SassNumber maximum = null;
        for (var arg : args) {
            if (!(arg instanceof SassNumber number)
                    || (maximum != null && !maximum.isComparableTo(number))) {
                maximum = null;
                break;
            }
            if (maximum == null
                    || number.valueInUnitsOf(maximum) > maximum.value()) {
                maximum = number;
            }
        }
        if (maximum != null) {
            return maximum;
        }
        verifyCompatibleNumbers(args);
        return new SassCalculation("max", args);
    }

    /// Creates a simplified {@code clamp()} value.
    ///
    /// @param min     the minimum
    /// @param value   the preferred value, or {@code null} when omitted via var()
    /// @param max     the maximum, or {@code null} when omitted via var()
    /// @return a number or calculation
    public static SassValue clamp(Object min, @Nullable Object value, @Nullable Object max) {
        var args = new ArrayList<Object>();
        args.add(simplify(min));
        if (value != null) {
            args.add(simplify(value));
        }
        if (max != null) {
            args.add(simplify(max));
        }
        verifyLength(args, 3);
        verifyCompatibleNumbers(args);
        if (args.size() == 3
                && args.get(0) instanceof SassNumber minNumber
                && args.get(1) instanceof SassNumber valueNumber
                && args.get(2) instanceof SassNumber maxNumber
                && minNumber.isComparableTo(valueNumber)
                && valueNumber.isComparableTo(maxNumber)) {
            if (SassFuzzy.greaterThanOrEquals(minNumber.value(), maxNumber.valueInUnitsOf(minNumber))) {
                return minNumber;
            }
            if (SassFuzzy.greaterThanOrEquals(minNumber.value(), valueNumber.valueInUnitsOf(minNumber))) {
                return minNumber;
            }
            if (SassFuzzy.lessThanOrEquals(maxNumber.value(), valueNumber.valueInUnitsOf(maxNumber))) {
                return maxNumber;
            }
            return valueNumber;
        }
        return new SassCalculation("clamp", args);
    }

    /// Creates a simplified single-argument math calculation.
    ///
    /// @param name         the calculation name
    /// @param argument     the argument
    /// @param math         the numeric implementation
    /// @param forbidUnits  whether units are rejected
    /// @return a number or calculation
    public static SassValue singleArgument(
            String name,
            Object argument,
            java.util.function.Function<SassNumber, SassNumber> math,
            boolean forbidUnits
    ) {
        Object simplified = simplify(argument);
        if (!(simplified instanceof SassNumber number)) {
            return new SassCalculation(name, List.of(simplified));
        }
        if (forbidUnits) {
            number.assertNoUnits();
        }
        return math.apply(number);
    }

    /// Creates a simplified {@code hypot()} value.
    ///
    /// @param arguments the arguments
    /// @return a number or calculation
    public static SassValue hypot(List<Object> arguments) {
        var args = simplifyAll(arguments);
        if (args.isEmpty()) {
            throw new SassValueException("hypot() must have at least one argument.");
        }
        verifyCompatibleNumbers(args);
        Object first = args.get(0);
        if (!(first instanceof SassNumber firstNumber)
                || firstNumber.numeratorUnits().equals(List.of("%"))) {
            return new SassCalculation("hypot", args);
        }
        double subtotal = 0.0;
        for (var arg : args) {
            if (!(arg instanceof SassNumber number) || !hasCompatibleUnits(firstNumber, number)) {
                return new SassCalculation("hypot", args);
            }
            double value = number.valueInUnitsOf(firstNumber);
            subtotal += value * value;
        }
        return SassNumber.withUnits(
                Math.sqrt(subtotal),
                firstNumber.numeratorUnits(),
                firstNumber.denominatorUnits()
        );
    }

    /// Creates a simplified {@code pow()} value.
    ///
    /// @param base     the base
    /// @param exponent the exponent, or {@code null} when omitted via var()
    /// @return a number or calculation
    public static SassValue pow(Object base, @Nullable Object exponent) {
        var args = new ArrayList<Object>();
        args.add(simplify(base));
        if (exponent != null) {
            args.add(simplify(exponent));
        }
        verifyLength(args, 2);
        base = args.get(0);
        exponent = args.size() > 1 ? args.get(1) : null;
        if (!(base instanceof SassNumber baseNumber)
                || !(exponent instanceof SassNumber exponentNumber)) {
            return new SassCalculation("pow", args);
        }
        baseNumber.assertNoUnits();
        exponentNumber.assertNoUnits();
        return SassNumber.of(
                powSpecial(baseNumber.value(), exponentNumber.value()),
                null
        );
    }

    /// Computes a power with dart-sass special cases for exact {@code ±1} and infinity.
    private static double powSpecial(double base, double exponent) {
        if (base == 1.0) {
            return 1.0;
        }
        if (base == -1.0 && Double.isInfinite(exponent)) {
            return 1.0;
        }
        return Math.pow(base, exponent);
    }

    /// Creates a simplified {@code log()} value.
    ///
    /// @param number the number
    /// @param base   the optional base
    /// @return a number or calculation
    public static SassValue log(Object number, @Nullable Object base) {
        Object simplifiedNumber = simplify(number);
        @Nullable Object simplifiedBase = base == null ? null : simplify(base);
        var args = new ArrayList<Object>();
        args.add(simplifiedNumber);
        if (simplifiedBase != null) {
            args.add(simplifiedBase);
        }
        if (!(simplifiedNumber instanceof SassNumber n)
                || (simplifiedBase != null && !(simplifiedBase instanceof SassNumber))) {
            return new SassCalculation("log", args);
        }
        n.assertNoUnits();
        if (simplifiedBase == null) {
            return SassNumber.of(Math.log(n.value()), null);
        }
        var b = (SassNumber) simplifiedBase;
        b.assertNoUnits();
        return SassNumber.of(Math.log(n.value()) / Math.log(b.value()), null);
    }

    /// Creates a simplified {@code atan2()} value.
    ///
    /// @param y the y argument
    /// @param x the x argument, or {@code null} when omitted via var()
    /// @return a number or calculation
    public static SassValue atan2(Object y, @Nullable Object x) {
        var args = new ArrayList<Object>();
        args.add(simplify(y));
        if (x != null) {
            args.add(simplify(x));
        }
        verifyLength(args, 2);
        verifyCompatibleNumbers(args);
        if (args.size() == 2
                && args.get(0) instanceof SassNumber yNumber
                && args.get(1) instanceof SassNumber xNumber
                && !yNumber.numeratorUnits().equals(List.of("%"))
                && !xNumber.numeratorUnits().equals(List.of("%"))
                && hasCompatibleUnits(yNumber, xNumber)) {
            double radians = Math.atan2(yNumber.value(), xNumber.valueInUnitsOf(yNumber));
            return SassNumber.of(radians * (180.0 / Math.PI), "deg");
        }
        return new SassCalculation("atan2", args);
    }

    /// Creates a simplified {@code mod()} value.
    ///
    /// @param dividend the dividend
    /// @param modulus  the modulus, or {@code null} when omitted via var()
    /// @return a number or calculation
    public static SassValue mod(Object dividend, @Nullable Object modulus) {
        var args = new ArrayList<Object>();
        args.add(simplify(dividend));
        if (modulus != null) {
            args.add(simplify(modulus));
        }
        verifyLength(args, 2);
        verifyCompatibleNumbers(args);
        if (args.size() == 2
                && args.get(0) instanceof SassNumber left
                && args.get(1) instanceof SassNumber right
                && hasCompatibleUnits(left, right)) {
            return left.modulo(right);
        }
        return new SassCalculation("mod", args);
    }

    /// Creates a simplified {@code rem()} value.
    ///
    /// @param dividend the dividend
    /// @param modulus  the modulus, or {@code null} when omitted via var()
    /// @return a number or calculation
    public static SassValue rem(Object dividend, @Nullable Object modulus) {
        var args = new ArrayList<Object>();
        args.add(simplify(dividend));
        if (modulus != null) {
            args.add(simplify(modulus));
        }
        verifyLength(args, 2);
        verifyCompatibleNumbers(args);
        if (args.size() == 2
                && args.get(0) instanceof SassNumber left
                && args.get(1) instanceof SassNumber right
                && hasCompatibleUnits(left, right)) {
            SassValue result = left.modulo(right);
            if (!(result instanceof SassNumber remainder)) {
                return result;
            }
            // Preserve IEEE signed zeros so rem(-0, infinity) keeps −0.
            double leftSign = signedSignum(left.value());
            double rightSign = signedSignum(right.value());
            if (leftSign != rightSign) {
                if (Double.isInfinite(right.value())) {
                    return left;
                }
                if (remainder.value() == 0.0) {
                    return SassNumber.withUnits(
                            -0.0,
                            remainder.numeratorUnits(),
                            remainder.denominatorUnits()
                    );
                }
                return remainder.minus(right);
            }
            return remainder;
        }
        return new SassCalculation("rem", args);
    }

    /// Creates a simplified {@code round()} value.
    ///
    /// @param strategyOrNumber strategy or number
    /// @param numberOrStep     number or step
    /// @param step             optional step
    /// @return a number or calculation
    public static SassValue round(
            Object strategyOrNumber,
            @Nullable Object numberOrStep,
            @Nullable Object step
    ) {
        return round(strategyOrNumber, numberOrStep, step, null, null);
    }

    /// Creates a simplified {@code round()} value with optional legacy global behavior.
    ///
    /// @param strategyOrNumber     strategy or number
    /// @param numberOrStep         number or step
    /// @param step                 optional step
    /// @param inLegacySassFunction legacy global function name, or {@code null}
    /// @param warn                 deprecation reporter, or {@code null}
    /// @return a number or calculation
    public static SassValue round(
            Object strategyOrNumber,
            @Nullable Object numberOrStep,
            @Nullable Object step,
            @Nullable String inLegacySassFunction,
            @Nullable java.util.function.Consumer<String> warn
    ) {
        Object first = simplify(strategyOrNumber);
        @Nullable Object second = numberOrStep == null ? null : simplify(numberOrStep);
        @Nullable Object third = step == null ? null : simplify(step);
        // Three-argument form requires a rounding strategy as the first argument.
        // Forms such as {@code round(10px + 2px, 8px, 9px)} simplify the sum first
        // and then fail with dart-sass's strategy diagnostic.
        if (first instanceof SassNumber number && second != null && third != null) {
            throw new SassValueException(
                    number + " must be either nearest, up, down or to-zero."
            );
        }
        if (second == null && third == null && first instanceof SassNumber number) {
            if (number.isUnitless()) {
                // Global Sass {@code round()} (legacy) uses half-away-from-zero;
                // pure CSS calculation {@code round()} uses banker's {@code rint}.
                double rounded = inLegacySassFunction != null
                        ? roundHalfAwayFromZero(number.value())
                        : Math.rint(number.value());
                return SassNumber.of(rounded, null);
            }
            if (inLegacySassFunction != null) {
                if (warn != null) {
                    warn.accept(
                            "In future versions of Sass, round() will be interpreted as a CSS "
                                    + "round() calculation. This requires an explicit modulus when "
                                    + "rounding numbers with units. If you want to use the Sass "
                                    + "function, call math.round() instead.\n"
                                    + "\n"
                                    + "See https://sass-lang.com/d/import"
                    );
                }
                return matchUnits(roundHalfAwayFromZero(number.value()), number);
            }
            // With units, CSS round requires an explicit step; preserve calculation.
            return new SassCalculation("round", List.of(first));
        }
        if (second instanceof SassNumber stepNumber && first instanceof SassNumber number
                && third == null) {
            verifyCompatibleNumbers(List.of(number, stepNumber));
            if (hasCompatibleUnits(number, stepNumber)) {
                return roundWithStep("nearest", number, stepNumber);
            }
            return new SassCalculation("round", List.of(number, stepNumber));
        }
        if (first instanceof SassString strategy
                && !strategy.hasQuotes()
                && isRoundStrategy(strategy.text())) {
            if (second instanceof SassString rest && !rest.hasQuotes() && third == null) {
                // Forms such as round(up, var(--c)) remain unsimplified.
                return new SassCalculation("round", List.of(strategy, rest));
            }
            if (second != null && third == null) {
                throw new SassValueException("If strategy is not null, step is required.");
            }
            if (second instanceof SassNumber number && third instanceof SassNumber stepNumber) {
                verifyCompatibleNumbers(List.of(number, stepNumber));
                if (hasCompatibleUnits(number, stepNumber)) {
                    return roundWithStep(strategy.text().toLowerCase(Locale.ROOT), number, stepNumber);
                }
                return new SassCalculation("round", List.of(strategy, number, stepNumber));
            }
            if (second != null && third != null) {
                return new SassCalculation("round", List.of(strategy, second, third));
            }
        }
        if (first instanceof SassString strategy
                && !strategy.hasQuotes()
                && isRoundStrategy(strategy.text())
                && second == null
                && third == null) {
            throw new SassValueException("Number to round and step arguments are required.");
        }
        var args = new ArrayList<Object>();
        args.add(first);
        if (second != null) {
            args.add(second);
        }
        if (third != null) {
            args.add(third);
        }
        // Special CSS variables may stand in for strategy or numeric slots.
        if (first instanceof SassString firstString
                && !firstString.hasQuotes()
                && isSpecialVariable(firstString.text())
                && second != null
                && third != null) {
            return new SassCalculation("round", args);
        }
        if (first instanceof SassString strategy
                && !strategy.hasQuotes()
                && !isRoundStrategy(strategy.text())
                && !isSpecialVariable(strategy.text())
                && second != null
                && third != null) {
            throw new SassValueException(
                    first + " must be either nearest, up, down or to-zero."
            );
        }
        return new SassCalculation("round", args);
    }

    /// Returns whether text is a CSS special variable call such as {@code var(...)}.
    private static boolean isSpecialVariable(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("var(")
                || lower.startsWith("env(")
                || lower.startsWith("arg(")
                || lower.startsWith("attr(");
    }

    /// Creates a simplified {@code calc-size()} value.
    ///
    /// @param basis the basis
    /// @param value the value, or {@code null}
    /// @return the calculation
    public static SassValue calcSize(Object basis, @Nullable Object value) {
        var args = new ArrayList<Object>();
        args.add(simplify(basis));
        if (value != null) {
            args.add(simplify(value));
        }
        verifyLength(args, 2);
        return new SassCalculation("calc-size", args);
    }

    /// Combines two calculation operands with an operator, simplifying when possible.
    ///
    /// @param operator the operator
    /// @param left     the left operand
    /// @param right    the right operand
    /// @return a number or operation tree node
    public static Object operate(CalculationOperator operator, Object left, Object right) {
        return operate(operator, left, right, null, null);
    }

    /// Combines two calculation operands, optionally allowing legacy unitless mixing.
    ///
    /// When {@code inLegacySassFunction} is non-null, unitless numbers may be
    /// added or subtracted with unitful numbers for global {@code min()}/{@code max()}/
    /// {@code round()}/{@code abs()} compatibility. The optional {@code warn} callback
    /// receives the global-builtin deprecation message.
    ///
    /// @param operator               the operator
    /// @param left                   the left operand
    /// @param right                  the right operand
    /// @param inLegacySassFunction   the legacy global function name, or {@code null}
    /// @param warn                   deprecation reporter, or {@code null}
    /// @return a number or operation tree node
    public static Object operate(
            CalculationOperator operator,
            Object left,
            Object right,
            @Nullable String inLegacySassFunction,
            @Nullable java.util.function.Consumer<String> warn
    ) {
        left = simplify(left);
        right = simplify(right);
        if (operator == CalculationOperator.PLUS || operator == CalculationOperator.MINUS) {
            if (left instanceof SassNumber leftNumber && right instanceof SassNumber rightNumber) {
                boolean compatible = hasCompatibleUnits(leftNumber, rightNumber);
                if (!compatible
                        && inLegacySassFunction != null
                        && leftNumber.isComparableTo(rightNumber)) {
                    if (warn != null) {
                        warn.accept(
                                "In future versions of Sass, " + inLegacySassFunction
                                        + "() will be interpreted as the CSS "
                                        + inLegacySassFunction
                                        + "() calculation. This doesn't allow unitless "
                                        + "numbers to be mixed with numbers with units. "
                                        + "If you want to use the Sass function, call math."
                                        + inLegacySassFunction + "() instead.\n"
                                        + "\n"
                                        + "See https://sass-lang.com/d/import"
                        );
                    }
                    compatible = true;
                }
                if (compatible) {
                    return operator == CalculationOperator.PLUS
                            ? leftNumber.plus(rightNumber)
                            : leftNumber.minus(rightNumber);
                }
            }
            verifyCompatibleNumbers(List.of(left, right));
            if (right instanceof SassNumber rightNumber && SassFuzzy.lessThan(rightNumber.value(), 0.0)) {
                right = rightNumber.times(SassNumber.of(-1, null));
                operator = operator == CalculationOperator.PLUS
                        ? CalculationOperator.MINUS
                        : CalculationOperator.PLUS;
            }
            return new CalculationOperation(operator, left, right);
        }
        if (left instanceof SassNumber leftNumber && right instanceof SassNumber rightNumber) {
            return operator == CalculationOperator.TIMES
                    ? leftNumber.times(rightNumber)
                    : leftNumber.dividedBy(rightNumber);
        }
        return new CalculationOperation(operator, left, right);
    }

    /// Returns the calculation name.
    ///
    /// @return the lowercase name
    public String name() {
        return name;
    }

    /// Returns the arguments.
    ///
    /// @return the immutable arguments
    public @Unmodifiable List<Object> arguments() {
        return arguments;
    }

    /// Returns this calculation.
    ///
    /// @return this calculation
    @Override
    public SassCalculation assertCalculation() {
        return this;
    }

    @Override
    public boolean isSpecialNumber() {
        return true;
    }

    @Override
    public SassValue plus(SassValue other) {
        // String concatenation is allowed (dart-sass): {@code calc(...) + ""} yields a quoted string.
        if (other instanceof SassString) {
            return SassValue.super.plus(other);
        }
        throw undefinedCalculationOperation("+", other);
    }

    @Override
    public SassValue minus(SassValue other) {
        throw undefinedCalculationOperation("-", other);
    }

    @Override
    public SassValue times(SassValue other) {
        throw undefinedCalculationOperation("*", other);
    }

    @Override
    public SassValue dividedBy(SassValue other) {
        // Outside calc simplification, slash joins CSS text so color channel
        // forms such as {@code calc(1px + 1%) / 0.4} can reach constructors.
        return SassValue.super.dividedBy(other);
    }

    @Override
    public SassValue modulo(SassValue other) {
        throw undefinedCalculationOperation("%", other);
    }

    @Override
    public SassBoolean greaterThan(SassValue other) {
        throw undefinedCalculationOperation(">", other);
    }

    @Override
    public SassBoolean greaterThanOrEquals(SassValue other) {
        throw undefinedCalculationOperation(">=", other);
    }

    @Override
    public SassBoolean lessThan(SassValue other) {
        throw undefinedCalculationOperation("<", other);
    }

    @Override
    public SassBoolean lessThanOrEquals(SassValue other) {
        throw undefinedCalculationOperation("<=", other);
    }

    @Override
    public SassValue unaryPlus() {
        throw SassValueException.undefinedOperation(
                "Undefined operation \"+" + this + "\"."
        );
    }

    @Override
    public SassValue unaryMinus() {
        throw SassValueException.undefinedOperation(
                "Undefined operation \"-" + this + "\"."
        );
    }

    @Override
    public SassValue unaryDivide() {
        throw SassValueException.undefinedOperation(
                "Undefined operation \"/" + this + "\"."
        );
    }

    /// Creates an undefined binary operation failure for calculations.
    private SassValueException undefinedCalculationOperation(String operator, SassValue other) {
        return SassValueException.undefinedOperation(
                "Undefined operation \"" + this + " " + operator + " " + other + "\"."
        );
    }

    @Override
    public String toCssString() {
        var builder = new StringBuilder(name).append('(');
        for (var index = 0; index < arguments.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(CalculationOperation.serializeOperand(arguments.get(index)));
        }
        return builder.append(')').toString();
    }

    @Override
    public String toString() {
        return toCssString();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof SassCalculation calculation
                && name.equals(calculation.name)
                && arguments.equals(calculation.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arguments);
    }

    /// Simplifies every calculation argument while preserving order.
    ///
    /// @param arguments the input arguments
    /// @return the simplified arguments
    private static List<Object> simplifyAll(List<Object> arguments) {
        var result = new ArrayList<Object>(arguments.size());
        for (var argument : arguments) {
            result.add(simplify(argument));
        }
        return result;
    }

    /// Simplifies one value into a valid calculation operand.
    ///
    /// @param arg the candidate operand
    /// @return the simplified operand
    private static Object simplify(Object arg) {
        if (arg instanceof SassNumber || arg instanceof CalculationOperation) {
            return arg;
        }
        if (arg instanceof SassString string) {
            if (string.hasQuotes()) {
                throw new SassValueException(
                        "Quoted string " + string + " can't be used in a calculation."
                );
            }
            return string;
        }
        if (arg instanceof SassCalculation calculation) {
            if ("calc".equals(calculation.name) && calculation.arguments.size() == 1) {
                Object only = calculation.arguments.get(0);
                if (only instanceof SassString string && !string.hasQuotes()
                        && needsParentheses(string.text())) {
                    return new SassString("(" + string.text() + ")", false);
                }
                return only;
            }
            return calculation;
        }
        if (arg instanceof SassValue value) {
            throw new SassValueException("Value " + value + " can't be used in a calculation.");
        }
        throw new IllegalArgumentException("Unexpected calculation argument: " + arg);
    }

    /// Returns whether flattened calculation text requires grouping.
    ///
    /// @param text the unquoted CSS text
    /// @return whether parentheses are required
    private static boolean needsParentheses(String text) {
        for (var index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isWhitespace(character) || character == '/' || character == '*') {
                return true;
            }
        }
        return text.length() >= 4
                && text.regionMatches(true, 0, "var(", 0, 4);
    }

    /// Verifies a fixed argument count unless unresolved CSS text prevents
    /// determining the final count.
    ///
    /// @param args the calculation arguments
    /// @param expectedLength the required count
    private static void verifyLength(List<Object> args, int expectedLength) {
        if (args.size() == expectedLength) {
            return;
        }
        for (var arg : args) {
            if (arg instanceof SassString) {
                return;
            }
        }
        throw new SassValueException(
                expectedLength + " arguments required, but only " + args.size()
                        + (args.size() == 1 ? " was" : " were") + " passed."
        );
    }

    /// Rejects numeric arguments that cannot coexist in one CSS calculation.
    ///
    /// @param args the calculation arguments
    private static void verifyCompatibleNumbers(List<Object> args) {
        for (var arg : args) {
            if (arg instanceof SassNumber number && number.hasComplexUnits()) {
                throw new SassValueException(
                        "Number " + number + " isn't compatible with CSS calculations."
                );
            }
        }
        for (var i = 0; i < args.size() - 1; i++) {
            if (!(args.get(i) instanceof SassNumber number1)) {
                continue;
            }
            for (var j = i + 1; j < args.size(); j++) {
                if (!(args.get(j) instanceof SassNumber number2)) {
                    continue;
                }
                if (number1.hasPossiblyCompatibleUnits(number2)) {
                    continue;
                }
                throw new SassValueException(number1 + " and " + number2 + " are incompatible.");
            }
        }
    }

    /// Returns whether two numbers have matching, mutually coercible unit
    /// dimensions.
    ///
    /// @param left the first number
    /// @param right the second number
    /// @return whether their units are compatible
    private static boolean hasCompatibleUnits(
            SassNumber left,
            SassNumber right
    ) {
        if (left.isUnitless() && right.isUnitless()) {
            return true;
        }
        if (left.isUnitless() || right.isUnitless()) {
            return false;
        }
        return left.isComparableTo(right)
                && left.numeratorUnits().size() == right.numeratorUnits().size()
                && left.denominatorUnits().size() == right.denominatorUnits().size();
    }

    /// Returns whether text names a CSS round strategy.
    ///
    /// @param text the candidate name
    /// @return whether it is a recognized strategy
    private static boolean isRoundStrategy(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        return "nearest".equals(lower) || "up".equals(lower)
                || "down".equals(lower) || "to-zero".equals(lower);
    }

    /// Applies a CSS round strategy with a unit-compatible step.
    ///
    /// @param strategy the normalized strategy name
    /// @param number the value to round
    /// @param step the rounding step
    /// @return the rounded value with the input units
    private static SassNumber roundWithStep(
            String strategy,
            SassNumber number,
            SassNumber step
    ) {
        if (Double.isInfinite(number.value()) && Double.isInfinite(step.value())
                || step.value() == 0.0
                || Double.isNaN(number.value())
                || Double.isNaN(step.value())) {
            return matchUnits(Double.NaN, number);
        }
        if (Double.isInfinite(number.value())) {
            return number;
        }
        double stepValue = step.valueInUnitsOf(number);
        if (Double.isInfinite(step.value())) {
            // CSS Values: infinite step maps finite numbers to 0 or infinity
            // while preserving the signed-zero of a zero input for down/up.
            boolean negative = isNegativeIncludingNegativeZero(number.value());
            return switch (strategy) {
                case "nearest", "to-zero" -> matchUnits(
                        negative ? -0.0 : 0.0,
                        number
                );
                // up: positive → +∞; +0 → +0; negative or −0 → −0
                case "up" -> number.value() > 0
                        ? matchUnits(Double.POSITIVE_INFINITY, number)
                        : matchUnits(negative ? -0.0 : 0.0, number);
                // down: negative → −∞; −0 → −0; non-negative → +0
                case "down" -> number.value() < 0
                        ? matchUnits(Double.NEGATIVE_INFINITY, number)
                        : matchUnits(negative ? -0.0 : 0.0, number);
                default -> matchUnits(Double.NaN, number);
            };
        }
        double ratio = number.value() / stepValue;
        double rounded = switch (strategy) {
            // CSS nearest: at a midpoint choose the integer with larger |value|.
            case "nearest" -> nearestAwayFromZero(ratio) * stepValue;
            case "up" -> (step.value() < 0 ? Math.floor(ratio) : Math.ceil(ratio)) * stepValue;
            case "down" -> (step.value() < 0 ? Math.ceil(ratio) : Math.floor(ratio)) * stepValue;
            case "to-zero" -> (number.value() < 0 ? Math.ceil(ratio) : Math.floor(ratio)) * stepValue;
            default -> Double.NaN;
        };
        return matchUnits(rounded, number);
    }

    /// Returns whether {@code value} is negative or a negative zero.
    ///
    /// @param value the IEEE-754 value
    /// @return whether the sign bit is set or the value is less than zero
    private static boolean isNegativeIncludingNegativeZero(double value) {
        return value < 0.0 || value == 0.0 && Double.doubleToRawLongBits(value) < 0L;
    }

    /// Returns {@code -1.0}, {@code 0.0}, or {@code 1.0}, treating signed zeros
    /// as signed (unlike {@link Math#signum(double)} which maps both zeros to
    /// {@code +0.0}).
    ///
    /// @param value the IEEE-754 value
    /// @return the signed unit direction of {@code value}
    private static double signedSignum(double value) {
        if (value > 0.0) {
            return 1.0;
        }
        if (value < 0.0) {
            return -1.0;
        }
        return Double.doubleToRawLongBits(value) < 0L ? -1.0 : 1.0;
    }

    /// Rounds {@code ratio} to the nearest integer; midpoints choose the integer
    /// with the larger absolute value (CSS {@code nearest} strategy).
    ///
    /// @param ratio the value divided by the step
    /// @return the chosen integer ratio
    private static double nearestAwayFromZero(double ratio) {
        double floor = Math.floor(ratio);
        double ceil = Math.ceil(ratio);
        if (floor == ceil) {
            return floor;
        }
        double toFloor = Math.abs(ratio - floor);
        double toCeil = Math.abs(ceil - ratio);
        if (toFloor < toCeil) {
            return floor;
        }
        if (toCeil < toFloor) {
            return ceil;
        }
        return Math.abs(ceil) >= Math.abs(floor) ? ceil : floor;
    }

    /// Creates a number with a replacement magnitude and an existing unit
    /// representation.
    ///
    /// @param value the replacement magnitude
    /// @param number the source of numerator and denominator units
    /// @return the new number
    private static SassNumber matchUnits(double value, SassNumber number) {
        return SassNumber.withUnits(value, number.numeratorUnits(), number.denominatorUnits());
    }

    /// Rounds midpoints away from zero, matching Sass {@code math.round()}.
    ///
    /// @param value the value to round
    /// @return the rounded magnitude
    private static double roundHalfAwayFromZero(double value) {
        var rounded = Math.floor(Math.abs(value) + 0.5);
        return rounded == 0.0 ? 0.0 : Math.copySign(rounded, value);
    }
}
