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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies deprecation controls across immediate, watch, and interactive
/// command-line execution.
@NotNullByDefault
final class DeprecationModesCliTest {
    /// Reports option conflicts once per invocation in every execution mode.
    @Test
    void reportsConfigurationWarningsOncePerInvocation(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { value: initial; }");
        var message = "Ignoring setting to silence elseif deprecation";

        var immediateError = new StringWriter();
        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), immediateError)
                        .execute(
                                "--fatal-deprecation=elseif",
                                "--silence-deprecation=elseif",
                                source.toString()
                        )
        );
        assertEquals(1, countOccurrences(immediateError.toString(), message));

        var interactiveError = new StringWriter();
        assertEquals(
                0,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        interactiveError
                ).execute(
                        "--interactive",
                        "--fatal-deprecation=elseif",
                        "--silence-deprecation=elseif"
                )
        );
        assertEquals(1, countOccurrences(interactiveError.toString(), message));

        var watchOutput = new StringWriter();
        var watchError = new StringWriter();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    directory,
                    watchOutput,
                    watchError
            ).execute(
                    "--watch",
                    "--poll",
                    "--fatal-deprecation=elseif",
                    "--silence-deprecation=elseif",
                    source + ":" + destination
            ));

            awaitTextContains(watchOutput, "Sass is watching");
            awaitTextContains(watchError, message);
            Files.writeString(source, "a { value: changed; }");
            awaitFileContains(destination, "value: changed;");
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(
                    1,
                    countOccurrences(watchError.toString(), message),
                    watchError.toString()
            );
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Rejects unknown identifiers before watch or interactive execution
    /// begins.
    @Test
    void rejectsUnknownIdentifiersAcrossModes(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("style.scss");
        Files.writeString(source, "");
        for (var invocation : new String[][]{
                {
                        "--watch",
                        "--poll",
                        "--silence-deprecation=unknown",
                        source + ":" + directory.resolve("style.css")
                },
                {
                        "--interactive",
                        "--fatal-deprecation=unknown"
                },
                {
                        "--interactive",
                        "--future-deprecation=unknown"
                }
        }) {
            var output = new StringWriter();
            assertEquals(
                    64,
                    commandLine("", directory, output, new StringWriter())
                            .execute(invocation)
            );
            assertTrue(output.toString().contains(
                    "Invalid deprecation \"unknown\"."
            ));
        }
    }

    /// Silences parser deprecations in immediate, watch, and interactive
    /// execution.
    @Test
    void silencesParserDeprecationsAcrossModes(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "@if true {} @elseif false {}");

        var immediateError = new StringWriter();
        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), immediateError)
                        .execute(
                                "--silence-deprecation=elseif",
                                source.toString()
                        )
        );
        assertEquals("", immediateError.toString());

        var interactiveOutput = new StringWriter();
        var interactiveError = new StringWriter();
        assertEquals(
                0,
                commandLine(
                        "4 -(5)\n",
                        directory,
                        interactiveOutput,
                        interactiveError
                ).execute(
                        "--interactive",
                        "--silence-deprecation=strict-unary"
                )
        );
        assertTrue(normalize(interactiveOutput.toString()).endsWith(
                ">> 4 -(5)\n-1\n"
        ));
        assertEquals("", interactiveError.toString());

        var watchOutput = new StringWriter();
        var watchError = new StringWriter();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    directory,
                    watchOutput,
                    watchError
            ).execute(
                    "--watch",
                    "--poll",
                    "--silence-deprecation=elseif",
                    source + ":" + destination
            ));
            awaitTextContains(watchOutput, "Sass is watching");
            assertEquals("", watchError.toString());
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Makes parser deprecations fatal while preserving watch and interactive
    /// recovery.
    @Test
    void recoversAfterFatalParserDeprecations(@TempDir Path directory)
            throws Exception {
        var interactiveOutput = new StringWriter();
        assertEquals(
                0,
                commandLine(
                        "4 -(5)\n1\n",
                        directory,
                        interactiveOutput,
                        new StringWriter()
                ).execute(
                        "--interactive",
                        "--fatal-deprecation=strict-unary",
                        "--no-color",
                        "--no-unicode"
                )
        );
        var interactive = normalize(interactiveOutput.toString());
        assertTrue(interactive.contains(
                "strict-unary deprecation to be fatal"
        ));
        assertTrue(interactive.endsWith(">> 1\n1\n"));
        assertFalse(interactive.contains(">> 4 -(5)\n-1\n"));

        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "@if true {} @elseif false {}");
        var watchOutput = new StringWriter();
        var watchError = new StringWriter();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    directory,
                    watchOutput,
                    watchError
            ).execute(
                    "--watch",
                    "--poll",
                    "--fatal-deprecation=elseif",
                    source + ":" + destination
            ));
            awaitTextContains(watchError, "elseif deprecation to be fatal");
            awaitTextContains(watchOutput, "Sass is watching");
            Files.writeString(source, "a { value: recovered; }");
            awaitFileContains(destination, "value: recovered;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Applies evaluation-time function-unit deprecations in all execution
    /// modes.
    @Test
    void controlsEvaluationDeprecationsAcrossModes(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'sass:math'; a { value: math.random(1px); }"
        );

        var immediateError = new StringWriter();
        assertEquals(
                0,
                commandLine("", directory, new StringWriter(), immediateError)
                        .execute(
                                "--silence-deprecation=function-units",
                                source.toString()
                        )
        );
        assertEquals("", immediateError.toString());

        var interactiveOutput = new StringWriter();
        var interactiveError = new StringWriter();
        assertEquals(
                0,
                commandLine(
                        "@use 'sass:math'\nmath.random(1px)\n1\n",
                        directory,
                        interactiveOutput,
                        interactiveError
                ).execute(
                        "--interactive",
                        "--fatal-deprecation=function-units",
                        "--no-color",
                        "--no-unicode"
                )
        );
        var interactive = normalize(interactiveOutput.toString());
        assertTrue(interactive.contains(
                "function-units deprecation to be fatal"
        ));
        assertTrue(interactive.endsWith(">> 1\n1\n"));
        assertEquals("", interactiveError.toString());

        var watchOutput = new StringWriter();
        var watchError = new StringWriter();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    directory,
                    watchOutput,
                    watchError
            ).execute(
                    "--watch",
                    "--poll",
                    "--fatal-deprecation=function-units",
                    source + ":" + destination
            ));
            awaitTextContains(
                    watchError,
                    "function-units deprecation to be fatal"
            );
            awaitTextContains(watchOutput, "Sass is watching");
            Files.writeString(source, "a { value: recovered; }");
            awaitFileContains(destination, "value: recovered;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Creates an isolated command line with injected standard input.
    ///
    /// @param input UTF-8 standard input
    /// @param directory invocation working directory
    /// @param output standard output
    /// @param error standard error
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            Path directory,
            StringWriter output,
            StringWriter error
    ) {
        return SassFXMain.configure(new CommandLine(new SassFXMain(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                directory,
                null
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }

    /// Waits for text to appear in a captured writer.
    ///
    /// @param writer the concurrently written text
    /// @param expected the required fragment
    /// @throws Exception if waiting is interrupted
    private static void awaitTextContains(
            StringWriter writer,
            String expected
    ) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (writer.toString().contains(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(writer.toString().contains(expected), writer.toString());
    }

    /// Waits for text to appear in a file.
    ///
    /// @param path the output file
    /// @param expected the required fragment
    /// @throws Exception if waiting or file access fails
    private static void awaitFileContains(Path path, String expected)
            throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(path)
                    && Files.readString(path).contains(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(
                Files.isRegularFile(path)
                        && Files.readString(path).contains(expected),
                "Expected " + path + " to contain " + expected
        );
    }

    /// Counts non-overlapping substring occurrences.
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

    /// Normalizes platform line separators.
    ///
    /// @param value text to normalize
    /// @return text using line feeds
    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
