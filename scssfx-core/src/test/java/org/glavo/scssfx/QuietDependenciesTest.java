// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies importer-provenance handling for {@code quietDeps}.
@NotNullByDefault
final class QuietDependenciesTest {
    /// Retains compiler warnings from entrypoint-relative filesystem modules.
    @Test
    void retainsRootRelativeWarnings(@TempDir Path directory) throws Exception {
        var root = directory.resolve("root.scss");
        Files.writeString(root, "@use \"relative\";");
        Files.writeString(
                directory.resolve("_relative.scss"),
                "$value: (1/2);"
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(root),
                CssTarget.DEFAULT,
                options(List.of(), Set.of())
        );

        assertEquals(1, result.diagnostics().size());
        assertEquals("slash-div", result.diagnostics().get(0).code());
    }

    /// Suppresses compiler warnings from load paths and their transitive modules.
    @Test
    void suppressesLoadPathAndTransitiveWarnings(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_dependency.scss"),
                """
                        @use "nested";
                        $dependency-value: (1/2);
                        """
        );
        Files.writeString(
                directory.resolve("_nested.scss"),
                "$nested-value: (1/2);"
        );

        var result = compile(
                "@use \"dependency\";",
                options(List.of(directory), Set.of())
        );

        assertTrue(result.diagnostics().isEmpty());
    }

    /// Keeps explicit warning and debug statements visible in dependencies.
    @Test
    void retainsUserMessagesFromDependencies(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_dependency.scss"),
                """
                        @warn dependency-warning;
                        @debug dependency-debug;
                        $value: (1/2);
                        """
        );

        var result = compile(
                "@use \"dependency\";",
                options(List.of(directory), Set.of())
        );

        assertEquals(2, result.diagnostics().size());
        assertEquals(DiagnosticSeverity.WARNING, result.diagnostics().get(0).severity());
        assertEquals(DiagnosticSeverity.DEBUG, result.diagnostics().get(1).severity());
    }

    /// Restores dependency provenance when root code calls a dependency function.
    @Test
    void suppressesWarningsFromDependencyCallables(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_dependency.scss"),
                """
                        @function legacy() {
                          @return (1/2);
                        }
                        """
        );

        var result = compile(
                """
                        @use "dependency";
                        a { value: dependency.legacy(); }
                        """,
                options(List.of(directory), Set.of())
        );

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(
                """
                        a {
                          value: 0.5;
                        }""",
                result.output()
        );
    }

    /// Applies dependency silencing before fatal-deprecation promotion.
    @Test
    void suppressesFatalDeprecationsFromDependencies(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_dependency.scss"),
                "$value: (1/2);"
        );

        var result = compile(
                "@use \"dependency\";",
                options(
                        List.of(directory),
                        Set.of(SassDeprecation.SLASH_DIV)
                )
        );

        assertTrue(result.diagnostics().isEmpty());
    }

    /// Creates quiet dependency options for the supplied load paths.
    private static CompileOptions options(
            List<Path> loadPaths,
            Set<SassDeprecation> fatal
    ) {
        return new CompileOptions(
                false,
                loadPaths,
                null,
                List.of(),
                List.of(),
                new SassDiagnosticOptions(
                        SassLogger.NO_OP,
                        true,
                        false,
                        Set.of(),
                        fatal,
                        Set.of()
                )
        );
    }

    /// Compiles an in-memory root with explicit options.
    private static CompileResult<String> compile(
            String source,
            CompileOptions options
    ) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT,
                options
        );
    }
}
