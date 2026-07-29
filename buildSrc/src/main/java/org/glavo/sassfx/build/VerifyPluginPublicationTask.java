// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/// Verifies the generated metadata for the self-contained Gradle plugin publication.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyPluginPublicationTask extends DefaultTask {
    /// Returns the generated Maven POM.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPomFile();

    /// Returns the generated Gradle module metadata.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getModuleMetadataFile();

    /// Inspects the generated publication metadata.
    @TaskAction
    public final void verify() throws IOException {
        String pom = Files.readString(
                getPomFile().get().getAsFile().toPath(),
                StandardCharsets.UTF_8
        );
        if (pom.contains("<dependencies>")) {
            throw new GradleException(
                    "The Gradle plugin POM exposes dependencies instead of "
                            + "publishing a self-contained Shadow JAR."
            );
        }

        String moduleMetadata = Files.readString(
                getModuleMetadataFile().get().getAsFile().toPath(),
                StandardCharsets.UTF_8
        );
        if (!moduleMetadata.contains("\"name\": \"shadowRuntimeElements\"")) {
            throw new GradleException(
                    "The Gradle plugin module metadata has no shadowed runtime variant."
            );
        }
        if (moduleMetadata.contains("\"dependencies\":")) {
            throw new GradleException(
                    "The Gradle plugin shadowed runtime variant exposes dependencies."
            );
        }
    }
}
