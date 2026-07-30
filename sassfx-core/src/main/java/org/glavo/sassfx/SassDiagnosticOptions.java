// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Configures compiler logging and deprecation processing.
///
/// @param logger receives processed warnings, deprecations, and debug messages
/// @param quietDeps whether warnings from importer and load-path dependencies
///                  are suppressed
/// @param verbose whether every repeated deprecation is retained
/// @param silenceDeprecations deprecations omitted from output
/// @param fatalDeprecations deprecations promoted to compilation errors
/// @param futureDeprecations future deprecations explicitly enabled
@NotNullByDefault
public record SassDiagnosticOptions(
        SassLogger logger,
        boolean quietDeps,
        boolean verbose,
        @Unmodifiable Set<SassDeprecation> silenceDeprecations,
        @Unmodifiable Set<SassDeprecation> fatalDeprecations,
        @Unmodifiable Set<SassDeprecation> futureDeprecations
) {
    /// The default processing policy with no logger side effects.
    public static final SassDiagnosticOptions DEFAULT =
            new SassDiagnosticOptions(
                    SassLogger.NO_OP,
                    false,
                    false,
                    Set.of(),
                    Set.of(),
                    Set.of()
            );

    /// Creates default diagnostic processing with a custom logger.
    ///
    /// @param logger receives all retained events
    public SassDiagnosticOptions(SassLogger logger) {
        this(logger, false, false, Set.of(), Set.of(), Set.of());
    }

    /// Creates options with immutable deprecation-set snapshots.
    public SassDiagnosticOptions {
        Objects.requireNonNull(logger, "logger");
        silenceDeprecations = Set.copyOf(silenceDeprecations);
        fatalDeprecations = Set.copyOf(fatalDeprecations);
        futureDeprecations = Set.copyOf(futureDeprecations);
    }

    /// Returns non-fatal warnings produced by this option combination.
    ///
    /// The returned diagnostics have no source span or deprecation identifier.
    /// Calling this method does not publish warnings to [#logger()].
    ///
    /// @return configuration warnings in deprecation-registry order
    public @Unmodifiable List<Diagnostic> configurationWarnings() {
        var warnings = new ArrayList<Diagnostic>();
        for (var deprecation : SassDeprecation.values()) {
            if (!fatalDeprecations.contains(deprecation)) {
                continue;
            }
            if (deprecation.isFuture()
                    && !futureDeprecations.contains(deprecation)) {
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
            } else if (silenceDeprecations.contains(deprecation)) {
                warnings.add(configurationWarning(
                        "Ignoring setting to silence " + deprecation.id()
                                + " deprecation, since it has also been made fatal."
                ));
            }
        }

        for (var deprecation : SassDeprecation.values()) {
            if (!silenceDeprecations.contains(deprecation)) {
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
                    && futureDeprecations.contains(deprecation)) {
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
            if (!futureDeprecations.contains(deprecation)) {
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
}
