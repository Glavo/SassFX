// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.LogEventType;
import com.sass_lang.embedded_protocol.OutboundMessage;
import com.sass_lang.embedded_protocol.OutputStyle;
import com.sass_lang.embedded_protocol.ProtocolErrorType;
import com.sass_lang.embedded_protocol.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Closeable;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the Embedded Sass compiler through its framed duplex wire protocol.
@NotNullByDefault
final class EmbeddedCompilerWireTest {
    /// Verifies a version request is answered on wire ID zero with the same ID.
    @Test
    void servesVersionRequest() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    0,
                    InboundMessage.newBuilder()
                            .setVersionRequest(
                                    InboundMessage.VersionRequest.newBuilder()
                                            .setId(123)
                            )
                            .build()
            );

            var received = harness.receive();
            assertEquals(0, received.compilationId());
            var response = received.message().getVersionResponse();
            assertEquals(123, response.getId());
            assertEquals(
                    EmbeddedCompiler.PROTOCOL_VERSION,
                    response.getProtocolVersion()
            );
            assertEquals(
                    EmbeddedCompiler.COMPILER_VERSION,
                    response.getCompilerVersion()
            );
            assertEquals(
                    EmbeddedCompiler.IMPLEMENTATION_VERSION,
                    response.getImplementationVersion()
            );
            assertEquals(
                    EmbeddedCompiler.IMPLEMENTATION_NAME,
                    response.getImplementationName()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a string entrypoint compiles to a success response.
    @Test
    void compilesStringInput() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(1, compileString("a {b: 1px + 2px}"));

            var response = compileResponse(harness.receive(), 1);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("b: 3px"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Applies exact mandatory-field validation to compile inputs and
    /// top-level importer descriptors.
    @Test
    void validatesMandatoryCompileRequestFields() throws Exception {
        assertFatalCompileRequest(
                InboundMessage.CompileRequest.getDefaultInstance(),
                ProtocolErrorType.PARAMS,
                "Missing mandatory field CompileRequest.input",
                EmbeddedCompiler.PROTOCOL_EXIT_STATUS
        );
        assertFatalCompileRequest(
                InboundMessage.CompileRequest.newBuilder()
                        .setPath("")
                        .build(),
                ProtocolErrorType.PARAMS,
                "Missing mandatory field CompileRequest.Input.path",
                EmbeddedCompiler.PROTOCOL_EXIT_STATUS
        );
        assertFatalCompileRequest(
                InboundMessage.CompileRequest.newBuilder()
                        .setString(
                                InboundMessage.CompileRequest.StringInput
                                        .newBuilder()
                                        .setSource("a { b: c; }")
                                        .setSyntax(Syntax.SCSS)
                        )
                        .addImporters(
                                InboundMessage.CompileRequest.Importer
                                        .getDefaultInstance()
                        )
                        .build(),
                ProtocolErrorType.PARAMS,
                "Missing mandatory field Importer.importer",
                EmbeddedCompiler.PROTOCOL_EXIT_STATUS
        );
    }

    /// Treats an explicitly present empty StringInput importer descriptor as
    /// the absence of the optional importer.
    @Test
    void allowsUnsetOptionalStringImporter() throws Exception {
        var input = InboundMessage.CompileRequest.StringInput.newBuilder()
                .setSource("a { b: c; }")
                .setSyntax(Syntax.SCSS)
                .setImporter(
                        InboundMessage.CompileRequest.Importer
                                .getDefaultInstance()
                );
        assertTrue(input.hasImporter());

        try (var harness = new CompilerHarness()) {
            harness.send(
                    18,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setString(input)
                            )
                            .build()
            );

            var response = compileResponse(harness.receive(), 18);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("b: c"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Reports an unrecognized StringInput syntax as an internal protocol
    /// failure, matching the pinned compiler's unknown-enum path.
    @Test
    void rejectsUnknownStringInputSyntax() throws Exception {
        var request = InboundMessage.CompileRequest.newBuilder()
                .setString(
                        InboundMessage.CompileRequest.StringInput.newBuilder()
                                .setSource("a { b: c; }")
                                .setSyntaxValue(91)
                )
                .build();
        assertFatalCompileRequest(
                request,
                ProtocolErrorType.INTERNAL,
                "Unknown syntax UNRECOGNIZED.",
                EmbeddedCompiler.SOFTWARE_EXIT_STATUS
        );
    }

    /// Reports invalid global-function signatures as compilation failures and
    /// keeps serving later requests on the same connection.
    @Test
    void rejectsInvalidGlobalFunctionSignatures() throws Exception {
        var signatures = List.of(
                "",
                "foo",
                "foo($bar",
                "foo() ",
                "foo($)"
        );
        var messages = List.of(
                "Invalid signature \"\": Expected identifier.",
                "Invalid signature \"foo\": expected \"(\".",
                "Invalid signature \"foo($bar\": expected \")\".",
                "Invalid signature \"foo() \": expected no more input.",
                "Invalid signature \"foo($)\": Expected identifier."
        );

        try (var harness = new CompilerHarness()) {
            for (var index = 0; index < signatures.size(); index++) {
                var compilationId = 30L + index;
                var request = InboundMessage.CompileRequest.newBuilder()
                        .setString(
                                InboundMessage.CompileRequest.StringInput
                                        .newBuilder()
                                        .setSource("a { b: c; }")
                                        .setSyntax(Syntax.SCSS)
                        )
                        .addGlobalFunctions(signatures.get(index))
                        .build();
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setCompileRequest(request)
                                .build()
                );

                var response = compileResponse(
                        harness.receive(),
                        compilationId
                );
                assertTrue(response.hasFailure());
                assertEquals(
                        messages.get(index),
                        response.getFailure().getMessage()
                );
            }

            harness.send(35, compileString("a { b: c; }"));
            assertTrue(compileResponse(harness.receive(), 35).hasSuccess());

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a filesystem entrypoint compiles to a success response.
    ///
    /// @param temporaryDirectory the isolated test directory
    @Test
    void compilesPathInput(@TempDir Path temporaryDirectory) throws Exception {
        var input = temporaryDirectory.resolve("input.scss");
        Files.writeString(
                input,
                "a {b: 1px + 2px}",
                StandardCharsets.UTF_8
        );

        try (var harness = new CompilerHarness()) {
            var request = InboundMessage.CompileRequest.newBuilder()
                    .setPath(input.toString())
                    .build();
            harness.send(
                    2,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request)
                            .build()
            );

            var response = compileResponse(harness.receive(), 2);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("b: 3px"));
            assertTrue(response.getLoadedUrlsList().contains(
                    input.toUri().toString()
            ));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a Sass failure is nonfatal and its compilation ID can be reused.
    @Test
    void recoversAfterCompilationFailure() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(3, compileString("a {b: }"));

            var failure = compileResponse(harness.receive(), 3);
            assertTrue(failure.hasFailure());
            assertFalse(failure.getFailure().getMessage().isBlank());

            harness.send(3, compileString("a {b: 1px + 2px}"));
            var success = compileResponse(harness.receive(), 3);
            assertTrue(success.hasSuccess());
            assertTrue(success.getSuccess().getCss().contains("b: 3px"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies compiler diagnostics are emitted as log events before completion.
    @Test
    void emitsLogEvent() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(4, compileString("a {@debug hello}"));

            var first = harness.receive();
            assertEquals(4, first.compilationId());
            assertTrue(first.message().hasLogEvent());
            assertEquals(
                    LogEventType.DEBUG,
                    first.message().getLogEvent().getType()
            );
            assertEquals(
                    "hello",
                    first.message().getLogEvent().getMessage()
            );

            var terminal = compileResponse(harness.receive(), 4);
            assertTrue(terminal.hasSuccess());

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Applies output style and charset options, including the pinned
    /// compiler's expanded fallback for an unknown style enum.
    @Test
    void appliesOutputStyleAndCharsetOptions() throws Exception {
        var input = InboundMessage.CompileRequest.StringInput.newBuilder()
                .setSource("a { b: café; }")
                .setSyntax(Syntax.SCSS);

        try (var harness = new CompilerHarness()) {
            var expanded = InboundMessage.CompileRequest.newBuilder()
                    .setString(input)
                    .setStyle(OutputStyle.EXPANDED)
                    .setCharset(true);
            harness.send(
                    40,
                    InboundMessage.newBuilder()
                            .setCompileRequest(expanded)
                            .build()
            );
            var expandedCss = compileResponse(harness.receive(), 40)
                    .getSuccess()
                    .getCss();
            assertTrue(expandedCss.startsWith("@charset \"UTF-8\";\n"));
            assertTrue(expandedCss.contains("\na {\n"));

            var compressed = expanded
                    .setStyle(OutputStyle.COMPRESSED);
            harness.send(
                    41,
                    InboundMessage.newBuilder()
                            .setCompileRequest(compressed)
                            .build()
            );
            var compressedCss = compileResponse(harness.receive(), 41)
                    .getSuccess()
                    .getCss();
            assertTrue(compressedCss.startsWith("\uFEFFa{"));
            assertFalse(compressedCss.contains("@charset"));

            var unknown = expanded
                    .setStyleValue(91)
                    .setCharset(false);
            harness.send(
                    42,
                    InboundMessage.newBuilder()
                            .setCompileRequest(unknown)
                            .build()
            );
            var unknownCss = compileResponse(harness.receive(), 42)
                    .getSuccess()
                    .getCss();
            assertFalse(unknownCss.startsWith("@charset"));
            assertTrue(unknownCss.startsWith("a {\n"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Emits invalid deprecation configuration warnings in API processing
    /// order and suppresses them when the request is silent.
    @Test
    void validatesDeprecationOptionsAndHonorsSilent() throws Exception {
        var request = compileString("a { b: c; }")
                .getCompileRequest()
                .toBuilder()
                .addSilenceDeprecation("invalid-silence")
                .addFatalDeprecation("invalid-fatal")
                .addFutureDeprecation("invalid-future");

        try (var harness = new CompilerHarness()) {
            harness.send(
                    43,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request)
                            .build()
            );

            var messages = List.of(
                    "Invalid deprecation id \"invalid-silence\".",
                    "Invalid deprecation id or version \"invalid-fatal\".",
                    "Invalid deprecation id \"invalid-future\"."
            );
            for (var message : messages) {
                var event = harness.receive();
                assertEquals(43, event.compilationId());
                assertTrue(event.message().hasLogEvent());
                assertEquals(
                        LogEventType.WARNING,
                        event.message().getLogEvent().getType()
                );
                assertEquals(
                        message,
                        event.message().getLogEvent().getMessage()
                );
            }
            assertTrue(compileResponse(harness.receive(), 43).hasSuccess());

            harness.send(
                    44,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request.setSilent(true))
                            .build()
            );
            assertTrue(compileResponse(harness.receive(), 44).hasSuccess());

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies source-map requests return a nonempty JSON map.
    @Test
    void returnsSourceMap() throws Exception {
        try (var harness = new CompilerHarness()) {
            var input = InboundMessage.CompileRequest.StringInput.newBuilder()
                    .setSource("a {b: 1px + 2px}")
                    .setSyntax(Syntax.SCSS)
                    .setUrl("file:///input.scss");
            var request = InboundMessage.CompileRequest.newBuilder()
                    .setString(input)
                    .setSourceMap(true);
            harness.send(
                    5,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request)
                            .build()
            );

            var response = compileResponse(harness.receive(), 5);
            assertTrue(response.hasSuccess());
            var sourceMap = response.getSuccess().getSourceMap();
            assertFalse(sourceMap.isBlank());
            assertTrue(sourceMap.contains("\"version\""));
            assertTrue(sourceMap.contains("\"sources\""));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies source contents are emitted only when explicitly requested.
    @Test
    void controlsEmbeddedSourceContents() throws Exception {
        try (var harness = new CompilerHarness()) {
            var input = InboundMessage.CompileRequest.StringInput.newBuilder()
                    .setSource("a {b: café}")
                    .setSyntax(Syntax.SCSS)
                    .setUrl("custom:input.scss");
            var request = InboundMessage.CompileRequest.newBuilder()
                    .setString(input)
                    .setSourceMap(true);
            harness.send(
                    6,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request)
                            .build()
            );
            var withoutSources = compileResponse(harness.receive(), 6)
                    .getSuccess()
                    .getSourceMap();
            assertFalse(withoutSources.contains("\"sourcesContent\""));

            harness.send(
                    7,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request
                                    .setSourceMapIncludeSources(true))
                            .build()
            );
            var withSources = compileResponse(harness.receive(), 7)
                    .getSuccess()
                    .getSourceMap();
            assertTrue(withSources.contains(
                    "\"sourcesContent\":[\"a {b: café}\"]"
            ));

            var anonymousInput = InboundMessage.CompileRequest.StringInput
                    .newBuilder()
                    .setSource("b {c: d}")
                    .setSyntax(Syntax.SCSS);
            harness.send(
                    13,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setString(anonymousInput)
                                            .setSourceMap(true)
                            )
                            .build()
            );
            var anonymousMap = compileResponse(harness.receive(), 13)
                    .getSuccess()
                    .getSourceMap();
            assertTrue(anonymousMap.contains(
                    "\"sources\":[\"data:application/octet-stream;"
            ));

            harness.send(
                    14,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setString(input)
                                            .setSourceMap(false)
                                            .setSourceMapIncludeSources(true)
                            )
                            .build()
            );
            assertEquals(
                    "",
                    compileResponse(harness.receive(), 14)
                            .getSuccess()
                            .getSourceMap()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies alert-color affects only the formatted debug presentation.
    @Test
    void formatsDebugEventWithColor() throws Exception {
        try (var harness = new CompilerHarness()) {
            var request = compileString("a {@debug hello}").toBuilder()
                    .setCompileRequest(
                            compileString("a {@debug hello}")
                                    .getCompileRequest()
                                    .toBuilder()
                                    .setAlertColor(true)
                    )
                    .build();
            harness.send(8, request);

            var logEvent = harness.receive().message().getLogEvent();
            assertEquals("hello", logEvent.getMessage());
            assertEquals(
                    "-:1 \u001b[1mDebug\u001b[0m: hello\n",
                    logEvent.getFormatted()
            );
            assertTrue(compileResponse(harness.receive(), 8).hasSuccess());

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies missing path input reports a zero-length span without a
    /// synthesized formatted diagnostic.
    ///
    /// @param temporaryDirectory the isolated test directory
    @Test
    void reportsMissingFileSpan(@TempDir Path temporaryDirectory)
            throws Exception {
        var input = temporaryDirectory.resolve("missing.scss");
        try (var harness = new CompilerHarness()) {
            harness.send(
                    9,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setPath(input.toString())
                            )
                            .build()
            );

            var failure = compileResponse(harness.receive(), 9).getFailure();
            assertTrue(failure.getMessage().startsWith("Cannot open file: "));
            assertEquals("", failure.getFormatted());
            assertEquals(input.toUri().toString(), failure.getSpan().getUrl());
            assertEquals(0, failure.getSpan().getStart().getOffset());
            assertEquals(0, failure.getSpan().getEnd().getOffset());

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Uses the already-loaded path source for failure context and formatting.
    ///
    /// @param temporaryDirectory the isolated test directory
    @Test
    void reportsPathFailureContext(@TempDir Path temporaryDirectory)
            throws Exception {
        var input = temporaryDirectory.resolve("failure.scss");
        Files.writeString(
                input,
                "a {b: 1px + 1em}",
                StandardCharsets.UTF_8
        );
        try (var harness = new CompilerHarness()) {
            harness.send(
                    18,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setPath(input.toString())
                            )
                            .build()
            );

            var failure = compileResponse(
                    harness.receive(),
                    18
            ).getFailure();
            assertEquals(
                    input.toRealPath(),
                    Path.of(java.net.URI.create(
                            failure.getSpan().getUrl()
                    )).toRealPath()
            );
            assertEquals(
                    "a {b: 1px + 1em}",
                    failure.getSpan().getContext()
            );
            assertTrue(failure.getFormatted().contains(
                    "1 │ a {b: 1px + 1em}"
            ));
            assertTrue(failure.getStackTrace().endsWith(
                    " 1:7  root stylesheet\n"
            ));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies ASCII alerts replace only source-frame glyphs.
    @Test
    void formatsFailureWithAsciiFrame() throws Exception {
        try (var harness = new CompilerHarness()) {
            var base = compileString("a {b: 1px + 1em}")
                    .getCompileRequest()
                    .toBuilder()
                    .setAlertAscii(true);
            harness.send(
                    12,
                    InboundMessage.newBuilder()
                            .setCompileRequest(base)
                            .build()
            );

            var failure = compileResponse(harness.receive(), 12).getFailure();
            assertTrue(failure.getFormatted().contains("\n  ,\n"));
            assertTrue(failure.getFormatted().contains("\n  | "));
            assertTrue(failure.getFormatted().contains("\n  '"));
            assertFalse(failure.getFormatted().contains("╷"));
            assertFalse(failure.getFormatted().contains("│"));
            assertFalse(failure.getFormatted().contains("╵"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies compiler configuration failures carry the protocol's bogus
    /// zero-position span rather than omitting structured location data.
    @Test
    void reportsConfigurationFailureSpan() throws Exception {
        try (var harness = new CompilerHarness()) {
            var request = compileString(".a {value: ok}")
                    .getCompileRequest()
                    .toBuilder()
                    .addGlobalFunctions("invalid");
            harness.send(
                    15,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request)
                            .build()
            );

            var failure = compileResponse(harness.receive(), 15).getFailure();
            assertFalse(failure.getMessage().isBlank());
            assertTrue(failure.hasSpan());
            assertEquals("", failure.getSpan().getUrl());
            assertEquals(0, failure.getSpan().getStart().getOffset());
            assertEquals(0, failure.getSpan().getEnd().getOffset());

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Preserves a multi-line failure span, full-line context, and root frame.
    @Test
    void reportsMultilineFailureContext() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    16,
                    compileString(
                            "a {\n"
                                    + "  b: 1px +\n"
                                    + "      1em;\n"
                                    + "}"
                    )
            );

            var failure = compileResponse(harness.receive(), 16).getFailure();
            assertEquals(
                    "1px +\n      1em",
                    failure.getSpan().getText()
            );
            assertEquals(9, failure.getSpan().getStart().getOffset());
            assertEquals(1, failure.getSpan().getStart().getLine());
            assertEquals(5, failure.getSpan().getStart().getColumn());
            assertEquals(24, failure.getSpan().getEnd().getOffset());
            assertEquals(2, failure.getSpan().getEnd().getLine());
            assertEquals(9, failure.getSpan().getEnd().getColumn());
            assertEquals(
                    "  b: 1px +\n      1em;\n",
                    failure.getSpan().getContext()
            );
            assertEquals(
                    "- 2:6  root stylesheet\n",
                    failure.getStackTrace()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Preserves every Sass call frame from the failure site outward.
    @Test
    void reportsMultipleFailureStackFrames() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    17,
                    compileString(
                            "@function inner() {\n"
                                    + "  @return 1px + 1em;\n"
                                    + "}\n"
                                    + "\n"
                                    + "@function outer() {\n"
                                    + "  @return inner();\n"
                                    + "}\n"
                                    + "\n"
                                    + "a {\n"
                                    + "  b: outer();\n"
                                    + "}"
                    )
            );

            var failure = compileResponse(harness.receive(), 17).getFailure();
            assertEquals(
                    "- 2:11  inner()\n"
                            + "- 6:11  outer()\n"
                            + "- 10:6  root stylesheet\n",
                    failure.getStackTrace()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies distinct compilation IDs may be in flight concurrently.
    @Test
    void compilesDistinctIdsConcurrently() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(10, compileString("a {value: 10}"));
            harness.send(11, compileString("b {value: 11}"));

            var responses = new HashMap<Long, OutboundMessage.CompileResponse>();
            for (var index = 0; index < 2; index++) {
                var received = harness.receive();
                responses.put(
                        received.compilationId(),
                        compileResponse(
                                received,
                                received.compilationId()
                        )
                );
            }

            assertEquals(2, responses.size());
            assertTrue(responses.get(10L).getSuccess().getCss()
                    .contains("value: 10"));
            assertTrue(responses.get(11L).getSuccess().getCss()
                    .contains("value: 11"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a wire-ID violation emits a protocol error and returns status 76.
    @Test
    void returnsProtocolStatusForInvalidWireId() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(0, compileString("a {b: c}"));

            var received = harness.receive();
            assertEquals(0, received.compilationId());
            assertTrue(received.message().hasError());
            assertEquals(
                    ProtocolErrorType.PARAMS,
                    received.message().getError().getType()
            );
            assertEquals(
                    0xffff_ffffL,
                    Integer.toUnsignedLong(
                            received.message().getError().getId()
                    )
            );
            assertEquals(
                    EmbeddedCompiler.PROTOCOL_EXIT_STATUS,
                    harness.awaitStatus()
            );
        }
    }

    /// Creates one SCSS string compile request.
    ///
    /// @param source the SCSS source text
    /// @return the inbound wrapper
    private static InboundMessage compileString(String source) {
        var input = InboundMessage.CompileRequest.StringInput.newBuilder()
                .setSource(source)
                .setSyntax(Syntax.SCSS);
        return InboundMessage.newBuilder()
                .setCompileRequest(
                        InboundMessage.CompileRequest.newBuilder()
                                .setString(input)
                )
                .build();
    }

    /// Sends one compile request that must terminate the endpoint with a
    /// protocol error.
    ///
    /// @param request the malformed compile request
    /// @param type the expected protocol error category
    /// @param message the expected error text
    /// @param status the expected process-compatible exit status
    private static void assertFatalCompileRequest(
            InboundMessage.CompileRequest request,
            ProtocolErrorType type,
            String message,
            int status
    ) throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    19,
                    InboundMessage.newBuilder()
                            .setCompileRequest(request)
                            .build()
            );

            var received = harness.receive();
            assertEquals(19, received.compilationId());
            assertTrue(received.message().hasError());
            assertEquals(type, received.message().getError().getType());
            assertEquals(message, received.message().getError().getMessage());
            assertEquals(status, harness.awaitStatus());
        }
    }

    /// Extracts and verifies one terminal compile response.
    ///
    /// @param received the received packet
    /// @param expectedCompilationId its expected compilation ID
    /// @return the terminal response
    private static OutboundMessage.CompileResponse compileResponse(
            Received received,
            long expectedCompilationId
    ) {
        assertEquals(expectedCompilationId, received.compilationId());
        assertTrue(received.message().hasCompileResponse());
        return received.message().getCompileResponse();
    }

    /// Runs an endpoint against host-side piped streams.
    @NotNullByDefault
    private static final class CompilerHarness implements Closeable {
        /// Receives bytes written by the host.
        private final PipedInputStream compilerInput;

        /// Sends bytes from the host to the compiler.
        private final PipedOutputStream hostOutput;

        /// Receives bytes written by the compiler.
        private final PipedInputStream hostInput;

        /// Sends bytes from the compiler to the host.
        private final PipedOutputStream compilerOutput;

        /// Runs the blocking compiler endpoint.
        private final ExecutorService compilerExecutor;

        /// Bounds blocking host reads.
        private final ExecutorService readerExecutor;

        /// Completes with the endpoint process status.
        private final Future<Integer> status;

        /// Whether the host-to-compiler stream has been closed.
        private boolean inputClosed;

        /// Creates and starts a connected in-process endpoint.
        private CompilerHarness() throws IOException {
            compilerInput = new PipedInputStream(1 << 20);
            hostOutput = new PipedOutputStream(compilerInput);
            hostInput = new PipedInputStream(1 << 20);
            compilerOutput = new PipedOutputStream(hostInput);
            compilerExecutor = Executors.newSingleThreadExecutor();
            readerExecutor = Executors.newSingleThreadExecutor();
            status = compilerExecutor.submit(
                    () -> new EmbeddedCompiler().run(
                            compilerInput,
                            compilerOutput
                    )
            );
        }

        /// Sends one framed inbound message.
        ///
        /// @param compilationId the packet compilation ID
        /// @param message the inbound wrapper
        private void send(long compilationId, InboundMessage message)
                throws IOException {
            EmbeddedPacketIO.write(
                    hostOutput,
                    compilationId,
                    message.toByteArray()
            );
        }

        /// Reads one framed outbound message with a finite timeout.
        ///
        /// @return the decoded packet and wrapper
        private Received receive() throws Exception {
            var read = readerExecutor.submit(
                    () -> EmbeddedPacketIO.read(hostInput)
            );
            final @Nullable EmbeddedPacketIO.Packet packet;
            try {
                packet = read.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                var cause = failure.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw failure;
            } catch (TimeoutException failure) {
                read.cancel(true);
                throw new AssertionError(
                        "Timed out waiting for an embedded compiler packet.",
                        failure
                );
            }
            var present = assertInstanceOf(
                    EmbeddedPacketIO.Packet.class,
                    packet
            );
            return new Received(
                    present.compilationId(),
                    OutboundMessage.parseFrom(present.message())
            );
        }

        /// Closes the host input side to signal clean compiler EOF.
        private void closeInput() throws IOException {
            if (inputClosed) {
                return;
            }
            inputClosed = true;
            hostOutput.close();
        }

        /// Waits for the endpoint process status.
        ///
        /// @return the process-compatible status
        private int awaitStatus() throws Exception {
            try {
                return status.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                var cause = failure.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw failure;
            }
        }

        /// Closes streams and stops helper executors.
        @Override
        public void close() throws IOException {
            closeInput();
            try {
                status.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException ignored) {
                // The test that requested the result reports endpoint failures.
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ignored) {
                // Executor shutdown below terminates a stalled endpoint.
            } finally {
                hostInput.close();
                compilerOutput.close();
                compilerInput.close();
                readerExecutor.shutdownNow();
                compilerExecutor.shutdownNow();
            }
        }
    }

    /// Contains one decoded compiler packet.
    ///
    /// @param compilationId the packet compilation ID
    /// @param message the outbound wrapper
    @NotNullByDefault
    private record Received(
            long compilationId,
            OutboundMessage message
    ) {
        /// Creates one received packet.
        private Received {
        }
    }
}
