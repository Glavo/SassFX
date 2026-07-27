// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.SassCompilationException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Dart Sass-compatible error stylesheet serialization.
@NotNullByDefault
final class CliErrorCssTest {
    /// Uses an ASCII-safe comment without changing the displayed diagnostic.
    @Test
    void isolatesCommentEscapingFromDisplayedMessage(@TempDir Path directory) {
        var css = format(directory, "Expected \"*/\".");

        assertTrue(css.contains("/* Error: Expected \"*∕\". */"), css);
        assertTrue(css.contains(
                "content: 'Error: Expected \"*/\".';"
        ), css);
    }

    /// Selects quotes and terminates hexadecimal control escapes as Sass does.
    @Test
    void serializesQuotedDiagnosticLikeSass(@TempDir Path directory) {
        var css = format(directory, "can't parse \"line\"\nface");

        assertTrue(css.contains(
                "content: \"Error: can't parse \\\"line\\\"\\a face\";"
        ), css);
    }

    /// Formats one synthetic diagnostic as error CSS.
    ///
    /// @param directory the diagnostic path base
    /// @param message the diagnostic message
    /// @return the complete error stylesheet
    private static String format(Path directory, String message) {
        var failure = new SassCompilationException(List.of(new Diagnostic(
                DiagnosticSeverity.ERROR,
                message,
                null,
                null
        )));
        return CliErrorCss.format(
                failure,
                new DiagnosticPrinter(false, true, directory, null, null)
        );
    }
}
