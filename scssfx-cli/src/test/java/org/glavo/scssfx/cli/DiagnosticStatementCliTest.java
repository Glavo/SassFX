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
        assertTrue(error.toString().contains("DEBUG: hello"));
        assertTrue(error.toString().contains("WARNING: "));
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
                65,
                commandLine(output, error).execute(
                        "-o",
                        destination.toString(),
                        input.toString()
                )
        );
        assertEquals("", output.toString());
        assertTrue(Files.readString(destination).contains("body::before"));

        var diagnostics = error.toString();
        var debugIndex = diagnostics.indexOf("DEBUG: before");
        var warningIndex = diagnostics.indexOf("WARNING: first");
        var errorIndex = diagnostics.indexOf("Error: \"fatal\"");
        assertTrue(debugIndex >= 0);
        assertTrue(warningIndex > debugIndex);
        assertTrue(errorIndex > warningIndex, diagnostics);
    }

    /// Warns for named colors interpolated into CSS selectors and values.
    @Test
    void warnsForNamedColorInterpolation(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(
                input,
                """
                        $ordinary-string: "#{green}";
                        #{blue} { value: #{red}; }
                        """
        );
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(new StringWriter(), error).execute(input.toString())
        );
        var diagnostics = error.toString();
        assertEquals(
                1,
                countOccurrences(
                        diagnostics,
                        "You probably don't mean to use the color value"
                ),
                diagnostics
        );
        assertTrue(diagnostics.contains("color value blue"), diagnostics);
        assertFalse(diagnostics.contains("color value red"), diagnostics);
        assertFalse(diagnostics.contains("color value green"), diagnostics);
        assertTrue(diagnostics.contains(
                "If you really want to use the color value here, use "
                        + "'\"\" + blue'."
        ), diagnostics);
    }

    /// Suppresses explicit and compiler-generated diagnostics with quiet mode.
    @Test
    void quietSuppressesEveryNonErrorDiagnostic(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(
                input,
                """
                        @warn warning;
                        @debug debug;
                        #{blue} { value: ok; }
                        a { logic: c && d; }
                        """
        );
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(new StringWriter(), error).execute(
                        "--quiet",
                        input.toString()
                )
        );
        assertEquals("", error.toString());
    }

    /// Silences compiler warnings only for load-path dependencies.
    @Test
    void quietDepsDistinguishesRelativeAndLoadPathSources(
            @TempDir Path directory
    ) throws Exception {
        var input = directory.resolve("input.scss");
        var local = directory.resolve("_local.scss");
        var dependencies = directory.resolve("dependencies");
        Files.createDirectories(dependencies);
        Files.writeString(
                input,
                """
                        @use "local";
                        @use "dependency";
                        """
        );
        Files.writeString(
                local,
                """
                        #{blue} { value: local; }
                        a { logic: c && d; }
                        """
        );
        Files.writeString(
                dependencies.resolve("_dependency.scss"),
                """
                        #{red} { value: dependency; }
                        b { logic: c && d; }
                        """
        );
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(new StringWriter(), error).execute(
                        "--quiet-deps",
                        "--load-path",
                        dependencies.toString(),
                        input.toString()
                )
        );
        var diagnostics = error.toString();
        assertTrue(diagnostics.contains("color value blue"), diagnostics);
        assertFalse(diagnostics.contains("color value red"), diagnostics);
        assertEquals(
                1,
                countOccurrences(diagnostics, "\"&&\" means two copies"),
                diagnostics
        );
    }

    /// Keeps explicit dependency diagnostics while suppressing runner warnings.
    @Test
    void quietDepsPreservesExplicitDependencyDiagnostics(
            @TempDir Path directory
    ) throws Exception {
        var input = directory.resolve("input.scss");
        var dependencies = directory.resolve("dependencies");
        Files.createDirectories(dependencies);
        Files.writeString(
                input,
                """
                        @use "dependency";
                        @include dependency.emit;
                        """
        );
        Files.writeString(
                dependencies.resolve("_dependency.scss"),
                """
                        @mixin wrapper {
                          @content;
                        }
                        @mixin emit {
                          @warn explicit-warning;
                          @debug explicit-debug;
                          @include wrapper {
                            #{blue} { value: dependency; }
                          }
                        }
                        """
        );
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(new StringWriter(), error).execute(
                        "--quiet-deps",
                        "--load-path",
                        dependencies.toString(),
                        input.toString()
                )
        );
        var diagnostics = error.toString();
        assertTrue(diagnostics.contains("WARNING: explicit-warning"), diagnostics);
        assertTrue(diagnostics.contains("DEBUG: explicit-debug"), diagnostics);
        assertFalse(diagnostics.contains("color value blue"), diagnostics);
    }

    /// Counts non-overlapping occurrences of a substring.
    ///
    /// @param text the searched text
    /// @param value the nonempty substring
    /// @return the occurrence count
    private static int countOccurrences(String text, String value) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
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
