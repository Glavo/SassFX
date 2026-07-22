// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Configures JavaFX-compatible textual CSS output.
///
/// @param compatibility the JavaFX compatibility level
/// @param style the formatting style applied to the generated stylesheet
@NotNullByDefault
public record JavaFxCssTarget(
        JavaFxCompatibility compatibility,
        OutputStyle style
) implements OutputTarget<String> {
    /// The default expanded target compatible with JavaFX 17.
    public static final JavaFxCssTarget DEFAULT =
            new JavaFxCssTarget(JavaFxCompatibility.JAVA_FX_17, OutputStyle.EXPANDED);

    /// Creates a JavaFX CSS output target.
    public JavaFxCssTarget {
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(style, "style");
    }
}
