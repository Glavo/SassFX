// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.Serial;
import java.util.Objects;

/// Reports a callback failure that must abort its surrounding integration.
///
/// A [SassCustomFunction] normally converts a thrown exception into a
/// source-associated Sass compilation failure. Protocol and transport
/// adapters may extend this class when continuing the compilation would hide
/// a connection-level failure. The compiler will propagate such an exception
/// unchanged instead of converting it into a Sass diagnostic.
@NotNullByDefault
public abstract class SassCallbackAbortException extends RuntimeException {
    /// Identifies the serialized exception form.
    @Serial
    private static final long serialVersionUID = 1L;

    /// Creates an aborting callback failure.
    ///
    /// @param message the failure detail
    protected SassCallbackAbortException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}
