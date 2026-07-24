// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.BssTarget;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.JavaFXCssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies assignment through namespaced, forwarded, and global module variables.
@NotNullByDefault
final class ModuleVariableAssignmentTest {
    /// Shares writes across aliases, module callables, and meta module inspection.
    @Test
    void sharesQualifiedWritesAcrossModuleConsumers(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_state.scss"),
                """
                        $value: 1;
                        $derived: $value + 1;
                        @function current() { @return $value; }
                        @mixin set-internally() { $value: 5 !global; }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "sass:map";
                        @use "sass:meta";
                        @use "state" as first;
                        @use "state" as second;
                        $caller: 2;
                        first.$value: $caller + 1;
                        .before {
                          direct: second.$value;
                          function: first.current();
                          derived: second.$derived;
                          meta: map.get(meta.module-variables("second"), "value");
                        }
                        @include second.set-internally;
                        .after {
                          direct: first.$value;
                          function: second.current();
                          derived: first.$derived;
                        }
                        """
        );

        assertEquals(
                """
                        .before {
                          direct: 3;
                          function: 3;
                          derived: 2;
                          meta: 3;
                        }

                        .after {
                          direct: 5;
                          function: 5;
                          derived: 2;
                        }""",
                compile(entry)
        );
    }

    /// Writes through prefixed forward views and rejects filtered exports.
    @Test
    void writesThroughForwardedVariableViews(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_base.scss"),
                """
                        $value: base;
                        $hidden: hidden;
                        @function read() { @return $value; }
                        """
        );
        Files.writeString(
                directory.resolve("_facade.scss"),
                """
                        @forward "base" as theme-* show $theme-value, theme-read;
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "base" as base;
                        @use "facade" as facade;
                        facade.$theme-value: changed;
                        .result {
                          facade: facade.$theme-value;
                          original: base.$value;
                          callable: facade.theme-read();
                        }
                        """
        );

        assertEquals(
                """
                        .result {
                          facade: changed;
                          original: changed;
                          callable: changed;
                        }""",
                compile(entry)
        );

        var blocked = Files.writeString(
                directory.resolve("blocked.scss"),
                """
                        @use "facade" as facade;
                        facade.$theme-hidden: changed;
                        """
        );
        assertEquals(
                "Undefined variable.",
                assertFailure(blocked).getMessage()
        );
    }

    /// Applies guarded assignments and routes root writes to `as *` modules.
    @Test
    void supportsGuardedAndGlobalModuleAssignments(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_guarded.scss"),
                """
                        $value: null;
                        $present: original;
                        """
        );
        var guarded = Files.writeString(
                directory.resolve("guarded-main.scss"),
                """
                        @use "guarded" as state;
                        state.$present: $missing !default;
                        state.$value: assigned !default;
                        .result {
                          present: state.$present;
                          value: state.$value;
                        }
                        """
        );
        assertEquals(
                """
                        .result {
                          present: original;
                          value: assigned;
                        }""",
                compile(guarded)
        );

        Files.writeString(
                directory.resolve("_global.scss"),
                """
                        $value: 1;
                        @function current() { @return $value; }
                        """
        );
        var global = Files.writeString(
                directory.resolve("global-main.scss"),
                """
                        @use "global" as *;
                        @if true {
                          $value: 7 !global;
                        }
                        .result {
                          direct: $value;
                          callable: current();
                        }
                        """
        );
        assertEquals(
                """
                        .result {
                          direct: 7;
                          callable: 7;
                        }""",
                compile(global)
        );
    }

    /// Reports missing namespaces, absent exports, and read-only built-in variables.
    @Test
    void rejectsInvalidModuleVariableAssignments(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_state.scss"), "$value: 1;");

        assertAssignmentFailure(
                directory,
                "missing-export.scss",
                "@use \"state\"; state.$missing: 1;",
                "Undefined variable."
        );
        assertAssignmentFailure(
                directory,
                "guarded-missing.scss",
                "@use \"state\"; state.$missing: 1 !default;",
                "Undefined variable."
        );
        assertAssignmentFailure(
                directory,
                "missing-namespace.scss",
                "missing.$value: 1;",
                "There is no module with the namespace \"missing\"."
        );
        assertAssignmentFailure(
                directory,
                "built-in.scss",
                "@use \"sass:math\"; math.$pi: 0;",
                "Cannot modify built-in variable."
        );
        assertAssignmentFailure(
                directory,
                "built-in-missing.scss",
                "@use \"sass:math\"; math.$missing: 0;",
                "Undefined variable."
        );
    }

    /// Carries indented-Sass module writes through every output backend.
    @Test
    void compilesIndentedModuleWritesForAllBackends(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_state.scss"), "$opacity: 0.5;");
        var entry = Files.writeString(
                directory.resolve("main.sass"),
                """
                        @use "state" as state
                        state.$opacity: 0.75
                        Pane
                          -fx-opacity: state.$opacity
                        """
        );

        var compiler = new SassCompiler();
        assertEquals(
                """
                        Pane {
                          -fx-opacity: 0.75;
                        }""",
                compiler.compile(SassSource.fromFile(entry), CssTarget.DEFAULT).output()
        );
        assertEquals(
                "Pane{-fx-opacity:0.75}",
                compiler.compile(
                        SassSource.fromFile(entry),
                        new JavaFXCssTarget(
                                JavaFXCompatibility.JAVA_FX_17,
                                OutputStyle.COMPRESSED
                        )
                ).output()
        );
        assertTrue(
                compiler.compile(SassSource.fromFile(entry), BssTarget.DEFAULT)
                        .output()
                        .remaining() > 0
        );
    }

    /// Compiles one filesystem entry to expanded CSS.
    ///
    /// @param entry the root stylesheet
    /// @return the emitted CSS
    /// @throws Exception if compilation fails
    private static String compile(Path entry) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromFile(entry),
                CssTarget.DEFAULT
        ).output();
    }

    /// Compiles one invalid filesystem entry.
    ///
    /// @param entry the root stylesheet
    /// @return the structured compilation failure
    private static SassCompilationException assertFailure(Path entry) {
        return assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(entry),
                        CssTarget.DEFAULT
                )
        );
    }

    /// Writes and compiles one invalid assignment fixture.
    ///
    /// @param directory the fixture directory
    /// @param fileName the root stylesheet file name
    /// @param source the invalid Sass source
    /// @param message the expected primary failure message
    /// @throws Exception if the fixture cannot be written
    private static void assertAssignmentFailure(
            Path directory,
            String fileName,
            String source,
            String message
    ) throws Exception {
        var entry = Files.writeString(directory.resolve(fileName), source);
        assertEquals(message, assertFailure(entry).getMessage());
    }
}
