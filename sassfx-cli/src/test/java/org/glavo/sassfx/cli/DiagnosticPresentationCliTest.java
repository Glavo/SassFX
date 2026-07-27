// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassStackFrame;
import org.glavo.sassfx.SourceLocation;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies command-line color, glyph, source-context, and trace policies.
@NotNullByDefault
final class DiagnosticPresentationCliTest {
    /// Uses Unicode diagnostic frames by default and displays complete lines.
    @Test
    void usesUnicodeFramesAndCompleteSourceLinesByDefault(
            @TempDir Path directory
    ) throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "a { color: $missing; }");
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), error)
                        .execute(input.toString())
        );

        var text = error.toString();
        assertTrue(text.contains("Error: Undefined variable."));
        assertTrue(text.contains("  ╷"));
        assertTrue(text.contains("1 │ a { color: $missing; }"));
        assertTrue(text.contains("  │            ^^^^^^^^"));
        assertTrue(text.contains("  ╵"));
        assertTrue(text.contains("input.scss 1:12  root stylesheet"));
        assertFalse(text.contains("\u001b["));
    }

    /// Replaces frame glyphs with the Dart Sass ASCII equivalents.
    @Test
    void selectsAsciiOrUnicodeFramesExplicitly(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "a { color: $missing; }");
        var asciiError = new StringWriter();
        var unicodeError = new StringWriter();

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), asciiError)
                        .execute("--no-unicode", input.toString())
        );
        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), unicodeError)
                        .execute("--unicode", input.toString())
        );

        assertTrue(asciiError.toString().contains("  ,"));
        assertTrue(asciiError.toString().contains("1 | a { color: $missing; }"));
        assertTrue(asciiError.toString().contains("  '"));
        assertFalse(asciiError.toString().contains("│"));
        assertTrue(unicodeError.toString().contains("1 │ a { color: $missing; }"));
    }

    /// Applies ANSI styling only when explicitly enabled for buffered output.
    @Test
    void forcesColorOnAndOff(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "a { color: $missing; }");
        var coloredError = new StringWriter();
        var plainError = new StringWriter();

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), coloredError)
                        .execute("--color", input.toString())
        );
        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), plainError)
                        .execute("--no-color", input.toString())
        );

        assertTrue(coloredError.toString().contains("\u001b[34m"));
        assertTrue(coloredError.toString().contains("\u001b[31m"));
        assertTrue(coloredError.toString().contains("\u001b[0m"));
        assertFalse(plainError.toString().contains("\u001b["));
    }

    /// Styles warning and debug labels while retaining their stderr stream.
    @Test
    void colorsWarningsAndDebugMessages(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(
                input,
                """
                        @debug hello;
                        @warn warning;
                        a { color: red; }
                        """
        );
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine("", directory, output, error)
                        .execute("--color", input.toString())
        );

        assertTrue(output.toString().contains("color: red"));
        assertTrue(error.toString().contains("\u001b[1mDebug\u001b[0m"));
        assertTrue(error.toString().contains(
                "\u001b[33m\u001b[1mWarning\u001b[0m"
        ));
    }

    /// Appends Java frames only when trace output is enabled for Sass errors.
    @Test
    void controlsSassImplementationTraces(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "a { color: $missing; }");
        var plainError = new StringWriter();
        var tracedError = new StringWriter();
        var disabledError = new StringWriter();

        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), plainError)
                        .execute(input.toString())
        );
        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), tracedError)
                        .execute("--trace", input.toString())
        );
        assertEquals(
                65,
                commandLine("", directory, new StringWriter(), disabledError)
                        .execute("--no-trace", input.toString())
        );

        assertFalse(plainError.toString().contains("\tat "));
        var normalizedTrace = normalizeNewlines(tracedError.toString());
        assertTrue(
                normalizedTrace.contains("\n\n\tat "),
                normalizedTrace
        );
        assertTrue(tracedError.toString().contains(".java:"));
        assertFalse(disabledError.toString().contains("\tat "));
    }

    /// Appends traces to IO failures without changing their exit status.
    @Test
    void tracesIoFailures(@TempDir Path directory) {
        var missing = directory.resolve("missing.scss");
        var error = new StringWriter();

        assertEquals(
                66,
                commandLine("", directory, new StringWriter(), error)
                        .execute("--trace", missing.toString())
        );

        assertTrue(error.toString().startsWith("sassfx:"));
        var normalizedTrace = normalizeNewlines(error.toString());
        assertTrue(
                normalizedTrace.contains("\n\n\tat "),
                normalizedTrace
        );
        assertTrue(error.toString().contains(".java:"));
    }

    /// Does not attach implementation traces to command-line usage errors.
    @Test
    void leavesUsageErrorsUntraced(@TempDir Path directory) {
        var error = new StringWriter();

        assertEquals(
                64,
                commandLine("", directory, new StringWriter(), error)
                        .execute("--trace", "--style", "dense", "input.scss")
        );

        assertFalse(error.toString().contains("\tat "));
    }

    /// Uses stdin contents when rendering a source excerpt.
    @Test
    void rendersStandardInputSourceContext(@TempDir Path directory) {
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine(
                        "a { color: $missing; }",
                        directory,
                        new StringWriter(),
                        error
                ).execute("--stdin")
        );

        assertTrue(error.toString().contains(
                "1 │ a { color: $missing; }"
        ));
        assertTrue(error.toString().contains("stdin 1:12  root stylesheet"));
    }

    /// Renders importer source captured by the failure without reading its URL.
    @Test
    void rendersCapturedVirtualSourceContext(@TempDir Path directory) {
        var url = URI.create("virtual:dependency");
        var contents = "a { width: 1px + 1em; }\n";
        var span = new SourceSpan(
                url,
                new SourceLocation(0, 11, 11),
                new SourceLocation(0, 20, 20),
                "1px + 1em"
        );
        var failure = new SassCompilationException(
                List.of(new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        "1px and 1em have incompatible units.",
                        span,
                        null
                )),
                List.of(new SassStackFrame("root stylesheet", span)),
                Set.of(url),
                Map.of(url, contents),
                null
        );
        var printer = new DiagnosticPrinter(
                false,
                true,
                directory,
                null,
                null
        );

        var text = printer.format(failure);

        assertTrue(text.contains("1 │ a { width: 1px + 1em; }"), text);
        assertTrue(
                text.contains("virtual:dependency 1:12  root stylesheet"),
                text
        );
    }

    /// Keeps error stylesheets free of ANSI escapes and uses ASCII comments.
    @Test
    void keepsErrorCssTerminalIndependent(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.scss");
        var output = directory.resolve("output.css");
        Files.writeString(input, "a { color: $missing; }");

        assertEquals(
                65,
                commandLine(
                        "",
                        directory,
                        new StringWriter(),
                        new StringWriter()
                ).execute(
                        "--color",
                        "--unicode",
                        input.toString(),
                        output.toString()
                )
        );

        var css = Files.readString(output);
        assertFalse(css.contains("\u001b["));
        assertTrue(css.contains(" *   ,"));
        assertTrue(css.contains("\\2577 "));
        assertTrue(css.contains("\\2502 "));
        assertTrue(css.contains("\\2575 "));
    }

    /// Advertises every diagnostic presentation option in command help.
    @Test
    void documentsPresentationOptionsInHelp(@TempDir Path directory) {
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine("", directory, output, new StringWriter())
                        .execute("--help")
        );

        var help = output.toString();
        assertTrue(help.contains("--[no-]color"));
        assertTrue(help.contains("--[no-]unicode"));
        assertTrue(help.contains("--[no-]trace"));
    }

    /// Always traces unexpected implementation failures and returns status 255.
    @Test
    void reportsUnexpectedFailuresWithTrace(@TempDir Path directory) {
        var error = new StringWriter();
        var commandLine = SassFXMain.configure(new CommandLine(new SassFXMain(
                new UnexpectedFailureInputStream(),
                directory
        )))
                .setOut(new PrintWriter(new StringWriter(), true))
                .setErr(new PrintWriter(error, true));

        assertEquals(
                255,
                commandLine.execute("--color", "--stdin")
        );

        var normalized = normalizeNewlines(error.toString());
        assertTrue(normalized.contains(
                "\u001b[31m\u001b[1mUnexpected exception:\u001b[0m"
        ));
        assertTrue(normalized.contains("synthetic input failure"));
        assertTrue(normalized.contains("\n\n\tat "));
    }

    /// Creates a command line using isolated streams and working directory.
    ///
    /// @param input the UTF-8 standard-input contents
    /// @param directory the injected working directory
    /// @param output the standard-output buffer
    /// @param error the standard-error buffer
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            Path directory,
            StringWriter output,
            StringWriter error
    ) {
        return SassFXMain.configure(new CommandLine(new SassFXMain(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                directory
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }

    /// Normalizes platform line separators for structural assertions.
    ///
    /// @param value the text to normalize
    /// @return text containing only line-feed separators
    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    /// Throws an unexpected runtime failure whenever stdin is read.
    private static final class UnexpectedFailureInputStream
            extends InputStream {
        /// Creates the synthetic failing input.
        private UnexpectedFailureInputStream() {
        }

        /// Throws the synthetic failure.
        ///
        /// @return this method does not return normally
        /// @throws IOException never
        @Override
        public int read() throws IOException {
            throw new IllegalStateException("synthetic input failure");
        }
    }
}
