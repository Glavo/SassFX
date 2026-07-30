// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.OutputTarget;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.net.URI;
import java.util.Objects;
import java.util.function.Predicate;

/// Contains immutable settings shared by every compiled job in one invocation.
///
/// @param outputTarget the fully configured output target
/// @param compileOptions compiler options
/// @param outputPolicy resolved output policy
/// @param stdinUrl synthetic standard-input URL
/// @param stdinContents standard-input contents, or `null`
/// @param diagnosticPrinter diagnostic formatter
/// @param color whether terminal status lines use ANSI styling
/// @param quiet whether non-error diagnostics are suppressed
/// @param out standard output
/// @param err standard error
/// @param diagnosticFilter selects diagnostics not already emitted for the
///                         invocation
@NotNullByDefault
record CliExecutionContext(
        OutputTarget<?> outputTarget,
        CompileOptions compileOptions,
        CliOutputPolicy outputPolicy,
        URI stdinUrl,
        @Nullable String stdinContents,
        DiagnosticPrinter diagnosticPrinter,
        boolean color,
        boolean quiet,
        PrintWriter out,
        PrintWriter err,
        Predicate<Diagnostic> diagnosticFilter
) {
    /// Creates a non-null execution context.
    CliExecutionContext {
        Objects.requireNonNull(outputTarget, "outputTarget");
        Objects.requireNonNull(compileOptions, "compileOptions");
        Objects.requireNonNull(outputPolicy, "outputPolicy");
        Objects.requireNonNull(stdinUrl, "stdinUrl");
        Objects.requireNonNull(diagnosticPrinter, "diagnosticPrinter");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Objects.requireNonNull(diagnosticFilter, "diagnosticFilter");
    }

    /// Reports whether a diagnostic should be emitted by this invocation.
    ///
    /// @param diagnostic the diagnostic considered for output
    /// @return whether the diagnostic has not already been emitted
    boolean shouldPrint(Diagnostic diagnostic) {
        return diagnosticFilter.test(
                Objects.requireNonNull(diagnostic, "diagnostic")
        );
    }
}
