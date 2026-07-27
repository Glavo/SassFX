// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Configures standards-compliant CSS text output.
///
/// @param style the formatting style applied to the generated CSS
/// @param charset whether non-ASCII output begins with an expanded
///                {@code @charset} declaration or a compressed UTF-8 BOM
@NotNullByDefault
public record CssTarget(OutputStyle style, boolean charset) implements OutputTarget<String> {
    /// The default expanded CSS target with charset emission enabled.
    public static final CssTarget DEFAULT = new CssTarget(OutputStyle.EXPANDED, true);

    /// Creates a CSS output target.
    public CssTarget {
        Objects.requireNonNull(style, "style");
    }
}
