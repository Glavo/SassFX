// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import com.sass_lang.embedded_protocol.ProtocolErrorType;
import org.glavo.scssfx.internal.callable.FatalSassCallbackException;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Reports a connection-level Embedded Sass protocol violation.
@NotNullByDefault
final class EmbeddedProtocolException extends FatalSassCallbackException {
    /// Identifies the serialized exception form.
    private static final long serialVersionUID = 1L;

    /// Contains the protocol error category.
    private final ProtocolErrorType type;

    /// Contains the associated request ID.
    private final int requestId;

    /// Creates a protocol violation.
    ///
    /// @param type the protocol error category
    /// @param requestId the associated request ID
    /// @param message the diagnostic message
    EmbeddedProtocolException(
            ProtocolErrorType type,
            int requestId,
            String message
    ) {
        super(Objects.requireNonNull(message, "message"));
        this.type = Objects.requireNonNull(type, "type");
        this.requestId = requestId;
    }

    /// Returns the protocol error category.
    ///
    /// @return the error type
    ProtocolErrorType type() {
        return type;
    }

    /// Returns the associated request ID.
    ///
    /// @return the raw unsigned request ID bits
    int requestId() {
        return requestId;
    }
}
