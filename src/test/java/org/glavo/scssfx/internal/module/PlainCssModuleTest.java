// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.BssTarget;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.JavaFXCssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies plain-CSS parsing and CSS files loaded through the module system.
@NotNullByDefault
final class PlainCssModuleTest {
    /// Parses raw CSS roots without evaluating native functions as Sass built-ins.
    @Test
    void compilesPlainCssRoots() throws Exception {
        var source = """
                @charset "UTF-8";
                @page {
                  margin: 1cm;
                }
                @keyframes pulse {
                  from { opacity: 0; }
                  to { opacity: 1; }
                }
                .item {
                  color: rgb(1 2 3 / 50%);
                  width: min(10px, 20px);
                  --theme: calc(1px + 2%);
                }
                """;

        var expanded = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.CSS),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        @charset "UTF-8";
                        @page {
                          margin: 1cm;
                        }

                        @keyframes pulse {
                          from {
                            opacity: 0;
                          }
                          to {
                            opacity: 1;
                          }
                        }

                        .item {
                          color: rgb(1 2 3/50%);
                          width: 10px;
                          --theme: calc(1px + 2%);
                        }""",
                expanded
        );

        var compressed = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.CSS),
                new JavaFXCssTarget(
                        org.glavo.scssfx.JavaFXCompatibility.JAVA_FX_17,
                        OutputStyle.COMPRESSED
                )
        ).output();
        assertTrue(
                compressed.contains("@keyframes pulse{from{opacity:0}to{opacity:1}}"),
                compressed
        );
        assertTrue(compressed.contains("@page{margin:1cm}"), compressed);
        assertTrue(compressed.contains("width:10px"), compressed);
    }

    /// Loads CSS modules before caller CSS and retains their empty namespace.
    @Test
    void loadsCssModulesWithNamespace(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_theme.css"),
                """
                        @use "not-loaded.css";
                        .theme { color: rgb(1 2 3); }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "theme.css";
                        .main { color: blue; }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        @use "not-loaded.css";
                        .theme {
                          color: rgb(1 2 3);
                        }

                        .main {
                          color: blue;
                        }""",
                result.output()
        );
        assertEquals(2, result.loadedUrls().size());

        Files.writeString(
                directory.resolve("member.scss"),
                "@use \"theme.css\"; .x { color: theme.$missing; }"
        );
        var missing = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("member.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("Undefined variable.", missing.getMessage());
    }

    /// Forwards and aliases one canonical CSS module without duplicating its CSS.
    @Test
    void forwardsAndDeduplicatesCssModules(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("base.css"), ".base { color: red; }");
        Files.writeString(
                directory.resolve("facade.scss"),
                "@forward \"base.css\" as theme-* show missing;"
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "base.css" as first;
                        @use "base" as second;
                        @use "facade";
                        .main { color: blue; }
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(1, css.split("\\.base", -1).length - 1);
        assertTrue(css.indexOf(".base") < css.indexOf(".main"));
    }

    /// Rejects configuration because a CSS module has no configurable members.
    @Test
    void rejectsCssModuleConfiguration(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("theme.css"), ".theme { color: red; }");
        Files.writeString(
                directory.resolve("main.scss"),
                "@use \"theme.css\" with ($color: red);"
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("main.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals(
                "$color was not declared with !default in the @used module.",
                failure.getMessage()
        );
    }

    /// Rejects Sass-only expressions, rules, imports, and nested declarations.
    @Test
    void rejectsSassOnlyPlainCssSyntax() {
        for (var source : new String[]{
                "$color: red;",
                ".item { color: $color; }",
                ".item { color: #{$color}; }",
                "// comment\n.item { color: red; }",
                "@mixin theme {}",
                "@function theme() {}",
                ".item { color: unit(1px); }",
                "@import \"a.css\", \"b.css\";",
                ".item { font: { size: 1rem; } }",
                "%placeholder { color: red; }"
        }) {
            assertThrows(
                    SassCompilationException.class,
                    () -> new SassCompiler().compile(
                            SassSource.fromString(source, Syntax.CSS),
                            CssTarget.DEFAULT
                    )
            );
        }
    }

    /// Retains native CSS nesting and parent selectors without Sass flattening.
    @Test
    void compilesNativeCssNesting() throws Exception {
        var css = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                .parent {
                                  color: blue;
                                  .child {
                                    color: red;
                                    @media screen {
                                      opacity: 0.5;
                                    }
                                  }
                                  &:hover {
                                    color: green;
                                  }
                                }
                                """,
                        Syntax.CSS
                ),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        .parent {
                          color: blue;
                          .child {
                            color: red;
                            @media screen {
                              opacity: 0.5;
                            }
                          }
                          &:hover {
                            color: green;
                          }
                        }""",
                css
        );
    }

    /// Emits comments that precede `@use` before the loaded module's CSS.
    @Test
    void emitsCommentsBeforeUsedModuleCss(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("theme.css"), ".theme { color: red; }");
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        /* before */
                        @use "theme.css";
                        .main { color: blue; }
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        /* before */
                        .theme {
                          color: red;
                        }

                        .main {
                          color: blue;
                        }""",
                css
        );
    }

    /// Preserves custom function at-rules and dollar signs that are string data.
    @Test
    void acceptsNativePlainCssTokens() throws Exception {
        var source = """
                @function --theme() {}
                .item {
                  content: "$price";
                  width: calc(1px + 2%);
                }
                """;

        var css = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.CSS),
                CssTarget.DEFAULT
        ).output();
        assertTrue(css.contains("@function --theme() {}"), css);
        assertTrue(css.contains("content: \"$price\";"), css);
        assertTrue(css.contains("width: calc(1px + 2%);"), css);
    }

    /// Loads simple CSS declarations for the pure-Java BSS target.
    @Test
    void loadsCssModuleForBss(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("theme.css"),
                "Pane { -fx-opacity: 0.5; }"
        );
        Files.writeString(directory.resolve("main.scss"), "@use \"theme.css\";");

        var buffer = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                BssTarget.DEFAULT
        ).output();
        assertTrue(buffer.isReadOnly());
        assertEquals(0, buffer.position());
        assertTrue(buffer.remaining() > 0);
    }
}
