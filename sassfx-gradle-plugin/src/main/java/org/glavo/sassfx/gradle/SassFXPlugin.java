// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.jetbrains.annotations.NotNullByDefault;

/// Adds Sass compilation to a Gradle project.
///
/// The plugin creates the `sassfx` extension and a `compileScss` task. When the
/// Java plugin is present, `processResources` copies the compiled stylesheets
/// and depends on their compilation.
@NotNullByDefault
public final class SassFXPlugin implements Plugin<Project> {
    /// Creates the plugin implementation.
    public SassFXPlugin() {
    }

    /// Applies SassFX conventions and registers the default compilation task.
    ///
    /// @param project the project receiving the plugin
    @Override
    public void apply(Project project) {
        var layout = project.getLayout();
        var extension = project.getExtensions().create(
                "sassfx",
                SassFXExtension.class
        );
        extension.getSourceDirectory().convention(
                layout.getProjectDirectory().dir("src/main/scss")
        );
        extension.getOutputDirectory().convention(
                layout.getBuildDirectory().dir("generated/sassfx/main")
        );
        extension.getTarget().convention("css");
        extension.getStyle().convention("expanded");
        extension.getCharset().convention(true);

        TaskProvider<SassFXCompile> compileScss = project.getTasks().register(
                "compileScss",
                SassFXCompile.class,
                task -> {
                    task.setGroup("build");
                    task.setDescription(
                            "Compiles Sass stylesheets with SassFX."
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
