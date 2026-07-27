// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies filesystem candidate precedence unique to legacy imports.
@NotNullByDefault
final class FilesystemLegacyImporterTest {
    /// Prefers an import-only partial while ordinary module loading ignores it.
    @Test
    void distinguishesLegacyAndModuleResolution(@TempDir Path directory) throws Exception {
        var containing = Files.writeString(directory.resolve("main.scss"), "");
        var ordinary = Files.writeString(directory.resolve("_theme.scss"), "ordinary");
        var importOnly = Files.writeString(
                directory.resolve("_theme.import.scss"),
                "import-only"
        );
        var importer = new FilesystemImporter(List.of());

        var moduleResult = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme",
                containing.toRealPath().toUri()
        ));
        var importResult = Objects.requireNonNull(importer.canonicalizeAndLoadImport(
                "theme",
                containing.toRealPath().toUri()
        ));

        assertEquals(ordinary.toRealPath().toUri(), moduleResult.canonicalUrl());
        assertEquals(importOnly.toRealPath().toUri(), importResult.canonicalUrl());
    }

    /// Resolves an import-only directory index.
    @Test
    void resolvesImportOnlyDirectoryIndexes(@TempDir Path directory) throws Exception {
        var containing = Files.writeString(directory.resolve("main.scss"), "");
        var packageDirectory = Files.createDirectory(directory.resolve("theme"));
        var index = Files.writeString(
                packageDirectory.resolve("_index.import.scss"),
                "index"
        );
        var importer = new FilesystemImporter(List.of());

        var result = Objects.requireNonNull(importer.canonicalizeAndLoadImport(
                "theme",
                containing.toRealPath().toUri()
        ));

        assertEquals(index.toRealPath().toUri(), result.canonicalUrl());
    }
}
