// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a Sass placeholder selector.
///
/// Placeholder selectors are retained in the selector AST even though they do
/// not directly produce ordinary CSS without an extension relationship.
///
/// @param name the placeholder identifier
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record PlaceholderSelector(CssIdentifier name, SourceSpan span) implements SimpleSelector {
    /// Creates a placeholder selector.
    public PlaceholderSelector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }

    /// Creates a placeholder selector from a decoded identifier.
    ///
    /// @param name the decoded placeholder name
    /// @param span the source span
    public PlaceholderSelector(String name, SourceSpan span) {
        this(CssIdentifier.of(name), span);
    }

    @Override
    public String toCssString() {
        return "%" + name.toCssString();
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    /// Appends an identifier suffix to this placeholder name.
    ///
    /// Nested forms such as {@code %foo { &bar { … } }} become {@code %foobar},
    /// matching dart-sass {@code PlaceholderSelector.addSuffix}.
    ///
    /// @param suffix the identifier suffix after a parent selector
    /// @return a placeholder with the concatenated name
    @Override
    public SimpleSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return new PlaceholderSelector(name.append(suffix), span);
    }
}
