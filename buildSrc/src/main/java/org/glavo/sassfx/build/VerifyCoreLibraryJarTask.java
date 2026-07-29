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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/// Verifies the public boundary and manifest of the core library JAR.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyCoreLibraryJarTask extends DefaultTask {
    /// Returns the core library JAR to inspect.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    /// Inspects the configured core library JAR.
    @TaskAction
    public final void verify() throws IOException {
        try (JarFile jar = new JarFile(getArchiveFile().get().getAsFile())) {
            @Unmodifiable List<String> forbiddenEntries = jar.stream()
                    .map(JarEntry::getName)
                    .filter(name ->
                            name.startsWith("org/glavo/sassfx/cli/")
                                    || name.startsWith("picocli/")
                                    || name.contains("/picocli/")
                    )
                    .toList();
            if (!forbiddenEntries.isEmpty()) {
                throw new GradleException(
                        "The core library JAR contains CLI entries: "
                                + String.join(", ", forbiddenEntries)
                );
            }

            @Nullable Manifest manifest = jar.getManifest();
            if (manifest == null) {
                throw new GradleException("The core library JAR has no manifest.");
            }
            @Nullable String mainClass = manifest.getMainAttributes()
                    .getValue(Attributes.Name.MAIN_CLASS);
            if (mainClass != null) {
                throw new GradleException(
                        "The core library JAR declares an application entry point: "
                                + mainClass
                );
            }
            @Nullable String moduleName = manifest.getMainAttributes()
                    .getValue("Automatic-Module-Name");
            if (!"org.glavo.sassfx".equals(moduleName)) {
                throw new GradleException(
                        "The core library JAR declares an unexpected module name: "
                                + moduleName
                );
            }
        }
    }
}
