// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.function;

import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassBoolean;
import org.glavo.scssfx.internal.value.SassColor;
import org.glavo.scssfx.internal.value.SassList;
import org.glavo.scssfx.internal.value.SassMap;
import org.glavo.scssfx.internal.value.SassNull;
import org.glavo.scssfx.internal.value.SassNumber;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Registers the first built-in global Sass functions.
@ApiStatus.Internal
@NotNullByDefault
public final class BuiltInFunctions {
    /// Prevents instantiation.
    private BuiltInFunctions() {
    }

    /// Returns the global built-in function table keyed by normalized name.
    ///
    /// @return an immutable name-to-callable map
    public static @Unmodifiable Map<String, BuiltInCallable> global() {
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        register(functions, BuiltInCallable.of("rgb", 3, 4, BuiltInFunctions::rgb));
        register(functions, BuiltInCallable.of("rgba", 3, 4, BuiltInFunctions::rgb));
        register(functions, BuiltInCallable.of("quote", 1, BuiltInFunctions::quote));
        register(functions, BuiltInCallable.of("unquote", 1, BuiltInFunctions::unquote));
        register(functions, BuiltInCallable.of("length", 1, BuiltInFunctions::length));
        register(functions, BuiltInCallable.of("nth", 2, BuiltInFunctions::nth));
        register(functions, BuiltInCallable.of("join", 2, 3, BuiltInFunctions::join));
        register(functions, BuiltInCallable.of("append", 2, 3, BuiltInFunctions::append));
        register(functions, BuiltInCallable.of("type-of", 1, BuiltInFunctions::typeOf));
        register(functions, BuiltInCallable.of("inspect", 1, BuiltInFunctions::inspect));
        register(functions, BuiltInCallable.of("unit", 1, BuiltInFunctions::unit));
        register(functions, BuiltInCallable.of("comparable", 2, BuiltInFunctions::comparable));
        register(functions, BuiltInCallable.of("percentage", 1, BuiltInFunctions::percentage));
        register(functions, BuiltInCallable.of("abs", 1, BuiltInFunctions::abs));
        register(functions, BuiltInCallable.of("round", 1, BuiltInFunctions::round));
        register(functions, BuiltInCallable.of("ceil", 1, BuiltInFunctions::ceil));
        register(functions, BuiltInCallable.of("floor", 1, BuiltInFunctions::floor));
        register(functions, BuiltInCallable.of("min", 1, Integer.MAX_VALUE, BuiltInFunctions::min));
        register(functions, BuiltInCallable.of("max", 1, Integer.MAX_VALUE, BuiltInFunctions::max));
        return Map.copyOf(functions);
    }

    /// Adds one callable to the registry.
    ///
    /// @param functions the mutable registry
    /// @param callable  the callable to add
    private static void register(
            LinkedHashMap<String, BuiltInCallable> functions,
            BuiltInCallable callable
    ) {
        functions.put(callable.name(), callable);
    }

    /// Implements `rgb`/`rgba`.
    private static SassValue rgb(List<SassValue> args) {
        if (args.size() == 3) {
            return SassColor.rgb(
                    channel(args.get(0)),
                    channel(args.get(1)),
                    channel(args.get(2)),
                    1.0,
                    null
            );
        }
        return SassColor.rgb(
                channel(args.get(0)),
                channel(args.get(1)),
                channel(args.get(2)),
                alpha(args.get(3)),
                null
        );
    }

    /// Reads one RGB channel number.
    private static double channel(SassValue value) {
        return value.assertNumber().assertNoUnits().value();
    }

    /// Reads one alpha channel number in `[0, 1]`.
    private static double alpha(SassValue value) {
        var number = value.assertNumber().assertNoUnits();
        var alpha = number.value();
        if (!(alpha >= 0.0 && alpha <= 1.0)) {
            throw new SassValueException("$alpha: Expected " + number + " to be within 0 and 1.");
        }
        return alpha;
    }

    /// Implements `quote`.
    private static SassValue quote(List<SassValue> args) {
        var string = args.get(0).assertString();
        return new SassString(string.text(), true);
    }

    /// Implements `unquote`.
    private static SassValue unquote(List<SassValue> args) {
        var string = args.get(0).assertString();
        return new SassString(string.text(), false);
    }

    /// Implements `length`.
    private static SassValue length(List<SassValue> args) {
        return SassNumber.of(args.get(0).lengthAsList(), null);
    }

    /// Implements `nth`.
    private static SassValue nth(List<SassValue> args) {
        var list = args.get(0);
        var index = list.sassIndexToListIndex(args.get(1), list.lengthAsList());
        return list.asList().get(index);
    }

    /// Implements `join`.
    private static SassValue join(List<SassValue> args) {
        var left = args.get(0);
        var right = args.get(1);
        var separator = args.size() == 3
                ? separatorArgument(args.get(2), left, right)
                : defaultSeparator(left, right);
        var contents = new ArrayList<SassValue>(left.lengthAsList() + right.lengthAsList());
        contents.addAll(left.asList());
        contents.addAll(right.asList());
        return new SassList(contents, separator, false);
    }

    /// Implements `append`.
    private static SassValue append(List<SassValue> args) {
        var list = args.get(0);
        var value = args.get(1);
        var separator = args.size() == 3
                ? separatorArgument(args.get(2), list, value)
                : list.separator() == ListSeparator.UNDECIDED
                ? ListSeparator.SPACE
                : list.separator();
        var contents = new ArrayList<SassValue>(list.lengthAsList() + 1);
        contents.addAll(list.asList());
        contents.add(value);
        return new SassList(contents, separator, list.hasBrackets());
    }

    /// Chooses a separator for `join` when none is supplied.
    private static ListSeparator defaultSeparator(SassValue left, SassValue right) {
        if (left.separator() != ListSeparator.UNDECIDED) {
            return left.separator();
        }
        if (right.separator() != ListSeparator.UNDECIDED) {
            return right.separator();
        }
        return ListSeparator.SPACE;
    }

    /// Parses a separator argument string.
    ///
    /// @param value the separator argument
    /// @param left  the left operand used when {@code auto} is selected
    /// @param right the right operand used when {@code auto} is selected
    private static ListSeparator separatorArgument(
            SassValue value,
            SassValue left,
            SassValue right
    ) {
        var text = value.assertString().text();
        return switch (text) {
            case "space" -> ListSeparator.SPACE;
            case "comma" -> ListSeparator.COMMA;
            case "slash" -> ListSeparator.SLASH;
            case "auto" -> defaultSeparator(left, right);
            default -> throw new SassValueException(
                    "$separator: Must be \"space\", \"comma\", \"slash\", or \"auto\"."
            );
        };
    }

    /// Implements `type-of`.
    private static SassValue typeOf(List<SassValue> args) {
        var value = args.get(0);
        String type;
        if (value instanceof SassNull) {
            type = "null";
        } else if (value instanceof SassBoolean) {
            type = "bool";
        } else if (value instanceof SassNumber) {
            type = "number";
        } else if (value instanceof SassString) {
            type = "string";
        } else if (value instanceof SassColor) {
            type = "color";
        } else if (value instanceof SassList) {
            type = "list";
        } else if (value instanceof SassMap) {
            type = "map";
        } else {
            throw new AssertionError("unexpected value type: " + value.getClass().getName());
        }
        return new SassString(type, false);
    }

    /// Implements `inspect`.
    private static SassValue inspect(List<SassValue> args) {
        return new SassString(args.get(0).toString(), false);
    }

    /// Implements `unit`.
    private static SassValue unit(List<SassValue> args) {
        return new SassString(args.get(0).assertNumber().unitString(), true);
    }

    /// Implements `comparable`.
    private static SassValue comparable(List<SassValue> args) {
        var left = args.get(0).assertNumber();
        var right = args.get(1).assertNumber();
        return SassBoolean.of(left.isComparableTo(right));
    }

    /// Implements `percentage`.
    private static SassValue percentage(List<SassValue> args) {
        var number = args.get(0).assertNumber().assertNoUnits();
        return SassNumber.of(number.value() * 100.0, "%");
    }

    /// Implements `abs`.
    private static SassValue abs(List<SassValue> args) {
        var number = args.get(0).assertNumber();
        return SassNumber.withUnits(
                Math.abs(number.value()),
                number.numeratorUnits(),
                number.denominatorUnits()
        );
    }

    /// Implements `round`.
    private static SassValue round(List<SassValue> args) {
        return mapNumber(args.get(0), Math::rint);
    }

    /// Implements `ceil`.
    private static SassValue ceil(List<SassValue> args) {
        return mapNumber(args.get(0), Math::ceil);
    }

    /// Implements `floor`.
    private static SassValue floor(List<SassValue> args) {
        return mapNumber(args.get(0), Math::floor);
    }

    /// Implements `min`.
    private static SassValue min(List<SassValue> args) {
        return extreme(args, true);
    }

    /// Implements `max`.
    private static SassValue max(List<SassValue> args) {
        return extreme(args, false);
    }

    /// Selects the minimum or maximum among number arguments.
    private static SassValue extreme(List<SassValue> args, boolean minimum) {
        if (args.isEmpty()) {
            throw new SassValueException("At least one argument required.");
        }
        var best = args.get(0).assertNumber();
        for (var index = 1; index < args.size(); index++) {
            var next = args.get(index).assertNumber();
            if (!best.isComparableTo(next)) {
                throw new SassValueException(best + " and " + next + " have incompatible units.");
            }
            var nextValue = next.valueInUnitsOf(best);
            if (minimum ? nextValue < best.value() : nextValue > best.value()) {
                best = next;
            }
        }
        return best;
    }

    /// Applies a magnitude transform while retaining units.
    private static SassNumber mapNumber(
            SassValue value,
            java.util.function.DoubleUnaryOperator operator
    ) {
        var number = value.assertNumber();
        return SassNumber.withUnits(
                operator.applyAsDouble(number.value()),
                number.numeratorUnits(),
                number.denominatorUnits()
        );
    }
}
