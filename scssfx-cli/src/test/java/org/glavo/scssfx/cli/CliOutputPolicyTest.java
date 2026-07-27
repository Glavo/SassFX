// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Verifies source-map, charset, error-CSS, and multi-job failure output.
@NotNullByDefault
final class CliOutputPolicyTest {
    /// Writes a sidecar source map by default for file output.
    @Test
    void writesDefaultSidecarSourceMap(@TempDir Path directory)
            throws Exception {
        var sourceDirectory = directory.resolve("source");
        var outputDirectory = directory.resolve("output");
        Files.createDirectories(sourceDirectory);
        var input = sourceDirectory.resolve("in.scss");
        var output = outputDirectory.resolve("out.css");
        Files.writeString(input, "a {b: c}");

        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(input.toString(), output.toString())
        );

        assertEquals(
                """
                        a {
                          b: c;
                        }

                        /*# sourceMappingURL=out.css.map */
                        """.replace("\r\n", "\n"),
                Files.readString(output).replace("\r\n", "\n")
        );
        var mapPath = Path.of(output + ".map");
        var map = Files.readString(mapPath);
        assertFalse(map.endsWith("\n"));
        assertTrue(map.contains("\"version\":3"));
        assertTrue(map.contains("\"sourceRoot\":\"\""));
        assertTrue(map.contains("\"sources\":[\"../source/in.scss\"]"));
        assertTrue(map.contains("\"names\":[]"));
        assertTrue(map.contains("\"mappings\":\""));
        assertTrue(map.contains("\"file\":\"out.css\""));
        assertFalse(map.contains("\"sourcesContent\""));
    }

    /// Omits map output without deleting a pre-existing sidecar.
    @Test
    void disablesSourceMapWithoutDeletingStaleSidecar(
            @TempDir Path directory
    ) throws Exception {
        var input = directory.resolve("in.scss");
        var output = directory.resolve("out.css");
        var map = Path.of(output + ".map");
        Files.writeString(input, "a {b: c}");
        Files.writeString(map, "stale");

        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute("--no-source-map", input.toString(), output.toString())
        );
        assertFalse(Files.readString(output).contains("sourceMappingURL"));
        assertEquals("stale", Files.readString(map));
    }

    /// Embeds a map, absolute source URL, and source contents in stdout.
    @Test
    void embedsSourceMapInStandardOutput(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("in.scss");
        Files.writeString(input, "a {b: c}");
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine("", directory, output, new StringWriter()).execute(
                        "--embed-source-map",
                        "--source-map-urls=absolute",
                        "--embed-sources",
                        input.toString()
                )
        );

        var css = output.toString().replace("\r\n", "\n");
        var map = embeddedSourceMap(css);
        assertTrue(map.contains(
                "\"sources\":[\"" + input.toUri().toASCIIString() + "\"]"
        ), map);
        assertTrue(map.contains("\"sourcesContent\":[\"a {b: c}\"]"));
        assertFalse(map.contains("\"file\""));
        assertFalse(Files.exists(Path.of(input + ".map")));
    }

    /// Preserves imported-source order and aligns embedded contents with the
    /// rewritten relative source URLs.
    ///
    /// @param directory the isolated source and output directory
    @Test
    void mapsMultipleSourcesAndEmbeddedContents(@TempDir Path directory)
            throws Exception {
        var importedDirectory = directory.resolve("dir");
        Files.createDirectories(importedDirectory);
        var imported = importedDirectory.resolve("other.scss");
        var input = directory.resolve("input.scss");
        var output = directory.resolve("output.css");
        Files.writeString(imported, "a { b: imported; }");
        Files.writeString(
                input,
                "@use 'dir/other';\nx { y: root; }\n"
        );

        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(
                        "--embed-sources",
                        input.toString(),
                        output.toString()
                )
        );

        var map = Files.readString(Path.of(output + ".map"));
        assertTrue(map.contains(
                "\"sources\":[\"dir/other.scss\",\"input.scss\"]"
        ), map);
        assertTrue(map.contains(
                "\"sourcesContent\":[\"a { b: imported; }\"," +
                        "\"@use 'dir/other';\\nx { y: root; }\\n\"]"
        ), map);
    }

    /// Rewrites every imported source to an absolute file URL when requested.
    ///
    /// @param directory the isolated source and output directory
    @Test
    void mapsMultipleSourcesToAbsoluteUrls(@TempDir Path directory)
            throws Exception {
        var importedDirectory = directory.resolve("dir");
        Files.createDirectories(importedDirectory);
        var imported = importedDirectory.resolve("other.scss");
        var input = directory.resolve("input.scss");
        var output = directory.resolve("output.css");
        Files.writeString(imported, "a { b: imported; }");
        Files.writeString(input, "@use 'dir/other'; x { y: root; }");

        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(
                        "--source-map-urls=absolute",
                        input.toString(),
                        output.toString()
                )
        );

        var map = Files.readString(Path.of(output + ".map"));
        assertTrue(map.contains(
                "\"sources\":[\""
                        + imported.toRealPath().toUri().toASCIIString()
                        + "\",\""
                        + input.toRealPath().toUri().toASCIIString()
                        + "\"]"
        ), map);
    }

    /// Preserves the spelling of a file name supplied with matching case.
    ///
    /// @param directory the isolated source and output directory
    @Test
    void preservesSourceFileCase(@TempDir Path directory) throws Exception {
        var input = directory.resolve("TeSt.scss");
        var output = directory.resolve("output.css");
        Files.writeString(input, "a { b: c; }");

        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(input.toString(), output.toString())
        );
        var map = Files.readString(Path.of(output + ".map"));
        assertTrue(map.contains("\"sources\":[\"TeSt.scss\"]"), map);
    }

    /// Uses the on-disk file spelling when Windows resolves a root or import
    /// through a differently-cased path.
    ///
    /// @param directory the isolated source and output directory
    @Test
    void preservesResolvedFileCaseOnWindows(@TempDir Path directory)
            throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var actual = directory.resolve("TeSt.scss");
        Files.writeString(actual, "a { b: c; }");

        var rootOutput = directory.resolve("root.css");
        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(
                        directory.resolve("test.scss").toString(),
                        rootOutput.toString()
                )
        );
        var rootMap = Files.readString(Path.of(rootOutput + ".map"));
        assertTrue(rootMap.contains("\"sources\":[\"TeSt.scss\"]"), rootMap);

        var importer = directory.resolve("importer.scss");
        var importOutput = directory.resolve("import.css");
        Files.writeString(importer, "@use 'test'; x { y: z; }");
        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(importer.toString(), importOutput.toString())
        );
        var importMap = Files.readString(Path.of(importOutput + ".map"));
        assertTrue(importMap.contains(
                "\"sources\":[\"TeSt.scss\",\"importer.scss\"]"
        ), importMap);
    }

    /// Embeds a map into a file target while retaining the target file field
    /// and omitting the sidecar.
    ///
    /// @param directory the isolated source and output directory
    @Test
    void embedsSourceMapInFileOutput(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        var outputDirectory = directory.resolve("out");
        var output = outputDirectory.resolve("output.css");
        Files.writeString(input, "a { b: 1 + 2; }");

        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(
                        "--embed-source-map",
                        input.toString(),
                        output.toString()
                )
        );

        var map = embeddedSourceMap(Files.readString(output));
        assertTrue(map.contains("\"sources\":[\"../input.scss\"]"), map);
        assertTrue(map.contains("\"file\":\"output.css\""), map);
        assertFalse(Files.exists(Path.of(output + ".map")));
    }

    /// Percent-encodes non-ASCII embedded source-map JSON without losing its
    /// source contents.
    ///
    /// @param directory the isolated source and output directory
    @Test
    void embedsNonAsciiSourceContents(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        var output = directory.resolve("output.css");
        Files.writeString(input, "a { b: '▼'; }");

        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(
                        "--embed-source-map",
                        "--embed-sources",
                        input.toString(),
                        output.toString()
                )
        );

        var map = embeddedSourceMap(Files.readString(output));
        assertTrue(map.contains(
                "\"sourcesContent\":[\"a { b: '▼'; }\"]"
        ), map);
    }

    /// Uses the stdin contents data URL as the source-map source.
    @Test
    void mapsStandardInputToDataUrl(@TempDir Path directory)
            throws Exception {
        var output = directory.resolve("out.css");

        assertEquals(
                0,
                commandLine(
                        "a {b: c}\n",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute("--stdin", output.toString())
        );

        var map = Files.readString(Path.of(output + ".map"));
        assertTrue(map.contains(
                "\"sources\":[\"data:;charset=utf-8,a%20%7Bb%3A%20c%7D%0A\"]"
        ), map);
    }

    /// Reports source-map option conflicts using Dart Sass precedence.
    @Test
    void rejectsSourceMapOptionConflicts(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("in.scss");
        var output = directory.resolve("out.css");
        Files.writeString(input, "a {b: c}");

        assertUsageFailure(
                directory,
                "--source-map-urls isn't allowed with --no-source-map.",
                "--no-source-map",
                "--source-map-urls=absolute",
                "--embed-sources",
                "--embed-source-map",
                input.toString(),
                output.toString()
        );
        assertUsageFailure(
                directory,
                "When printing to stdout, --source-map requires --embed-source-map.",
                "--source-map",
                "--source-map-urls=absolute",
                "--embed-sources",
                input.toString()
        );
        assertUsageFailure(
                directory,
                "--source-map-urls=relative isn't allowed when printing to stdout.",
                "--source-map-urls=relative",
                "--embed-source-map",
                input.toString()
        );
        assertUsageFailure(
                directory,
                "--embed-sources isn't allowed with --no-source-map.",
                "--no-source-map",
                "--embed-sources",
                input.toString(),
                output.toString()
        );
        assertUsageFailure(
                directory,
                "--embed-source-map isn't allowed with --no-source-map.",
                "--no-source-map",
                "--embed-source-map",
                input.toString(),
                output.toString()
        );
        assertUsageFailure(
                directory,
                "When printing to stdout, --source-map-urls requires "
                        + "--embed-source-map.",
                "--source-map-urls=absolute",
                input.toString()
        );
        assertUsageFailure(
                directory,
                "When printing to stdout, --embed-sources requires "
                        + "--embed-source-map.",
                "--embed-sources",
                input.toString()
        );
        assertFalse(Files.exists(output));
    }

    /// Emits charset markers only for non-ASCII output when enabled.
    @Test
    void emitsCharsetMarkers(@TempDir Path directory) throws Exception {
        var input = directory.resolve("in.scss");
        Files.writeString(input, "a {b: 👭}");
        var expanded = new StringWriter();

        assertEquals(
                0,
                commandLine("", directory, expanded, new StringWriter())
                        .execute(input.toString())
        );
        assertTrue(expanded.toString().startsWith("@charset \"UTF-8\";\n"));

        var withoutCharset = new StringWriter();
        assertEquals(
                0,
                commandLine("", directory, withoutCharset, new StringWriter())
                        .execute("--no-charset", input.toString())
        );
        assertFalse(withoutCharset.toString().contains("@charset"));

        var compressed = directory.resolve("compressed.css");
        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(
                                "--style=compressed",
                                "--no-source-map",
                                input.toString(),
                                compressed.toString()
                        )
        );
        var bytes = Files.readAllBytes(compressed);
        assertArrayEquals(
                new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
                new byte[]{bytes[0], bytes[1], bytes[2]}
        );
    }

    /// Applies textual charset and source-map policies to JavaFX CSS.
    @Test
    void appliesTextPoliciesToJavaFxCss(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("in.scss");
        var output = directory.resolve("out.css");
        Files.writeString(
                input,
                "Label {-fx-font-family: \"字体\"}"
        );

        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(
                                "--target=javafx-css",
                                "--javafx-target=27",
                                input.toString(),
                                output.toString()
                        )
        );
        var css = Files.readString(output);
        assertTrue(css.startsWith("@charset \"UTF-8\";\n"));
        assertTrue(css.contains(
                "/*# sourceMappingURL=out.css.map */"
        ));
        assertTrue(Files.exists(Path.of(output + ".map")));
    }

    /// Emits error CSS for file output but not implicit stdout output.
    @Test
    void appliesDefaultErrorCssPolicy(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("bad.scss");
        var output = directory.resolve("out.css");
        Files.writeString(input, "a {b: $missing}");

        var stdout = new StringWriter();
        assertEquals(
                65,
                commandLine("", directory, stdout, new StringWriter())
                        .execute(input.toString())
        );
        assertEquals("", stdout.toString());

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(input.toString(), output.toString())
        );
        var errorCss = Files.readString(output);
        assertTrue(errorCss.startsWith("/* Error: Undefined variable."));
        assertTrue(errorCss.contains("body::before {"));
        assertTrue(errorCss.contains("white-space: pre;"));
        assertTrue(errorCss.contains("content: \"Error: Undefined variable."));
        assertFalse(errorCss.contains("sourceMappingURL"));
        assertFalse(Files.exists(Path.of(output + ".map")));
    }

    /// Emits explicitly requested error CSS to stdout.
    @Test
    void emitsExplicitErrorCssToStandardOutput(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("bad.scss");
        Files.writeString(input, "a {b: $missing}");
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine("", directory, output, error)
                        .execute("--error-css", input.toString())
        );
        assertTrue(output.toString().contains("body::before"));
        assertTrue(error.toString().contains("Undefined variable"));
    }

    /// Honors both explicit error-CSS settings for file and stdout output.
    @Test
    void appliesExplicitErrorCssPolicyAcrossDestinations(
            @TempDir Path directory
    ) throws Exception {
        var input = directory.resolve("bad.scss");
        var output = directory.resolve("out.css");
        Files.writeString(input, "a {b: $missing}");

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(
                                "--error-css",
                                input.toString(),
                                output.toString()
                        )
        );
        assertTrue(Files.readString(output).contains("body::before"));

        var stdout = new StringWriter();
        assertEquals(
                65,
                commandLine("", directory, stdout, new StringWriter())
                        .execute("--no-error-css", input.toString())
        );
        assertEquals("", stdout.toString());
    }

    /// Deletes an old textual destination when error CSS is disabled.
    @Test
    void deletesFailedOutputWithoutErrorCss(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("bad.scss");
        var output = directory.resolve("out.css");
        var map = Path.of(output + ".map");
        Files.writeString(input, "a {b: $missing}");
        Files.writeString(output, "old css");
        Files.writeString(map, "stale map");

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(
                                "--no-error-css",
                                input.toString(),
                                output.toString()
                        )
        );
        assertFalse(Files.exists(output));
        assertEquals("stale map", Files.readString(map));
    }

    /// Replaces error CSS and a stale map after the source becomes valid.
    @Test
    void recoversFailedFileOutput(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        var output = directory.resolve("output.css");
        var map = Path.of(output + ".map");
        Files.writeString(input, "a {b: $missing}");
        Files.writeString(map, "stale map");

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(input.toString(), output.toString())
        );
        assertTrue(Files.readString(output).contains("body::before"));
        assertEquals("stale map", Files.readString(map));

        Files.writeString(input, "a {b: valid}");
        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(input.toString(), output.toString())
        );
        var css = Files.readString(output);
        assertTrue(css.contains("b: valid;"));
        assertFalse(css.contains("body::before"));
        assertFalse(Files.readString(map).equals("stale map"));
    }

    /// Stops serial multi-input compilation after the first failed job.
    @Test
    void stopsAfterFirstFailureWhenRequested(@TempDir Path directory)
            throws Exception {
        var invalid = directory.resolve("invalid.scss");
        var valid = directory.resolve("valid.scss");
        var invalidOutput = directory.resolve("invalid.css");
        var validOutput = directory.resolve("valid.css");
        Files.writeString(invalid, "a {b: $missing}");
        Files.writeString(valid, "b {color: blue}");

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), new StringWriter())
                        .execute(
                                "--stop-on-error",
                                invalid + ":" + invalidOutput,
                                valid + ":" + validOutput
                        )
        );
        assertTrue(Files.exists(invalidOutput));
        assertFalse(Files.exists(validOutput));
    }

    /// Rejects source-map generation for binary BSS output.
    @Test
    void rejectsExplicitBssSourceMap(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("in.scss");
        var output = directory.resolve("out.bss");
        Files.writeString(input, "Pane {-fx-opacity: 1}");

        assertUsageFailure(
                directory,
                "source maps are not supported for the bss target",
                "--target=bss",
                "--source-map",
                input.toString(),
                output.toString()
        );
    }

    /// Asserts one usage failure and its primary diagnostic.
    ///
    /// @param directory the command working directory
    /// @param message the expected diagnostic fragment
    /// @param arguments command-line arguments
    private static void assertUsageFailure(
            Path directory,
            String message,
            String... arguments
    ) {
        var output = new StringWriter();
        var error = new StringWriter();
        assertEquals(
                64,
                commandLine("", directory, output, error)
                        .execute(arguments)
        );
        assertTrue(output.toString().contains(message), output.toString());
        assertTrue(output.toString().contains("Usage: scssfx"));
        assertEquals("", error.toString());
    }

    /// Extracts and decodes an embedded source-map data URL from CSS.
    ///
    /// @param css generated CSS containing one embedded map comment
    /// @return the decoded UTF-8 JSON document
    private static String embeddedSourceMap(String css) {
        var prefix =
                "/*# sourceMappingURL=data:application/json;charset=utf-8,";
        var start = css.indexOf(prefix);
        assertTrue(start >= 0, css);
        var end = css.indexOf(" */", start);
        assertTrue(end > start, css);
        var encoded = css.substring(start + prefix.length(), end);
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    /// Creates an isolated command line.
    ///
    /// @param input standard-input text
    /// @param workingDirectory the stdin importer base
    /// @param output standard-output buffer
    /// @param error standard-error buffer
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            Path workingDirectory,
            StringWriter output,
            StringWriter error
    ) {
        return ScssfxMain.configure(new CommandLine(new ScssfxMain(
                new ByteArrayInputStream(
                        input.getBytes(StandardCharsets.UTF_8)
                ),
                workingDirectory
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }
}
