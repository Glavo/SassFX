// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Verifies that the native CLI starts and exercises its principal runtime paths.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyNativeCliTask extends DefaultTask {
    /// Returns the native executable to verify.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getExecutableFile();

    /// Returns the implementation version expected from the executable.
    @Input
    public abstract Property<String> getExpectedVersion();

    /// Runs version, compilation, BSS, and Embedded Protocol smoke checks.
    @TaskAction
    public final void verify() throws IOException {
        Path executable = getExecutableFile().get().getAsFile().toPath();
        String expectedVersion = getExpectedVersion().get();

        requireContains(
                run(executable, List.of("--version"), null),
                "sassfx " + expectedVersion,
                "version output"
        );

        String embeddedVersion = run(
                executable,
                List.of("--embedded", "--version"),
                null
        );
        requireContains(
                embeddedVersion,
                "\"protocolVersion\": \"3.2.0\"",
                "Embedded Protocol version output"
        );
        requireContains(
                embeddedVersion,
                "\"implementationVersion\": \"" + expectedVersion + "\"",
                "Embedded implementation version output"
        );

        Path temporaryDirectory = getTemporaryDir().toPath();
        Files.createDirectories(temporaryDirectory);
        verifyCssCompilation(executable);
        verifyBssCompilation(executable, temporaryDirectory);
    }

    /// Compiles SCSS from standard input to CSS and verifies its contents.
    ///
    /// @param executable native executable path
    /// @throws IOException if the process cannot be started or observed
    private void verifyCssCompilation(Path executable) throws IOException {
        String output = run(
                executable,
                List.of("--stdin", "--no-source-map"),
                "$color: red; a { color: $color; }"
        );
        requireContains(
                output,
                "color: red;",
                "CSS output"
        );
    }

    /// Compiles one SCSS file to JavaFX 27 BSS and verifies its format version.
    ///
    /// @param executable native executable path
    /// @param directory task-owned temporary directory
    /// @throws IOException if the input, process, or output cannot be accessed
    private void verifyBssCompilation(
            Path executable,
            Path directory
    ) throws IOException {
        Path source = directory.resolve("native-smoke-bss.scss");
        Path destination = directory.resolve("native-smoke.bss");
        Files.writeString(
                source,
                "Pane { -fx-opacity: 0.5; }",
                StandardCharsets.UTF_8
        );
        Files.deleteIfExists(destination);

        run(
                executable,
                List.of(
                        "--target",
                        "bss/javafx@27",
                        "-o",
                        destination.toString(),
                        source.toString()
                ),
                null
        );
        requireRegularFile(destination, "BSS output");
        byte[] contents = Files.readAllBytes(destination);
        if (contents.length < Short.BYTES
                || Short.toUnsignedInt(ByteBuffer.wrap(contents).getShort())
                != 9) {
            throw new GradleException(
                    "BSS output does not use the JavaFX 27 format version"
            );
        }
    }

    /// Runs the native executable and returns its combined standard output and error.
    ///
    /// @param executable native executable path
    /// @param arguments command-line arguments
    /// @param standardInput UTF-8 standard input, or `null` to close the stream
    /// @return combined UTF-8 process output
    /// @throws IOException if the process cannot be started or observed
    private String run(
            Path executable,
            @Unmodifiable List<String> arguments,
            @Nullable String standardInput
    ) throws IOException {
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(executable.toAbsolutePath().toString());
        command.addAll(arguments);

        Process process = new ProcessBuilder(command)
                .directory(getTemporaryDir())
                .redirectErrorStream(true)
                .start();
        try {
            try (var input = process.getOutputStream()) {
                if (standardInput != null) {
                    input.write(standardInput.getBytes(StandardCharsets.UTF_8));
                }
            }

            String output;
            try (var processOutput = process.getInputStream()) {
                output = new String(
                        processOutput.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new GradleException(
                        "Interrupted while waiting for the native CLI",
                        failure
                );
            }
            if (exitCode != 0) {
                throw new GradleException(
                        "Native CLI exited with status " + exitCode
                                + System.lineSeparator() + output
                );
            }
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /// Requires a text value to contain an expected fragment.
    ///
    /// @param actual inspected text
    /// @param expected required fragment
    /// @param description value name used in diagnostics
    private static void requireContains(
            String actual,
            String expected,
            String description
    ) {
        if (!actual.contains(expected)) {
            throw new GradleException(
                    description + " does not contain '" + expected + "':"
                            + System.lineSeparator() + actual
            );
        }
    }

    /// Requires a path to identify a regular file.
    ///
    /// @param path inspected path
    /// @param description path name used in diagnostics
    private static void requireRegularFile(Path path, String description) {
        if (!Files.isRegularFile(path)) {
            throw new GradleException(
                    description + " was not created at " + path
            );
        }
    }
}
