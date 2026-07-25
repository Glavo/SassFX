// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies evaluation, consumption, diagnostics, and caching for `@use` configuration.
@NotNullByDefault
final class ModuleConfigurationTest {
    /// Evaluates configuration expressions in the caller and makes all configured
    /// values visible to later module defaults.
    @Test
    void evaluatesCallerValuesAndLaterDefaults(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("settings.scss"),
                """
                        $first: 1 !default;
                        $second: $first + 1 !default;
                        $third: $second + 1 !default;
                        .settings {
                          first: $first;
                          second: $second;
                          third: $third;
                        }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        $base: 4;
                        @use "settings" as settings with (
                          $first: $base + 1,
                          $second: $base + 2,
                        );
                        .main { value: settings.$third; }
                        """
        );

        assertEquals(
                """
                        .settings {
                          first: 5;
                          second: 6;
                          third: 7;
                        }

                        .main {
                          value: 7;
                        }""",
                compile(entry)
        );
    }

    /// Consumes a null configuration while retaining the module's declared default.
    @Test
    void nullConfigurationRetainsDefault(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("theme.scss"),
                """
                        $color: red !default;
                        .theme { color: $color; }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "theme" as theme with ($color: null);
                        .main { color: theme.$color; }
                        """
        );

        assertEquals(
                """
                        .theme {
                          color: red;
                        }

                        .main {
                          color: red;
                        }""",
                compile(entry)
        );
    }

    /// Skips the original default expression after installing a non-null override.
    @Test
    void nonNullConfigurationSkipsDefaultExpression(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("configured.scss"),
                "$value: $undefined-value !default;"
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "configured" as configured with ($value: accepted);
                        .main { value: configured.$value; }
                        """
        );

        assertEquals(
                """
                        .main {
                          value: accepted;
                        }""",
                compile(entry)
        );
    }

    /// Rejects unknown names, non-default variables, and duplicate configuration entries.
    @Test
    void rejectsInvalidConfigurationEntries(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("known.scss"), "$known: 1 !default;");
        var unknownEntry = Files.writeString(
                directory.resolve("unknown.scss"),
                "@use \"known\" with ($missing: 2);"
        );
        assertEquals(
                "This variable was not declared with !default in the @used module.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(unknownEntry)
                ).getMessage()
        );

        Files.writeString(directory.resolve("fixed.scss"), "$fixed: 1;");
        var fixedEntry = Files.writeString(
                directory.resolve("non-default.scss"),
                "@use \"fixed\" with ($fixed: 2);"
        );
        assertEquals(
                "This variable was not declared with !default in the @used module.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(fixedEntry)
                ).getMessage()
        );

        var duplicateEntry = Files.writeString(
                directory.resolve("duplicate.scss"),
                "@use \"known\" with ($known: 2, $known: 3);"
        );
        assertEquals(
                "The same variable may only be configured once.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(duplicateEntry)
                ).getMessage()
        );
    }

    /// Rejects an explicit configuration after the same canonical module was loaded.
    @Test
    void rejectsConfigurationAfterPriorLoad(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("theme.scss"), "$color: red !default;");
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "theme" as first;
                        @use "theme" as second with ($color: blue);
                        """
        );

        assertEquals(
                "This module was already loaded, so it can't be configured using \"with\".",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(entry)
                ).getMessage()
        );
    }

    /// Reuses a previously configured module without re-evaluating or duplicating its CSS.
    @Test
    void reusesInitiallyConfiguredModuleAndCss(@TempDir Path directory) throws Exception {
        var module = Files.writeString(
                directory.resolve("theme.scss"),
                """
                        $color: red !default;
                        .theme { color: $color; }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "theme" as first with ($color: blue);
                        @use "theme" as second;
                        .main {
                          color: first.$color;
                          background: second.$color;
                        }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(entry),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .theme {
                          color: blue;
                        }

                        .main {
                          color: blue;
                          background: blue;
                        }""",
                result.output()
        );
        assertEquals(
                Set.of(
                        entry.toAbsolutePath().normalize().toUri(),
                        module.toRealPath().toUri()
                ),
                result.loadedUrls()
        );
        assertEquals(1, result.output().split("\\.theme", -1).length - 1);
    }

    /// Compiles one filesystem entrypoint and returns expanded CSS.
    ///
    /// @param entry the root stylesheet
    /// @return expanded CSS
    /// @throws Exception if reading or compilation fails
    private static String compile(Path entry) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromFile(entry),
                CssTarget.DEFAULT
        ).output();
    }
}
