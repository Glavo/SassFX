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

    /// Loads explicit, extensionless, and directory-index CSS modules.
    @Test
    void loadsPlainCssModules(@TempDir Path directory) throws Exception {
        var containingFile = Files.writeString(directory.resolve("main.scss"), "");
        var partial = Files.writeString(directory.resolve("_theme.css"), ".theme {}");
        var importer = new FilesystemImporter(List.of());

        var explicit = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme.css",
                containingFile.toRealPath().toUri()
        ));
        assertEquals(Syntax.CSS, explicit.syntax());
        assertEquals(partial.toRealPath().toUri(), explicit.canonicalUrl());

        Files.delete(partial);
        var extensionlessFile = Files.writeString(
                directory.resolve("theme.css"),
                ".extensionless {}"
        );
        var extensionless = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme",
                containingFile.toRealPath().toUri()
        ));
        assertEquals(extensionlessFile.toRealPath().toUri(), extensionless.canonicalUrl());

        Files.delete(extensionlessFile);
        var moduleDirectory = Files.createDirectory(directory.resolve("theme"));
        var index = Files.writeString(moduleDirectory.resolve("_index.css"), ".index {}");
        var indexed = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme",
                containingFile.toRealPath().toUri()
        ));
        assertEquals(index.toRealPath().toUri(), indexed.canonicalUrl());
    }

    /// Prefers any Sass or SCSS candidate group over an extensionless CSS candidate.
    @Test
    void prefersSassSyntaxCandidatesOverCss(@TempDir Path directory) throws Exception {
        var containingFile = Files.writeString(directory.resolve("main.scss"), "");
        var scss = Files.writeString(directory.resolve("theme.scss"), "$source: scss;");
        Files.writeString(directory.resolve("theme.css"), ".theme {}");
        var importer = new FilesystemImporter(List.of());

        var result = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme",
                containingFile.toRealPath().toUri()
        ));
        assertEquals(Syntax.SCSS, result.syntax());
        assertEquals(scss.toRealPath().toUri(), result.canonicalUrl());
    }

    /// Reports regular and partial CSS candidates as ambiguous.
    @Test
    void rejectsAmbiguousCssCandidates(@TempDir Path directory) throws Exception {
        var containingFile = Files.writeString(directory.resolve("main.scss"), "");
        Files.writeString(directory.resolve("theme.css"), ".regular {}");
        Files.writeString(directory.resolve("_theme.css"), ".partial {}");
        var importer = new FilesystemImporter(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> importer.canonicalizeAndLoad(
                        "theme.css",
                        containingFile.toRealPath().toUri()
                )
        );
    }

    /// Resolves recognized extensions case-insensitively and extends dotted stems.
    @Test
    void resolvesCaseInsensitiveExtensionsAndDottedStems(@TempDir Path directory)
            throws Exception {
        var containingFile = Files.writeString(directory.resolve("main.scss"), "");
        var uppercase = Files.writeString(directory.resolve("theme.CSS"), ".upper {}");
        var dotted = Files.writeString(directory.resolve("theme.v2.css"), ".dotted {}");
        var importer = new FilesystemImporter(List.of());

        var explicit = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme.CSS",
                containingFile.toRealPath().toUri()
        ));
        assertEquals(Syntax.CSS, explicit.syntax());
        assertEquals(uppercase.toRealPath().toUri(), explicit.canonicalUrl());

        var extensionless = Objects.requireNonNull(importer.canonicalizeAndLoad(
                "theme.v2",
                containingFile.toRealPath().toUri()
        ));
        assertEquals(dotted.toRealPath().toUri(), extensionless.canonicalUrl());
    }

    /// Prefers an import-only CSS file during legacy import resolution.
    @Test
    void prefersImportOnlyCssCandidates(@TempDir Path directory) throws Exception {
        var containingFile = Files.writeString(directory.resolve("main.scss"), "");
        Files.writeString(directory.resolve("theme.css"), ".regular {}");
        var importOnly = Files.writeString(
                directory.resolve("_theme.import.css"),
                ".import-only {}"
        );
        var importer = new FilesystemImporter(List.of());

        var result = Objects.requireNonNull(importer.canonicalizeAndLoadImport(
                "theme",
                containingFile.toRealPath().toUri()
        ));
        assertEquals(Syntax.CSS, result.syntax());
        assertEquals(importOnly.toRealPath().toUri(), result.canonicalUrl());
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
                        + "  _tokens.scss\n"
                        + "  tokens.scss",
                failure.getMessage()
        );
    }
}
