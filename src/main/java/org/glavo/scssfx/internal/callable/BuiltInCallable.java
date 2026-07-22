// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.callable;

import org.glavo.scssfx.internal.value.SassValue;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// A built-in Sass function with fixed positional arity.
@ApiStatus.Internal
@NotNullByDefault
public final class BuiltInCallable implements Callable {
    /// Contains the normalized function name.
    private final String name;

    /// Contains the minimum accepted positional argument count.
    private final int minArgs;

    /// Contains the maximum accepted positional argument count.
    private final int maxArgs;

    /// Contains the implementation invoked after arity validation.
    private final Function<@Unmodifiable List<SassValue>, SassValue> callback;

    /// Creates a built-in callable.
    ///
    /// @param name     the normalized function name
    /// @param minArgs  the inclusive minimum positional arity
    /// @param maxArgs  the inclusive maximum positional arity
    /// @param callback the implementation
    private BuiltInCallable(
            String name,
            int minArgs,
            int maxArgs,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        if (minArgs < 0 || maxArgs < minArgs) {
            throw new IllegalArgumentException("invalid arity range");
        }
        this.name = Objects.requireNonNull(name, "name");
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    /// Creates a built-in function with an exact positional arity.
    ///
    /// @param name     the normalized function name
    /// @param arity    the required positional argument count
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            int arity,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        return new BuiltInCallable(name, arity, arity, callback);
    }

    /// Creates a built-in function with an inclusive positional arity range.
    ///
    /// @param name     the normalized function name
    /// @param minArgs  the inclusive minimum positional arity
    /// @param maxArgs  the inclusive maximum positional arity
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            int minArgs,
            int maxArgs,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        return new BuiltInCallable(name, minArgs, maxArgs, callback);
    }

    /// Returns the normalized function name.
    ///
    /// @return the hyphenated name
    @Override
    public String name() {
        return name;
    }

    /// Invokes this function after validating the positional argument count.
    ///
    /// Named arguments are rejected. Rest arguments must already have been
    /// expanded into the positional list by the evaluator.
    ///
    /// @param positional the evaluated positional arguments
    /// @return the function result
    /// @throws SassValueException if the arity is wrong
    public SassValue invoke(@Unmodifiable List<SassValue> positional) {
        Objects.requireNonNull(positional, "positional");
        if (positional.size() < minArgs || positional.size() > maxArgs) {
            if (minArgs == maxArgs) {
                throw new SassValueException(
                        "Only " + minArgs + " "
                                + (minArgs == 1 ? "argument" : "arguments")
                                + " allowed, but " + positional.size() + " "
                                + (positional.size() == 1 ? "was" : "were")
                                + " passed."
                );
            }
            throw new SassValueException(
                    "Between " + minArgs + " and " + maxArgs
                            + " arguments allowed, but " + positional.size() + " "
                            + (positional.size() == 1 ? "was" : "were")
                            + " passed."
            );
        }
        return callback.apply(positional);
    }
}
