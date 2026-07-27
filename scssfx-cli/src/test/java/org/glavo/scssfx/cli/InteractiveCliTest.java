// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Dart Sass-compatible interactive command-line behavior.
@NotNullByDefault
final class InteractiveCliTest {
    /// Evaluates expressions and retains variables for later physical input lines.
    @Test
    void evaluatesExpressionsInOnePersistentSession(@TempDir Path directory) {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "1 + 2\n$value: 4\n$value + 1\nnull\n",
                        directory,
                        output,
                        error
                ).execute("--interactive")
        );

        assertEquals(
                """
                        >> 1 + 2
                        3
                        >> $value: 4
                        4
                        >> $value + 1
                        5
                        >> null
                        null
                        """.replace("\r\n", "\n"),
                normalize(output.toString())
        );
        assertEquals("", error.toString());
    }

    /// Loads local modules relative to the interactive working directory.
    @Test
    void loadsModulesAndExposesNamespaces(@TempDir Path directory)
            throws Exception {
        Files.writeString(directory.resolve("_theme.scss"), "$color: teal;");
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "@use \"theme\"\ntheme.$color\n",
                        directory,
                        output,
                        error
                ).execute("-i")
        );

        assertEquals(
                """
                        >> @use "theme"
                        >> theme.$color
                        teal
                        """.replace("\r\n", "\n"),
                normalize(output.toString())
        );
        assertEquals("", error.toString());
    }

    /// Continues evaluating subsequent lines after a SassScript failure.
    @Test
    void recoversAfterInteractiveErrors(@TempDir Path directory) {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "$value: 1\n$value: $missing\n$value\n",
                        directory,
                        output,
                        error
                ).execute("--interactive")
        );

        var normalized = normalize(output.toString());
        assertTrue(normalized.contains(">> $value: 1\n1\n"));
        assertTrue(normalized.contains(">> $value: $missing\n"));
        assertTrue(normalized.contains("Error:"));
        assertTrue(normalized.endsWith(">> $value\n1\n"));
        assertEquals("", error.toString());
    }

    /// Uses Dart Sass's compact prompt-relative pointer for local failures.
    @Test
    void formatsCompactLocalFailures(@TempDir Path directory) {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "1 + 2;\nfoo(\n1 + $x + 3\n",
                        directory,
                        output,
                        error
                ).execute("--interactive", "--no-color", "--no-unicode")
        );

        assertEquals(
                """
                        >> 1 + 2;
                                ^
                        Error: expected no more input.
                        >> foo(
                               ^
                        Error: expected ")".
                        >> 1 + $x + 3
                               ^^
                        Error: Undefined variable.
                        """.replace("\r\n", "\n"),
                normalize(output.toString())
        );
        assertEquals("", error.toString());
    }

    /// Switches later local failures to a complete source frame after a warning.
    @Test
    void formatsFullLocalFailureAfterWarning(@TempDir Path directory) {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "$ratio: (1 / 2)\n1 + $missing\n",
                        directory,
                        output,
                        error
                ).execute("--interactive", "--no-color", "--no-unicode")
        );

        assertTrue(error.toString().contains("DEPRECATION WARNING"));
        assertTrue(normalize(output.toString()).contains(
                """
                        >> 1 + $missing
                        Error: Undefined variable.
                          ,
                        1 | 1 + $missing
                          |     ^^^^^^^^
                          '
                        """.replace("\r\n", "\n").stripTrailing()
        ));
    }

    /// Uses a file-backed diagnostic frame for errors raised by loaded modules.
    @Test
    void formatsLoadedModuleFailures(@TempDir Path directory)
            throws Exception {
        Files.writeString(directory.resolve("_broken.scss"), "$value: 1px +");
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "@use \"broken\"\n1 + 1\n",
                        directory,
                        output,
                        error
                ).execute("--interactive", "--no-color", "--no-unicode")
        );

        var normalized = normalize(output.toString());
        assertTrue(normalized.contains("Error: Expected expression."));
        assertTrue(normalized.contains("1 | $value: 1px +"));
        assertTrue(normalized.endsWith(">> 1 + 1\n2\n"));
        assertEquals("", error.toString());
    }

    /// Treats blank lines as no-ops and terminates successfully at EOF.
    @Test
    void acceptsBlankLinesAndEndOfFile(@TempDir Path directory) {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine("\n   \n1 + 1\n", directory, output, error)
                        .execute("--interactive")
        );

        assertEquals(
                ">> \n>>    \n>> 1 + 1\n2\n",
                normalize(output.toString())
        );
        assertEquals("", error.toString());

        var eofOutput = new StringWriter();
        var eofError = new StringWriter();
        assertEquals(
                0,
                commandLine("", directory, eofOutput, eofError).execute("-i")
        );
        assertEquals("", eofOutput.toString());
        assertEquals("", eofError.toString());
    }

    /// Writes evaluated values and interactive failures to stdout but warnings to stderr.
    @Test
    void separatesInteractiveValuesAndDiagnostics(@TempDir Path directory) {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "$ratio: (1 / 2)\n",
                        directory,
                        output,
                        error
                ).execute("--interactive")
        );

        assertEquals(
                ">> $ratio: (1 / 2)\n0.5\n",
                normalize(output.toString())
        );
        assertTrue(error.toString().contains("slash-div"));
    }

    /// Rejects output-compilation options that cannot affect a shell.
    @Test
    void rejectsInteractiveOptionConflicts(@TempDir Path directory) {
        for (var arguments : new String[][]{
                {"--stdin"},
                {"--indented"},
                {"--style", "compressed"},
                {"--source-map"},
                {"--update"},
                {"--watch"},
                {"--target", "javafx-css"},
                {"--javafx-target", "27"},
                {"--output", directory.resolve("out.css").toString()}
        }) {
            var output = new StringWriter();
            var error = new StringWriter();
            var command = commandLine("", directory, output, error);
            var invocation = new String[arguments.length + 1];
            invocation[0] = "--interactive";
            System.arraycopy(arguments, 0, invocation, 1, arguments.length);

            assertEquals(64, command.execute(invocation));
            assertTrue(output.toString().contains(
                    "isn't allowed with --interactive"
            ));
            assertEquals("", error.toString());
        }
    }

    /// Ignores positional operands in interactive mode, matching Dart Sass.
    @Test
    void ignoresInteractiveOperands(@TempDir Path directory) {
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine("1 + 1\n", directory, output, error)
                        .execute("--interactive", "ignored.scss")
        );
        assertEquals(">> 1 + 1\n2\n", normalize(output.toString()));
        assertEquals("", error.toString());
    }

    /// Preserves interactive options that configure imports and diagnostic presentation.
    @Test
    void acceptsInteractiveImportAndDiagnosticOptions(@TempDir Path directory)
            throws Exception {
        var loadPath = Files.createDirectory(directory.resolve("load-path"));
        Files.writeString(loadPath.resolve("_theme.scss"), "$size: 2px;");
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        "@use \"theme\" as *\n$size\n",
                        directory,
                        output,
                        error
                ).execute(
                        "--interactive",
                        "--load-path",
                        loadPath.toString(),
                        "--quiet",
                        "--color",
                        "--unicode",
                        "--pkg-importer",
                        "node"
                )
        );

        assertTrue(normalize(output.toString()).endsWith(">> $size\n2px\n"));
        assertFalse(error.toString().contains("Error:"));
    }

    /// Supports built-in functions, global modules, and configured modules.
    @Test
    void supportsCompleteInteractiveUseForms(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_theme.scss"),
                """
                        $base: 12 !default;
                        $derived: $base + 13;
                        @function doubled() {
                          @return $base * 2;
                        }
                        """
        );
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        """
                                @use "sass:math"
                                math.abs(-1)
                                @use "theme" with ($base: 1)
                                theme.$base
                                theme.$derived
                                theme.doubled()
                                """,
                        directory,
                        output,
                        error
                ).execute("--interactive")
        );

        var normalized = normalize(output.toString());
        assertTrue(normalized.contains(">> math.abs(-1)\n1\n"));
        assertTrue(normalized.contains(">> theme.$base\n1\n"));
        assertTrue(normalized.contains(">> theme.$derived\n14\n"));
        assertTrue(normalized.contains(">> theme.doubled()\n2\n"));
        assertEquals("", error.toString());

        var globalOutput = new StringWriter();
        var globalError = new StringWriter();
        assertEquals(
                0,
                commandLine(
                        "@use \"theme\" as *\n$base\ndoubled()\n",
                        directory,
                        globalOutput,
                        globalError
                ).execute("--interactive")
        );
        assertTrue(normalize(globalOutput.toString()).endsWith(
                ">> $base\n12\n>> doubled()\n24\n"
        ));
        assertEquals("", globalError.toString());
    }

    /// Creates a command line with isolated standard streams and a working directory.
    ///
    /// @param input UTF-8 interactive input
    /// @param directory base directory for relative module resolution
    /// @param output standard-output capture
    /// @param error standard-error capture
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            Path directory,
            StringWriter output,
            StringWriter error
    ) {
        return ScssfxMain.configure(new CommandLine(new ScssfxMain(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                directory
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }

    /// Normalizes platform line separators for assertions.
    ///
    /// @param value text produced by a command line
    /// @return text using line-feed separators only
    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
