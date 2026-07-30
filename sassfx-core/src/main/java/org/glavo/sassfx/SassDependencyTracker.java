// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/// Records mutable filesystem inputs consulted during Sass resolution.
///
/// Pass an instance to
/// [SassCompiler#compile(SassSource, OutputTarget, CompileOptions, SassDependencyTracker)]
/// when an incremental frontend must observe files whose creation, removal, or
/// replacement could change import resolution. One tracker may be reused
/// sequentially; each compilation replaces its previous state. A tracker
/// cannot be used by more than one compilation at a time.
///
/// The compiler clears the visible state when a tracked compilation begins and
/// installs the final or partial state before that compilation returns or
/// throws. Reads performed concurrently with compilation may therefore observe
/// the cleared state.
@NotNullByDefault
public final class SassDependencyTracker {
    /// Contains the immutable candidate-path snapshot.
    private @Unmodifiable Set<Path> candidatePaths = Set.of();

    /// Whether candidate paths fully represent mutable resolution inputs.
    private boolean complete = true;

    /// Whether one compiler invocation currently owns this tracker.
    private boolean active;

    /// Creates an empty, complete tracker.
    public SassDependencyTracker() {
    }

    /// Returns filesystem candidates in resolution-observation order.
    ///
    /// Every path is absolute and normalized. The returned set is an immutable
    /// snapshot whose contents do not change when the tracker is reused.
    ///
    /// @return paths whose existence or contents may affect resolution
    public synchronized @Unmodifiable Set<Path> candidatePaths() {
        return candidatePaths;
    }

    /// Reports whether the candidate paths describe every mutable resolution
    /// input observed by the compilation.
    ///
    /// A successful custom importer or JavaFX stylesheet resolver makes this
    /// value `false`, because its mutable inputs cannot be represented solely
    /// as filesystem candidate paths.
    ///
    /// @return whether the candidate-path snapshot is complete
    public synchronized boolean isComplete() {
        return complete;
    }

    /// Reserves and clears this tracker for one compilation.
    ///
    /// @throws IllegalStateException if another compilation already uses this
    /// tracker
    synchronized void beginCompilation() {
        if (active) {
            throw new IllegalStateException(
                    "SassDependencyTracker is already in use"
            );
        }
        active = true;
        candidatePaths = Set.of();
        complete = true;
    }

    /// Publishes the resolution state recorded by a finished compilation.
    ///
    /// @param candidates filesystem candidates in observation order
    /// @param resolutionComplete whether the candidates represent every
    /// mutable resolution input
    /// @throws IllegalStateException if no compilation owns this tracker
    synchronized void finishCompilation(
            Set<Path> candidates,
            boolean resolutionComplete
    ) {
        if (!active) {
            throw new IllegalStateException(
                    "SassDependencyTracker is not in use"
            );
        }
        candidatePaths = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(
                        candidates,
                        "candidates"
                ))
        );
        complete = resolutionComplete;
        active = false;
    }
}
