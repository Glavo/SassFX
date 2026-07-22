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

/// Verifies plain `@forward` exports, configuration flow, conflicts, and module graphs.
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
                "Module loop: this module is already being loaded.",
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
