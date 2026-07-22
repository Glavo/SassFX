// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Formats compiler diagnostics for CLI output.
@NotNullByDefault
final class DiagnosticPrinter {
    /// Prevents instantiation.
    private DiagnosticPrinter() {
    }

    /// Formats a compilation exception for stderr.
    ///
    /// @param failure the compilation failure
    /// @return the multi-line diagnostic text
    static String format(SassCompilationException failure) {
        Objects.requireNonNull(failure, "failure");
        return format(failure.primaryDiagnostic());
    }

    /// Formats one diagnostic for CLI output.
    ///
    /// @param diagnostic the diagnostic to format
    /// @return the multi-line diagnostic text
    static String format(Diagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        var label = switch (diagnostic.severity()) {
            case ERROR -> "Error";
            case WARNING -> "Warning";
            case DEPRECATION -> "Deprecation Warning";
            case DEBUG -> "Debug";
        };
        var builder = new StringBuilder(label).append(": ").append(diagnostic.message());
        @Nullable SourceSpan span = diagnostic.span();
        if (span == null) {
            return builder.toString();
        }

        builder.append('\n');
        appendSpan(builder, span);
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
            builder.append("  root stylesheet");
        }
        return builder.toString();
    }

    /// Appends a source-span excerpt with a caret underline.
    ///
    /// @param builder the destination
    /// @param span    the source span
    private static void appendSpan(StringBuilder builder, SourceSpan span) {
        var lineNumber = span.start().line() + 1;
        var column = span.start().column();
        var lineText = span.text().contains("\n")
                ? span.text().substring(0, span.text().indexOf('\n'))
                : span.text();
        // Prefer a single-line excerpt when the span is multi-line.
        if (span.text().indexOf('\n') >= 0 && !lineText.isEmpty()) {
            // Keep the first line of the span text only.
        }

        builder.append("  ╷\n");
        builder.append(String.format("%3d", lineNumber)).append(" │ ").append(lineText).append('\n');
        builder.append("  │ ").append(" ".repeat(Math.max(0, column))).append("^\n");
        builder.append("  ╵\n");
        if (span.url() != null) {
            builder.append(span.url()).append(' ');
        }
        builder.append(lineNumber).append(':').append(column + 1).append('\n');
    }
}
