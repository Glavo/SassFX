// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a percentage selector in a CSS keyframe rule.
///
/// @param css  the CSS spelling
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record KeyframePercentageSelector(String css, SourceSpan span) implements SimpleSelector {
    /// Creates a keyframe percentage selector.
    public KeyframePercentageSelector {
        Objects.requireNonNull(css, "css");
        if (css.isEmpty()) {
            throw new IllegalArgumentException("css must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    @Override
    public String toCssString() {
        return css;
    }

    @Override
    public SimpleSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return new KeyframePercentageSelector(css + suffix.toCssString(), span);
    }
}
