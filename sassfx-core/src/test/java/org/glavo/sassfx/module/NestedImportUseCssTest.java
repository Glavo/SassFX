// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.module;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies CSS from {@code @use} inside nested {@code @import} nests under the outer rule.
@NotNullByDefault
final class NestedImportUseCssTest {
    @Test
    void nestedImportUsesModuleCssUnderOuterSelector(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_used.scss"), """
                @use "sass:meta";
                in-used {parent: meta.inspect(&)}
                """);
        Files.writeString(directory.resolve("_imported.scss"), """
                @use "sass:meta";
                @use "used";
                in-imported {parent: meta.inspect(&)}
                """);
        Files.writeString(directory.resolve("input.scss"), """
                outer {@import "imported"}
                """);
        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(directory))
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        outer in-used {
                          parent: (in-used,);
                        }
                        outer in-imported {
                          parent: (outer in-imported,);
                        }""",
                css
        );
    }

    @Test
    void rootUseAndNestedImportBothEmitModuleCss(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_used.scss"), """
                in-used {x: y}
                """);
        Files.writeString(directory.resolve("_imported.scss"), """
                @use "used";
                in-imported {z: w}
                """);
        Files.writeString(directory.resolve("input.scss"), """
                @use "used";
                outer {@import "imported"}
                """);
        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(directory))
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        in-used {
                          x: y;
                        }

                        outer in-used {
                          x: y;
                        }
                        outer in-imported {
                          z: w;
                        }""",
                css
        );
    }
}
