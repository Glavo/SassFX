// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.callable;

import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// A built-in Sass function with named parameters and optional rest support.
@ApiStatus.Internal
@NotNullByDefault
public final class BuiltInCallable implements Callable {
    /// Contains the normalized function name.
    private final String name;

    /// Contains ordinary parameters in source order.
    private final @Unmodifiable List<Param> parameters;

    /// Contains the rest parameter name, or {@code null}.
    private final @Nullable String restParameter;

    /// Contains the number of required leading parameters.
    private final int minArgs;

    /// Contains the implementation invoked after argument binding.
    private final Function<@Unmodifiable List<SassValue>, SassValue> callback;

    /// Creates a built-in callable.
    private BuiltInCallable(
            String name,
            List<Param> parameters,
            @Nullable String restParameter,
            int minArgs,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.parameters = List.copyOf(parameters);
        this.restParameter = restParameter;
        this.minArgs = minArgs;
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    /// Creates a built-in function with required positional parameters.
    ///
    /// @param name       the function name
    /// @param paramNames the parameter names in order
    /// @param callback   the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            List<String> paramNames,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        var params = new ArrayList<Param>(paramNames.size());
        for (var paramName : paramNames) {
            params.add(new Param(paramName, null));
        }
        return new BuiltInCallable(name, params, null, params.size(), callback);
    }

    /// Creates a built-in function with optional trailing parameters.
    ///
    /// @param name     the function name
    /// @param params   the parameters including defaults
    /// @param minArgs  the number of required leading parameters
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            List<Param> params,
            int minArgs,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        return new BuiltInCallable(name, params, null, minArgs, callback);
    }

    /// Creates a built-in function with a rest parameter.
    ///
    /// @param name           the function name
    /// @param paramNames     ordinary parameter names
    /// @param restParameter  the rest parameter name
    /// @param callback       the implementation
    /// @return the callable
    public static BuiltInCallable withRest(
            String name,
            List<String> paramNames,
            String restParameter,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        var params = new ArrayList<Param>(paramNames.size());
        for (var paramName : paramNames) {
            params.add(new Param(paramName, null));
        }
        return new BuiltInCallable(
                name,
                params,
                Objects.requireNonNull(restParameter, "restParameter"),
                params.size(),
                callback
        );
    }

    /// Compatibility factory used by older call sites with arity-only APIs.
    ///
    /// @param name     the function name
    /// @param arity    the exact arity
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            int arity,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        var params = new ArrayList<Param>(arity);
        for (var index = 0; index < arity; index++) {
            params.add(new Param("arg" + (index + 1), null));
        }
        return new BuiltInCallable(name, params, null, arity, callback);
    }

    /// Compatibility factory for arity ranges without names.
    ///
    /// @param name     the function name
    /// @param minArgs  the minimum arity
    /// @param maxArgs  the maximum arity
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            int minArgs,
            int maxArgs,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        if (maxArgs == Integer.MAX_VALUE) {
            var params = new ArrayList<Param>(Math.max(0, minArgs - 1));
            for (var index = 0; index < Math.max(0, minArgs - 1); index++) {
                params.add(new Param("arg" + (index + 1), null));
            }
            return new BuiltInCallable(name, params, "args", minArgs, callback);
        }
        var params = new ArrayList<Param>(maxArgs);
        for (var index = 0; index < maxArgs; index++) {
            params.add(new Param("arg" + (index + 1), null));
        }
        return new BuiltInCallable(name, params, null, minArgs, callback);
    }

    /// Returns the normalized function name.
    ///
    /// @return the hyphenated name
    @Override
    public String name() {
        return name;
    }

    /// Returns ordinary parameters.
    ///
    /// @return the parameter list
    public @Unmodifiable List<Param> parameters() {
        return parameters;
    }

    /// Returns the rest parameter name.
    ///
    /// @return the rest name, or {@code null}
    public @Nullable String restParameter() {
        return restParameter;
    }

    /// Returns the number of required leading parameters.
    ///
    /// @return the minimum arity
    public int minArgs() {
        return minArgs;
    }

    /// Invokes this function with already-bound positional values.
    ///
    /// @param bound the bound argument values
    /// @return the function result
    public SassValue invoke(@Unmodifiable List<SassValue> bound) {
        return callback.apply(Objects.requireNonNull(bound, "bound"));
    }

    /// One built-in parameter declaration.
    ///
    /// @param name          the normalized parameter name
    /// @param defaultValue  the default value, or {@code null} when required
    public record Param(String name, @Nullable SassValue defaultValue) {
        /// Creates a parameter.
        public Param {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name must not be empty");
            }
        }

        /// Creates a required parameter.
        ///
        /// @param name the parameter name
        /// @return the parameter
        public static Param required(String name) {
            return new Param(name, null);
        }

        /// Creates an optional parameter.
        ///
        /// @param name         the parameter name
        /// @param defaultValue the default value
        /// @return the parameter
        public static Param optional(String name, SassValue defaultValue) {
            return new Param(name, Objects.requireNonNull(defaultValue, "defaultValue"));
        }
    }
}
