// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Contains one plain-text interpolation part.
///
/// @param text the text represented by this part
@ApiStatus.Internal
@NotNullByDefault
public record TextInterpolationPart(String text) implements InterpolationPart {
    /// Creates a plain-text interpolation part.
    public TextInterpolationPart {
        Objects.requireNonNull(text, "text");
    }

    /// Returns this part's plain text.
    ///
    /// @return the plain text
    @Override
    public String toString() {
        return text;
    }
}
