// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the command-option surface inherited from Dart Sass 1.102.0.
@NotNullByDefault
final class CliOptionCompatibilityTest {
    /// Accepts the short style alias and the hidden sass-spec compatibility
    /// switches without exposing the hidden switches in help.
    @Test
    void acceptsStyleAliasAndHiddenCompatibilityOptions() {
        var output = new StringWriter();
        var commandLine = commandLine(
                "a { b: c; }",
                output,
                new StringWriter()
        );

        assertEquals(
                0,
                commandLine.execute(
                        "--precision",
                        "10",
                        "--async",
                        "-s",
                        "compressed",
                        "--stdin"
                )
        );
        assertTrue(output.toString().contains("a{b:c}"));

        var help = new StringWriter();
        var helpCommand = commandLine(
                "",
                help,
                new StringWriter()
        );
        assertEquals(0, helpCommand.execute("--help"));
        assertTrue(help.toString().contains("-s"));
        assertTrue(help.toString().contains("--style"));
        assertFalse(help.toString().contains("--precision"));
        assertFalse(help.toString().contains("--async"));

        assertNotNull(helpCommand.getCommandSpec().findOption("-s"));
        assertNotNull(helpCommand.getCommandSpec().findOption("--precision"));
        assertNotNull(helpCommand.getCommandSpec().findOption("--async"));
    }

    /// Accepts negated input flags and applies their false values.
    ///
    /// @param directory the isolated source directory
    @Test
    void appliesNegatedInputFlags(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("input.scss");
        Files.writeString(source, "a { b: file; }");
        var fileOutput = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "a { b: stdin; }",
                        fileOutput,
                        new StringWriter()
                ).execute(
                        "--no-stdin",
                        source.toString()
                )
        );
        assertTrue(fileOutput.toString().contains("b: file"));
        assertFalse(fileOutput.toString().contains("b: stdin"));

        var syntaxOutput = new StringWriter();
        assertEquals(
                0,
                commandLine(
                        "$value: scss; a { b: $value; }",
                        syntaxOutput,
                        new StringWriter()
                ).execute(
                        "--no-indented",
                        "--stdin"
                )
        );
        assertTrue(syntaxOutput.toString().contains("b: scss"));
    }

    /// Accepts negated source-map content flags and preserves sidecar output.
    ///
    /// @param directory the isolated output directory
    @Test
    void appliesNegatedSourceMapFlags(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("input.scss");
        var destination = directory.resolve("output.css");
        Files.writeString(source, "a { b: c; }");

        assertEquals(
                0,
                commandLine(
                        "",
                        new StringWriter(),
                        new StringWriter()
                ).execute(
                        "--no-embed-sources",
                        "--no-embed-source-map",
                        source.toString(),
                        destination.toString()
                )
        );

        var css = Files.readString(destination);
        var sourceMap = Files.readString(
                directory.resolve("output.css.map")
        );
        assertTrue(css.contains("sourceMappingURL=output.css.map"));
        assertFalse(css.contains("data:application/json"));
        assertFalse(sourceMap.contains("\"sourcesContent\""));
    }

    /// Accepts negated warning flags and retains ordinary diagnostic output.
    @Test
    void appliesNegatedDiagnosticFlags() {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "a { @debug hello; b: c; }",
                        output,
                        error
                ).execute(
                        "--no-quiet",
                        "--no-quiet-deps",
                        "--no-verbose",
                        "--stdin"
                )
        );
        assertTrue(output.toString().contains("b: c"));
        assertTrue(error.toString().contains("DEBUG: hello"));
    }

    /// Creates a command line with isolated UTF-8 streams.
    ///
    /// @param input standard-input contents
    /// @param output standard-output buffer
    /// @param error standard-error buffer
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            StringWriter output,
            StringWriter error
    ) {
        return SassFXMain.configure(new CommandLine(new SassFXMain(
                new ByteArrayInputStream(
                        input.getBytes(StandardCharsets.UTF_8)
                )
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }
}
