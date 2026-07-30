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
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;

/// Verifies that frontend modules use only supported core API boundaries.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyModuleBoundariesTask extends DefaultTask {
    /// Core implementation types intentionally used by the embedded protocol
    /// value bridge.
    private static final @Unmodifiable Set<String>
            EMBEDDED_INTERNAL_IMPORTS = Set.of(
                    "org.glavo.sassfx.internal.callable.CustomFunctionCallable",
                    "org.glavo.sassfx.internal.callable.FatalSassCallbackException",
                    "org.glavo.sassfx.internal.value.CalculationOperation",
                    "org.glavo.sassfx.internal.value.SassArgumentList",
                    "org.glavo.sassfx.internal.value.SassBoolean",
                    "org.glavo.sassfx.internal.value.SassCalculation",
                    "org.glavo.sassfx.internal.value.SassColor",
                    "org.glavo.sassfx.internal.value.SassFunction",
                    "org.glavo.sassfx.internal.value.SassList",
                    "org.glavo.sassfx.internal.value.SassMap",
                    "org.glavo.sassfx.internal.value.SassMixin",
                    "org.glavo.sassfx.internal.value.SassNull",
                    "org.glavo.sassfx.internal.value.SassNumber",
                    "org.glavo.sassfx.internal.value.SassString",
                    "org.glavo.sassfx.internal.value.SassValueException",
                    "org.glavo.sassfx.internal.value.color.ColorSpace"
            );

    /// Returns production Java sources from frontend modules.
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    /// Returns the repository root used to classify module paths.
    @Internal
    public abstract DirectoryProperty getRootDirectory();

    /// Scans frontend imports and rejects unsupported core dependencies.
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
                @Nullable String importedType =
                        internalImport(lines.get(index));
                if (importedType != null
                        && isForbidden(relativePath, importedType)) {
                    violations.add(
                            relativePath + ":" + (index + 1) + ": "
                                    + importedType
                    );
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new GradleException(
                    "Frontend modules import unsupported core internals:\n"
                            + String.join("\n", violations)
            );
        }
    }

    /// Extracts a core-internal type from one Java import statement.
    ///
    /// @param line one source line
    /// @return the imported internal type, or `null`
    private static @Nullable String internalImport(String line) {
        var statement = line.strip();
        if (!statement.startsWith("import ")
                || !statement.endsWith(";")) {
            return null;
        }
        var importedType = statement.substring(
                "import ".length(),
                statement.length() - 1
        );
        if (importedType.startsWith("static ")) {
            importedType = importedType.substring("static ".length());
        }
        return importedType.startsWith("org.glavo.sassfx.internal.")
                ? importedType
                : null;
    }

    /// Reports whether one internal import crosses a protected boundary.
    ///
    /// CLI and Gradle plugin code must use only supported APIs. The embedded
    /// protocol module has a narrow allowlist for its value-translation bridge.
    ///
    /// @param relativePath repository-relative source path
    /// @param importedType imported core-internal type
    /// @return whether the import violates the module boundary
    private static boolean isForbidden(
            String relativePath,
            String importedType
    ) {
        if (relativePath.startsWith("sassfx-cli/")
                || relativePath.startsWith("sassfx-gradle-plugin/")) {
            return true;
        }
        return relativePath.startsWith("sassfx-embedded/")
                && !EMBEDDED_INTERNAL_IMPORTS.contains(importedType);
    }
}
