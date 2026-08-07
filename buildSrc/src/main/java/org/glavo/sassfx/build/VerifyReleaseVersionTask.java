// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;

/// Verifies that a release version is stable and follows the supported syntax.
@NotNullByDefault
@DisableCachingByDefault(because = "Verification tasks have no outputs.")
public abstract class VerifyReleaseVersionTask extends DefaultTask {
    /// Returns the version to validate.
    @Input
    public abstract Property<String> getReleaseVersion();

    /// Validates the configured release version.
    @TaskAction
    public final void verify() {
        String version = getReleaseVersion().get();
        if (!version.matches("(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)")) {
            throw new GradleException(
                    "A release requires -PsassfxVersion=<stable semantic version>; "
                            + "received '" + version + "'."
            );
        }
    }
}
