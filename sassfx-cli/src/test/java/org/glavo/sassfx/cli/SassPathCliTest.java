// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Dart Sass-compatible `SASS_PATH` command-line behavior.
@NotNullByDefault
final class SassPathCliTest {
    /// Searches all environment paths in their declared order.
    @Test
    void searchesEnvironmentPathsInOrder(@TempDir Path directory)
            throws Exception {
        var first = Files.createDirectory(directory.resolve("first"));
        var second = Files.createDirectory(directory.resolve("second"));
        Files.writeString(first.resolve("_shared.scss"), "$value: first;");
        Files.writeString(first.resolve("_one.scss"), ".one {value: one}");
        Files.writeString(second.resolve("_shared.scss"), "$value: second;");
        Files.writeString(second.resolve("_two.scss"), ".two {value: two}");
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        directory,
                        first + File.pathSeparator + second,
                        """
                                @use "shared";
                                @use "one";
                                @use "two";
                                .result {value: shared.$value}
                                """,
                        output,
                        error
                ).execute("-")
        );

        var css = output.toString();
        assertTrue(css.contains(".one"), css);
        assertTrue(css.contains(".two"), css);
        assertTrue(css.contains("value: first"), css);
        assertEquals("", error.toString());
    }

    /// Gives explicit command-line paths precedence over environment paths.
    @Test
    void prefersExplicitLoadPaths(@TempDir Path directory) throws Exception {
        var explicit = Files.createDirectory(directory.resolve("explicit"));
        var environment = Files.createDirectory(
                directory.resolve("environment")
        );
        Files.writeString(explicit.resolve("_theme.scss"), "$value: explicit;");
        Files.writeString(
                environment.resolve("_theme.scss"),
                "$value: environment;"
        );
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        directory,
                        environment.toString(),
                        "@use \"theme\"; .result {value: theme.$value}",
                        output,
                        new StringWriter()
                ).execute("--load-path", explicit.toString(), "-")
        );
        assertTrue(output.toString().contains("value: explicit"));
        assertFalse(output.toString().contains("value: environment"));
    }

    /// Resolves relative and empty entries from the invocation directory.
    @Test
    void resolvesRelativeAndEmptyEntries(@TempDir Path directory)
            throws Exception {
        var dependencies = Files.createDirectory(directory.resolve("deps"));
        Files.writeString(
                directory.resolve("_local.scss"),
                ".local {value: local}"
        );
        Files.writeString(
                dependencies.resolve("_dependency.scss"),
                ".dependency {value: dependency}"
        );
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        directory,
                        File.pathSeparator + "deps",
                        "@use \"local\"; @use \"dependency\";",
                        output,
                        new StringWriter()
                ).execute("-")
        );
        assertTrue(output.toString().contains(".local"));
        assertTrue(output.toString().contains(".dependency"));
    }

    /// Makes environment load paths available to the interactive shell.
    @Test
    void supportsInteractiveImports(@TempDir Path directory) throws Exception {
        var dependencies = Files.createDirectory(directory.resolve("deps"));
        Files.writeString(
                dependencies.resolve("_theme.scss"),
                "$size: 3px;"
        );
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        directory,
                        "deps",
                        "@use \"theme\" as *\n$size\n",
                        output,
                        error
                ).execute("--interactive")
        );
        assertTrue(normalize(output.toString()).endsWith(">> $size\n3px\n"));
        assertEquals("", error.toString());
    }

    /// Classifies environment imports as dependencies for `--quiet-deps`.
    @Test
    void quietDepsSuppressesEnvironmentDependencyWarnings(
            @TempDir Path directory
    ) throws Exception {
        var dependencies = Files.createDirectory(directory.resolve("deps"));
        Files.writeString(
                dependencies.resolve("_dependency.scss"),
                "#{blue} {value: dependency}"
        );
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        directory,
                        "deps",
                        "@use \"dependency\";",
                        new StringWriter(),
                        error
                ).execute("--quiet-deps", "-")
        );
        assertEquals("", error.toString());
    }

    /// Creates a command line with an injected `SASS_PATH` value.
    ///
    /// @param directory the invocation working directory
    /// @param sassPath the raw environment value, or `null` when absent
    /// @param input UTF-8 standard input
    /// @param output standard output
    /// @param error standard error
    /// @return the configured command line
    private static CommandLine commandLine(
            Path directory,
            @Nullable String sassPath,
            String input,
            StringWriter output,
            StringWriter error
    ) {
        return SassFXMain.configure(new CommandLine(new SassFXMain(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                directory,
                sassPath
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }

    /// Normalizes platform line separators for assertions.
    ///
    /// @param value text produced by the command line
    /// @return text using line-feed separators only
    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
