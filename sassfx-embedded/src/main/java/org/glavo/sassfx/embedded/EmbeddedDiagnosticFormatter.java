// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import com.sass_lang.embedded_protocol.LogEventType;
import com.sass_lang.embedded_protocol.OutboundMessage;
import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassFileSource;
import org.glavo.sassfx.SassLogEvent;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.SassStackFrame;
import org.glavo.sassfx.SassStringSource;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Encodes compiler diagnostics in Embedded Sass protocol representations.
///
/// This formatter owns both structured protocol spans and the human-readable
/// terminal text carried by compile failures and log events.
@NotNullByDefault
final class EmbeddedDiagnosticFormatter {
    /// Prevents instantiation.
    private EmbeddedDiagnosticFormatter() {
    }

    /// Converts one Sass compilation failure to its terminal protocol payload.
    ///
    /// @param failure the compiler failure
    /// @param source the compilation root, or `null` if source creation failed
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return the complete compile-failure payload
    static OutboundMessage.CompileResponse.CompileFailure compileFailure(
            SassCompilationException failure,
            @Nullable SassSource source,
            boolean color,
            boolean ascii
    ) {
        var primary = failure.primaryDiagnostic();
        var builder = OutboundMessage.CompileResponse.CompileFailure.newBuilder()
                .setMessage(primary.message())
                .setFormatted(formatDiagnostic(
                        primary,
                        failure.sassTrace(),
                        source,
                        failure.sourceContents(),
                        color,
                        ascii
                ));
        if (primary.span() != null) {
            builder.setSpan(span(
                    primary.span(),
                    source,
                    failure.sourceContents()
            ));
        }
        if (!failure.sassTrace().isEmpty()) {
            builder.setStackTrace(stackTrace(failure.sassTrace()));
        }
        return builder.build();
    }

    /// Creates a root-source IO failure.
    ///
    /// File-backed failures expose the requested file URL and Dart
    /// Sass-compatible display message. Other failures retain their supplied
    /// message without a synthetic span.
    ///
    /// @param message the failure message
    /// @param source the source selected before failure, or `null`
    /// @return the complete compile-failure payload
    static OutboundMessage.CompileResponse.CompileFailure ioFailure(
            String message,
            @Nullable SassSource source
    ) {
        var displayMessage = source instanceof SassFileSource fileSource
                ? "Cannot open file: " + fileSource.path()
                : message;
        var builder = OutboundMessage.CompileResponse.CompileFailure.newBuilder()
                .setMessage(displayMessage);
        if (source instanceof SassFileSource fileSource) {
            builder.setSpan(
                    com.sass_lang.embedded_protocol.SourceSpan.newBuilder()
                            .setUrl(fileSource.path().toUri().toString())
                            .setStart(
                                    com.sass_lang.embedded_protocol.SourceSpan
                                            .SourceLocation.newBuilder()
                            )
                            .setEnd(
                                    com.sass_lang.embedded_protocol.SourceSpan
                                            .SourceLocation.newBuilder()
                            )
            );
        }
        return builder.build();
    }

    /// Creates a compiler-configuration failure with a zero-valued span.
    ///
    /// @param message the configuration failure message
    /// @param color whether ANSI terminal colors are enabled
    /// @return the complete compile-failure payload
    static OutboundMessage.CompileResponse.CompileFailure configurationFailure(
            String message,
            boolean color
    ) {
        var diagnostic = new Diagnostic(
                DiagnosticSeverity.ERROR,
                message,
                null,
                null
        );
        return OutboundMessage.CompileResponse.CompileFailure.newBuilder()
                .setMessage(message)
                .setFormatted(formatDiagnostic(
                        diagnostic,
                        List.of(),
                        null,
                        Map.of(),
                        color,
                        false
                ))
                .setSpan(
                        com.sass_lang.embedded_protocol.SourceSpan.newBuilder()
                                .setStart(
                                        com.sass_lang.embedded_protocol
                                                .SourceSpan.SourceLocation
                                                .newBuilder()
                                )
                                .setEnd(
                                        com.sass_lang.embedded_protocol
                                                .SourceSpan.SourceLocation
                                                .newBuilder()
                                )
                )
                .build();
    }

    /// Converts one compiler log event to an outbound protocol wrapper.
    ///
    /// @param event the compiler log event
    /// @param source the compilation root
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return the outbound log-event wrapper
    /// @throws IllegalArgumentException if the event contains an error
    ///                                  diagnostic
    static OutboundMessage logEvent(
            SassLogEvent event,
            SassSource source,
            boolean color,
            boolean ascii
    ) {
        var diagnostic = event.diagnostic();
        var builder = OutboundMessage.LogEvent.newBuilder()
                .setType(switch (diagnostic.severity()) {
                    case WARNING -> LogEventType.WARNING;
                    case DEPRECATION -> LogEventType.DEPRECATION_WARNING;
                    case DEBUG -> LogEventType.DEBUG;
                    case ERROR -> throw new IllegalArgumentException(
                            "error diagnostics cannot be logger events"
                    );
                })
                .setMessage(diagnostic.message())
                .setFormatted(formatDiagnostic(
                        diagnostic,
                        event.sassTrace(),
                        source,
                        Map.of(),
                        color,
                        ascii
                ))
                .setStackTrace(stackTrace(event.sassTrace()));
        if (diagnostic.span() != null) {
            builder.setSpan(span(
                    diagnostic.span(),
                    source,
                    Map.of()
            ));
        }
        if (event.deprecation() != null) {
            builder.setDeprecationType(event.deprecation().id());
        }
        return OutboundMessage.newBuilder().setLogEvent(builder).build();
    }

    /// Converts a compiler source span to the protocol representation.
    ///
    /// @param sourceSpan the compiler span
    /// @param source the compilation root, or `null`
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return the protocol span
    private static com.sass_lang.embedded_protocol.SourceSpan span(
            SourceSpan sourceSpan,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        var start = com.sass_lang.embedded_protocol.SourceSpan.SourceLocation
                .newBuilder()
                .setOffset(sourceSpan.start().offset())
                .setLine(sourceSpan.start().line())
                .setColumn(sourceSpan.start().column());
        var end = com.sass_lang.embedded_protocol.SourceSpan.SourceLocation
                .newBuilder()
                .setOffset(sourceSpan.end().offset())
                .setLine(sourceSpan.end().line())
                .setColumn(sourceSpan.end().column());
        return com.sass_lang.embedded_protocol.SourceSpan.newBuilder()
                .setText(sourceSpan.text())
                .setStart(start)
                .setEnd(end)
                .setUrl(sourceSpan.url() == null
                        ? ""
                        : sourceSpan.url().toString())
                .setContext(spanContext(
                        sourceSpan,
                        source,
                        sourceContents
                ))
                .build();
    }

    /// Formats one diagnostic for the protocol's human-readable field.
    ///
    /// @param diagnostic the compiler diagnostic
    /// @param frames Sass call frames from the diagnostic site outward
    /// @param source the compilation root, or `null`
    /// @param sourceContents loaded text keyed by canonical URL
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return a stable terminal representation
    private static String formatDiagnostic(
            Diagnostic diagnostic,
            @Unmodifiable List<SassStackFrame> frames,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents,
            boolean color,
            boolean ascii
    ) {
        if (diagnostic.severity() == DiagnosticSeverity.DEBUG) {
            var location = diagnostic.span() == null
                    ? "-"
                    : location(diagnostic.span());
            var label = color
                    ? "\u001b[1mDebug\u001b[0m"
                    : "DEBUG";
            return location + " " + label + ": "
                    + diagnostic.message() + "\n";
        }

        var label = switch (diagnostic.severity()) {
            case ERROR -> "Error";
            case WARNING -> color
                    ? "\u001b[33m\u001b[1mWarning\u001b[0m"
                    : "WARNING";
            case DEPRECATION -> color
                    ? "\u001b[33m\u001b[1mDeprecation Warning\u001b[0m"
                    : "DEPRECATION WARNING";
            case DEBUG -> throw new AssertionError();
        };
        var result = new StringBuilder()
                .append(label)
                .append(": ")
                .append(diagnostic.message());
        if (diagnostic.span() != null) {
            result.append(diagnostic.severity() == DiagnosticSeverity.ERROR
                            ? "\n"
                            : "\n\n")
                    .append(formatSpan(
                            diagnostic.span(),
                            source,
                            sourceContents,
                            color,
                            ascii
                    ));
        }
        var trace = stackTrace(frames);
        if (!trace.isEmpty()) {
            var indentation = diagnostic.severity() == DiagnosticSeverity.ERROR
                    ? "  "
                    : "    ";
            for (var traceLine : trace.stripTrailing().split("\n")) {
                result.append('\n')
                        .append(indentation)
                        .append(traceLine);
            }
        }
        if (diagnostic.severity() != DiagnosticSeverity.ERROR) {
            result.append('\n');
        }
        return result.toString();
    }

    /// Formats a single-line source excerpt with Sass terminal glyphs.
    ///
    /// @param span the highlighted source span
    /// @param source the compilation root, or `null`
    /// @param sourceContents loaded text keyed by canonical URL
    /// @param color whether ANSI terminal colors are enabled
    /// @param ascii whether ASCII frame glyphs are selected
    /// @return the formatted source frame
    private static String formatSpan(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents,
            boolean color,
            boolean ascii
    ) {
        var lineNumber = Integer.toString(span.start().line() + 1);
        var width = lineNumber.length();
        var line = sourceLine(span, source, sourceContents);
        var startColumn = Math.min(span.start().column(), line.length());
        var highlightedLength = Math.max(
                1,
                span.start().line() == span.end().line()
                        ? span.end().column() - span.start().column()
                        : Math.max(1, line.length() - startColumn)
        );
        highlightedLength = Math.min(
                highlightedLength,
                Math.max(1, line.length() - startColumn)
        );
        var top = ascii ? "," : "╷";
        var bar = ascii ? "|" : "│";
        var bottom = ascii ? "'" : "╵";
        if (!color) {
            return " ".repeat(width + 1) + top + "\n"
                    + lineNumber + " " + bar + " " + line + "\n"
                    + " ".repeat(width + 1) + bar + " "
                    + " ".repeat(startColumn)
                    + "^".repeat(highlightedLength) + "\n"
                    + " ".repeat(width + 1) + bottom;
        }

        var blue = "\u001b[34m";
        var red = "\u001b[31m";
        var reset = "\u001b[0m";
        var before = line.substring(0, startColumn);
        var highlightEnd = Math.min(
                line.length(),
                startColumn + highlightedLength
        );
        var highlighted = line.substring(startColumn, highlightEnd);
        var after = line.substring(highlightEnd);
        return blue + " ".repeat(width + 1) + top + reset + "\n"
                + blue + lineNumber + " " + bar + reset + " "
                + before + red + highlighted + reset + after + "\n"
                + blue + " ".repeat(width + 1) + bar + reset + " "
                + red + " ".repeat(startColumn)
                + "^".repeat(highlightedLength) + reset + "\n"
                + blue + " ".repeat(width + 1) + bottom + reset;
    }

    /// Returns the display location used by debug messages.
    ///
    /// @param span the associated source span
    /// @return a URL and one-based line
    private static String location(SourceSpan span) {
        return (span.url() == null ? "-" : span.url())
                + ":" + (span.start().line() + 1);
    }

    /// Returns the source line containing a span start.
    ///
    /// @param span the source span
    /// @param source the compilation root, or `null`
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return the complete root line when available, otherwise a padded span
    private static String sourceLine(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        @Nullable var contents = matchingSourceContents(
                span,
                source,
                sourceContents
        );
        if (contents == null) {
            return " ".repeat(Math.max(0, span.start().column()))
                    + span.text().lines().findFirst().orElse("");
        }
        var lines = contents.split("\\R", -1);
        return span.start().line() < lines.length
                ? lines[span.start().line()]
                : " ".repeat(Math.max(0, span.start().column()))
                        + span.text().lines().findFirst().orElse("");
    }

    /// Returns the protocol context excerpt for a span.
    ///
    /// @param span the source span
    /// @param source the compilation root, or `null`
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return the complete covered root lines when available
    private static String spanContext(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        @Nullable var contents = matchingSourceContents(
                span,
                source,
                sourceContents
        );
        if (contents == null) {
            return " ".repeat(Math.max(0, span.start().column()))
                    + span.text();
        }
        var start = lineStart(contents, span.start().offset());
        var end = lineEnd(contents, span.end().offset());
        return contents.substring(start, end);
    }

    /// Returns root text when it owns the requested span.
    ///
    /// @param span the source span
    /// @param source the compilation root, or `null`
    /// @param sourceContents loaded text keyed by canonical URL
    /// @return matching root text, or `null`
    private static @Nullable String matchingSourceContents(
            SourceSpan span,
            @Nullable SassSource source,
            @Unmodifiable Map<URI, String> sourceContents
    ) {
        if (span.url() != null) {
            @Nullable var loaded = sourceContents.get(span.url());
            if (loaded != null) {
                return loaded;
            }
        }
        if (!(source instanceof SassStringSource stringSource)) {
            return null;
        }
        return Objects.equals(span.url(), stringSource.canonicalUrl())
                ? stringSource.content()
                : null;
    }

    /// Returns the offset after the preceding line break.
    ///
    /// @param contents the source text
    /// @param offset an offset in or at the end of the source
    /// @return the containing line's start offset
    private static int lineStart(String contents, int offset) {
        var index = contents.lastIndexOf('\n', Math.max(0, offset - 1));
        return index < 0 ? 0 : index + 1;
    }

    /// Returns the offset after the containing line break when present.
    ///
    /// @param contents the source text
    /// @param offset an offset in or at the end of the source
    /// @return the context excerpt end offset
    private static int lineEnd(String contents, int offset) {
        var index = contents.indexOf('\n', Math.min(offset, contents.length()));
        return index < 0 ? contents.length() : index + 1;
    }

    /// Formats Sass call frames as an implementation-defined stack trace.
    ///
    /// @param frames frames ordered from the failure site outward
    /// @return an empty string or one line per frame
    private static String stackTrace(
            @Unmodifiable List<SassStackFrame> frames
    ) {
        return frames.stream()
                .map(frame -> {
                    var span = frame.span();
                    return (span.url() == null ? "-" : span.url())
                            + " " + (span.start().line() + 1)
                            + ":" + (span.start().column() + 1)
                            + "  " + frame.member();
                })
                .reduce((left, right) -> left + "\n" + right)
                .map(value -> value + "\n")
                .orElse("");
    }
}
