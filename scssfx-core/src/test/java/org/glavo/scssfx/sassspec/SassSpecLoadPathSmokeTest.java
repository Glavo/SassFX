// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies suite-root load paths used by full upstream HRX materialization.
@NotNullByDefault
final class SassSpecLoadPathSmokeTest {
    /// Compiles a trailing-comma include resolved through a mounted suite path.
    @Test
    void resolvesMountedArchiveUtilsWithTrailingCommaArgs(@TempDir Path temporaryDirectory)
            throws Exception {
        Path suiteRoot = temporaryDirectory.resolve("suite");
        Path utils = suiteRoot.resolve("callable/arguments/mixin/_utils.scss");
        Path input = suiteRoot.resolve("callable/arguments/mixin/trailing_comma/positional/input.scss");
        Files.createDirectories(utils.getParent());
        Files.createDirectories(input.getParent());
        Files.writeString(
                utils,
                """
                        @use "sass:meta";

                        @mixin a($args...) {
                          b {
                            positional: meta.inspect($args);
                            named: meta.inspect(meta.keywords($args));
                          }
                        }
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                input,
                """
                        @use "callable/arguments/mixin/utils";
                        @include utils.a(1, );
                        """,
                StandardCharsets.UTF_8
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(input),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(suiteRoot))
        );
        assertEquals(
                """
                        b {
                          positional: (1,);
                          named: ();
                        }""",
                result.output()
        );
    }
}
