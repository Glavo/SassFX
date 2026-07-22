// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// An opaque simple selector retained as CSS text.
///
/// This first implementation uses opaque text for pseudo-classes, attributes,
/// placeholders, and namespaced forms that are not modeled explicitly yet.
///
/// @param css  the CSS spelling
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record OtherSimpleSelector(String css, SourceSpan span) implements SimpleSelector {
    /// Creates an opaque simple selector.
    public OtherSimpleSelector {
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
    public SimpleSelector addSuffix(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        if (css.startsWith(":") || css.startsWith("[") || css.startsWith("%")) {
            throw new SassValueException("Selector " + css + " can't have a suffix.");
        }
        return new OtherSimpleSelector(css + suffix, span);
    }
}
