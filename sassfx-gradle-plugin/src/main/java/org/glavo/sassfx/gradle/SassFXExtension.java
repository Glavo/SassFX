// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNullByDefault;

/// Configures the default [SassFXCompile] task registered by [SassFXPlugin].
///
/// Every property is connected lazily to `compileScss`. Configuring an
/// individual task property overrides the corresponding extension convention.
@NotNullByDefault
public abstract class SassFXExtension {
    /// Creates the extension.
    public SassFXExtension() {
    }

    /// Returns the directory containing root stylesheets and their partials.
    ///
    /// @return the source directory property
    public abstract DirectoryProperty getSourceDirectory();

    /// Returns the directory that receives generated stylesheets.
    ///
    /// @return the output directory property
    public abstract DirectoryProperty getOutputDirectory();

    /// Returns additional filesystem roots searched for Sass imports.
    ///
    /// @return the configurable load-path collection
    public abstract ConfigurableFileCollection getLoadPaths();

    /// Returns the output target selector.
    ///
    /// Supported values are `css`, `css/javafx@8` through
    /// `css/javafx@27`, and `bss/javafx@8` through
    /// `bss/javafx@27`.
    ///
    /// @return the target property
    public abstract Property<String> getTarget();

    /// Returns the CSS formatting style.
    ///
    /// Supported values are `expanded` and `compressed`. BSS output requires
    /// the default `expanded` value because formatting does not apply to binary
    /// output.
    ///
    /// @return the output-style property
    public abstract Property<String> getStyle();

    /// Returns whether textual non-ASCII output receives a charset marker.
    ///
    /// @return the charset-emission property
    public abstract Property<Boolean> getCharset();
}
