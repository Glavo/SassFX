// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.module;

import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies {@code @use} inside {@code @import}ed files isolates namespaces and
/// re-emits already-loaded module CSS.
@NotNullByDefault
final class UseInsideImportNamespaceTest {
    @Test
    void sharedModuleUsedByRootAndImportDuplicatesCss(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), """
                a {b: c}
                """);
        Files.writeString(directory.resolve("_imported.scss"), """
                @use "shared";
                """);
        Files.writeString(directory.resolve("input.scss"), """
                @use "shared";
                @import "imported";
                """);
        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(directory))
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        a {
                          b: c;
                        }

                        a {
                          b: c;
                        }""",
                css
        );
    }
}
