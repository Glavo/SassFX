// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/// Records filesystem paths whose existence can change one compilation's
/// import resolution.
///
/// This type supports incremental frontends. It is not part of the stable
/// compiler API.
@ApiStatus.Internal
@NotNullByDefault
public final class SassResolutionTracker {
    /// Candidate paths consulted by filesystem resolution.
    private final LinkedHashSet<Path> candidatePaths =
            new LinkedHashSet<>();

    /// Whether every successful resolution used the built-in filesystem
    /// importer.
    private boolean complete = true;

    /// Creates an empty tracker.
    public SassResolutionTracker() {
    }

    /// Records one filesystem candidate before its existence is tested.
    ///
    /// @param path the candidate path
    void recordCandidate(Path path) {
        candidatePaths.add(Objects.requireNonNull(
                path,
                "path"
        ).toAbsolutePath().normalize());
    }

    /// Marks a successful resolution whose mutable inputs cannot be represented
    /// by filesystem candidate paths.
    public void markIncomplete() {
        complete = false;
    }

    /// Returns candidate paths in resolution-observation order.
    ///
    /// The returned set is an immutable snapshot.
    ///
    /// @return candidate paths whose addition, removal, or replacement may
    /// affect the compilation
    public @Unmodifiable Set<Path> candidatePaths() {
        return Set.copyOf(candidatePaths);
    }

    /// Reports whether the recorded paths completely describe mutable
    /// resolution inputs.
    ///
    /// @return `false` if a custom importer successfully resolved any load
    public boolean isComplete() {
        return complete;
    }
}
