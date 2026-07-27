// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.evaluate;

import org.glavo.sassfx.internal.value.SassValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents control flow returned by statement evaluation.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface StatementResult permits StatementResult.Continue, StatementResult.ReturnValue {
    /// Continues evaluation with the next statement.
    Continue CONTINUE = new Continue();

    /// Continues evaluation with the next statement.
    record Continue() implements StatementResult {
    }

    /// Returns a value from a user-defined function.
    ///
    /// @param value the returned Sass value
    record ReturnValue(SassValue value) implements StatementResult {
        /// Creates a return result.
        public ReturnValue {
            Objects.requireNonNull(value, "value");
        }
    }
}
