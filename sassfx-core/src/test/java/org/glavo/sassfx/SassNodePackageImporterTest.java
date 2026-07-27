// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Node package lookup, package metadata, exports, and CLI-facing
/// importer contracts.
@NotNullByDefault
final class SassNodePackageImporterTest {
    /// Resolves exports before sass, style, and index root fields.
    @Test
    void resolvesRootFieldPrecedence(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeManifest(
                packageRoot,
                """
                        {
                          "exports": {".": {"sass": "./export.scss"}},
                          "sass": "./sass.scss",
                          "style": "./style.css"
                        }
                        """
        );
        writeStylesheet(packageRoot.resolve("export.scss"), ".picked-export {v: 1}");
        writeStylesheet(packageRoot.resolve("sass.scss"), ".picked-sass {v: 1}");
        writeStylesheet(packageRoot.resolve("style.css"), ".picked-style {v: 1}");
        writeStylesheet(packageRoot.resolve("index.scss"), ".picked-index {v: 1}");

        var css = compileFile(root, "@use \"pkg:demo\";");
        assertTrue(css.contains(".picked-export"));
        assertFalse(css.contains(".picked-sass"));
        assertFalse(css.contains(".picked-style"));
        assertFalse(css.contains(".picked-index"));
    }

    /// Uses manifest insertion order for recognized export conditions.
    @Test
    void preservesExportConditionOrder(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeManifest(
                packageRoot,
                """
                        {
                          "exports": {
                            "default": "./default.scss",
                            "sass": "./sass.scss"
                          }
                        }
                        """
        );
        writeStylesheet(packageRoot.resolve("default.scss"), ".picked-default {v: 1}");
        writeStylesheet(packageRoot.resolve("sass.scss"), ".picked-sass {v: 1}");

        var css = compileFile(root, "@use \"pkg:demo\";");
        assertTrue(css.contains(".picked-default"));
        assertFalse(css.contains(".picked-sass"));
    }

    /// Falls back through sass, style, and filesystem index resolution.
    @Test
    void resolvesRootFallbacks(@TempDir Path directory) throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeManifest(
                packageRoot,
                """
                        {
                          "sass": "./entry",
                          "style": "./style.css",
                          "main": "./ignored.scss"
                        }
                        """
        );
        writeStylesheet(packageRoot.resolve("style.css"), ".picked-style {v: 1}");
        writeStylesheet(packageRoot.resolve("ignored.scss"), ".picked-main {v: 1}");
        assertTrue(compileFile(root, "@use \"pkg:demo\";")
                .contains(".picked-style"));

        writeManifest(packageRoot, "{\"main\":\"./ignored.scss\"}");
        writeStylesheet(packageRoot.resolve("_index.scss"), ".picked-index {v: 1}");
        var css = compileFile(root, "@use \"pkg:demo\";");
        assertTrue(css.contains(".picked-index"));
        assertFalse(css.contains(".picked-main"));
    }

    /// Resolves scoped packages and the closest nested node_modules directory.
    @Test
    void resolvesScopedAndNestedPackages(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var outer = packageRoot(root, "@scope/outer");
        writeManifest(outer, "{\"sass\":\"./outer.scss\"}");
        writeStylesheet(
                outer.resolve("outer.scss"),
                "@use \"pkg:inner\"; .picked-outer {v: 1}"
        );

        var nested = packageRoot(outer, "inner");
        writeManifest(nested, "{\"sass\":\"./inner.scss\"}");
        writeStylesheet(
                nested.resolve("inner.scss"),
                ".picked-nested {v: 1}"
        );

        var parent = packageRoot(root, "inner");
        writeManifest(parent, "{\"sass\":\"./inner.scss\"}");
        writeStylesheet(
                parent.resolve("inner.scss"),
                ".picked-parent {v: 1}"
        );

        var css = compileFile(root, "@use \"pkg:@scope/outer\";");
        assertTrue(css.contains(".picked-outer"));
        assertTrue(css.contains(".picked-nested"));
        assertFalse(css.contains(".picked-parent"));
    }

    /// Resolves raw subpaths with standard partial and directory-index rules.
    @Test
    void resolvesFilesystemSubpaths(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeManifest(packageRoot, "{}");
        writeStylesheet(
                packageRoot.resolve("tokens").resolve("_colors.scss"),
                ".picked-partial {v: 1}"
        );
        writeStylesheet(
                packageRoot.resolve("components").resolve("_index.sass"),
                ".picked-index\n  v: 1"
        );

        var css = compileFile(
                root,
                """
                        @use "pkg:demo/tokens/colors";
                        @use "pkg:demo/components";
                        """
        );
        assertTrue(css.contains(".picked-partial"));
        assertTrue(css.contains(".picked-index"));
    }

    /// Treats an exact export target as an exact file rather than a file stem.
    @Test
    void doesNotReResolveExactExportTargets(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeManifest(
                packageRoot,
                "{\"exports\":{\".\":\"./entry.scss\"}}"
        );
        writeStylesheet(packageRoot.resolve("entry.scss"), ".picked-exact {v: 1}");
        writeStylesheet(packageRoot.resolve("_entry.scss"), ".picked-partial {v: 1}");

        var css = compileFile(root, "@use \"pkg:demo\";");
        assertTrue(css.contains(".picked-exact"));
        assertFalse(css.contains(".picked-partial"));
    }

    /// Resolves wildcard exports and falls through missing pattern targets.
    @Test
    void resolvesWildcardExportTargets(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeManifest(
                packageRoot,
                """
                        {
                          "exports": {
                            "./themes/*": [
                              {"sass": "./missing/*.scss"},
                              {"default": "./src/*.scss"}
                            ]
                          }
                        }
                        """
        );
        writeStylesheet(
                packageRoot.resolve("src").resolve("dark.scss"),
                ".picked-dark {v: 1}"
        );

        assertTrue(compileFile(root, "@use \"pkg:demo/themes/dark\";")
                .contains(".picked-dark"));
    }

    /// Accepts multiple export variants only when they select one path.
    @Test
    void detectsAmbiguousExportVariants(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeStylesheet(packageRoot.resolve("same.scss"), ".picked-same {v: 1}");
        writeManifest(
                packageRoot,
                """
                        {
                          "exports": {
                            "./token": "./same.scss",
                            "./token.scss": "./same.scss"
                          }
                        }
                        """
        );
        assertTrue(compileFile(root, "@use \"pkg:demo/token\";")
                .contains(".picked-same"));

        writeStylesheet(packageRoot.resolve("other.scss"), ".picked-other {v: 1}");
        writeManifest(
                packageRoot,
                """
                        {
                          "exports": {
                            "./token": "./same.scss",
                            "./token.scss": "./other.scss"
                          }
                        }
                        """
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compileFile(root, "@use \"pkg:demo/token\";")
        );
        assertTrue(failure.getMessage().contains(
                "Unable to determine which of multiple potential resolutions"
        ));
    }

    /// Selects import-only siblings for legacy imports but not modules.
    @Test
    void selectsImportOnlyPackageEntries(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeManifest(packageRoot, "{\"sass\":\"./entry.scss\"}");
        writeStylesheet(packageRoot.resolve("entry.scss"), ".picked-module {v: 1}");
        writeStylesheet(
                packageRoot.resolve("entry.import.scss"),
                ".picked-import {v: 1}"
        );

        var moduleCss = compileFile(root, "@use \"pkg:demo\";");
        assertTrue(moduleCss.contains(".picked-module"));
        assertFalse(moduleCss.contains(".picked-import"));

        var importCss = compileFile(root, "@import \"pkg:demo\";");
        assertTrue(importCss.contains(".picked-import"));
        assertFalse(importCss.contains(".picked-module"));
    }

    /// Uses the configured entry-point directory for URL-less string roots.
    @Test
    void usesEntrypointDirectoryWithoutContainingUrl(
            @TempDir Path directory
    ) throws Exception {
        var packageRoot = packageRoot(directory, "demo");
        writeManifest(packageRoot, "{\"sass\":\"./entry.scss\"}");
        writeStylesheet(packageRoot.resolve("entry.scss"), ".picked-entry {v: 1}");

        var options = options(new SassNodePackageImporter(directory));
        var result = new SassCompiler().compile(
                SassSource.fromString("@use \"pkg:demo\";", Syntax.SCSS),
                CssTarget.DEFAULT,
                options
        );
        assertTrue(result.output().contains(".picked-entry"));
    }

    /// Reports malformed package URLs and manifests as Sass failures.
    @Test
    void reportsPackageContractFailures(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeStylesheet(packageRoot.resolve("index.scss"), ".unused {v: 1}");

        writeManifest(packageRoot, "{");
        var malformed = assertThrows(
                SassCompilationException.class,
                () -> compileFile(root, "@use \"pkg:demo\";")
        );
        assertTrue(malformed.getMessage().contains("Failed to parse"));

        writeManifest(packageRoot, "{}");
        for (var request : List.of("pkg:/demo", "pkg:demo?query", "pkg:demo#fragment")) {
            var failure = assertThrows(
                    SassCompilationException.class,
                    () -> compileFile(
                            root,
                            "@use \"" + request + "\" as package;"
                    )
            );
            assertTrue(
                    failure.getMessage().startsWith("A pkg: URL"),
                    failure.getMessage()
            );
        }
    }

    /// Reports invalid export maps, targets, and output extensions.
    @Test
    void reportsInvalidPackageExports(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        writeStylesheet(packageRoot.resolve("entry.scss"), ".unused {v: 1}");

        assertExportFailure(
                root,
                packageRoot,
                "{\"exports\":{\".\":\"entry.scss\"}}",
                "must be a path relative"
        );
        assertExportFailure(
                root,
                packageRoot,
                "{\"exports\":{\".\":\"./entry.js\"}}",
                "which is not a '.scss', '.sass', or '.css' file"
        );
        assertExportFailure(
                root,
                packageRoot,
                "{\"exports\":{\".\":true}}",
                "Invalid 'exports' value"
        );
        assertExportFailure(
                root,
                packageRoot,
                "{\"exports\":{\".\":\"./entry.scss\",\"sass\":\"./entry.scss\"}}",
                "can not have both conditions and paths"
        );
    }

    /// Records canonical package files as loaded and source-map URLs.
    @Test
    void recordsCanonicalPackageUrls(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("project");
        var packageRoot = packageRoot(root, "demo");
        var entry = packageRoot.resolve("entry.scss");
        writeManifest(packageRoot, "{\"sass\":\"./entry.scss\"}");
        writeStylesheet(entry, ".picked-entry {v: 1}");
        var source = writeStylesheet(
                root.resolve("src").resolve("main.scss"),
                "@use \"pkg:demo\";"
        );
        var options = new CompileOptions(
                true,
                List.of(),
                null,
                List.of(new SassNodePackageImporter(root.resolve("unrelated")))
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(source),
                CssTarget.DEFAULT,
                options
        );
        var canonicalEntry = entry.toRealPath().toUri();
        assertTrue(result.loadedUrls().contains(canonicalEntry));
        assertTrue(result.sourceMap().json().contains(
                canonicalEntry.toASCIIString()
        ));
    }

    /// Asserts one invalid exports manifest.
    ///
    /// @param root the project root
    /// @param packageRoot the installed package root
    /// @param manifest the package manifest JSON
    /// @param message the expected failure fragment
    private static void assertExportFailure(
            Path root,
            Path packageRoot,
            String manifest,
            String message
    ) throws Exception {
        writeManifest(packageRoot, manifest);
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compileFile(root, "@use \"pkg:demo\";")
        );
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }

    /// Compiles one file root with a deliberately unrelated importer fallback.
    ///
    /// @param root the project root containing node_modules
    /// @param sourceText the root SCSS
    /// @return generated CSS
    private static String compileFile(Path root, String sourceText)
            throws Exception {
        var source = writeStylesheet(
                root.resolve("src").resolve("main.scss"),
                sourceText
        );
        return new SassCompiler().compile(
                SassSource.fromFile(source),
                CssTarget.DEFAULT,
                options(new SassNodePackageImporter(
                        root.resolve("unrelated")
                ))
        ).output();
    }

    /// Creates compile options containing one package importer.
    ///
    /// @param importer the package importer
    /// @return compile options without source maps
    private static CompileOptions options(SassImporter importer) {
        return new CompileOptions(
                false,
                List.of(),
                null,
                List.of(importer)
        );
    }

    /// Creates and returns an installed package directory.
    ///
    /// @param projectRoot the ancestor containing node_modules
    /// @param packageName the slash-separated package name
    /// @return the package root
    private static Path packageRoot(Path projectRoot, String packageName)
            throws IOException {
        var result = projectRoot.resolve("node_modules");
        for (var segment : packageName.split("/")) {
            result = result.resolve(segment);
        }
        Files.createDirectories(result);
        return result;
    }

    /// Writes a package manifest.
    ///
    /// @param packageRoot the package root
    /// @param json the complete JSON document
    private static void writeManifest(Path packageRoot, String json)
            throws IOException {
        Files.createDirectories(packageRoot);
        Files.writeString(
                packageRoot.resolve("package.json"),
                json,
                java.nio.charset.StandardCharsets.UTF_8
        );
    }

    /// Writes a UTF-8 stylesheet and returns its path.
    ///
    /// @param path the stylesheet path
    /// @param contents the stylesheet text
    /// @return {@code path}
    private static Path writeStylesheet(Path path, String contents)
            throws IOException {
        @Nullable Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                path,
                contents,
                java.nio.charset.StandardCharsets.UTF_8
        );
        return path;
    }
}
