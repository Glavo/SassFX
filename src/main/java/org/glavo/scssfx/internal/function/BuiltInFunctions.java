// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.function;

import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.callable.BuiltInCallable.Param;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassArgumentList;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/// Registers built-in Sass functions for global and module namespaces.
@ApiStatus.Internal
@NotNullByDefault
public final class BuiltInFunctions {
    /// Generates unique IDs within the current process.
    private static final AtomicLong UNIQUE_ID = new AtomicLong(1);

    /// Prevents instantiation.
    private BuiltInFunctions() {
    }

    /// Returns the global built-in function table keyed by normalized name.
    ///
    /// @return an immutable name-to-callable map
    public static @Unmodifiable Map<String, BuiltInCallable> global() {
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        register(functions, BuiltInCallable.of(
                "rgb",
                List.of(
                        Param.required("red"),
                        Param.required("green"),
                        Param.required("blue"),
                        Param.optional("alpha", SassNumber.of(1, null))
                ),
                3,
                BuiltInFunctions::rgb
        ));
        register(functions, BuiltInCallable.of(
                "rgba",
                List.of(
                        Param.required("red"),
                        Param.required("green"),
                        Param.required("blue"),
                        Param.optional("alpha", SassNumber.of(1, null))
                ),
                3,
                BuiltInFunctions::rgb
        ));
        register(functions, BuiltInCallable.of("quote", List.of("string"), BuiltInFunctions::quote));
        register(functions, BuiltInCallable.of("unquote", List.of("string"), BuiltInFunctions::unquote));
        register(functions, BuiltInCallable.of("length", List.of("list"), BuiltInFunctions::length));
        register(functions, BuiltInCallable.of("nth", List.of("list", "n"), BuiltInFunctions::nth));
        register(functions, BuiltInCallable.of(
                "join",
                List.of(
                        Param.required("list1"),
                        Param.required("list2"),
                        Param.optional("separator", new SassString("auto", false))
                ),
                2,
                BuiltInFunctions::join
        ));
        register(functions, BuiltInCallable.of(
                "append",
                List.of(
                        Param.required("list"),
                        Param.required("val"),
                        Param.optional("separator", new SassString("auto", false))
                ),
                2,
                BuiltInFunctions::append
        ));
        register(functions, BuiltInCallable.of("type-of", List.of("value"), BuiltInFunctions::typeOf));
        register(functions, BuiltInCallable.of("inspect", List.of("value"), BuiltInFunctions::inspect));
        register(functions, BuiltInCallable.of("unit", List.of("number"), BuiltInFunctions::unit));
        register(functions, BuiltInCallable.of(
                "comparable",
                List.of("number1", "number2"),
                BuiltInFunctions::comparable
        ));
        register(functions, BuiltInCallable.of(
                "percentage",
                List.of("number"),
                BuiltInFunctions::percentage
        ));
        register(functions, BuiltInCallable.of("abs", List.of("number"), BuiltInFunctions::abs));
        register(functions, BuiltInCallable.of("round", List.of("number"), BuiltInFunctions::round));
        register(functions, BuiltInCallable.of("ceil", List.of("number"), BuiltInFunctions::ceil));
        register(functions, BuiltInCallable.of("floor", List.of("number"), BuiltInFunctions::floor));
        register(functions, BuiltInCallable.withRest(
                "min",
                List.of(),
                "numbers",
                BuiltInFunctions::min
        ));
        register(functions, BuiltInCallable.withRest(
                "max",
                List.of(),
                "numbers",
                BuiltInFunctions::max
        ));

        register(functions, BuiltInCallable.of("map-get", List.of("map", "key"), BuiltInFunctions::mapGet));
        register(functions, BuiltInCallable.of("map-keys", List.of("map"), BuiltInFunctions::mapKeys));
        register(functions, BuiltInCallable.of("map-values", List.of("map"), BuiltInFunctions::mapValues));
        register(functions, BuiltInCallable.of(
                "map-merge",
                List.of("map1", "map2"),
                BuiltInFunctions::mapMerge
        ));
        register(functions, BuiltInCallable.of(
                "map-has-key",
                List.of("map", "key"),
                BuiltInFunctions::mapHasKey
        ));

        register(functions, BuiltInCallable.of(
                "str-length",
                List.of("string"),
                BuiltInFunctions::strLength
        ));
        register(functions, BuiltInCallable.of(
                "str-index",
                List.of("string", "substring"),
                BuiltInFunctions::strIndex
        ));
        register(functions, BuiltInCallable.of(
                "str-slice",
                List.of(
                        Param.required("string"),
                        Param.required("start-at"),
                        Param.optional("end-at", SassNumber.of(-1, null))
                ),
                2,
                BuiltInFunctions::strSlice
        ));
        register(functions, BuiltInCallable.of(
                "to-upper-case",
                List.of("string"),
                BuiltInFunctions::toUpperCase
        ));
        register(functions, BuiltInCallable.of(
                "to-lower-case",
                List.of("string"),
                BuiltInFunctions::toLowerCase
        ));
        register(functions, BuiltInCallable.of("unique-id", List.of(), BuiltInFunctions::uniqueId));

        register(functions, BuiltInCallable.of(
                "list-separator",
                List.of("list"),
                BuiltInFunctions::listSeparator
        ));
        register(functions, BuiltInCallable.of(
                "is-bracketed",
                List.of("list"),
                BuiltInFunctions::isBracketed
        ));
        register(functions, BuiltInCallable.of(
                "index",
                List.of("list", "value"),
                BuiltInFunctions::index
        ));
        register(functions, BuiltInCallable.of(
                "set-nth",
                List.of("list", "n", "value"),
                BuiltInFunctions::setNth
        ));
        register(functions, BuiltInCallable.withRest(
                "zip",
                List.of(),
                "lists",
                BuiltInFunctions::zip
        ));

        register(functions, BuiltInCallable.of("red", List.of("color"), BuiltInFunctions::red));
        register(functions, BuiltInCallable.of("green", List.of("color"), BuiltInFunctions::green));
        register(functions, BuiltInCallable.of("blue", List.of("color"), BuiltInFunctions::blue));
        register(functions, BuiltInCallable.of("alpha", List.of("color"), BuiltInFunctions::alphaChannel));
        register(functions, BuiltInCallable.of("opacity", List.of("color"), BuiltInFunctions::opacity));
        return Map.copyOf(functions);
    }

    /// Returns the functions exported by the {@code sass:math} module.
    ///
    /// The module reuses global implementations where their behavior and
    /// parameter contract match the module API, while assigning module-local
    /// callable names for lookup and diagnostics.
    ///
    /// @return an immutable math function table
    public static @Unmodifiable Map<String, BuiltInCallable> mathModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        moduleFunction(functions, global, "abs", "abs");
        moduleFunction(functions, global, "ceil", "ceil");
        moduleFunction(functions, global, "floor", "floor");
        moduleFunction(functions, global, "max", "max");
        moduleFunction(functions, global, "min", "min");
        moduleFunction(functions, global, "percentage", "percentage");
        moduleFunction(functions, global, "round", "round");
        moduleFunction(functions, global, "unit", "unit");
        moduleFunction(functions, global, "comparable", "compatible");
        register(functions, BuiltInCallable.of(
                "div",
                List.of("number1", "number2"),
                BuiltInFunctions::div
        ));
        register(functions, BuiltInCallable.of(
                "is-unitless",
                List.of("number"),
                BuiltInFunctions::isUnitless
        ));
        return Map.copyOf(functions);
    }

    /// Returns the functions exported by the {@code sass:list} module.
    ///
    /// @return an immutable list function table
    public static @Unmodifiable Map<String, BuiltInCallable> listModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        moduleFunction(functions, global, "append", "append");
        moduleFunction(functions, global, "index", "index");
        moduleFunction(functions, global, "is-bracketed", "is-bracketed");
        moduleFunction(functions, global, "join", "join");
        moduleFunction(functions, global, "length", "length");
        moduleFunction(functions, global, "nth", "nth");
        moduleFunction(functions, global, "list-separator", "separator");
        moduleFunction(functions, global, "set-nth", "set-nth");
        moduleFunction(functions, global, "zip", "zip");
        return Map.copyOf(functions);
    }

    /// Returns the functions exported by the {@code sass:map} module.
    ///
    /// @return an immutable map function table
    public static @Unmodifiable Map<String, BuiltInCallable> mapModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        moduleFunction(functions, global, "map-get", "get");
        moduleFunction(functions, global, "map-has-key", "has-key");
        moduleFunction(functions, global, "map-keys", "keys");
        moduleFunction(functions, global, "map-merge", "merge");
        moduleFunction(functions, global, "map-values", "values");
        return Map.copyOf(functions);
    }

    /// Adds a global callable to a module map under its module-local name.
    ///
    /// @param destination the module function map
    /// @param global the global function table
    /// @param globalName the source global function name
    /// @param moduleName the exported module function name
    private static void moduleFunction(
            LinkedHashMap<String, BuiltInCallable> destination,
            Map<String, BuiltInCallable> global,
            String globalName,
            String moduleName
    ) {
        @Nullable BuiltInCallable callable = global.get(globalName);
        if (callable == null) {
            throw new AssertionError("missing registered global function: " + globalName);
        }
        destination.put(moduleName, callable.withName(moduleName));
    }
    private static void register(
            LinkedHashMap<String, BuiltInCallable> functions,
            BuiltInCallable callable
    ) {
        functions.put(callable.name(), callable);
    }

    private static SassValue rgb(List<SassValue> args) {
        return SassColor.rgb(
                channel(args.get(0)),
                channel(args.get(1)),
                channel(args.get(2)),
                args.size() > 3 ? alpha(args.get(3)) : 1.0,
                null
        );
    }

    private static double channel(SassValue value) {
        return value.assertNumber().assertNoUnits().value();
    }

    private static double alpha(SassValue value) {
        var number = value.assertNumber().assertNoUnits();
        var alpha = number.value();
        if (!(alpha >= 0.0 && alpha <= 1.0)) {
            throw new SassValueException("$alpha: Expected " + number + " to be within 0 and 1.");
        }
        return alpha;
    }

    private static SassValue quote(List<SassValue> args) {
        var string = args.get(0).assertString();
        return new SassString(string.text(), true);
    }

    private static SassValue unquote(List<SassValue> args) {
        var string = args.get(0).assertString();
        return new SassString(string.text(), false);
    }

    private static SassValue length(List<SassValue> args) {
        return SassNumber.of(args.get(0).lengthAsList(), null);
    }

    private static SassValue nth(List<SassValue> args) {
        var list = args.get(0);
        var index = list.sassIndexToListIndex(args.get(1), list.lengthAsList());
        return list.asList().get(index);
    }

    private static SassValue join(List<SassValue> args) {
        var left = args.get(0);
        var right = args.get(1);
        var separator = separatorArgument(args.get(2), left, right);
        var contents = new ArrayList<SassValue>(left.lengthAsList() + right.lengthAsList());
        contents.addAll(left.asList());
        contents.addAll(right.asList());
        return new SassList(contents, separator, false);
    }

    private static SassValue append(List<SassValue> args) {
        var list = args.get(0);
        var value = args.get(1);
        var separator = args.get(2) instanceof SassString string && string.text().equals("auto")
                ? (list.separator() == ListSeparator.UNDECIDED
                ? ListSeparator.SPACE
                : list.separator())
                : separatorArgument(args.get(2), list, value);
        var contents = new ArrayList<SassValue>(list.lengthAsList() + 1);
        contents.addAll(list.asList());
        contents.add(value);
        return new SassList(contents, separator, list.hasBrackets());
    }

    private static ListSeparator defaultSeparator(SassValue left, SassValue right) {
        if (left.separator() != ListSeparator.UNDECIDED) {
            return left.separator();
        }
        if (right.separator() != ListSeparator.UNDECIDED) {
            return right.separator();
        }
        return ListSeparator.SPACE;
    }

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
        } else if (value instanceof SassMap) {
            type = "map";
        } else if (value instanceof SassList || value instanceof SassArgumentList) {
            type = "list";
        } else {
            throw new AssertionError("unexpected value type: " + value.getClass().getName());
        }
        return new SassString(type, false);
    }

    private static SassValue inspect(List<SassValue> args) {
        return new SassString(args.get(0).toString(), false);
    }

    private static SassValue unit(List<SassValue> args) {
        return new SassString(args.get(0).assertNumber().unitString(), true);
    }

    private static SassValue comparable(List<SassValue> args) {
        return SassBoolean.of(args.get(0).assertNumber().isComparableTo(args.get(1).assertNumber()));
    }

    private static SassValue percentage(List<SassValue> args) {
        return SassNumber.of(args.get(0).assertNumber().assertNoUnits().value() * 100.0, "%");
    }

    /// Divides two numbers for the {@code sass:math} module.
    ///
    /// @param args the bound number arguments
    /// @return the unit-aware quotient
    private static SassValue div(List<SassValue> args) {
        return args.get(0).assertNumber().dividedBy(args.get(1).assertNumber());
    }

    /// Reports whether one number has no numerator or denominator units.
    ///
    /// @param args the bound number argument
    /// @return a Sass boolean describing unitlessness
    private static SassValue isUnitless(List<SassValue> args) {
        return SassBoolean.of(args.get(0).assertNumber().isUnitless());
    }
    private static SassValue abs(List<SassValue> args) {
        var number = args.get(0).assertNumber();
        return SassNumber.withUnits(
                Math.abs(number.value()),
                number.numeratorUnits(),
                number.denominatorUnits()
        );
    }

    private static SassValue round(List<SassValue> args) {
        return mapNumber(args.get(0), Math::rint);
    }

    private static SassValue ceil(List<SassValue> args) {
        return mapNumber(args.get(0), Math::ceil);
    }

    private static SassValue floor(List<SassValue> args) {
        return mapNumber(args.get(0), Math::floor);
    }

    private static SassValue min(List<SassValue> args) {
        return extreme(restValues(args), true);
    }

    private static SassValue max(List<SassValue> args) {
        return extreme(restValues(args), false);
    }

    private static List<SassValue> restValues(List<SassValue> args) {
        if (args.size() == 1 && args.get(0) instanceof SassArgumentList list) {
            return list.asList();
        }
        return args;
    }

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

    private static SassValue mapGet(List<SassValue> args) {
        var map = args.get(0).assertMap();
        @Nullable SassValue value = map.contents().get(args.get(1));
        return value == null ? SassNull.NULL : value;
    }

    private static SassValue mapKeys(List<SassValue> args) {
        return new SassList(
                List.copyOf(args.get(0).assertMap().contents().keySet()),
                ListSeparator.COMMA,
                false
        );
    }

    private static SassValue mapValues(List<SassValue> args) {
        return new SassList(
                List.copyOf(args.get(0).assertMap().contents().values()),
                ListSeparator.COMMA,
                false
        );
    }

    private static SassValue mapMerge(List<SassValue> args) {
        var result = new LinkedHashMap<>(args.get(0).assertMap().contents());
        result.putAll(args.get(1).assertMap().contents());
        return new SassMap(result);
    }

    private static SassValue mapHasKey(List<SassValue> args) {
        return SassBoolean.of(args.get(0).assertMap().contents().containsKey(args.get(1)));
    }

    private static SassValue strLength(List<SassValue> args) {
        var text = args.get(0).assertString().text();
        return SassNumber.of(text.codePointCount(0, text.length()), null);
    }

    private static SassValue strIndex(List<SassValue> args) {
        var text = args.get(0).assertString().text();
        var substring = args.get(1).assertString().text();
        var index = text.indexOf(substring);
        return index < 0
                ? SassNull.NULL
                : SassNumber.of(text.codePointCount(0, index) + 1, null);
    }

    private static SassValue strSlice(List<SassValue> args) {
        var string = args.get(0).assertString();
        var text = string.text();
        var length = text.codePointCount(0, text.length());
        var start = normalizeStringIndex(args.get(1).assertNumber().assertInt(), length);
        var end = normalizeStringIndex(args.get(2).assertNumber().assertInt(), length);
        if (end < start || start > length || end < 1) {
            return new SassString("", string.hasQuotes());
        }
        var startOffset = text.offsetByCodePoints(0, start - 1);
        var endOffset = text.offsetByCodePoints(0, end);
        return new SassString(text.substring(startOffset, endOffset), string.hasQuotes());
    }

    private static int normalizeStringIndex(int index, int length) {
        if (index == 0) {
            return 0;
        }
        if (index > 0) {
            return Math.min(index, length);
        }
        return Math.max(length + index + 1, 0);
    }

    private static SassValue toUpperCase(List<SassValue> args) {
        var string = args.get(0).assertString();
        return new SassString(asciiCase(string.text(), true), string.hasQuotes());
    }

    private static SassValue toLowerCase(List<SassValue> args) {
        var string = args.get(0).assertString();
        return new SassString(asciiCase(string.text(), false), string.hasQuotes());
    }

    private static String asciiCase(String text, boolean upper) {
        var result = new StringBuilder(text.length());
        for (var index = 0; index < text.length(); ) {
            var codePoint = text.codePointAt(index);
            if (codePoint >= 'a' && codePoint <= 'z' && upper) {
                result.append((char) (codePoint - 32));
            } else if (codePoint >= 'A' && codePoint <= 'Z' && !upper) {
                result.append((char) (codePoint + 32));
            } else {
                result.appendCodePoint(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static SassValue uniqueId(List<SassValue> args) {
        var value = UNIQUE_ID.getAndIncrement();
        return new SassString("u" + Long.toString(value, 36), false);
    }

    private static SassValue listSeparator(List<SassValue> args) {
        var separator = args.get(0).separator();
        var text = separator == ListSeparator.COMMA
                ? "comma"
                : separator == ListSeparator.SLASH ? "slash" : "space";
        return new SassString(text, false);
    }

    private static SassValue isBracketed(List<SassValue> args) {
        return SassBoolean.of(args.get(0).hasBrackets());
    }

    private static SassValue index(List<SassValue> args) {
        var list = args.get(0).asList();
        var value = args.get(1);
        for (var index = 0; index < list.size(); index++) {
            if (list.get(index).equals(value)) {
                return SassNumber.of(index + 1, null);
            }
        }
        return SassNull.NULL;
    }

    private static SassValue setNth(List<SassValue> args) {
        var list = args.get(0);
        var contents = new ArrayList<>(list.asList());
        var index = list.sassIndexToListIndex(args.get(1), contents.size());
        contents.set(index, args.get(2));
        var separator = list.separator() == ListSeparator.UNDECIDED
                ? ListSeparator.SPACE
                : list.separator();
        return new SassList(contents, separator, list.hasBrackets());
    }

    private static SassValue zip(List<SassValue> args) {
        var lists = restValues(args);
        if (lists.isEmpty()) {
            return new SassList(List.of(), ListSeparator.COMMA, false);
        }
        var min = lists.stream().mapToInt(SassValue::lengthAsList).min().orElse(0);
        var result = new ArrayList<SassValue>(min);
        for (var index = 0; index < min; index++) {
            var row = new ArrayList<SassValue>(lists.size());
            for (var list : lists) {
                row.add(list.asList().get(index));
            }
            result.add(new SassList(row, ListSeparator.SPACE, false));
        }
        return new SassList(result, ListSeparator.COMMA, false);
    }

    private static SassValue red(List<SassValue> args) {
        return SassNumber.of(args.get(0).assertColor().red(), null);
    }

    private static SassValue green(List<SassValue> args) {
        return SassNumber.of(args.get(0).assertColor().green(), null);
    }

    private static SassValue blue(List<SassValue> args) {
        return SassNumber.of(args.get(0).assertColor().blue(), null);
    }

    private static SassValue alphaChannel(List<SassValue> args) {
        return SassNumber.of(args.get(0).assertColor().alpha(), null);
    }

    private static SassValue opacity(List<SassValue> args) {
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            return new SassString("opacity(" + number.toCssString() + ")", false);
        }
        return SassNumber.of(value.assertColor().alpha(), null);
    }
}
