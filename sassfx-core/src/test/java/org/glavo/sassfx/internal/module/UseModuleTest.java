// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies multi-file `@use` loading, namespaces, and CSS ordering.
@NotNullByDefault
final class UseModuleTest {
    /// Compiles a namespaced module with variables, functions, mixins, and CSS.
    @Test
    void compilesNamespacedUse(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("other.scss"),
                """
                        $var: red;
                        @function fn() { @return 1px; }
                        @mixin m { outline: 1px; }
                        .dep { margin: 1; }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "other" as o;
                        .main {
                          color: o.$var;
                          width: o.fn();
                          @include o.m;
                        }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .dep {
                          margin: 1;
                        }

                        .main {
                          color: red;
                          width: 1px;
                          outline: 1px;
                        }""",
                result.output()
        );
        assertEquals(2, result.loadedUrls().size());
    }

    /// Compiles `@use as *` and default namespaces.
    @Test
    void compilesAsStarAndDefaultNamespace(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("colors.scss"), "$c: blue;");
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "colors" as *;
                        a { color: $c; }
                        """
        );
        assertEquals(
                """
                        a {
                          color: blue;
                        }""",
                new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("main.scss")),
                        CssTarget.DEFAULT
                ).output()
        );

        Files.writeString(directory.resolve("_lib.scss"), "$x: 1;");
        Files.writeString(
                directory.resolve("default.scss"),
                """
                        @use "lib";
                        a { z-index: lib.$x; }
                        """
        );
        assertEquals(
                """
                        a {
                          z-index: 1;
                        }""",
                new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("default.scss")),
                        CssTarget.DEFAULT
                ).output()
        );
    }

    /// Loads an indented Sass module through a namespaced {@code @use}.
    @Test
    void compilesIndentedSassModule(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_theme.sass"),
                """
                        $color: red
                        =accent
                          color: $color
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "theme" as t;
                        .item {
                          @include t.accent;
                        }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .item {
                          color: red;
                        }""",
                result.output()
        );
    }

    /// Resolves modules through compile options load paths.
    @Test
    void resolvesLoadPaths(@TempDir Path directory) throws Exception {
        var lib = directory.resolve("lib");
        Files.createDirectories(lib);
        Files.writeString(lib.resolve("_tokens.scss"), "$gap: 8px;");
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "tokens" as t;
                        a { margin: t.$gap; }
                        """
        );
        var result = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT,
                CompileOptions.DEFAULT.withLoadPaths(List.of(lib))
        );
        assertEquals(
                """
                        a {
                          margin: 8px;
                        }""",
                result.output()
        );
    }

    /// Emits shared dependencies only once.
    @Test
    void loadsEachModuleOnce(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("a.scss"), ".a { z-index: 1; }");
        Files.writeString(directory.resolve("b.scss"), "@use \"a\" as a;");
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "a" as a;
                        @use "b" as b;
                        .main { z-index: 2; }
                        """
        );
        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(1, css.split("\\.a", -1).length - 1);
        assertTrue(css.contains(".main"));
    }

    /// Reports module loops, missing files, and illegal `@use` placement.
    @Test
    void reportsModuleErrors(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("a.scss"), "@use \"b\";");
        Files.writeString(directory.resolve("b.scss"), "@use \"a\";");
        Files.writeString(directory.resolve("loop.scss"), "@use \"a\";");
        var loop = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("loop.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertTrue(loop.getMessage().contains("Module loop"));

        Files.writeString(directory.resolve("missing.scss"), "@use \"nope\";");
        var missing = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("missing.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("Can't find stylesheet to import.", missing.getMessage());

        Files.writeString(
                directory.resolve("late.scss"),
                """
                        a { x: 1; }
                        @use "a";
                        """
        );
        var late = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("late.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertTrue(late.getMessage().contains("@use rules must be written before"));
    }

    /// Keeps private members out of exports.
    @Test
    void hidesPrivateMembers(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("priv.scss"),
                """
                        $-hidden: 1;
                        $shown: 2;
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "priv" as p;
                        a { z-index: p.$shown; }
                        """
        );
        assertEquals(
                """
                        a {
                          z-index: 2;
                        }""",
                new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("main.scss")),
                        CssTarget.DEFAULT
                ).output()
        );

        Files.writeString(
                directory.resolve("bad.scss"),
                """
                        @use "priv" as p;
                        a { z-index: p.$-hidden; }
                        """
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("bad.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals(
                "Private members can't be accessed from outside their modules.",
                failure.getMessage()
        );
    }
}
