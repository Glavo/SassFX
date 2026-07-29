// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/// Verifies that committed project inputs do not expose local reference checkouts.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyReferenceIsolationTask extends DefaultTask {
    /// Returns the project inputs to scan.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    /// Returns the repository root used to identify absolute local paths.
    @Internal
    public abstract DirectoryProperty getRootDirectory();

    /// Scans the configured project inputs.
    @TaskAction
    public final void verify() throws IOException {
        File root = getRootDirectory().get().getAsFile();
        String absoluteRoot = root.getAbsolutePath();
        @Unmodifiable List<String> forbiddenReferences = List.of(
                "external" + "/",
                "external" + "\\",
                absoluteRoot,
                absoluteRoot.replace(File.separatorChar, '/')
        );
        List<String> violations = new ArrayList<>();
        for (File file : getSourceFiles().getFiles()) {
            if (!file.isFile()) {
                continue;
            }
            List<String> lines = Files.readAllLines(
                    file.toPath(),
                    StandardCharsets.UTF_8
            );
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                for (String reference : forbiddenReferences) {
                    if (containsIgnoreCase(line, reference)) {
                        String relativePath = root.toPath()
                                .relativize(file.toPath())
                                .toString()
                                .replace(File.separatorChar, '/');
                        violations.add(
                                relativePath + ":" + (index + 1) + ": " + reference
                        );
                        break;
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new GradleException(
                    "Project inputs contain forbidden local references:\n"
                            + String.join("\n", violations)
            );
        }
    }

    /// Returns whether `text` contains `fragment`, ignoring case.
    ///
    /// @param text the text to search
    /// @param fragment the fragment to find
    /// @return whether the fragment occurs in the text
    private static boolean containsIgnoreCase(String text, String fragment) {
        int maximumStart = text.length() - fragment.length();
        for (int index = 0; index <= maximumStart; index++) {
            if (text.regionMatches(true, index, fragment, 0, fragment.length())) {
                return true;
            }
        }
        return false;
    }
}
