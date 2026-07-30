// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.Serial;

/// Reports an internal Sass value operation that is undefined for its operands.
///
/// This exception does not carry source information. An evaluator must attach
/// the span of the expression that invoked the operation before exposing the
/// failure outside the value layer.
@ApiStatus.Internal
@NotNullByDefault
public final class SassValueException
        extends org.glavo.sassfx.SassValueException {
    /// Contains the serialization version of this exception representation.
    @Serial
    private static final long serialVersionUID = 1L;

    /// Creates a value-operation failure.
    ///
    /// @param message the human-readable Sass failure message
    public SassValueException(String message) {
        super(message);
    }
}
