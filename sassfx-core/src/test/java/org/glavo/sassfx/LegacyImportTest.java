// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies end-to-end legacy Sass import behavior.
@NotNullByDefault
final class LegacyImportTest {
    /// Executes an imported partial in the caller's variable and callable scope.
    @Test
    void sharesScopeWithImportedStylesheets(@TempDir Path directory) throws Exception {
        var partial = Files.writeString(
                directory.resolve("_tokens.scss"),
                """
                        .from-import { color: $input; }
                        $output: blue;
                        @mixin imported { border-color: $input; }
                        """
        );
        var root = Files.writeString(
                directory.resolve("main.scss"),
                """
                        $input: red;
                        @import "tokens";
                        .after {
                          color: $output;
                          @include imported;
                        }
                        """
        );

        var result = new SassCompiler().compile(SassSource.fromFile(root), CssTarget.DEFAULT);

        assertEquals(
                """
                        .from-import {
                          color: red;
                        }

                        .after {
                          color: blue;
                          border-color: red;
                        }""",
                result.output()
        );
        assertTrue(result.loadedUrls().contains(partial.toRealPath().toUri()));
        assertEquals("import", result.diagnostics().get(0).code());
    }

    /// Re-evaluates a stylesheet at every legacy import occurrence.
    @Test
    void repeatsLegacyImportExecution(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_increment.scss"),
                """
                        .item { order: $count; }
                        $count: $count + 1 !global;
                        """
        );
        var root = Files.writeString(
                directory.resolve("main.scss"),
                """
                        $count: 0;
                        @import "increment";
                        @import "increment";
                        .result { order: $count; }
                        """
        );

        var result = new SassCompiler().compile(SassSource.fromFile(root), CssTarget.DEFAULT);

        assertEquals(
                """
                        .item {
                          order: 0;
                        }

                        .item {
                          order: 1;
                        }

                        .result {
                          order: 2;
                        }""",
                result.output()
        );
        assertEquals(2, result.diagnostics().size());
    }

    /// Resolves nested imports relative to the imported stylesheet.
    @Test
    void resolvesNestedRelativeImports(@TempDir Path directory) throws Exception {
        var nested = Files.createDirectories(directory.resolve("nested"));
        Files.writeString(nested.resolve("_leaf.scss"), ".leaf { color: green; }");
        Files.writeString(nested.resolve("_entry.scss"), "@import \"leaf\";");
        var root = Files.writeString(directory.resolve("main.scss"), "@import \"nested/entry\";");

        var result = new SassCompiler().compile(SassSource.fromFile(root), CssTarget.DEFAULT);

        assertEquals(".leaf {\n  color: green;\n}", result.output());
        assertEquals(2, result.diagnostics().size());
    }

    /// Supports bare legacy import URLs in the indented Sass syntax.
    @Test
    void importsFromIndentedSass(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_tokens.sass"), ".token\n  color: teal\n");
        var root = Files.writeString(
                directory.resolve("main.sass"),
                "@import tokens\n"
        );

        var result = new SassCompiler().compile(SassSource.fromFile(root), CssTarget.DEFAULT);

        assertEquals(".token {\n  color: teal;\n}", result.output());
    }

    /// Prefers an import-only partial over the ordinary stylesheet.
    @Test
    void prefersImportOnlyStylesheets(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_theme.scss"), ".theme { source: ordinary; }");
        Files.writeString(directory.resolve("_theme.import.scss"), ".theme { source: import-only; }");
        var root = Files.writeString(directory.resolve("main.scss"), "@import \"theme\";");

        var result = new SassCompiler().compile(SassSource.fromFile(root), CssTarget.DEFAULT);

        assertEquals(".theme {\n  source: import-only;\n}", result.output());
    }

    /// Searches configured load paths after the containing directory.
    @Test
    void resolvesImportsFromLoadPaths(@TempDir Path directory) throws Exception {
        var loadPath = Files.createDirectory(directory.resolve("styles"));
        Files.writeString(loadPath.resolve("_shared.scss"), ".shared { color: purple; }");
        var root = Files.writeString(directory.resolve("main.scss"), "@import \"shared\";");

        var result = new SassCompiler().compile(
                SassSource.fromFile(root),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(loadPath))
        );

        assertEquals(".shared {\n  color: purple;\n}", result.output());
    }

    /// Rejects recursive import cycles with a source-associated failure.
    @Test
    void rejectsImportCycles(@TempDir Path directory) throws Exception {
        var first = Files.writeString(directory.resolve("first.scss"), "@import \"second\";");
        Files.writeString(directory.resolve("_second.scss"), "@import \"first\";");

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(SassSource.fromFile(first), CssTarget.DEFAULT)
        );

        assertEquals("This file is already being loaded.", failure.getMessage());
    }
}
