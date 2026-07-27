// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

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

/// Verifies command-line logging and deprecation controls.
@NotNullByDefault
final class DeprecationOptionsCliTest {
    /// Prints five repeated deprecations and a summary unless verbose is selected.
    @Test
    void controlsDeprecationRepetition(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, repeatedSlashDivision(7));

        var limitedError = new StringWriter();
        assertEquals(
                0,
                commandLine(new StringWriter(), limitedError)
                        .execute(input.toString())
        );
        assertEquals(
                5,
                countOccurrences(
                        limitedError.toString(),
                        "DEPRECATION WARNING"
                )
        );
        assertTrue(limitedError.toString().contains(
                "2 repetitive deprecation warnings omitted"
        ));

        var verboseError = new StringWriter();
        assertEquals(
                0,
                commandLine(new StringWriter(), verboseError)
                        .execute("--verbose", input.toString())
        );
        assertEquals(
                7,
                countOccurrences(
                        verboseError.toString(),
                        "DEPRECATION WARNING"
                )
        );
        assertFalse(verboseError.toString().contains("warnings omitted"));
    }

    /// Silences selected deprecations and makes fatal categories fail compilation.
    @Test
    void appliesSilenceAndFatalFlags(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "a { value: (1/2); }");

        var silentError = new StringWriter();
        assertEquals(
                0,
                commandLine(new StringWriter(), silentError).execute(
                        "--silence-deprecation",
                        "slash-div",
                        input.toString()
                )
        );
        assertEquals("", silentError.toString());

        var fatalOutput = new StringWriter();
        var fatalError = new StringWriter();
        assertEquals(
                65,
                commandLine(fatalOutput, fatalError).execute(
                        "--fatal-deprecation",
                        "slash-div",
                        input.toString()
                )
        );
        assertEquals("", fatalOutput.toString());
        assertTrue(fatalError.toString().contains(
                "slash-div deprecation to be fatal"
        ));
    }

    /// Accepts a bounded version selector for fatal deprecations.
    @Test
    void expandsFatalVersionSelectors(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "a { value: (1/2); }");
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine(new StringWriter(), error).execute(
                        "--fatal-deprecation",
                        "1.79.0",
                        input.toString()
                )
        );
        assertTrue(error.toString().contains(
                "slash-div deprecation to be fatal"
        ));
    }

    /// Rejects unknown identifiers and versions newer than the pinned compiler.
    @Test
    void rejectsInvalidDeprecationSelectors(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "");
        var unknownOutput = new StringWriter();

        assertEquals(
                64,
                commandLine(unknownOutput, new StringWriter()).execute(
                        "--silence-deprecation",
                        "unknown",
                        input.toString()
                )
        );
        assertTrue(unknownOutput.toString().contains(
                "Invalid deprecation \"unknown\""
        ));

        var versionOutput = new StringWriter();
        assertEquals(
                64,
                commandLine(versionOutput, new StringWriter()).execute(
                        "--fatal-deprecation",
                        "1.102.0",
                        input.toString()
                )
        );
        assertTrue(versionOutput.toString().contains(
                "requires a version less than or equal"
        ));
    }

    /// Suppresses non-error output without disabling fatal processing.
    @Test
    void quietSuppressesMessagesButNotFatalErrors(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(
                input,
                """
                        @debug debug;
                        @warn warning;
                        a { value: (1/2); }
                        """
        );
        var quietError = new StringWriter();
        assertEquals(
                0,
                commandLine(new StringWriter(), quietError)
                        .execute("--quiet", input.toString())
        );
        assertEquals("", quietError.toString());

        var fatalError = new StringWriter();
        assertEquals(
                65,
                commandLine(new StringWriter(), fatalError).execute(
                        "--quiet",
                        "--fatal-deprecation",
                        "slash-div",
                        input.toString()
                )
        );
        assertTrue(fatalError.toString().contains("Error:"));
        assertFalse(fatalError.toString().contains("Debug:"));
        assertFalse(fatalError.toString().contains("Warning: warning"));
    }

    /// Combines load paths and quiet dependency processing.
    @Test
    void suppressesLoadPathDependencyWarnings(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        var dependencies = directory.resolve("dependencies");
        Files.createDirectories(dependencies);
        Files.writeString(input, "@use \"dependency\";");
        Files.writeString(
                dependencies.resolve("_dependency.scss"),
                "$value: (1/2);"
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
        assertEquals("", error.toString());
    }

    /// Creates source containing distinct slash-division spans.
    private static String repeatedSlashDivision(int count) {
        var source = new StringBuilder();
        for (var index = 0; index < count; index++) {
            source.append("$value-")
                    .append(index)
                    .append(": (1/2);\n");
        }
        return source.toString();
    }

    /// Counts non-overlapping occurrences of a substring.
    private static int countOccurrences(String text, String value) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    /// Creates a command line with isolated output writers.
    private static CommandLine commandLine(
            StringWriter output,
            StringWriter error
    ) {
        return new CommandLine(new SassFXMain())
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }
}
