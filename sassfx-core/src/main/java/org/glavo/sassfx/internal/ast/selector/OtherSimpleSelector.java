// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Retains a legacy simple selector that has not been structurally modeled.
///
/// The selector parser no longer creates this type for attributes, pseudo
/// selectors, placeholders, or namespaces. It remains available to preserve
/// compatibility with AST values created before those forms were modeled.
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
    public SimpleSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        if (css.startsWith(":") || css.startsWith("[") || css.startsWith("%")) {
            throw new SassValueException("Selector " + css + " can't have a suffix.");
        }
        return new OtherSimpleSelector(css + suffix.toCssString(), span);
    }
}
