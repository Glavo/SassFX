// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the initial command-line contract.
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

    /// Verifies that the scaffold does not claim compilation success.
    @Test
    void rejectsCompilationUntilEngineExists() {
        var error = new StringWriter();
        var commandLine = commandLine(new StringWriter(), error);

        assertEquals(1, commandLine.execute("style.scss"));
        assertTrue(error.toString().contains("compilation engine is not available"));
    }

    /// Creates a command line that writes to the supplied buffers.
    ///
    /// @param output the standard-output buffer
    /// @param error the standard-error buffer
    /// @return the configured command line
    private static CommandLine commandLine(StringWriter output, StringWriter error) {
        return new CommandLine(new ScssfxMain())
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }
}
