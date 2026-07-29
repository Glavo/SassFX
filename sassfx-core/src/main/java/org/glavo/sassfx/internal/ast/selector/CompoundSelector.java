// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// A compound selector composed of one or more simple selectors.
///
/// @param components the simple selectors in source order
/// @param span       the compound span
@ApiStatus.Internal
@NotNullByDefault
public record CompoundSelector(
        @Unmodifiable List<SimpleSelector> components,
        SourceSpan span
) {
    /// Creates a compound selector.
    ///
    /// @throws IllegalArgumentException if {@code components} is empty
    public CompoundSelector {
        components = List.copyOf(components);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("components must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    /// Returns the CSS text of this compound selector.
    ///
    /// @return the concatenated simple selectors
    public String toCssString() {
        var result = new StringBuilder();
        for (var component : components) {
            result.append(component.toCssString());
        }
        return result.toString();
    }

    /// Returns whether any simple selector in this compound is CSS-invisible.
    ///
    /// @return whether this compound makes its enclosing complex selector invisible
    public boolean isInvisible() {
        for (var component : components) {
            if (component.isInvisible()) {
                return true;
            }
        }
        return false;
    }

}
