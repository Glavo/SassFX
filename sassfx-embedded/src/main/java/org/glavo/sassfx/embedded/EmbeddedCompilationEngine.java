// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.OutboundMessage;
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
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassLogEvent;
import org.glavo.sassfx.SassLogger;
import org.glavo.sassfx.SassNodePackageImporter;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.SassStringSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Executes validated Embedded Sass compilation requests.
///
/// The engine translates protocol sources, importers, functions, diagnostics,
/// and output options to the public SassFX API. Instances are thread-safe and
/// may serve concurrent compilation channels.
@NotNullByDefault
final class EmbeddedCompilationEngine {
    /// Parses semantic versions accepted by fatal deprecation configuration.
    private static final Pattern VERSION =
            Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)");

    /// Compiles translated Sass sources.
    private final SassCompiler compiler = new SassCompiler();

    /// Creates a reusable compilation engine.
    EmbeddedCompilationEngine() {
    }

    /// Validates mandatory compile-request protocol fields.
    ///
    /// @param request the inbound compile request
    /// @return the violation detail, or `null` when structurally valid
    static @Nullable String validate(
            InboundMessage.CompileRequest request
    ) {
        Objects.requireNonNull(request, "request");
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

    /// Compiles one request into its terminal protocol response.
    ///
    /// Logger events and host callback requests may be sent before this method
    /// returns. Compiler, source IO, and configuration failures are encoded as
    /// ordinary compile responses. Protocol violations and logger-output
    /// failures propagate for endpoint-level handling.
    ///
    /// @param request the structurally valid compile request
    /// @param dispatcher the request's host callback dispatcher
    /// @param sender the compilation-channel outbound sender
    /// @return the terminal outbound wrapper
    OutboundMessage compile(
            InboundMessage.CompileRequest request,
            EmbeddedCompilationDispatcher dispatcher,
            EmbeddedCompilationDispatcher.Sender sender
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(sender, "sender");

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
                            event,
                            sender,
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
                            sender,
                            request.getSilent(),
                            compileSource,
                            request.getAlertColor(),
                            request.getAlertAscii()
                    ),
                    deprecations(
                            request.getFatalDeprecationList(),
                            true,
                            sender,
                            request.getSilent(),
                            compileSource,
                            request.getAlertColor(),
                            request.getAlertAscii()
                    ),
                    deprecations(
                            request.getFutureDeprecationList(),
                            false,
                            sender,
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
                            == com.sass_lang.embedded_protocol.OutputStyle
                            .COMPRESSED
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
                    .setFailure(
                            EmbeddedDiagnosticFormatter.compileFailure(
                                    failure,
                                    source,
                                    request.getAlertColor(),
                                    request.getAlertAscii()
                            )
                    )
                    .addAllLoadedUrls(failure.loadedUrls().stream()
                            .map(URI::toString)
                            .toList());
            return OutboundMessage.newBuilder()
                    .setCompileResponse(response)
                    .build();
        } catch (IOException failure) {
            var response = OutboundMessage.CompileResponse.newBuilder()
                    .setFailure(EmbeddedDiagnosticFormatter.ioFailure(
                            messageOf(failure),
                            source
                    ));
            return OutboundMessage.newBuilder()
                    .setCompileResponse(response)
                    .build();
        } catch (IllegalArgumentException failure) {
            var response = OutboundMessage.CompileResponse.newBuilder()
                    .setFailure(
                            EmbeddedDiagnosticFormatter.configurationFailure(
                                    messageOf(failure),
                                    request.getAlertColor()
                            )
                    );
            return OutboundMessage.newBuilder()
                    .setCompileResponse(response)
                    .build();
        }
    }

    /// Validates one importer descriptor.
    ///
    /// @param importer the protocol importer
    /// @param mandatory whether an unset importer oneof is invalid
    /// @return the violation detail, or `null` when structurally valid
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

    /// Extracts the callable name from a validated host function signature.
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
            case NODE_PACKAGE_IMPORTER -> importers.add(
                    new SassNodePackageImporter(Path.of(
                            importer.getNodePackageImporter()
                                    .getEntryPointDirectory()
                    ))
            );
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
    private static SassSource source(
            InboundMessage.CompileRequest request
    ) {
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

    /// Sends one compiler logger event on its compilation channel.
    ///
    /// @param event the compiler event
    /// @param sender the compilation-channel sender
    /// @param source the compilation root
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @throws EmbeddedOutputException if the event cannot be written
    private static void sendLog(
            SassLogEvent event,
            EmbeddedCompilationDispatcher.Sender sender,
            SassSource source,
            boolean color,
            boolean ascii
    ) {
        try {
            sender.send(EmbeddedDiagnosticFormatter.logEvent(
                    event,
                    source,
                    color,
                    ascii
            ));
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
    /// @param sender the compilation-channel sender
    /// @param silent whether all log events are suppressed
    /// @param source the compilation root
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return an immutable set of recognized deprecations
    private static @Unmodifiable Set<SassDeprecation> deprecations(
            List<String> values,
            boolean versions,
            EmbeddedCompilationDispatcher.Sender sender,
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
                        sender,
                        source,
                        color,
                        ascii
                );
            }
        }
        return Set.copyOf(result);
    }

    /// Returns a non-null detail for one compilation failure.
    ///
    /// @param failure the failure
    /// @return its non-blank message or class name
    private static String messageOf(Throwable failure) {
        @Nullable var message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : message;
    }

    /// Wraps an output failure thrown through the synchronous logger contract.
    @NotNullByDefault
    private static final class EmbeddedOutputException
            extends RuntimeException {
        /// The serialization version of this exception representation.
        @Serial
        private static final long serialVersionUID = 1L;

        /// Creates a logger output failure.
        ///
        /// @param cause the stream failure
        private EmbeddedOutputException(IOException cause) {
            super(cause);
        }
    }
}
