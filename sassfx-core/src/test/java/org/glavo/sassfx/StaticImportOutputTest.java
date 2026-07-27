// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

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

    /// Evaluates structured supports modifiers and preserves surrounding modifiers.
    @Test
    void evaluatesStaticImportSupportsModifiers() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                $display: grid;
                                $theme-name: theme;
                                $theme-value: dark;
                                @import "theme.css" layer(theme)
                                        supports(display: $display) screen;
                                @import "custom.css"
                                        supports((display: $display)
                                        and selector(.button)
                                        and (--#{$theme-name}: #{$theme-value}));
                                @import "quoted.css"
                                        supports("--theme": $theme-value);
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        @import "theme.css" layer(theme) supports(display: grid) screen;
                        @import "custom.css" supports((display: grid) and selector(.button) and \
                        (--theme: dark));
                        @import "quoted.css" supports(--theme: dark);""",
                result.output()
        );
    }

    /// Evaluates static-import supports modifiers in indented Sass syntax.
    @Test
    void evaluatesIndentedStaticImportSupportsModifiers() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        "$display: grid\n@import \"theme.css\" supports(display: $display)\n",
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                "@import \"theme.css\" supports(display: grid);",
                result.output()
        );
    }

    /// Rejects a bare interpolation where an import supports declaration is required.
    @Test
    void rejectsBareInterpolatedStaticImportSupportsCondition() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "$condition: \"\"; @import \"theme.css\" supports(#{$condition});",
                                Syntax.SCSS
                        ),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("expected \":\".", failure.getMessage());
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

    /// Serializes unconditional imports for both JavaFX compatibility levels.
    @Test
    void serializesJavaFxCssImports() throws Exception {
        var source = SassSource.fromString("@import \"theme.css\";", Syntax.SCSS);
        var compiler = new SassCompiler();
        var javaFx17 = compiler.compile(
                source,
                new JavaFXCssTarget(JavaFXTarget.JAVAFX17, OutputStyle.COMPRESSED)
        ).output();
        var javaFx27 = compiler.compile(
                source,
                new JavaFXCssTarget(JavaFXTarget.JAVAFX27, OutputStyle.COMPRESSED)
        ).output();

        assertEquals("@import \"theme.css\";", javaFx17);
        assertEquals("@import \"theme.css\";", javaFx27);
    }

    /// Rejects conditional imports whose semantics differ on JavaFX 17.
    @Test
    void rejectsConditionalImportsForJavaFx17() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "@import \"theme.css\" (prefers-color-scheme: dark);",
                                Syntax.SCSS
                        ),
                        new JavaFXCssTarget(
                                JavaFXTarget.JAVAFX17,
                                OutputStyle.COMPRESSED
                        )
                )
        );

        assertEquals(
                "JavaFX 17 CSS supports only unconditional @import rules.",
                failure.getMessage()
        );
    }

    /// Serializes JavaFX 27 media conditions after the import URL.
    @Test
    void serializesConditionalImportsForJavaFx27() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        "@import \"theme.css\" (prefers-color-scheme: dark);",
                        Syntax.SCSS
                ),
                new JavaFXCssTarget(JavaFXTarget.JAVAFX27, OutputStyle.COMPRESSED)
        );

        assertEquals(
                "@import \"theme.css\" (prefers-color-scheme: dark);",
                result.output()
        );
    }

    /// Rejects conditional imports for a BSS target predating JavaFX 27.
    @Test
    void rejectsConditionalImportsForLegacyBss() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "@import \"theme.css\" supports(display: grid);",
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "JavaFX 17 CSS supports only unconditional @import rules.",
                failure.getMessage()
        );
    }
}
