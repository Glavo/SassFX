// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A type selector such as `div`.
///
/// @param name the element name
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record TypeSelector(String name, SourceSpan span) implements SimpleSelector {
    /// Creates a type selector.
    public TypeSelector {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    @Override
    public String toCssString() {
        return name;
    }

    @Override
    public TypeSelector addSuffix(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return new TypeSelector(name + suffix, span);
    }
}
