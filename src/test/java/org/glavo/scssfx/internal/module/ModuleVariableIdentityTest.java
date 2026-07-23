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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies module-variable identity through diamond and built-in forward views.
@NotNullByDefault
final class ModuleVariableIdentityTest {
    /// Deduplicates diamond `as *` exports by their shared member identities.
    @Test
    void writesDiamondGlobalModuleVariableOnce(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_diamond-base.scss"),
                """
                        $value: 1;
                        @function current() { @return $value; }
                        """
        );
        Files.writeString(
                directory.resolve("_diamond-left.scss"),
                "@forward \"diamond-base\";"
        );
        Files.writeString(
                directory.resolve("_diamond-right.scss"),
                "@forward \"diamond-base\";"
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "diamond-left" as *;
                        @use "diamond-right" as *;
                        $value: 2;
                        .result {
                          direct: $value;
                          callable: current();
                        }
                        """
        );

        assertEquals(
                """
                        .result {
                          direct: 2;
                          callable: 2;
                        }""",
                compile(entry)
        );
    }

    /// Preserves built-in read-only bindings through prefixing and filtering.
    @Test
    void rejectsForwardedBuiltInVariableAssignment(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_facade.scss"),
                "@forward \"sass:math\" as number-* show $number-pi;"
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "facade" as facade;
                        facade.$number-pi: 0;
                        """
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(entry)
        );
        assertEquals("Cannot modify built-in variable.", failure.getMessage());
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
}
