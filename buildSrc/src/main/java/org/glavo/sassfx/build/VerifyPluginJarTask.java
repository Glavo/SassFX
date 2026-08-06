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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/// Verifies the descriptor and dependency boundary of the Gradle plugin JAR.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyPluginJarTask extends DefaultTask {
    /// Returns the Gradle plugin JAR to inspect.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    /// Inspects the configured Gradle plugin JAR.
    @TaskAction
    public final void verify() throws IOException {
        try (JarFile jar = new JarFile(getArchiveFile().get().getAsFile())) {
            @Nullable JarEntry descriptor = jar.getJarEntry(
                    "META-INF/gradle-plugins/org.glavo.sassfx.properties"
            );
            if (descriptor == null) {
                throw new GradleException(
                        "The Gradle plugin JAR has no org.glavo.sassfx descriptor."
                );
            }
            String descriptorText;
            try (var input = jar.getInputStream(descriptor)) {
                descriptorText = new String(
                        input.readAllBytes(),
                        StandardCharsets.ISO_8859_1
                );
            }
            if (!descriptorText.contains(
                    "implementation-class=org.glavo.sassfx.gradle.SassFXPlugin"
            )) {
                throw new GradleException(
                        "The Gradle plugin descriptor has an unexpected "
                                + "implementation class."
                );
            }
            if (!descriptorText.contains(
                    "compatibility.feature.configuration-cache=DECLARED_SUPPORTED"
            )) {
                throw new GradleException(
                        "The Gradle plugin descriptor does not declare "
                                + "Configuration Cache compatibility."
                );
            }

            ArtifactNoticeVerifier.verify(jar, "The Gradle plugin JAR");

            @Unmodifiable List<String> requiredEntries = List.of(
                    "org/glavo/sassfx/gradle/SassFXPlugin.class",
                    "org/glavo/sassfx/gradle/SassFXCompile.class",
                    "org/glavo/sassfx/gradle/internal/compiler/SassCompiler.class",
                    "org/glavo/sassfx/gradle/internal/compiler/sassfx-version.properties",
                    "org/glavo/sassfx/gradle/internal/thirdparty/gson/stream/JsonReader.class",
                    "org/glavo/sassfx/gradle/internal/thirdparty/errorprone/"
                            + "annotations/CheckReturnValue.class"
            );
            List<String> missingEntries = new ArrayList<>();
            for (String entry : requiredEntries) {
                if (jar.getEntry(entry) == null) {
                    missingEntries.add(entry);
                }
            }
            if (!missingEntries.isEmpty()) {
                throw new GradleException(
                        "The Gradle plugin JAR is missing required entries: "
                                + String.join(", ", missingEntries)
                );
            }

            Pattern nativeEntryPattern = Pattern.compile(
                    ".*\\.(a|dll|dylib|exe|jnilib|lib|node|so|wasm)$",
                    Pattern.CASE_INSENSITIVE
            );
            List<String> forbiddenEntries = new ArrayList<>();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if ((name.startsWith("org/glavo/sassfx/")
                        && !name.startsWith("org/glavo/sassfx/gradle/"))
                        || name.startsWith("com/google/errorprone/")
                        || name.startsWith("com/google/gson/")
                        || name.startsWith("org/gradle/")
                        || name.startsWith("groovy/")
                        || name.startsWith("org/codehaus/groovy/")
                        || name.startsWith("kotlin/")
                        || name.startsWith("javax/inject/")
                        || name.startsWith("org/slf4j/")
                        || name.startsWith("javafx/")
                        || name.startsWith("com/sun/javafx/")
                        || nativeEntryPattern.matcher(name).matches()) {
                    forbiddenEntries.add(name);
                }
            }
            if (!forbiddenEntries.isEmpty()) {
                throw new GradleException(
                        "The Gradle plugin JAR contains forbidden entries: "
                                + String.join(", ", forbiddenEntries)
                );
            }
        }
    }
}
