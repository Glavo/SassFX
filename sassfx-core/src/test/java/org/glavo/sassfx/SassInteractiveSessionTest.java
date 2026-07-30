// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies persistent SassScript evaluation for the interactive shell.
@NotNullByDefault
final class SassInteractiveSessionTest {
    /// Evaluates expressions, preserves variables, and renders the Sass null value.
    @Test
    void evaluatesExpressionsAndRetainsVariables(@TempDir Path directory)
            throws Exception {
        var session = session(CompileOptions.DEFAULT, directory);

        assertEquals("3", session.evaluate("1 + 2").value());
        assertEquals("4", session.evaluate("$value: 4").value());
        assertEquals("5", session.evaluate("$value + 1").value());
        assertEquals("null", session.evaluate("null").value());
        assertNull(session.evaluate("   ").value());
        assertTrue(session.drainDiagnostics().isEmpty());
    }

    /// Resolves built-in, custom-file, and load-path modules with namespaces.
    @Test
    void resolvesBuiltInFileAndLoadPathModules(@TempDir Path directory)
            throws Exception {
        var imported = Files.writeString(
                directory.resolve("_imported.scss"),
                "$accent: rebeccapurple;"
        );
        var loadPath = Files.createDirectory(directory.resolve("load-path"));
        Files.writeString(
                loadPath.resolve("_theme.scss"),
                "$spacing: 3px;"
        );
        SassFileImporter importer = (url, context) ->
                url.toString().equals("virtual:palette")
                        ? imported.toUri()
                        : null;
        var options = new CompileOptions(
                false,
                List.of(loadPath),
                null,
                List.of(importer)
        );
        var session = session(options, directory);

        assertNull(session.evaluate("@use \"sass:math\"").value());
        assertEquals("3px", session.evaluate("math.div(6px, 2)").value());
        assertNull(session.evaluate("@use \"virtual:palette\" as palette").value());
        assertEquals("rebeccapurple", session.evaluate("palette.$accent").value());
        assertNull(session.evaluate("@use \"theme\" as *").value());
        assertEquals("3px", session.evaluate("$spacing").value());
    }

    /// Applies module configuration before evaluating an imported module.
    @Test
    void configuresModulesWithWithClauses(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_settings.scss"),
                "$theme: light !default;"
        );
        var session = session(CompileOptions.DEFAULT, directory);

        assertNull(
                session.evaluate(
                        "@use \"settings\" with ($theme: dark)"
                ).value()
        );
        assertEquals("dark", session.evaluate("settings.$theme").value());
    }

    /// Resolves relative module URLs against the session working directory.
    @Test
    void resolvesModulesRelativeToWorkingDirectory(@TempDir Path directory)
            throws Exception {
        var styles = Files.createDirectories(directory.resolve("styles"));
        Files.writeString(styles.resolve("_local.scss"), "$answer: 42;");
        var session = session(CompileOptions.DEFAULT, styles);

        assertNull(session.evaluate("@use \"local\"").value());
        assertEquals("42", session.evaluate("local.$answer").value());
    }

    /// Restores the session after parse, evaluation, and module-load failures.
    @Test
    void recoversAfterLineFailures(@TempDir Path directory) throws Exception {
        var session = session(CompileOptions.DEFAULT, directory);

        assertThrows(SassCompilationException.class, () -> session.evaluate("1 +"));
        assertEquals("2", session.evaluate("1 + 1").value());

        assertThrows(
                SassCompilationException.class,
                () -> session.evaluate("$missing")
        );
        assertEquals("3", session.evaluate("1 + 2").value());

        assertThrows(
                SassCompilationException.class,
                () -> session.evaluate("@use \"does-not-exist\"")
        );
        assertEquals("4", session.evaluate("2 + 2").value());
    }

    /// Keeps a previous variable binding when evaluating a replacement fails.
    @Test
    void retainsPreviousVariableAfterFailedAssignment(@TempDir Path directory)
            throws Exception {
        var session = session(CompileOptions.DEFAULT, directory);

        assertEquals("old", session.evaluate("$value: old").value());
        assertThrows(
                SassCompilationException.class,
                () -> session.evaluate("$value: $missing")
        );
        assertEquals("old", session.evaluate("$value").value());
    }

    /// Delivers diagnostics per line, promotes fatal deprecations, and honors silence.
    @Test
    void processesDiagnosticsPerLineAndRecoversAfterFatalDeprecations(
            @TempDir Path directory
    ) throws Exception {
        var warningSession = session(CompileOptions.DEFAULT, directory);
        var warning = warningSession.evaluate("$ratio: (1 / 2)");
        assertEquals("0.5", warning.value());
        assertEquals(1, warning.diagnostics().size());
        assertEquals("slash-div", warning.diagnostics().get(0).code());
        assertEquals(
                DiagnosticSeverity.DEPRECATION,
                warning.diagnostics().get(0).severity()
        );
        assertTrue(warningSession.evaluate("$ratio").diagnostics().isEmpty());
        assertTrue(warningSession.drainDiagnostics().isEmpty());

        var fatalOptions = options(Set.of(), Set.of(SassDeprecation.SLASH_DIV));
        var fatalSession = session(fatalOptions, directory);
        var failure = assertThrows(
                SassCompilationException.class,
                () -> fatalSession.evaluate("$ratio: (1 / 2)")
        );
        assertEquals("slash-div", failure.primaryDiagnostic().code());
        assertEquals("3", fatalSession.evaluate("1 + 2").value());

        var silentOptions = options(Set.of(SassDeprecation.SLASH_DIV), Set.of());
        var silentSession = session(silentOptions, directory);
        assertFalse(
                silentSession.evaluate("$ratio: (1 / 2)")
                        .diagnostics()
                        .stream()
                        .anyMatch(diagnostic -> "slash-div".equals(diagnostic.code()))
        );
    }

    /// Creates a session with a root directory appropriate for temporary fixtures.
    private static SassInteractiveSession session(
            CompileOptions options,
            Path directory
    ) {
        return new SassInteractiveSession(options, directory);
    }

    /// Creates options with explicit silent and fatal deprecation categories.
    private static CompileOptions options(
            Set<SassDeprecation> silence,
            Set<SassDeprecation> fatal
    ) {
        return new CompileOptions(
                false,
                List.of(),
                null,
                List.of(),
                List.of(),
                new SassDiagnosticOptions(
                        event -> {
                        },
                        false,
                        false,
                        silence,
                        fatal,
                        Set.of()
                )
        );
    }
}
