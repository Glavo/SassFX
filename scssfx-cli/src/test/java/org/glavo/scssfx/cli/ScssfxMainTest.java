// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.glavo.scssfx.BssTarget;
import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassFileSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /// Compiles a single indented Sass file to stdout.
    @Test
    void compilesIndentedSassFileToStdout(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.sass");
        Files.writeString(
                input,
                """
                        a
                          color: red
                        """
        );

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

    /// Compiles compressed JavaFX CSS to stdout.
    @Test
    void compilesCompressedJavaFxCssToStdout(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        Files.writeString(input, "a { -fx-text-fill: red; }");

        var output = new StringWriter();
        var commandLine = commandLine(output, new StringWriter());

        assertEquals(
                0,
                commandLine.execute(
                        "--target",
                        "javafx-css",
                        "--javafx-compatibility",
                        "27",
                        "--style",
                        "compressed",
                        input.toString()
                )
        );
        assertEquals(
                "a{-fx-text-fill:red}\n".replace("\r\n", "\n"),
                output.toString().replace("\r\n", "\n")
        );
    }

    /// Compiles JavaFX 27 BSS to an explicitly selected binary output file.
    @Test
    void compilesBssToOutputPath(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        var destination = directory.resolve("out").resolve("style.bss");
        Files.writeString(input, "Pane { -fx-opacity: 0.5; }");

        var output = new StringWriter();
        var commandLine = commandLine(output, new StringWriter());

        assertEquals(
                0,
                commandLine.execute(
                        "--target",
                        "bss",
                        "--javafx-compatibility",
                        "27",
                        "-o",
                        destination.toString(),
                        input.toString()
                )
        );

        var expected = remainingBytes(new SassCompiler().compile(
                new SassFileSource(input, Syntax.SCSS),
                new BssTarget(JavaFXCompatibility.JAVAFX27)
        ).output());
        var actual = Files.readAllBytes(destination);
        assertArrayEquals(expected, actual);
        assertEquals(9, Short.toUnsignedInt(ByteBuffer.wrap(actual).getShort()));
        assertEquals("", output.toString());
    }

    /// Rejects BSS output directed to standard output.
    @Test
    void rejectsBssOutputWithoutFile(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        Files.writeString(input, "Pane { -fx-opacity: 0.5; }");

        var output = new StringWriter();
        var error = new StringWriter();
        var commandLine = commandLine(output, error);

        assertEquals(2, commandLine.execute("--target", "bss", input.toString()));
        assertEquals("", output.toString());
        assertTrue(error.toString().contains("BSS output requires an output path"));
    }

    /// Leaves the BSS destination absent when serialization rejects the stylesheet.
    @Test
    void leavesNoBssOutputForCompilationFailure(@TempDir Path directory) throws Exception {
        var input = directory.resolve("unsupported.scss");
        var destination = directory.resolve("out").resolve("style.bss");
        Files.writeString(input, "Pane + .button { -fx-opacity: 0.5; }");

        var error = new StringWriter();
        var commandLine = commandLine(new StringWriter(), error);

        assertEquals(
                1,
                commandLine.execute(
                        "--target",
                        "bss",
                        "-o",
                        destination.toString(),
                        input.toString()
                )
        );
        assertFalse(Files.exists(destination));
        assertTrue(error.toString().contains("BSS output supports only descendant and child"));
    }
    /// Rejects text-only and JavaFX-only options when their targets do not support them.
    @Test
    void rejectsTargetIncompatibleOptions(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        var destination = directory.resolve("style.bss");
        Files.writeString(input, "Pane { -fx-opacity: 0.5; }");

        var bssError = new StringWriter();
        var bssCommand = commandLine(new StringWriter(), bssError);
        assertEquals(
                2,
                bssCommand.execute(
                        "--target",
                        "bss",
                        "--style",
                        "compressed",
                        "-o",
                        destination.toString(),
                        input.toString()
                )
        );
        assertTrue(bssError.toString().contains("--style is supported only"));
        assertFalse(Files.exists(destination));

        var cssError = new StringWriter();
        var cssCommand = commandLine(new StringWriter(), cssError);
        assertEquals(
                2,
                cssCommand.execute("--javafx-compatibility", "27", input.toString())
        );
        assertTrue(cssError.toString().contains("--javafx-compatibility is supported only"));
    }

    /// Reports unsupported output option values as usage errors.
    @Test
    void rejectsUnsupportedOutputOptions(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        Files.writeString(input, "a { color: red; }");

        var error = new StringWriter();
        var commandLine = commandLine(new StringWriter(), error);

        assertEquals(2, commandLine.execute("--style", "dense", input.toString()));
        assertTrue(error.toString().contains("unsupported output style 'dense'"));
    }

    /// Accepts every boundary of the configurable JavaFX target range.
    @Test
    void acceptsJavaFxTargetRange(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        Files.writeString(input, "Pane { -fx-opacity: 1; }");

        for (var version : new String[]{"8", "27"}) {
            var commandLine = commandLine(new StringWriter(), new StringWriter());
            assertEquals(
                    0,
                    commandLine.execute(
                            "--target",
                            "javafx-css",
                            "--javafx-compatibility",
                            version,
                            input.toString()
                    )
            );
        }
    }

    /// Rejects JavaFX target values outside the supported integer range.
    @Test
    void rejectsInvalidJavaFxTargets(@TempDir Path directory) throws Exception {
        var input = directory.resolve("style.scss");
        Files.writeString(input, "Pane { -fx-opacity: 1; }");

        for (var version : new String[]{"7", "28", "17.0", "current"}) {
            var error = new StringWriter();
            var commandLine = commandLine(new StringWriter(), error);
            assertEquals(
                    2,
                    commandLine.execute(
                            "--target",
                            "javafx-css",
                            "--javafx-compatibility",
                            version,
                            input.toString()
                    )
            );
            assertTrue(error.toString().contains(
                    "expected an integer from 8 through 27"
            ));
        }
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

    /// Copies the remaining binary document bytes without changing the supplied buffer.
    ///
    /// @param buffer the read-only BSS buffer
    /// @return a newly allocated byte array containing the remaining bytes
    private static byte[] remainingBytes(@Unmodifiable ByteBuffer buffer) {
        var copy = buffer.duplicate();
        var bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
