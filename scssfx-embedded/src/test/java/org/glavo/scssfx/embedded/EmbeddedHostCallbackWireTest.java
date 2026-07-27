// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.NodePackageImporter;
import com.sass_lang.embedded_protocol.OutboundMessage;
import com.sass_lang.embedded_protocol.ProtocolError;
import com.sass_lang.embedded_protocol.ProtocolErrorType;
import com.sass_lang.embedded_protocol.Syntax;
import com.sass_lang.embedded_protocol.Value;
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

/// Verifies host callbacks through the framed Embedded Sass duplex endpoint.
@NotNullByDefault
final class EmbeddedHostCallbackWireTest {
    /// Rejects non-canonical schemes on path, file, and unset importers before
    /// checking whether the importer union itself is present.
    @Test
    void restrictsNonCanonicalSchemesToHostImporters() throws Exception {
        var descriptors = List.of(
                InboundMessage.CompileRequest.Importer.newBuilder()
                        .setPath("somewhere")
                        .addNonCanonicalScheme("u"),
                InboundMessage.CompileRequest.Importer.newBuilder()
                        .setFileImporterId(1)
                        .addNonCanonicalScheme("u"),
                InboundMessage.CompileRequest.Importer.newBuilder()
                        .addNonCanonicalScheme("u")
        );

        for (var index = 0; index < descriptors.size(); index++) {
            try (var harness = new CompilerHarness()) {
                var compilationId = 30L + index;
                harness.send(
                        compilationId,
                        compileString(
                                "a { b: c; }",
                                descriptors.get(index),
                                null
                        )
                );

                var received = harness.receive();
                assertEquals(compilationId, received.compilationId());
                assertTrue(received.message().hasError());
                var error = received.message().getError();
                assertEquals(ProtocolErrorType.PARAMS, error.getType());
                assertEquals(
                        "Importer.non_canonical_scheme may only be set along "
                                + "with Importer.importer.importer_id",
                        error.getMessage()
                );
                assertEquals(
                        EmbeddedCompiler.PROTOCOL_EXIT_STATUS,
                        harness.awaitStatus()
                );
            }
        }
    }

    /// Ignores non-canonical scheme metadata on a node package importer,
    /// matching the pinned compiler's importer decoding behavior.
    @Test
    void ignoresNonCanonicalSchemesForNodePackageImporter(
            @TempDir Path directory
    ) throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    33,
                    compileString(
                            "a { b: c; }",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setNodePackageImporter(
                                            NodePackageImporter.newBuilder()
                                                    .setEntryPointDirectory(
                                                            directory.toString()
                                                    )
                                    )
                                    .addNonCanonicalScheme("U"),
                            null
                    )
            );

            assertTrue(terminal(harness.receive(), 33).hasSuccess());
            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Reports invalid non-canonical schemes as compilation failures while
    /// accepting the lowercase grammar and duplicate declarations.
    @Test
    void validatesNonCanonicalSchemeGrammar() throws Exception {
        try (var harness = new CompilerHarness()) {
            var invalidSchemes = List.of("", "U", "u:");
            for (var index = 0; index < invalidSchemes.size(); index++) {
                var scheme = invalidSchemes.get(index);
                var compilationId = 34L + index;
                harness.send(
                        compilationId,
                        compileString(
                                "a { b: c; }",
                                InboundMessage.CompileRequest.Importer
                                        .newBuilder()
                                        .setImporterId(1)
                                        .addNonCanonicalScheme(scheme),
                                null
                        )
                );

                var failure = terminal(
                        harness.receive(),
                        compilationId
                ).getFailure();
                assertEquals(
                        "\"" + scheme + "\" isn't a valid URL scheme "
                                + "(for example \"file\").",
                        failure.getMessage()
                );
            }

            harness.send(
                    37,
                    compileString(
                            "a { b: c; }",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(1)
                                    .addNonCanonicalScheme("a0+.-")
                                    .addNonCanonicalScheme("u")
                                    .addNonCanonicalScheme("u"),
                            null
                    )
            );
            assertTrue(terminal(harness.receive(), 37).hasSuccess());

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Sends the root URL when canonicalizing a declared non-canonical
    /// absolute scheme, but not for an ordinary absolute scheme.
    @Test
    void controlsContainingUrlForNonCanonicalSchemes() throws Exception {
        for (var declared : List.of(false, true)) {
            try (var harness = new CompilerHarness()) {
                var importer = InboundMessage.CompileRequest.Importer
                        .newBuilder()
                        .setImporterId(1);
                if (declared) {
                    importer.addNonCanonicalScheme("u");
                }
                harness.send(
                        declared ? 39 : 38,
                        compileString(
                                "@use \"u:orange\";",
                                importer,
                                null,
                                "x:original.scss"
                        )
                );

                var canonicalize = callback(
                        harness.receive(),
                        declared ? 39 : 38,
                        OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                ).getCanonicalizeRequest();
                assertEquals(declared, canonicalize.hasContainingUrl());
                if (declared) {
                    assertEquals(
                            "x:original.scss",
                            canonicalize.getContainingUrl()
                    );
                }
                harness.send(
                        declared ? 39 : 38,
                        InboundMessage.newBuilder()
                                .setCanonicalizeResponse(
                                        InboundMessage.CanonicalizeResponse
                                                .newBuilder()
                                                .setId(canonicalize.getId())
                                )
                                .build()
                );
                assertTrue(terminal(
                        harness.receive(),
                        declared ? 39 : 38
                ).hasFailure());

                harness.closeInput();
                assertEquals(0, harness.awaitStatus());
            }
        }
    }

    /// Rejects a canonicalization result whose scheme was declared
    /// non-canonical by the same host importer.
    @Test
    void rejectsCanonicalResultsWithNonCanonicalSchemes() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 40L;
            harness.send(
                    compilationId,
                    compileString(
                            "@use \"other\";",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(1)
                                    .addNonCanonicalScheme("u"),
                            null
                    )
            );

            var canonicalize = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalize.getId())
                                            .setUrl("u:other")
                            )
                            .build()
            );

            var failure = terminal(
                    harness.receive(),
                    compilationId
            ).getFailure();
            assertTrue(
                    failure.getMessage().contains(
                            "uses a scheme declared as non-canonical"
                    ),
                    failure.getMessage()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Applies Dart Sass URL validation and wording to contents importer
    /// canonical and source-map callback results.
    @Test
    void validatesContentsImporterCallbackUrls() throws Exception {
        try (var harness = new CompilerHarness()) {
            var invalidCanonicalUrls = List.of("", "relative");
            for (var index = 0; index < invalidCanonicalUrls.size(); index++) {
                var url = invalidCanonicalUrls.get(index);
                var compilationId = 50L + index;
                harness.send(
                        compilationId,
                        compileString(
                                "@use \"other\";",
                                InboundMessage.CompileRequest.Importer
                                        .newBuilder()
                                        .setImporterId(1),
                                null
                        )
                );
                var request = callback(
                        harness.receive(),
                        compilationId,
                        OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                ).getCanonicalizeRequest();
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setCanonicalizeResponse(
                                        InboundMessage.CanonicalizeResponse
                                                .newBuilder()
                                                .setId(request.getId())
                                                .setUrl(url)
                                )
                                .build()
                );

                assertEquals(
                        "The importer must return an absolute URL, was \""
                                + url + "\"",
                        terminal(
                                harness.receive(),
                                compilationId
                        ).getFailure().getMessage()
                );
            }

            var compilationId = 52L;
            harness.send(
                    compilationId,
                    compileString(
                            "@use \"other\";",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(1),
                            null
                    )
            );
            var canonicalize = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalize.getId())
                                            .setUrl("host:other")
                            )
                            .build()
            );
            var load = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.IMPORT_REQUEST
            ).getImportRequest();
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setImportResponse(
                                    InboundMessage.ImportResponse.newBuilder()
                                            .setId(load.getId())
                                            .setSuccess(
                                                    InboundMessage
                                                            .ImportResponse
                                                            .ImportSuccess
                                                            .newBuilder()
                                                            .setSourceMapUrl(
                                                                    "relative"
                                                            )
                                            )
                            )
                            .build()
            );

            assertEquals(
                    "The importer must return an absolute URL, was "
                            + "\"relative\"",
                    terminal(
                            harness.receive(),
                            compilationId
                    ).getFailure().getMessage()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Applies Dart Sass URL validation and wording to file-import callback
    /// results.
    @Test
    void validatesFileImporterCallbackUrls() throws Exception {
        var urls = List.of("", "foo", "other:foo");
        var messages = List.of(
                "The file importer must return an absolute URL, was \"\"",
                "The file importer must return an absolute URL, was \"foo\"",
                "The file importer must return a file: URL, was \"other:foo\""
        );

        try (var harness = new CompilerHarness()) {
            for (var index = 0; index < urls.size(); index++) {
                var compilationId = 53L + index;
                harness.send(
                        compilationId,
                        compileString(
                                "@use \"other\";",
                                InboundMessage.CompileRequest.Importer
                                        .newBuilder()
                                        .setFileImporterId(1),
                                null
                        )
                );
                var request = callback(
                        harness.receive(),
                        compilationId,
                        OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
                ).getFileImportRequest();
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setFileImportResponse(
                                        InboundMessage.FileImportResponse
                                                .newBuilder()
                                                .setId(request.getId())
                                                .setFileUrl(urls.get(index))
                                )
                                .build()
                );

                assertEquals(
                        messages.get(index),
                        terminal(
                                harness.receive(),
                                compilationId
                        ).getFailure().getMessage()
                );
            }

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Converts callback errors and unset results into compilation failures
    /// without terminating the Embedded connection.
    @Test
    void handlesImporterCallbackErrorsAndNullResults() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    56,
                    compileString(
                            "@use \"other\";",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(1),
                            null
                    )
            );
            var canonicalError = callback(
                    harness.receive(),
                    56,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            harness.send(
                    56,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalError.getId())
                                            .setError("canonical failure")
                            )
                            .build()
            );
            assertImporterFailure(
                    terminal(harness.receive(), 56).getFailure(),
                    "canonical failure"
            );

            harness.send(
                    57,
                    compileString(
                            "@use \"other\";",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(1),
                            null
                    )
            );
            var canonicalNull = callback(
                    harness.receive(),
                    57,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            harness.send(
                    57,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalNull.getId())
                            )
                            .build()
            );
            assertImporterFailure(
                    terminal(harness.receive(), 57).getFailure(),
                    "Can't find stylesheet to import."
            );

            for (var errorResult : List.of(false, true)) {
                var compilationId = errorResult ? 59L : 58L;
                harness.send(
                        compilationId,
                        compileString(
                                "@use \"other\";",
                                InboundMessage.CompileRequest.Importer
                                        .newBuilder()
                                        .setImporterId(1),
                                null
                        )
                );
                var canonicalize = callback(
                        harness.receive(),
                        compilationId,
                        OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                ).getCanonicalizeRequest();
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setCanonicalizeResponse(
                                        InboundMessage.CanonicalizeResponse
                                                .newBuilder()
                                                .setId(canonicalize.getId())
                                                .setUrl("host:other")
                                )
                                .build()
                );
                var load = callback(
                        harness.receive(),
                        compilationId,
                        OutboundMessage.MessageCase.IMPORT_REQUEST
                ).getImportRequest();
                var response = InboundMessage.ImportResponse.newBuilder()
                        .setId(load.getId());
                if (errorResult) {
                    response.setError("load failure");
                }
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setImportResponse(response)
                                .build()
                );
                assertImporterFailure(
                        terminal(
                                harness.receive(),
                                compilationId
                        ).getFailure(),
                        errorResult
                                ? "load failure"
                                : "Can't find stylesheet to import."
                );
            }

            for (var errorResult : List.of(false, true)) {
                var compilationId = errorResult ? 61L : 60L;
                harness.send(
                        compilationId,
                        compileString(
                                "@use \"other\";",
                                InboundMessage.CompileRequest.Importer
                                        .newBuilder()
                                        .setFileImporterId(1),
                                null
                        )
                );
                var file = callback(
                        harness.receive(),
                        compilationId,
                        OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
                ).getFileImportRequest();
                var response = InboundMessage.FileImportResponse.newBuilder()
                        .setId(file.getId());
                if (errorResult) {
                    response.setError("file failure");
                }
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setFileImportResponse(response)
                                .build()
                );
                assertImporterFailure(
                        terminal(
                                harness.receive(),
                                compilationId
                        ).getFailure(),
                        errorResult
                                ? "file failure"
                                : "Can't find stylesheet to import."
                );
            }

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Attempts contents and file importers in their declared interleaved
    /// order.
    @Test
    void preservesImporterDeclarationOrder() throws Exception {
        try (var harness = new CompilerHarness()) {
            var contentsImporters = new java.util.ArrayList<
                    InboundMessage.CompileRequest.Importer.Builder>();
            for (var index = 0; index < 10; index++) {
                contentsImporters.add(
                        InboundMessage.CompileRequest.Importer.newBuilder()
                                .setImporterId(index)
                );
            }
            harness.send(
                    62,
                    compileStringWithImporters(
                            "@use \"other\";",
                            contentsImporters
                    )
            );
            for (var index = 0; index < 10; index++) {
                var request = callback(
                        harness.receive(),
                        62,
                        OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                ).getCanonicalizeRequest();
                assertEquals(index, request.getImporterId());
                harness.send(
                        62,
                        InboundMessage.newBuilder()
                                .setCanonicalizeResponse(
                                        InboundMessage.CanonicalizeResponse
                                                .newBuilder()
                                                .setId(request.getId())
                                )
                                .build()
                );
            }
            assertImporterFailure(
                    terminal(harness.receive(), 62).getFailure(),
                    "Can't find stylesheet to import."
            );

            var mixedImporters = new java.util.ArrayList<
                    InboundMessage.CompileRequest.Importer.Builder>();
            for (var index = 0; index < 10; index++) {
                var importer =
                        InboundMessage.CompileRequest.Importer.newBuilder();
                if (index % 2 == 0) {
                    importer.setFileImporterId(index);
                } else {
                    importer.setImporterId(index);
                }
                mixedImporters.add(importer);
            }
            harness.send(
                    63,
                    compileStringWithImporters(
                            "@use \"other\";",
                            mixedImporters
                    )
            );
            for (var index = 0; index < 10; index++) {
                var received = harness.receive();
                if (index % 2 == 0) {
                    var request = callback(
                            received,
                            63,
                            OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
                    ).getFileImportRequest();
                    assertEquals(index, request.getImporterId());
                    harness.send(
                            63,
                            InboundMessage.newBuilder()
                                    .setFileImportResponse(
                                            InboundMessage.FileImportResponse
                                                    .newBuilder()
                                                    .setId(request.getId())
                                    )
                                    .build()
                    );
                } else {
                    var request = callback(
                            received,
                            63,
                            OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                    ).getCanonicalizeRequest();
                    assertEquals(index, request.getImporterId());
                    harness.send(
                            63,
                            InboundMessage.newBuilder()
                                    .setCanonicalizeResponse(
                                            InboundMessage
                                                    .CanonicalizeResponse
                                                    .newBuilder()
                                                    .setId(request.getId())
                                    )
                                    .build()
                    );
                }
            }
            assertImporterFailure(
                    terminal(harness.receive(), 63).getFailure(),
                    "Can't find stylesheet to import."
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Gives the importer that loaded a stylesheet first chance to resolve its
    /// relative dependencies.
    @Test
    void prioritizesTheOwningContentsImporter() throws Exception {
        var importers = new java.util.ArrayList<
                InboundMessage.CompileRequest.Importer.Builder>();
        for (var index = 0; index < 10; index++) {
            importers.add(
                    InboundMessage.CompileRequest.Importer.newBuilder()
                            .setImporterId(index)
            );
        }

        try (var harness = new CompilerHarness()) {
            harness.send(
                    64,
                    compileStringWithImporters(
                            "@use \"midstream\";",
                            importers
                    )
            );
            for (var index = 0; index < 5; index++) {
                var request = callback(
                        harness.receive(),
                        64,
                        OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                ).getCanonicalizeRequest();
                assertEquals(index, request.getImporterId());
                assertEquals("midstream", request.getUrl());
                harness.send(
                        64,
                        InboundMessage.newBuilder()
                                .setCanonicalizeResponse(
                                        InboundMessage.CanonicalizeResponse
                                                .newBuilder()
                                                .setId(request.getId())
                                )
                                .build()
                );
            }

            var owner = callback(
                    harness.receive(),
                    64,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            assertEquals(5, owner.getImporterId());
            harness.send(
                    64,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(owner.getId())
                                            .setUrl("custom:foo/bar")
                            )
                            .build()
            );
            var load = callback(
                    harness.receive(),
                    64,
                    OutboundMessage.MessageCase.IMPORT_REQUEST
            ).getImportRequest();
            assertEquals(5, load.getImporterId());
            harness.send(
                    64,
                    InboundMessage.newBuilder()
                            .setImportResponse(
                                    InboundMessage.ImportResponse.newBuilder()
                                            .setId(load.getId())
                                            .setSuccess(
                                                    InboundMessage
                                                            .ImportResponse
                                                            .ImportSuccess
                                                            .newBuilder()
                                                            .setContents(
                                                                    "@use "
                                                                            + "\"upstream\";"
                                                            )
                                            )
                            )
                            .build()
            );

            var nested = callback(
                    harness.receive(),
                    64,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            assertEquals(5, nested.getImporterId());
            assertEquals("custom:foo/upstream", nested.getUrl());
            harness.send(
                    64,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(nested.getId())
                                            .setError("nested stop")
                            )
                            .build()
            );
            assertEquals(
                    "nested stop",
                    terminal(harness.receive(), 64)
                            .getFailure()
                            .getMessage()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Resolves dependencies of a path returned by a file importer directly
    /// against that file before invoking later callbacks.
    ///
    /// @param directory the isolated stylesheet directory
    @Test
    void resolvesReturnedFileDependenciesRelatively(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(
                directory.resolve("midstream.scss"),
                "@use \"upstream\";"
        );
        Files.writeString(
                directory.resolve("upstream.scss"),
                "a { b: c; }"
        );
        var importers = new java.util.ArrayList<
                InboundMessage.CompileRequest.Importer.Builder>();
        for (var index = 0; index < 10; index++) {
            importers.add(
                    InboundMessage.CompileRequest.Importer.newBuilder()
                            .setFileImporterId(index)
            );
        }

        try (var harness = new CompilerHarness()) {
            harness.send(
                    65,
                    compileStringWithImporters(
                            "@use \"midstream\";",
                            importers
                    )
            );
            for (var index = 0; index < 5; index++) {
                var request = callback(
                        harness.receive(),
                        65,
                        OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
                ).getFileImportRequest();
                assertEquals(index, request.getImporterId());
                assertEquals("midstream", request.getUrl());
                harness.send(
                        65,
                        InboundMessage.newBuilder()
                                .setFileImportResponse(
                                        InboundMessage.FileImportResponse
                                                .newBuilder()
                                                .setId(request.getId())
                                )
                                .build()
                );
            }

            var owner = callback(
                    harness.receive(),
                    65,
                    OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
            ).getFileImportRequest();
            assertEquals(5, owner.getImporterId());
            harness.send(
                    65,
                    InboundMessage.newBuilder()
                            .setFileImportResponse(
                                    InboundMessage.FileImportResponse
                                            .newBuilder()
                                            .setId(owner.getId())
                                            .setFileUrl(
                                                    directory.resolve(
                                                            "midstream"
                                                    ).toUri().toString()
                                            )
                            )
                            .build()
            );

            var response = terminal(harness.receive(), 65);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("b: c"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a contents importer canonicalizes and loads one stylesheet.
    @Test
    void servesContentsImporterCallbacks() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 41L;
            harness.send(
                    compilationId,
                    compileString(
                            """
                                    @use "theme";
                                    a { value: theme.$value; }
                                    """,
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(17),
                            null
                    )
            );

            var canonicalize = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            assertEquals(17, canonicalize.getImporterId());
            assertEquals("theme", canonicalize.getUrl());
            assertFalse(canonicalize.getFromImport());
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalize.getId())
                                            .setUrl("host:theme")
                            )
                            .build()
            );

            var load = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.IMPORT_REQUEST
            ).getImportRequest();
            assertEquals(17, load.getImporterId());
            assertEquals("host:theme", load.getUrl());
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setImportResponse(
                                    InboundMessage.ImportResponse.newBuilder()
                                            .setId(load.getId())
                                            .setSuccess(
                                                    InboundMessage
                                                            .ImportResponse
                                                            .ImportSuccess
                                                            .newBuilder()
                                                            .setContents(
                                                                    "$value: 42;"
                                                            )
                                                            .setSyntax(
                                                                    Syntax.SCSS
                                                            )
                                            )
                            )
                            .build()
            );

            var response = terminal(harness.receive(), compilationId);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("value: 42"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Uses host-loaded text to render an imported failure without re-reading
    /// its canonical URL.
    @Test
    void reportsImportedFailureSourceContext() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 42L;
            harness.send(
                    compilationId,
                    compileString(
                            "@use \"broken\";",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(18),
                            null
                    )
            );

            var canonicalize = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalize.getId())
                                            .setUrl("host:broken")
                            )
                            .build()
            );

            var load = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.IMPORT_REQUEST
            ).getImportRequest();
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setImportResponse(
                                    InboundMessage.ImportResponse.newBuilder()
                                            .setId(load.getId())
                                            .setSuccess(
                                                    InboundMessage
                                                            .ImportResponse
                                                            .ImportSuccess
                                                            .newBuilder()
                                                            .setContents(
                                                                    ".broken {\n"
                                                                            + "  value: 1px +\n"
                                                                            + "      1em;\n"
                                                                            + "}\n"
                                                            )
                                                            .setSyntax(
                                                                    Syntax.SCSS
                                                            )
                                            )
                            )
                            .build()
            );

            var failure = terminal(
                    harness.receive(),
                    compilationId
            ).getFailure();
            assertEquals("host:broken", failure.getSpan().getUrl());
            assertEquals(
                    "1px +\n      1em",
                    failure.getSpan().getText()
            );
            assertEquals(
                    "  value: 1px +\n      1em;\n",
                    failure.getSpan().getContext()
            );
            assertTrue(failure.getFormatted().contains(
                    "  value: 1px +"
            ));
            assertEquals(
                    "host:broken 2:10  root stylesheet\n",
                    failure.getStackTrace()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a string entrypoint importer resolves a root-source load.
    @Test
    void usesStringInputImporterForRootRelativeLoad() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 47L;
            var input = InboundMessage.CompileRequest.StringInput.newBuilder()
                    .setSource(
                            """
                                    @use "entry-dependency";
                                    a { value: entry-dependency.$value; }
                                    """
                    )
                    .setSyntax(Syntax.SCSS)
                    .setImporter(
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(31)
                    );
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setString(input)
                            )
                            .build()
            );

            var canonicalize = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            assertEquals(31, canonicalize.getImporterId());
            assertEquals("entry-dependency", canonicalize.getUrl());
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalize.getId())
                                            .setUrl("entry:dependency")
                            )
                            .build()
            );

            var load = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.IMPORT_REQUEST
            ).getImportRequest();
            assertEquals(31, load.getImporterId());
            assertEquals("entry:dependency", load.getUrl());
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setImportResponse(
                                    InboundMessage.ImportResponse.newBuilder()
                                            .setId(load.getId())
                                            .setSuccess(
                                                    InboundMessage
                                                            .ImportResponse
                                                            .ImportSuccess
                                                            .newBuilder()
                                                            .setContents(
                                                                    "$value: 91;"
                                                            )
                                                            .setSyntax(
                                                                    Syntax.SCSS
                                                            )
                                            )
                            )
                            .build()
            );

            var response = terminal(harness.receive(), compilationId);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("value: 91"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Uses a string input's file importer only when its root URL cannot
    /// provide a filesystem base.
    ///
    /// @param directory the isolated stylesheet directory
    @Test
    void handlesStringInputFileImporterWithAndWithoutBaseUrl(
            @TempDir Path directory
    ) throws Exception {
        var imported = directory.resolve("other.scss");
        Files.writeString(imported, "a { b: c; }");

        try (var harness = new CompilerHarness()) {
            var withoutBase = InboundMessage.CompileRequest.StringInput
                    .newBuilder()
                    .setSource("@use \"other\";")
                    .setSyntax(Syntax.SCSS)
                    .setImporter(
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setFileImporterId(9)
                    );
            harness.send(
                    66,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setString(withoutBase)
                            )
                            .build()
            );
            var callback = callback(
                    harness.receive(),
                    66,
                    OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
            ).getFileImportRequest();
            assertEquals(9, callback.getImporterId());
            assertEquals("other", callback.getUrl());
            harness.send(
                    66,
                    InboundMessage.newBuilder()
                            .setFileImportResponse(
                                    InboundMessage.FileImportResponse
                                            .newBuilder()
                                            .setId(callback.getId())
                                            .setFileUrl(
                                                    directory.resolve("other")
                                                            .toUri()
                                                            .toString()
                                            )
                            )
                            .build()
            );
            assertTrue(terminal(harness.receive(), 66).hasSuccess());

            var withBase = InboundMessage.CompileRequest.StringInput
                    .newBuilder()
                    .setSource("@use \"other\";")
                    .setSyntax(Syntax.SCSS)
                    .setUrl(directory.resolve("input.scss").toUri().toString())
                    .setImporter(
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setFileImporterId(9)
                    );
            harness.send(
                    67,
                    InboundMessage.newBuilder()
                            .setCompileRequest(
                                    InboundMessage.CompileRequest.newBuilder()
                                            .setString(withBase)
                            )
                            .build()
            );
            var response = terminal(harness.receive(), 67);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("b: c"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Marks canonicalize and file-import callbacks originating from legacy
    /// imports.
    @Test
    void reportsLegacyImportContextToImporters() throws Exception {
        for (var fileImporter : List.of(false, true)) {
            try (var harness = new CompilerHarness()) {
                var compilationId = fileImporter ? 69L : 68L;
                var importer =
                        InboundMessage.CompileRequest.Importer.newBuilder();
                if (fileImporter) {
                    importer.setFileImporterId(1);
                } else {
                    importer.setImporterId(1);
                }
                harness.send(
                        compilationId,
                        compileString(
                                "@import \"other\";",
                                importer,
                                null
                        )
                );

                var received = receiveCallbackSkippingLogs(
                        harness,
                        compilationId
                );
                if (fileImporter) {
                    var request = callback(
                            received,
                            compilationId,
                            OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
                    ).getFileImportRequest();
                    assertTrue(request.getFromImport());
                    harness.send(
                            compilationId,
                            InboundMessage.newBuilder()
                                    .setFileImportResponse(
                                            InboundMessage.FileImportResponse
                                                    .newBuilder()
                                                    .setId(request.getId())
                                    )
                                    .build()
                    );
                } else {
                    var request = callback(
                            received,
                            compilationId,
                            OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                    ).getCanonicalizeRequest();
                    assertTrue(request.getFromImport());
                    harness.send(
                            compilationId,
                            InboundMessage.newBuilder()
                                    .setCanonicalizeResponse(
                                            InboundMessage
                                                    .CanonicalizeResponse
                                                    .newBuilder()
                                                    .setId(request.getId())
                                    )
                                    .build()
                    );
                }
                var response = terminal(
                        receiveCallbackSkippingLogs(
                                harness,
                                compilationId
                        ),
                        compilationId
                );
                assertEquals(
                        "Can't find stylesheet to import.",
                        response.getFailure().getMessage()
                );

                harness.closeInput();
                assertEquals(0, harness.awaitStatus());
            }
        }
    }

    /// Verifies a file importer delegates file resolution back to the compiler.
    ///
    /// @param temporaryDirectory the isolated stylesheet directory
    @Test
    void servesFileImporterCallback(@TempDir Path temporaryDirectory)
            throws Exception {
        var imported = temporaryDirectory.resolve("theme.scss");
        Files.writeString(
                imported,
                "$value: 73;",
                StandardCharsets.UTF_8
        );

        try (var harness = new CompilerHarness()) {
            var compilationId = 42L;
            harness.send(
                    compilationId,
                    compileString(
                            """
                                    @use "theme";
                                    a { value: theme.$value; }
                                    """,
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setFileImporterId(23),
                            null
                    )
            );

            var request = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
            ).getFileImportRequest();
            assertEquals(23, request.getImporterId());
            assertEquals("theme", request.getUrl());
            assertFalse(request.getFromImport());
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setFileImportResponse(
                                    InboundMessage.FileImportResponse
                                            .newBuilder()
                                            .setId(request.getId())
                                            .setFileUrl(imported.toUri().toString())
                            )
                            .build()
            );

            var response = terminal(harness.receive(), compilationId);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("value: 73"));
            assertTrue(response.getLoadedUrlsList().contains(
                    imported.toUri().toString()
            ));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies number, list, and map arguments survive host round trips.
    @Test
    void roundTripsGlobalFunctionValues() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 43L;
            harness.send(
                    compilationId,
                    compileString(
                            """
                                    @use "sass:meta";
                                    a {
                                      number: round-trip(2px);
                                      list: meta.inspect(round-trip((1, 2)));
                                      map: meta.inspect(round-trip((x: 3)));
                                    }
                                    """,
                            null,
                            "round-trip($value)"
                    )
            );

            var expectedCases = new Value.ValueCase[]{
                    Value.ValueCase.NUMBER,
                    Value.ValueCase.LIST,
                    Value.ValueCase.MAP
            };
            for (var expectedCase : expectedCases) {
                var request = callback(
                        harness.receive(),
                        compilationId,
                        OutboundMessage.MessageCase.FUNCTION_CALL_REQUEST
                ).getFunctionCallRequest();
                assertEquals(
                        OutboundMessage.FunctionCallRequest.IdentifierCase.NAME,
                        request.getIdentifierCase()
                );
                assertEquals("round-trip", request.getName());
                assertEquals(1, request.getArgumentsCount());
                assertEquals(
                        expectedCase,
                        request.getArguments(0).getValueCase()
                );
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setFunctionCallResponse(
                                        InboundMessage.FunctionCallResponse
                                                .newBuilder()
                                                .setId(request.getId())
                                                .setSuccess(
                                                        request.getArguments(0)
                                                )
                                )
                                .build()
                );
            }

            var response = terminal(harness.receive(), compilationId);
            assertTrue(response.hasSuccess());
            var css = response.getSuccess().getCss();
            assertTrue(css.contains("number: 2px"));
            assertTrue(css.contains("1, 2"));
            assertTrue(css.contains("x: 3"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a callback response with the wrong ID is connection-fatal.
    @Test
    void rejectsWrongCallbackId() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 44L;
            harness.send(
                    compilationId,
                    compileString(
                            "@use \"missing\";",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(1),
                            null
                    )
            );

            var request = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(request.getId() + 1)
                            )
                            .build()
            );

            var error = assertFatalProtocolError(
                    harness.receive(),
                    compilationId
            );
            assertEquals(
                    "Response ID 1 doesn't match any outstanding requests in "
                            + "compilation 44.",
                    error.getMessage()
            );
            assertEquals(
                    EmbeddedCompiler.PROTOCOL_EXIT_STATUS,
                    harness.awaitStatus()
            );
        }
    }

    /// Verifies a callback response of the wrong type is connection-fatal.
    @Test
    void rejectsWrongCallbackType() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 45L;
            harness.send(
                    compilationId,
                    compileString(
                            "@use \"missing\";",
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(1),
                            null
                    )
            );

            var request = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setImportResponse(
                                    InboundMessage.ImportResponse.newBuilder()
                                            .setId(request.getId())
                            )
                            .build()
            );

            var error = assertFatalProtocolError(
                    harness.receive(),
                    compilationId
            );
            assertEquals(
                    "Request ID 0 doesn't match response type "
                            + "InboundMessage_ImportResponse in compilation "
                            + "45.",
                    error.getMessage()
            );
            assertEquals(
                    EmbeddedCompiler.PROTOCOL_EXIT_STATUS,
                    harness.awaitStatus()
            );
        }
    }

    /// Verifies a returned host function is called again by its opaque ID.
    @Test
    void callsReturnedHostFunctionById() throws Exception {
        try (var harness = new CompilerHarness()) {
            var compilationId = 46L;
            var hostFunctionId = 8675;
            harness.send(
                    compilationId,
                    compileString(
                            """
                                    @use "sass:meta";
                                    a { value: meta.call(make-function(), 4); }
                                    """,
                            null,
                            "make-function()"
                    )
            );

            var factory = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.FUNCTION_CALL_REQUEST
            ).getFunctionCallRequest();
            assertEquals(
                    OutboundMessage.FunctionCallRequest.IdentifierCase.NAME,
                    factory.getIdentifierCase()
            );
            assertEquals("make-function", factory.getName());
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setFunctionCallResponse(
                                    InboundMessage.FunctionCallResponse
                                            .newBuilder()
                                            .setId(factory.getId())
                                            .setSuccess(
                                                    Value.newBuilder()
                                                            .setHostFunction(
                                                                    Value
                                                                            .HostFunction
                                                                            .newBuilder()
                                                                            .setId(
                                                                                    hostFunctionId
                                                                            )
                                                                            .setSignature(
                                                                                    "double($value)"
                                                                            )
                                                            )
                                            )
                            )
                            .build()
            );

            var invocation = callback(
                    harness.receive(),
                    compilationId,
                    OutboundMessage.MessageCase.FUNCTION_CALL_REQUEST
            ).getFunctionCallRequest();
            assertEquals(
                    OutboundMessage.FunctionCallRequest.IdentifierCase
                            .FUNCTION_ID,
                    invocation.getIdentifierCase()
            );
            assertEquals(hostFunctionId, invocation.getFunctionId());
            assertEquals(1, invocation.getArgumentsCount());
            assertEquals(
                    Value.ValueCase.NUMBER,
                    invocation.getArguments(0).getValueCase()
            );
            assertEquals(
                    4.0,
                    invocation.getArguments(0).getNumber().getValue()
            );
            harness.send(
                    compilationId,
                    InboundMessage.newBuilder()
                            .setFunctionCallResponse(
                                    InboundMessage.FunctionCallResponse
                                            .newBuilder()
                                            .setId(invocation.getId())
                                            .setSuccess(
                                                    Value.newBuilder()
                                                            .setNumber(
                                                                    Value.Number
                                                                            .newBuilder()
                                                                            .setValue(
                                                                                    8
                                                                            )
                                                            )
                                            )
                            )
                            .build()
            );

            var response = terminal(harness.receive(), compilationId);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss().contains("value: 8"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies callbacks for two active compilations remain independently routed.
    @Test
    void interleavesCallbacksAcrossCompilationIds() throws Exception {
        try (var harness = new CompilerHarness()) {
            var importerCompilationId = 48L;
            var functionCompilationId = 49L;
            harness.send(
                    importerCompilationId,
                    compileString(
                            """
                                    @use "interleaved";
                                    a { value: interleaved.$value; }
                                    """,
                            InboundMessage.CompileRequest.Importer.newBuilder()
                                    .setImporterId(41),
                            null
                    )
            );

            var canonicalize = callback(
                    harness.receive(),
                    importerCompilationId,
                    OutboundMessage.MessageCase.CANONICALIZE_REQUEST
            ).getCanonicalizeRequest();

            // Keep the importer callback outstanding while a second
            // compilation starts and emits an unrelated function callback.
            harness.send(
                    functionCompilationId,
                    compileString(
                            "b { value: host-value(3); }",
                            null,
                            "host-value($input)"
                    )
            );
            var function = callback(
                    harness.receive(),
                    functionCompilationId,
                    OutboundMessage.MessageCase.FUNCTION_CALL_REQUEST
            ).getFunctionCallRequest();

            harness.send(
                    importerCompilationId,
                    InboundMessage.newBuilder()
                            .setCanonicalizeResponse(
                                    InboundMessage.CanonicalizeResponse
                                            .newBuilder()
                                            .setId(canonicalize.getId())
                                            .setUrl("interleaved:module")
                            )
                            .build()
            );
            var load = callback(
                    harness.receive(),
                    importerCompilationId,
                    OutboundMessage.MessageCase.IMPORT_REQUEST
            ).getImportRequest();

            harness.send(
                    functionCompilationId,
                    InboundMessage.newBuilder()
                            .setFunctionCallResponse(
                                    InboundMessage.FunctionCallResponse
                                            .newBuilder()
                                            .setId(function.getId())
                                            .setSuccess(
                                                    Value.newBuilder()
                                                            .setNumber(
                                                                    Value.Number
                                                                            .newBuilder()
                                                                            .setValue(
                                                                                    103
                                                                            )
                                                            )
                                            )
                            )
                            .build()
            );
            var functionResponse = terminal(
                    harness.receive(),
                    functionCompilationId
            );
            assertTrue(functionResponse.hasSuccess());
            assertTrue(
                    functionResponse.getSuccess().getCss()
                            .contains("value: 103")
            );

            harness.send(
                    importerCompilationId,
                    InboundMessage.newBuilder()
                            .setImportResponse(
                                    InboundMessage.ImportResponse.newBuilder()
                                            .setId(load.getId())
                                            .setSuccess(
                                                    InboundMessage
                                                            .ImportResponse
                                                            .ImportSuccess
                                                            .newBuilder()
                                                            .setContents(
                                                                    "$value: 107;"
                                                            )
                                                            .setSyntax(
                                                                    Syntax.SCSS
                                                            )
                                            )
                            )
                            .build()
            );
            var importerResponse = terminal(
                    harness.receive(),
                    importerCompilationId
            );
            assertTrue(importerResponse.hasSuccess());
            assertTrue(
                    importerResponse.getSuccess().getCss()
                            .contains("value: 107")
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Uses `containing_url_unused` to cache contents-importer
    /// canonicalization responses.
    @Test
    void cachesContentsImporterResponsesWhenContainingUrlIsUnused()
            throws Exception {
        for (var containingUrlUnused : List.of(false, true)) {
            try (var harness = new CompilerHarness()) {
                var compilationId = containingUrlUnused ? 71L : 70L;
                harness.send(
                        compilationId,
                        compileString(
                                """
                                        @import "shared";
                                        @import "shared";
                                        """,
                                InboundMessage.CompileRequest.Importer
                                        .newBuilder()
                                        .setImporterId(81),
                                null,
                                "root:entry.scss"
                        )
                );

                var first = callback(
                        receiveCallbackSkippingLogs(
                                harness,
                                compilationId
                        ),
                        compilationId,
                        OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                ).getCanonicalizeRequest();
                assertEquals("shared", first.getUrl());
                assertEquals("root:entry.scss", first.getContainingUrl());
                assertTrue(first.getFromImport());
                var canonicalizeResponse =
                        InboundMessage.CanonicalizeResponse.newBuilder()
                                .setId(first.getId())
                                .setUrl("host:shared");
                if (containingUrlUnused) {
                    canonicalizeResponse.setContainingUrlUnused(true);
                }
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setCanonicalizeResponse(canonicalizeResponse)
                                .build()
                );

                var load = callback(
                        receiveCallbackSkippingLogs(
                                harness,
                                compilationId
                        ),
                        compilationId,
                        OutboundMessage.MessageCase.IMPORT_REQUEST
                ).getImportRequest();
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setImportResponse(
                                        InboundMessage.ImportResponse
                                                .newBuilder()
                                                .setId(load.getId())
                                                .setSuccess(
                                                        InboundMessage
                                                                .ImportResponse
                                                                .ImportSuccess
                                                                .newBuilder()
                                                                .setContents(
                                                                        ".shared { value: loaded; }"
                                                                )
                                                                .setSyntax(
                                                                        Syntax.SCSS
                                                                )
                                                )
                                )
                                .build()
                );

                var next = receiveCallbackSkippingLogs(
                        harness,
                        compilationId
                );
                if (!containingUrlUnused) {
                    var second = callback(
                            next,
                            compilationId,
                            OutboundMessage.MessageCase.CANONICALIZE_REQUEST
                    ).getCanonicalizeRequest();
                    assertEquals("shared", second.getUrl());
                    assertEquals(
                            "root:entry.scss",
                            second.getContainingUrl()
                    );
                    harness.send(
                            compilationId,
                            InboundMessage.newBuilder()
                                    .setCanonicalizeResponse(
                                            InboundMessage
                                                    .CanonicalizeResponse
                                                    .newBuilder()
                                                    .setId(second.getId())
                                                    .setUrl("host:shared")
                                    )
                                    .build()
                    );
                    next = receiveCallbackSkippingLogs(
                            harness,
                            compilationId
                    );
                }

                var response = terminal(next, compilationId);
                assertTrue(response.hasSuccess());
                var css = response.getSuccess().getCss();
                assertTrue(css.indexOf(".shared") != css.lastIndexOf(".shared"));

                harness.closeInput();
                assertEquals(0, harness.awaitStatus());
            }
        }
    }

    /// Uses `containing_url_unused` to cache file-importer responses.
    ///
    /// @param directory the isolated imported stylesheet directory
    @Test
    void cachesFileImporterResponsesWhenContainingUrlIsUnused(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(
                directory.resolve("shared.scss"),
                ".shared { value: loaded; }"
        );
        for (var containingUrlUnused : List.of(false, true)) {
            try (var harness = new CompilerHarness()) {
                var compilationId = containingUrlUnused ? 73L : 72L;
                harness.send(
                        compilationId,
                        compileString(
                                """
                                        @import "shared";
                                        @import "shared";
                                        """,
                                InboundMessage.CompileRequest.Importer
                                        .newBuilder()
                                        .setFileImporterId(82),
                                null,
                                "root:entry.scss"
                        )
                );

                var first = callback(
                        receiveCallbackSkippingLogs(
                                harness,
                                compilationId
                        ),
                        compilationId,
                        OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
                ).getFileImportRequest();
                assertEquals("shared", first.getUrl());
                assertEquals("root:entry.scss", first.getContainingUrl());
                assertTrue(first.getFromImport());
                var fileResponse =
                        InboundMessage.FileImportResponse.newBuilder()
                                .setId(first.getId())
                                .setFileUrl(
                                        directory.resolve("shared")
                                                .toUri()
                                                .toString()
                                );
                if (containingUrlUnused) {
                    fileResponse.setContainingUrlUnused(true);
                }
                harness.send(
                        compilationId,
                        InboundMessage.newBuilder()
                                .setFileImportResponse(fileResponse)
                                .build()
                );

                var next = receiveCallbackSkippingLogs(
                        harness,
                        compilationId
                );
                if (!containingUrlUnused) {
                    var second = callback(
                            next,
                            compilationId,
                            OutboundMessage.MessageCase.FILE_IMPORT_REQUEST
                    ).getFileImportRequest();
                    assertEquals("shared", second.getUrl());
                    assertEquals(
                            "root:entry.scss",
                            second.getContainingUrl()
                    );
                    harness.send(
                            compilationId,
                            InboundMessage.newBuilder()
                                    .setFileImportResponse(
                                            InboundMessage.FileImportResponse
                                                    .newBuilder()
                                                    .setId(second.getId())
                                                    .setFileUrl(
                                                            directory.resolve(
                                                                    "shared"
                                                            ).toUri().toString()
                                                    )
                                    )
                                    .build()
                    );
                    next = receiveCallbackSkippingLogs(
                            harness,
                            compilationId
                    );
                }

                var response = terminal(next, compilationId);
                assertTrue(response.hasSuccess());
                var css = response.getSuccess().getCss();
                assertTrue(css.indexOf(".shared") != css.lastIndexOf(".shared"));

                harness.closeInput();
                assertEquals(0, harness.awaitStatus());
            }
        }
    }

    /// Creates one SCSS string compile request with one optional callback.
    ///
    /// @param source the SCSS source
    /// @param importer the importer descriptor, or {@code null}
    /// @param function the global function signature, or {@code null}
    /// @return the inbound wrapper
    private static InboundMessage compileString(
            String source,
            @Nullable InboundMessage.CompileRequest.Importer.Builder importer,
            @Nullable String function
    ) {
        return compileString(source, importer, function, null);
    }

    /// Creates a string compile request with optional host callbacks and URL.
    ///
    /// @param source the SCSS source
    /// @param importer an optional importer descriptor
    /// @param function an optional global-function signature
    /// @param url an optional canonical root URL
    /// @return the inbound wrapper
    private static InboundMessage compileString(
            String source,
            @Nullable InboundMessage.CompileRequest.Importer.Builder importer,
            @Nullable String function,
            @Nullable String url
    ) {
        var request = InboundMessage.CompileRequest.newBuilder()
                .setString(
                        InboundMessage.CompileRequest.StringInput.newBuilder()
                                .setSource(source)
                                .setSyntax(Syntax.SCSS)
                );
        if (url != null) {
            request.getStringBuilder().setUrl(url);
        }
        if (importer != null) {
            request.addImporters(importer);
        }
        if (function != null) {
            request.addGlobalFunctions(function);
        }
        return InboundMessage.newBuilder()
                .setCompileRequest(request)
                .build();
    }

    /// Creates a string compile request with ordered importer descriptors.
    ///
    /// @param source the SCSS source
    /// @param importers importer builders in declaration order
    /// @return the inbound wrapper
    private static InboundMessage compileStringWithImporters(
            String source,
            List<InboundMessage.CompileRequest.Importer.Builder> importers
    ) {
        var request = InboundMessage.CompileRequest.newBuilder()
                .setString(
                        InboundMessage.CompileRequest.StringInput.newBuilder()
                                .setSource(source)
                                .setSyntax(Syntax.SCSS)
                );
        for (var importer : importers) {
            request.addImporters(importer);
        }
        return InboundMessage.newBuilder()
                .setCompileRequest(request)
                .build();
    }

    /// Extracts one outbound callback after checking its routing metadata.
    ///
    /// @param received the decoded packet
    /// @param compilationId the expected compilation ID
    /// @param messageCase the expected callback case
    /// @return the callback wrapper
    private static OutboundMessage callback(
            Received received,
            long compilationId,
            OutboundMessage.MessageCase messageCase
    ) {
        assertEquals(compilationId, received.compilationId());
        assertEquals(messageCase, received.message().getMessageCase());
        return received.message();
    }

    /// Reads through preceding log events and returns the next callback.
    ///
    /// @param harness the active duplex harness
    /// @param compilationId the expected compilation ID
    /// @return the first non-log packet
    private static Received receiveCallbackSkippingLogs(
            CompilerHarness harness,
            long compilationId
    ) throws Exception {
        var received = harness.receive();
        while (received.message().hasLogEvent()) {
            assertEquals(compilationId, received.compilationId());
            received = harness.receive();
        }
        return received;
    }

    /// Extracts one terminal compilation response.
    ///
    /// @param received the decoded packet
    /// @param compilationId the expected compilation ID
    /// @return the terminal response
    private static OutboundMessage.CompileResponse terminal(
            Received received,
            long compilationId
    ) {
        assertEquals(compilationId, received.compilationId());
        assertTrue(received.message().hasCompileResponse());
        return received.message().getCompileResponse();
    }

    /// Checks the common source association of an importer compilation
    /// failure.
    ///
    /// @param failure the importer failure
    /// @param message the expected failure message
    private static void assertImporterFailure(
            OutboundMessage.CompileResponse.CompileFailure failure,
            String message
    ) {
        assertEquals(message, failure.getMessage());
        assertEquals("@use \"other\"", failure.getSpan().getText());
        assertEquals("- 1:1  root stylesheet\n", failure.getStackTrace());
    }

    /// Checks one fatal protocol-error packet.
    ///
    /// @param received the decoded packet
    /// @param compilationId the expected compilation ID
    /// @return the validated protocol error
    private static ProtocolError assertFatalProtocolError(
            Received received,
            long compilationId
    ) {
        assertEquals(compilationId, received.compilationId());
        assertTrue(received.message().hasError());
        var error = received.message().getError();
        assertEquals(
                ProtocolErrorType.PARAMS,
                error.getType()
        );
        return error;
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
        /// Creates one decoded packet.
        private Received {
        }
    }
}
