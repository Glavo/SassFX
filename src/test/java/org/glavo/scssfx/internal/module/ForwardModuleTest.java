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

/// Verifies forward export views, configuration flow, conflicts, and module graphs.
@NotNullByDefault
final class ForwardModuleTest {
    /// Re-exports public variables, functions, and mixins while retaining dependency CSS.
    @Test
    void forwardsPublicMembersAndCss(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("base.scss"),
                """
                        $color: red;
                        $-secret: black;
                        @function size() { @return 2px; }
                        @function -secret-function() { @return 3px; }
                        @mixin border { border-width: 4px; }
                        @mixin -secret-mixin { border-style: hidden; }
                        .base { color: $color; }
                        """
        );
        Files.writeString(directory.resolve("facade.scss"), "@forward \"base\";");
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "facade" as facade;
                        .main {
                          color: facade.$color;
                          width: facade.size();
                          @include facade.border;
                        }
                        """
        );

        assertEquals(
                """
                        .base {
                          color: red;
                        }

                        .main {
                          color: red;
                          width: 2px;
                          border-width: 4px;
                        }""",
                compile(entry)
        );
    }

    /// Keeps each kind of private member hidden through a forwarding facade.
    @Test
    void hidesPrivateForwardedMembers(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("base.scss"),
                """
                        $-secret: black;
                        @function -secret-function() { @return 3px; }
                        @mixin -secret-mixin { border-style: hidden; }
                        """
        );
        Files.writeString(directory.resolve("facade.scss"), "@forward \"base\";");

        var variableEntry = Files.writeString(
                directory.resolve("private-variable.scss"),
                """
                        @use "facade" as facade;
                        a { color: facade.$-secret; }
                        """
        );
        assertPrivateFailure(
                variableEntry,
                "Private members can't be accessed from outside their modules."
        );

        var functionEntry = Files.writeString(
                directory.resolve("private-function.scss"),
                """
                        @use "facade" as facade;
                        a { width: facade.-secret-function(); }
                        """
        );
        assertPrivateFailure(
                functionEntry,
                "Private members can't be accessed from outside their modules."
        );

        var mixinEntry = Files.writeString(
                directory.resolve("private-mixin.scss"),
                """
                        @use "facade" as facade;
                        a { @include facade.-secret-mixin; }
                        """
        );
        assertPrivateFailure(mixinEntry, "Undefined mixin.");
    }

    /// Does not make forwarded members visible while evaluating the forwarding module.
    @Test
    void doesNotExposeForwardedMembersInsideForwarder(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("base.scss"), "$value: 1;");
        Files.writeString(
                directory.resolve("facade.scss"),
                """
                        @forward "base";
                        .facade { value: $value; }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                "@use \"facade\";"
        );

        assertEquals(
                "Undefined variable.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(entry)
                ).getMessage()
        );
    }

    /// Gives local variables, functions, and mixins precedence over forwarded exports.
    @Test
    void localMembersOverrideForwardedExports(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("base.scss"),
                """
                        $value: base;
                        @function value() { @return base; }
                        @mixin paint { source: base; }
                        """
        );
        Files.writeString(
                directory.resolve("facade.scss"),
                """
                        @forward "base";
                        $value: local;
                        @function value() { @return local; }
                        @mixin paint { source: local; }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "facade" as facade;
                        .main {
                          variable: facade.$value;
                          function: facade.value();
                          @include facade.paint;
                        }
                        """
        );

        assertEquals(
                """
                        .main {
                          variable: local;
                          function: local;
                          source: local;
                        }""",
                compile(entry)
        );
    }

    /// Rejects different forwarded definitions for every member kind.
    @Test
    void rejectsConflictingForwardedDefinitions(@TempDir Path directory) throws Exception {
        assertForwardConflict(
                directory,
                "$value: one;",
                "$value: two;",
                "Two forwarded modules both define a variable named $value."
        );
        assertForwardConflict(
                directory,
                "@function value() { @return one; }",
                "@function value() { @return two; }",
                "Two forwarded modules both define a function named value."
        );
        assertForwardConflict(
                directory,
                "@mixin value { source: one; }",
                "@mixin value { source: two; }",
                "Two forwarded modules both define a mixin named value."
        );
    }

    /// Accepts diamond forwarding of one member identity and emits shared CSS once.
    @Test
    void acceptsDiamondIdentityAndDeduplicatesCss(@TempDir Path directory) throws Exception {
        var base = Files.writeString(
                directory.resolve("base.scss"),
                """
                        $value: shared;
                        .base { value: $value; }
                        """
        );
        var left = Files.writeString(
                directory.resolve("left.scss"),
                "@forward \"base\";"
        );
        var right = Files.writeString(
                directory.resolve("right.scss"),
                "@forward \"base\";"
        );
        var facade = Files.writeString(
                directory.resolve("facade.scss"),
                """
                        @forward "left";
                        @forward "right";
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "facade" as facade;
                        .main { value: facade.$value; }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(entry),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .base {
                          value: shared;
                        }

                        .main {
                          value: shared;
                        }""",
                result.output()
        );
        assertEquals(1, result.output().split("\\.base", -1).length - 1);
        assertEquals(
                Set.of(
                        entry.toAbsolutePath().normalize().toUri(),
                        facade.toRealPath().toUri(),
                        left.toRealPath().toUri(),
                        right.toRealPath().toUri(),
                        base.toRealPath().toUri()
                ),
                result.loadedUrls()
        );
    }

    /// Passes explicit configuration through one and multiple plain forwarding layers.
    @Test
    void passesConfigurationThroughPlainForwards(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("one-base.scss"),
                "$value: one-default !default;"
        );
        Files.writeString(
                directory.resolve("one-facade.scss"),
                "@forward \"one-base\";"
        );
        Files.writeString(
                directory.resolve("multi-base.scss"),
                "$value: multi-default !default;"
        );
        Files.writeString(
                directory.resolve("multi-middle.scss"),
                "@forward \"multi-base\";"
        );
        Files.writeString(
                directory.resolve("multi-facade.scss"),
                "@forward \"multi-middle\";"
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "one-facade" as one with ($value: one-configured);
                        @use "multi-facade" as multi with ($value: multi-configured);
                        .main {
                          one: one.$value;
                          multi: multi.$value;
                        }
                        """
        );

        assertEquals(
                """
                        .main {
                          one: one-configured;
                          multi: multi-configured;
                        }""",
                compile(entry)
        );
    }

    /// Applies prefixes and member filters while retaining the target module CSS.
    @Test
    void transformsForwardedMembersAndPreservesCss(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(
                directory.resolve("filtered-base.scss"),
                """
                        $shown: red;
                        $hidden: blue;
                        @function shown() { @return 1px; }
                        @function hidden() { @return 2px; }
                        @mixin shown { border-width: 3px; }
                        @mixin hidden { border-width: 4px; }
                        .base { color: $hidden; }
                        """
        );
        Files.writeString(
                directory.resolve("shown-facade.scss"),
                """
                        @forward "filtered-base" as theme-* show $theme-shown, theme-shown;
                        """
        );
        var shownEntry = Files.writeString(
                directory.resolve("shown-main.scss"),
                """
                        @use "shown-facade" as theme;
                        .main {
                          color: theme.$theme-shown;
                          width: theme.theme-shown();
                          @include theme.theme-shown;
                        }
                        """
        );

        assertEquals(
                """
                        .base {
                          color: blue;
                        }

                        .main {
                          color: red;
                          width: 1px;
                          border-width: 3px;
                        }""",
                compile(shownEntry)
        );

        Files.writeString(
                directory.resolve("hidden-facade.scss"),
                """
                        @forward "filtered-base" hide $hidden, hidden;
                        """
        );
        var hiddenEntry = Files.writeString(
                directory.resolve("hidden-main.scss"),
                """
                        @use "hidden-facade" as visible;
                        .main {
                          color: visible.$shown;
                          width: visible.shown();
                          @include visible.shown;
                        }
                        """
        );

        assertEquals(
                """
                        .base {
                          color: blue;
                        }

                        .main {
                          color: red;
                          width: 1px;
                          border-width: 3px;
                        }""",
                compile(hiddenEntry)
        );

        var blockedEntry = Files.writeString(
                directory.resolve("blocked-main.scss"),
                """
                        @use "shown-facade" as theme;
                        a { color: theme.$theme-hidden; }
                        """
        );
        assertEquals(
                "Undefined variable.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(blockedEntry)
                ).getMessage()
        );
    }

    /// Projects configuration through prefixes and upstream-compatible filters.
    @Test
    void projectsConfigurationThroughPrefixesAndFilters(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(
                directory.resolve("prefix-base.scss"),
                """
                        $color: red !default;
                        .prefix { color: $color; }
                        """
        );
        Files.writeString(
                directory.resolve("prefix-facade.scss"),
                """
                        @forward "prefix-base" as theme-*;
                        """
        );
        var prefixEntry = Files.writeString(
                directory.resolve("prefix-main.scss"),
                """
                        @use "prefix-facade" as theme with ($theme-color: blue);
                        .main { color: theme.$theme-color; }
                        """
        );
        assertEquals(
                """
                        .prefix {
                          color: blue;
                        }

                        .main {
                          color: blue;
                        }""",
                compile(prefixEntry)
        );

        Files.writeString(
                directory.resolve("nested-base.scss"),
                """
                        $color: red !default;
                        .nested { color: $color; }
                        """
        );
        Files.writeString(
                directory.resolve("nested-middle.scss"),
                """
                        @forward "nested-base" as inner-*;
                        """
        );
        Files.writeString(
                directory.resolve("nested-facade.scss"),
                """
                        @forward "nested-middle" as outer-*;
                        """
        );
        var nestedEntry = Files.writeString(
                directory.resolve("nested-main.scss"),
                """
                        @use "nested-facade" as nested with (
                          $outer-inner-color: purple
                        );
                        .main { color: nested.$outer-inner-color; }
                        """
        );
        assertEquals(
                """
                        .nested {
                          color: purple;
                        }

                        .main {
                          color: purple;
                        }""",
                compile(nestedEntry)
        );

        Files.writeString(
                directory.resolve("show-base.scss"),
                """
                        $color: red !default;
                        .show { color: $color; }
                        """
        );
        Files.writeString(
                directory.resolve("show-facade.scss"),
                """
                        @forward "show-base" as p-* show $p-color;
                        """
        );
        var showEntry = Files.writeString(
                directory.resolve("show-main.scss"),
                """
                        @use "show-facade" with ($p-color: blue);
                        """
        );
        assertEquals(
                "$p-color was not declared with !default in the @used module.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(showEntry)
                ).getMessage()
        );

        Files.writeString(
                directory.resolve("hide-base.scss"),
                """
                        $color: red !default;
                        .hide { color: $color; }
                        """
        );
        Files.writeString(
                directory.resolve("hide-facade.scss"),
                """
                        @forward "hide-base" as p-* hide $p-color;
                        """
        );
        var hideEntry = Files.writeString(
                directory.resolve("hide-main.scss"),
                """
                        @use "hide-facade" with ($p-color: blue);
                        """
        );
        assertEquals(
                """
                        .hide {
                          color: blue;
                        }""",
                compile(hideEntry)
        );

        var showCacheEntry = Files.writeString(
                directory.resolve("show-cache-main.scss"),
                """
                        @use "show-facade" as first;
                        @use "show-facade" as second with ($p-color: blue);
                        """
        );
        assertEquals(
                "$p-color was not declared with !default in the @used module.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(showCacheEntry)
                ).getMessage()
        );

        var prefixCacheEntry = Files.writeString(
                directory.resolve("prefix-cache-main.scss"),
                """
                        @use "prefix-facade" as first;
                        @use "prefix-facade" as second with ($theme-color: blue);
                        """
        );
        assertEquals(
                """
                        This module was already loaded, so it can't be configured using "with".""",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(prefixCacheEntry)
                ).getMessage()
        );
    }

    /// Applies hard and guarded forward-owned configuration with correct precedence.
    @Test
    void appliesHardAndGuardedForwardConfiguration(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(
                directory.resolve("hard-base.scss"),
                """
                        $value: base !default;
                        .hard { value: $value; }
                        """
        );
        Files.writeString(
                directory.resolve("hard-facade.scss"),
                """
                        @forward "hard-base" with ($value: hard);
                        """
        );
        var hardEntry = Files.writeString(
                directory.resolve("hard-main.scss"),
                """
                        @use "hard-facade" as hard;
                        .main { value: hard.$value; }
                        """
        );
        assertEquals(
                """
                        .hard {
                          value: hard;
                        }

                        .main {
                          value: hard;
                        }""",
                compile(hardEntry)
        );

        var hardOuterEntry = Files.writeString(
                directory.resolve("hard-outer-main.scss"),
                """
                        @use "hard-facade" with ($value: outer);
                        """
        );
        assertEquals(
                "$value was not declared with !default in the @used module.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(hardOuterEntry)
                ).getMessage()
        );

        Files.writeString(
                directory.resolve("guard-base.scss"),
                """
                        $value: base !default;
                        .guard { value: $value; }
                        """
        );
        Files.writeString(
                directory.resolve("guard-facade.scss"),
                """
                        @forward "guard-base" with ($value: forward !default);
                        """
        );
        var guardDefaultEntry = Files.writeString(
                directory.resolve("guard-default-main.scss"),
                """
                        @use "guard-facade" as guard;
                        .main { value: guard.$value; }
                        """
        );
        assertEquals(
                """
                        .guard {
                          value: forward;
                        }

                        .main {
                          value: forward;
                        }""",
                compile(guardDefaultEntry)
        );

        var guardNullEntry = Files.writeString(
                directory.resolve("guard-null-main.scss"),
                """
                        @use "guard-facade" as guard with ($value: null);
                        .main { value: guard.$value; }
                        """
        );
        assertEquals(
                """
                        .guard {
                          value: forward;
                        }

                        .main {
                          value: forward;
                        }""",
                compile(guardNullEntry)
        );

        Files.writeString(
                directory.resolve("guard-skip-facade.scss"),
                """
                        @forward "guard-base" with ($value: $missing !default);
                        """
        );
        var guardOuterEntry = Files.writeString(
                directory.resolve("guard-outer-main.scss"),
                """
                        @use "guard-skip-facade" as guard with ($value: outer);
                        .main { value: guard.$value; }
                        """
        );
        assertEquals(
                """
                        .guard {
                          value: outer;
                        }

                        .main {
                          value: outer;
                        }""",
                compile(guardOuterEntry)
        );

        Files.writeString(
                directory.resolve("fixed-base.scss"),
                """
                        $value: fixed;
                        """
        );
        Files.writeString(
                directory.resolve("fixed-facade.scss"),
                """
                        @forward "fixed-base" with ($value: configured);
                        """
        );
        var fixedEntry = Files.writeString(
                directory.resolve("fixed-main.scss"),
                """
                        @use "fixed-facade";
                        """
        );
        assertEquals(
                "$value was not declared with !default in the @used module.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(fixedEntry)
                ).getMessage()
        );
    }
    /// Reports forwarding loops and preserves dependency order and loaded URLs.
    @Test
    void reportsLoopsAndOrdersDependencies(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("loop-a.scss"), "@forward \"loop-b\";");
        Files.writeString(directory.resolve("loop-b.scss"), "@forward \"loop-a\";");
        var loopEntry = Files.writeString(
                directory.resolve("loop.scss"),
                "@use \"loop-a\";"
        );
        assertEquals(
                "Module loop: loop-a.scss is already being loaded.",
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(loopEntry)
                ).getMessage()
        );

        var first = Files.writeString(
                directory.resolve("first.scss"),
                ".first { order: 1; }"
        );
        var shared = Files.writeString(
                directory.resolve("shared.scss"),
                ".shared { order: 2; }"
        );
        var second = Files.writeString(
                directory.resolve("second.scss"),
                """
                        @forward "shared";
                        .second { order: 3; }
                        """
        );
        var facade = Files.writeString(
                directory.resolve("ordered-facade.scss"),
                """
                        @forward "first";
                        @forward "second";
                        .facade { order: 4; }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("ordered.scss"),
                """
                        @use "ordered-facade";
                        .main { order: 5; }
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(entry),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .first {
                          order: 1;
                        }

                        .shared {
                          order: 2;
                        }

                        .second {
                          order: 3;
                        }

                        .facade {
                          order: 4;
                        }

                        .main {
                          order: 5;
                        }""",
                result.output()
        );
        assertEquals(
                Set.of(
                        entry.toAbsolutePath().normalize().toUri(),
                        facade.toRealPath().toUri(),
                        first.toRealPath().toUri(),
                        second.toRealPath().toUri(),
                        shared.toRealPath().toUri()
                ),
                result.loadedUrls()
        );
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

    /// Asserts that a private member access fails through a forwarding facade.
    ///
    /// @param entry the root stylesheet that accesses the member
    /// @param expectedMessage the member-kind-specific diagnostic
    /// @throws Exception if reading fails
    private static void assertPrivateFailure(
            Path entry,
            String expectedMessage
    ) throws Exception {
        assertEquals(
                expectedMessage,
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(entry)
                ).getMessage()
        );
    }

    /// Creates two source modules and verifies a forwarded-member conflict.
    ///
    /// @param directory the temporary module directory
    /// @param firstSource the first module source
    /// @param secondSource the second module source
    /// @param expectedMessage the expected conflict diagnostic
    /// @throws Exception if writing, reading, or compilation fails unexpectedly
    private static void assertForwardConflict(
            Path directory,
            String firstSource,
            String secondSource,
            String expectedMessage
    ) throws Exception {
        Files.writeString(directory.resolve("conflict-a.scss"), firstSource);
        Files.writeString(directory.resolve("conflict-b.scss"), secondSource);
        var entry = Files.writeString(
                directory.resolve("conflict.scss"),
                """
                        @forward "conflict-a";
                        @forward "conflict-b";
                        """
        );
        assertEquals(
                expectedMessage,
                assertThrows(
                        SassCompilationException.class,
                        () -> compile(entry)
                ).getMessage()
        );
    }
}
