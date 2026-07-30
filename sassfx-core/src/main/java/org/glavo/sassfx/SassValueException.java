// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.Serial;
import java.util.Objects;

/// Reports an invalid operation on one or more Sass values.
///
/// This exception has no source location. When it escapes a
/// [SassCustomFunction], the compiler associates it with the Sass call before
/// reporting the compilation failure.
@NotNullByDefault
public class SassValueException extends RuntimeException {
    /// Identifies the serialized exception form.
    @Serial
    private static final long serialVersionUID = 1L;

    /// Creates a Sass value-operation failure.
    ///
    /// @param message the human-readable Sass failure detail
    public SassValueException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}
