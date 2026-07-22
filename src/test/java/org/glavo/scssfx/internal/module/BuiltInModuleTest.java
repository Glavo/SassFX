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

    /// Evaluates the first string, legacy color, and static meta module APIs.
    @Test
    void evaluatesStandardBuiltInModuleApisWithoutLoadingStylesheets() throws Exception {
        var result = compile(
                """
                        @use "sass:color";
                        @use "sass:map";
                        @use "sass:meta";
                        @use "sass:string";

                        @function collect($args...) {
                          @return meta.keywords($args);
                        }

                        @function argument-type($args...) {
                          $ignored: meta.keywords($args);
                          @return meta.type-of($args);
                        }

                        $values: collect($tone: #123456, $gap: 2px);

                        .card {
                          inserted: string.insert("A\uD83D\uDE00B", "X", 3);
                          negative: string.insert("ab", "X", -2);
                          slice: string.slice("A\uD83D\uDE00B", -2, -1);
                          zero-start: string.length(string.slice("abc", 0));
                          past-end: string.length(string.slice("abc", 6));
                          position: string.index("A\uD83D\uDE00B", "\uD83D\uDE00");
                          split: string.split("a,b,c", ",", 1);
                          tone: map.get($values, tone);
                          gap: map.get($values, gap);
                          arglist: argument-type($unused: true);
                          inspected: meta.inspect($values);
                          red: color.red(map.get($values, tone));
                          alpha: color.alpha(rgba(0, 0, 0, 0.25));
                          same: color.same(#123, #112233);
                          filter: color.opacity(0.5);
                        }
                        """
        );

        assertEquals(
                """
                        @charset "UTF-8";
                        .card {
                          inserted: "A\uD83D\uDE00XB";
                          negative: "aXb";
                          slice: "\uD83D\uDE00B";
                          zero-start: 3;
                          past-end: 0;
                          position: 2;
                          split: ["a", "b,c"];
                          tone: #123456;
                          gap: 2px;
                          arglist: arglist;
                          inspected: (tone: #123456, gap: 2px);
                          red: 18;
                          alpha: 0.25;
                          same: true;
                          filter: opacity(0.5);
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Reports invalid static meta and string module arguments precisely.
    @Test
    void rejectsInvalidStandardBuiltInModuleArguments() {
        assertEquals(
                "$args: 1 is not an argument list.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(
                                "@use \"sass:meta\"; a { value: meta.keywords(1); }"
                        )
                ).getMessage()
        );
        assertEquals(
                "$limit: Must be 1 or greater, was 0.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(
                                "@use \"sass:string\"; a { value: string.split(\"a\", \"\", 0); }"
                        )
                ).getMessage()
        );
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
