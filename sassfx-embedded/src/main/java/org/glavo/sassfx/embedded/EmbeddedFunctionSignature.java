// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import org.glavo.sassfx.SassCustomFunction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Validates host function signatures using Embedded Sass error semantics.
@NotNullByDefault
final class EmbeddedFunctionSignature {
    /// Prevents instantiation.
    private EmbeddedFunctionSignature() {
    }

    /// Returns the protocol-compatible error for an invalid signature.
    ///
    /// @param signature the complete signature supplied by the host
    /// @return the error message, or `null` if the signature is valid
    static @Nullable String error(String signature) {
        Objects.requireNonNull(signature, "signature");
        if (signature.isEmpty()) {
            return "Invalid signature \"\": Expected identifier.";
        }

        var closingParenthesis = signature.lastIndexOf(')');
        if (closingParenthesis >= 0
                && closingParenthesis != signature.length() - 1) {
            return "Invalid signature \"" + signature
                    + "\": expected no more input.";
        }

        try {
            SassCustomFunction.validateSignature(signature);
            return null;
        } catch (IllegalArgumentException failure) {
            var message = Objects.requireNonNullElse(
                    failure.getMessage(),
                    "Invalid signature \"" + signature + "\"."
            );
            if (message.startsWith("Invalid custom function signature ")) {
                return "Invalid signature " + message.substring(
                        "Invalid custom function signature ".length()
                );
            }
            return message;
        }
    }
}
