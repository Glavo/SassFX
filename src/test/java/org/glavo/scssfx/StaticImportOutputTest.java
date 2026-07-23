// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies textual and binary backend behavior for static CSS imports.
@NotNullByDefault
final class StaticImportOutputTest {
    /// Moves late top-level imports before ordinary CSS while retaining source order.
    @Test
    void serializesExpandedStaticImportsInCssOrder() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                $asset: "theme.css";
                                @import url("quoted.css");
                                .before { color: red; }
                                @import "theme.css";
                                @import url(#{$asset}) screen;
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        @import url("quoted.css");
                        @import "theme.css";
                        @import url(theme.css) screen;
                        .before {
                          color: red;
                        }""",
                result.output()
        );
        assertEquals(0, result.diagnostics().size());
    }

    /// Places root imports before CSS emitted by used modules.
    @Test
    void ordersImportsBeforeModuleCss(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_dependency.scss"), ".dependency { color: red; }");
        var root = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "dependency";
                        @import "theme.css";
                        .root { color: blue; }
                        """
        );

        var result = new SassCompiler().compile(SassSource.fromFile(root), CssTarget.DEFAULT);

        assertEquals(
                """
                        @import "theme.css";
                        .dependency {
                          color: red;
                        }

                        .root {
                          color: blue;
                        }""",
                result.output()
        );
    }

    /// Serializes static imports for JavaFX CSS without loading JavaFX classes.
    @Test
    void serializesJavaFxCssImports() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString("@import \"theme.css\" screen;", Syntax.SCSS),
                new JavaFXCssTarget(JavaFXCompatibility.JAVA_FX_17, OutputStyle.COMPRESSED)
        );

        assertEquals("@import \"theme.css\" screen;", result.output());
    }

    /// Rejects imports explicitly when the BSS backend cannot encode them.
    @Test
    void rejectsStaticImportsForBss() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString("@import \"theme.css\";", Syntax.SCSS),
                        BssTarget.DEFAULT
                )
        );

        assertEquals("BSS output doesn't support @import rules.", failure.getMessage());
    }
}
