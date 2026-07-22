// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
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

    /// Returns whether this compound begins with a parent selector.
    ///
    /// @return whether the first simple selector is `&`
    public boolean startsWithParent() {
        return components.get(0) instanceof ParentSelector;
    }

    /// Returns a compound with an additional simple selector appended.
    ///
    /// @param simple the simple selector to append
    /// @return the extended compound
    public CompoundSelector withAdditionalSimple(SimpleSelector simple) {
        var next = new ArrayList<>(components);
        next.add(Objects.requireNonNull(simple, "simple"));
        return new CompoundSelector(next, span);
    }
}
