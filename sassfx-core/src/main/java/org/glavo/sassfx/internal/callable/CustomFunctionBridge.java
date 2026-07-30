// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.callable;

import org.glavo.sassfx.internal.value.SassValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Bridges one public custom-function definition to evaluator values.
///
/// Implementations own all conversion between the public callback value model
/// and the evaluator representation. The callable layer owns signature
/// binding and active-compilation context.
@ApiStatus.Internal
@NotNullByDefault
public interface CustomFunctionBridge {
    /// Returns the complete Sass function signature.
    ///
    /// @return the signature to parse and bind
    String signature();

    /// Invokes the public callback with already-bound evaluator arguments.
    ///
    /// @param arguments immutable arguments in declaration order
    /// @return the evaluator result, or `null` if the callback violated its
    /// non-null return contract
    /// @throws Exception if the callback fails
    @Nullable SassValue invoke(
            @Unmodifiable List<SassValue> arguments
    ) throws Exception;
}
