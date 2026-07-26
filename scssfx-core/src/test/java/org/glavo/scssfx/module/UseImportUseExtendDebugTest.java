// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.module;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// {@code @use} of an importer that {@code @import}s a module which {@code @use}s
/// shared, alongside a sibling {@code @use} of shared — each copy is extended
/// independently (sass-spec use_into_use_and_use_into_import_into_use).
@NotNullByDefault
final class UseImportUseExtendDebugTest {
    @Test
    void importPathCopyAndUsePathCopyAreIsolated(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), "shared {x: y}");
        Files.writeString(directory.resolve("_imported.scss"), """
                @use "shared";
                in-imported {@extend shared}
                """);
        Files.writeString(directory.resolve("_importer.scss"), "@import \"imported\";");
        Files.writeString(directory.resolve("_used.scss"), """
                @use "shared";
                in-used {@extend shared}
                """);
        Files.writeString(directory.resolve("input.scss"), """
                @use "importer";
                @use "used";
                """);
        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        shared, in-imported {
                          x: y;
                        }

                        shared, in-used {
                          x: y;
                        }""",
                css
        );
    }

    /// Keeps sibling extensions isolated in used and imported diamond copies.
    @Test
    void importedDiamondDoesNotExposeSiblingExtensionProducts(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), "in-shared {x: y}");
        Files.writeString(directory.resolve("_left.scss"), """
                @use "shared";
                left-extendee {@extend in-shared}
                left-extender {@extend right-extendee !optional}
                """);
        Files.writeString(directory.resolve("_right.scss"), """
                @use "shared";
                right-extendee {@extend in-shared}
                right-extender {@extend left-extendee !optional}
                """);
        Files.writeString(directory.resolve("_downstream.scss"), """
                @use "left";
                @use "right";
                """);
        Files.writeString(directory.resolve("_imported.scss"), "@use \"downstream\";");
        Files.writeString(directory.resolve("input.scss"), """
                @use "downstream";
                @import "downstream";
                @import "imported";
                """);

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        in-shared, right-extendee, left-extendee {
                          x: y;
                        }

                        in-shared, right-extendee, left-extendee {
                          x: y;
                        }

                        in-shared, right-extendee, left-extendee {
                          x: y;
                        }""",
                css
        );
    }
}
