// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies filesystem module search precedence, ambiguity, and canonical URLs.
@NotNullByDefault
final class FilesystemImporterTest {
    /// Prefers a module beside the containing stylesheet over every load path.
    @Test
    void prefersContainingDirectoryOverLoadPaths(@TempDir Path directory) throws Exception {
        var root = Files.createDirectory(directory.resolve("root"));
        var loadPath = Files.createDirectory(directory.resolve("load-path"));
        var containingFile = Files.writeString(root.resolve("main.scss"), "");
        var relativeModule = Files.writeString(root.resolve("_tokens.scss"), "relative");
        Files.writeString(loadPath.resolve("_tokens.scss"), "load-path");

        var importer = new FilesystemImporter(List.of(loadPath));
        var result = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "tokens",
                containingFile.toRealPath().toUri()
        ));

        assertEquals("relative", result.source().content());
        assertEquals(relativeModule.toRealPath().toUri(), result.canonicalUrl());
        assertEquals(result.canonicalUrl(), result.source().url());
    }

    /// Loads an indented Sass partial selected by an extensionless module URL.
    @Test
    void loadsIndentedSassModules(@TempDir Path directory) throws Exception {
        var containingFile = Files.writeString(directory.resolve("main.scss"), "");
        var module = Files.writeString(directory.resolve("_theme.sass"), "body\n  color: red\n");
        var importer = new FilesystemImporter(List.of());

        var result = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme",
                containingFile.toRealPath().toUri()
        ));

        assertEquals("body\n  color: red\n", result.source().content());
        assertEquals(Syntax.SASS, result.syntax());
        assertEquals(module.toRealPath().toUri(), result.canonicalUrl());
    }

    /// Selects the first load path containing a matching module.
    @Test
    void searchesLoadPathsInDeclarationOrder(@TempDir Path directory) throws Exception {
        var first = Files.createDirectory(directory.resolve("first"));
        var second = Files.createDirectory(directory.resolve("second"));
        var firstModule = Files.writeString(first.resolve("_tokens.scss"), "first");
        Files.writeString(second.resolve("_tokens.scss"), "second");

        var importer = new FilesystemImporter(List.of(first, second));
        var result = Objects.requireNonNull(importer.canonicalizeAndLoad("tokens", null));

        assertEquals("first", result.source().content());
        assertEquals(firstModule.toRealPath().toUri(), result.canonicalUrl());
    }

    /// Reports regular and partial candidates that conflict within one search location.
    @Test
    void rejectsAmbiguousCandidatesWithinOneLocation(@TempDir Path directory) throws Exception {
        var containingFile = Files.writeString(directory.resolve("main.scss"), "");
        var regular = Files.writeString(directory.resolve("tokens.scss"), "regular");
        var partial = Files.writeString(directory.resolve("_tokens.scss"), "partial");
        var importer = new FilesystemImporter(List.of());

        var failure = assertThrows(
                IllegalStateException.class,
                () -> importer.canonicalizeAndLoad(
                        "tokens",
                        containingFile.toRealPath().toUri()
                )
        );

        assertEquals(
                "It's not clear which file to import. Found:\n"
                        + "  " + regular + "\n"
                        + "  " + partial,
                failure.getMessage()
        );
    }
}
