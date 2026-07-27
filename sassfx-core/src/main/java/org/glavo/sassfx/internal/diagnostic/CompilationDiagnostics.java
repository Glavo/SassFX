// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.diagnostic;

import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.SassDeprecation;
import org.glavo.sassfx.SassDiagnosticOptions;
import org.glavo.sassfx.SassLogEvent;
import org.glavo.sassfx.SassStackFrame;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.evaluate.EvaluationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/// Processes and collects diagnostics for exactly one compilation.
///
/// This object is intentionally not stored in compile options or compiler
/// instances. Repetition counts, exact-warning deduplication, and dependency
/// state must not leak across compilations.
@ApiStatus.Internal
@NotNullByDefault
public final class CompilationDiagnostics {
    /// The number of retained occurrences per deprecation in non-verbose mode.
    private static final int MAX_DEPRECATION_REPETITIONS = 5;

    /// Contains immutable user configuration.
    private final SassDiagnosticOptions options;

    /// Contains retained events in delivery order.
    private final ArrayList<Diagnostic> diagnostics = new ArrayList<>();

    /// Contains compiler-warning identities already processed.
    private final HashSet<WarningIdentity> emittedWarnings = new HashSet<>();

    /// Counts active, non-silenced deprecations before repetition filtering.
    private final EnumMap<SassDeprecation, Integer> warningCounts =
            new EnumMap<>(SassDeprecation.class);

    /// Records whether successful-compilation summarization has run.
    private boolean finished;

    /// Creates one diagnostic processor and reports option-validation warnings.
    ///
    /// @param options immutable diagnostic configuration
    public CompilationDiagnostics(SassDiagnosticOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        validateOptions();
    }

    /// Returns non-fatal warnings produced by one diagnostic configuration.
    ///
    /// The returned diagnostics have no source span or deprecation identifier.
    /// This method does not publish the warnings to the configured logger.
    ///
    /// @param options immutable diagnostic configuration
    /// @return configuration warnings in Sass registry order
    public static @Unmodifiable List<Diagnostic> configurationWarnings(
            SassDiagnosticOptions options
    ) {
        Objects.requireNonNull(options, "options");
        var warnings = new ArrayList<Diagnostic>();
        for (var deprecation : SassDeprecation.values()) {
            if (!options.fatalDeprecations().contains(deprecation)) {
                continue;
            }
            if (deprecation.isFuture()
                    && !options.futureDeprecations().contains(deprecation)) {
                warnings.add(configurationWarning(
                        "Future " + deprecation.id()
                                + " deprecation must be enabled before it can "
                                + "be made fatal."
                ));
            } else if (deprecation.obsoleteIn() != null) {
                warnings.add(configurationWarning(
                        deprecation.id()
                                + " deprecation is obsolete, so does not need "
                                + "to be made fatal."
                ));
            } else if (options.silenceDeprecations().contains(deprecation)) {
                warnings.add(configurationWarning(
                        "Ignoring setting to silence " + deprecation.id()
                                + " deprecation, since it has also been made fatal."
                ));
            }
        }

        for (var deprecation : SassDeprecation.values()) {
            if (!options.silenceDeprecations().contains(deprecation)) {
                continue;
            }
            if (deprecation == SassDeprecation.USER_AUTHORED) {
                warnings.add(configurationWarning(
                        "User-authored deprecations should not be silenced."
                ));
            } else if (deprecation.obsoleteIn() != null) {
                warnings.add(configurationWarning(
                        deprecation.id()
                                + " deprecation is obsolete. If you were previously "
                                + "silencing it, your code may now behave in "
                                + "unexpected ways."
                ));
            } else if (deprecation.isFuture()
                    && options.futureDeprecations().contains(deprecation)) {
                warnings.add(configurationWarning(
                        "Conflicting options for future " + deprecation.id()
                                + " deprecation cancel each other out."
                ));
            } else if (deprecation.isFuture()) {
                warnings.add(configurationWarning(
                        "Future " + deprecation.id()
                                + " deprecation is not yet active, so silencing "
                                + "it is unnecessary."
                ));
            }
        }

        for (var deprecation : SassDeprecation.values()) {
            if (!options.futureDeprecations().contains(deprecation)) {
                continue;
            }
            if (!deprecation.isFuture()) {
                warnings.add(configurationWarning(
                        deprecation.id()
                                + " is not a future deprecation, so it does not "
                                + "need to be explicitly enabled."
                ));
            }
        }
        return List.copyOf(warnings);
    }

    /// Returns retained diagnostics as an immutable snapshot.
    ///
    /// @return diagnostics in delivery order
    public @Unmodifiable List<Diagnostic> snapshot() {
        return List.copyOf(diagnostics);
    }

    /// Reports a parser- or compiler-generated warning.
    ///
    /// @param diagnostic the warning or deprecation
    /// @param dependency whether it originated in a dependency stylesheet
    public void compilerWarning(Diagnostic diagnostic, boolean dependency) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.severity() != DiagnosticSeverity.WARNING
                && diagnostic.severity() != DiagnosticSeverity.DEPRECATION) {
            throw new IllegalArgumentException(
                    "compiler warnings must have WARNING or DEPRECATION severity"
            );
        }
        if (options.quietDeps() && dependency) {
            return;
        }
        if (!emittedWarnings.add(new WarningIdentity(
                diagnostic.message(),
                diagnostic.span()
        ))) {
            return;
        }
        if (diagnostic.severity() == DiagnosticSeverity.DEPRECATION) {
            processDeprecation(diagnostic);
        } else {
            retain(diagnostic, traceFor(diagnostic.span()), null);
        }
    }

    /// Reports an explicit Sass {@code @warn}.
    ///
    /// User warnings bypass dependency silencing and compiler-warning
    /// deduplication. The retained diagnostic preserves the statement span,
    /// while the logger view follows Sass and exposes no direct span.
    ///
    /// @param diagnostic the warning with its statement span
    /// @param trace the Sass call trace from the warning site outward
    public void userWarning(
            Diagnostic diagnostic,
            @Unmodifiable List<SassStackFrame> trace
    ) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.severity() != DiagnosticSeverity.WARNING) {
            throw new IllegalArgumentException(
                    "user warnings must have WARNING severity"
            );
        }
        diagnostics.add(diagnostic);
        var loggerDiagnostic = new Diagnostic(
                DiagnosticSeverity.WARNING,
                diagnostic.message(),
                null,
                diagnostic.code()
        );
        options.logger().log(new SassLogEvent(
                loggerDiagnostic,
                trace,
                null
        ));
    }

    /// Reports an explicit Sass {@code @debug} event.
    ///
    /// @param diagnostic the debug message and mandatory statement span
    public void debug(Diagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.severity() != DiagnosticSeverity.DEBUG) {
            throw new IllegalArgumentException(
                    "debug events must have DEBUG severity"
            );
        }
        diagnostics.add(diagnostic);
        options.logger().log(new SassLogEvent(diagnostic, List.of(), null));
    }

    /// Adds the omitted-deprecation summary after successful compilation.
    ///
    /// Repeated calls have no effect.
    public void finishSuccess() {
        if (finished) {
            return;
        }
        finished = true;
        if (options.verbose()) {
            return;
        }
        var omitted = warningCounts.values().stream()
                .filter(count -> count > MAX_DEPRECATION_REPETITIONS)
                .mapToInt(count -> count - MAX_DEPRECATION_REPETITIONS)
                .sum();
        if (omitted == 0) {
            return;
        }
        retain(
                new Diagnostic(
                        DiagnosticSeverity.WARNING,
                        omitted + " repetitive deprecation warnings omitted.\n"
                                + "Run in verbose mode to see all warnings.",
                        null,
                        null
                ),
                List.of(),
                null
        );
    }

    /// Applies future, fatal, silence, and repetition processing.
    private void processDeprecation(Diagnostic diagnostic) {
        @Nullable SassDeprecation deprecation =
                diagnostic.code() == null
                        ? null
                        : SassDeprecation.fromId(diagnostic.code());
        if (deprecation == null) {
            deprecation = SassDeprecation.USER_AUTHORED;
        }

        if (deprecation.isFuture()
                && !options.futureDeprecations().contains(deprecation)) {
            return;
        }
        if (options.fatalDeprecations().contains(deprecation)) {
            var message = diagnostic.message()
                    + "\n\nThis is only an error because you've set the "
                    + deprecation.id() + " deprecation to be fatal.\n"
                    + "Remove this setting if you need to keep using this feature.";
            var span = Objects.requireNonNull(
                    diagnostic.span(),
                    "fatal deprecation span"
            );
            throw new EvaluationException(
                    new Diagnostic(
                            DiagnosticSeverity.ERROR,
                            message,
                            span,
                            deprecation.id()
                    ),
                    List.of(),
                    traceFor(span),
                    null
            );
        }
        if (options.silenceDeprecations().contains(deprecation)) {
            return;
        }

        var count = warningCounts.merge(deprecation, 1, Integer::sum);
        if (!options.verbose() && count > MAX_DEPRECATION_REPETITIONS) {
            return;
        }
        retain(diagnostic, traceFor(diagnostic.span()), deprecation);
    }

    /// Stores and synchronously publishes one processed event.
    private void retain(
            Diagnostic diagnostic,
            @Unmodifiable List<SassStackFrame> trace,
            @Nullable SassDeprecation deprecation
    ) {
        diagnostics.add(diagnostic);
        options.logger().log(new SassLogEvent(diagnostic, trace, deprecation));
    }

    /// Returns the best currently available Sass trace for a diagnostic span.
    private static @Unmodifiable List<SassStackFrame> traceFor(
            @Nullable SourceSpan span
    ) {
        return span == null
                ? List.of()
                : List.of(new SassStackFrame("root stylesheet", span));
    }

    /// Reports non-fatal configuration conflicts in Sass registry order.
    private void validateOptions() {
        for (var warning : configurationWarnings(options)) {
            retain(warning, List.of(), null);
        }
    }

    /// Creates one span-free ordinary configuration warning.
    ///
    /// @param message complete warning text
    /// @return the warning diagnostic
    private static Diagnostic configurationWarning(String message) {
        return new Diagnostic(
                DiagnosticSeverity.WARNING,
                message,
                null,
                null
        );
    }

    /// Identifies one compiler warning before deprecation processing.
    ///
    /// @param message the complete warning text
    /// @param span the exact source range, or {@code null}
    @NotNullByDefault
    private record WarningIdentity(
            String message,
            @Nullable SourceSpan span
    ) {
    }
}
