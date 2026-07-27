// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.OutboundMessage;
import com.sass_lang.embedded_protocol.ProtocolErrorType;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/// Routes one compilation's synchronous host callbacks over the shared stream.
///
/// At most one callback is outstanding because Sass importer and function
/// callbacks are synchronous. The endpoint reader delivers matching inbound
/// responses while the compilation worker waits for completion.
@NotNullByDefault
final class EmbeddedCompilationDispatcher implements AutoCloseable {
    /// Contains this dispatcher's outer compilation ID.
    private final long compilationId;

    /// Sends one callback request using this compilation's wire ID.
    private final Sender sender;

    /// Contains the callback currently awaiting a host response.
    private @Nullable Pending pending;

    /// Records that the protocol connection has closed.
    private boolean closed;

    /// Creates a dispatcher that writes through the supplied sender.
    ///
    /// @param compilationId the outer compilation ID
    /// @param sender the serialized outbound writer
    EmbeddedCompilationDispatcher(long compilationId, Sender sender) {
        this.compilationId = compilationId;
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /// Sends a callback request and waits for its matching response.
    ///
    /// @param request the outbound callback wrapper
    /// @param expected the required inbound response case
    /// @return the matching inbound response
    /// @throws IOException if the stream closes, the worker is interrupted, or
    ///                     the request cannot be written
    InboundMessage request(
            OutboundMessage request,
            InboundMessage.MessageCase expected
    ) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(expected, "expected");
        var response = new CompletableFuture<InboundMessage>();
        synchronized (this) {
            if (closed) {
                throw new EOFException("Embedded Sass host connection is closed.");
            }
            if (pending != null) {
                throw new IOException(
                        "A host callback is already active for this compilation."
                );
            }
            pending = new Pending(expected, response);
        }

        try {
            sender.send(request);
        } catch (IOException failure) {
            synchronized (this) {
                if (pending != null && pending.response() == response) {
                    pending = null;
                }
            }
            response.completeExceptionally(failure);
            throw failure;
        }

        try {
            return response.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Interrupted while waiting for an Embedded Sass host callback.",
                    failure
            );
        } catch (ExecutionException failure) {
            var cause = failure.getCause();
            if (cause instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Embedded Sass host callback failed.", cause);
        }
    }

    /// Delivers one inbound callback response.
    ///
    /// @param message the inbound response wrapper
    /// @return a protocol violation, or {@code null} after successful delivery
    synchronized @Nullable Violation accept(InboundMessage message) {
        Objects.requireNonNull(message, "message");
        if (pending == null) {
            return new Violation(
                    ProtocolErrorType.PARAMS,
                    -1,
                    "Response ID "
                            + Integer.toUnsignedString(
                                    EmbeddedCompiler.requestId(message)
                            )
                            + " doesn't match any outstanding requests in "
                            + "compilation " + compilationId + "."
            );
        }
        var requestId = EmbeddedCompiler.requestId(message);
        if (requestId != 0) {
            return new Violation(
                    ProtocolErrorType.PARAMS,
                    -1,
                    "Response ID " + Integer.toUnsignedString(requestId)
                            + " doesn't match any outstanding requests in "
                            + "compilation " + compilationId + "."
            );
        }
        if (message.getMessageCase() != pending.expected()) {
            return new Violation(
                    ProtocolErrorType.PARAMS,
                    -1,
                    "Request ID 0 doesn't match response type "
                            + responseTypeName(message.getMessageCase())
                            + " in compilation "
                            + compilationId + "."
            );
        }

        var response = pending.response();
        pending = null;
        response.complete(message);
        return null;
    }

    /// Returns the generated protocol message name used by Dart Sass
    /// parameter errors.
    ///
    /// @param messageCase the inbound wrapper case
    /// @return a nested protobuf message name
    private static String responseTypeName(
            InboundMessage.MessageCase messageCase
    ) {
        var result = new StringBuilder("InboundMessage_");
        for (var word : messageCase.name().split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            result.append(word.charAt(0))
                    .append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return result.toString();
    }

    /// Cancels the outstanding callback after the host connection closes.
    @Override
    public synchronized void close() {
        closed = true;
        if (pending != null) {
            pending.response().completeExceptionally(
                    new EOFException("Embedded Sass host connection is closed.")
            );
            pending = null;
        }
    }

    /// Sends an outbound callback message.
    @FunctionalInterface
    @NotNullByDefault
    interface Sender {
        /// Writes one callback message.
        ///
        /// @param message the outbound wrapper
        /// @throws IOException if the stream cannot be written
        void send(OutboundMessage message) throws IOException;
    }

    /// Describes an inbound callback protocol violation.
    ///
    /// @param type the protocol error type
    /// @param requestId the offending request ID
    /// @param message the diagnostic message
    @NotNullByDefault
    record Violation(
            ProtocolErrorType type,
            int requestId,
            String message
    ) {
        /// Validates the violation fields.
        Violation {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(message, "message");
        }
    }

    /// Contains one callback's expected response and completion signal.
    ///
    /// @param expected the required inbound message case
    /// @param response the response completion signal
    @NotNullByDefault
    private record Pending(
            InboundMessage.MessageCase expected,
            CompletableFuture<InboundMessage> response
    ) {
        /// Validates the pending callback fields.
        private Pending {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(response, "response");
        }
    }
}
