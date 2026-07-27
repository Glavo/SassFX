// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies {@code meta.load-css} loading, configuration, nesting, and caching.
@NotNullByDefault
final class MetaLoadCssTest {
    /// Injects loaded CSS without exposing module members.
    @Test
    void loadsCssWithoutExposingMembers(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_theme.scss"),
                """
                        $color: red !default;
                        .theme { color: $color; }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "sass:meta";
                        @include meta.load-css("theme");
                        .main { color: blue; }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .theme {
                          color: red;
                        }

                        .main {
                          color: blue;
                        }""",
                result.output()
        );
        assertEquals(2, result.loadedUrls().size());
    }

    /// Configures root defaults and nests loaded selectors under the include parent.
    @Test
    void configuresAndNestsLoadedCss(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_theme.scss"),
                """
                        $color: red !default;
                        code { color: $color; }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "sass:meta";
                        body.dark {
                          @include meta.load-css("theme", $with: (color: lime));
                        }
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        body.dark code {
                          color: lime;
                        }""",
                css
        );
    }

    /// Shares one evaluation with `@use` of the same canonical module.
    @Test
    void sharesEvaluationWithUse(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_counter.scss"),
                """
                        $n: 0 !default;
                        $n: $n + 1;
                        .count-#{$n} { order: $n; }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "sass:meta";
                        @use "counter";
                        @include meta.load-css("counter");
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        // Evaluation runs once, but CSS is emitted by both @use and load-css.
        assertEquals(2, css.split("\\.count-1", -1).length - 1);
        assertTrue(css.contains("order: 1"), css);
    }

    /// Rejects configuration for variables that are not defaults.
    @Test
    void rejectsUnusedConfiguration(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_theme.scss"), ".theme { color: red; }");
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "sass:meta";
                        @include meta.load-css("theme", $with: (missing: blue));
                        """
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("main.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals(
                "$missing was not declared with !default in the @used module.",
                failure.getMessage()
        );
    }
}
