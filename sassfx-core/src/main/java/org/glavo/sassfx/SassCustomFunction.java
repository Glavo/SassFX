// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.glavo.sassfx.internal.callable.CustomFunctionBridge;
import org.glavo.sassfx.internal.callable.CustomFunctionCallable;
import org.glavo.sassfx.internal.value.SassFunction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Defines one synchronous Java function callable from Sass.
///
/// The callback may be invoked concurrently when the same compile options are
/// used by concurrent compilations. Implementations must therefore be
/// thread-safe. Callbacks run on the compiling thread and block that
/// compilation until they return.
///
/// @param signature a complete Sass function signature such as
///                  {@code pow($base, $exponent)} or
///                  {@code collect($values...)}
/// @param callback the callback invoked with arguments bound by the signature
@NotNullByDefault
public record SassCustomFunction(
        String signature,
        Callback callback
) {
    /// Ensures that both components are present and the signature is nonempty.
    public SassCustomFunction {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(callback, "callback");
        if (signature.isBlank()) {
            throw new IllegalArgumentException("signature must not be blank");
        }
    }

    /// Parses and validates one complete custom function signature.
    ///
    /// This performs the same signature validation used when compile options
    /// register a custom function. It does not invoke a callback.
    ///
    /// @param signature the complete function signature
    /// @throws IllegalArgumentException if the signature is not one complete
    /// function signature
    public static void validateSignature(String signature) {
        new SassCustomFunction(
                signature,
                ignored -> SassValue.nullValue()
        ).toCallable();
    }

    /// Creates the evaluator callable for this definition.
    ///
    /// @return a newly parsed callable
    CustomFunctionCallable toCallable() {
        return CustomFunctionCallable.parse(new Bridge(signature, callback));
    }

    /// Creates a first-class evaluator function for the active compilation.
    ///
    /// @return the compilation-bound function value
    /// @throws IllegalStateException if no custom callback is active
    SassFunction toFunctionValue() {
        return new SassFunction(
                toCallable(),
                CustomFunctionCallable.callbackCompilationContext()
        );
    }

    /// Implements one synchronous custom Sass function body.
    @FunctionalInterface
    @NotNullByDefault
    public interface Callback {
        /// Computes the function result.
        ///
        /// Ordinary parameters appear in declaration order after positional,
        /// keyword, and default binding. A rest parameter appears as a final
        /// [SassValueType#ARGUMENT_LIST] value whose [SassValue#keywords()]
        /// accessor marks leftover keywords as consumed.
        ///
        /// @param arguments an immutable bound argument list
        /// @return the non-{@code null} Sass result
        /// @throws Exception if the call fails
        SassValue apply(@Unmodifiable List<SassValue> arguments) throws Exception;
    }

    /// Converts values at the public callback boundary.
    ///
    /// @param signature the complete Sass function signature
    /// @param callback the public callback
    @NotNullByDefault
    private record Bridge(
            String signature,
            Callback callback
    ) implements CustomFunctionBridge {
        /// Validates the retained public function definition.
        private Bridge {
            Objects.requireNonNull(signature, "signature");
            Objects.requireNonNull(callback, "callback");
        }

        /// Invokes the callback with immutable public wrappers.
        ///
        /// @param arguments immutable evaluator arguments
        /// @return the evaluator result, or `null` for an invalid callback
        /// return
        /// @throws Exception if the callback fails
        @Override
        public @Nullable org.glavo.sassfx.internal.value.SassValue invoke(
                @Unmodifiable
                List<org.glavo.sassfx.internal.value.SassValue> arguments
        ) throws Exception {
            var publicArguments = new ArrayList<SassValue>(arguments.size());
            for (var argument : arguments) {
                publicArguments.add(SassValue.wrap(argument));
            }
            @Nullable var result = callback.apply(
                    List.copyOf(publicArguments)
            );
            return result == null ? null : result.internalValue();
        }
    }
}
