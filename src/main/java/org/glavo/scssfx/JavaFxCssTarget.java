// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Configures JavaFX-compatible textual CSS output.
///
/// @param compatibility the JavaFX compatibility level
/// @param style the formatting style applied to the generated stylesheet
@NotNullByDefault
public record JavaFXCssTarget(
        JavaFXCompatibility compatibility,
        OutputStyle style
) implements OutputTarget<String> {
    /// The default expanded target compatible with JavaFX 17.
    public static final JavaFXCssTarget DEFAULT =
            new JavaFXCssTarget(JavaFXCompatibility.JAVA_FX_17, OutputStyle.EXPANDED);

    /// Creates a JavaFX CSS output target.
    public JavaFXCssTarget {
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(style, "style");
    }
}
