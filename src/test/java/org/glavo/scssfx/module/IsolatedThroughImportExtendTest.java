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

/// Verifies {@code @extend} isolation when the same module is both {@code @use}d
/// and re-emitted through {@code @import}.
@NotNullByDefault
final class IsolatedThroughImportExtendTest {
    @Test
    void eachCopyReceivesOnlyItsOwnExtends(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), """
                .in-shared {a: b}
                """);
        Files.writeString(directory.resolve("_used-by-input.scss"), """
                @use "shared";
                .in-used-by-input {@extend .in-shared}
                """);
        Files.writeString(directory.resolve("_used-by-imported.scss"), """
                @use "shared";
                .in-used-by-imported {@extend .in-shared}
                """);
        Files.writeString(directory.resolve("_imported.scss"), """
                @use "used-by-imported";
                """);
        Files.writeString(directory.resolve("input.scss"), """
                @use "used-by-input";
                @import "imported";
                """);
        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        .in-shared, .in-used-by-input {
                          a: b;
                        }

                        .in-shared, .in-used-by-imported {
                          a: b;
                        }""",
                css
        );
    }
}
