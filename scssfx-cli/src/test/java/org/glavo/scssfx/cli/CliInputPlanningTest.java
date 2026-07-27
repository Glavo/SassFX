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

/// Verifies stdin, plain-CSS, mapping, and directory input planning.
@NotNullByDefault
final class CliInputPlanningTest {
    /// Compiles SCSS from standard input to standard output.
    @Test
    void compilesStandardInputToStandardOutput() {
        var output = new StringWriter();
        var error = new StringWriter();
        var commandLine = commandLine(
                "$value: 3; a { width: $value * 1px; }",
                output,
                error
        );

        assertEquals(0, commandLine.execute("--stdin"));
        assertTrue(output.toString().contains("width: 3px;"));
        assertEquals("", error.toString());
    }

    /// Selects indented syntax for standard input and writes a positional output.
    @Test
    void compilesIndentedStandardInputToFile(@TempDir Path directory)
            throws Exception {
        var destination = directory.resolve("nested").resolve("out.css");
        var commandLine = commandLine(
                "a\n  color: red\n",
                new StringWriter(),
                new StringWriter()
        );

        assertEquals(
                0,
                commandLine.execute(
                        "--stdin",
                        "--indented",
                        destination.toString()
                )
        );
        assertTrue(Files.readString(destination).contains("color: red;"));
    }

    /// Resolves stdin-relative modules from the command working directory.
    @Test
    void resolvesStandardInputImportsFromWorkingDirectory(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(
                directory.resolve("_theme.scss"),
                "$color: rebeccapurple;"
        );
        var output = new StringWriter();
        var commandLine = commandLine(
                "@use 'theme'; a { color: theme.$color; }",
                directory,
                output,
                new StringWriter()
        );

        assertEquals(0, commandLine.execute("--stdin"));
        assertTrue(output.toString().contains("color: rebeccapurple;"));
    }

    /// Accepts the magic stdin source in positional and mapping forms.
    @Test
    void acceptsMagicStandardInputSource(@TempDir Path directory)
            throws Exception {
        var stdout = new StringWriter();
        assertEquals(
                0,
                commandLine(
                        "a { color: red; }",
                        stdout,
                        new StringWriter()
                ).execute("-")
        );
        assertTrue(stdout.toString().contains("color: red;"));

        var destination = directory.resolve("mapped.css");
        assertEquals(
                0,
                commandLine(
                        "b { color: blue; }",
                        new StringWriter(),
                        new StringWriter()
                ).execute("-:" + destination)
        );
        assertTrue(Files.readString(destination).contains("color: blue;"));
    }

    /// Compiles plain CSS with CSS syntax rather than SCSS syntax.
    @Test
    void compilesPlainCssAndRejectsSassSyntax(@TempDir Path directory)
            throws Exception {
        var valid = directory.resolve("valid.css");
        var invalid = directory.resolve("invalid.css");
        Files.writeString(valid, "a { color: red; }");
        Files.writeString(invalid, "$color: red; a { color: $color; }");

        var output = new StringWriter();
        assertEquals(
                0,
                commandLine("", output, new StringWriter())
                        .execute(valid.toString())
        );
        assertTrue(output.toString().contains("color: red;"));

        var error = new StringWriter();
        assertEquals(
                65,
                commandLine("", new StringWriter(), error)
                        .execute(invalid.toString())
        );
        assertTrue(error.toString().contains("Error:"));
    }

    /// Treats explicit unknown and uppercase extensions as SCSS roots.
    @Test
    void defaultsExplicitUnknownExtensionsToScss(@TempDir Path directory)
            throws Exception {
        for (var name : new String[]{"style.input", "style.CSS"}) {
            var source = directory.resolve(name);
            Files.writeString(source, "$color: red; a { color: $color; }");
            var output = new StringWriter();

            assertEquals(
                    0,
                    commandLine("", output, new StringWriter())
                            .execute(source.toString())
            );
            assertTrue(output.toString().contains("color: red;"));
        }
    }

    /// Compiles multiple mapped roots and creates destination directories.
    @Test
    void compilesMultipleMappedRoots(@TempDir Path directory)
            throws Exception {
        var scss = directory.resolve("one.scss");
        var sass = directory.resolve("two.sass");
        var css = directory.resolve("three.css");
        Files.writeString(scss, "a { width: 1px + 2px; }");
        Files.writeString(sass, "b\n  color: blue\n");
        Files.writeString(css, "c { opacity: 0.5; }");

        var first = directory.resolve("out").resolve("one.css");
        var second = directory.resolve("out").resolve("two.css");
        var third = directory.resolve("out").resolve("three.css");
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine("", output, new StringWriter()).execute(
                        scss + ":" + first,
                        sass + ":" + second,
                        css + ":" + third
                )
        );
        assertEquals("", output.toString());
        assertTrue(Files.readString(first).contains("width: 3px;"));
        assertTrue(Files.readString(second).contains("color: blue;"));
        assertTrue(Files.readString(third).contains("opacity: 0.5;"));
    }

    /// Recursively maps directory entrypoints while excluding partials and
    /// unsupported extensions.
    @Test
    void expandsDirectoryMappings(@TempDir Path directory) throws Exception {
        var source = directory.resolve("input");
        var destination = directory.resolve("output");
        Files.createDirectories(source.resolve("nested"));
        Files.createDirectories(source.resolve("_group"));
        Files.writeString(source.resolve("root.scss"), "a { color: red; }");
        Files.writeString(source.resolve("_partial.scss"), "invalid {");
        Files.writeString(
                source.resolve("nested").resolve("child.sass"),
                "b\n  color: blue\n"
        );
        Files.writeString(
                source.resolve("_group").resolve("entry.css"),
                "c { color: green; }"
        );
        Files.writeString(source.resolve("ignored.SCSS"), "invalid {");
        Files.writeString(source.resolve("notes.txt"), "invalid {");

        assertEquals(
                0,
                commandLine("", new StringWriter(), new StringWriter())
                        .execute(source + ":" + destination)
        );

        assertTrue(Files.exists(destination.resolve("root.css")));
        assertTrue(Files.exists(
                destination.resolve("nested").resolve("child.css")
        ));
        assertTrue(Files.exists(
                destination.resolve("_group").resolve("entry.css")
        ));
        assertFalse(Files.exists(destination.resolve("_partial.css")));
        assertFalse(Files.exists(destination.resolve("ignored.css")));
        assertFalse(Files.exists(destination.resolve("notes.css")));
    }

    /// Compiles a standalone directory in place without rewriting CSS roots.
    @Test
    void compilesStandaloneDirectoryInPlace(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("input");
        Files.createDirectories(source);
        var scss = source.resolve("entry.scss");
        var css = source.resolve("existing.css");
        Files.writeString(scss, "a { color: red; }");
        Files.writeString(css, "preserve exactly");

        assertEquals(
                0,
                commandLine("", new StringWriter(), new StringWriter())
                        .execute(source.toString())
        );
        assertTrue(Files.readString(source.resolve("entry.css"))
                .contains("color: red;"));
        assertEquals("preserve exactly", Files.readString(css));
    }

    /// Continues later mappings after an earlier IO failure and returns the
    /// highest encountered exit status.
    @Test
    void continuesAfterMappedFailure(@TempDir Path directory)
            throws Exception {
        var missing = directory.resolve("missing.scss");
        var valid = directory.resolve("valid.scss");
        var missingOutput = directory.resolve("missing.css");
        var validOutput = directory.resolve("valid.css");
        Files.writeString(valid, "a { color: red; }");
        var error = new StringWriter();

        assertEquals(
                66,
                commandLine("", new StringWriter(), error).execute(
                        missing + ":" + missingOutput,
                        valid + ":" + validOutput
                )
        );
        assertFalse(Files.exists(missingOutput));
        assertTrue(Files.readString(validOutput).contains("color: red;"));
        assertTrue(error.toString().contains("missing.scss"));
    }

    /// Continues later mappings after an earlier Sass failure.
    @Test
    void continuesAfterMappedSassFailure(@TempDir Path directory)
            throws Exception {
        var invalid = directory.resolve("invalid.scss");
        var valid = directory.resolve("valid.scss");
        var invalidOutput = directory.resolve("invalid.css");
        var validOutput = directory.resolve("valid.css");
        Files.writeString(invalid, "a { color: $missing; }");
        Files.writeString(valid, "b { color: blue; }");
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine("", new StringWriter(), error).execute(
                        invalid + ":" + invalidOutput,
                        valid + ":" + validOutput
                )
        );
        assertTrue(Files.readString(invalidOutput)
                .contains("body::before"));
        assertTrue(Files.readString(validOutput).contains("color: blue;"));
        assertTrue(error.toString().contains("Undefined variable"));
    }

    /// Rejects incompatible operand modes before creating any output.
    @Test
    void rejectsMixedAndDuplicateOperands(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("input.scss");
        var first = directory.resolve("first.css");
        var second = directory.resolve("second.css");
        Files.writeString(source, "a { color: red; }");

        var mixedOutput = new StringWriter();
        assertEquals(
                64,
                commandLine("", mixedOutput, new StringWriter()).execute(
                        source.toString(),
                        source + ":" + first
                )
        );
        assertTrue(mixedOutput.toString().contains("may not both be used"));

        var duplicateOutput = new StringWriter();
        assertEquals(
                64,
                commandLine("", duplicateOutput, new StringWriter()).execute(
                        source + ":" + first,
                        source + ":" + second
                )
        );
        assertTrue(duplicateOutput.toString().contains("Duplicate source"));
        assertFalse(Files.exists(first));
        assertFalse(Files.exists(second));
    }

    /// Rejects stdin, explicit-output, and malformed mapping conflicts.
    @Test
    void rejectsMappingOptionConflicts(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("input.scss");
        var destination = directory.resolve("out.css");
        Files.writeString(source, "a { color: red; }");

        assertEquals(
                64,
                commandLine("", new StringWriter(), new StringWriter())
                        .execute(
                                "--stdin",
                                source + ":" + destination
                        )
        );
        assertEquals(
                64,
                commandLine("", new StringWriter(), new StringWriter())
                        .execute(
                                "-o",
                                destination.toString(),
                                source + ":" + destination
                        )
        );
        assertEquals(
                64,
                commandLine("", new StringWriter(), new StringWriter())
                        .execute(source + ":" + destination + ":again")
        );
        assertFalse(Files.exists(destination));
    }

    /// Reports unmatched option-like operands as usage errors.
    @Test
    void rejectsUnknownOptions() {
        var output = new StringWriter();

        assertEquals(
                64,
                commandLine("", output, new StringWriter())
                        .execute("--unknown-cli-option")
        );
        assertTrue(output.toString().contains("Unknown option"));
        assertTrue(output.toString().contains("Usage: scssfx"));
    }

    /// Writes usage failures to standard output and leaves standard error
    /// untouched for both semantic and parser-level argument errors.
    @Test
    void writesUsageFailuresToStandardOutput() {
        var missingInputOutput = new StringWriter();
        var missingInputError = new StringWriter();
        assertEquals(
                64,
                commandLine(
                        "",
                        missingInputOutput,
                        missingInputError
                ).execute()
        );
        assertTrue(missingInputOutput.toString().startsWith(
                "Compile Sass to CSS."
        ));
        assertTrue(missingInputOutput.toString().contains("Usage: scssfx"));
        assertEquals("", missingInputError.toString());

        var parserOutput = new StringWriter();
        var parserError = new StringWriter();
        assertEquals(
                64,
                commandLine("", parserOutput, parserError)
                        .execute("--style")
        );
        assertTrue(parserOutput.toString().contains(
                "Missing required parameter"
        ));
        assertTrue(parserOutput.toString().contains("Usage: scssfx"));
        assertEquals("", parserError.toString());
    }

    /// Reports a directory used as a positional source and suggests the
    /// source-to-destination mapping form.
    ///
    /// @param directory the isolated source parent
    @Test
    void suggestsDirectoryMappingSyntax(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("input");
        var destination = directory.resolve("output");
        Files.createDirectories(source);
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                64,
                commandLine("", output, error).execute(
                        source.toString(),
                        destination.toString()
                )
        );
        assertTrue(output.toString().contains(
                "Directory \"" + source + "\" may not be a positional arg."
        ));
        assertTrue(output.toString().contains(
                "use `scssfx " + source + ":" + destination + "`."
        ));
        assertTrue(output.toString().contains("Usage: scssfx"));
        assertEquals("", error.toString());
    }

    /// Reports every mapped IO failure in declaration order.
    ///
    /// @param directory the isolated output directory
    @Test
    void reportsAllMappedIoFailures(@TempDir Path directory) {
        var first = directory.resolve("first.scss");
        var second = directory.resolve("second.scss");
        var error = new StringWriter();

        assertEquals(
                66,
                commandLine("", new StringWriter(), error).execute(
                        first + ":" + directory.resolve("first.css"),
                        second + ":" + directory.resolve("second.css")
                )
        );
        var diagnostics = error.toString();
        var firstIndex = diagnostics.indexOf(first.toString());
        var secondIndex = diagnostics.indexOf(second.toString());
        assertTrue(firstIndex >= 0, diagnostics);
        assertTrue(secondIndex > firstIndex, diagnostics);
    }

    /// Reports every mapped Sass failure and publishes each default error
    /// stylesheet.
    ///
    /// @param directory the isolated source and output directory
    @Test
    void reportsAllMappedSassFailures(@TempDir Path directory)
            throws Exception {
        var first = directory.resolve("first.scss");
        var second = directory.resolve("second.scss");
        var firstOutput = directory.resolve("first.css");
        var secondOutput = directory.resolve("second.css");
        Files.writeString(first, "a { b: $first-missing; }");
        Files.writeString(second, "x { y: $second-missing; }");
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine("", new StringWriter(), error).execute(
                        first + ":" + firstOutput,
                        second + ":" + secondOutput
                )
        );
        var diagnostics = error.toString();
        var firstIndex = diagnostics.indexOf("$first-missing");
        var secondIndex = diagnostics.indexOf("$second-missing");
        assertTrue(firstIndex >= 0, diagnostics);
        assertTrue(secondIndex > firstIndex, diagnostics);
        assertTrue(Files.readString(firstOutput).contains("body::before"));
        assertTrue(Files.readString(secondOutput).contains("body::before"));
    }

    /// Uses the BSS extension for directory-derived binary destinations.
    @Test
    void mapsBssDirectoriesToBssFiles(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("input");
        var destination = directory.resolve("output");
        Files.createDirectories(source);
        Files.writeString(
                source.resolve("style.scss"),
                "Pane { -fx-opacity: 0.5; }"
        );

        assertEquals(
                0,
                commandLine("", new StringWriter(), new StringWriter())
                        .execute(
                                "--target",
                                "bss/javafx@17",
                                source + ":" + destination
                        )
        );
        var output = destination.resolve("style.bss");
        assertTrue(Files.exists(output));
        assertTrue(Files.size(output) > 2);
        assertFalse(Files.exists(destination.resolve("style.css")));
    }

    /// Creates a command line with isolated UTF-8 input and output streams.
    ///
    /// @param input standard-input text
    /// @param output standard-output buffer
    /// @param error standard-error buffer
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            StringWriter output,
            StringWriter error
    ) {
        return commandLine(
                input,
                Path.of("").toAbsolutePath(),
                output,
                error
        );
    }

    /// Creates a command line with an explicit working directory.
    ///
    /// @param input standard-input text
    /// @param workingDirectory base directory for relative stdin imports
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
