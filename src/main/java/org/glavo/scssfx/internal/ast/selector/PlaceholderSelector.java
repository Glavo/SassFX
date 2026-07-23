// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
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
    public SimpleSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        throw new SassValueException("Placeholder selector can't have a suffix.");
    }
}
