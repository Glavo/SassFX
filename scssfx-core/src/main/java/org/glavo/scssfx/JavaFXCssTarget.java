// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Configures textual CSS intended for JavaFX stylesheet consumers.
///
/// This target does not load JavaFX classes. Before serialization, the compiler
/// rejects CSS structures, media conditions, imports, and declarations that
/// cannot preserve their intended meaning at the selected [#compatibility()]
/// level.
///
/// @param compatibility the JavaFX stylesheet compatibility level
/// @param style the formatting style applied to the generated stylesheet
@NotNullByDefault
public record JavaFXCssTarget(
        JavaFXCompatibility compatibility,
        OutputStyle style
) implements OutputTarget<String> {
    /// The default expanded target compatible with JavaFX 17.
    public static final JavaFXCssTarget DEFAULT =
            new JavaFXCssTarget(JavaFXCompatibility.JAVAFX17, OutputStyle.EXPANDED);

    /// Creates a JavaFX CSS output target.
    public JavaFXCssTarget {
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(style, "style");
    }
}
