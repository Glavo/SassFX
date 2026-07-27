// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies exact filesystem candidate tracking for incremental frontends.
@NotNullByDefault
final class SassResolutionTrackerTest {
    /// Stops tracking after a relative location resolves and after the first
    /// successful candidate group.
    @Test
    void excludesUnconsultedLowerPriorityCandidates(
            @TempDir Path directory
    ) throws Exception {
        var loadPath = Files.createDirectory(directory.resolve("load-path"));
        var root = directory.resolve("root.scss");
        Files.writeString(root, "");
        Files.writeString(directory.resolve("_theme.scss"), "$value: root;");
        var tracker = new SassResolutionTracker();
        var importer = new FilesystemImporter(List.of(loadPath), tracker);

        assertNotNull(importer.canonicalizeAndLoad(
                "theme",
                root.toUri()
        ));

        var candidates = tracker.candidatePaths();
        assertTrue(candidates.contains(directory.resolve("theme.scss")));
        assertTrue(candidates.contains(directory.resolve("_theme.scss")));
        assertTrue(candidates.contains(directory.resolve("theme.sass")));
        assertTrue(candidates.contains(directory.resolve("_theme.sass")));
        assertFalse(candidates.contains(directory.resolve("theme.css")));
        assertFalse(candidates.contains(
                directory.resolve("theme/_index.scss")
        ));
        assertFalse(candidates.contains(loadPath.resolve("_theme.scss")));
    }

    /// Tracks every candidate location when resolution is exhausted.
    @Test
    void includesMissingRelativeAndLoadPathCandidates(
            @TempDir Path directory
    ) throws Exception {
        var first = Files.createDirectory(directory.resolve("first"));
        var second = Files.createDirectory(directory.resolve("second"));
        var root = directory.resolve("root.scss");
        Files.writeString(root, "");
        var tracker = new SassResolutionTracker();
        var importer = new FilesystemImporter(
                List.of(first, second),
                tracker
        );

        assertNull(importer.canonicalizeAndLoad(
                "missing",
                root.toUri()
        ));

        var candidates = tracker.candidatePaths();
        assertTrue(candidates.contains(
                directory.resolve("_missing.scss")
        ));
        assertTrue(candidates.contains(
                first.resolve("_missing.scss")
        ));
        assertTrue(candidates.contains(
                second.resolve("_missing.scss")
        ));
        assertTrue(candidates.contains(
                second.resolve("missing/_index.css")
        ));
    }
}
