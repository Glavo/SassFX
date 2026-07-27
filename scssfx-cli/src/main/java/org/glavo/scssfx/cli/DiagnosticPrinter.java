// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassStackFrame;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Formats compiler diagnostics for command-line output.
///
/// The formatter is immutable. Compilation failures use their captured source
/// text without re-reading URLs. Other diagnostics may read UTF-8 file sources
/// to reconstruct complete source lines, but a read failure only reduces the
/// displayed context.
@NotNullByDefault
final class DiagnosticPrinter {
    /// Starts blue terminal text.
    private static final String BLUE = "\u001b[34m";

    /// Starts red terminal text.
    private static final String RED = "\u001b[31m";

    /// Starts bold terminal text.
    private static final String BOLD = "\u001b[1m";

    /// Starts bold yellow terminal text.
    private static final String BOLD_YELLOW = "\u001b[33m\u001b[1m";

    /// Restores default terminal styling.
    private static final String RESET = "\u001b[0m";

    /// Whether ANSI terminal styling is enabled.
    private final boolean color;

    /// Whether diagnostic frames use Unicode glyphs.
    private final boolean unicode;

    /// The base used to shorten displayed file paths.
    private final Path workingDirectory;

    /// The synthetic URL assigned to standard input.
    private final @Nullable URI inMemoryUrl;

    /// Source text associated with [#inMemoryUrl], or {@code null}.
    private final @Nullable String inMemoryContents;

    /// Creates a diagnostic formatter.
    ///
    /// @param color whether ANSI styling is enabled
    /// @param unicode whether diagnostic frames use Unicode glyphs
    /// @param workingDirectory the base used to shorten file paths
    /// @param inMemoryUrl an optional URL whose contents are supplied directly
    /// @param inMemoryContents source text for {@code inMemoryUrl}, or
    ///                         {@code null}
    DiagnosticPrinter(
            boolean color,
            boolean unicode,
            Path workingDirectory,
            @Nullable URI inMemoryUrl,
            @Nullable String inMemoryContents
    ) {
        this.color = color;
        this.unicode = unicode;
        this.workingDirectory = Objects.requireNonNull(
                workingDirectory,
                "workingDirectory"
        ).toAbsolutePath().normalize();
        this.inMemoryUrl = inMemoryUrl;
        this.inMemoryContents = inMemoryContents;
        if ((inMemoryUrl == null) != (inMemoryContents == null)) {
            throw new IllegalArgumentException(
                    "in-memory URL and contents must either both be present or both be absent"
            );
        }
    }

    /// Returns an equivalent formatter without terminal styling.
    ///
    /// @return a formatter retaining the current glyph and source settings
    DiagnosticPrinter withoutColor() {
        return color
                ? new DiagnosticPrinter(
                        false,
                        unicode,
                        workingDirectory,
                        inMemoryUrl,
                        inMemoryContents
                )
                : this;
    }

    /// Returns an equivalent formatter using ASCII frame glyphs.
    ///
    /// @return a formatter retaining the current color and source settings
    DiagnosticPrinter withAsciiGlyphs() {
        return unicode
                ? new DiagnosticPrinter(
                        color,
                        false,
                        workingDirectory,
                        inMemoryUrl,
                        inMemoryContents
                )
                : this;
    }

    /// Formats a compilation exception for standard error.
    ///
    /// @param failure the compilation failure
    /// @return the multi-line diagnostic text
    String format(SassCompilationException failure) {
        Objects.requireNonNull(failure, "failure");
        return formatError(
                failure.primaryDiagnostic(),
                failure.sassTrace(),
                failure.sourceContents()
        );
    }

    /// Formats one non-error diagnostic for standard error.
    ///
    /// Error diagnostics are also accepted and receive a synthetic root
    /// stylesheet frame.
    ///
    /// @param diagnostic the diagnostic to format
    /// @return the single- or multi-line diagnostic text
    String format(Diagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
            @Nullable SourceSpan span = diagnostic.span();
            return formatError(
                    diagnostic,
                    span == null
                            ? List.of()
                            : List.of(new SassStackFrame(
                                    "root stylesheet",
                                    span
                            )),
                    Map.of()
            );
        }

        @Nullable SourceSpan span = diagnostic.span();
        if (diagnostic.severity() == DiagnosticSeverity.DEBUG && span != null) {
            return displayUrl(span.url())
                    + ":" + (span.start().line() + 1)
                    + " " + styledDebugLabel() + ": "
                    + diagnostic.message();
        }

        var builder = new StringBuilder(styledWarningLabel(diagnostic))
                .append(": ")
                .append(diagnostic.message());
        if (span != null) {
            builder.append("\n\n");
            appendHighlight(builder, span);
        }
        return builder.toString();
    }

    /// Formats an interactive-line error with the caller's complete input.
    ///
    /// @param diagnostic the interactive error
    /// @param sourceLine the complete physical input line
    /// @return the error label and one source frame without a call trace
    String formatInteractiveError(
            Diagnostic diagnostic,
            String sourceLine
    ) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        Objects.requireNonNull(sourceLine, "sourceLine");
        if (diagnostic.severity() != DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException(
                    "interactive errors must have ERROR severity"
            );
        }
        var builder = new StringBuilder("Error: ")
                .append(diagnostic.message());
        @Nullable SourceSpan span = diagnostic.span();
        if (span != null) {
            builder.append('\n');
            appendHighlight(builder, span, sourceLine);
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    /// Formats an error diagnostic and its Sass call trace.
    ///
    /// @param diagnostic the primary error
    /// @param trace the Sass call trace from the failure site outward
    /// @param loadedSources source text captured by the failed compilation
    /// @return the complete error text
    private String formatError(
            Diagnostic diagnostic,
            List<SassStackFrame> trace,
            @Unmodifiable Map<URI, String> loadedSources
    ) {
        var builder = new StringBuilder("Error: ")
                .append(diagnostic.message());
        @Nullable SourceSpan span = diagnostic.span();
        if (span != null) {
            builder.append('\n');
            appendHighlight(
                    builder,
                    span,
                    sourceLine(span, loadedSources)
            );
        }
        if (!trace.isEmpty()) {
            appendTrace(builder, trace);
        } else if (span != null) {
            appendTrace(
                    builder,
                    List.of(new SassStackFrame("root stylesheet", span))
            );
        }
        return builder.toString();
    }

    /// Returns the warning label appropriate for one diagnostic.
    ///
    /// @param diagnostic the warning or deprecation
    /// @return a plain or ANSI-styled label
    private String styledWarningLabel(Diagnostic diagnostic) {
        var deprecation = diagnostic.severity()
                == DiagnosticSeverity.DEPRECATION;
        @Nullable String deprecationId = deprecation
                ? diagnostic.code()
                : null;
        if (!color) {
            return deprecation
                    ? "DEPRECATION WARNING"
                    + (deprecationId == null
                    ? ""
                    : " [" + deprecationId + "]")
                    : "WARNING";
        }
        var label = BOLD_YELLOW
                + (deprecation ? "Deprecation Warning" : "Warning")
                + RESET;
        return deprecationId == null
                ? label
                : label + " [" + BLUE + deprecationId + RESET + "]";
    }

    /// Returns the debug label for the current color mode.
    ///
    /// @return a plain or ANSI-styled label
    private String styledDebugLabel() {
        return color ? BOLD + "Debug" + RESET : "DEBUG";
    }

    /// Appends a source line and underline for one span.
    ///
    /// @param builder the destination
    /// @param span the source span
    private void appendHighlight(StringBuilder builder, SourceSpan span) {
        appendHighlight(builder, span, sourceLine(span, Map.of()));
    }

    /// Appends one supplied source line and underline for a span.
    ///
    /// @param builder the destination
    /// @param span the source span
    /// @param lineText the complete line containing the span
    private void appendHighlight(
            StringBuilder builder,
            SourceSpan span,
            String lineText
    ) {
        var lineNumber = span.start().line() + 1;
        var column = Math.max(0, span.start().column());
        var safeColumn = Math.min(column, lineText.length());
        var underlineLength = underlineLength(span, lineText, safeColumn);
        var width = Integer.toString(lineNumber).length();
        var blank = " ".repeat(width);

        appendGutter(builder, blank + (unicode ? " ╷" : " ,"));
        builder.append('\n');
        appendGutter(builder, lineNumber + (unicode ? " │ " : " | "));
        appendHighlightedLine(builder, lineText, safeColumn, span);
        builder.append('\n');
        appendGutter(builder, blank + (unicode ? " │ " : " | "));
        builder.append(" ".repeat(safeColumn));
        if (color) {
            builder.append(RED);
        }
        builder.append("^".repeat(underlineLength));
        if (color) {
            builder.append(RESET);
        }
        builder.append('\n');
        appendGutter(builder, blank + (unicode ? " ╵" : " '"));
        builder.append('\n');
    }

    /// Appends one source line with its failing range highlighted.
    ///
    /// @param builder the destination
    /// @param lineText the complete source line
    /// @param column the bounded start column
    /// @param span the highlighted span
    private void appendHighlightedLine(
            StringBuilder builder,
            String lineText,
            int column,
            SourceSpan span
    ) {
        if (!color) {
            builder.append(lineText);
            return;
        }
        var end = span.start().line() == span.end().line()
                ? Math.min(lineText.length(), Math.max(column, span.end().column()))
                : lineText.length();
        builder.append(lineText, 0, column)
                .append(RED)
                .append(lineText, column, end)
                .append(RESET)
                .append(lineText, end, lineText.length());
    }

    /// Appends gutter text using the configured terminal style.
    ///
    /// @param builder the destination
    /// @param text the gutter text
    private void appendGutter(StringBuilder builder, String text) {
        if (color) {
            builder.append(BLUE);
        }
        builder.append(text);
        if (color) {
            builder.append(RESET);
        }
    }

    /// Returns the number of carets used for one displayed source line.
    ///
    /// @param span the highlighted source span
    /// @param lineText the complete displayed line
    /// @param column the bounded start column
    /// @return a positive underline length
    private static int underlineLength(
            SourceSpan span,
            String lineText,
            int column
    ) {
        if (span.start().line() == span.end().line()) {
            return Math.max(
                    1,
                    Math.min(
                            lineText.length() - column,
                            span.end().column() - span.start().column()
                    )
            );
        }
        return Math.max(1, lineText.length() - column);
    }

    /// Appends Sass call-trace locations.
    ///
    /// @param builder the destination
    /// @param trace frames ordered from the failure site outward
    private void appendTrace(
            StringBuilder builder,
            List<SassStackFrame> trace
    ) {
        for (var frame : trace) {
            var span = frame.span();
            builder.append("  ")
                    .append(displayUrl(span.url()))
                    .append(' ')
                    .append(span.start().line() + 1)
                    .append(':')
                    .append(span.start().column() + 1)
                    .append("  ")
                    .append(frame.member())
                    .append('\n');
        }
        builder.setLength(builder.length() - 1);
    }

    /// Returns the complete source line containing one span.
    ///
    /// @param span the source span
    /// @param loadedSources source text captured by a failed compilation
    /// @return a source line or a best-effort synthetic line
    private String sourceLine(
            SourceSpan span,
            @Unmodifiable Map<URI, String> loadedSources
    ) {
        if (span.url() != null) {
            @Nullable var loaded = loadedSources.get(span.url());
            if (loaded != null) {
                @Nullable var line = lineAt(
                        loaded,
                        span.start().line()
                );
                if (line != null) {
                    return line;
                }
            }
        }
        @Nullable String contents = sourceContents(span.url());
        if (contents != null) {
            @Nullable String line = lineAt(contents, span.start().line());
            if (line != null) {
                return line;
            }
        }

        var firstLine = span.text();
        var newline = firstLine.indexOf('\n');
        if (newline >= 0) {
            firstLine = firstLine.substring(0, newline);
        }
        return " ".repeat(Math.max(0, span.start().column())) + firstLine;
    }

    /// Returns source text for a diagnostic URL when it is locally available.
    ///
    /// @param url the source URL, or {@code null}
    /// @return UTF-8 source text, or {@code null} when unavailable
    private @Nullable String sourceContents(@Nullable URI url) {
        if (url == null) {
            return null;
        }
        if (url.equals(inMemoryUrl)) {
            return inMemoryContents;
        }
        if (!"file".equalsIgnoreCase(url.getScheme())) {
            return null;
        }
        try {
            return Files.readString(Path.of(url), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    /// Returns one zero-based line from source text.
    ///
    /// @param contents the complete source text
    /// @param targetLine the zero-based line index
    /// @return the line without its terminator, or {@code null}
    private static @Nullable String lineAt(
            String contents,
            int targetLine
    ) {
        var line = 0;
        var start = 0;
        for (var index = 0; index < contents.length(); index++) {
            if (contents.charAt(index) != '\n') {
                continue;
            }
            if (line == targetLine) {
                var end = index > start && contents.charAt(index - 1) == '\r'
                        ? index - 1
                        : index;
                return contents.substring(start, end);
            }
            line++;
            start = index + 1;
        }
        if (line == targetLine) {
            var end = contents.endsWith("\r")
                    ? contents.length() - 1
                    : contents.length();
            return contents.substring(start, end);
        }
        return null;
    }

    /// Returns a concise display name for a source URL.
    ///
    /// @param url the source URL, or {@code null}
    /// @return a relative file path, URL, or {@code -}
    private String displayUrl(@Nullable URI url) {
        if (url == null) {
            return "-";
        }
        if (!"file".equalsIgnoreCase(url.getScheme())) {
            return url.toString();
        }
        try {
            var path = Path.of(url).toAbsolutePath().normalize();
            if (path.getRoot() != null
                    && path.getRoot().equals(workingDirectory.getRoot())) {
                var relative = workingDirectory.relativize(path);
                if (!relative.startsWith("..")) {
                    return relative.toString();
                }
            }
            return path.toString();
        } catch (IllegalArgumentException ignored) {
            return url.toString();
        }
    }
}
