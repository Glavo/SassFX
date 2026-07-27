// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

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
}
