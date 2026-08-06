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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/// Verifies that a release version is stable and matches repository metadata.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyReleaseVersionTask extends DefaultTask {
    /// Returns the version to validate.
    @Input
    public abstract Property<String> getReleaseVersion();

    /// Returns the release version declared by the repository.
    @Input
    public abstract Property<String> getBaseVersion();

    /// Returns the changelog whose release heading must match the version.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getChangelogFile();

    /// Validates the configured release version and its changelog entry.
    ///
    /// @throws IOException if the changelog cannot be read
    @TaskAction
    public final void verify() throws IOException {
        String version = getReleaseVersion().get();
        if (!version.matches("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?")
                || version.endsWith("-SNAPSHOT")) {
            throw new GradleException(
                    "A release requires -PsassfxVersion=<stable semantic version>; "
                            + "received '" + version + "'."
            );
        }

        String baseVersion = getBaseVersion().get();
        if (!version.equals(baseVersion)) {
            throw new GradleException(
                    "Release version '" + version
                            + "' does not match gradle/version.properties version '"
                            + baseVersion + "'."
            );
        }

        String changelog = Files.readString(
                getChangelogFile().get().getAsFile().toPath(),
                StandardCharsets.UTF_8
        );
        Pattern releaseHeading = Pattern.compile(
                "(?m)^## " + Pattern.quote(version)
                        + " — \\d{4}-\\d{2}-\\d{2}$"
        );
        if (!releaseHeading.matcher(changelog).find()) {
            throw new GradleException(
                    "CHANGELOG.md must contain a dated release heading for "
                            + version + "."
            );
        }
    }
}
