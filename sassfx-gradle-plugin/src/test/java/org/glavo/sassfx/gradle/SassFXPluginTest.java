// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the plugin through isolated Gradle consumer builds.
@NotNullByDefault
final class SassFXPluginTest {
    /// Creates the functional test suite.
    SassFXPluginTest() {
    }

    /// Compiles default CSS and contributes it to Java resources.
    @Test
    void compilesCssIntoJavaResources(@TempDir Path project)
            throws IOException {
        writeProject(
                project,
                """
                        plugins {
                            id 'java'
                            id 'org.glavo.sassfx'
                        }

                        sassfx {
                            loadPaths.from(
                                layout.projectDirectory.dir('src/shared/scss')
                            )
                        }
                        """
        );
        write(
                project.resolve("src/shared/scss/_colors.scss"),
                "$accent: #336699;"
        );
        write(
                project.resolve("src/main/scss/theme.scss"),
                """
                        @use "colors";
                        .theme { color: colors.$accent; }
                        """
        );

        var result = run(project, "processResources");

        assertEquals(
                TaskOutcome.SUCCESS,
                taskOutcome(result, ":compileScss")
        );
        var generated = project.resolve(
                "build/generated/sassfx/main/theme.css"
        );
        var resource = project.resolve("build/resources/main/theme.css");
        assertTrue(Files.readString(generated).contains("color: #336699"));
        assertEquals(Files.readString(generated), Files.readString(resource));
        assertFalse(Files.exists(
                project.resolve(
                        "build/generated/sassfx/main/_colors.css"
                )
        ));
    }

    /// Compiles strict JavaFX CSS and BSS targets without JavaFX dependencies.
    @Test
    void compilesJavaFxCssAndBssTargets(@TempDir Path project)
            throws IOException {
        writeProject(
                project,
                """
                        plugins {
                            id 'org.glavo.sassfx'
                        }

                        sassfx {
                            target.set('css/javafx@21')
                            style.set('compressed')
                        }

                        tasks.register(
                            'compileBss',
                            org.glavo.sassfx.gradle.SassFXCompile
                        ) {
                            sourceDirectory.set(layout.projectDirectory.dir('src/main/scss'))
                            outputDirectory.set(layout.buildDirectory.dir('generated/sassfx/bss'))
                            target.set('bss/javafx@27')
                        }
                        """
        );
        write(
                project.resolve("src/main/scss/application.scss"),
                "Pane { -fx-opacity: 0.5; }"
        );

        var result = run(project, "compileScss", "compileBss");

        assertEquals(
                TaskOutcome.SUCCESS,
                taskOutcome(result, ":compileScss")
        );
        assertEquals(
                TaskOutcome.SUCCESS,
                taskOutcome(result, ":compileBss")
        );
        assertEquals(
                "Pane{-fx-opacity:0.5}",
                Files.readString(project.resolve(
                        "build/generated/sassfx/main/application.css"
                ))
        );
        assertTrue(
                Files.size(project.resolve(
                        "build/generated/sassfx/bss/application.bss"
                )) > 16
        );
    }

    /// Declares stable inputs and removes outputs whose entrypoints disappear.
    @Test
    void tracksInputsAndRemovesStaleOutputs(@TempDir Path project)
            throws IOException {
        writeProject(
                project,
                """
                        plugins {
                            id 'org.glavo.sassfx'
                        }
                        """
        );
        var source = project.resolve("src/main/scss/obsolete.scss");
        var output = project.resolve(
                "build/generated/sassfx/main/obsolete.css"
        );
        write(source, ".obsolete { display: block; }");

        assertEquals(
                TaskOutcome.SUCCESS,
                taskOutcome(
                        run(project, "compileScss", "--configuration-cache"),
                        ":compileScss"
                )
        );
        assertTrue(Files.exists(output));
        assertEquals(
                TaskOutcome.UP_TO_DATE,
                taskOutcome(
                        run(project, "compileScss", "--configuration-cache"),
                        ":compileScss"
                )
        );

        Files.delete(source);
        assertEquals(
                TaskOutcome.SUCCESS,
                taskOutcome(
                        run(project, "compileScss", "--configuration-cache"),
                        ":compileScss"
                )
        );
        assertFalse(Files.exists(output));
    }

    /// Rejects noncanonical target spellings instead of accepting aliases.
    @Test
    void rejectsNoncanonicalTargets(@TempDir Path project)
            throws IOException {
        writeProject(
                project,
                """
                        plugins {
                            id 'org.glavo.sassfx'
                        }

                        sassfx {
                            target.set('CSS/JAVAFX@21')
                        }
                        """
        );
        write(
                project.resolve("src/main/scss/application.scss"),
                "Pane { -fx-opacity: 0.5; }"
        );

        var result = runAndFail(project, "compileScss");

        assertTrue(result.getOutput().contains(
                "Unsupported SassFX target 'CSS/JAVAFX@21'"
        ));
    }

    /// Writes settings and the supplied Groovy build script.
    ///
    /// @param project the temporary consumer project
    /// @param buildScript the complete build script
    /// @throws IOException if a project file cannot be written
    private static void writeProject(Path project, String buildScript)
            throws IOException {
        write(project.resolve("settings.gradle"), "rootProject.name = 'consumer'");
        write(project.resolve("build.gradle"), buildScript);
    }

    /// Writes one UTF-8 project file and creates its parent directories.
    ///
    /// @param path the destination
    /// @param content the complete file contents
    /// @throws IOException if the file cannot be written
    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(Objects.requireNonNull(path.getParent()));
        Files.writeString(path, content);
    }

    /// Runs a successful consumer build with the plugin-under-test classpath.
    ///
    /// @param project the consumer project directory
    /// @param arguments requested Gradle tasks and options
    /// @return the completed build
    private static BuildResult run(Path project, String... arguments) {
        return runner(project, arguments).build();
    }

    /// Runs an expected-to-fail consumer build.
    ///
    /// @param project the consumer project directory
    /// @param arguments requested Gradle tasks and options
    /// @return the failed build result
    private static BuildResult runAndFail(
            Path project,
            String... arguments
    ) {
        return runner(project, arguments).buildAndFail();
    }

    /// Creates a Gradle runner for one consumer build.
    ///
    /// @param project the consumer project directory
    /// @param arguments requested Gradle tasks and options
    /// @return the configured runner
    private static GradleRunner runner(Path project, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments(arguments)
                .withPluginClasspath()
                .forwardOutput();
    }

    /// Returns the outcome of one required task.
    ///
    /// @param result the completed build
    /// @param path the absolute task path
    /// @return the task outcome
    private static TaskOutcome taskOutcome(
            BuildResult result,
            String path
    ) {
        var task = result.task(path);
        assertNotNull(task);
        return task.getOutcome();
    }
}
