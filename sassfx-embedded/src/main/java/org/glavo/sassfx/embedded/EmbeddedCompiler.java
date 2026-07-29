// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.LogEventType;
import com.sass_lang.embedded_protocol.OutboundMessage;
import com.sass_lang.embedded_protocol.ProtocolError;
import com.sass_lang.embedded_protocol.ProtocolErrorType;
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassCustomFunction;
import org.glavo.sassfx.SassDeprecation;
import org.glavo.sassfx.SassDiagnosticOptions;
import org.glavo.sassfx.SassFileImporter;
import org.glavo.sassfx.SassFileSource;
import org.glavo.sassfx.SassFXVersion;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassLogEvent;
import org.glavo.sassfx.SassLogger;
import org.glavo.sassfx.SassNodePackageImporter;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.SassStackFrame;
import org.glavo.sassfx.SassStringSource;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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

    /// Parses semantic versions accepted by fatal deprecation configuration.
    private static final Pattern VERSION =
            Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)");

    /// Compiles Sass sources for protocol requests.
    private final SassCompiler compiler = new SassCompiler();

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
                @Nullable var invalidRequest = validateCompileRequest(
                        message.getCompileRequest()
                );
                if (invalidRequest != null) {
                    return protocolFailure(
                            output,
                            packet.compilationId(),
                            ProtocolErrorType.PARAMS,
                            UNKNOWN_REQUEST_ID,
                            invalidRequest
                    );
                }
                var dispatcher = new EmbeddedCompilationDispatcher(
                        packet.compilationId(),
                        response -> send(
                                output,
                                packet.compilationId(),
                                response
                        )
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
                            response = compile(
                                    packet.compilationId(),
                                    message.getCompileRequest(),
                                    dispatcher,
                                    output
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
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /// Validates mandatory compile-request protocol fields.
    ///
    /// @param request the inbound compile request
    /// @return the violation detail, or {@code null} when structurally valid
    private static @Nullable String validateCompileRequest(
            InboundMessage.CompileRequest request
    ) {
        if (request.getInputCase()
                == InboundMessage.CompileRequest.InputCase.INPUT_NOT_SET) {
            return "Missing mandatory field CompileRequest.input";
        }
        if (request.getInputCase()
                == InboundMessage.CompileRequest.InputCase.PATH
                && request.getPath().isEmpty()) {
            return "Missing mandatory field CompileRequest.Input.path";
        }
        if (request.getInputCase()
                == InboundMessage.CompileRequest.InputCase.STRING
                && request.getString().hasImporter()) {
            @Nullable var failure = validateImporter(
                    request.getString().getImporter(),
                    false
            );
            if (failure != null) {
                return failure;
            }
        }
        for (var importer : request.getImportersList()) {
            @Nullable var failure = validateImporter(importer, true);
            if (failure != null) {
                return failure;
            }
        }
        return null;
    }

    /// Validates one importer descriptor.
    ///
    /// @param importer the protocol importer
    /// @param mandatory whether an unset importer oneof is invalid
    /// @return the violation detail, or {@code null} when structurally valid
    private static @Nullable String validateImporter(
            InboundMessage.CompileRequest.Importer importer,
            boolean mandatory
    ) {
        var importerCase = importer.getImporterCase();
        // The pinned Dart compiler ignores this field for its built-in Node
        // package importer, but rejects it before the mandatory-union check
        // for path, file, and unset importer descriptors.
        if (importerCase
                != InboundMessage.CompileRequest.Importer.ImporterCase
                .IMPORTER_ID
                && importerCase
                != InboundMessage.CompileRequest.Importer.ImporterCase
                .NODE_PACKAGE_IMPORTER
                && importer.getNonCanonicalSchemeCount() != 0) {
            return "Importer.non_canonical_scheme may only be set along with "
                    + "Importer.importer.importer_id";
        }
        if (mandatory
                && importer.getImporterCase()
                == InboundMessage.CompileRequest.Importer.ImporterCase
                .IMPORTER_NOT_SET) {
            return "Missing mandatory field Importer.importer";
        }
        return null;
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

    /// Compiles one request into its terminal protocol response.
    ///
    /// @param compilationId the packet compilation ID
    /// @param request the compile request
    /// @param dispatcher the host callback dispatcher
    /// @param output the shared protocol output
    /// @return the terminal outbound wrapper
    private OutboundMessage compile(
            long compilationId,
            InboundMessage.CompileRequest request,
            EmbeddedCompilationDispatcher dispatcher,
            OutputStream output
    ) {
        @Nullable SassSource source = null;
        try {
            var importers = new ArrayList<SassImporter>();
            if (request.getInputCase()
                    == InboundMessage.CompileRequest.InputCase.STRING
                    && request.getString().hasImporter()
                    && request.getString().getImporter().getImporterCase()
                    != InboundMessage.CompileRequest.Importer.ImporterCase
                    .IMPORTER_NOT_SET) {
                addImporter(
                        request.getString().getImporter(),
                        importers,
                        dispatcher
                );
            }
            for (var importer : request.getImportersList()) {
                addImporter(importer, importers, dispatcher);
            }
            var valueCodec = new EmbeddedValueCodec(dispatcher);
            var functions = new ArrayList<SassCustomFunction>(
                    request.getGlobalFunctionsCount()
            );
            for (var signature : request.getGlobalFunctionsList()) {
                @Nullable var signatureError =
                        EmbeddedFunctionSignature.error(signature);
                if (signatureError != null) {
                    throw new IllegalArgumentException(signatureError);
                }
                var name = functionName(signature);
                functions.add(new SassCustomFunction(
                        signature,
                        arguments -> valueCodec.callByName(name, arguments)
                ));
            }

            var compileSource = source(request);
            source = compileSource;
            SassLogger logger = request.getSilent()
                    ? SassLogger.NO_OP
                    : event -> sendLog(
                            compilationId,
                            event,
                            output,
                            compileSource,
                            request.getAlertColor(),
                            request.getAlertAscii()
                    );
            var diagnosticOptions = new SassDiagnosticOptions(
                    logger,
                    request.getQuietDeps(),
                    request.getVerbose(),
                    deprecations(
                            request.getSilenceDeprecationList(),
                            false,
                            compilationId,
                            output,
                            request.getSilent(),
                            compileSource,
                            request.getAlertColor(),
                            request.getAlertAscii()
                    ),
                    deprecations(
                            request.getFatalDeprecationList(),
                            true,
                            compilationId,
                            output,
                            request.getSilent(),
                            compileSource,
                            request.getAlertColor(),
                            request.getAlertAscii()
                    ),
                    deprecations(
                            request.getFutureDeprecationList(),
                            false,
                            compilationId,
                            output,
                            request.getSilent(),
                            compileSource,
                            request.getAlertColor(),
                            request.getAlertAscii()
                    )
            );
            var options = new CompileOptions(
                    request.getSourceMap(),
                    List.of(),
                    null,
                    importers,
                    functions,
                    diagnosticOptions,
                    request.getSourceMapIncludeSources()
            );
            var target = new CssTarget(
                    request.getStyle()
                            == com.sass_lang.embedded_protocol.OutputStyle.COMPRESSED
                            ? OutputStyle.COMPRESSED
                            : OutputStyle.EXPANDED,
                    request.getCharset()
            );
            var result = compiler.compile(source, target, options);
            var success = OutboundMessage.CompileResponse.CompileSuccess
                    .newBuilder()
                    .setCss(result.output());
            if (result.sourceMap() != null) {
                success.setSourceMap(result.sourceMap().json());
            }
            var response = OutboundMessage.CompileResponse.newBuilder()
                    .setSuccess(success)
                    .addAllLoadedUrls(result.loadedUrls().stream()
                            .map(URI::toString)
                            .toList());
            return OutboundMessage.newBuilder()
                    .setCompileResponse(response)
                    .build();
        } catch (SassCompilationException failure) {
            var response = OutboundMessage.CompileResponse.newBuilder()
                    .setFailure(compileFailure(
                            failure,
                            source,
                            request.getAlertColor(),
                            request.getAlertAscii()
                    ))
                    .addAllLoadedUrls(failure.loadedUrls().stream()
                            .map(URI::toString)
                            .toList());
            return OutboundMessage.newBuilder()
                    .setCompileResponse(response)
                    .build();
        } catch (IOException failure) {
            var response = OutboundMessage.CompileResponse.newBuilder()
                    .setFailure(ioFailure(messageOf(failure), source));
            return OutboundMessage.newBuilder()
                    .setCompileResponse(response)
                    .build();
        } catch (IllegalArgumentException failure) {
            var response = OutboundMessage.CompileResponse.newBuilder()
                    .setFailure(configurationFailure(
                            messageOf(failure),
                            request.getAlertColor()
                    ));
            return OutboundMessage.newBuilder()
                    .setCompileResponse(response)
                    .build();
        }
    }

    /// Extracts the callable name from a host global-function signature.
    ///
    /// Signature validity is checked by the core custom-function parser.
    ///
    /// @param signature the complete Sass function signature
    /// @return the trimmed name prefix
    private static String functionName(String signature) {
        var openingParenthesis = signature.indexOf('(');
        return openingParenthesis < 0
                ? signature.trim()
                : signature.substring(0, openingParenthesis).trim();
    }

    /// Adds one protocol importer to the compiler's ordered importer list.
    ///
    /// @param importer the protocol importer descriptor
    /// @param importers the destination importer list
    /// @param dispatcher the host callback dispatcher
    private static void addImporter(
            InboundMessage.CompileRequest.Importer importer,
            List<SassImporter> importers,
            EmbeddedCompilationDispatcher dispatcher
    ) {
        switch (importer.getImporterCase()) {
            case PATH -> {
                if (importer.getNonCanonicalSchemeCount() != 0) {
                    throw new IllegalArgumentException(
                            "Importer.non_canonical_scheme may only be set "
                                    + "along with "
                                    + "Importer.importer.importer_id"
                    );
                }
                var base = Path.of(importer.getPath())
                        .toAbsolutePath()
                        .normalize();
                SassFileImporter loadPath = (url, context) ->
                        url.isAbsolute()
                                ? null
                                : base.resolve(url.getPath()).toUri();
                importers.add(loadPath);
            }
            case NODE_PACKAGE_IMPORTER -> {
                importers.add(new SassNodePackageImporter(Path.of(
                        importer.getNodePackageImporter()
                                .getEntryPointDirectory()
                )));
            }
            case IMPORTER_ID -> importers.add(new EmbeddedHostImporter(
                    importer.getImporterId(),
                    importer.getNonCanonicalSchemeList(),
                    dispatcher
            ));
            case FILE_IMPORTER_ID -> {
                if (importer.getNonCanonicalSchemeCount() != 0) {
                    throw new IllegalArgumentException(
                            "Importer.non_canonical_scheme may only be set "
                                    + "along with "
                                    + "Importer.importer.importer_id"
                    );
                }
                importers.add(EmbeddedHostImporter.fileImporter(
                        importer.getFileImporterId(),
                        dispatcher
                ));
            }
            case IMPORTER_NOT_SET -> throw new IllegalArgumentException(
                    "CompileRequest.Importer.importer is mandatory."
            );
        }
    }

    /// Creates a compiler source from one request input.
    ///
    /// @param request the compile request
    /// @return the compiler source
    private static SassSource source(InboundMessage.CompileRequest request) {
        return switch (request.getInputCase()) {
            case STRING -> {
                var input = request.getString();
                @Nullable URI url = input.getUrl().isEmpty()
                        ? null
                        : URI.create(input.getUrl());
                yield new SassStringSource(
                        input.getSource(),
                        switch (input.getSyntax()) {
                            case INDENTED -> Syntax.SASS;
                            case CSS -> Syntax.CSS;
                            case SCSS -> Syntax.SCSS;
                            case UNRECOGNIZED -> throw new IllegalStateException(
                                    "Unknown syntax " + input.getSyntax() + "."
                            );
                        },
                        url
                );
            }
            case PATH -> {
                if (request.getPath().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Missing mandatory field CompileRequest.Input.path"
                    );
                }
                yield new SassFileSource(Path.of(request.getPath()));
            }
            case INPUT_NOT_SET -> throw new IllegalArgumentException(
                    "CompileRequest.input is mandatory."
            );
        };
    }

    /// Converts one checked Sass failure to a protocol failure.
    ///
    /// @param failure the checked compiler failure
    /// @param source the compilation root, or {@code null} if unavailable
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return the protocol failure builder
    private static OutboundMessage.CompileResponse.CompileFailure compileFailure(
            SassCompilationException failure,
            @Nullable SassSource source,
            boolean color,
            boolean ascii
    ) {
        var primary = failure.primaryDiagnostic();
        var builder = OutboundMessage.CompileResponse.CompileFailure.newBuilder()
                .setMessage(primary.message())
                .setFormatted(formatDiagnostic(
                        primary,
                        failure.sassTrace(),
                        source,
                        failure.sourceContents(),
                        color,
                        ascii
                ));
        if (primary.span() != null) {
            builder.setSpan(span(
                    primary.span(),
                    source,
                    failure.sourceContents()
            ));
        }
        if (!failure.sassTrace().isEmpty()) {
            builder.setStackTrace(stackTrace(failure.sassTrace()));
        }
        return builder.build();
    }

    /// Creates a root-source IO failure.
    ///
    /// @param message the failure message
    /// @param source the source selected before failure, or {@code null}
    /// @return the protocol failure
    private static OutboundMessage.CompileResponse.CompileFailure ioFailure(
            String message,
            @Nullable SassSource source
    ) {
        var displayMessage = source instanceof SassFileSource fileSource
                ? "Cannot open file: " + fileSource.path()
                : message;
        var builder = OutboundMessage.CompileResponse.CompileFailure.newBuilder()
                .setMessage(displayMessage);
        if (source instanceof SassFileSource fileSource) {
            builder.setSpan(
                    com.sass_lang.embedded_protocol.SourceSpan.newBuilder()
                            .setUrl(fileSource.path().toUri().toString())
                            .setStart(
                                    com.sass_lang.embedded_protocol.SourceSpan
                                            .SourceLocation.newBuilder()
                            )
                            .setEnd(
                                    com.sass_lang.embedded_protocol.SourceSpan
                                            .SourceLocation.newBuilder()
                            )
            );
        }
        return builder.build();
    }

    /// Creates a compiler-configuration failure with a bogus zero span.
    ///
    /// @param message the configuration failure message
    /// @param color whether terminal colors are enabled
    /// @return the protocol failure
    private static OutboundMessage.CompileResponse.CompileFailure
            configurationFailure(String message, boolean color) {
        var diagnostic = new Diagnostic(
                DiagnosticSeverity.ERROR,
                message,
                null,
                null
        );
        return OutboundMessage.CompileResponse.CompileFailure.newBuilder()
                .setMessage(message)
                .setFormatted(formatDiagnostic(
                        diagnostic,
                        List.of(),
                        null,
                        Map.of(),
                        color,
                        false
                ))
                .setSpan(
                        com.sass_lang.embedded_protocol.SourceSpan.newBuilder()
                                .setStart(
                                        com.sass_lang.embedded_protocol
                                                .SourceSpan.SourceLocation
                                                .newBuilder()
                                )
                                .setEnd(
                                        com.sass_lang.embedded_protocol
                                                .SourceSpan.SourceLocation
                                                .newBuilder()
                                )
                )
                .build();
    }

    /// Sends one logger event on its compilation channel.
    ///
    /// @param compilationId the compilation channel
    /// @param event the compiler event
    /// @param output the shared output stream
    /// @param source the compilation root
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    private void sendLog(
            long compilationId,
            SassLogEvent event,
            OutputStream output,
            SassSource source,
            boolean color,
            boolean ascii
    ) {
        var diagnostic = event.diagnostic();
        var builder = OutboundMessage.LogEvent.newBuilder()
                .setType(switch (diagnostic.severity()) {
                    case WARNING -> LogEventType.WARNING;
                    case DEPRECATION -> LogEventType.DEPRECATION_WARNING;
                    case DEBUG -> LogEventType.DEBUG;
                    case ERROR -> throw new IllegalArgumentException(
                            "error diagnostics cannot be logger events"
                    );
                })
                .setMessage(diagnostic.message())
                .setFormatted(formatDiagnostic(
                        diagnostic,
                        event.sassTrace(),
                        source,
                        Map.of(),
                        color,
                        ascii
                ))
                .setStackTrace(stackTrace(event.sassTrace()));
        if (diagnostic.span() != null) {
            builder.setSpan(span(
                    diagnostic.span(),
                    source,
                    Map.of()
            ));
        }
        if (event.deprecation() != null) {
            builder.setDeprecationType(event.deprecation().id());
        }
        try {
            send(
                    output,
                    compilationId,
                    OutboundMessage.newBuilder().setLogEvent(builder).build()
            );
        } catch (IOException failure) {
            throw new EmbeddedOutputException(failure);
        }
    }

    /// Parses deprecation IDs and optional semantic versions.
    ///
    /// Invalid entries emit ordinary warning events and are ignored.
    ///
    /// @param values requested IDs or versions
    /// @param versions whether semantic versions are accepted
    /// @param compilationId the compilation channel
    /// @param output the shared output stream
    /// @param silent whether all log events are suppressed
    /// @param source the compilation root
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return an immutable set of recognized deprecations
    private @Unmodifiable Set<SassDeprecation> deprecations(
            List<String> values,
            boolean versions,
            long compilationId,
            OutputStream output,
            boolean silent,
            SassSource source,
            boolean color,
            boolean ascii
    ) {
        var result = new LinkedHashSet<SassDeprecation>();
        for (var value : values) {
            @Nullable var deprecation = SassDeprecation.fromId(value);
            if (deprecation != null) {
                result.add(deprecation);
                continue;
            }
            var matcher = VERSION.matcher(value);
            if (versions && matcher.matches()) {
                result.addAll(SassDeprecation.forVersion(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                ));
                continue;
            }
            if (!silent) {
                var message = versions
                        ? "Invalid deprecation id or version \"" + value + "\"."
                        : "Invalid deprecation id \"" + value + "\".";
                sendLog(
                        compilationId,
                        new SassLogEvent(
                                new Diagnostic(
                                        DiagnosticSeverity.WARNING,
                                        message,
                                        null,
                                        null
                                ),
                                List.of(),
                                null
                        ),
                        output,
                        source,
                        color,
                        ascii
                );
            }
        }
        return Set.copyOf(result);
    }

    /// Converts a public source span to the protocol representation.
    ///
    /// @param sourceSpan the compiler span
    /// @param source the compilation root, or {@code null}
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return the protocol span
    private static com.sass_lang.embedded_protocol.SourceSpan span(
            SourceSpan sourceSpan,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        var start = com.sass_lang.embedded_protocol.SourceSpan.SourceLocation
                .newBuilder()
                .setOffset(sourceSpan.start().offset())
                .setLine(sourceSpan.start().line())
                .setColumn(sourceSpan.start().column());
        var end = com.sass_lang.embedded_protocol.SourceSpan.SourceLocation
                .newBuilder()
                .setOffset(sourceSpan.end().offset())
                .setLine(sourceSpan.end().line())
                .setColumn(sourceSpan.end().column());
        return com.sass_lang.embedded_protocol.SourceSpan.newBuilder()
                .setText(sourceSpan.text())
                .setStart(start)
                .setEnd(end)
                .setUrl(sourceSpan.url() == null
                        ? ""
                        : sourceSpan.url().toString())
                .setContext(spanContext(
                        sourceSpan,
                        source,
                        sourceContents
                ))
                .build();
    }

    /// Formats one diagnostic for the protocol's human-readable field.
    ///
    /// @param diagnostic the compiler diagnostic
    /// @param frames Sass call frames from the diagnostic site outward
    /// @param source the compilation root, or {@code null}
    /// @param sourceContents loaded text keyed by canonical URL
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return a stable plain-text representation
    private static String formatDiagnostic(
            Diagnostic diagnostic,
            @Unmodifiable List<SassStackFrame> frames,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents,
            boolean color,
            boolean ascii
    ) {
        if (diagnostic.severity() == DiagnosticSeverity.DEBUG) {
            var location = diagnostic.span() == null
                    ? "-"
                    : location(diagnostic.span());
            var label = color
                    ? "\u001b[1mDebug\u001b[0m"
                    : "DEBUG";
            return location + " " + label + ": "
                    + diagnostic.message() + "\n";
        }

        var label = switch (diagnostic.severity()) {
            case ERROR -> "Error";
            case WARNING -> color
                    ? "\u001b[33m\u001b[1mWarning\u001b[0m"
                    : "WARNING";
            case DEPRECATION -> color
                    ? "\u001b[33m\u001b[1mDeprecation Warning\u001b[0m"
                    : "DEPRECATION WARNING";
            case DEBUG -> throw new AssertionError();
        };
        var result = new StringBuilder()
                .append(label)
                .append(": ")
                .append(diagnostic.message());
        if (diagnostic.span() != null) {
            result.append(diagnostic.severity() == DiagnosticSeverity.ERROR
                            ? "\n"
                            : "\n\n")
                    .append(formatSpan(
                            diagnostic.span(),
                            source,
                            sourceContents,
                            color,
                            ascii
                    ));
        }
        var trace = stackTrace(frames);
        if (!trace.isEmpty()) {
            var indentation = diagnostic.severity() == DiagnosticSeverity.ERROR
                    ? "  "
                    : "    ";
            for (var traceLine : trace.stripTrailing().split("\n")) {
                result.append('\n')
                        .append(indentation)
                        .append(traceLine);
            }
        }
        if (diagnostic.severity() != DiagnosticSeverity.ERROR) {
            result.append('\n');
        }
        return result.toString();
    }

    /// Formats a single-line source excerpt with Sass terminal glyphs.
    ///
    /// @param span the highlighted source span
    /// @param source the compilation root, or {@code null}
    /// @param sourceContents loaded text keyed by canonical URL
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return the formatted source frame
    private static String formatSpan(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents,
            boolean color,
            boolean ascii
    ) {
        var lineNumber = Integer.toString(span.start().line() + 1);
        var width = lineNumber.length();
        var line = sourceLine(span, source, sourceContents);
        var startColumn = Math.min(span.start().column(), line.length());
        var highlightedLength = Math.max(
                1,
                span.start().line() == span.end().line()
                        ? span.end().column() - span.start().column()
                        : Math.max(1, line.length() - startColumn)
        );
        highlightedLength = Math.min(
                highlightedLength,
                Math.max(1, line.length() - startColumn)
        );
        var top = ascii ? "," : "╷";
        var bar = ascii ? "|" : "│";
        var bottom = ascii ? "'" : "╵";
        if (!color) {
            return " ".repeat(width + 1) + top + "\n"
                    + lineNumber + " " + bar + " " + line + "\n"
                    + " ".repeat(width + 1) + bar + " "
                    + " ".repeat(startColumn)
                    + "^".repeat(highlightedLength) + "\n"
                    + " ".repeat(width + 1) + bottom;
        }

        var blue = "\u001b[34m";
        var red = "\u001b[31m";
        var reset = "\u001b[0m";
        var before = line.substring(0, startColumn);
        var highlightEnd = Math.min(
                line.length(),
                startColumn + highlightedLength
        );
        var highlighted = line.substring(startColumn, highlightEnd);
        var after = line.substring(highlightEnd);
        return blue + " ".repeat(width + 1) + top + reset + "\n"
                + blue + lineNumber + " " + bar + reset + " "
                + before + red + highlighted + reset + after + "\n"
                + blue + " ".repeat(width + 1) + bar + reset + " "
                + red + " ".repeat(startColumn)
                + "^".repeat(highlightedLength) + reset + "\n"
                + blue + " ".repeat(width + 1) + bottom + reset;
    }

    /// Returns the display location used by debug messages.
    ///
    /// @param span the associated source span
    /// @return a URL and one-based line
    private static String location(SourceSpan span) {
        return (span.url() == null ? "-" : span.url())
                + ":" + (span.start().line() + 1);
    }

    /// Returns the source line containing a span start.
    ///
    /// @param span the source span
    /// @param source the compilation root, or {@code null}
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return the complete root line when available, otherwise a padded span
    private static String sourceLine(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        @Nullable var contents = matchingSourceContents(
                span,
                source,
                sourceContents
        );
        if (contents == null) {
            return " ".repeat(Math.max(0, span.start().column()))
                    + span.text().lines().findFirst().orElse("");
        }
        var lines = contents.split("\\R", -1);
        return span.start().line() < lines.length
                ? lines[span.start().line()]
                : " ".repeat(Math.max(0, span.start().column()))
                        + span.text().lines().findFirst().orElse("");
    }

    /// Returns the protocol context excerpt for a span.
    ///
    /// @param span the source span
    /// @param source the compilation root, or {@code null}
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return the complete covered root lines when available
    private static String spanContext(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        @Nullable var contents = matchingSourceContents(
                span,
                source,
                sourceContents
        );
        if (contents == null) {
            return " ".repeat(Math.max(0, span.start().column()))
                    + span.text();
        }
        var start = lineStart(contents, span.start().offset());
        var end = lineEnd(contents, span.end().offset());
        return contents.substring(start, end);
    }

    /// Returns root text when it owns the requested span.
    ///
    /// @param span the source span
    /// @param source the compilation root, or {@code null}
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return matching root text, or {@code null}
    private static @Nullable String matchingSourceContents(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        if (span.url() != null) {
            @Nullable var loaded = sourceContents.get(span.url());
            if (loaded != null) {
                return loaded;
            }
        }
        if (!(source instanceof SassStringSource stringSource)) {
            return null;
        }
        return Objects.equals(span.url(), stringSource.canonicalUrl())
                ? stringSource.content()
                : null;
    }

    /// Returns the offset after the preceding line break.
    ///
    /// @param contents the source text
    /// @param offset an offset in or at the end of the source
    /// @return the containing line's start offset
    private static int lineStart(String contents, int offset) {
        var index = contents.lastIndexOf('\n', Math.max(0, offset - 1));
        return index < 0 ? 0 : index + 1;
    }

    /// Returns the offset after the containing line break when present.
    ///
    /// @param contents the source text
    /// @param offset an offset in or at the end of the source
    /// @return the context excerpt end offset
    private static int lineEnd(String contents, int offset) {
        var index = contents.indexOf('\n', Math.min(offset, contents.length()));
        return index < 0 ? contents.length() : index + 1;
    }

    /// Formats Sass call frames as an implementation-defined stack trace.
    ///
    /// @param frames frames ordered from the failure site outward
    /// @return an empty string or one line per frame
    private static String stackTrace(
            @Unmodifiable List<SassStackFrame> frames
    ) {
        return frames.stream()
                .map(frame -> {
                    var span = frame.span();
                    return (span.url() == null ? "-" : span.url())
                            + " " + (span.start().line() + 1)
                            + ":" + (span.start().column() + 1)
                            + "  " + frame.member();
                })
                .reduce((left, right) -> left + "\n" + right)
                .map(value -> value + "\n")
                .orElse("");
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

    /// Wraps an output failure thrown through the synchronous logger contract.
    @NotNullByDefault
    private static final class EmbeddedOutputException
            extends RuntimeException {
        /// The serialization version of this exception representation.
        private static final long serialVersionUID = 1L;

        /// Creates a logger output failure.
        ///
        /// @param cause the stream failure
        private EmbeddedOutputException(IOException cause) {
            super(cause);
        }
    }
}
