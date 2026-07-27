// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

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
    /// Validates a custom function definition.
    public SassCustomFunction {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(callback, "callback");
        if (signature.isBlank()) {
            throw new IllegalArgumentException("signature must not be blank");
        }
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
}
