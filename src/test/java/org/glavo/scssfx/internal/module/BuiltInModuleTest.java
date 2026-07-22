// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies built-in Sass modules and their module-system integration.
@NotNullByDefault
final class BuiltInModuleTest {
    /// Evaluates the initial math, list, and map module API surface.
    @Test
    void evaluatesBuiltInModulesWithoutLoadingStylesheets() throws Exception {
        var result = compile(
                """
                        @use "sass:math";
                        @use "sass:math" as alternative;
                        @use "sass:list";
                        @use "sass:map";

                        $theme: (
                          spacing: 8px,
                          colors: (
                            primary: #036
                          )
                        );
                        $updated: map.set($theme, spacing, math.div(3rem, 2));
                        $without-spacing: map.remove($updated, spacing);
                        $joined: list.join([a], [b], comma, true);
                        $slashed: list.slash(1px, 2px);
                        $merged: map.deep-merge((tokens: (first: 1)), (tokens: (second: 2)));
                        $trimmed: map.deep-remove($merged, tokens, first);

                        .card {
                          width: map.get($updated, spacing);
                          color: map.get($theme, colors, primary);
                          round: math.round(-1.5);
                          pi: math.$pi;
                          unitless: math.is-unitless(math.$pi);
                          joined: $joined;
                          item: list.nth($joined, 2);
                          count: list.length($joined);
                          slashed: $slashed;
                          compatible: math.compatible(1in, 96px);
                          bracketed: list.is-bracketed($joined);
                          contains: map.has-key($theme, colors, primary);
                          removed: map.has-key($without-spacing, spacing);
                          limit: alternative.max(3, 2);
                          depth: map.get($trimmed, tokens, second);
                        }
                        """
        );

        assertEquals(
                """
                        .card {
                          width: 1.5rem;
                          color: #036;
                          round: -2;
                          pi: 3.141592653589793;
                          unitless: true;
                          joined: [a, b];
                          item: b;
                          count: 2;
                          slashed: 1px / 2px;
                          compatible: true;
                          bracketed: true;
                          contains: true;
                          removed: false;
                          limit: 3;
                          depth: 2;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Forwards filtered built-in functions while excluding built-in URLs from the load result.
    @Test
    void forwardsBuiltInModulesWithoutAddingBuiltInUrls(@TempDir Path directory) throws Exception {
        var facade = Files.writeString(
                directory.resolve("_facade.scss"),
                "@forward \"sass:math\" as safe-* show safe-round;"
        );
        var entry = Files.writeString(
                directory.resolve("entry.scss"),
                """
                        @use "facade";

                        .value {
                          order: facade.safe-round(-1.5);
                        }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(entry),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        .value {
                          order: -2;
                        }""",
                result.output()
        );
        assertEquals(
                Set.of(entry.toAbsolutePath().normalize().toUri(), facade.toRealPath().toUri()),
                result.loadedUrls()
        );
    }

    /// Rejects configuration owned directly by a built-in use or forward directive.
    @Test
    void rejectsDirectBuiltInConfigurations() {
        assertEquals(
                "Built-in modules can't be configured.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile("@use \"sass:math\" with ($pi: 4);")
                ).getMessage()
        );
        assertEquals(
                "Built-in modules can't be configured.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile("@forward \"sass:math\" with ($pi: 4);")
                ).getMessage()
        );
        assertEquals(
                "Can't find stylesheet to import.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile("@use \"sass:unknown\";")
                ).getMessage()
        );
    }

    /// Retains the outer unused-configuration diagnostic through a plain forward.
    @Test
    void diagnosesInheritedConfigurationAfterPlainBuiltInForward(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_facade.scss"),
                "@forward \"sass:math\";"
        );
        var entry = Files.writeString(
                directory.resolve("entry.scss"),
                "@use \"facade\" with ($pi: 4);"
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(entry),
                        CssTarget.DEFAULT
                )
        );
        assertEquals(
                "This variable was not declared with !default in the @used module.",
                failure.getMessage()
        );
    }

    /// Compiles one SCSS string source with the expanded CSS target.
    ///
    /// @param source the source text to compile
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static CompileResult<String> compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
    }
}
