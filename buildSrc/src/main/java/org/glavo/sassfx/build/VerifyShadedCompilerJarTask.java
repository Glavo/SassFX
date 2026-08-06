// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/// Verifies the manifest, contents, and dependency isolation of a shaded compiler JAR.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyShadedCompilerJarTask extends DefaultTask {
    /// Returns the shaded JAR to inspect.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    /// Returns the required main class.
    @Input
    public abstract Property<String> getExpectedMainClass();

    /// Returns the artifact name used in diagnostics.
    @Input
    public abstract Property<String> getArtifactName();

    /// Returns the entries that must be present in the JAR.
    @Input
    public abstract ListProperty<String> getRequiredEntries();

    /// Inspects the configured shaded compiler JAR.
    @TaskAction
    public final void verify() throws IOException {
        String artifact = getArtifactName().get();
        @Unmodifiable List<Pattern> forbiddenEntryPatterns = List.of(
                Pattern.compile("(^|.*/)javafx/.*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(^|.*/)com/sun/javafx/.*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("^com/google/errorprone/.*"),
                Pattern.compile("^com/google/gson/.*"),
                Pattern.compile("^com/google/protobuf/.*"),
                Pattern.compile("^com/sass_lang/embedded_protocol/.*"),
                Pattern.compile(
                        ".*\\.(a|dll|dylib|exe|jnilib|lib|node|wasm)$",
                        Pattern.CASE_INSENSITIVE
                ),
                Pattern.compile(".*\\.so(?:\\.\\d+)*$", Pattern.CASE_INSENSITIVE),
                Pattern.compile(
                        ".*\\.(dart|js|mjs|cjs)$",
                        Pattern.CASE_INSENSITIVE
                )
        );
        @Unmodifiable List<String> forbiddenClassReferences = List.of(
                "javafx/",
                "com/sun/javafx/",
                "java/lang/foreign/",
                "jdk/incubator/foreign/",
                "com/sun/jna/",
                "com/sun/jnr/",
                "jnr/ffi/",
                "com/kenai/jffi/"
        );
        List<String> forbiddenEntries = new ArrayList<>();
        List<String> forbiddenReferences = new ArrayList<>();

        try (JarFile jar = new JarFile(getArchiveFile().get().getAsFile())) {
            @Nullable Manifest manifest = jar.getManifest();
            @Nullable String mainClass = manifest == null
                    ? null
                    : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (!getExpectedMainClass().get().equals(mainClass)) {
                throw new GradleException(
                        artifact + " has an unexpected Main-Class: " + mainClass
                );
            }

            List<String> missingEntries = new ArrayList<>();
            for (String entry : getRequiredEntries().get()) {
                if (jar.getEntry(entry) == null) {
                    missingEntries.add(entry);
                }
            }
            if (!missingEntries.isEmpty()) {
                throw new GradleException(
                        artifact + " is missing required entries: "
                                + String.join(", ", missingEntries)
                );
            }

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                for (Pattern pattern : forbiddenEntryPatterns) {
                    if (pattern.matcher(entry.getName()).matches()) {
                        forbiddenEntries.add(entry.getName());
                        break;
                    }
                }
                if (entry.getName().endsWith(".class")) {
                    String contents;
                    try (var input = jar.getInputStream(entry)) {
                        contents = new String(
                                input.readAllBytes(),
                                StandardCharsets.ISO_8859_1
                        );
                    }
                    for (String reference : forbiddenClassReferences) {
                        if (contents.contains(reference)) {
                            forbiddenReferences.add(
                                    entry.getName() + ": " + reference
                            );
                        }
                    }
                }
            }
        }

        if (!forbiddenEntries.isEmpty()) {
            throw new GradleException(
                    artifact + " contains forbidden entries: "
                            + String.join(", ", forbiddenEntries)
            );
        }
        if (!forbiddenReferences.isEmpty()) {
            throw new GradleException(
                    artifact + " contains forbidden class references: "
                            + String.join(", ", forbiddenReferences)
            );
        }
    }
}
