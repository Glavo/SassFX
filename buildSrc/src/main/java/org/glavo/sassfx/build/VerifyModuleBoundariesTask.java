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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

/// Verifies the supported API boundary between core and frontend modules.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyModuleBoundariesTask extends DefaultTask {
    /// Returns production Java sources from frontend modules.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    /// Returns top-level core sources that define the supported public API.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getPublicApiSourceFiles();

    /// Returns the repository root used to report relative source paths.
    @Internal
    public abstract DirectoryProperty getRootDirectory();

    /// Rejects frontend references to core internals and public API escape
    /// hatches.
    ///
    /// @throws IOException if a configured source file cannot be read
    @TaskAction
    public final void verify() throws IOException {
        var root = getRootDirectory().get().getAsFile().toPath();
        var violations = new ArrayList<String>();
        for (var file : getSourceFiles().getFiles()) {
            if (!file.isFile()) {
                continue;
            }
            var relativePath = root.relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');
            var lines = Files.readAllLines(
                    file.toPath(),
                    StandardCharsets.UTF_8
            );
            for (var index = 0; index < lines.size(); index++) {
                @Nullable String internalType =
                        internalReference(lines.get(index));
                if (internalType != null) {
                    violations.add(
                            relativePath + ":" + (index + 1)
                                    + ": frontend reference to "
                                    + internalType
                    );
                }
            }
        }
        for (var file : getPublicApiSourceFiles().getFiles()) {
            if (!file.isFile()) {
                continue;
            }
            var relativePath = root.relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');
            var lines = Files.readAllLines(
                    file.toPath(),
                    StandardCharsets.UTF_8
            );
            for (var index = 0; index < lines.size(); index++) {
                if (lines.get(index).contains("ApiStatus.Internal")) {
                    violations.add(
                            relativePath + ":" + (index + 1)
                                    + ": supported public packages must not "
                                    + "declare internal API escape hatches"
                    );
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new GradleException(
                    "Unsupported API boundary violations:\n"
                            + String.join("\n", violations)
            );
        }
    }

    /// Extracts a core-internal qualified name from one source line.
    ///
    /// @param line one source line
    /// @return the referenced internal name, or `null`
    private static @Nullable String internalReference(String line) {
        var prefix = "org.glavo.sassfx.internal.";
        var start = line.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        var end = start + prefix.length();
        while (end < line.length()) {
            var character = line.charAt(end);
            if (character != '.'
                    && character != '*'
                    && !Character.isJavaIdentifierPart(character)) {
                break;
            }
            end++;
        }
        return line.substring(start, end);
    }
}
