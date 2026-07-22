// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the command-line compilation contract.
@NotNullByDefault
final class ScssfxMainTest {
    /// Verifies that help is available without invoking compilation.
    @Test
    void printsHelp() {
        var output = new StringWriter();
        var commandLine = commandLine(output, new StringWriter());

        assertEquals(0, commandLine.execute("--help"));
        assertTrue(output.toString().contains("Usage: scssfx"));
    }

    /// Verifies the reported development version.
    @Test
    void printsVersion() {
        var output = new StringWriter();
        var commandLine = commandLine(output, new StringWriter());

        assertEquals(0, commandLine.execute("--version"));
        assertTrue(output.toString().contains("scssfx 0.1.0-SNAPSHOT"));
    }

    /// Verifies that invocation without an input is a usage error.
    @Test
    void rejectsMissingInput() {
        var output = new StringWriter();
        var commandLine = commandLine(output, new StringWriter());

        assertEquals(2, commandLine.execute());
        assertTrue(output.toString().contains("Usage: scssfx"));
    }

    /// Compiles a single SCSS file to stdout.
    @Test
    void compilesFileToStdout(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        Files.writeString(input, "$c: red; a { color: $c; }");

        var output = new StringWriter();
        var commandLine = commandLine(output, new StringWriter());

        assertEquals(0, commandLine.execute(input.toString()));
        assertEquals(
                """
                        a {
                          color: red;
                        }
                        """.replace("\r\n", "\n"),
                output.toString().replace("\r\n", "\n")
        );
    }

    /// Compiles a single SCSS file to an output path.
    @Test
    void compilesFileToOutputPath(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        var destination = directory.resolve("out").resolve("style.css");
        Files.writeString(input, "a { width: 1px + 2px; }");

        var commandLine = commandLine(new StringWriter(), new StringWriter());
        assertEquals(0, commandLine.execute(input.toString(), "-o", destination.toString()));
        assertEquals(
                """
                        a {
                          width: 3px;
                        }
                        """.replace("\r\n", "\n"),
                Files.readString(destination).replace("\r\n", "\n")
        );
    }

    /// Reports structured compilation failures on stderr.
    @Test
    void reportsCompilationFailures(@TempDir Path directory) throws Exception {
        var input = directory.resolve("bad.scss");
        Files.writeString(input, "a { color: $missing; }");

        var error = new StringWriter();
        var commandLine = commandLine(new StringWriter(), error);

        assertEquals(1, commandLine.execute(input.toString()));
        assertTrue(error.toString().contains("Error: Undefined variable."));
        assertTrue(error.toString().contains("$missing"));
    }

    /// Reports missing input files as IO failures.
    @Test
    void reportsMissingFiles(@TempDir Path directory) {
        var missing = directory.resolve("missing.scss");
        var error = new StringWriter();
        var commandLine = commandLine(new StringWriter(), error);

        assertEquals(1, commandLine.execute(missing.toString()));
        assertTrue(error.toString().startsWith("scssfx:"));
    }

    /// Creates a command line that writes to the supplied buffers.
    ///
    /// @param output the standard-output buffer
    /// @param error  the standard-error buffer
    /// @return the configured command line
    private static CommandLine commandLine(StringWriter output, StringWriter error) {
        return new CommandLine(new ScssfxMain())
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }
}
