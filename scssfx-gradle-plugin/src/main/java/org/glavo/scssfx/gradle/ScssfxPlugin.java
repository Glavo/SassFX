// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.jetbrains.annotations.NotNullByDefault;

/// Adds Sass compilation to a Gradle project.
///
/// The plugin creates the `scssfx` extension and a `compileScss` task. When the
/// Java plugin is present, `processResources` copies the compiled stylesheets
/// and depends on their compilation.
@NotNullByDefault
public final class ScssfxPlugin implements Plugin<Project> {
    /// Creates the plugin implementation.
    public ScssfxPlugin() {
    }

    /// Applies SCSSFX conventions and registers the default compilation task.
    ///
    /// @param project the project receiving the plugin
    @Override
    public void apply(Project project) {
        var layout = project.getLayout();
        var extension = project.getExtensions().create(
                "scssfx",
                ScssfxExtension.class
        );
        extension.getSourceDirectory().convention(
                layout.getProjectDirectory().dir("src/main/scss")
        );
        extension.getOutputDirectory().convention(
                layout.getBuildDirectory().dir("generated/scssfx/main")
        );
        extension.getTarget().convention("css");
        extension.getStyle().convention("expanded");
        extension.getCharset().convention(true);

        TaskProvider<ScssfxCompile> compileScss = project.getTasks().register(
                "compileScss",
                ScssfxCompile.class,
                task -> {
                    task.setGroup("build");
                    task.setDescription(
                            "Compiles Sass stylesheets with SCSSFX."
                    );
                    task.getSourceDirectory().convention(
                            extension.getSourceDirectory()
                    );
                    task.getOutputDirectory().convention(
                            extension.getOutputDirectory()
                    );
                    task.getLoadPaths().from(extension.getLoadPaths());
                    task.getTarget().convention(extension.getTarget());
                    task.getStyle().convention(extension.getStyle());
                    task.getCharset().convention(extension.getCharset());
                }
        );

        project.getPluginManager().withPlugin(
                "java",
                ignored -> project.getTasks().named(
                        JavaPlugin.PROCESS_RESOURCES_TASK_NAME,
                        ProcessResources.class
                ).configure(task -> task.from(compileScss))
        );
    }
}
