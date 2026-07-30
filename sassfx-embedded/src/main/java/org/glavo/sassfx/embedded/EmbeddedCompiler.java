// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.OutboundMessage;
import com.sass_lang.embedded_protocol.ProtocolError;
import com.sass_lang.embedded_protocol.ProtocolErrorType;
import org.glavo.sassfx.SassFXVersion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/// Serves Embedded Sass Protocol 3.2.0 over byte streams.
///
/// Different nonzero compilation IDs run concurrently. A clean input EOF
/// cancels all in-flight compilations and returns status {@code 0}. Protocol
/// violations emit one fatal [ProtocolError] and return status {@code 76}.
@NotNullByDefault
public final class EmbeddedCompiler {
    /// The Embedded Sass protocol implemented by this endpoint.
    public static final String PROTOCOL_VERSION = "3.2.0";

    /// The Dart Sass language-compatibility baseline implemented by SassFX.
    public static final String COMPILER_VERSION = "1.102.0";

    /// The current SassFX implementation version.
    public static final String IMPLEMENTATION_VERSION = SassFXVersion.current();

    /// The implementation name reported to hosts.
    public static final String IMPLEMENTATION_NAME = "sassfx";

    /// The process status for a host protocol violation.
    public static final int PROTOCOL_EXIT_STATUS = 76;

    /// The process status for an unexpected implementation failure.
    public static final int SOFTWARE_EXIT_STATUS = 70;

    /// The compilation ID used when an incoming frame cannot be identified.
    private static final long UNKNOWN_COMPILATION_ID = 0xffff_ffffL;

    /// The request ID used when an incoming message cannot be identified.
    private static final int UNKNOWN_REQUEST_ID = -1;

    /// Executes translated compilation requests.
    private final EmbeddedCompilationEngine compilationEngine =
            new EmbeddedCompilationEngine();

    /// Serializes all writes because compilation workers share one stdout.
    private final Object outputLock = new Object();

    /// Contains the resource limits applied to each endpoint run.
    private final EmbeddedLimits limits;

    /// Creates an embedded compiler endpoint with [EmbeddedLimits#DEFAULT].
    public EmbeddedCompiler() {
        this(EmbeddedLimits.DEFAULT);
    }

    /// Creates an embedded compiler endpoint with explicit resource limits.
    ///
    /// @param limits the per-run endpoint limits
    public EmbeddedCompiler(EmbeddedLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /// Returns the endpoint version response as a standalone JSON document.
    ///
    /// This representation is used by the executable's {@code --version}
    /// command and is not part of the framed protocol stream.
    ///
    /// @return a JSON version response whose request ID is zero
    public static String versionJson() {
        return """
                {
                  "protocolVersion": "%s",
                  "compilerVersion": "%s",
                  "implementationVersion": "%s",
                  "implementationName": "%s",
                  "id": 0
                }""".formatted(
                PROTOCOL_VERSION,
                COMPILER_VERSION,
                IMPLEMENTATION_VERSION,
                IMPLEMENTATION_NAME
        );
    }

    /// Runs the endpoint until EOF or a fatal protocol failure.
    ///
    /// The method does not close either stream. On normal EOF, queued and
    /// running compilations are interrupted and no additional terminal
    /// responses are guaranteed.
    ///
    /// @param input framed inbound messages
    /// @param output framed outbound messages
    /// @return {@code 0} for clean EOF, {@code 76} for a protocol error, or
    /// {@code 70} for an unexpected endpoint failure
    public int run(InputStream input, OutputStream output) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Map<Long, EmbeddedCompilationDispatcher> active =
                new ConcurrentHashMap<>();
        BlockingQueue<EndpointEvent> events = new ArrayBlockingQueue<>(
                limits.maxInboundEvents()
        );
        ExecutorService executor = new ThreadPoolExecutor(
                limits.maxConcurrentCompilations(),
                limits.maxConcurrentCompilations(),
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(limits.maxQueuedCompilations()),
                new ThreadPoolExecutor.AbortPolicy()
        );
        var reader = new Thread(
                () -> readPackets(
                        input,
                        events,
                        limits.maxPacketLength()
                ),
                "sassfx-embedded-reader"
        );
        reader.setDaemon(true);
        reader.start();
        try {
            while (true) {
                final EndpointEvent event;
                try {
                    event = events.take();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    return SOFTWARE_EXIT_STATUS;
                }
                if (event instanceof EndOfInput) {
                    active.values().forEach(
                            EmbeddedCompilationDispatcher::close
                    );
                    return 0;
                }
                if (event instanceof ReadFailure readFailure) {
                    return protocolFailure(
                            output,
                            UNKNOWN_COMPILATION_ID,
                            ProtocolErrorType.PARSE,
                            UNKNOWN_REQUEST_ID,
                            messageOf(readFailure.failure())
                    );
                }
                if (event instanceof FatalFailure fatalFailure) {
                    return protocolFailure(
                            output,
                            fatalFailure.compilationId(),
                            fatalFailure.type(),
                            fatalFailure.requestId(),
                            fatalFailure.message()
                    );
                }
                var packet = ((PacketEvent) event).packet();

                final InboundMessage message;
                try {
                    message = InboundMessage.parseFrom(packet.rawMessage());
                } catch (InvalidProtocolBufferException failure) {
                    return protocolFailure(
                            output,
                            packet.compilationId(),
                            ProtocolErrorType.PARSE,
                            UNKNOWN_REQUEST_ID,
                            messageOf(failure)
                    );
                }

                if (packet.compilationId() == 0) {
                    if (message.getMessageCase()
                            != InboundMessage.MessageCase.VERSION_REQUEST) {
                        return protocolFailure(
                                output,
                                0,
                                ProtocolErrorType.PARAMS,
                                requestId(message),
                                "Only VersionRequest may have wire ID 0."
                        );
                    }
                    try {
                        sendVersion(
                                output,
                                message.getVersionRequest().getId()
                        );
                    } catch (IOException failure) {
                        return SOFTWARE_EXIT_STATUS;
                    }
                    continue;
                }

                if (message.getMessageCase()
                        != InboundMessage.MessageCase.COMPILE_REQUEST) {
                    if (message.getMessageCase()
                            == InboundMessage.MessageCase.VERSION_REQUEST) {
                        return protocolFailure(
                                output,
                                packet.compilationId(),
                                ProtocolErrorType.PARAMS,
                                message.getVersionRequest().getId(),
                                "VersionRequest must have compilation ID 0."
                        );
                    }
                    @Nullable var dispatcher = active.get(
                            packet.compilationId()
                    );
                    if (dispatcher != null) {
                        @Nullable var violation = dispatcher.accept(message);
                        if (violation == null) {
                            continue;
                        }
                        return protocolFailure(
                                output,
                                packet.compilationId(),
                                violation.type(),
                                violation.requestId(),
                                violation.message()
                        );
                    }
                    return protocolFailure(
                            output,
                            packet.compilationId(),
                            ProtocolErrorType.PARAMS,
                            requestId(message),
                            "No host callback request is active for compilation "
                                    + packet.compilationId() + "."
                    );
                }

                @Nullable var invalidRequest = EmbeddedCompilationEngine
                        .validate(message.getCompileRequest());
                if (invalidRequest != null) {
                    return protocolFailure(
                            output,
                            packet.compilationId(),
                            ProtocolErrorType.PARAMS,
                            UNKNOWN_REQUEST_ID,
                            invalidRequest
                    );
                }
                EmbeddedCompilationDispatcher.Sender sender =
                        response -> send(
                                output,
                                packet.compilationId(),
                                response
                        );
                var dispatcher = new EmbeddedCompilationDispatcher(
                        packet.compilationId(),
                        sender
                );
                if (active.putIfAbsent(
                        packet.compilationId(),
                        dispatcher
                ) != null) {
                    return protocolFailure(
                            output,
                            packet.compilationId(),
                            ProtocolErrorType.PARAMS,
                            UNKNOWN_REQUEST_ID,
                            "A CompileRequest with compilation ID "
                                    + packet.compilationId()
                                    + " is already active."
                    );
                }

                try {
                    executor.execute(() -> {
                        OutboundMessage response;
                        try {
                            response = compilationEngine.compile(
                                    message.getCompileRequest(),
                                    dispatcher,
                                    sender
                            );
                        } catch (EmbeddedProtocolException failure) {
                            publishEvent(events, new FatalFailure(
                                    packet.compilationId(),
                                    failure.type(),
                                    failure.requestId(),
                                    messageOf(failure)
                            ));
                            return;
                        } catch (RuntimeException failure) {
                            publishEvent(events, new FatalFailure(
                                    packet.compilationId(),
                                    ProtocolErrorType.INTERNAL,
                                    UNKNOWN_REQUEST_ID,
                                    messageOf(failure)
                            ));
                            return;
                        }

                        // Release the ID before publishing its terminal response
                        // so the host can safely reuse it after observing the
                        // response.
                        active.remove(packet.compilationId(), dispatcher);
                        dispatcher.close();
                        try {
                            send(output, packet.compilationId(), response);
                        } catch (IOException failure) {
                            publishEvent(events, new FatalFailure(
                                    packet.compilationId(),
                                    ProtocolErrorType.INTERNAL,
                                    UNKNOWN_REQUEST_ID,
                                    messageOf(failure)
                            ));
                        }
                    });
                } catch (RejectedExecutionException failure) {
                    active.remove(packet.compilationId(), dispatcher);
                    dispatcher.close();
                    return protocolFailure(
                            output,
                            packet.compilationId(),
                            ProtocolErrorType.PARAMS,
                            UNKNOWN_REQUEST_ID,
                            "The Embedded Sass compilation queue is full."
                    );
                }
            }
        } catch (RuntimeException failure) {
            return protocolFailure(
                    output,
                    UNKNOWN_COMPILATION_ID,
                    ProtocolErrorType.INTERNAL,
                    UNKNOWN_REQUEST_ID,
                    messageOf(failure)
            );
        } finally {
            active.values().forEach(EmbeddedCompilationDispatcher::close);
            reader.interrupt();
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /// Reads framed packets on a daemon thread and publishes endpoint events.
    ///
    /// @param input the host-owned protocol input
    /// @param events the endpoint event queue
    /// @param maxPacketLength the largest accepted frame body
    private static void readPackets(
            InputStream input,
            BlockingQueue<EndpointEvent> events,
            int maxPacketLength
    ) {
        try {
            while (true) {
                @Nullable var packet = EmbeddedPacketIO.read(
                        input,
                        maxPacketLength
                );
                if (packet == null) {
                    publishEvent(events, EndOfInput.INSTANCE);
                    return;
                }
                events.put(new PacketEvent(packet));
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (IOException failure) {
            publishEvent(events, new ReadFailure(failure));
        } catch (RuntimeException failure) {
            publishEvent(events, new FatalFailure(
                    UNKNOWN_COMPILATION_ID,
                    ProtocolErrorType.INTERNAL,
                    UNKNOWN_REQUEST_ID,
                    messageOf(failure)
            ));
        }
    }

    /// Publishes an endpoint event while preserving interruption.
    ///
    /// Worker and reader threads use blocking publication so terminal failures
    /// cannot be dropped when the bounded inbound queue is temporarily full.
    ///
    /// @param events the endpoint event queue
    /// @param event the event to publish
    private static void publishEvent(
            BlockingQueue<EndpointEvent> events,
            EndpointEvent event
    ) {
        try {
            events.put(event);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }

    /// Sends a version response on compilation ID zero.
    ///
    /// @param output the shared output
    /// @param requestId the host's version request ID
    /// @throws IOException if the response cannot be written
    private void sendVersion(OutputStream output, int requestId)
            throws IOException {
        var version = OutboundMessage.VersionResponse.newBuilder()
                .setProtocolVersion(PROTOCOL_VERSION)
                .setCompilerVersion(COMPILER_VERSION)
                .setImplementationVersion(IMPLEMENTATION_VERSION)
                .setImplementationName(IMPLEMENTATION_NAME)
                .setId(requestId);
        send(
                output,
                0,
                OutboundMessage.newBuilder().setVersionResponse(version).build()
        );
    }

    /// Sends one fatal protocol error and returns its process status.
    ///
    /// @param output the shared output
    /// @param compilationId the associated compilation ID
    /// @param type the protocol error category
    /// @param requestId the associated message ID
    /// @param message the error detail
    /// @return the protocol exit status
    private int protocolFailure(
            OutputStream output,
            long compilationId,
            ProtocolErrorType type,
            int requestId,
            String message
    ) {
        try {
            send(
                    output,
                    compilationId,
                    protocolError(type, requestId, message)
            );
        } catch (IOException ignored) {
            // A closed host output prevents delivery but does not change the
            // classification of the protocol failure.
        }
        return type == ProtocolErrorType.INTERNAL
                ? SOFTWARE_EXIT_STATUS
                : PROTOCOL_EXIT_STATUS;
    }

    /// Creates one wrapped protocol error.
    ///
    /// @param type the error category
    /// @param requestId the associated request ID
    /// @param message the error detail
    /// @return the outbound wrapper
    private static OutboundMessage protocolError(
            ProtocolErrorType type,
            int requestId,
            String message
    ) {
        var id = requestId < 0 ? UNKNOWN_REQUEST_ID : requestId;
        return OutboundMessage.newBuilder()
                .setError(ProtocolError.newBuilder()
                        .setType(type)
                        .setId(id)
                        .setMessage(message))
                .build();
    }

    /// Extracts the request ID carried by an inbound callback or version message.
    ///
    /// @param message the inbound wrapper
    /// @return the unsigned request ID represented as an int, or {@code -1}
    static int requestId(InboundMessage message) {
        return switch (message.getMessageCase()) {
            case VERSION_REQUEST -> message.getVersionRequest().getId();
            case CANONICALIZE_RESPONSE ->
                    message.getCanonicalizeResponse().getId();
            case IMPORT_RESPONSE -> message.getImportResponse().getId();
            case FILE_IMPORT_RESPONSE ->
                    message.getFileImportResponse().getId();
            case FUNCTION_CALL_RESPONSE ->
                    message.getFunctionCallResponse().getId();
            case COMPILE_REQUEST, MESSAGE_NOT_SET -> UNKNOWN_REQUEST_ID;
        };
    }

    /// Writes one outbound wrapper atomically.
    ///
    /// @param output the shared output
    /// @param compilationId the compilation channel
    /// @param message the outbound wrapper
    /// @throws IOException if the stream fails
    private void send(
            OutputStream output,
            long compilationId,
            OutboundMessage message
    ) throws IOException {
        synchronized (outputLock) {
            EmbeddedPacketIO.write(
                    output,
                    compilationId,
                    message.toByteArray()
            );
        }
    }

    /// Returns a non-null detail for one failure.
    ///
    /// @param failure the failure
    /// @return its message or class name
    private static String messageOf(Throwable failure) {
        @Nullable var message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : message;
    }

    /// Identifies one event consumed by the endpoint coordinator.
    @NotNullByDefault
    private sealed interface EndpointEvent permits
            PacketEvent,
            EndOfInput,
            ReadFailure,
            FatalFailure {
    }

    /// Delivers one successfully framed packet.
    ///
    /// @param packet the framed protocol packet
    @NotNullByDefault
    private record PacketEvent(
            EmbeddedPacketIO.Packet packet
    ) implements EndpointEvent {
        /// Validates the packet event.
        private PacketEvent {
            Objects.requireNonNull(packet, "packet");
        }
    }

    /// Reports a clean input EOF.
    @NotNullByDefault
    private enum EndOfInput implements EndpointEvent {
        /// The singleton EOF event.
        INSTANCE
    }

    /// Reports a framing input failure.
    ///
    /// @param failure the framing failure
    @NotNullByDefault
    private record ReadFailure(
            IOException failure
    ) implements EndpointEvent {
        /// Validates the read failure event.
        private ReadFailure {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /// Reports a connection-level failure discovered by a worker.
    ///
    /// @param compilationId the associated compilation channel
    /// @param type the protocol error type
    /// @param requestId the associated request ID
    /// @param message the diagnostic detail
    @NotNullByDefault
    private record FatalFailure(
            long compilationId,
            ProtocolErrorType type,
            int requestId,
            String message
    ) implements EndpointEvent {
        /// Validates the fatal failure event.
        private FatalFailure {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(message, "message");
        }
    }
}
