// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/// Renders [DiagnosticCode] values into human-readable English text.
///
/// All user-facing diagnostic strings must be produced here so implementations
/// can pass structured codes and parameters without embedding final prose at
/// throw sites.
@NotNullByDefault
public final class DiagnosticMessages {
    private DiagnosticMessages() {
    }

    /// Renders one diagnostic code with optional format arguments.
    ///
    /// @param code the stable diagnostic code
    /// @param args format arguments for codes that carry parameters; the first
    ///             argument is treated as a pre-rendered message for
    ///             {@link DiagnosticCode#PARSE_ERROR} and
    ///             {@link DiagnosticCode#EVALUATION_ERROR}
    /// @return the English diagnostic text
    public static String render(DiagnosticCode code, Object @Nullable ... args) {
        Objects.requireNonNull(code, "code");
        Object[] safeArgs = args == null ? new Object[0] : args;
        return switch (code) {
            case PARSE_ERROR, EVALUATION_ERROR, MODULE_ERROR, SELECTOR_ERROR,
                    SERIALIZE_ERROR, UNSUPPORTED_FEATURE, EXPECTED_TOKEN,
                    UNDEFINED_OPERATION ->
                    requireMessage(safeArgs);
            case INDENTED_NESTING_WITHOUT_HEADER ->
                    "Indented Sass statements must be nested below a block header.";
            case INDENTED_TEXT_AFTER_COMMENT ->
                    "Unexpected text after end of comment";
            case INDENTED_INCONSISTENT_INDENT ->
                    // Match dart-sass wording exactly: "spaces" even when tabs
                    // contributed to the measured indent width.
                    safeArgs.length > 0
                            ? "Inconsistent indentation, expected " + safeArgs[0] + " spaces."
                            : "Inconsistent indentation.";
        };
    }

    /// Creates an error [Diagnostic] from a code and arguments.
    ///
    /// @param code the stable code
    /// @param span the associated source span, or {@code null}
    /// @param args format arguments
    /// @return the structured diagnostic with rendered message
    public static Diagnostic error(
            DiagnosticCode code,
            @Nullable SourceSpan span,
            Object @Nullable ... args
    ) {
        return new Diagnostic(
                DiagnosticSeverity.ERROR,
                render(code, args),
                span,
                code.name()
        );
    }

    /// Creates a warning [Diagnostic] from a code and arguments.
    ///
    /// @param code the stable code
    /// @param span the associated source span, or {@code null}
    /// @param args format arguments
    /// @return the structured diagnostic with rendered message
    public static Diagnostic warning(
            DiagnosticCode code,
            @Nullable SourceSpan span,
            Object @Nullable ... args
    ) {
        return new Diagnostic(
                DiagnosticSeverity.WARNING,
                render(code, args),
                span,
                code.name()
        );
    }

    private static String requireMessage(Object[] args) {
        if (args.length == 0 || args[0] == null) {
            throw new IllegalArgumentException("message argument is required");
        }
        return String.valueOf(args[0]);
    }
}
