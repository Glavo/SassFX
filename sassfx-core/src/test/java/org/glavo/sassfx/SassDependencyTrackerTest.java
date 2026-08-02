// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies public incremental dependency tracking.
@NotNullByDefault
final class SassDependencyTrackerTest {
    /// Exposes filesystem candidates from a successful compilation.
    @Test
    void recordsFilesystemCandidates(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("root.scss");
        Files.writeString(
                root,
                "@use 'theme'; a { value: theme.$value; }"
        );
        Files.writeString(
                directory.resolve("_theme.scss"),
                "$value: tracked;"
        );
        var tracker = new SassDependencyTracker();

        new SassCompiler().compile(
                SassSource.fromFile(root),
                CssTarget.DEFAULT,
                CompileOptions.DEFAULT,
                tracker
        );

        assertTrue(tracker.isComplete());
        assertTrue(tracker.candidatePaths().contains(
                directory.resolve("theme.scss")
                        .toAbsolutePath()
                        .normalize()
        ));
        assertTrue(tracker.candidatePaths().contains(
                directory.resolve("_theme.scss")
                        .toAbsolutePath()
                        .normalize()
        ));
        assertThrows(
                UnsupportedOperationException.class,
                () -> tracker.candidatePaths().clear()
        );
    }

    /// Retains partial resolution state after a compilation failure.
    @Test
    void recordsCandidatesBeforeFailure(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("root.scss");
        Files.writeString(root, "@use 'missing';");
        var tracker = new SassDependencyTracker();

        assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(root),
                        CssTarget.DEFAULT,
                        CompileOptions.DEFAULT,
                        tracker
                )
        );

        assertTrue(tracker.isComplete());
        assertFalse(tracker.candidatePaths().isEmpty());
        assertTrue(tracker.candidatePaths().contains(
                directory.resolve("_missing.scss")
                        .toAbsolutePath()
                        .normalize()
        ));
    }

    /// Marks successful custom-importer resolution as incomplete.
    @Test
    void marksCustomImporterResolutionIncomplete(
            @TempDir Path directory
    ) throws Exception {
        var root = directory.resolve("root.scss");
        var imports = Files.createDirectory(directory.resolve("imports"));
        var imported = imports.resolve("_custom.scss");
        Files.writeString(root, "@use 'custom';");
        Files.writeString(imported, "$value: custom;");
        SassFileImporter importer = (url, context) -> imported.toUri();
        var options = CompileOptions.DEFAULT.withImporters(List.of(importer));
        var tracker = new SassDependencyTracker();

        new SassCompiler().compile(
                SassSource.fromFile(root),
                CssTarget.DEFAULT,
                options,
                tracker
        );

        assertFalse(tracker.isComplete());
    }
}
