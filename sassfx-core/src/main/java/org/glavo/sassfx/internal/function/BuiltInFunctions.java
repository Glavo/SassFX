// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.function;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.callable.BuiltInCallable;
import org.glavo.sassfx.internal.callable.BuiltInCallable.Param;
import org.glavo.sassfx.internal.callable.UserDefinedCallable;
import org.glavo.sassfx.internal.module.ConfiguredValue;
import org.glavo.sassfx.internal.module.ModuleConfiguration;
import org.glavo.sassfx.internal.value.CalculationOperation;
import org.glavo.sassfx.internal.value.ListSeparator;
import org.glavo.sassfx.internal.value.RgbFunctionColorFormat;
import org.glavo.sassfx.internal.value.SassArgumentList;
import org.glavo.sassfx.internal.value.SassBoolean;
import org.glavo.sassfx.internal.value.SassCalculation;
import org.glavo.sassfx.internal.value.SassColor;
import org.glavo.sassfx.internal.value.SassFunction;
import org.glavo.sassfx.internal.value.SassMixin;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassMap;
import org.glavo.sassfx.internal.value.SassNull;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.glavo.sassfx.internal.value.SassValueException;
import org.glavo.sassfx.internal.value.SassFuzzy;
import org.glavo.sassfx.internal.value.color.ColorChannel;
import org.glavo.sassfx.internal.value.color.ColorSpace;
import org.glavo.sassfx.internal.value.color.GamutMapMethod;
import org.glavo.sassfx.internal.value.color.InterpolationMethod;
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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
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

    /// Contains the stable deprecation identifier for invalid function units.
    private static final String FUNCTION_UNITS_CODE = "function-units";

    /// Contains the stable deprecation identifier for legacy color functions.
    private static final String COLOR_FUNCTIONS_CODE = "color-functions";

    /// Contains the stable deprecation identifier for color-module CSS fallbacks.
    private static final String COLOR_MODULE_COMPAT_CODE = "color-module-compat";

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
        // Accept both rgb($red, $green, $blue, $alpha) and rgb($color, $alpha)
        // named forms by using rest-parameter dispatch.
        register(functions, BuiltInCallable.withRest(
                "rgb",
                List.of(),
                "args",
                BuiltInFunctions::rgbRest
        ));
        register(functions, BuiltInCallable.withRest(
                "rgba",
                List.of(),
                "args",
                BuiltInFunctions::rgbaRest
        ));
        register(functions, BuiltInCallable.of(
                "color",
                List.of(Param.required("description")),
                1,
                BuiltInFunctions::cssColor
        ));
        // hwb/hsl/hsla use rest-parameter dispatch so both $channels and
        // multi-arg named forms ($hue/$saturation/...) resolve correctly.
        register(functions, BuiltInCallable.contextualWithRest(
                "hwb",
                List.of(),
                "args",
                0,
                BuiltInFunctions::hwbRest
        ));
        register(functions, BuiltInCallable.of(
                "lab",
                List.of(Param.required("channels")),
                1,
                BuiltInFunctions::cssLab
        ));
        register(functions, BuiltInCallable.of(
                "lch",
                List.of(Param.required("channels")),
                1,
                BuiltInFunctions::cssLch
        ));
        register(functions, BuiltInCallable.of(
                "oklab",
                List.of(Param.required("channels")),
                1,
                BuiltInFunctions::cssOklab
        ));
        register(functions, BuiltInCallable.of(
                "oklch",
                List.of(Param.required("channels")),
                1,
                BuiltInFunctions::cssOklch
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "hsl",
                List.of(),
                "args",
                0,
                BuiltInFunctions::hslRest
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "hsla",
                List.of(),
                "args",
                0,
                BuiltInFunctions::hslaRest
        ));
        register(functions, BuiltInCallable.of("quote", List.of("string"), BuiltInFunctions::quote));
        register(functions, BuiltInCallable.of("unquote", List.of("string"), BuiltInFunctions::unquote));
        register(functions, BuiltInCallable.of("length", List.of("list"), BuiltInFunctions::length));
        register(functions, BuiltInCallable.contextual(
                "nth",
                List.of(Param.required("list"), Param.required("n")),
                2,
                BuiltInFunctions::nth
        ));
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
        // Eager ternary used by get-function("if") / meta.call. Direct if() calls
        // remain LegacyIfExpression/IfExpression so unused branches stay unevaluated.
        register(functions, BuiltInCallable.of(
                "if",
                List.of("condition", "if-true", "if-false"),
                BuiltInFunctions::legacyIf
        ));
        register(functions, BuiltInCallable.contextual(
                "feature-exists",
                List.of(Param.required("feature")),
                1,
                BuiltInFunctions::featureExists
        ));
        register(functions, BuiltInCallable.of("unit", List.of("number"), BuiltInFunctions::unit));
        register(functions, BuiltInCallable.of(
                "comparable",
                List.of("number1", "number2"),
                BuiltInFunctions::comparable
        ));
        // Global alias of math.is-unitless (deprecated in favor of the module form).
        register(functions, BuiltInCallable.of(
                "unitless",
                List.of("number"),
                BuiltInFunctions::isUnitless
        ));
        register(functions, BuiltInCallable.of(
                "percentage",
                List.of("number"),
                BuiltInFunctions::percentage
        ));
        register(functions, BuiltInCallable.contextual(
                "abs",
                List.of(Param.required("number")),
                1,
                BuiltInFunctions::abs
        ));
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
        register(functions, BuiltInCallable.contextual(
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
        // Global map-remove accepts rest keys like map.remove (deprecated global form).
        register(functions, BuiltInCallable.withRest(
                "map-remove",
                List.of("map"),
                "keys",
                BuiltInFunctions::mapRemove
        ));

        // Legacy global selector algebra names mirror sass:selector.
        var selector = SelectorFunctions.module();
        registerGlobalSelector(functions, selector, "nest", "selector-nest");
        registerGlobalSelector(functions, selector, "append", "selector-append");
        registerGlobalSelector(functions, selector, "replace", "selector-replace");
        registerGlobalSelector(functions, selector, "extend", "selector-extend");
        registerGlobalSelector(functions, selector, "unify", "selector-unify");
        registerGlobalSelector(functions, selector, "is-superselector", "is-superselector");
        registerGlobalSelector(functions, selector, "simple-selectors", "simple-selectors");
        registerGlobalSelector(functions, selector, "parse", "selector-parse");

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
                "str-insert",
                List.of("string", "insert", "index"),
                BuiltInFunctions::strInsert
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
        register(functions, BuiltInCallable.contextual(
                "set-nth",
                List.of(
                        Param.required("list"),
                        Param.required("n"),
                        Param.required("value")
                ),
                3,
                BuiltInFunctions::setNth
        ));
        register(functions, BuiltInCallable.withRest(
                "zip",
                List.of(),
                "lists",
                BuiltInFunctions::zip
        ));

        register(functions, deprecatedColorChannelFunction(
                "red",
                ColorSpace.RGB,
                null,
                true
        ));
        register(functions, deprecatedColorChannelFunction(
                "green",
                ColorSpace.RGB,
                null,
                true
        ));
        register(functions, deprecatedColorChannelFunction(
                "blue",
                ColorSpace.RGB,
                null,
                true
        ));
        // Alpha supports Microsoft filter overloads: alpha(c=d) and multi-arg forms.
        register(functions, BuiltInCallable.contextualWithRest(
                "alpha",
                List.of(),
                "args",
                0,
                BuiltInFunctions::alphaChannel
        ));
        register(functions, BuiltInCallable.contextual(
                "opacity",
                List.of(Param.required("color")),
                1,
                BuiltInFunctions::opacity
        ));
        register(functions, deprecatedColorChannelFunction(
                "hue",
                ColorSpace.HSL,
                "deg",
                true
        ));
        register(functions, deprecatedColorChannelFunction(
                "saturation",
                ColorSpace.HSL,
                "%",
                true
        ));
        register(functions, deprecatedColorChannelFunction(
                "lightness",
                ColorSpace.HSL,
                "%",
                true
        ));
        // Global mix() shares the Color 4 $method parameter with color.mix();
        // non-legacy colors require $method even when called without a namespace.
        register(functions, BuiltInCallable.contextual(
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
        // Global invert accepts $space for Color 4 modern colors; callers are
        // still warned by the global-builtin deprecation path.
        register(functions, BuiltInCallable.contextual(
                "invert",
                List.of(
                        Param.required("color"),
                        Param.optional("weight", SassNumber.of(100, "%")),
                        Param.optional("space", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::globalColorInvert
        ));
        register(functions, BuiltInCallable.contextual(
                "grayscale",
                List.of(Param.required("color")),
                1,
                BuiltInFunctions::globalGrayscale
        ));
        register(functions, BuiltInCallable.of(
                "complement",
                List.of("color"),
                BuiltInFunctions::globalComplement
        ));
        register(functions, BuiltInCallable.contextual(
                "adjust-hue",
                List.of(
                        Param.required("color"),
                        Param.required("degrees")
                ),
                2,
                BuiltInFunctions::adjustHue
        ));
        register(functions, BuiltInCallable.contextual(
                "lighten",
                List.of(
                        Param.required("color"),
                        Param.required("amount")
                ),
                2,
                BuiltInFunctions::lighten
        ));
        register(functions, BuiltInCallable.contextual(
                "darken",
                List.of(
                        Param.required("color"),
                        Param.required("amount")
                ),
                2,
                BuiltInFunctions::darken
        ));
        // Global saturate() overloads CSS filter saturate($amount) and the
        // legacy color adjuster saturate($color, $amount). Rest dispatch keeps
        // both signatures' missing-argument diagnostics aligned with dart-sass.
        register(functions, BuiltInCallable.contextualWithRest(
                "saturate",
                List.of(),
                "args",
                0,
                BuiltInFunctions::saturateRest
        ));
        register(functions, BuiltInCallable.contextual(
                "desaturate",
                List.of(
                        Param.required("color"),
                        Param.required("amount")
                ),
                2,
                BuiltInFunctions::desaturate
        ));
        register(functions, BuiltInCallable.contextual(
                "opacify",
                List.of(
                        Param.required("color"),
                        Param.required("amount")
                ),
                2,
                (context, args) -> opacifyNamed(context, args, "opacify")
        ));
        register(functions, BuiltInCallable.contextual(
                "fade-in",
                List.of(
                        Param.required("color"),
                        Param.required("amount")
                ),
                2,
                (context, args) -> opacifyNamed(context, args, "fade-in")
        ));
        register(functions, BuiltInCallable.contextual(
                "transparentize",
                List.of(
                        Param.required("color"),
                        Param.required("amount")
                ),
                2,
                (context, args) -> transparentizeNamed(
                        context,
                        args,
                        "transparentize"
                )
        ));
        register(functions, BuiltInCallable.contextual(
                "fade-out",
                List.of(
                        Param.required("color"),
                        Param.required("amount")
                ),
                2,
                (context, args) -> transparentizeNamed(context, args, "fade-out")
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "adjust-color",
                List.of(Param.required("color")),
                "kwargs",
                1,
                BuiltInFunctions::globalAdjustColor
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "scale-color",
                List.of(Param.required("color")),
                "kwargs",
                1,
                BuiltInFunctions::globalScaleColor
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "change-color",
                List.of(Param.required("color")),
                "kwargs",
                1,
                BuiltInFunctions::globalChangeColor
        ));
        register(functions, BuiltInCallable.of(
                "ie-hex-str",
                List.of("color"),
                BuiltInFunctions::ieHexStr
        ));

        // Meta introspection functions that also exist as globals (dart-sass
        // registers them on _builtInFunctions with a meta deprecation warning).
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
                "get-mixin",
                List.of(
                        Param.required("name"),
                        Param.optional("module", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::metaGetMixin
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "call",
                List.of(Param.required("function")),
                "args",
                1,
                BuiltInFunctions::metaCall
        ));
        applyGlobalBuiltInDeprecations(functions);
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
        register(functions, BuiltInCallable.of(
                "abs",
                List.of("number"),
                BuiltInFunctions::moduleAbs
        ));
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
        moduleFunction(functions, global, "map-values", "values");
        // Nested path merge: merge($map1, $keys..., $map2).
        register(functions, BuiltInCallable.withRest(
                "merge",
                List.of("map1"),
                "args",
                BuiltInFunctions::moduleMapMerge
        ));
        register(functions, BuiltInCallable.withRest(
                "get",
                List.of("map", "key"),
                "keys",
                BuiltInFunctions::moduleMapGet
        ));
        // Nested path set: set($map, $keys..., $value).
        register(functions, BuiltInCallable.withRest(
                "set",
                List.of("map"),
                "args",
                BuiltInFunctions::moduleMapSet
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
        moduleFunction(functions, global, "str-insert", "insert");
        moduleFunction(functions, global, "to-upper-case", "to-upper-case");
        moduleFunction(functions, global, "to-lower-case", "to-lower-case");
        moduleFunction(functions, global, "unique-id", "unique-id");
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

    /// Returns the functions exported by the {@code sass:color} module.
    ///
    /// Color values use the CSS Color Level 4 multi-space model, including
    /// space conversion, channel updates with {@code $space}, Color 4
    /// {@code mix} interpolation methods, and gamut mapping.
    ///
    /// @return an immutable color function table
    public static @Unmodifiable Map<String, BuiltInCallable> colorModule() {
        var global = global();
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        register(functions, deprecatedColorChannelFunction(
                "red",
                ColorSpace.RGB,
                null,
                false
        ));
        register(functions, deprecatedColorChannelFunction(
                "green",
                ColorSpace.RGB,
                null,
                false
        ));
        register(functions, deprecatedColorChannelFunction(
                "blue",
                ColorSpace.RGB,
                null,
                false
        ));
        // Module alpha uses distinct diagnostics from the global Microsoft-filter form.
        register(functions, BuiltInCallable.contextualWithRest(
                "alpha",
                List.of(),
                "args",
                0,
                BuiltInFunctions::moduleAlphaChannel
        ));
        register(functions, BuiltInCallable.contextual(
                "opacity",
                List.of(Param.required("color")),
                1,
                BuiltInFunctions::moduleOpacity
        ));
        // color.hwb is the only multi-arg space constructor re-exported from sass:color.
        moduleFunction(functions, global, "hwb", "hwb");
        // Legacy global color functions are not re-exported from sass:color.
        register(functions, removedColorFunction("adjust-hue", "hue", false));
        register(functions, removedColorFunction("lighten", "lightness", false));
        register(functions, removedColorFunction("darken", "lightness", true));
        register(functions, removedColorFunction("saturate", "saturation", false));
        register(functions, removedColorFunction("desaturate", "saturation", true));
        register(functions, removedColorFunction("opacify", "alpha", false));
        register(functions, removedColorFunction("fade-in", "alpha", false));
        register(functions, removedColorFunction("transparentize", "alpha", true));
        register(functions, removedColorFunction("fade-out", "alpha", true));
        register(functions, BuiltInCallable.contextual(
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
        register(functions, BuiltInCallable.contextual(
                "invert",
                List.of(
                        Param.required("color"),
                        Param.optional("weight", SassNumber.of(100, "%")),
                        Param.optional("space", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::moduleColorInvert
        ));
        register(functions, deprecatedColorChannelFunction(
                "hue",
                ColorSpace.HSL,
                "deg",
                false
        ));
        register(functions, deprecatedColorChannelFunction(
                "saturation",
                ColorSpace.HSL,
                "%",
                false
        ));
        register(functions, deprecatedColorChannelFunction(
                "lightness",
                ColorSpace.HSL,
                "%",
                false
        ));
        register(functions, deprecatedColorChannelFunction(
                "whiteness",
                ColorSpace.HWB,
                "%",
                false
        ));
        register(functions, deprecatedColorChannelFunction(
                "blackness",
                ColorSpace.HWB,
                "%",
                false
        ));
        register(functions, BuiltInCallable.contextual(
                "grayscale",
                List.of(Param.required("color")),
                1,
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
        register(functions, BuiltInCallable.of(
                "space",
                List.of("color"),
                BuiltInFunctions::colorSpace
        ));
        register(functions, BuiltInCallable.of(
                "to-space",
                List.of("color", "space"),
                BuiltInFunctions::colorToSpace
        ));
        register(functions, BuiltInCallable.of(
                "is-legacy",
                List.of("color"),
                BuiltInFunctions::colorIsLegacy
        ));
        register(functions, BuiltInCallable.of(
                "is-missing",
                List.of("color", "channel"),
                BuiltInFunctions::colorIsMissing
        ));
        register(functions, BuiltInCallable.of(
                "is-in-gamut",
                List.of(
                        Param.required("color"),
                        Param.optional("space", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::colorIsInGamut
        ));
        register(functions, BuiltInCallable.of(
                "channel",
                List.of(
                        Param.required("color"),
                        Param.required("channel"),
                        Param.optional("space", SassNull.NULL)
                ),
                2,
                BuiltInFunctions::colorChannel
        ));
        register(functions, BuiltInCallable.of(
                "is-powerless",
                List.of(
                        Param.required("color"),
                        Param.required("channel"),
                        Param.optional("space", SassNull.NULL)
                ),
                2,
                BuiltInFunctions::colorIsPowerless
        ));
        register(functions, BuiltInCallable.of(
                "to-gamut",
                List.of(
                        Param.required("color"),
                        Param.optional("space", SassNull.NULL),
                        Param.optional("method", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::colorToGamut
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "adjust",
                List.of(Param.required("color")),
                "kwargs",
                1,
                BuiltInFunctions::colorAdjust
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "scale",
                List.of(Param.required("color")),
                "kwargs",
                1,
                BuiltInFunctions::colorScale
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "change",
                List.of(Param.required("color")),
                "kwargs",
                1,
                BuiltInFunctions::colorChange
        ));
        moduleFunction(functions, global, "ie-hex-str", "ie-hex-str");
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
        moduleFunction(functions, global, "feature-exists", "feature-exists");
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
        register(functions, BuiltInCallable.of(
                "calc-args",
                List.of("calc"),
                BuiltInFunctions::metaCalcArgs
        ));
        register(functions, BuiltInCallable.of(
                "calc-name",
                List.of("calc"),
                BuiltInFunctions::metaCalcName
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
        register(mixins, BuiltInCallable.contextualMixin(
                "load-css",
                List.of(
                        Param.required("url"),
                        Param.optional("with", SassNull.NULL)
                ),
                1,
                BuiltInFunctions::metaLoadCss
        ));
        return freeze(mixins);
    }

    /// Returns the selector functions exported by {@code sass:selector}.
    ///
    /// @return an immutable selector function table
    public static @Unmodifiable Map<String, BuiltInCallable> selectorModule() {
        return SelectorFunctions.module();
    }

    /// Registers one module selector function under a legacy global name.
    ///
    /// @param globals     the mutable global function table
    /// @param selector    the {@code sass:selector} function table
    /// @param moduleName  the module-local function name
    /// @param globalName  the legacy global function name
    private static void registerGlobalSelector(
            LinkedHashMap<String, BuiltInCallable> globals,
            Map<String, BuiltInCallable> selector,
            String moduleName,
            String globalName
    ) {
        @Nullable BuiltInCallable callable = selector.get(moduleName);
        if (callable == null) {
            throw new AssertionError("missing selector module function: " + moduleName);
        }
        register(globals, callable.withName(globalName));
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
        destination.put(
                moduleName,
                callable.withoutDeprecationWarning().withName(moduleName)
        );
    }

    /// Adds Dart Sass global-builtin replacement metadata to global aliases.
    ///
    /// Conditional aliases whose arguments may denote plain CSS functions are
    /// handled by their callbacks and are intentionally absent from this table.
    ///
    /// @param functions the complete mutable global function table
    private static void applyGlobalBuiltInDeprecations(
            LinkedHashMap<String, BuiltInCallable> functions
    ) {
        deprecateGlobal(functions, "red", "color", "red");
        deprecateGlobal(functions, "green", "color", "green");
        deprecateGlobal(functions, "blue", "color", "blue");
        deprecateGlobal(functions, "mix", "color", "mix");
        deprecateGlobal(functions, "hue", "color", "hue");
        deprecateGlobal(functions, "saturation", "color", "saturation");
        deprecateGlobal(functions, "lightness", "color", "lightness");
        deprecateGlobal(functions, "adjust-hue", "color", "adjust");
        deprecateGlobal(functions, "lighten", "color", "adjust");
        deprecateGlobal(functions, "darken", "color", "adjust");
        deprecateGlobal(functions, "desaturate", "color", "adjust");
        deprecateGlobal(functions, "opacify", "color", "adjust");
        deprecateGlobal(functions, "fade-in", "color", "adjust");
        deprecateGlobal(functions, "transparentize", "color", "adjust");
        deprecateGlobal(functions, "fade-out", "color", "adjust");
        deprecateGlobal(functions, "complement", "color", "complement");
        deprecateGlobal(functions, "adjust-color", "color", "adjust");
        deprecateGlobal(functions, "scale-color", "color", "scale");
        deprecateGlobal(functions, "change-color", "color", "change");

        deprecateGlobal(functions, "ceil", "math", "ceil");
        deprecateGlobal(functions, "floor", "math", "floor");
        deprecateGlobal(functions, "max", "math", "max");
        deprecateGlobal(functions, "min", "math", "min");
        deprecateGlobal(functions, "percentage", "math", "percentage");
        deprecateGlobal(functions, "random", "math", "random");
        deprecateGlobal(functions, "round", "math", "round");
        deprecateGlobal(functions, "unit", "math", "unit");
        deprecateGlobal(functions, "comparable", "math", "compatible");
        deprecateGlobal(functions, "unitless", "math", "is-unitless");

        deprecateGlobal(functions, "length", "list", "length");
        deprecateGlobal(functions, "nth", "list", "nth");
        deprecateGlobal(functions, "set-nth", "list", "set-nth");
        deprecateGlobal(functions, "join", "list", "join");
        deprecateGlobal(functions, "append", "list", "append");
        deprecateGlobal(functions, "zip", "list", "zip");
        deprecateGlobal(functions, "index", "list", "index");
        deprecateGlobal(functions, "is-bracketed", "list", "is-bracketed");
        deprecateGlobal(functions, "list-separator", "list", "separator");

        deprecateGlobal(functions, "map-get", "map", "get");
        deprecateGlobal(functions, "map-merge", "map", "merge");
        deprecateGlobal(functions, "map-remove", "map", "remove");
        deprecateGlobal(functions, "map-keys", "map", "keys");
        deprecateGlobal(functions, "map-values", "map", "values");
        deprecateGlobal(functions, "map-has-key", "map", "has-key");

        deprecateGlobal(functions, "quote", "string", "quote");
        deprecateGlobal(functions, "unquote", "string", "unquote");
        deprecateGlobal(functions, "to-upper-case", "string", "to-upper-case");
        deprecateGlobal(functions, "to-lower-case", "string", "to-lower-case");
        deprecateGlobal(functions, "unique-id", "string", "unique-id");
        deprecateGlobal(functions, "str-length", "string", "length");
        deprecateGlobal(functions, "str-insert", "string", "insert");
        deprecateGlobal(functions, "str-index", "string", "index");
        deprecateGlobal(functions, "str-slice", "string", "slice");

        deprecateGlobal(
                functions,
                "is-superselector",
                "selector",
                "is-superselector"
        );
        deprecateGlobal(
                functions,
                "simple-selectors",
                "selector",
                "simple-selectors"
        );
        deprecateGlobal(functions, "selector-parse", "selector", "parse");
        deprecateGlobal(functions, "selector-nest", "selector", "nest");
        deprecateGlobal(functions, "selector-append", "selector", "append");
        deprecateGlobal(functions, "selector-extend", "selector", "extend");
        deprecateGlobal(functions, "selector-replace", "selector", "replace");
        deprecateGlobal(functions, "selector-unify", "selector", "unify");

        deprecateGlobal(functions, "feature-exists", "meta", "feature-exists");
        deprecateGlobal(functions, "inspect", "meta", "inspect");
        deprecateGlobal(functions, "type-of", "meta", "type-of");
        deprecateGlobal(functions, "keywords", "meta", "keywords");
        deprecateGlobal(functions, "content-exists", "meta", "content-exists");
        deprecateGlobal(functions, "variable-exists", "meta", "variable-exists");
        deprecateGlobal(
                functions,
                "global-variable-exists",
                "meta",
                "global-variable-exists"
        );
        deprecateGlobal(functions, "function-exists", "meta", "function-exists");
        deprecateGlobal(functions, "mixin-exists", "meta", "mixin-exists");
        deprecateGlobal(functions, "get-function", "meta", "get-function");
        deprecateGlobal(functions, "get-mixin", "meta", "get-mixin");
        deprecateGlobal(functions, "call", "meta", "call");
    }

    /// Attaches one module replacement to a registered global function.
    ///
    /// @param functions the mutable global function table
    /// @param globalName the registered global name
    /// @param module the replacement module namespace
    /// @param moduleName the replacement function name
    private static void deprecateGlobal(
            LinkedHashMap<String, BuiltInCallable> functions,
            String globalName,
            String module,
            String moduleName
    ) {
        @Nullable var callable = functions.get(globalName);
        if (callable == null) {
            throw new AssertionError(
                    "missing global function for deprecation: " + globalName
            );
        }
        functions.put(
                globalName,
                callable.withDeprecationWarning(module, moduleName)
        );
    }

    /// Reports one conditional deprecated global built-in invocation.
    ///
    /// @param context the invocation receiving the diagnostic
    /// @param module the replacement module namespace
    /// @param name the replacement function name
    private static void warnGlobalBuiltIn(
            BuiltInCallable.Context context,
            String module,
            String name
    ) {
        context.deprecate(
                "Global built-in functions are deprecated and will be removed "
                        + "in Dart Sass 3.0.0.\n"
                        + "Use " + module + "." + name + " instead.\n\n"
                        + "More info and automated migrator: "
                        + "https://sass-lang.com/d/import",
                "global-builtin"
        );
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

    /// Dispatches {@code rgb(...)} overloads from a rest argument list.
    private static SassValue rgbRest(List<SassValue> args) {
        return rgbOrRgba("rgb", restArgumentList(args));
    }

    /// Dispatches {@code rgba(...)} overloads from a rest argument list.
    private static SassValue rgbaRest(List<SassValue> args) {
        return rgbOrRgba("rgba", restArgumentList(args));
    }

    /// Implements rgb/rgba positional and named overload selection.
    private static SassValue rgbOrRgba(String name, SassArgumentList rest) {
        var values = new ArrayList<>(rest.asList());
        var keywords = new LinkedHashMap<>(rest.keywords());
        if (values.isEmpty() && keywords.containsKey("color")) {
            @Nullable SassValue color = keywords.remove("color");
            @Nullable SassValue alphaArg = keywords.remove("alpha");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            if (color == null) {
                throw new SassValueException("Missing argument $color.");
            }
            if (alphaArg == null) {
                throw new SassValueException("Missing argument $alpha.");
            }
            return rgbTwoArg(name, color, alphaArg);
        }
        if (keywords.containsKey("channels")) {
            @Nullable SassValue channels = keywords.remove("channels");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            if (!values.isEmpty()) {
                throw new SassValueException(
                        "Only 1 argument allowed, but " + (1 + values.size()) + " were passed."
                );
            }
            return CssColorChannels.parseFixedSpace(
                    name,
                    Objects.requireNonNull(channels),
                    ColorSpace.RGB
            );
        }
        if (keywords.containsKey("red") || keywords.containsKey("green")
                || keywords.containsKey("blue")) {
            @Nullable SassValue red = takeNamedOrPositional(values, keywords, "red");
            @Nullable SassValue green = takeNamedOrPositional(values, keywords, "green");
            @Nullable SassValue blue = takeNamedOrPositional(values, keywords, "blue");
            @Nullable SassValue alphaArg = takeNamedOrPositional(values, keywords, "alpha");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            if (!values.isEmpty()) {
                throw new SassValueException(
                        "Only 4 arguments allowed, but "
                                + (4 + values.size()) + " were passed."
                );
            }
            return rgbChannels(name, red, green, blue, alphaArg);
        }
        if (!keywords.isEmpty() && !keywords.containsKey("alpha") && values.size() <= 1) {
            // Single channels list may carry a named alpha.
        }
        if (keywords.containsKey("alpha") && values.size() == 1) {
            @Nullable SassValue alphaArg = keywords.remove("alpha");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            return rgbTwoArg(name, values.get(0), Objects.requireNonNull(alphaArg));
        }
        if (!keywords.isEmpty()) {
            // Allow trailing named alpha for the three-channel form.
            if (keywords.size() == 1 && keywords.containsKey("alpha") && values.size() == 3) {
                return rgbChannels(
                        name,
                        values.get(0),
                        values.get(1),
                        values.get(2),
                        keywords.get("alpha")
                );
            }
            throw new SassValueException(
                    "No argument named $" + keywords.keySet().iterator().next() + "."
            );
        }
        if (values.isEmpty()) {
            // dart-sass's first rgb overload is {@code rgb($channels)}.
            throw new SassValueException("Missing argument $channels.");
        }
        if (values.size() == 1) {
            return CssColorChannels.parseFixedSpace(name, values.get(0), ColorSpace.RGB);
        }
        if (values.size() == 2) {
            return rgbTwoArg(name, values.get(0), values.get(1));
        }
        if (values.size() == 3) {
            return rgbChannels(name, values.get(0), values.get(1), values.get(2), null);
        }
        if (values.size() == 4) {
            return rgbChannels(
                    name,
                    values.get(0),
                    values.get(1),
                    values.get(2),
                    values.get(3)
            );
        }
        throw new SassValueException(
                "Only 4 arguments allowed, but " + values.size() + " were passed."
        );
    }

    /// Builds an RGB color from three channel values and optional alpha.
    private static SassValue rgbChannels(
            String name,
            @Nullable SassValue red,
            @Nullable SassValue green,
            @Nullable SassValue blue,
            @Nullable SassValue alphaArg
    ) {
        if (red == null) {
            throw new SassValueException("Missing argument $red.");
        }
        if (green == null) {
            throw new SassValueException("Missing argument $green.");
        }
        if (blue == null) {
            throw new SassValueException("Missing argument $blue.");
        }
        if (red.isSpecialNumber()
                || green.isSpecialNumber()
                || blue.isSpecialNumber()
                || (alphaArg != null && alphaArg.isSpecialNumber())) {
            var specialArgs = new ArrayList<SassValue>();
            specialArgs.add(red);
            specialArgs.add(green);
            specialArgs.add(blue);
            if (alphaArg != null) {
                specialArgs.add(alphaArg);
            }
            return CssColorChannels.functionString(name, specialArgs);
        }
        // Legacy rgb() clamps channels and alpha like CSS Color 3.
        return SassColor.rgb(
                clampLikeCss(namedChannel(red, "red"), 0.0, 255.0),
                clampLikeCss(namedChannel(green, "green"), 0.0, 255.0),
                clampLikeCss(namedChannel(blue, "blue"), 0.0, 255.0),
                alphaArg != null ? namedAlpha(alphaArg) : 1.0,
                RgbFunctionColorFormat.INSTANCE
        );
    }

    /// Reads an RGB channel while preserving the parameter name in diagnostics.
    ///
    /// Unitless values are used as-is. Percentage values map {@code 0%..100%} onto
    /// {@code 0..255}, matching dart-sass multi-argument {@code rgb()}.
    private static double namedChannel(SassValue value, String name) {
        try {
            var number = value.assertNumber();
            if (number.isUnitless()) {
                return number.value();
            }
            if (number.numeratorUnits().equals(List.of("%"))
                    && number.denominatorUnits().isEmpty()) {
                return number.value() * 255.0 / 100.0;
            }
            throw new SassValueException(
                    "Expected " + number + " to have unit \"%\" or no units."
            );
        } catch (SassValueException exception) {
            String message = Objects.requireNonNull(exception.getMessage(), "channel message");
            if (message.startsWith("$")) {
                throw exception;
            }
            throw new SassValueException("$" + name + ": " + message);
        }
    }

    /// Reads an alpha value while preserving the parameter name in diagnostics.
    private static double namedAlpha(SassValue value) {
        try {
            return alpha(value);
        } catch (SassValueException exception) {
            String message = Objects.requireNonNull(exception.getMessage(), "alpha message");
            if (message.startsWith("$")) {
                throw exception;
            }
            throw new SassValueException("$alpha: " + message);
        }
    }

    /// Reads one named keyword or the next positional value.
    private static @Nullable SassValue takeNamedOrPositional(
            ArrayList<SassValue> values,
            LinkedHashMap<String, SassValue> keywords,
            String name
    ) {
        if (keywords.containsKey(name)) {
            return keywords.remove(name);
        }
        if (!values.isEmpty()) {
            return values.remove(0);
        }
        return null;
    }

    /// Implements the two-argument {@code rgb($color, $alpha)} / {@code rgba(...)} form.
    private static SassValue rgbTwoArg(String name, SassValue colorOrSpecial, SassValue alphaValue) {
        if (colorOrSpecial.isSpecialVariable()
                || (!(colorOrSpecial instanceof SassColor) && alphaValue.isSpecialVariable())) {
            return CssColorChannels.functionString(name, List.of(colorOrSpecial, alphaValue));
        }
        if (alphaValue.isSpecialNumber()) {
            // Known colors expand to channel numbers so forms such as
            // {@code rgb(blue, calc(0.4))} become {@code rgb(0, 0, 255, calc(0.4))}.
            // Special color operands keep their original spelling.
            if (colorOrSpecial instanceof SassColor color) {
                var rgb = color.toSpace(ColorSpace.RGB, false);
                return CssColorChannels.functionString(
                        name,
                        List.of(
                                SassNumber.of(rgb.channel0(), null),
                                SassNumber.of(rgb.channel1(), null),
                                SassNumber.of(rgb.channel2(), null),
                                alphaValue
                        )
                );
            }
            return CssColorChannels.functionString(name, List.of(colorOrSpecial, alphaValue));
        }
        var color = colorArgument(colorOrSpecial, "color");
        return color.changeAlpha(namedAlpha(alphaValue));
    }

    /// Parses the CSS {@code color()} constructor.
    private static SassValue cssColor(List<SassValue> args) {
        return CssColorChannels.parseColorDescription(args.get(0));
    }

    /// Dispatches {@code hwb(...)} overloads from a rest argument list.
    private static SassValue hwbRest(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return hwbOrChannels(context, restArgumentList(args));
    }

    /// Implements hwb positional and named overload selection.
    private static SassValue hwbOrChannels(
            BuiltInCallable.Context context,
            SassArgumentList rest
    ) {
        var values = new ArrayList<>(rest.asList());
        var keywords = new LinkedHashMap<>(rest.keywords());
        if (keywords.containsKey("channels")) {
            @Nullable SassValue channels = keywords.remove("channels");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            if (!values.isEmpty()) {
                throw new SassValueException(
                        "Only 1 argument allowed, but " + (1 + values.size()) + " were passed."
                );
            }
            return CssColorChannels.parseFixedSpace(
                    context,
                    "hwb",
                    Objects.requireNonNull(channels),
                    ColorSpace.HWB
            );
        }
        if (keywords.containsKey("hue")
                || keywords.containsKey("whiteness")
                || keywords.containsKey("blackness")) {
            @Nullable SassValue hue = takeNamedOrPositional(values, keywords, "hue");
            @Nullable SassValue whiteness = takeNamedOrPositional(values, keywords, "whiteness");
            @Nullable SassValue blackness = takeNamedOrPositional(values, keywords, "blackness");
            @Nullable SassValue alphaArg = takeNamedOrPositional(values, keywords, "alpha");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            if (!values.isEmpty()) {
                throw new SassValueException(
                        "Only 4 arguments allowed, but "
                                + (4 + values.size()) + " were passed."
                );
            }
            return hwbChannels(
                    context,
                    hue,
                    whiteness,
                    blackness,
                    alphaArg
            );
        }
        if (!keywords.isEmpty()) {
            if (keywords.size() == 1 && keywords.containsKey("alpha") && values.size() == 3) {
                return hwbChannels(
                        context,
                        values.get(0),
                        values.get(1),
                        values.get(2),
                        keywords.get("alpha")
                );
            }
            throw new SassValueException(
                    "No argument named $" + keywords.keySet().iterator().next() + "."
            );
        }
        if (values.isEmpty()) {
            throw new SassValueException("Missing argument $channels.");
        }
        if (values.size() == 1) {
            return CssColorChannels.parseFixedSpace(
                    context,
                    "hwb",
                    values.get(0),
                    ColorSpace.HWB
            );
        }
        if (values.size() == 2) {
            // dart-sass rejects two-arg hwb() with a fixed-arity message.
            throw new SassValueException(
                    "Only 1 argument allowed, but 2 were passed."
            );
        }
        if (values.size() == 3) {
            return hwbChannels(
                    context,
                    values.get(0),
                    values.get(1),
                    values.get(2),
                    null
            );
        }
        if (values.size() == 4) {
            return hwbChannels(
                    context,
                    values.get(0),
                    values.get(1),
                    values.get(2),
                    values.get(3)
            );
        }
        throw new SassValueException(
                "Only 4 arguments allowed, but " + values.size() + " were passed."
        );
    }

    /// Builds an HWB color from individual channel arguments.
    private static SassValue hwbChannels(
            BuiltInCallable.Context context,
            @Nullable SassValue hueValue,
            @Nullable SassValue whitenessValue,
            @Nullable SassValue blacknessValue,
            @Nullable SassValue alphaArg
    ) {
        if (hueValue == null) {
            throw new SassValueException("Missing argument $hue.");
        }
        if (whitenessValue == null) {
            throw new SassValueException("Missing argument $whiteness.");
        }
        if (blacknessValue == null) {
            throw new SassValueException("Missing argument $blackness.");
        }
        if (hueValue.isSpecialNumber()
                || whitenessValue.isSpecialNumber()
                || blacknessValue.isSpecialNumber()
                || (alphaArg != null && alphaArg.isSpecialNumber())) {
            var builder = new StringBuilder("hwb(")
                    .append(hueValue.toCssString()).append(' ')
                    .append(whitenessValue.toCssString()).append(' ')
                    .append(blacknessValue.toCssString());
            if (alphaArg != null) {
                builder.append(" / ").append(alphaArg.toCssString());
            }
            return new SassString(builder.append(')').toString(), false);
        }
        double hue = angleValue(
                context,
                channelTypeNumber(hueValue, "hue"),
                "hue"
        );
        SassNumber whitenessNumber = channelTypeNumber(whitenessValue, "whiteness");
        SassNumber blacknessNumber = channelTypeNumber(blacknessValue, "blackness");
        assertPercentUnit(whitenessNumber, "whiteness");
        assertPercentUnit(blacknessNumber, "blackness");
        double whiteness = whitenessNumber.value();
        double blackness = blacknessNumber.value();
        if (whiteness + blackness > 100.0) {
            var sum = whiteness + blackness;
            whiteness = whiteness / sum * 100.0;
            blackness = blackness / sum * 100.0;
        }
        double alphaValue = alphaArg != null ? alpha(alphaArg) : 1.0;
        return SassColor.hwb(hue, whiteness, blackness, alphaValue);
    }

    /// Asserts a multi-arg color channel is a number with dart-sass diagnostics.
    private static SassNumber channelTypeNumber(SassValue value, String channel) {
        if (value instanceof SassNumber number) {
            return number;
        }
        throw new SassValueException(
                "Expected " + channel + " channel to be a number, was " + value + "."
        );
    }

    /// Parses the CSS {@code lab()} constructor.
    private static SassValue cssLab(List<SassValue> args) {
        return CssColorChannels.parseFixedSpace("lab", args.get(0), ColorSpace.LAB);
    }

    /// Parses the CSS {@code lch()} constructor.
    private static SassValue cssLch(List<SassValue> args) {
        return CssColorChannels.parseFixedSpace("lch", args.get(0), ColorSpace.LCH);
    }

    /// Parses the CSS {@code oklab()} constructor.
    private static SassValue cssOklab(List<SassValue> args) {
        return CssColorChannels.parseFixedSpace("oklab", args.get(0), ColorSpace.OKLAB);
    }

    /// Parses the CSS {@code oklch()} constructor.
    private static SassValue cssOklch(List<SassValue> args) {
        return CssColorChannels.parseFixedSpace("oklch", args.get(0), ColorSpace.OKLCH);
    }

    /// Dispatches {@code hsl(...)} overloads from a rest argument list.
    private static SassValue hslRest(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return hslOrHsla(context, "hsl", restArgumentList(args));
    }

    /// Dispatches {@code hsla(...)} overloads from a rest argument list.
    private static SassValue hslaRest(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return hslOrHsla(context, "hsla", restArgumentList(args));
    }

    /// Implements hsl/hsla positional and named overload selection.
    private static SassValue hslOrHsla(
            BuiltInCallable.Context context,
            String name,
            SassArgumentList rest
    ) {
        var values = new ArrayList<>(rest.asList());
        var keywords = new LinkedHashMap<>(rest.keywords());
        if (keywords.containsKey("channels")) {
            @Nullable SassValue channels = keywords.remove("channels");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            if (!values.isEmpty()) {
                throw new SassValueException(
                        "Only 1 argument allowed, but " + (1 + values.size()) + " were passed."
                );
            }
            return CssColorChannels.parseFixedSpace(
                    context,
                    name,
                    Objects.requireNonNull(channels),
                    ColorSpace.HSL
            );
        }
        if (keywords.containsKey("hue")
                || keywords.containsKey("saturation")
                || keywords.containsKey("lightness")) {
            @Nullable SassValue hue = takeNamedOrPositional(values, keywords, "hue");
            @Nullable SassValue saturation = takeNamedOrPositional(values, keywords, "saturation");
            @Nullable SassValue lightness = takeNamedOrPositional(values, keywords, "lightness");
            @Nullable SassValue alphaArg = takeNamedOrPositional(values, keywords, "alpha");
            if (!keywords.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
            if (!values.isEmpty()) {
                throw new SassValueException(
                        "Only 4 arguments allowed, but "
                                + (4 + values.size()) + " were passed."
                );
            }
            return hslChannels(
                    context,
                    name,
                    hue,
                    saturation,
                    lightness,
                    alphaArg
            );
        }
        if (!keywords.isEmpty()) {
            if (keywords.size() == 1 && keywords.containsKey("alpha") && values.size() == 3) {
                return hslChannels(
                        context,
                        name,
                        values.get(0),
                        values.get(1),
                        values.get(2),
                        keywords.get("alpha")
                );
            }
            if (keywords.size() == 1 && keywords.containsKey("alpha") && values.size() == 1) {
                // Single channels list may carry a named alpha via modern form;
                // fall through is not valid for hsl — reject unknown alone.
            }
            throw new SassValueException(
                    "No argument named $" + keywords.keySet().iterator().next() + "."
            );
        }
        if (values.isEmpty()) {
            throw new SassValueException("Missing argument $channels.");
        }
        if (values.size() == 1) {
            return CssColorChannels.parseFixedSpace(
                    context,
                    name,
                    values.get(0),
                    ColorSpace.HSL
            );
        }
        if (values.size() == 2) {
            // hsl(123, var(--foo)) is valid CSS because --foo might expand.
            if (values.get(0).isSpecialVariable() || values.get(1).isSpecialVariable()) {
                return CssColorChannels.functionString(name, values);
            }
            throw new SassValueException("Missing argument $lightness.");
        }
        if (values.size() == 3) {
            return hslChannels(
                    context,
                    name,
                    values.get(0),
                    values.get(1),
                    values.get(2),
                    null
            );
        }
        if (values.size() == 4) {
            return hslChannels(
                    context,
                    name,
                    values.get(0),
                    values.get(1),
                    values.get(2),
                    values.get(3)
            );
        }
        throw new SassValueException(
                "Only 4 arguments allowed, but " + values.size() + " were passed."
        );
    }

    /// Builds an HSL color from individual channel arguments.
    private static SassValue hslChannels(
            BuiltInCallable.Context context,
            String name,
            @Nullable SassValue hueValue,
            @Nullable SassValue saturationValue,
            @Nullable SassValue lightnessValue,
            @Nullable SassValue alphaArg
    ) {
        if (hueValue == null) {
            throw new SassValueException("Missing argument $hue.");
        }
        if (saturationValue == null) {
            throw new SassValueException("Missing argument $saturation.");
        }
        if (lightnessValue == null) {
            throw new SassValueException("Missing argument $lightness.");
        }
        if (hueValue.isSpecialNumber()
                || saturationValue.isSpecialNumber()
                || lightnessValue.isSpecialNumber()
                || (alphaArg != null && alphaArg.isSpecialNumber())) {
            var specialArgs = new ArrayList<SassValue>();
            specialArgs.add(hueValue);
            specialArgs.add(saturationValue);
            specialArgs.add(lightnessValue);
            if (alphaArg != null) {
                specialArgs.add(alphaArg);
            }
            return CssColorChannels.functionString(name, specialArgs);
        }
        double hue = angleValue(
                context,
                numberArgument(hueValue, "hue"),
                "hue"
        );
        var saturationNumber = numberArgument(
                saturationValue,
                "saturation"
        );
        checkPercent(context, saturationNumber, "saturation");
        // Saturation is lower-clamped so negative values become 0% rather than
        // inverting the hue via forSpace preprocessing.
        double saturation = Math.max(
                0.0,
                percentageOrUnitlessChannel(
                        saturationNumber,
                        "saturation"
                )
        );
        if (Double.isNaN(saturation)) {
            saturation = 0.0;
        }
        var lightnessNumber = numberArgument(lightnessValue, "lightness");
        checkPercent(context, lightnessNumber, "lightness");
        double lightness = percentageOrUnitlessChannel(
                lightnessNumber,
                "lightness"
        );
        double alphaValue = alphaArg != null ? alpha(alphaArg) : 1.0;
        return SassColor.hsl(hue, saturation, lightness, alphaValue);
    }

    /// Reads a percent-scale channel, accepting unknown units as bare magnitudes
    /// during the function-units deprecation period.
    private static double percentageOrUnitlessChannel(SassNumber number, String name) {
        // Percent, unitless, and legacy non-percent units all use the raw magnitude.
        return number.value();
    }

    /// Requires a number to have unit {@code %}.
    private static void assertPercentUnit(SassNumber number, String name) {
        if (number.numeratorUnits().equals(List.of("%")) && number.denominatorUnits().isEmpty()) {
            return;
        }
        throw new SassValueException(
                "$" + name + ": Expected " + number + " to have unit \"%\"."
        );
    }

    /// Reads a unitless numeric legacy color channel.
    ///
    /// @param value the channel value
    /// @return its magnitude
    private static double channel(SassValue value) {
        return value.assertNumber().assertNoUnits().value();
    }

    /// Parses a constructor alpha as unitless or {@code %}, clamping into {@code 0..1}.
    ///
    /// Matches dart-sass {@code clampLikeCss(_percentageOrUnitless(...), 0, 1)} so
    /// out-of-range and non-finite alphas are clamped rather than rejected.
    private static double alpha(SassValue value) {
        var number = value.assertNumber();
        double raw;
        if (number.isUnitless()) {
            raw = number.value();
        } else if (number.numeratorUnits().equals(List.of("%"))
                && number.denominatorUnits().isEmpty()) {
            raw = number.value() / 100.0;
        } else {
            throw new SassValueException(
                    "$alpha: Expected " + number + " to have unit \"%\" or no units."
            );
        }
        return clampLikeCss(raw, 0.0, 1.0);
    }

    /// Returns a quoted copy of a string argument.
    private static SassValue quote(List<SassValue> args) {
        var string = stringArgument(args.get(0), "string");
        return new SassString(string.text(), true);
    }

    /// Returns an unquoted copy of a string argument.
    private static SassValue unquote(List<SassValue> args) {
        var string = stringArgument(args.get(0), "string");
        return new SassString(string.text(), false);
    }

    /// Returns the list-view length of one value.
    private static SassValue length(List<SassValue> args) {
        return SassNumber.of(args.get(0).lengthAsList(), null);
    }

    /// Returns the value at a Sass one-based or negative list index.
    private static SassValue nth(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var list = args.get(0);
        deprecateUnitfulIndex(context, args.get(1), "n");
        var index = list.sassIndexToListIndex(args.get(1), list.lengthAsList());
        return list.asList().get(index);
    }

    /// Joins two list views using a selected separator.
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

    /// Appends one value while retaining the input list's bracket state.
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

    /// Selects the first explicit input separator or defaults to a space.
    private static ListSeparator defaultSeparator(
            SassValue left,
            SassValue right
    ) {
        if (left.separator() != ListSeparator.UNDECIDED) {
            return left.separator();
        }
        if (right.separator() != ListSeparator.UNDECIDED) {
            return right.separator();
        }
        return ListSeparator.SPACE;
    }

    /// Parses a list separator argument, including the `auto` selector.
    private static ListSeparator separatorArgument(
            SassValue value,
            SassValue left,
            SassValue right
    ) {
        String text;
        try {
            text = value.assertString().text();
        } catch (SassValueException exception) {
            throw prefixParameterException("separator", exception);
        }
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

    /// Known historical Sass language features reported by {@code feature-exists()}.
    private static final @Unmodifiable Set<String> KNOWN_FEATURES = Set.of(
            "global-variable-shadowing",
            "extend-selector-pseudoclass",
            "units-level-3",
            "at-error",
            "custom-property"
    );

    /// Returns whether a historical Sass language feature is available.
    ///
    /// @param args the feature name
    /// @return whether the feature is known
    private static SassValue featureExists(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        context.deprecate(
                "The feature-exists() function is deprecated.\n\n"
                        + "More info: https://sass-lang.com/d/feature-exists",
                "feature-exists"
        );
        if (!(args.get(0) instanceof SassString feature)) {
            throw new SassValueException("$feature: " + args.get(0) + " is not a string.");
        }
        return SassBoolean.of(KNOWN_FEATURES.contains(feature.text()));
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
        } else if (value instanceof SassCalculation) {
            type = "calculation";
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

    /// Implements the eager global {@code if($condition, $if-true, $if-false)}.
    ///
    /// Used when {@code if} is invoked through a first-class function reference.
    /// Direct {@code if()} expressions keep short-circuit evaluation in the AST.
    ///
    /// @param args the condition and both branch values
    /// @return {@code $if-true} when the condition is truthy, otherwise {@code $if-false}
    private static SassValue legacyIf(List<SassValue> args) {
        return args.get(0).isTruthy() ? args.get(1) : args.get(2);
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
            // Dart Sass omits the trailing period on this diagnostic.
            throw new SassValueException("Function not found: " + nameArgument);
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
            // Dart Sass omits the trailing period on this diagnostic.
            throw new SassValueException("Mixin not found: " + nameArgument);
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

    /// Loads a stylesheet and injects its CSS without exposing module members.
    ///
    /// @param context the active built-in invocation context
    /// @param args    the URL and optional configuration map
    /// @return Sass null after CSS injection
    private static SassValue metaLoadCss(
            BuiltInCallable.Context context,
            @Unmodifiable List<SassValue> args
    ) {
        var url = metaStringArgument(args.get(0), "url");
        var withArgument = args.get(1);
        var configured = !(withArgument instanceof SassNull);
        var configuration = configured
                ? configurationFromWithMap(context, withArgument)
                : ModuleConfiguration.empty();
        context.loadCss(url, configuration, configured);
        return SassNull.NULL;
    }

    /// Converts a {@code $with} map into an explicit module configuration.
    ///
    /// @param context the invocation receiving private-configuration diagnostics
    /// @param value the configuration map
    /// @return the explicit configuration, or empty when the map has no entries
    private static ModuleConfiguration configurationFromWithMap(
            BuiltInCallable.Context context,
            SassValue value
    ) {
        @Nullable SassMap map = value.tryMap();
        if (map == null) {
            throw new SassValueException("$with: " + value + " is not a map.");
        }
        if (map.contents().isEmpty()) {
            return ModuleConfiguration.empty();
        }
        var span = context.span();
        var values = new LinkedHashMap<String, ConfiguredValue>();
        var reportedPrivate = false;
        for (var entry : map.contents().entrySet()) {
            if (!(entry.getKey() instanceof SassString key)) {
                // dart-sass: "$with key: 1 is not a string."
                throw new SassValueException(
                        "$with key: " + entry.getKey() + " is not a string."
                );
            }
            var name = key.text().replace('_', '-');
            if (name.isEmpty()) {
                throw new SassValueException("$with: \"\" is not a valid variable name.");
            }
            if (name.startsWith("-") && !reportedPrivate) {
                reportedPrivate = true;
                context.deprecate(
                        "Configuring private variables (such as $" + name
                                + ") is deprecated.\n"
                                + "This will be an error in Dart Sass 2.0.0.",
                        "with-private"
                );
            }
            var configured = new ConfiguredValue(entry.getValue(), span, span);
            @Nullable ConfiguredValue previous = values.put(name, configured);
            if (previous != null) {
                throw new SassValueException(
                        "The variable $" + name + " was configured twice."
                );
            }
        }
        return ModuleConfiguration.explicit(values);
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

    /// Returns the arguments of a calculation as a comma-separated list.
    ///
    /// Numbers and nested calculations are preserved as Sass values. Operations
    /// and other non-value calculation operands serialize as unquoted strings,
    /// matching dart-sass {@code meta.calc-args()}.
    ///
    /// @param args the one calculation argument
    /// @return a comma-separated argument list
    private static SassValue metaCalcArgs(List<SassValue> args) {
        var calculation = calculationArgument(args.get(0), "calc");
        var contents = new ArrayList<SassValue>(calculation.arguments().size());
        for (var argument : calculation.arguments()) {
            contents.add(calculationArgumentAsValue(argument));
        }
        return new SassList(contents, ListSeparator.COMMA, false);
    }

    /// Returns the lowercase name of a calculation as a quoted string.
    ///
    /// @param args the one calculation argument
    /// @return the quoted calculation name
    private static SassValue metaCalcName(List<SassValue> args) {
        var calculation = calculationArgument(args.get(0), "calc");
        return new SassString(calculation.name(), true);
    }

    /// Returns a calculation argument while identifying its Sass parameter.
    ///
    /// @param value the supplied argument
    /// @param name  the parameter name without a dollar sign
    /// @return the calculation
    private static SassCalculation calculationArgument(SassValue value, String name) {
        try {
            return value.assertCalculation();
        } catch (SassValueException exception) {
            throw prefixParameterException(name, exception);
        }
    }

    /// Converts one calculation-tree operand into a Sass value for introspection.
    ///
    /// @param argument a calculation argument object
    /// @return the corresponding Sass value
    private static SassValue calculationArgumentAsValue(Object argument) {
        if (argument instanceof SassValue value) {
            return value;
        }
        if (argument instanceof CalculationOperation operation) {
            return new SassString(operation.toCssString(), false);
        }
        return new SassString(String.valueOf(argument), false);
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

    /// Returns the serialized units of one number as a quoted string.
    private static SassValue unit(List<SassValue> args) {
        return new SassString(numberArgument(args.get(0), "number").unitString(), true);
    }

    /// Returns whether two numbers can be compared after unit conversion.
    private static SassValue comparable(List<SassValue> args) {
        return SassBoolean.of(numberArgument(args.get(0), "number1")
                .isComparableTo(numberArgument(args.get(1), "number2")));
    }

    /// Converts one unitless number to a percentage.
    private static SassValue percentage(List<SassValue> args) {
        var number = numberArgument(args.get(0), "number");
        try {
            number = number.assertNoUnits();
        } catch (SassValueException exception) {
            throw prefixParameterException("number", exception);
        }
        return SassNumber.of(number.value() * 100.0, "%");
    }

    /// Divides two values for the {@code sass:math} module.
    ///
    /// Non-number operands currently produce a slash-separated unquoted string
    /// (matching dart-sass until a future release restricts {@code math.div} to
    /// numbers only). Callers should migrate non-numeric cases to
    /// {@code list.slash()}.
    ///
    /// @param args the bound dividend and divisor arguments
    /// @return the unit-aware quotient, or a slash-separated CSS string
    private static SassValue div(List<SassValue> args) {
        return args.get(0).dividedBy(args.get(1));
    }

    /// Reports whether one number has no numerator or denominator units.
    ///
    /// @param args the bound number argument
    /// @return a Sass boolean describing unitlessness
    private static SassValue isUnitless(List<SassValue> args) {
        return SassBoolean.of(numberArgument(args.get(0), "number").isUnitless());
    }

    /// Clamps {@code number} between compatible {@code min} and {@code max} bounds.
    ///
    /// @param args the bound min, number, and max arguments
    /// @return the clamped number
    private static SassValue clamp(List<SassValue> args) {
        var min = numberArgument(args.get(0), "min");
        var number = numberArgument(args.get(1), "number");
        var max = numberArgument(args.get(2), "max");
        // Pairwise checks match dart-sass message order and unitless rules.
        assertMathUnitsCompatible(min, "min", number, "number");
        assertMathUnitsCompatible(min, "min", max, "max");
        assertMathUnitsCompatible(number, "number", max, "max");
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
        // Type errors omit the rest-parameter index; unit errors include it.
        var first = numbers.get(0).assertNumber();
        var subtotal = 0.0;
        for (var index = 0; index < numbers.size(); index++) {
            var number = numbers.get(index).assertNumber();
            if (index > 0) {
                assertMathUnitsCompatible(
                        first,
                        "numbers[1]",
                        number,
                        "numbers[" + (index + 1) + "]"
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
        var number = unitlessNumberArgument(args.get(0), "number");
        if (args.get(1) instanceof SassNull) {
            return SassNumber.of(Math.log(number.value()), null);
        }
        var base = unitlessNumberArgument(args.get(1), "base");
        return SassNumber.of(Math.log(number.value()) / Math.log(base.value()), null);
    }

    /// Raises a unitless base to a unitless exponent.
    ///
    /// @param args the bound base and exponent
    /// @return the unitless power
    private static SassValue pow(List<SassValue> args) {
        var base = unitlessNumberArgument(args.get(0), "base");
        var exponent = unitlessNumberArgument(args.get(1), "exponent");
        return SassNumber.of(powValue(base.value(), exponent.value()), null);
    }

    /// Returns the square root of a unitless number.
    ///
    /// @param args the bound number
    /// @return the unitless square root
    private static SassValue sqrt(List<SassValue> args) {
        var number = unitlessNumberArgument(args.get(0), "number");
        return SassNumber.of(Math.sqrt(number.value()), null);
    }

    /// Returns the sine of an angle coerced to radians.
    ///
    /// @param args the bound angle
    /// @return the unitless sine
    private static SassValue sin(List<SassValue> args) {
        return SassNumber.of(Math.sin(radians(numberArgument(args.get(0), "number"))), null);
    }

    /// Returns the cosine of an angle coerced to radians.
    ///
    /// @param args the bound angle
    /// @return the unitless cosine
    private static SassValue cos(List<SassValue> args) {
        return SassNumber.of(Math.cos(radians(numberArgument(args.get(0), "number"))), null);
    }

    /// Returns the tangent of an angle coerced to radians.
    ///
    /// @param args the bound angle
    /// @return the unitless tangent
    private static SassValue tan(List<SassValue> args) {
        return SassNumber.of(Math.tan(radians(numberArgument(args.get(0), "number"))), null);
    }

    /// Returns the arcsine of a unitless number in degrees.
    ///
    /// @param args the bound number
    /// @return the degree angle
    private static SassValue asin(List<SassValue> args) {
        return degrees(Math.asin(unitlessNumberArgument(args.get(0), "number").value()));
    }

    /// Returns the arccosine of a unitless number in degrees.
    ///
    /// @param args the bound number
    /// @return the degree angle
    private static SassValue acos(List<SassValue> args) {
        return degrees(Math.acos(unitlessNumberArgument(args.get(0), "number").value()));
    }

    /// Returns the arctangent of a unitless number in degrees.
    ///
    /// @param args the bound number
    /// @return the degree angle
    private static SassValue atan(List<SassValue> args) {
        return degrees(Math.atan(unitlessNumberArgument(args.get(0), "number").value()));
    }

    /// Returns the two-argument arctangent of compatible coordinates in degrees.
    ///
    /// @param args the bound y and x coordinates
    /// @return the degree angle
    private static SassValue atan2(List<SassValue> args) {
        var y = numberArgument(args.get(0), "y");
        var x = numberArgument(args.get(1), "x");
        assertMathUnitsCompatible(y, "y", x, "x");
        return degrees(Math.atan2(y.value(), x.valueInUnitsOf(y)));
    }

    /// Returns a unitless number argument with a parameter-scoped failure.
    ///
    /// @param value the supplied argument
    /// @param name  the parameter name without a dollar sign
    /// @return the unitless number
    private static SassNumber unitlessNumberArgument(SassValue value, String name) {
        var number = numberArgument(value, name);
        try {
            return number.assertNoUnits();
        } catch (SassValueException exception) {
            throw prefixParameterException(name, exception);
        }
    }

    /// Requires two numbers to have matching unitfulness and compatible units.
    ///
    /// Unlike [SassNumber#isComparableTo], unitless numbers are not treated as
    /// compatible with unitful numbers. Message order reports the later argument
    /// first, matching dart-sass math diagnostics.
    ///
    /// @param earlier     the earlier formal parameter value
    /// @param earlierName the earlier parameter name without a dollar sign
    /// @param later       the later formal parameter value
    /// @param laterName   the later parameter name without a dollar sign
    private static void assertMathUnitsCompatible(
            SassNumber earlier,
            String earlierName,
            SassNumber later,
            String laterName
    ) {
        if (earlier.isUnitless() != later.isUnitless()) {
            throw new SassValueException(
                    "$" + laterName + ": " + later + " and $" + earlierName + ": " + earlier
                            + " have incompatible units (one has units and the other doesn't)."
            );
        }
        if (!earlier.isUnitless() && !earlier.isComparableTo(later)) {
            throw new SassValueException(
                    "$" + laterName + ": " + later + " and $" + earlierName + ": " + earlier
                            + " have incompatible units."
            );
        }
    }

    /// Computes {@code base ** exponent} with dart-sass special cases for
    /// {@code ±1} bases and infinite exponents.
    ///
    /// Exact equality is required for the {@code ±1} cases so fuzzy ones such as
    /// {@code 1.000000000001 ** ∞} still follow IEEE-style Math.pow results.
    ///
    /// @param base     the unitless base
    /// @param exponent the unitless exponent
    /// @return the power value
    private static double powValue(double base, double exponent) {
        // Java Math.pow(1, ±∞) and Math.pow(-1, ±∞) yield NaN; Sass defines 1.
        if (base == 1.0) {
            return 1.0;
        }
        if (base == -1.0 && Double.isInfinite(exponent)) {
            return 1.0;
        }
        return Math.pow(base, exponent);
    }

    /// Returns a random unitless number, optionally bounded by a positive integer limit.
    ///
    /// @param context the invocation context used for unit deprecations
    /// @param args the optional limit
    /// @return a number in {@code [0, 1)} or an integer in {@code 1..limit}
    private static SassValue random(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        if (args.isEmpty() || args.get(0) instanceof SassNull) {
            return SassNumber.of(RANDOM.nextDouble(), null);
        }
        var limit = numberArgument(args.get(0), "limit");
        if (!limit.isUnitless()) {
            var units = limit.unitString();
            context.deprecate(
                    "math.random() will no longer ignore $limit units ("
                            + limit + ") in a future release.\n\n"
                            + "Recommendation: math.random(math.div($limit, 1"
                            + units + ")) * 1" + units + "\n\n"
                            + "To preserve current behavior: "
                            + "math.random(math.div($limit, 1" + units + "))\n\n"
                            + "More info: https://sass-lang.com/d/function-units",
                    FUNCTION_UNITS_CODE
            );
        }
        int limitScalar;
        try {
            limitScalar = limit.assertInt();
        } catch (SassValueException exception) {
            throw prefixParameterException("limit", exception);
        }
        if (limitScalar < 1) {
            throw new SassValueException(
                    "$limit: Must be greater than 0, was " + limit + "."
            );
        }
        return SassNumber.of(RANDOM.nextInt(limitScalar) + 1.0, null);
    }

    /// Coerces an angle to radians for trigonometric functions.
    ///
    /// Unitless values are treated as radians. Angle units
    /// ({@code deg}, {@code grad}, {@code rad}, {@code turn}) are coerced.
    /// Other units are rejected with dart-sass's {@code $number} angle message.
    ///
    /// @param number the angle, unitless or angle-unitful
    /// @return the magnitude in radians
    private static double radians(SassNumber number) {
        if (number.isUnitless()) {
            return number.value();
        }
        try {
            return number.coerce(RADIANS, List.of()).value();
        } catch (SassValueException exception) {
            throw new SassValueException(
                    "$number: Expected " + number
                            + " to have an angle unit (deg, grad, rad, turn)."
            );
        }
    }

    /// Returns one degree-valued angle from a radian magnitude.
    ///
    /// @param radians the angle in radians
    /// @return the degree number
    private static SassNumber degrees(double radians) {
        return SassNumber.withUnits(radians * (180.0 / Math.PI), DEGREES, List.of());
    }

    /// Implements the deprecated global absolute-value function and its
    /// compatibility diagnostics.
    private static SassValue abs(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        try {
            var number = args.get(0).assertNumber();
            if (number.numeratorUnits().equals(List.of("%"))
                    && number.denominatorUnits().isEmpty()) {
                context.deprecate(
                        "Passing percentage units to the global abs() function "
                                + "is deprecated.\n"
                                + "In the future, this will emit a CSS abs() "
                                + "function to be resolved by the browser.\n"
                                + "To preserve current behavior: math.abs("
                                + number.toCssString() + ")\n"
                                + "To emit a CSS abs() now: abs(#{"
                                + number.toCssString() + "})\n"
                                + "More info: https://sass-lang.com/d/abs-percent",
                        "abs-percent"
                );
            } else {
                warnGlobalBuiltIn(context, "math", "abs");
            }
            return SassNumber.withUnits(
                    Math.abs(number.value()),
                    number.numeratorUnits(),
                    number.denominatorUnits()
            );
        } catch (SassValueException exception) {
            String message = Objects.requireNonNull(exception.getMessage(), "abs message");
            if (message.startsWith("$")) {
                throw exception;
            }
            throw new SassValueException("$number: " + message);
        }
    }

    /// Returns the absolute value for the non-deprecated module entry point.
    ///
    /// @param args the one number argument
    /// @return the number with a non-negative magnitude and unchanged units
    private static SassValue moduleAbs(List<SassValue> args) {
        var number = numberArgument(args.get(0), "number");
        return SassNumber.withUnits(
                Math.abs(number.value()),
                number.numeratorUnits(),
                number.denominatorUnits()
        );
    }

    /// Rounds one number to the nearest integer, preserving its units.
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

    /// Rounds one number toward positive infinity.
    private static SassValue ceil(List<SassValue> args) {
        return mapNumber(args.get(0), Math::ceil);
    }

    /// Rounds one number toward negative infinity.
    private static SassValue floor(List<SassValue> args) {
        return mapNumber(args.get(0), Math::floor);
    }

    /// Returns the least of a nonempty set of comparable numbers.
    private static SassValue min(List<SassValue> args) {
        return extreme(restValues(args), true);
    }

    /// Returns the greatest of a nonempty set of comparable numbers.
    private static SassValue max(List<SassValue> args) {
        return extreme(restValues(args), false);
    }

    /// Expands a sole argument-list value into its positional elements.
    private static List<SassValue> restValues(List<SassValue> args) {
        if (args.size() == 1 && args.get(0) instanceof SassArgumentList list) {
            return list.asList();
        }
        return args;
    }

    /// Selects the minimum or maximum from comparable numbers.
    private static SassValue extreme(List<SassValue> args, boolean minimum) {
        if (args.isEmpty()) {
            throw new SassValueException("At least one argument must be passed.");
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

    /// Applies a scalar operation while retaining a number's units.
    private static SassNumber mapNumber(
            SassValue value,
            java.util.function.DoubleUnaryOperator operator
    ) {
        var number = numberArgument(value, "number");
        return SassNumber.withUnits(
                operator.applyAsDouble(number.value()),
                number.numeratorUnits(),
                number.denominatorUnits()
        );
    }

    /// Returns a mapped value or Sass null for an absent key.
    private static SassValue mapGet(List<SassValue> args) {
        var map = mapArgument(args.get(0), "map");
        @Nullable SassValue value = map.contents().get(args.get(1));
        return value == null ? SassNull.NULL : value;
    }

    /// Returns map keys in insertion order as a comma-separated list.
    private static SassValue mapKeys(List<SassValue> args) {
        return new SassList(
                List.copyOf(mapArgument(args.get(0), "map").contents().keySet()),
                ListSeparator.COMMA,
                false
        );
    }

    /// Returns map values in insertion order as a comma-separated list.
    private static SassValue mapValues(List<SassValue> args) {
        return new SassList(
                List.copyOf(mapArgument(args.get(0), "map").contents().values()),
                ListSeparator.COMMA,
                false
        );
    }

    /// Merges two maps at their top level.
    private static SassValue mapMerge(List<SassValue> args) {
        return flatMergeMaps(
                mapArgument(args.get(0), "map1"),
                mapArgument(args.get(1), "map2")
        );
    }

    /// Returns whether a map contains a top-level key.
    private static SassValue mapHasKey(List<SassValue> args) {
        return SassBoolean.of(mapArgument(args.get(0), "map").contents().containsKey(args.get(1)));
    }

    /// Looks up a value by a possibly nested path in a map module call.
    ///
    /// @param args the map, first key, and remaining key arguments
    /// @return the mapped value or Sass null when no path element exists
    private static SassValue moduleMapGet(List<SassValue> args) {
        var map = mapArgument(args.get(0), "map");
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
        var map = mapArgument(args.get(0), "map");
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

    /// Merges two maps, optionally at a nested path of keys.
    ///
    /// Forms:
    /// - {@code merge($map1, $map2)}
    /// - {@code merge($map1, $keys..., $map2)}
    /// - {@code merge($map1: …, $map2: …)} and nested variants with named {@code $map2}
    ///
    /// @param args {@code $map1} followed by a rest list of keys and {@code $map2}
    /// @return the merged map
    private static SassValue moduleMapMerge(List<SassValue> args) {
        var map1 = mapArgument(args.get(0), "map1");
        if (args.size() < 2 || !(args.get(1) instanceof SassArgumentList rest)) {
            throw new SassValueException("Expected $args to contain a key.");
        }
        var positional = rest.asList();
        var keywords = rest.keywords();
        @Nullable SassValue namedMap2 = keywords.get("map2");
        if (namedMap2 != null) {
            if (keywords.size() != 1) {
                throw unknownMapKeyword(keywords.keySet(), Set.of("map2"));
            }
            var map2 = mapArgument(namedMap2, "map2");
            return positional.isEmpty()
                    ? flatMergeMaps(map1, map2)
                    : nestedMergeAtPath(map1, positional, map2);
        }
        if (!keywords.isEmpty()) {
            throw unknownMapKeyword(keywords.keySet(), Set.of("map2"));
        }
        if (positional.isEmpty()) {
            throw new SassValueException("Expected $args to contain a key.");
        }
        if (positional.size() == 1) {
            return flatMergeMaps(map1, mapArgument(positional.get(0), "map2"));
        }
        var keys = positional.subList(0, positional.size() - 1);
        var map2 = mapArgument(positional.get(positional.size() - 1), "map2");
        return nestedMergeAtPath(map1, keys, map2);
    }

    /// Sets one direct map key to a replacement value.
    ///
    /// @param args the map, key, and replacement value
    /// @return a map containing the replacement entry
    private static SassValue mapSet(List<SassValue> args) {
        var contents = new LinkedHashMap<>(mapArgument(args.get(0), "map").contents());
        contents.put(args.get(1), args.get(2));
        return new SassMap(contents);
    }

    /// Sets a value at a possibly nested path of keys.
    ///
    /// Forms: {@code set($map, $keys..., $value)} with at least one key, or the
    /// named form {@code set($map: …, $key: …, $value: …)}.
    ///
    /// @param args {@code $map} followed by keys and a final value
    /// @return a map containing the replacement entry
    private static SassValue moduleMapSet(List<SassValue> args) {
        var map = mapArgument(args.get(0), "map");
        if (args.size() < 2 || !(args.get(1) instanceof SassArgumentList rest)) {
            throw new SassValueException("Expected $args to contain a key.");
        }
        var positional = rest.asList();
        var keywords = rest.keywords();
        @Nullable SassValue namedKey = keywords.get("key");
        @Nullable SassValue namedValue = keywords.get("value");
        if (namedKey != null || namedValue != null) {
            for (var name : keywords.keySet()) {
                if (!"key".equals(name) && !"value".equals(name)) {
                    throw unknownMapKeyword(keywords.keySet(), Set.of("key", "value"));
                }
            }
            if (namedKey == null) {
                throw new SassValueException("Expected $args to contain a key.");
            }
            if (namedValue == null) {
                throw new SassValueException("Expected $args to contain a value.");
            }
            var keys = new ArrayList<>(positional);
            keys.add(namedKey);
            return nestedSetAtPath(map, keys, namedValue);
        }
        if (!keywords.isEmpty()) {
            throw unknownMapKeyword(keywords.keySet(), Set.of("key", "value"));
        }
        if (positional.isEmpty()) {
            throw new SassValueException("Expected $args to contain a key.");
        }
        if (positional.size() == 1) {
            throw new SassValueException("Expected $args to contain a value.");
        }
        var keys = positional.subList(0, positional.size() - 1);
        var value = positional.get(positional.size() - 1);
        return nestedSetAtPath(map, keys, value);
    }

    /// Removes all supplied direct keys from a map.
    ///
    /// Accepts positional rest keys and the named form {@code $key}.
    ///
    /// @param args the map and rest key arguments
    /// @return a map without the supplied keys
    private static SassValue mapRemove(List<SassValue> args) {
        var contents = new LinkedHashMap<>(mapArgument(args.get(0), "map").contents());
        if (args.size() > 1 && args.get(1) instanceof SassArgumentList rest) {
            var keywords = rest.keywords();
            @Nullable SassValue namedKey = keywords.get("key");
            if (namedKey != null) {
                if (keywords.size() != 1) {
                    throw unknownMapKeyword(keywords.keySet(), Set.of("key"));
                }
                if (!rest.asList().isEmpty()) {
                    throw new SassValueException(
                            "Argument $key was passed both by position and by name."
                    );
                }
                contents.remove(namedKey);
                return new SassMap(contents);
            }
            if (!keywords.isEmpty()) {
                throw unknownMapKeyword(keywords.keySet(), Set.of("key"));
            }
            for (var key : rest.asList()) {
                contents.remove(key);
            }
            return new SassMap(contents);
        }
        for (var key : restValuesAt(args, 1)) {
            contents.remove(key);
        }
        return new SassMap(contents);
    }

    /// Creates the unknown-keyword diagnostic for map rest parameters.
    private static SassValueException unknownMapKeyword(
            Set<String> names,
            Set<String> allowed
    ) {
        var unexpected = names.stream()
                .filter(name -> !allowed.contains(name))
                .sorted()
                .map(name -> "$" + name)
                .toList();
        if (unexpected.size() == 1) {
            return new SassValueException("No parameter named " + unexpected.get(0) + ".");
        }
        return new SassValueException(
                "No parameters named " + String.join(" or ", unexpected) + "."
        );
    }

    /// Merges nested map values recursively while giving the second map precedence.
    ///
    /// @param args the maps to merge
    /// @return a recursively merged map
    private static SassValue mapDeepMerge(List<SassValue> args) {
        return deepMerge(mapArgument(args.get(0), "map1"), mapArgument(args.get(1), "map2"));
    }

    /// Removes a possibly nested path from a map.
    ///
    /// @param args the map, first key, and remaining key arguments
    /// @return a map with the target path removed when it exists
    private static SassValue mapDeepRemove(List<SassValue> args) {
        return deepRemove(mapArgument(args.get(0), "map"), keysWithRest(args), 0);
    }

    /// Returns a map argument while identifying its Sass parameter in failures.
    private static SassMap mapArgument(SassValue value, String name) {
        try {
            return value.assertMap();
        } catch (SassValueException exception) {
            throw prefixParameterException(name, exception);
        }
    }

    /// Shallow-merges two maps with the second map winning on key conflicts.
    private static SassMap flatMergeMaps(SassMap first, SassMap second) {
        var result = new LinkedHashMap<>(first.contents());
        result.putAll(second.contents());
        return new SassMap(result);
    }

    /// Merges {@code map2} into {@code map} at the nested path identified by {@code keys}.
    ///
    /// Non-map intermediate values are replaced by empty maps so the path can be
    /// created, matching dart-sass {@code map.merge} nested semantics.
    private static SassMap nestedMergeAtPath(
            SassMap map,
            List<SassValue> keys,
            SassMap map2
    ) {
        if (keys.isEmpty()) {
            return flatMergeMaps(map, map2);
        }
        var key = keys.get(0);
        var remaining = keys.subList(1, keys.size());
        @Nullable SassValue existing = map.contents().get(key);
        @Nullable SassMap nested = existing == null ? null : existing.tryMap();
        if (nested == null) {
            nested = new SassMap(Map.of());
        }
        var mergedNested = nestedMergeAtPath(nested, remaining, map2);
        var contents = new LinkedHashMap<>(map.contents());
        contents.put(key, mergedNested);
        return new SassMap(contents);
    }

    /// Sets {@code value} into {@code map} at the nested path identified by {@code keys}.
    ///
    /// Non-map intermediate values are replaced by empty maps so the path can be
    /// created, matching dart-sass {@code map.set} nested semantics.
    private static SassMap nestedSetAtPath(
            SassMap map,
            List<SassValue> keys,
            SassValue value
    ) {
        if (keys.isEmpty()) {
            throw new AssertionError("nested set requires at least one key");
        }
        if (keys.size() == 1) {
            var contents = new LinkedHashMap<>(map.contents());
            contents.put(keys.get(0), value);
            return new SassMap(contents);
        }
        var key = keys.get(0);
        var remaining = keys.subList(1, keys.size());
        @Nullable SassValue existing = map.contents().get(key);
        @Nullable SassMap nested = existing == null ? null : existing.tryMap();
        if (nested == null) {
            nested = new SassMap(Map.of());
        }
        var updatedNested = nestedSetAtPath(nested, remaining, value);
        var contents = new LinkedHashMap<>(map.contents());
        contents.put(key, updatedNested);
        return new SassMap(contents);
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

    /// Returns a string's Unicode code-point length.
    private static SassValue strLength(List<SassValue> args) {
        var text = stringArgument(args.get(0), "string").text();
        return SassNumber.of(text.codePointCount(0, text.length()), null);
    }

    /// Returns the one-based code-point index of a substring or Sass null.
    private static SassValue strIndex(List<SassValue> args) {
        var text = stringArgument(args.get(0), "string").text();
        var substring = stringArgument(args.get(1), "substring").text();
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
        var string = stringArgument(args.get(0), "string");
        var inserted = stringArgument(args.get(1), "insert");
        var text = string.text();
        var length = text.codePointCount(0, text.length());
        int index;
        try {
            index = numberArgument(args.get(2), "index").assertNoUnits().assertInt();
        } catch (SassValueException exception) {
            throw prefixParameterException("index", exception);
        }
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
        var string = stringArgument(args.get(0), "string");
        var separator = stringArgument(args.get(1), "separator");
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
        SassNumber number;
        int limit;
        try {
            number = numberArgument(value, "limit").assertNoUnits();
            limit = number.assertInt();
        } catch (SassValueException exception) {
            throw prefixParameterException("limit", exception);
        }
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
        var string = stringArgument(args.get(0), "string");
        var text = string.text();
        var length = text.codePointCount(0, text.length());
        // dart-sass prefixes unit failures with $start-at/$end-at, but leaves
        // bare "is not an int" for non-integer slice indexes.
        SassNumber startNumber;
        SassNumber endNumber;
        try {
            startNumber = numberArgument(args.get(1), "start-at").assertNoUnits();
        } catch (SassValueException exception) {
            throw prefixParameterException("start-at", exception);
        }
        try {
            endNumber = numberArgument(args.get(2), "end-at").assertNoUnits();
        } catch (SassValueException exception) {
            throw prefixParameterException("end-at", exception);
        }
        var startAt = startNumber.assertInt();
        var endAt = endNumber.assertInt();
        var start = stringCodePointIndex(startAt, length, false);
        if (endAt == 0) {
            return new SassString("", string.hasQuotes());
        }
        var end = stringCodePointIndex(endAt, length, true);
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

    /// Converts ASCII lowercase letters while preserving string quote state.
    private static SassValue toUpperCase(List<SassValue> args) {
        var string = stringArgument(args.get(0), "string");
        return new SassString(asciiCase(string.text(), true), string.hasQuotes());
    }

    /// Converts ASCII uppercase letters while preserving string quote state.
    private static SassValue toLowerCase(List<SassValue> args) {
        var string = stringArgument(args.get(0), "string");
        return new SassString(asciiCase(string.text(), false), string.hasQuotes());
    }

    /// Returns a string argument while identifying its Sass parameter in failures.
    ///
    /// @param value the supplied argument
    /// @param name  the parameter name without a dollar sign
    /// @return the string value
    /// @throws SassValueException if {@code value} is not a string
    private static SassString stringArgument(SassValue value, String name) {
        if (value instanceof SassString string) {
            return string;
        }
        // Parenthesize bare lists so diagnostics match dart-sass inspect form.
        var rendered = value instanceof SassList list && !list.hasBrackets()
                ? "(" + value + ")"
                : value.toString();
        throw new SassValueException("$" + name + ": " + rendered + " is not a string.");
    }

    /// Applies ASCII-only case conversion without changing non-ASCII code
    /// points.
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

    /// Returns a process-unique unquoted Sass identifier.
    private static SassValue uniqueId(List<SassValue> args) {
        var value = UNIQUE_ID.getAndIncrement();
        return new SassString("u" + Long.toString(value, 36), false);
    }

    /// Returns the effective separator name for one list view.
    private static SassValue listSeparator(List<SassValue> args) {
        var separator = args.get(0).separator();
        var text = separator == ListSeparator.COMMA
                ? "comma"
                : separator == ListSeparator.SLASH ? "slash" : "space";
        return new SassString(text, false);
    }

    /// Returns whether a list value is bracketed.
    private static SassValue isBracketed(List<SassValue> args) {
        return SassBoolean.of(args.get(0).hasBrackets());
    }

    /// Returns a value's first one-based list index or Sass null.
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

    /// Replaces the value at a Sass list index.
    private static SassValue setNth(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var list = args.get(0);
        var contents = new ArrayList<>(list.asList());
        deprecateUnitfulIndex(context, args.get(1), "n");
        var index = list.sassIndexToListIndex(args.get(1), contents.size());
        contents.set(index, args.get(2));
        var separator = list.separator() == ListSeparator.UNDECIDED
                ? ListSeparator.SPACE
                : list.separator();
        return new SassList(contents, separator, list.hasBrackets());
    }

    /// Transposes list views, truncating rows to the shortest input.
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

    /// Creates one deprecated legacy color-channel reader.
    ///
    /// RGB channels retain Sass's historical nearest-integer rounding. HSL and
    /// HWB channels are returned in their conventional units.
    ///
    /// @param name the legacy channel name
    /// @param space the replacement function's explicit color space
    /// @param unit the returned unit, or {@code null} for unitless channels
    /// @param global whether the function is exposed without a module namespace
    /// @return the contextual channel function
    private static BuiltInCallable deprecatedColorChannelFunction(
            String name,
            ColorSpace space,
            @Nullable String unit,
            boolean global
    ) {
        return BuiltInCallable.contextual(
                name,
                List.of(Param.required("color")),
                1,
                (context, args) -> {
                    var color = colorArgument(args.get(0), "color");
                    double value = switch (name) {
                        case "red" -> roundSass(color.red());
                        case "green" -> roundSass(color.green());
                        case "blue" -> roundSass(color.blue());
                        case "hue" -> color.hue();
                        case "saturation" -> color.saturation();
                        case "lightness" -> color.lightness();
                        case "whiteness" -> color.whiteness();
                        case "blackness" -> color.blackness();
                        default -> throw new AssertionError(
                                "unknown legacy color channel: " + name
                        );
                    };
                    context.deprecate(
                            (global ? "" : "color.") + name
                                    + "() is deprecated. Suggestion:\n\n"
                                    + "color.channel($color, \"" + name
                                    + "\", $space: " + space.spaceName() + ")\n\n"
                                    + "More info: https://sass-lang.com/d/color-functions",
                            COLOR_FUNCTIONS_CODE
                    );
                    return SassNumber.of(value, unit);
                }
        );
    }

    /// Pattern matching the start of a proprietary Microsoft filter argument
    /// such as {@code opacity=50} or {@code c=d}.
    private static final java.util.regex.Pattern MICROSOFT_FILTER_START =
            java.util.regex.Pattern.compile("^[a-zA-Z]+\\s*=");

    /// Returns the alpha channel of a color, or preserves Microsoft filter forms.
    ///
    /// Supports:
    /// <ul>
    ///   <li>{@code alpha($color)} for legacy colors</li>
    ///   <li>{@code alpha(c=d)} / multi-arg Microsoft filter passthrough</li>
    /// </ul>
    ///
    /// @param context the invocation receiving global-builtin diagnostics
    /// @param args the rest argument list bound as {@code $args}
    /// @return the alpha number or an unquoted plain-CSS {@code alpha(...)} call
    private static SassValue alphaChannel(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return alphaChannelImpl(context, args, false);
    }

    /// Module {@code color.alpha()} with module-specific diagnostics.
    ///
    /// @param context the invocation receiving module-compatibility diagnostics
    /// @param args the rest argument list bound as {@code $args}
    /// @return the alpha number or an unquoted plain-CSS {@code alpha(...)} call
    private static SassValue moduleAlphaChannel(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return alphaChannelImpl(context, args, true);
    }

    /// Shared alpha implementation for global and module entry points.
    ///
    /// @param context the invocation receiving applicable diagnostics
    /// @param args the rest argument list bound as {@code $args}
    /// @param module whether this is the {@code sass:color} entry point
    /// @return the alpha number or a preserved Microsoft filter call
    private static SassValue alphaChannelImpl(
            BuiltInCallable.Context context,
            List<SassValue> args,
            boolean module
    ) {
        SassArgumentList rest = restArgumentList(args);
        var values = rest.asList();
        var keywords = rest.keywords();
        if (values.isEmpty() && keywords.containsKey("color") && keywords.size() == 1) {
            values = List.of(keywords.get("color"));
        } else if (!keywords.isEmpty()) {
            // Unknown or excess keywords fall through to arity diagnostics.
            if (!(values.isEmpty() && keywords.containsKey("color"))) {
                throw new SassValueException(
                        "No argument named $" + keywords.keySet().iterator().next() + "."
                );
            }
        }
        if (values.size() == 1) {
            SassValue argument = values.get(0);
            if (isMicrosoftFilterArgument(argument)) {
                var result = CssColorChannels.functionString("alpha", values);
                if (module) {
                    warnColorModuleMicrosoftFilter(
                            context,
                            result
                    );
                }
                return result;
            }
            if (!module
                    && argument instanceof SassColor color
                    && !color.isLegacy()) {
                throw new SassValueException(
                        "alpha() is only supported for legacy colors. Please use "
                                + "color.channel() instead."
                );
            }
            if (!module) {
                warnGlobalBuiltIn(
                        Objects.requireNonNull(context, "global alpha context"),
                        "color",
                        "alpha"
                );
            }
            var color = colorArgument(argument, "color");
            if (!color.isLegacy()) {
                throw new SassValueException(
                        (module ? "color.alpha()" : "alpha()")
                                + " is only supported for legacy colors. Please use "
                                + "color.channel() instead."
                );
            }
            return SassNumber.of(color.alpha(), null);
        }
        if (module) {
            // Empty argument lists satisfy Iterable.every, so module alpha()
            // attempts Microsoft-filter serialization and fails with the empty
            // list CSS error, matching dart-sass.
            if (values.stream().allMatch(BuiltInFunctions::isMicrosoftFilterArgument)) {
                var result = CssColorChannels.functionString("alpha", List.of(rest));
                warnColorModuleMicrosoftFilter(
                        context,
                        result
                );
                return result;
            }
            throw new SassValueException(
                    "Only 1 argument allowed, but " + values.size() + " were passed."
            );
        }
        if (!values.isEmpty() && values.stream().allMatch(BuiltInFunctions::isMicrosoftFilterArgument)) {
            return CssColorChannels.functionString("alpha", values);
        }
        if (values.isEmpty()) {
            throw new SassValueException("Missing argument $color.");
        }
        throw new SassValueException(
                "Only 1 argument allowed, but " + values.size() + " were passed."
        );
    }

    /// Returns whether {@code value} is an unquoted Microsoft-filter style argument.
    private static boolean isMicrosoftFilterArgument(SassValue value) {
        return value instanceof SassString string
                && !string.hasQuotes()
                && MICROSOFT_FILTER_START.matcher(string.text()).find();
    }

    /// Reports use of {@code color.alpha()} as a Microsoft filter.
    ///
    /// @param context the invocation receiving the diagnostic
    /// @param result the preserved plain-CSS function value
    private static void warnColorModuleMicrosoftFilter(
            BuiltInCallable.Context context,
            SassValue result
    ) {
        context.deprecate(
                "Using color.alpha() for a Microsoft filter is deprecated.\n\n"
                        + "Recommendation: " + result.toCssString(),
                COLOR_MODULE_COMPAT_CODE
        );
    }

    /// Extracts the rest argument list bound to a {@code withRest} parameter.
    private static SassArgumentList restArgumentList(List<SassValue> args) {
        if (args.size() == 1 && args.get(0) instanceof SassArgumentList list) {
            return list;
        }
        return new SassArgumentList(args, ListSeparator.COMMA, Map.of());
    }

    /// Mixes two colors using either the legacy RGB algorithm or Color 4
    /// interpolation.
    ///
    /// @param args the two colors, weight, and optional interpolation method
    /// @return the mixed color
    private static SassValue colorMix(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var first = colorArgument(args.get(0), "color1");
        var second = colorArgument(args.get(1), "color2");
        var weight = numberArgument(args.get(2), "weight");
        var methodValue = args.get(3);
        if (!(methodValue instanceof SassNull)) {
            var method = InterpolationMethod.fromValue(methodValue, "method");
            // Color 4 mix requires a percent weight (or unitless during deprecation).
            return first.interpolate(
                    second,
                    method,
                    legacyWeight(weight, "weight"),
                    false
            );
        }
        checkPercent(context, weight, "weight");
        if (!first.isLegacy()) {
            throw new SassValueException(
                    "$color1: To use color.mix() with non-legacy color " + first
                            + ", you must provide a $method."
            );
        }
        if (!second.isLegacy()) {
            throw new SassValueException(
                    "$color2: To use color.mix() with non-legacy color " + second
                            + ", you must provide a $method."
            );
        }
        return first.mixedWith(second, legacyWeight(weight, "weight"));
    }

    /// Inverts a color in RGB or an explicit space, or preserves a plain-CSS
    /// number filter.
    ///
    /// @param args the color or number, weight, and optional output space
    /// @return the inverted color or an unquoted CSS {@code invert()} function
    private static SassValue colorInvert(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var weightNumber = numberArgument(args.get(1), "weight");
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            if (!isCssFilterDefaultWeight(weightNumber)) {
                throw new SassValueException(
                        "Only one argument may be passed to the plain-CSS invert() function."
                );
            }
            return new SassString("invert(" + number.toCssString() + ")", false);
        }
        // Preserve plain-CSS special numbers as invert(...) filters.
        if (value.isSpecialNumber()) {
            if (!isCssFilterDefaultWeight(weightNumber)) {
                throw new SassValueException(
                        "Only one argument may be passed to the plain-CSS invert() function."
                );
            }
            return CssColorChannels.functionString("invert", List.of(value));
        }
        var color = colorArgument(value, "color");
        var spaceValue = args.get(2);
        if (spaceValue instanceof SassNull) {
            if (!color.isLegacy()) {
                throw new SassValueException(
                        "$color: To use color.invert() with non-legacy color " + color
                                + ", you must provide a $space."
                );
            }
            checkPercent(context, weightNumber, "weight");
            return color.inverted().mixedWith(color, legacyWeight(weightNumber, "weight"));
        }

        var space = spaceArgument(spaceValue, "space");
        // With an explicit space, weight may be unitless or percent.
        var weight = color4Weight(weightNumber, "weight");
        if (SassFuzzy.equals(weight, 0.0)) {
            return color;
        }
        // Explicit $space keeps missing/powerless channels for diagnostics.
        var inSpace = color.toSpace(space, true);
        var inverted = invertInSpace(inSpace);
        if (SassFuzzy.equals(weight, 1.0)) {
            return inverted.toSpace(color.space(), false);
        }
        return color.interpolate(
                inverted,
                InterpolationMethod.of(space),
                1.0 - weight,
                false
        );
    }

    /// Dispatches module {@code color.invert()} with compatibility reporting.
    ///
    /// @param context the invocation receiving module-compatibility diagnostics
    /// @param args the color or filter argument, weight, and optional space
    /// @return the inverted color or preserved CSS filter
    private static SassValue moduleColorInvert(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var result = colorInvert(context, args);
        if (result instanceof SassString) {
            context.deprecate(
                    "Passing a number (" + args.get(0)
                            + ") to color.invert() is deprecated.\n\n"
                            + "Recommendation: " + result.toCssString(),
                    COLOR_MODULE_COMPAT_CODE
            );
        }
        return result;
    }

    /// Dispatches global {@code invert()} with conditional deprecation reporting.
    ///
    /// Plain-CSS number and special-number filter forms do not represent the
    /// deprecated Sass global built-in.
    ///
    /// @param context the invocation receiving global-builtin diagnostics
    /// @param args the color or filter argument, weight, and optional space
    /// @return the inverted color or preserved CSS filter
    private static SassValue globalColorInvert(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var value = args.get(0);
        if (!(value instanceof SassNumber) && !value.isSpecialNumber()) {
            warnGlobalBuiltIn(context, "color", "invert");
        }
        return colorInvert(context, args);
    }

    /// Parses a Color 4 weight that accepts unitless values or {@code %}.
    private static double color4Weight(SassNumber number, String name) {
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

    /// Global {@code invert()} with plain-CSS number filter fallback.
    private static SassValue globalInvert(List<SassValue> args) {
        var value = args.get(0);
        var weightNumber = numberArgument(args.get(1), "weight");
        if (value instanceof SassNumber number) {
            if (!isCssFilterDefaultWeight(weightNumber)) {
                throw new SassValueException(
                        "Only one argument may be passed to the plain-CSS invert() function."
                );
            }
            return new SassString("invert(" + number.toCssString() + ")", false);
        }
        if (value.isSpecialNumber()) {
            return CssColorChannels.functionString("invert", List.of(value));
        }
        var color = colorArgument(value, "color");
        if (!color.isLegacy()) {
            throw new SassValueException(
                    "$color: Global invert() only supports legacy colors. Use color.invert() instead."
            );
        }
        return color.inverted().mixedWith(color, legacyWeight(weightNumber, "weight"));
    }

    /// Global {@code grayscale()} with plain-CSS number filter fallback.
    private static SassValue globalGrayscale(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            return new SassString("grayscale(" + number.toCssString() + ")", false);
        }
        if (value.isSpecialNumber()) {
            return CssColorChannels.functionString("grayscale", List.of(value));
        }
        warnGlobalBuiltIn(context, "color", "grayscale");
        var color = colorArgument(value, "color");
        if (!color.isLegacy()) {
            throw new SassValueException(
                    "$color: Global grayscale() only supports legacy colors. Use color.grayscale() instead."
            );
        }
        return color.grayscale();
    }

    /// Global {@code complement()} for legacy colors.
    private static SassValue globalComplement(List<SassValue> args) {
        var color = colorArgument(args.get(0), "color");
        if (!color.isLegacy()) {
            throw new SassValueException(
                    "$color: Global complement() only supports legacy colors. Use color.complement() instead."
            );
        }
        return color.changeHsl(color.hue() + 180.0, null, null, null);
    }

    /// Implements the deprecated global {@code adjust-hue()} function.
    private static SassValue adjustHue(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var color = colorArgument(args.get(0), "color");
        requireLegacyColorFunction(color, "adjust-hue");
        double degrees = angleValue(
                context,
                numberArgument(args.get(1), "degrees"),
                "degrees"
        );
        context.deprecate(
                "adjust-hue() is deprecated. Suggestion:\n\n"
                        + "color.adjust($color, $hue: "
                        + SassNumber.of(degrees, "deg").toCssString()
                        + ")\n\n"
                        + "More info: https://sass-lang.com/d/color-functions",
                COLOR_FUNCTIONS_CODE
        );
        return color.changeHsl(color.hue() + degrees, null, null, null);
    }

    /// Implements the deprecated global {@code lighten()} function.
    private static SassValue lighten(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var color = colorArgument(args.get(0), "color");
        requireLegacyColorFunction(color, "lighten");
        double amount = numberArgument(args.get(1), "amount").valueInRange(0, 100, "amount");
        var result = color.changeHsl(
                null,
                null,
                clampLikeCss(color.lightness() + amount, 0, 100),
                null
        );
        deprecateColorAdjustment(
                context,
                "lighten",
                color,
                amount,
                "lightness"
        );
        return result;
    }

    /// Implements the deprecated global {@code darken()} function.
    private static SassValue darken(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var color = colorArgument(args.get(0), "color");
        requireLegacyColorFunction(color, "darken");
        double amount = numberArgument(args.get(1), "amount").valueInRange(0, 100, "amount");
        var result = color.changeHsl(
                null,
                null,
                clampLikeCss(color.lightness() - amount, 0, 100),
                null
        );
        deprecateColorAdjustment(
                context,
                "darken",
                color,
                -amount,
                "lightness"
        );
        return result;
    }

    /// Dispatches global {@code saturate()} CSS-filter and color-adjuster overloads.
    ///
    /// The filter form {@code saturate($amount)} is preferred for zero-or-one
    /// positional arguments without a {@code $color} keyword. The adjuster form
    /// {@code saturate($color, $amount)} is used when {@code $color} is named, two
    /// positionals are supplied, or one positional is paired with {@code $amount}.
    ///
    /// @param args the rest-bound argument list from [#withRest]
    /// @return a CSS filter string or adjusted color
    private static SassValue saturateRest(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var rest = restArgumentList(args);
        var positional = new ArrayList<>(rest.asList());
        var named = new LinkedHashMap<>(rest.keywords());

        if (positional.isEmpty() && named.isEmpty()) {
            throw new SassValueException("Missing argument $amount.");
        }

        boolean useColorForm = named.containsKey("color")
                || positional.size() >= 2
                || (positional.size() == 1 && named.containsKey("amount"));

        if (!useColorForm) {
            @Nullable SassValue amount = takeNamedOrPositional(positional, named, "amount");
            if (!named.isEmpty()) {
                throw new SassValueException(
                        "No argument named $" + named.keySet().iterator().next() + "."
                );
            }
            if (!positional.isEmpty()) {
                throw new SassValueException(
                        "Only 1 argument allowed, but "
                                + (1 + positional.size()) + " were passed."
                );
            }
            if (amount == null) {
                throw new SassValueException("Missing argument $amount.");
            }
            return saturateCssFilter(amount);
        }

        @Nullable SassValue color = takeNamedOrPositional(positional, named, "color");
        @Nullable SassValue amount = takeNamedOrPositional(positional, named, "amount");
        if (!named.isEmpty()) {
            throw new SassValueException(
                    "No argument named $" + named.keySet().iterator().next() + "."
            );
        }
        if (!positional.isEmpty()) {
            throw new SassValueException(
                    "Only 2 arguments allowed, but "
                            + (2 + positional.size()) + " were passed."
            );
        }
        if (color == null) {
            throw new SassValueException("Missing argument $color.");
        }
        if (amount == null) {
            throw new SassValueException("Missing argument $amount.");
        }
        warnGlobalBuiltIn(context, "color", "adjust");
        return saturateColor(context, color, amount);
    }

    /// Implements the one-argument CSS {@code saturate()} filter form.
    private static SassValue saturateCssFilter(SassValue amount) {
        if (amount instanceof SassNumber number) {
            return new SassString("saturate(" + number.toCssString() + ")", false);
        }
        if (amount.isSpecialNumber()) {
            return CssColorChannels.functionString("saturate", List.of(amount));
        }
        throw new SassValueException("$amount: " + amount + " is not a number.");
    }

    /// Implements the two-argument legacy color {@code saturate()} form.
    private static SassValue saturateColor(
            BuiltInCallable.Context context,
            SassValue colorValue,
            SassValue amountValue
    ) {
        var color = colorArgument(colorValue, "color");
        requireLegacyColorFunction(color, "saturate");
        double amount = numberArgument(amountValue, "amount").valueInRange(0, 100, "amount");
        var result = color.changeHsl(
                null,
                clampLikeCss(color.saturation() + amount, 0, 100),
                null,
                null
        );
        deprecateColorAdjustment(
                context,
                "saturate",
                color,
                amount,
                "saturation"
        );
        return result;
    }

    /// Implements the deprecated global {@code desaturate()} function.
    private static SassValue desaturate(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var color = colorArgument(args.get(0), "color");
        requireLegacyColorFunction(color, "desaturate");
        double amount = numberArgument(args.get(1), "amount").valueInRange(0, 100, "amount");
        var result = color.changeHsl(
                null,
                clampLikeCss(color.saturation() - amount, 0, 100),
                null,
                null
        );
        deprecateColorAdjustment(
                context,
                "desaturate",
                color,
                -amount,
                "saturation"
        );
        return result;
    }

    /// Implements global {@code opacify()}/{@code fade-in()}.
    private static SassValue opacifyNamed(
            BuiltInCallable.Context context,
            List<SassValue> args,
            String functionName
    ) {
        var color = colorArgument(args.get(0), "color");
        requireLegacyColorFunction(color, functionName);
        double amount = numberArgument(args.get(1), "amount")
                .valueInRangeWithUnit(0, 1, "amount", "");
        var result = color.changeAlpha(
                clampLikeCss(color.alpha() + amount, 0, 1)
        );
        deprecateColorAdjustment(
                context,
                functionName,
                color,
                amount,
                "alpha"
        );
        return result;
    }

    /// Implements global {@code transparentize()}/{@code fade-out()}.
    private static SassValue transparentizeNamed(
            BuiltInCallable.Context context,
            List<SassValue> args,
            String functionName
    ) {
        var color = colorArgument(args.get(0), "color");
        requireLegacyColorFunction(color, functionName);
        double amount = numberArgument(args.get(1), "amount")
                .valueInRangeWithUnit(0, 1, "amount", "");
        var result = color.changeAlpha(
                clampLikeCss(color.alpha() - amount, 0, 1)
        );
        deprecateColorAdjustment(
                context,
                functionName,
                color,
                -amount,
                "alpha"
        );
        return result;
    }

    /// Reports one deprecated legacy color adjustment.
    ///
    /// @param context the invocation receiving the diagnostic
    /// @param functionName the deprecated global function name
    /// @param color the original legacy color
    /// @param adjustment the signed channel adjustment
    /// @param channelName the adjusted HSL or alpha channel
    private static void deprecateColorAdjustment(
            BuiltInCallable.Context context,
            String functionName,
            SassColor color,
            double adjustment,
            String channelName
    ) {
        context.deprecate(
                functionName + "() is deprecated. "
                        + suggestScaleAndAdjust(color, adjustment, channelName)
                        + "\n\n"
                        + "More info: https://sass-lang.com/d/color-functions",
                COLOR_FUNCTIONS_CODE
        );
    }

    /// Returns Dart Sass migration suggestions for a legacy color adjustment.
    ///
    /// A non-zero adjustment includes both the proportional {@code color.scale()}
    /// replacement and the direct {@code color.adjust()} replacement. A zero
    /// adjustment includes only the latter.
    ///
    /// @param original the original legacy color
    /// @param adjustment the signed requested change
    /// @param channelName the HSL or alpha channel name
    /// @return the complete suggestion block
    private static String suggestScaleAndAdjust(
            SassColor original,
            double adjustment,
            String channelName
    ) {
        boolean alpha = "alpha".equals(channelName);
        double oldValue = alpha
                ? original.alpha()
                : original.toSpace(ColorSpace.HSL, false).channel(channelName);
        double minimum = 0.0;
        double maximum = alpha ? 1.0 : 100.0;
        double newValue = oldValue + adjustment;

        var suggestion = new StringBuilder("Suggestion");
        if (adjustment != 0.0) {
            double factor;
            if (newValue > maximum) {
                factor = 1.0;
            } else if (newValue < minimum) {
                factor = -1.0;
            } else if (adjustment > 0.0) {
                factor = adjustment / (maximum - oldValue);
            } else {
                factor = (newValue - oldValue) / (oldValue - minimum);
            }
            suggestion.append("s:\n\n")
                    .append("color.scale($color, $")
                    .append(channelName)
                    .append(": ")
                    .append(SassNumber.of(factor * 100.0, "%").toCssString())
                    .append(")\n");
        } else {
            suggestion.append(":\n\n");
        }

        return suggestion.append("color.adjust($color, $")
                .append(channelName)
                .append(": ")
                .append(SassNumber.of(
                        adjustment,
                        alpha ? null : "%"
                ).toCssString())
                .append(")")
                .toString();
    }

    /// Global {@code adjust-color()} keyword form.
    private static SassValue globalAdjustColor(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return updateColorComponents(context, args, ColorUpdateMode.ADJUST);
    }

    /// Global {@code scale-color()} keyword form.
    private static SassValue globalScaleColor(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return updateColorComponents(context, args, ColorUpdateMode.SCALE);
    }

    /// Global {@code change-color()} keyword form.
    private static SassValue globalChangeColor(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return updateColorComponents(context, args, ColorUpdateMode.CHANGE);
    }

    /// Implements {@code ie-hex-str($color)}.
    private static SassValue ieHexStr(List<SassValue> args) {
        var rgb = colorArgument(args.get(0), "color").toSpace(ColorSpace.RGB, false);
        int alpha = clampByte((int) Math.round(rgb.alpha() * 255.0));
        int red = clampByte((int) Math.round(rgb.channel0()));
        int green = clampByte((int) Math.round(rgb.channel1()));
        int blue = clampByte((int) Math.round(rgb.channel2()));
        return new SassString(String.format(Locale.ROOT, "#%02X%02X%02X%02X", alpha, red, green, blue), false);
    }

    /// Requires a legacy color for deprecated global color functions.
    private static void requireLegacyColorFunction(SassColor color, String name) {
        if (!color.isLegacy()) {
            throw new SassValueException(
                    name + "() is only supported for legacy colors. Please use "
                            + "color.adjust() instead with an explicit $space argument."
            );
        }
    }

    /// Clamps a value the same way CSS clamps channel extremes.
    ///
    /// {@code NaN} becomes {@code min}, matching dart-sass {@code clampLikeCss}.
    private static double clampLikeCss(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /// Clamps an integer into the inclusive byte range.
    private static int clampByte(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }

    /// Parses an angle argument as degrees for {@code adjust-hue}.
    ///
    /// Unknown units are accepted as bare magnitudes during the function-units
    /// deprecation period, matching dart-sass.
    /// Returns a grayscale legacy RGB color or preserves a plain-CSS number filter.
    ///
    /// @param context the invocation receiving module-compatibility diagnostics
    /// @param args the one color or number argument
    /// @return the grayscale color or an unquoted CSS {@code grayscale()} function
    private static SassValue colorGrayscale(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            var result = new SassString(
                    "grayscale(" + number.toCssString() + ")",
                    false
            );
            context.deprecate(
                    "Passing a number (" + value + ") to color.grayscale() is deprecated.\n\n"
                            + "Recommendation: " + result.toCssString(),
                    COLOR_MODULE_COMPAT_CODE
            );
            return result;
        }
        return colorArgument(value, "color").grayscale();
    }

    /// Returns the polar complement of one color.
    ///
    /// Legacy colors default to HSL when {@code $space} is omitted.
    /// Missing or powerless hue channels reject modification, matching dart-sass.
    ///
    /// @param args the color and optional polar space
    /// @return the complemented color in the original space
    private static SassValue colorComplement(List<SassValue> args) {
        var color = colorArgument(args.get(0), "color");
        var spaceValue = args.get(1);
        ColorSpace space;
        if (color.isLegacy() && spaceValue instanceof SassNull) {
            space = ColorSpace.HSL;
        } else {
            space = spaceArgument(spaceValue, "space");
        }
        if (!space.isPolar()) {
            throw new SassValueException(
                    "$space: Color space " + space + " doesn't have a hue channel."
            );
        }
        // Explicit $space keeps missing/powerless channels; the implicit legacy
        // path fills them with zero so historical grayscale complements stay valid.
        var colorInSpace = color.toSpace(space, !(spaceValue instanceof SassNull));
        SassColor complemented;
        if (space.isLegacy()) {
            complemented = SassColor.forSpace(
                    space,
                    adjustHueChannel(colorInSpace, colorInSpace.channel0OrNull()),
                    colorInSpace.channel1OrNull(),
                    colorInSpace.channel2OrNull(),
                    colorInSpace.alphaOrNull()
            );
        } else {
            complemented = SassColor.forSpace(
                    space,
                    colorInSpace.channel0OrNull(),
                    colorInSpace.channel1OrNull(),
                    adjustHueChannel(colorInSpace, colorInSpace.channel2OrNull()),
                    colorInSpace.alphaOrNull()
            );
        }
        return complemented.toSpace(color.space(), false);
    }

    /// Adds 180deg to a hue, rejecting missing channels.
    private static double adjustHueChannel(SassColor color, @Nullable Double hue) {
        if (hue == null) {
            throw missingChannelError(color, "hue");
        }
        return hue + 180.0;
    }

    /// Converts one legacy color weight to a fractional first-color contribution.
    ///
    /// During the function-units deprecation period, non-percent units are
    /// accepted by reading the raw magnitude (matching dart-sass).
    ///
    /// @param number the supplied weight number
    /// @param name the parameter name used for diagnostics
    /// @return the weight between zero and one
    /// @throws SassValueException if the number lies outside zero to 100
    private static double legacyWeight(SassNumber number, String name) {
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

    /// Converts a Color 4 weight that must use the percent unit.
    private static double percentWeight(SassNumber number, String name) {
        if (!(number.numeratorUnits().equals(List.of("%"))
                && number.denominatorUnits().isEmpty())) {
            throw new SassValueException(
                    "$" + name + ": Expected " + number + " to have unit \"%\"."
            );
        }
        try {
            return number.valueInRange(0.0, 100.0) / 100.0;
        } catch (SassValueException exception) {
            throw new SassValueException("$" + name + ": " + exception.getMessage());
        }
    }

    /// Inverts every channel of a color already expressed in its target space.
    private static SassColor invertInSpace(SassColor color) {
        var space = color.space();
        var channels = space.channels();
        return switch (space) {
            case HWB -> SassColor.forSpace(
                    space,
                    invertChannel(color, channels.get(0), color.channel0OrNull()),
                    color.channel2OrNull(),
                    color.channel1OrNull(),
                    color.alphaOrNull()
            );
            case HSL, LCH, OKLCH -> SassColor.forSpace(
                    space,
                    invertChannel(color, channels.get(0), color.channel0OrNull()),
                    color.channel1OrNull(),
                    invertChannel(color, channels.get(2), color.channel2OrNull()),
                    color.alphaOrNull()
            );
            default -> SassColor.forSpace(
                    space,
                    invertChannel(color, channels.get(0), color.channel0OrNull()),
                    invertChannel(color, channels.get(1), color.channel1OrNull()),
                    invertChannel(color, channels.get(2), color.channel2OrNull()),
                    color.alphaOrNull()
            );
        };
    }

    /// Returns the inverse of one channel value.
    private static @Nullable Double invertChannel(
            SassColor color,
            ColorChannel channel,
            @Nullable Double value
    ) {
        if (value == null) {
            throw missingChannelError(color, channel.name());
        }
        if (channel.isPolarAngle()) {
            return (value + 180.0) % 360.0;
        }
        if (channel instanceof ColorChannel.Linear linear) {
            if (linear.min() < 0.0) {
                return -value;
            }
            return linear.max() - value;
        }
        throw new SassValueException("Unknown channel " + channel.name() + ".");
    }

    /// Returns whether a named channel is powerless after optional conversion.
    private static SassValue colorIsPowerless(List<SassValue> args) {
        var color = colorInSpace(args.get(0), args.get(2));
        try {
            return SassBoolean.of(color.isChannelPowerless(channelNameArgument(args.get(1))));
        } catch (SassValueException exception) {
            throw prefixParameterException("channel", exception);
        }
    }

    /// Maps a color into gamut using an explicit algorithm.
    private static SassValue colorToGamut(List<SassValue> args) {
        var color = colorArgument(args.get(0), "color");
        var space = spaceOrDefault(color, args.get(1), "space");
        if (args.get(2) instanceof SassNull) {
            throw new SassValueException(
                    "$method: color.to-gamut() requires a $method argument for forwards-"
                            + "compatibility with changes in the CSS spec. Suggestion:\n"
                            + "\n"
                            + "$method: local-minde"
            );
        }
        if (!(args.get(2) instanceof SassString methodString)) {
            throw new SassValueException(
                    "$method: " + args.get(2) + " is not a string."
            );
        }
        if (methodString.hasQuotes()) {
            throw new SassValueException(
                    "$method: Expected " + methodString + " to be an unquoted string."
            );
        }
        GamutMapMethod method;
        try {
            method = GamutMapMethod.fromName(methodString.text());
        } catch (IllegalArgumentException exception) {
            // dart-sass does not prefix this diagnostic with $method.
            throw new SassValueException(exception.getMessage());
        }
        if (!space.isBounded()) {
            return color;
        }
        return color.toSpace(space)
                .toGamut(method)
                .toSpace(color.space(), false);
    }

    /// Resolves an optional space argument, defaulting to the color's own space.
    private static ColorSpace spaceOrDefault(
            SassColor color,
            SassValue spaceValue,
            String name
    ) {
        if (spaceValue instanceof SassNull) {
            return color.space();
        }
        return spaceArgument(spaceValue, name);
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
            throw prefixParameterException(name, exception);
        }
    }

    /// Prefixes a parameter name onto a {@link SassValueException} when absent.
    ///
    /// @param name      the parameter name without a dollar sign
    /// @param exception the original failure
    /// @return a parameter-scoped exception
    private static SassValueException prefixParameterException(
            String name,
            SassValueException exception
    ) {
        var message = Objects.requireNonNull(exception.getMessage(), "exception message");
        if (message.startsWith("$")) {
            return exception;
        }
        return new SassValueException("$" + name + ": " + message);
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

    /// Implements the global color alpha reader and plain-CSS filter fallback.
    private static SassValue opacity(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            return new SassString("opacity(" + number.toCssString() + ")", false);
        }
        if (value.isSpecialNumber()) {
            return CssColorChannels.functionString("opacity", List.of(value));
        }
        warnGlobalBuiltIn(context, "color", "opacity");
        return SassNumber.of(colorArgument(value, "color").alpha(), null);
    }

    /// Module {@code color.opacity()} rejects plain-CSS special-number filters.
    ///
    /// Numbers still serialize as a CSS {@code opacity()} filter for
    /// compatibility, matching dart-sass {@code color-module-compat}.
    ///
    /// @param context the invocation receiving module-compatibility diagnostics
    /// @param args the one color or number argument
    /// @return the alpha channel or an unquoted CSS {@code opacity()} function
    private static SassValue moduleOpacity(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var value = args.get(0);
        if (value instanceof SassNumber number) {
            var result = new SassString(
                    "opacity(" + number.toCssString() + ")",
                    false
            );
            context.deprecate(
                    "Passing a number (" + value + " to color.opacity() is deprecated.\n\n"
                            + "Recommendation: " + result.toCssString(),
                    COLOR_MODULE_COMPAT_CODE
            );
            return result;
        }
        return SassNumber.of(colorArgument(value, "color").alpha(), null);
    }

    /// Compares two colors using Color 4 {@code same} semantics.
    ///
    /// Colors in the same space compare channel-for-channel. Colors in
    /// different spaces compare after conversion to XYZ D65 with missing
    /// channels replaced by zero.
    ///
    /// @param args the two color arguments
    /// @return whether the colors represent the same appearance
    private static SassValue colorSame(List<SassValue> args) {
        var first = colorArgument(args.get(0), "color1");
        var second = colorArgument(args.get(1), "color2");
        if (first.space() == second.space()) {
            return SassBoolean.of(
                    SassFuzzy.equals(first.channel0(), second.channel0())
                            && SassFuzzy.equals(first.channel1(), second.channel1())
                            && SassFuzzy.equals(first.channel2(), second.channel2())
                            && SassFuzzy.equals(first.alpha(), second.alpha())
            );
        }
        return SassBoolean.of(toXyzNoMissing(first).equals(toXyzNoMissing(second)));
    }

    /// Returns the CSS name of one color's space.
    ///
    /// @param args the one color argument
    /// @return the unquoted space name
    private static SassValue colorSpace(List<SassValue> args) {
        return new SassString(colorArgument(args.get(0), "color").space().spaceName(), false);
    }

    /// Converts one color into another known space.
    ///
    /// @param args the color and space-name arguments
    /// @return the converted color
    private static SassValue colorToSpace(List<SassValue> args) {
        var color = colorArgument(args.get(0), "color");
        var space = spaceArgument(args.get(1), "space");
        return color.toSpace(space, false);
    }

    /// Returns whether one color uses a legacy space.
    ///
    /// @param args the one color argument
    /// @return whether the color is legacy
    private static SassValue colorIsLegacy(List<SassValue> args) {
        return SassBoolean.of(colorArgument(args.get(0), "color").isLegacy());
    }

    /// Returns whether one named channel is missing.
    ///
    /// @param args the color and channel-name arguments
    /// @return whether the channel is missing
    private static SassValue colorIsMissing(List<SassValue> args) {
        var color = colorArgument(args.get(0), "color");
        try {
            return SassBoolean.of(color.isChannelMissing(channelNameArgument(args.get(1))));
        } catch (SassValueException exception) {
            throw prefixParameterException("channel", exception);
        }
    }

    /// Returns whether one color is in-gamut in an optional space.
    ///
    /// @param args the color and optional space arguments
    /// @return whether the color is in-gamut
    private static SassValue colorIsInGamut(List<SassValue> args) {
        return SassBoolean.of(colorInSpace(args.get(0), args.get(1)).isInGamut());
    }

    /// Returns one channel of a color, optionally after conversion.
    ///
    /// @param args the color, channel name, and optional space
    /// @return the channel number with its conventional unit
    private static SassValue colorChannel(List<SassValue> args) {
        var color = colorInSpace(args.get(0), args.get(2));
        var channelName = channelNameArgument(args.get(1));
        if ("alpha".equals(channelName)) {
            return SassNumber.of(color.alpha(), null);
        }
        var channels = color.space().channels();
        var channelIndex = -1;
        for (var index = 0; index < channels.size(); index++) {
            if (channels.get(index).name().equals(channelName)) {
                channelIndex = index;
                break;
            }
        }
        if (channelIndex < 0) {
            throw new SassValueException(
                    "$channel: Color " + color + " has no channel named " + channelName + "."
            );
        }
        var channelInfo = channels.get(channelIndex);
        var channelValue = switch (channelIndex) {
            case 0 -> color.channel0();
            case 1 -> color.channel1();
            default -> color.channel2();
        };
        @Nullable String unit = channelInfo.associatedUnit();
        if ("%".equals(unit) && channelInfo instanceof ColorChannel.Linear linear) {
            channelValue = channelValue * 100.0 / linear.max();
        }
        return SassNumber.of(channelValue, unit);
    }

    /// Converts a color into XYZ D65 with missing channels replaced by zero.
    ///
    /// Missing channels must be filled before conversion. Filling after
    /// conversion is wrong because the conversion hub preserves missingness by
    /// discarding the computed destination component for a missing source
    /// channel. {@code color.same()} needs the fully computed XYZ triple.
    private static SassColor toXyzNoMissing(SassColor color) {
        var filled = SassColor.forSpace(
                color.space(),
                color.channel0(),
                color.channel1(),
                color.channel2(),
                color.alpha()
        );
        if (filled.space() == ColorSpace.XYZ_D65) {
            return filled;
        }
        return filled.toSpace(ColorSpace.XYZ_D65, false);
    }

    /// Converts {@code color} into {@code spaceArgument} when present.
    private static SassColor colorInSpace(SassValue colorValue, SassValue spaceValue) {
        var color = colorArgument(colorValue, "color");
        if (spaceValue instanceof SassNull) {
            return color;
        }
        return color.toSpace(spaceArgument(spaceValue, "space"), false);
    }

    /// Parses a color-space name argument.
    private static ColorSpace spaceArgument(SassValue value, String name) {
        if (!(value instanceof SassString string)) {
            throw new SassValueException("$" + name + ": " + value + " is not a string.");
        }
        if (string.hasQuotes()) {
            throw new SassValueException(
                    "$" + name + ": Expected " + value + " to be an unquoted string."
            );
        }
        try {
            return ColorSpace.fromName(string.text());
        } catch (IllegalArgumentException exception) {
            throw new SassValueException("$" + name + ": " + exception.getMessage());
        }
    }

    /// Parses a channel-name argument.
    ///
    /// Sass requires the channel name to be a string. Quoted strings are the
    /// normal form; the error text matches Dart Sass when a non-string is given.
    private static String channelNameArgument(SassValue value) {
        if (!(value instanceof SassString string)) {
            throw new SassValueException("$channel: " + value + " is not a string.");
        }
        if (!string.hasQuotes()) {
            throw new SassValueException(
                    "$channel: Expected " + value + " to be a quoted string."
            );
        }
        // Channel names are case-sensitive and match CSS channel identifiers exactly.
        return string.text();
    }

    /// Adjusts legacy RGB or HSL channels by additive deltas.
    ///
    /// @param args the color and keyword argument list
    /// @return the adjusted color
    private static SassValue colorAdjust(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return updateColorComponents(context, args, ColorUpdateMode.ADJUST);
    }

    /// Scales legacy RGB or HSL channels toward their channel extremes.
    ///
    /// @param args the color and keyword argument list
    /// @return the scaled color
    private static SassValue colorScale(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return updateColorComponents(context, args, ColorUpdateMode.SCALE);
    }

    /// Replaces legacy RGB or HSL channels with absolute values.
    ///
    /// @param args the color and keyword argument list
    /// @return the changed color
    private static SassValue colorChange(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return updateColorComponents(context, args, ColorUpdateMode.CHANGE);
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

    /// Implements {@code adjust}/{@code scale}/{@code change} for any known space.
    ///
    /// Legacy colors without an explicit {@code $space} continue to sniff RGB,
    /// HSL, or HWB channel keywords. Modern colors require either matching
    /// channel names for their current space or an explicit {@code $space}.
    ///
    /// @param args the color and keyword rest list
    /// @param mode the update algorithm
    /// @return the rewritten color in the original space
    private static SassValue updateColorComponents(
            BuiltInCallable.Context context,
            List<SassValue> args,
            ColorUpdateMode mode
    ) {
        var originalColor = colorArgument(args.get(0), "color");
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
        @Nullable SassValue spaceKeyword = keywords.remove("space");
        @Nullable SassValue alphaArg = keywords.remove("alpha");

        SassColor color;
        if (spaceKeyword == null && originalColor.isLegacy() && !keywords.isEmpty()) {
            // Sniffed legacy spaces use legacyMissing=false so powerless channels
            // (e.g. black's hue) become 0 and multi-channel adjust still works.
            @Nullable ColorSpace sniffed = sniffLegacyColorSpace(keywords.keySet());
            color = sniffed == null
                    ? originalColor
                    : originalColor.toSpace(sniffed, false);
        } else if (spaceKeyword == null) {
            color = originalColor;
        } else {
            // Explicit $space keeps missing/powerless channels so adjusting them
            // fails with the "modifying missing channels" diagnostic.
            color = originalColor.toSpace(spaceArgument(spaceKeyword, "space"), true);
        }

        // Even with no channel keywords, converting through $space (or the
        // sniffed legacy space) applies powerless-channel → missing rules on
        // the round-trip back to the original space.
        if (keywords.isEmpty() && alphaArg == null) {
            return color.toSpace(originalColor.space(), false);
        }

        var channels = color.space().channels();
        @Nullable SassValue channel0Arg = null;
        @Nullable SassValue channel1Arg = null;
        @Nullable SassValue channel2Arg = null;
        for (var entry : keywords.entrySet()) {
            if (entry.getKey().equals(channels.get(0).name())) {
                channel0Arg = entry.getValue();
            } else if (entry.getKey().equals(channels.get(1).name())) {
                channel1Arg = entry.getValue();
            } else if (entry.getKey().equals(channels.get(2).name())) {
                channel2Arg = entry.getValue();
            } else {
                throw new SassValueException(
                        "$" + entry.getKey() + ": Color space " + color.space()
                                + " doesn't have a channel with this name."
                );
            }
        }

        SassColor result = switch (mode) {
            case CHANGE -> changeColor(
                    context,
                    color,
                    channel0Arg,
                    channel1Arg,
                    channel2Arg,
                    alphaArg
            );
            case ADJUST -> adjustColor(
                    context,
                    color,
                    channel0Arg,
                    channel1Arg,
                    channel2Arg,
                    alphaArg
            );
            case SCALE -> scaleColor(color, channel0Arg, channel1Arg, channel2Arg, alphaArg);
        };
        return result.toSpace(originalColor.space(), false);
    }

    /// Implements absolute channel replacement.
    private static SassColor changeColor(
            BuiltInCallable.Context context,
            SassColor color,
            @Nullable SassValue channel0Arg,
            @Nullable SassValue channel1Arg,
            @Nullable SassValue channel2Arg,
            @Nullable SassValue alphaArg
    ) {
        return SassColor.forSpace(
                color.space(),
                channelValueForChange(context, channel0Arg, color, 0),
                channelValueForChange(context, channel1Arg, color, 1),
                channelValueForChange(context, channel2Arg, color, 2),
                alphaValueForChange(context, color, alphaArg)
        );
    }

    /// Implements additive channel adjustment.
    private static SassColor adjustColor(
            BuiltInCallable.Context context,
            SassColor color,
            @Nullable SassValue channel0Arg,
            @Nullable SassValue channel1Arg,
            @Nullable SassValue channel2Arg,
            @Nullable SassValue alphaArg
    ) {
        var channels = color.space().channels();
        @Nullable Double alpha = adjustChannel(
                context,
                color,
                ColorChannel.ALPHA,
                color.alphaOrNull(),
                alphaArg == null ? null : numberArgument(alphaArg, "alpha")
        );
        if (alpha != null) {
            alpha = clamp(alpha, 0.0, 1.0);
        }
        return SassColor.forSpace(
                color.space(),
                adjustChannel(
                        context,
                        color,
                        channels.get(0),
                        color.channel0OrNull(),
                        channel0Arg == null ? null : numberArgument(channel0Arg, channels.get(0).name())
                ),
                adjustChannel(
                        context,
                        color,
                        channels.get(1),
                        color.channel1OrNull(),
                        channel1Arg == null ? null : numberArgument(channel1Arg, channels.get(1).name())
                ),
                adjustChannel(
                        context,
                        color,
                        channels.get(2),
                        color.channel2OrNull(),
                        channel2Arg == null ? null : numberArgument(channel2Arg, channels.get(2).name())
                ),
                alpha
        );
    }

    /// Implements percent scaling of linear channels.
    private static SassColor scaleColor(
            SassColor color,
            @Nullable SassValue channel0Arg,
            @Nullable SassValue channel1Arg,
            @Nullable SassValue channel2Arg,
            @Nullable SassValue alphaArg
    ) {
        var channels = color.space().channels();
        return SassColor.forSpace(
                color.space(),
                scaleChannelValue(
                        color,
                        channels.get(0),
                        color.channel0OrNull(),
                        channel0Arg == null ? null : numberArgument(channel0Arg, channels.get(0).name())
                ),
                scaleChannelValue(
                        color,
                        channels.get(1),
                        color.channel1OrNull(),
                        channel1Arg == null ? null : numberArgument(channel1Arg, channels.get(1).name())
                ),
                scaleChannelValue(
                        color,
                        channels.get(2),
                        color.channel2OrNull(),
                        channel2Arg == null ? null : numberArgument(channel2Arg, channels.get(2).name())
                ),
                scaleChannelValue(
                        color,
                        ColorChannel.ALPHA,
                        color.alphaOrNull(),
                        alphaArg == null ? null : numberArgument(alphaArg, "alpha")
                )
        );
    }

    /// Resolves one absolute channel value for {@code color.change()}.
    private static @Nullable Double channelValueForChange(
            BuiltInCallable.Context context,
            @Nullable SassValue argument,
            SassColor color,
            int index
    ) {
        var channel = color.space().channels().get(index);
        if (argument == null) {
            return switch (index) {
                case 0 -> color.channel0OrNull();
                case 1 -> color.channel1OrNull();
                default -> color.channel2OrNull();
            };
        }
        if (isNone(argument)) {
            return null;
        }
        if (!(argument instanceof SassNumber number)) {
            throw new SassValueException(
                    "$" + channel.name() + ": " + argument
                            + " is not a number or unquoted \"none\"."
            );
        }
        // Legacy HSL/HWB still accept non-canonical units during the deprecation
        // period, matching dart-sass {@code _colorFromChannels}.
        if (channel.isPolarAngle()
                && (color.space() == ColorSpace.HSL || color.space() == ColorSpace.HWB)) {
            return angleValue(context, number, channel.name());
        }
        if (color.space() == ColorSpace.HSL
                && channel instanceof ColorChannel.Linear
                && ("saturation".equals(channel.name()) || "lightness".equals(channel.name()))) {
            checkPercent(context, number, channel.name());
            return channelFromValue(channel, forcePercent(number), false);
        }
        if (color.space() == ColorSpace.HWB
                && channel instanceof ColorChannel.Linear
                && ("whiteness".equals(channel.name()) || "blackness".equals(channel.name()))) {
            if (!(number.numeratorUnits().equals(List.of("%"))
                    && number.denominatorUnits().isEmpty())) {
                throw new SassValueException(
                        "$" + channel.name() + ": Expected " + number + " to have unit \"%\"."
                );
            }
            return channelFromValue(channel, number, false);
        }
        return channelFromValue(channel, number, false);
    }

    /// Resolves alpha for {@code color.change()}.
    private static @Nullable Double alphaValueForChange(
            BuiltInCallable.Context context,
            SassColor color,
            @Nullable SassValue alphaArg
    ) {
        if (alphaArg == null) {
            return color.alphaOrNull();
        }
        if (isNone(alphaArg)) {
            return null;
        }
        if (!(alphaArg instanceof SassNumber number)) {
            throw new SassValueException(
                    "$alpha: " + alphaArg + " is not a number or unquoted \"none\"."
            );
        }
        if (number.isUnitless()) {
            return number.valueInRange(0.0, 1.0, "alpha");
        }
        if (number.numeratorUnits().equals(List.of("%")) && number.denominatorUnits().isEmpty()) {
            return number.valueInRange(0.0, 100.0, "alpha") / 100.0;
        }
        deprecateChangeAlpha(context, number);
        // Preserve historical unitless interpretation for non-percent units.
        return number.valueInRange(0.0, 1.0, "alpha");
    }

    /// Returns {@code number} with unit {@code %} regardless of its original unit.
    private static SassNumber forcePercent(SassNumber number) {
        if (number.numeratorUnits().equals(List.of("%")) && number.denominatorUnits().isEmpty()) {
            return number;
        }
        return SassNumber.of(number.value(), "%");
    }

    /// Converts an angle to degrees and reports legacy non-angle units.
    ///
    /// @param context the invocation receiving a possible deprecation
    /// @param number the angle argument
    /// @param name the parameter name without a leading dollar sign
    /// @return the degree value or legacy bare magnitude
    static double angleValue(
            BuiltInCallable.Context context,
            SassNumber number,
            String name
    ) {
        if (number.isUnitless()) {
            return number.value();
        }
        try {
            return number.coerce(List.of("deg"), List.of()).value();
        } catch (SassValueException exception) {
            context.deprecate(
                    "$" + name + ": Passing a unit other than deg ("
                            + number + ") is deprecated.\n\n"
                            + "To preserve current behavior: "
                            + number.unitSuggestion(name, null) + "\n\n"
                            + "See https://sass-lang.com/d/function-units",
                    FUNCTION_UNITS_CODE
            );
            return number.value();
        }
    }

    /// Reports a number that legacy color behavior treats as a percentage.
    ///
    /// @param context the invocation receiving a possible deprecation
    /// @param number the percentage argument
    /// @param name the parameter name without a leading dollar sign
    static void checkPercent(
            BuiltInCallable.Context context,
            SassNumber number,
            String name
    ) {
        if (hasPercentUnit(number)) {
            return;
        }
        context.deprecate(
                "$" + name + ": Passing a number without unit % ("
                        + number + ") is deprecated.\n\n"
                        + "To preserve current behavior: "
                        + number.unitSuggestion(name, "%") + "\n\n"
                        + "More info: https://sass-lang.com/d/function-units",
                FUNCTION_UNITS_CODE
        );
    }

    /// Reports a unitful list index accepted for legacy compatibility.
    ///
    /// @param context the invocation receiving the deprecation
    /// @param value the list index argument
    /// @param name the parameter name without a leading dollar sign
    private static void deprecateUnitfulIndex(
            BuiltInCallable.Context context,
            SassValue value,
            String name
    ) {
        var number = numberArgument(value, name);
        if (number.isUnitless()) {
            return;
        }
        context.deprecate(
                "$" + name + ": Passing a number with unit "
                        + number.unitString() + " is deprecated.\n\n"
                        + "To preserve current behavior: "
                        + number.unitSuggestion(name, null) + "\n\n"
                        + "More info: https://sass-lang.com/d/function-units",
                FUNCTION_UNITS_CODE
        );
    }

    /// Reports a non-percent alpha replacement accepted by color.change().
    ///
    /// @param context the invocation receiving the deprecation
    /// @param number the alpha argument
    private static void deprecateChangeAlpha(
            BuiltInCallable.Context context,
            SassNumber number
    ) {
        context.deprecate(
                "$alpha: Passing a unit other than % (" + number
                        + ") is deprecated.\n\n"
                        + "To preserve current behavior: "
                        + number.unitSuggestion("alpha", null) + "\n\n"
                        + "See https://sass-lang.com/d/function-units",
                FUNCTION_UNITS_CODE
        );
    }

    /// Reports a unitful alpha delta accepted by color.adjust().
    ///
    /// @param context the invocation receiving the deprecation
    /// @param number the alpha adjustment
    private static void deprecateAdjustAlpha(
            BuiltInCallable.Context context,
            SassNumber number
    ) {
        context.deprecate(
                "$alpha: Passing a number with unit "
                        + number.unitString() + " is deprecated.\n\n"
                        + "To preserve current behavior: "
                        + number.unitSuggestion("alpha", null) + "\n\n"
                        + "More info: https://sass-lang.com/d/function-units",
                FUNCTION_UNITS_CODE
        );
    }

    /// Returns whether a number has exactly one percent numerator unit.
    ///
    /// @param number the number to inspect
    /// @return whether the number's sole unit is percent
    private static boolean hasPercentUnit(SassNumber number) {
        return number.numeratorUnits().equals(List.of("%"))
                && number.denominatorUnits().isEmpty();
    }

    /// Adjusts one channel by an additive delta.
    private static @Nullable Double adjustChannel(
            BuiltInCallable.Context context,
            SassColor color,
            ColorChannel channel,
            @Nullable Double oldValue,
            @Nullable SassNumber adjustment
    ) {
        if (adjustment == null) {
            return oldValue;
        }
        if (oldValue == null) {
            throw missingChannelError(color, channel.name());
        }
        SassNumber delta = adjustment;
        if (channel.isPolarAngle()
                && (color.space() == ColorSpace.HSL || color.space() == ColorSpace.HWB)) {
            // Legacy HSL/HWB still accept non-angle units during deprecation.
            delta = SassNumber.of(
                    angleValue(context, adjustment, channel.name()),
                    null
            );
        } else if ((color.space() == ColorSpace.HSL)
                && channel instanceof ColorChannel.Linear
                && ("saturation".equals(channel.name()) || "lightness".equals(channel.name()))) {
            // Legacy HSL treats the numeric magnitude as a percentage regardless of
            // the original unit (dart-sass deprecation period behavior).
            checkPercent(context, delta, channel.name());
            delta = forcePercent(delta);
        } else if (channel == ColorChannel.ALPHA && !delta.isUnitless()) {
            // Legacy alpha treats any unit (including %) as unitless magnitude.
            deprecateAdjustAlpha(context, delta);
            delta = SassNumber.of(delta.value(), null);
        }
        var result = oldValue + channelFromValue(channel, delta, false);
        if (channel instanceof ColorChannel.Linear linear) {
            return clampAdjustedChannel(
                    result,
                    oldValue,
                    linear.min(),
                    linear.max(),
                    linear.lowerClamped(),
                    linear.upperClamped()
            );
        }
        return result;
    }

    /// Scales one channel toward its conventional extremes.
    private static @Nullable Double scaleChannelValue(
            SassColor color,
            ColorChannel channel,
            @Nullable Double oldValue,
            @Nullable SassNumber factorArg
    ) {
        if (factorArg == null) {
            return oldValue;
        }
        if (!(channel instanceof ColorChannel.Linear linear)) {
            throw new SassValueException("$" + channel.name() + ": Channel isn't scalable.");
        }
        if (oldValue == null) {
            throw missingChannelError(color, channel.name());
        }
        return scaleChannel(oldValue, factorArg, channel.name(), linear.min(), linear.max());
    }

    /// Converts a Sass number into one channel's native unit system.
    ///
    /// Matches dart-sass {@code _channelFromValue}: unitless numbers are already in
    /// the channel's native scale, while percentages map {@code 0%..100%} onto
    /// {@code 0..max}. Channels that {@link ColorChannel.Linear#requiresPercent()}
    /// reject unitless values.
    private static double channelFromValue(
            ColorChannel channel,
            SassNumber number,
            boolean clamp
    ) {
        if (channel.isPolarAngle()) {
            return hueDegrees(number);
        }
        if (!(channel instanceof ColorChannel.Linear linear)) {
            throw new SassValueException("Unknown channel " + channel.name() + ".");
        }
        if (linear.requiresPercent()
                && !(number.numeratorUnits().equals(List.of("%"))
                && number.denominatorUnits().isEmpty())) {
            throw new SassValueException(
                    "$" + channel.name() + ": Expected " + number + " to have unit \"%\"."
            );
        }
        double value = percentageOrUnitless(number, linear.max(), channel.name());
        if (!clamp) {
            return value;
        }
        double lower = linear.lowerClamped() ? linear.min() : Double.NEGATIVE_INFINITY;
        double upper = linear.upperClamped() ? linear.max() : Double.POSITIVE_INFINITY;
        return clamp(value, lower, upper);
    }

    /// Builds the dart-sass missing-channel diagnostic for adjust/scale/change.
    private static SassValueException missingChannelError(SassColor color, String channel) {
        return new SassValueException(
                "$" + channel + ": Because the CSS working group is still deciding on the "
                        + "best behavior, Sass doesn't currently support modifying missing "
                        + "channels (color: " + color.toCssString() + ")."
        );
    }

    /// Creates a sass:color stub for a removed legacy global color function.
    private static BuiltInCallable removedColorFunction(
            String name,
            String argument,
            boolean negative
    ) {
        return BuiltInCallable.of(
                name,
                List.of("color", "amount"),
                args -> {
                    throw new SassValueException(
                            "The function " + name + "() isn't in the sass:color module.\n"
                                    + "\n"
                                    + "Recommendation: color.adjust(" + args.get(0) + ", $" + argument
                                    + ": " + (negative ? "-" : "") + args.get(1) + ")\n"
                                    + "\n"
                                    + "More info: https://sass-lang.com/documentation/functions/color#"
                                    + name
                    );
                }
        );
    }

    /// Returns whether a value is the unquoted identifier {@code none}.
    private static boolean isNone(SassValue value) {
        return value instanceof SassString string
                && !string.hasQuotes()
                && "none".equals(string.text());
    }

    /// Sniffs whether keyword names target RGB, HSL, or HWB channels.
    ///
    /// @param names the remaining keyword names
    /// @return the sniffed space, or {@code null} when only alpha may remain
    private static @Nullable ColorSpace sniffLegacyColorSpace(Iterable<String> names) {
        var sawHue = false;
        for (var name : names) {
            switch (name) {
                case "red", "green", "blue" -> {
                    return ColorSpace.RGB;
                }
                case "saturation", "lightness" -> {
                    return ColorSpace.HSL;
                }
                case "whiteness", "blackness" -> {
                    return ColorSpace.HWB;
                }
                case "hue" -> sawHue = true;
                default -> {
                }
            }
        }
        return sawHue ? ColorSpace.HSL : null;
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
            throw new SassValueException(
                    "$hue: Expected " + number + " to have an angle unit (deg, grad, rad, turn)."
            );
        }
    }

    /// Clamps a finite value into an inclusive range.
    private static double clamp(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }

    /// Returns the keyword map carried by an argument list.
    ///
    /// @param args the one argument-list value
    /// @return a Sass map keyed by unquoted keyword names
    /// @throws SassValueException if the value is not an argument list
    private static SassValue keywords(List<SassValue> args) {
        var value = args.get(0);
        if (!(value instanceof SassArgumentList argumentList)) {
            // dart-sass parenthesizes space lists in this diagnostic.
            var rendered = value instanceof org.glavo.sassfx.internal.value.SassList list
                    && list.separator() == ListSeparator.SPACE
                    && !list.hasBrackets()
                    ? "(" + value + ")"
                    : value.toString();
            throw new SassValueException("$args: " + rendered + " is not an argument list.");
        }
        var contents = new LinkedHashMap<SassValue, SassValue>();
        for (var entry : argumentList.keywords().entrySet()) {
            contents.put(new SassString(entry.getKey(), false), entry.getValue());
        }
        return new SassMap(contents);
    }
}
