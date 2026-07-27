// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.callable;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies an adapter-level failure that must escape Sass diagnostics.
///
/// Ordinary callback exceptions are Sass function failures. Protocol adapters
/// may use this marker when malformed remote data requires terminating the
/// surrounding transport rather than producing a compilation diagnostic.
@ApiStatus.Internal
@NotNullByDefault
public abstract class FatalSassCallbackException extends RuntimeException {
    /// Identifies the serialized exception form.
    private static final long serialVersionUID = 1L;

    /// Creates a fatal callback failure.
    ///
    /// @param message the failure detail
    protected FatalSassCallbackException(String message) {
        super(message);
    }
}
