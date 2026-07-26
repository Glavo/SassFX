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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies command-line reporting for Sass diagnostic statements.
@NotNullByDefault
final class DiagnosticStatementCliTest {
    /// Prints successful debug and warning diagnostics to standard error.
    @Test
    void printsSuccessfulDiagnosticsToStandardError(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(
                input,
                """
                        @debug "hello";
                        @warn null;
                        a { color: red; }
                        """
        );
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(0, commandLine(output, error).execute(input.toString()));
        assertTrue(output.toString().contains("color: red;"));
        assertTrue(error.toString().contains("Debug: hello"));
        assertTrue(error.toString().contains("Warning: "));
        assertTrue(error.toString().contains("@debug \"hello\""));
        assertTrue(error.toString().contains("@warn null"));
    }

    /// Prints pre-error diagnostics before the primary error and leaves no output file.
    @Test
    void printsEarlierDiagnosticsBeforeError(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        var destination = directory.resolve("output.css");
        Files.writeString(
                input,
                """
                        @debug before;
                        @warn first;
                        @error "fatal";
                        """
        );
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                1,
                commandLine(output, error).execute(
                        "-o",
                        destination.toString(),
                        input.toString()
                )
        );
        assertEquals("", output.toString());
        assertFalse(Files.exists(destination));

        var diagnostics = error.toString();
        var debugIndex = diagnostics.indexOf("Debug: before");
        var warningIndex = diagnostics.indexOf("Warning: first");
        var errorIndex = diagnostics.indexOf("Error: \"fatal\"");
        assertTrue(debugIndex >= 0);
        assertTrue(warningIndex > debugIndex);
        assertTrue(errorIndex > warningIndex, diagnostics);
    }

    /// Creates a command line using isolated output buffers.
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
