// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;

/// Fails with a configured message when an optional verification prerequisite is absent.
@NotNullByDefault
@DisableCachingByDefault(because = "This task always fails and has no outputs.")
public abstract class FailTask extends DefaultTask {
    /// Returns the failure message.
    @Input
    public abstract Property<String> getFailureMessage();

    /// Reports the configured failure.
    @TaskAction
    public final void fail() {
        throw new GradleException(getFailureMessage().get());
    }
}
