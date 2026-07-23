// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.function;

import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.callable.BuiltInCallable.Param;
import org.glavo.scssfx.internal.callable.UserDefinedCallable;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassArgumentList;
import org.glavo.scssfx.internal.value.SassBoolean;
import org.glavo.scssfx.internal.value.SassColor;
import org.glavo.scssfx.internal.value.SassFunction;
import org.glavo.scssfx.internal.value.SassMixin;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/// Registers built-in Sass functions for global and module namespaces.
@ApiStatus.Internal
@NotNullByDefault
public final class BuiltInFunctions {
    /// Generates unique IDs within the current process.
    private static final AtomicLong UNIQUE_ID = new AtomicLong(1);

    /// Supplies values for {@code math.random()} within the current process.
    private static final Random RANDOM = new Random();

    /// Contains the radians unit used by trigonometric angle coercion.
    private static final @Unmodifiable List<String> RADIANS = List.of("rad");

    /// Contains the degrees unit returned by inverse trigonometric functions.
    private static final @Unmodifiable List<String> DEGREES = List.of("deg");

    /// Contains the stable deprecation identifier for string-based {@code meta.call()}.
    private static final String CALL_STRING_CODE = "call-string";

    /// Contains the caller guidance for string-based {@code meta.call()}.
    private static final String CALL_STRING_DEPRECATION_MESSAGE =
            "Passing a string to call() is deprecated and will be illegal in Dart Sass 2.0.0.\n\n"
                    + "Recommendation: call(get-function($function))";

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
        register(functions, BuiltInCallable.of(
                "random",
                List.of(Param.optional("limit", SassNull.NULL)),
                0,
                BuiltInFunctions::random
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
        return freeze(functions);
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
        moduleFunction(functions, global, "random", "random");
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
        register(functions, BuiltInCallable.of(
                "clamp",
                List.of("min", "number", "max"),
                BuiltInFunctions::clamp
        ));
        register(functions, BuiltInCallable.withRest(
                "hypot",
                List.of(),
                "numbers",
                BuiltInFunctions::hypot
        ));
        register(functions, BuiltInCallable.of(
                "log",
                List.of(
                        Param.required("number"),
                        Param.optional("base", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::log
        ));
        register(functions, BuiltInCallable.of(
                "pow",
                List.of("base", "exponent"),
                BuiltInFunctions::pow
        ));
        register(functions, BuiltInCallable.of("sqrt", List.of("number"), BuiltInFunctions::sqrt));
        register(functions, BuiltInCallable.of("sin", List.of("number"), BuiltInFunctions::sin));
        register(functions, BuiltInCallable.of("cos", List.of("number"), BuiltInFunctions::cos));
        register(functions, BuiltInCallable.of("tan", List.of("number"), BuiltInFunctions::tan));
        register(functions, BuiltInCallable.of("asin", List.of("number"), BuiltInFunctions::asin));
        register(functions, BuiltInCallable.of("acos", List.of("number"), BuiltInFunctions::acos));
        register(functions, BuiltInCallable.of("atan", List.of("number"), BuiltInFunctions::atan));
        register(functions, BuiltInCallable.of(
                "atan2",
                List.of("y", "x"),
                BuiltInFunctions::atan2
        ));
        return freeze(functions);
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
        moduleFunction(functions, global, "length", "length");
        moduleFunction(functions, global, "nth", "nth");
        moduleFunction(functions, global, "list-separator", "separator");
        moduleFunction(functions, global, "set-nth", "set-nth");
        moduleFunction(functions, global, "zip", "zip");
        register(functions, BuiltInCallable.of(
                "join",
                List.of(
                        Param.required("list1"),
                        Param.required("list2"),
                        Param.optional("separator", new SassString("auto", false)),
                        Param.optional("bracketed", new SassString("auto", false))
                ),
                2,
                BuiltInFunctions::moduleJoin
        ));
        register(functions, BuiltInCallable.withRest(
                "slash",
                List.of(),
                "elements",
                BuiltInFunctions::slash
        ));
        return freeze(functions);
    }

    /// Returns the functions exported by the {@code sass:map} module.
    ///
    /// @return an immutable map function table
    public static @Unmodifiable Map<String, BuiltInCallable> mapModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        moduleFunction(functions, global, "map-keys", "keys");
        moduleFunction(functions, global, "map-merge", "merge");
        moduleFunction(functions, global, "map-values", "values");
        register(functions, BuiltInCallable.withRest(
                "get",
                List.of("map", "key"),
                "keys",
                BuiltInFunctions::moduleMapGet
        ));
        register(functions, BuiltInCallable.of(
                "set",
                List.of("map", "key", "value"),
                BuiltInFunctions::mapSet
        ));
        register(functions, BuiltInCallable.withRest(
                "remove",
                List.of("map"),
                "keys",
                BuiltInFunctions::mapRemove
        ));
        register(functions, BuiltInCallable.withRest(
                "has-key",
                List.of("map", "key"),
                "keys",
                BuiltInFunctions::moduleMapHasKey
        ));
        register(functions, BuiltInCallable.of(
                "deep-merge",
                List.of("map1", "map2"),
                BuiltInFunctions::mapDeepMerge
        ));
        register(functions, BuiltInCallable.withRest(
                "deep-remove",
                List.of("map", "key"),
                "keys",
                BuiltInFunctions::mapDeepRemove
        ));
        return freeze(functions);
    }

    /// Returns the functions exported by the {@code sass:string} module.
    ///
    /// @return an immutable string function table
    public static @Unmodifiable Map<String, BuiltInCallable> stringModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        moduleFunction(functions, global, "quote", "quote");
        moduleFunction(functions, global, "unquote", "unquote");
        moduleFunction(functions, global, "str-length", "length");
        moduleFunction(functions, global, "str-index", "index");
        moduleFunction(functions, global, "str-slice", "slice");
        moduleFunction(functions, global, "to-upper-case", "to-upper-case");
        moduleFunction(functions, global, "to-lower-case", "to-lower-case");
        moduleFunction(functions, global, "unique-id", "unique-id");
        register(functions, BuiltInCallable.of(
                "insert",
                List.of("string", "insert", "index"),
                BuiltInFunctions::strInsert
        ));
        register(functions, BuiltInCallable.of(
                "split",
                List.of(
                        Param.required("string"),
                        Param.required("separator"),
                        Param.optional("limit", SassNull.NULL)
                ),
                2,
                BuiltInFunctions::strSplit
        ));
        return freeze(functions);
    }

    /// Returns the legacy RGB functions exported by the {@code sass:color} module.
    ///
    /// The current color value model represents only legacy RGB colors.
    /// Color-valued APIs that accept Color 4 interpolation methods or output
    /// spaces expose their parameter for signature compatibility, but reject
    /// non-null values rather than silently applying an incorrect color-space
    /// algorithm. Compatible plain-CSS number filters retain native CSS
    /// invocation behavior.
    ///
    /// @return an immutable legacy color function table
    public static @Unmodifiable Map<String, BuiltInCallable> colorModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        moduleFunction(functions, global, "red", "red");
        moduleFunction(functions, global, "green", "green");
        moduleFunction(functions, global, "blue", "blue");
        moduleFunction(functions, global, "alpha", "alpha");
        moduleFunction(functions, global, "opacity", "opacity");
        register(functions, BuiltInCallable.of(
                "mix",
                List.of(
                        Param.required("color1"),
                        Param.required("color2"),
                        Param.optional("weight", SassNumber.of(50, "%")),
                        Param.optional("method", SassNull.NULL)
                ),
                2,
                BuiltInFunctions::colorMix
        ));
        register(functions, BuiltInCallable.of(
                "invert",
                List.of(
                        Param.required("color"),
                        Param.optional("weight", SassNumber.of(100, "%")),
                        Param.optional("space", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::colorInvert
        ));
        register(functions, BuiltInCallable.of("hue", List.of("color"), BuiltInFunctions::colorHue));
        register(functions, BuiltInCallable.of(
                "saturation",
                List.of("color"),
                BuiltInFunctions::colorSaturation
        ));
        register(functions, BuiltInCallable.of(
                "lightness",
                List.of("color"),
                BuiltInFunctions::colorLightness
        ));
        register(functions, BuiltInCallable.of(
                "grayscale",
                List.of("color"),
                BuiltInFunctions::colorGrayscale
        ));
        register(functions, BuiltInCallable.of(
                "complement",
                List.of(
                        Param.required("color"),
                        Param.optional("space", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::colorComplement
        ));
        register(functions, BuiltInCallable.of(
                "same",
                List.of("color1", "color2"),
                BuiltInFunctions::colorSame
        ));
        register(functions, BuiltInCallable.withRest(
                "adjust",
                List.of("color"),
                "kwargs",
                BuiltInFunctions::colorAdjust
        ));
        register(functions, BuiltInCallable.withRest(
                "scale",
                List.of("color"),
                "kwargs",
                BuiltInFunctions::colorScale
        ));
        register(functions, BuiltInCallable.withRest(
                "change",
                List.of("color"),
                "kwargs",
                BuiltInFunctions::colorChange
        ));
        return freeze(functions);
    }

    /// Returns the introspection functions exported by {@code sass:meta}.
    ///
    /// Functions that inspect the active lexical or module environment receive
    /// a limited invocation context. First-class function and mixin references
    /// use controlled evaluator bridges; stylesheet loading remains outside this
    /// context-aware surface.
    ///
    /// @return an immutable meta function table
    public static @Unmodifiable Map<String, BuiltInCallable> metaModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        moduleFunction(functions, global, "inspect", "inspect");
        moduleFunction(functions, global, "type-of", "type-of");
        register(functions, BuiltInCallable.of(
                "keywords",
                List.of("args"),
                BuiltInFunctions::keywords
        ));
        register(functions, BuiltInCallable.contextual(
                "content-exists",
                List.of(),
                0,
                BuiltInFunctions::metaContentExists
        ));
        register(functions, BuiltInCallable.contextual(
                "variable-exists",
                List.of(Param.required("name")),
                1,
                BuiltInFunctions::metaVariableExists
        ));
        register(functions, BuiltInCallable.contextual(
                "global-variable-exists",
                List.of(
                        Param.required("name"),
                        Param.optional("module", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::metaGlobalVariableExists
        ));
        register(functions, BuiltInCallable.contextual(
                "function-exists",
                List.of(
                        Param.required("name"),
                        Param.optional("module", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::metaFunctionExists
        ));
        register(functions, BuiltInCallable.contextual(
                "mixin-exists",
                List.of(
                        Param.required("name"),
                        Param.optional("module", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::metaMixinExists
        ));
        register(functions, BuiltInCallable.contextual(
                "module-variables",
                List.of(Param.required("module")),
                1,
                BuiltInFunctions::metaModuleVariables
        ));
        register(functions, BuiltInCallable.contextual(
                "get-function",
                List.of(
                        Param.required("name"),
                        Param.optional("css", SassBoolean.FALSE),
                        Param.optional("module", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::metaGetFunction
        ));
        register(functions, BuiltInCallable.contextual(
                "module-functions",
                List.of(Param.required("module")),
                1,
                BuiltInFunctions::metaModuleFunctions
        ));
        register(functions, BuiltInCallable.contextual(
                "get-mixin",
                List.of(
                        Param.required("name"),
                        Param.optional("module", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::metaGetMixin
        ));
        register(functions, BuiltInCallable.contextual(
                "module-mixins",
                List.of(Param.required("module")),
                1,
                BuiltInFunctions::metaModuleMixins
        ));
        register(functions, BuiltInCallable.contextual(
                "accepts-content",
                List.of(Param.required("mixin")),
                1,
                BuiltInFunctions::metaAcceptsContent
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "call",
                List.of(Param.required("function")),
                "args",
                1,
                BuiltInFunctions::metaCall
        ));
        return freeze(functions);
    }

    /// Returns the mixins exported by {@code sass:meta}.
    ///
    /// @return an immutable meta mixin table
    public static @Unmodifiable Map<String, BuiltInCallable> metaMixins() {
        var mixins = new LinkedHashMap<String, BuiltInCallable>();
        register(mixins, BuiltInCallable.contextualMixinWithRest(
                "apply",
                List.of(Param.required("mixin")),
                "args",
                1,
                BuiltInFunctions::metaApply
        ));
        return freeze(mixins);
    }

    /// Returns the selector functions exported by {@code sass:selector}.
    ///
    /// @return an immutable selector function table
    public static @Unmodifiable Map<String, BuiltInCallable> selectorModule() {
        return SelectorFunctions.module();
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

    /// Returns an insertion-ordered immutable view of a callable table.
    ///
    /// @param functions the mutable table to snapshot
    /// @return an immutable insertion-ordered callable table
    private static @Unmodifiable Map<String, BuiltInCallable> freeze(
            LinkedHashMap<String, BuiltInCallable> functions
    ) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(functions));
    }

    /// Registers one callable under its normalized name.
    ///
    /// @param functions the mutable function table
    /// @param callable the callable to register
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

    /// Joins two lists using the module-only bracket control parameter.
    ///
    /// @param args the two lists, separator selector, and bracket selector
    /// @return a list containing both input list views
    private static SassValue moduleJoin(List<SassValue> args) {
        var left = args.get(0);
        var right = args.get(1);
        var separator = separatorArgument(args.get(2), left, right);
        var bracketedArgument = args.get(3);
        var bracketed = bracketedArgument instanceof SassString string
                && string.text().equals("auto")
                ? left.hasBrackets()
                : bracketedArgument.isTruthy();
        var contents = new ArrayList<SassValue>(left.lengthAsList() + right.lengthAsList());
        contents.addAll(left.asList());
        contents.addAll(right.asList());
        return new SassList(contents, separator, bracketed);
    }

    /// Creates a slash-separated list from at least two rest arguments.
    ///
    /// @param args the rest argument list
    /// @return a slash-separated unbracketed list
    /// @throws SassValueException if fewer than two elements are supplied
    private static SassValue slash(List<SassValue> args) {
        var elements = restValues(args);
        if (elements.size() < 2) {
            throw new SassValueException("At least two elements are required.");
        }
        return new SassList(elements, ListSeparator.SLASH, false);
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

    /// Returns the Sass type name for one value.
    ///
    /// @param args the one bound value argument
    /// @return an unquoted Sass type name
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
        } else if (value instanceof SassFunction) {
            type = "function";
        } else if (value instanceof SassMixin) {
            type = "mixin";
        } else if (value instanceof SassMap) {
            type = "map";
        } else if (value instanceof SassArgumentList) {
            type = "arglist";
        } else if (value instanceof SassList) {
            type = "list";
        } else {
            throw new AssertionError("unexpected value type: " + value.getClass().getName());
        }
        return new SassString(type, false);
    }

    /// Returns the inspect serialization for one value.
    ///
    /// @param args the one bound value argument
    /// @return an unquoted string containing the value representation
    private static SassValue inspect(List<SassValue> args) {
        return new SassString(args.get(0).toString(), false);
    }

    /// Returns whether the current mixin invocation received a content block.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the empty bound argument list
    /// @return whether a direct content block is available
    /// @throws SassValueException outside mixin execution
    private static SassValue metaContentExists(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        return SassBoolean.of(context.contentExists());
    }

    /// Returns whether a name resolves to a visible lexical variable.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the one bound name argument
    /// @return whether the named variable is visible, including Sass null bindings
    /// @throws SassValueException if {@code $name} is not a string
    private static SassValue metaVariableExists(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        return SassBoolean.of(context.variableExists(metaName(args.get(0), "name")));
    }

    /// Returns whether a root or namespaced-module variable exists.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the name and optional module arguments
    /// @return whether the requested global binding exists, including Sass null bindings
    /// @throws SassValueException if a string argument is invalid or the module is absent
    private static SassValue metaGlobalVariableExists(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        return SassBoolean.of(context.globalVariableExists(
                metaName(args.get(0), "name"),
                metaModuleName(args.get(1))
        ));
    }

    /// Returns whether a function resolves in the active environment or built-ins.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the name and optional module arguments
    /// @return whether the requested function exists
    /// @throws SassValueException if a string argument is invalid or the module is absent
    private static SassValue metaFunctionExists(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        return SassBoolean.of(context.functionExists(
                metaName(args.get(0), "name"),
                metaModuleName(args.get(1))
        ));
    }

    /// Returns whether a mixin resolves in the active environment.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the name and optional module arguments
    /// @return whether the requested mixin exists
    /// @throws SassValueException if a string argument is invalid or the module is absent
    private static SassValue metaMixinExists(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        return SassBoolean.of(context.mixinExists(
                metaName(args.get(0), "name"),
                metaModuleName(args.get(1))
        ));
    }

    /// Returns one named module's public variables as a Sass map.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the one bound module argument
    /// @return a map with quoted public variable-name keys and their values
    /// @throws SassValueException if {@code $module} is not a string or is not loaded by name
    private static SassValue metaModuleVariables(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        var values = new LinkedHashMap<SassValue, SassValue>();
        for (var entry : context.moduleVariables(metaStringArgument(args.get(0), "module")).entrySet()) {
            values.put(new SassString(entry.getKey(), true), entry.getValue());
        }
        return new SassMap(values);
    }

    /// Returns a first-class function reference selected by name.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the name, CSS selector, and optional module arguments
    /// @return the resolved Sass or plain-CSS function reference
    /// @throws SassValueException if an argument is invalid, the requested module is absent, or no Sass function matches
    private static SassValue metaGetFunction(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        var nameArgument = args.get(0);
        var name = metaStringArgument(nameArgument, "name");
        var css = args.get(1).isTruthy();
        @Nullable String module = metaModuleName(args.get(2));
        if (css) {
            if (module != null) {
                throw new SassValueException("$css and $module may not both be passed at once.");
            }
            return context.plainCssFunction(name);
        }
        @Nullable SassFunction function = context.function(metaName(nameArgument, "name"), module);
        if (function == null) {
            throw new SassValueException("Function not found: " + nameArgument + ".");
        }
        return function;
    }

    /// Returns public functions from an explicitly named module as a Sass map.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the one bound module argument
    /// @return quoted public function-name keys mapped to function references
    /// @throws SassValueException if {@code $module} is not a string or is not loaded by name
    private static SassValue metaModuleFunctions(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        var values = new LinkedHashMap<SassValue, SassValue>();
        for (var entry : context.moduleFunctions(metaStringArgument(args.get(0), "module")).entrySet()) {
            values.put(new SassString(entry.getKey(), true), entry.getValue());
        }
        return new SassMap(values);
    }

    /// Returns a first-class mixin reference selected by name.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the name and optional module arguments
    /// @return the resolved Sass mixin reference
    /// @throws SassValueException if an argument is invalid, the requested module is absent, or no Sass mixin matches
    private static SassValue metaGetMixin(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        var nameArgument = args.get(0);
        @Nullable SassMixin mixin = context.mixin(
                metaName(nameArgument, "name"),
                metaModuleName(args.get(1))
        );
        if (mixin == null) {
            throw new SassValueException("Mixin not found: " + nameArgument + ".");
        }
        return mixin;
    }

    /// Returns public mixins from an explicitly named module as a Sass map.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the one bound module argument
    /// @return quoted public mixin-name keys mapped to mixin references
    /// @throws SassValueException if {@code $module} is not a string or is not loaded by name
    private static SassValue metaModuleMixins(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        var values = new LinkedHashMap<SassValue, SassValue>();
        for (var entry : context.moduleMixins(metaStringArgument(args.get(0), "module")).entrySet()) {
            values.put(new SassString(entry.getKey(), true), entry.getValue());
        }
        return new SassMap(values);
    }

    /// Returns whether a mixin reference accepts a direct content block.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the one bound mixin argument
    /// @return whether the referenced mixin accepts content
    /// @throws SassValueException if {@code $mixin} is not a mixin reference
    private static SassValue metaAcceptsContent(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        var target = args.get(0);
        if (!(target instanceof SassMixin mixin)) {
            throw new SassValueException("$mixin: " + target + " is not a mixin reference.");
        }
        var callable = mixin.callable();
        return SassBoolean.of(
                callable instanceof UserDefinedCallable userMixin && userMixin.acceptsContent()
                        || callable instanceof BuiltInCallable builtIn && builtIn.acceptsContent()
        );
    }

    /// Includes a first-class mixin reference with a preserved argument list.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the mixin target followed by the rest argument list
    /// @return Sass null after the target mixin has emitted its statements
    /// @throws SassValueException if the target is not a mixin reference
    private static SassValue metaApply(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        if (!(args.get(1) instanceof SassArgumentList arguments)) {
            throw new AssertionError("meta.apply() did not receive a rest argument list");
        }
        var target = args.get(0);
        if (!(target instanceof SassMixin mixin)) {
            throw new SassValueException("$mixin: " + target + " is not a mixin reference.");
        }
        context.apply(mixin, arguments);
        return SassNull.NULL;
    }

    /// Invokes a first-class function reference with a preserved argument list.
    ///
    /// String targets retain their deprecated compatibility behavior and emit a
    /// {@code call-string} diagnostic before resolving an ordinary Sass or plain-CSS function.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the function target followed by the rest argument list
    /// @return the dynamic call result
    /// @throws SassValueException if the target is not callable or a lookup fails
    private static SassValue metaCall(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        if (!(args.get(1) instanceof SassArgumentList arguments)) {
            throw new AssertionError("meta.call() did not receive a rest argument list");
        }
        var target = args.get(0);
        if (target instanceof SassFunction function) {
            return context.call(function, arguments);
        }
        if (target instanceof SassString string) {
            context.deprecate(CALL_STRING_DEPRECATION_MESSAGE, CALL_STRING_CODE);
            @Nullable SassFunction function = context.function(metaName(string, "function"), null);
            if (function == null) {
                function = context.plainCssFunction(string.text());
            }
            return context.call(function, arguments);
        }
        throw new SassValueException("$function: " + target + " is not a function reference.");
    }

    /// Returns a normalized Sass identifier name from a string argument.
    ///
    /// Sass treats underscores and hyphens as equivalent in identifier names.
    ///
    /// @param value     the argument value
    /// @param parameter the parameter name without a dollar sign
    /// @return the hyphenated identifier name
    /// @throws SassValueException if {@code value} is not a string
    private static String metaName(SassValue value, String parameter) {
        return metaStringArgument(value, parameter).replace('_', '-');
    }

    /// Returns one required string argument's text.
    ///
    /// @param value     the argument value
    /// @param parameter the parameter name without a dollar sign
    /// @return the string text without quote markers
    /// @throws SassValueException if {@code value} is not a string
    private static String metaStringArgument(SassValue value, String parameter) {
        if (value instanceof SassString string) {
            return string.text();
        }
        throw new SassValueException("$" + parameter + ": " + value + " is not a string.");
    }

    /// Returns the optional module namespace from a string-or-null argument.
    ///
    /// @param value the argument value
    /// @return the module namespace, or {@code null} for Sass null
    /// @throws SassValueException if {@code value} is neither a string nor Sass null
    private static @Nullable String metaModuleName(SassValue value) {
        return value instanceof SassNull ? null : metaStringArgument(value, "module");
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

    /// Clamps {@code number} between compatible {@code min} and {@code max} bounds.
    ///
    /// @param args the bound min, number, and max arguments
    /// @return the clamped number
    private static SassValue clamp(List<SassValue> args) {
        var min = args.get(0).assertNumber();
        var number = args.get(1).assertNumber();
        var max = args.get(2).assertNumber();
        if (!min.isComparableTo(number) || !min.isComparableTo(max)) {
            throw new SassValueException(
                    min + ", " + number + ", and " + max + " have incompatible units."
            );
        }
        if (min.greaterThanOrEquals(max).isTruthy()) {
            return min;
        }
        if (min.greaterThanOrEquals(number).isTruthy()) {
            return min;
        }
        if (number.greaterThanOrEquals(max).isTruthy()) {
            return max;
        }
        return number;
    }

    /// Returns the square root of the sum of squares of compatible numbers.
    ///
    /// @param args the rest argument list
    /// @return the hypotenuse with the first argument's units
    private static SassValue hypot(List<SassValue> args) {
        var numbers = restValues(args);
        if (numbers.isEmpty()) {
            throw new SassValueException("At least one argument must be passed.");
        }
        var first = numbers.get(0).assertNumber();
        var subtotal = 0.0;
        for (var index = 0; index < numbers.size(); index++) {
            var number = numbers.get(index).assertNumber();
            if (!first.isComparableTo(number)) {
                throw new SassValueException(
                        first + " and " + number + " have incompatible units."
                );
            }
            var value = number.valueInUnitsOf(first);
            subtotal += value * value;
        }
        return SassNumber.withUnits(
                Math.sqrt(subtotal),
                first.numeratorUnits(),
                first.denominatorUnits()
        );
    }

    /// Returns the natural or base-{@code base} logarithm of a unitless number.
    ///
    /// @param args the bound number and optional base
    /// @return the unitless logarithm
    private static SassValue log(List<SassValue> args) {
        var number = args.get(0).assertNumber().assertNoUnits();
        if (args.get(1) instanceof SassNull) {
            return SassNumber.of(Math.log(number.value()), null);
        }
        var base = args.get(1).assertNumber().assertNoUnits();
        return SassNumber.of(Math.log(number.value()) / Math.log(base.value()), null);
    }

    /// Raises a unitless base to a unitless exponent.
    ///
    /// @param args the bound base and exponent
    /// @return the unitless power
    private static SassValue pow(List<SassValue> args) {
        var base = args.get(0).assertNumber().assertNoUnits();
        var exponent = args.get(1).assertNumber().assertNoUnits();
        return SassNumber.of(Math.pow(base.value(), exponent.value()), null);
    }

    /// Returns the square root of a unitless number.
    ///
    /// @param args the bound number
    /// @return the unitless square root
    private static SassValue sqrt(List<SassValue> args) {
        var number = args.get(0).assertNumber().assertNoUnits();
        return SassNumber.of(Math.sqrt(number.value()), null);
    }

    /// Returns the sine of an angle coerced to radians.
    ///
    /// @param args the bound angle
    /// @return the unitless sine
    private static SassValue sin(List<SassValue> args) {
        return SassNumber.of(Math.sin(radians(args.get(0).assertNumber())), null);
    }

    /// Returns the cosine of an angle coerced to radians.
    ///
    /// @param args the bound angle
    /// @return the unitless cosine
    private static SassValue cos(List<SassValue> args) {
        return SassNumber.of(Math.cos(radians(args.get(0).assertNumber())), null);
    }

    /// Returns the tangent of an angle coerced to radians.
    ///
    /// @param args the bound angle
    /// @return the unitless tangent
    private static SassValue tan(List<SassValue> args) {
        return SassNumber.of(Math.tan(radians(args.get(0).assertNumber())), null);
    }

    /// Returns the arcsine of a unitless number in degrees.
    ///
    /// @param args the bound number
    /// @return the degree angle
    private static SassValue asin(List<SassValue> args) {
        return degrees(Math.asin(args.get(0).assertNumber().assertNoUnits().value()));
    }

    /// Returns the arccosine of a unitless number in degrees.
    ///
    /// @param args the bound number
    /// @return the degree angle
    private static SassValue acos(List<SassValue> args) {
        return degrees(Math.acos(args.get(0).assertNumber().assertNoUnits().value()));
    }

    /// Returns the arctangent of a unitless number in degrees.
    ///
    /// @param args the bound number
    /// @return the degree angle
    private static SassValue atan(List<SassValue> args) {
        return degrees(Math.atan(args.get(0).assertNumber().assertNoUnits().value()));
    }

    /// Returns the two-argument arctangent of compatible coordinates in degrees.
    ///
    /// @param args the bound y and x coordinates
    /// @return the degree angle
    private static SassValue atan2(List<SassValue> args) {
        var y = args.get(0).assertNumber();
        var x = args.get(1).assertNumber();
        if (!y.isComparableTo(x)) {
            throw new SassValueException(y + " and " + x + " have incompatible units.");
        }
        return degrees(Math.atan2(y.value(), x.valueInUnitsOf(y)));
    }

    /// Returns a random unitless number, optionally bounded by a positive integer limit.
    ///
    /// @param args the optional limit
    /// @return a number in {@code [0, 1)} or an integer in {@code 1..limit}
    private static SassValue random(List<SassValue> args) {
        if (args.isEmpty() || args.get(0) instanceof SassNull) {
            return SassNumber.of(RANDOM.nextDouble(), null);
        }
        var limit = args.get(0).assertNumber();
        var limitScalar = limit.assertInt();
        if (limitScalar < 1) {
            throw new SassValueException(
                    "$limit: Must be greater than 0, was " + limit + "."
            );
        }
        return SassNumber.of(RANDOM.nextInt(limitScalar) + 1.0, null);
    }

    /// Coerces an angle to radians for trigonometric functions.
    ///
    /// @param number the angle, unitless or angle-unitful
    /// @return the magnitude in radians
    private static double radians(SassNumber number) {
        return number.coerce(RADIANS, List.of()).value();
    }

    /// Returns one degree-valued angle from a radian magnitude.
    ///
    /// @param radians the angle in radians
    /// @return the degree number
    private static SassNumber degrees(double radians) {
        return SassNumber.withUnits(radians * (180.0 / Math.PI), DEGREES, List.of());
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
        return mapNumber(args.get(0), BuiltInFunctions::roundSass);
    }

    /// Rounds a finite midpoint away from zero, matching Sass number rounding.
    ///
    /// @param value the value to round
    /// @return the nearest integral magnitude with midpoint ties away from zero
    private static double roundSass(double value) {
        var rounded = Math.floor(Math.abs(value) + 0.5);
        return rounded == 0.0 ? 0.0 : Math.copySign(rounded, value);
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

    /// Looks up a value by a possibly nested path in a map module call.
    ///
    /// @param args the map, first key, and remaining key arguments
    /// @return the mapped value or Sass null when no path element exists
    private static SassValue moduleMapGet(List<SassValue> args) {
        var map = args.get(0).assertMap();
        var keys = keysWithRest(args);
        for (var index = 0; index < keys.size() - 1; index++) {
            @Nullable SassValue value = map.contents().get(keys.get(index));
            @Nullable SassMap nested = value == null ? null : value.tryMap();
            if (nested == null) {
                return SassNull.NULL;
            }
            map = nested;
        }
        @Nullable SassValue value = map.contents().get(keys.get(keys.size() - 1));
        return value == null ? SassNull.NULL : value;
    }

    /// Returns whether a possibly nested path exists in a map module call.
    ///
    /// @param args the map, first key, and remaining key arguments
    /// @return whether the complete path is present
    private static SassValue moduleMapHasKey(List<SassValue> args) {
        var map = args.get(0).assertMap();
        var keys = keysWithRest(args);
        for (var index = 0; index < keys.size() - 1; index++) {
            @Nullable SassValue value = map.contents().get(keys.get(index));
            @Nullable SassMap nested = value == null ? null : value.tryMap();
            if (nested == null) {
                return SassBoolean.FALSE;
            }
            map = nested;
        }
        return SassBoolean.of(map.contents().containsKey(keys.get(keys.size() - 1)));
    }

    /// Sets one direct map key to a replacement value.
    ///
    /// @param args the map, key, and replacement value
    /// @return a map containing the replacement entry
    private static SassValue mapSet(List<SassValue> args) {
        var contents = new LinkedHashMap<>(args.get(0).assertMap().contents());
        contents.put(args.get(1), args.get(2));
        return new SassMap(contents);
    }

    /// Removes all supplied direct keys from a map.
    ///
    /// @param args the map and rest key arguments
    /// @return a map without the supplied keys
    private static SassValue mapRemove(List<SassValue> args) {
        var contents = new LinkedHashMap<>(args.get(0).assertMap().contents());
        for (var key : restValuesAt(args, 1)) {
            contents.remove(key);
        }
        return new SassMap(contents);
    }

    /// Merges nested map values recursively while giving the second map precedence.
    ///
    /// @param args the maps to merge
    /// @return a recursively merged map
    private static SassValue mapDeepMerge(List<SassValue> args) {
        return deepMerge(args.get(0).assertMap(), args.get(1).assertMap());
    }

    /// Removes a possibly nested path from a map.
    ///
    /// @param args the map, first key, and remaining key arguments
    /// @return a map with the target path removed when it exists
    private static SassValue mapDeepRemove(List<SassValue> args) {
        return deepRemove(args.get(0).assertMap(), keysWithRest(args), 0);
    }

    /// Recursively merges a pair of Sass maps.
    ///
    /// @param first the lower-precedence map
    /// @param second the higher-precedence map
    /// @return the recursively merged map
    private static SassMap deepMerge(SassMap first, SassMap second) {
        var contents = new LinkedHashMap<>(first.contents());
        for (var entry : second.contents().entrySet()) {
            @Nullable SassValue previous = contents.get(entry.getKey());
            @Nullable SassMap previousMap = previous == null ? null : previous.tryMap();
            @Nullable SassMap replacementMap = entry.getValue().tryMap();
            if (previousMap != null && replacementMap != null) {
                contents.put(entry.getKey(), deepMerge(previousMap, replacementMap));
            } else {
                contents.put(entry.getKey(), entry.getValue());
            }
        }
        return new SassMap(contents);
    }

    /// Removes a nested path while preserving the original map for a missing path.
    ///
    /// @param map the map at the current recursion level
    /// @param keys the complete non-empty target path
    /// @param index the current path index
    /// @return the updated map, or {@code map} when no matching path exists
    private static SassMap deepRemove(SassMap map, List<SassValue> keys, int index) {
        var key = keys.get(index);
        @Nullable SassValue value = map.contents().get(key);
        if (value == null) {
            return map;
        }
        var contents = new LinkedHashMap<>(map.contents());
        if (index == keys.size() - 1) {
            contents.remove(key);
            return new SassMap(contents);
        }
        @Nullable SassMap nested = value.tryMap();
        if (nested == null) {
            return map;
        }
        var updated = deepRemove(nested, keys, index + 1);
        if (updated == nested) {
            return map;
        }
        contents.put(key, updated);
        return new SassMap(contents);
    }

    /// Returns the declared first key followed by rest keys for a map call.
    ///
    /// @param args the bound map call arguments
    /// @return an immutable non-empty key path
    private static @Unmodifiable List<SassValue> keysWithRest(List<SassValue> args) {
        var keys = new ArrayList<SassValue>();
        keys.add(args.get(1));
        keys.addAll(restValuesAt(args, 2));
        return List.copyOf(keys);
    }

    /// Reads positional values from the rest argument at one bound index.
    ///
    /// @param args the bound arguments
    /// @param index the rest argument index
    /// @return the rest values, or an empty list when no rest argument exists
    private static @Unmodifiable List<SassValue> restValuesAt(
            List<SassValue> args,
            int index
    ) {
        if (args.size() <= index) {
            return List.of();
        }
        var rest = args.get(index);
        return rest instanceof SassArgumentList list ? list.asList() : List.of(rest);
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

    /// Inserts one string at a Sass string index.
    ///
    /// Positive indexes insert before the indexed code point. Negative indexes
    /// insert after the indexed code point counted from the end.
    ///
    /// @param args the string, inserted string, and unitless integer index
    /// @return the combined string with the original quote state
    private static SassValue strInsert(List<SassValue> args) {
        var string = args.get(0).assertString();
        var inserted = args.get(1).assertString();
        var text = string.text();
        var length = text.codePointCount(0, text.length());
        var index = args.get(2).assertNumber().assertNoUnits().assertInt();
        var codePointIndex = index < 0
                ? Math.max(length + index + 1, 0)
                : index == 0 ? 0 : Math.min(index - 1, length);
        var offset = text.offsetByCodePoints(0, codePointIndex);
        return new SassString(
                text.substring(0, offset) + inserted.text() + text.substring(offset),
                string.hasQuotes()
        );
    }

    /// Splits one string into a bracketed comma-separated list.
    ///
    /// @param args the string, separator string, and optional split limit
    /// @return a bracketed list that retains the source string quote state
    private static SassValue strSplit(List<SassValue> args) {
        var string = args.get(0).assertString();
        var separator = args.get(1).assertString();
        @Nullable Integer limit = splitLimit(args.get(2));
        var text = string.text();
        if (text.isEmpty()) {
            return new SassList(List.of(), ListSeparator.COMMA, true);
        }

        var contents = new ArrayList<SassValue>();
        if (separator.text().isEmpty()) {
            for (var offset = 0; offset < text.length(); ) {
                var codePoint = text.codePointAt(offset);
                contents.add(new SassString(
                        new String(Character.toChars(codePoint)),
                        string.hasQuotes()
                ));
                offset += Character.charCount(codePoint);
            }
            return new SassList(contents, ListSeparator.COMMA, true);
        }

        var start = 0;
        var splits = 0;
        while (limit == null || splits < limit) {
            var match = text.indexOf(separator.text(), start);
            if (match < 0) {
                break;
            }
            contents.add(new SassString(text.substring(start, match), string.hasQuotes()));
            start = match + separator.text().length();
            splits++;
        }
        contents.add(new SassString(text.substring(start), string.hasQuotes()));
        return new SassList(contents, ListSeparator.COMMA, true);
    }

    /// Validates the optional split limit.
    ///
    /// @param value the bound optional limit argument
    /// @return the maximum separator count, or {@code null} when unlimited
    private static @Nullable Integer splitLimit(SassValue value) {
        if (value instanceof SassNull) {
            return null;
        }
        var number = value.assertNumber().assertNoUnits();
        var limit = number.assertInt();
        if (limit < 1) {
            throw new SassValueException(
                    "$limit: Must be 1 or greater, was " + number + "."
            );
        }
        return limit;
    }

    /// Extracts an inclusive Sass string slice using code-point indexes.
    ///
    /// @param args the string, start index, and optional inclusive end index
    /// @return a string retaining the source quote state
    private static SassValue strSlice(List<SassValue> args) {
        var string = args.get(0).assertString();
        var text = string.text();
        var length = text.codePointCount(0, text.length());
        var start = stringCodePointIndex(
                args.get(1).assertNumber().assertNoUnits().assertInt(),
                length,
                false
        );
        var endArgument = args.get(2).assertNumber().assertNoUnits().assertInt();
        if (endArgument == 0) {
            return new SassString("", string.hasQuotes());
        }
        var end = stringCodePointIndex(endArgument, length, true);
        if (end == length) {
            end--;
        }
        if (end < start) {
            return new SassString("", string.hasQuotes());
        }
        var startOffset = text.offsetByCodePoints(0, start);
        var endOffset = text.offsetByCodePoints(0, end + 1);
        return new SassString(text.substring(startOffset, endOffset), string.hasQuotes());
    }

    /// Converts a one-based Sass index to a zero-based code-point index.
    ///
    /// @param index the Sass integer index
    /// @param length the number of code points in the source string
    /// @param allowNegative whether a negative result may be retained
    /// @return a zero-based code-point index
    private static int stringCodePointIndex(int index, int length, boolean allowNegative) {
        if (index == 0) {
            return 0;
        }
        if (index > 0) {
            return Math.min(index - 1, length);
        }
        var result = length + index;
        return result < 0 && !allowNegative ? 0 : result;
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

    /// Returns the legacy RGB red channel rounded to Sass's nearest integer.
    ///
    /// @param args the one color argument
    /// @return the rounded red channel
    private static SassValue red(List<SassValue> args) {
        return SassNumber.of(roundSass(args.get(0).assertColor().red()), null);
    }

    /// Returns the legacy RGB green channel rounded to Sass's nearest integer.
    ///
    /// @param args the one color argument
    /// @return the rounded green channel
    private static SassValue green(List<SassValue> args) {
        return SassNumber.of(roundSass(args.get(0).assertColor().green()), null);
    }

    /// Returns the legacy RGB blue channel rounded to Sass's nearest integer.
    ///
    /// @param args the one color argument
    /// @return the rounded blue channel
    private static SassValue blue(List<SassValue> args) {
        return SassNumber.of(roundSass(args.get(0).assertColor().blue()), null);
    }

    /// Returns the alpha channel of one color.
    ///
    /// @param args the one color argument
    /// @return the alpha channel
    private static SassValue alphaChannel(List<SassValue> args) {
        return SassNumber.of(args.get(0).assertColor().alpha(), null);
    }

    /// Mixes two colors using Sass's legacy RGB algorithm.
    ///
    /// @param args the two colors, legacy weight, and interpolation method
    /// @return the mixed color
    private static SassValue colorMix(List<SassValue> args) {
        var first = colorArgument(args.get(0), "color1");
        var second = colorArgument(args.get(1), "color2");
        var weight = numberArgument(args.get(2), "weight");
        requireLegacyRgbOnly(args.get(3), "method", "color.mix");
        return first.mixedWith(second, legacyWeight(weight, "weight"));
    }

    /// Inverts a legacy RGB color or preserves a plain-CSS number filter.
    ///
    /// @param args the color or number, legacy weight, and output space
    /// @return the inverted color or an unquoted CSS {@code invert()} function
    private static SassValue colorInvert(List<SassValue> args) {
        var weight = numberArgument(args.get(1), "weight");
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            if (!isCssFilterDefaultWeight(weight)) {
                throw new SassValueException(
                        "Only one argument may be passed to the plain-CSS invert() function."
                );
            }
            return new SassString("invert(" + number.toCssString() + ")", false);
        }
        var color = colorArgument(value, "color");
        requireLegacyRgbOnly(args.get(2), "space", "color.invert");
        return color.inverted().mixedWith(color, legacyWeight(weight, "weight"));
    }

    /// Returns the legacy HSL hue of one RGB color.
    ///
    /// @param args the one color argument
    /// @return the hue in degrees
    private static SassValue colorHue(List<SassValue> args) {
        return SassNumber.of(colorArgument(args.get(0), "color").hue(), "deg");
    }

    /// Returns the legacy HSL saturation of one RGB color.
    ///
    /// @param args the one color argument
    /// @return the saturation percentage
    private static SassValue colorSaturation(List<SassValue> args) {
        return SassNumber.of(colorArgument(args.get(0), "color").saturation(), "%");
    }

    /// Returns the legacy HSL lightness of one RGB color.
    ///
    /// @param args the one color argument
    /// @return the lightness percentage
    private static SassValue colorLightness(List<SassValue> args) {
        return SassNumber.of(colorArgument(args.get(0), "color").lightness(), "%");
    }

    /// Returns a grayscale legacy RGB color or preserves a plain-CSS number filter.
    ///
    /// @param args the one color or number argument
    /// @return the grayscale color or an unquoted CSS {@code grayscale()} function
    private static SassValue colorGrayscale(List<SassValue> args) {
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            return new SassString("grayscale(" + number.toCssString() + ")", false);
        }
        return colorArgument(value, "color").grayscale();
    }

    /// Returns the legacy HSL complement of one RGB color.
    ///
    /// @param args the color and optional output space
    /// @return the complemented color
    private static SassValue colorComplement(List<SassValue> args) {
        var color = colorArgument(args.get(0), "color");
        requireLegacyRgbOnly(args.get(1), "space", "color.complement");
        return color.complemented();
    }

    /// Converts one legacy color weight to a fractional first-color contribution.
    ///
    /// @param number the supplied weight number
    /// @param name the parameter name used for diagnostics
    /// @return the weight between zero and one
    /// @throws SassValueException if the number has unsupported units or lies outside zero to 100
    private static double legacyWeight(SassNumber number, String name) {
        var percent = number.numeratorUnits().equals(List.of("%"))
                && number.denominatorUnits().isEmpty();
        if (!number.isUnitless() && !percent) {
            throw new SassValueException(
                    "$" + name + ": Expected " + number + " to have unit \"%\" or no units."
            );
        }
        try {
            return number.valueInRange(0.0, 100.0) / 100.0;
        } catch (SassValueException exception) {
            throw new SassValueException("$" + name + ": " + exception.getMessage());
        }
    }

    /// Returns whether a CSS filter call uses its sole supported default weight.
    ///
    /// @param weight the supplied filter weight
    /// @return whether {@code weight} is exactly {@code 100%}
    private static boolean isCssFilterDefaultWeight(SassNumber weight) {
        return weight.value() == 100.0
                && weight.numeratorUnits().equals(List.of("%"))
                && weight.denominatorUnits().isEmpty();
    }

    /// Returns a color argument while identifying its Sass parameter in failures.
    ///
    /// @param value the supplied argument
    /// @param name the parameter name used for diagnostics
    /// @return the supplied color
    /// @throws SassValueException if {@code value} is not a color
    private static SassColor colorArgument(SassValue value, String name) {
        try {
            return value.assertColor();
        } catch (SassValueException exception) {
            throw new SassValueException("$" + name + ": " + exception.getMessage());
        }
    }

    /// Returns a number argument while identifying its Sass parameter in failures.
    ///
    /// @param value the supplied argument
    /// @param name the parameter name used for diagnostics
    /// @return the supplied number
    /// @throws SassValueException if {@code value} is not a number
    private static SassNumber numberArgument(SassValue value, String name) {
        try {
            return value.assertNumber();
        } catch (SassValueException exception) {
            throw new SassValueException("$" + name + ": " + exception.getMessage());
        }
    }

    /// Rejects Color 4-only interpolation and output-space arguments.
    ///
    /// @param value the optional Color 4 argument
    /// @param name the parameter name used for diagnostics
    /// @param function the exported color function name
    /// @throws SassValueException if {@code value} is not {@code null}
    private static void requireLegacyRgbOnly(SassValue value, String name, String function) {
        if (!(value instanceof SassNull)) {
            throw new SassValueException(
                    "$" + name + ": " + function + "() only supports the legacy RGB algorithm."
            );
        }
    }

    private static SassValue opacity(List<SassValue> args) {
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            return new SassString("opacity(" + number.toCssString() + ")", false);
        }
        return SassNumber.of(value.assertColor().alpha(), null);
    }

    /// Compares two legacy RGB colors using Sass value equality.
    ///
    /// @param args the two color arguments
    /// @return whether all color channels are Sass-equal
    private static SassValue colorSame(List<SassValue> args) {
        return SassBoolean.of(
                args.get(0).assertColor().equals(args.get(1).assertColor())
        );
    }

    /// Adjusts legacy RGB or HSL channels by additive deltas.
    ///
    /// @param args the color and keyword argument list
    /// @return the adjusted color
    private static SassValue colorAdjust(List<SassValue> args) {
        return updateColorComponents(args, ColorUpdateMode.ADJUST);
    }

    /// Scales legacy RGB or HSL channels toward their channel extremes.
    ///
    /// @param args the color and keyword argument list
    /// @return the scaled color
    private static SassValue colorScale(List<SassValue> args) {
        return updateColorComponents(args, ColorUpdateMode.SCALE);
    }

    /// Replaces legacy RGB or HSL channels with absolute values.
    ///
    /// @param args the color and keyword argument list
    /// @return the changed color
    private static SassValue colorChange(List<SassValue> args) {
        return updateColorComponents(args, ColorUpdateMode.CHANGE);
    }

    /// Identifies how {@code color.adjust}, {@code color.scale}, and
    /// {@code color.change} rewrite channel values.
    private enum ColorUpdateMode {
        /// Adds deltas to existing channel values.
        ADJUST,
        /// Scales channels toward their minimum or maximum.
        SCALE,
        /// Replaces channel values absolutely.
        CHANGE
    }

    /// Identifies the legacy channel space selected by keyword sniffing.
    private enum LegacyColorSpace {
        /// The RGB channel space.
        RGB,
        /// The HSL channel space.
        HSL
    }

    /// Implements legacy-only {@code adjust}/{@code scale}/{@code change}.
    ///
    /// @param args the color and keyword rest list
    /// @param mode the update algorithm
    /// @return the rewritten color
    private static SassValue updateColorComponents(
            List<SassValue> args,
            ColorUpdateMode mode
    ) {
        var color = colorArgument(args.get(0), "color");
        if (!(args.get(1) instanceof SassArgumentList keywordsRest)) {
            throw new SassValueException(
                    "Only one positional argument is allowed. All other arguments must be passed by name."
            );
        }
        if (!keywordsRest.asList().isEmpty()) {
            throw new SassValueException(
                    "Only one positional argument is allowed. All other arguments must be passed by name."
            );
        }
        var keywords = new LinkedHashMap<>(keywordsRest.keywords());
        if (keywords.containsKey("space")) {
            requireLegacyRgbOnly(keywords.get("space"), "space", "color." + modeName(mode));
            keywords.remove("space");
        }
        if (keywords.containsKey("whiteness") || keywords.containsKey("blackness")) {
            throw new SassValueException(
                    "color." + modeName(mode) + "() only supports the legacy RGB and HSL algorithms."
            );
        }
        @Nullable SassValue alphaArg = keywords.remove("alpha");
        var space = sniffLegacyColorSpace(keywords.keySet());
        if (space == null && alphaArg == null) {
            if (!keywords.isEmpty()) {
                throw unknownColorChannel(keywords.keySet().iterator().next(), LegacyColorSpace.RGB);
            }
            return color;
        }
        if (space == null) {
            space = LegacyColorSpace.RGB;
        }
        return switch (space) {
            case RGB -> updateRgbColor(color, keywords, alphaArg, mode);
            case HSL -> updateHslColor(color, keywords, alphaArg, mode);
        };
    }

    /// Returns the exported function name for one update mode.
    ///
    /// @param mode the update mode
    /// @return the hyphenated function name
    private static String modeName(ColorUpdateMode mode) {
        return switch (mode) {
            case ADJUST -> "adjust";
            case SCALE -> "scale";
            case CHANGE -> "change";
        };
    }

    /// Sniffs whether keyword names target RGB or HSL channels.
    ///
    /// @param names the remaining keyword names
    /// @return the sniffed space, or {@code null} when only alpha may remain
    private static @Nullable LegacyColorSpace sniffLegacyColorSpace(Iterable<String> names) {
        var sawHue = false;
        for (var name : names) {
            switch (name) {
                case "red", "green", "blue" -> {
                    return LegacyColorSpace.RGB;
                }
                case "saturation", "lightness" -> {
                    return LegacyColorSpace.HSL;
                }
                case "hue" -> sawHue = true;
                default -> {
                }
            }
        }
        return sawHue ? LegacyColorSpace.HSL : null;
    }

    /// Updates RGB channels of one color.
    private static SassColor updateRgbColor(
            SassColor color,
            Map<String, SassValue> keywords,
            @Nullable SassValue alphaArg,
            ColorUpdateMode mode
    ) {
        var red = color.red();
        var green = color.green();
        var blue = color.blue();
        var alpha = color.alpha();
        for (var entry : keywords.entrySet()) {
            switch (entry.getKey()) {
                case "red" -> red = updateLinearChannel(
                        red, entry.getValue(), "red", 0.0, 255.0, true, true, mode
                );
                case "green" -> green = updateLinearChannel(
                        green, entry.getValue(), "green", 0.0, 255.0, true, true, mode
                );
                case "blue" -> blue = updateLinearChannel(
                        blue, entry.getValue(), "blue", 0.0, 255.0, true, true, mode
                );
                default -> throw unknownColorChannel(entry.getKey(), LegacyColorSpace.RGB);
            }
        }
        if (alphaArg != null) {
            alpha = updateAlpha(alpha, alphaArg, mode);
        }
        return SassColor.rgb(red, green, blue, alpha, null);
    }

    /// Updates HSL channels of one color.
    private static SassColor updateHslColor(
            SassColor color,
            Map<String, SassValue> keywords,
            @Nullable SassValue alphaArg,
            ColorUpdateMode mode
    ) {
        var hue = color.hue();
        var saturation = color.saturation();
        var lightness = color.lightness();
        var alpha = color.alpha();
        for (var entry : keywords.entrySet()) {
            switch (entry.getKey()) {
                case "hue" -> {
                    if (mode == ColorUpdateMode.SCALE) {
                        throw new SassValueException("$hue: Channel isn't scalable.");
                    }
                    var number = numberArgument(entry.getValue(), "hue");
                    var degrees = hueDegrees(number);
                    hue = mode == ColorUpdateMode.CHANGE ? degrees : hue + degrees;
                }
                case "saturation" -> saturation = updateLinearChannel(
                        saturation, entry.getValue(), "saturation", 0.0, 100.0, true, false, mode
                );
                case "lightness" -> lightness = updateLinearChannel(
                        lightness, entry.getValue(), "lightness", 0.0, 100.0, false, false, mode
                );
                default -> throw unknownColorChannel(entry.getKey(), LegacyColorSpace.HSL);
            }
        }
        if (alphaArg != null) {
            alpha = updateAlpha(alpha, alphaArg, mode);
        }
        return SassColor.hsl(hue, saturation, lightness, alpha);
    }

    /// Updates one linear channel according to the selected mode.
    private static double updateLinearChannel(
            double oldValue,
            SassValue argument,
            String name,
            double minimum,
            double maximum,
            boolean lowerClamped,
            boolean upperClamped,
            ColorUpdateMode mode
    ) {
        var number = numberArgument(argument, name);
        return switch (mode) {
            case CHANGE -> percentageOrUnitless(number, maximum, name);
            case ADJUST -> {
                var delta = percentageOrUnitless(number, maximum, name);
                yield clampAdjustedChannel(oldValue + delta, oldValue, minimum, maximum, lowerClamped, upperClamped);
            }
            case SCALE -> scaleChannel(oldValue, number, name, minimum, maximum);
        };
    }

    /// Updates the alpha channel according to the selected mode.
    private static double updateAlpha(
            double oldValue,
            SassValue argument,
            ColorUpdateMode mode
    ) {
        var number = numberArgument(argument, "alpha");
        return switch (mode) {
            case CHANGE -> {
                var next = percentageOrUnitless(number, 1.0, "alpha");
                yield clamp(next, 0.0, 1.0);
            }
            case ADJUST -> {
                var delta = percentageOrUnitless(number, 1.0, "alpha");
                yield clamp(oldValue + delta, 0.0, 1.0);
            }
            case SCALE -> scaleChannel(oldValue, number, "alpha", 0.0, 1.0);
        };
    }

    /// Scales one channel toward its minimum or maximum by a percent factor.
    private static double scaleChannel(
            double oldValue,
            SassNumber factorArg,
            String name,
            double minimum,
            double maximum
    ) {
        if (!(factorArg.numeratorUnits().equals(List.of("%"))
                && factorArg.denominatorUnits().isEmpty())) {
            throw new SassValueException("$" + name + ": Expected " + factorArg + " to have unit \"%\".");
        }
        double factor;
        try {
            factor = factorArg.valueInRange(-100.0, 100.0) / 100.0;
        } catch (SassValueException exception) {
            throw new SassValueException("$" + name + ": " + exception.getMessage());
        }
        if (factor == 0.0) {
            return oldValue;
        }
        if (factor > 0.0) {
            return oldValue >= maximum ? oldValue : oldValue + (maximum - oldValue) * factor;
        }
        return oldValue <= minimum ? oldValue : oldValue + (oldValue - minimum) * factor;
    }

    /// Clamps an adjusted channel using Sass linear-channel clamping rules.
    private static double clampAdjustedChannel(
            double result,
            double oldValue,
            double minimum,
            double maximum,
            boolean lowerClamped,
            boolean upperClamped
    ) {
        if (lowerClamped && result < minimum) {
            return oldValue < minimum ? Math.max(oldValue, result) : minimum;
        }
        if (upperClamped && result > maximum) {
            return oldValue > maximum ? Math.min(oldValue, result) : maximum;
        }
        return result;
    }

    /// Parses a number that is unitless or a percentage of {@code max}.
    private static double percentageOrUnitless(SassNumber number, double max, String name) {
        if (number.isUnitless()) {
            return number.value();
        }
        if (number.numeratorUnits().equals(List.of("%")) && number.denominatorUnits().isEmpty()) {
            return max * number.value() / 100.0;
        }
        throw new SassValueException(
                "$" + name + ": Expected " + number + " to have unit \"%\" or no units."
        );
    }

    /// Converts a hue argument to degrees.
    private static double hueDegrees(SassNumber number) {
        if (number.isUnitless()) {
            return number.value();
        }
        try {
            return number.coerce(List.of("deg"), List.of()).value();
        } catch (SassValueException exception) {
            throw new SassValueException("$hue: " + exception.getMessage());
        }
    }

    /// Clamps a finite value into an inclusive range.
    private static double clamp(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }

    /// Creates the failure for an unsupported keyword channel name.
    private static SassValueException unknownColorChannel(
            String name,
            LegacyColorSpace space
    ) {
        return new SassValueException(
                "$" + name + ": Color space " + space.name().toLowerCase(Locale.ROOT)
                        + " doesn't have a channel with this name."
        );
    }

    /// Returns the keyword map carried by an argument list.
    ///
    /// @param args the one argument-list value
    /// @return a Sass map keyed by unquoted keyword names
    /// @throws SassValueException if the value is not an argument list
    private static SassValue keywords(List<SassValue> args) {
        var value = args.get(0);
        if (!(value instanceof SassArgumentList argumentList)) {
            throw new SassValueException("$args: " + value + " is not an argument list.");
        }
        var contents = new LinkedHashMap<SassValue, SassValue>();
        for (var entry : argumentList.keywords().entrySet()) {
            contents.put(new SassString(entry.getKey(), false), entry.getValue());
        }
        return new SassMap(contents);
    }
}
