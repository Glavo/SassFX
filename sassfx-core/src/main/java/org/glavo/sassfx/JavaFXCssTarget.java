// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Configures textual CSS intended for JavaFX stylesheet consumers.
///
/// This target does not load JavaFX classes. Before serialization, the compiler
/// rejects CSS structures, media conditions, imports, and declarations that
/// cannot preserve their intended meaning at the selected [#javaFXTarget()]
/// level.
///
/// @param javaFXTarget the JavaFX release targeted by the stylesheet
/// @param style the formatting style applied to the generated stylesheet
/// @param charset whether non-ASCII output begins with an expanded
///                {@code @charset} declaration or a compressed UTF-8 BOM
@NotNullByDefault
public record JavaFXCssTarget(
        JavaFXTarget javaFXTarget,
        OutputStyle style,
        boolean charset
) implements OutputTarget<String> {
    /// The default expanded target compatible with JavaFX 17.
    public static final JavaFXCssTarget DEFAULT =
            new JavaFXCssTarget(
                    JavaFXTarget.JAVAFX17,
                    OutputStyle.EXPANDED,
                    true
            );

    /// Creates a JavaFX CSS target with charset emission enabled.
    ///
    /// @param javaFXTarget the JavaFX release targeted by the stylesheet
    /// @param style the formatting style applied to the generated stylesheet
    public JavaFXCssTarget(JavaFXTarget javaFXTarget, OutputStyle style) {
        this(javaFXTarget, style, true);
    }

    /// Creates a JavaFX CSS output target.
    public JavaFXCssTarget {
        Objects.requireNonNull(javaFXTarget, "javaFXTarget");
        Objects.requireNonNull(style, "style");
    }
}
