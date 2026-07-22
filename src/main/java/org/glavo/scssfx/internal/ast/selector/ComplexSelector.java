// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A complex selector made of compound components joined by combinators.
///
/// @param leadingCombinators combinators before the first compound
/// @param components         the ordered compounds and their trailing combinators
/// @param span               the complex selector span
@ApiStatus.Internal
@NotNullByDefault
public record ComplexSelector(
        @Unmodifiable List<Combinator> leadingCombinators,
        @Unmodifiable List<ComplexSelectorComponent> components,
        SourceSpan span
) {
    /// Creates a complex selector.
    ///
    /// @throws IllegalArgumentException if both lists are empty
    public ComplexSelector {
        leadingCombinators = List.copyOf(leadingCombinators);
        components = List.copyOf(components);
        if (leadingCombinators.isEmpty() && components.isEmpty()) {
            throw new IllegalArgumentException("complex selector must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    /// Returns whether this complex selector contains a parent selector.
    ///
    /// @return whether `&` is present
    public boolean containsParentSelector() {
        for (var component : components) {
            for (var simple : component.selector().components()) {
                if (simple instanceof ParentSelector) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Concatenates this complex selector with {@code child}.
    ///
    /// @param child the following complex selector
    /// @return the concatenated complex selector
    public ComplexSelector concatenate(ComplexSelector child) {
        Objects.requireNonNull(child, "child");
        if (components.isEmpty()) {
            var leading = new ArrayList<>(leadingCombinators);
            leading.addAll(child.leadingCombinators);
            return new ComplexSelector(leading, child.components, span);
        }
        if (child.components.isEmpty()) {
            var last = components.get(components.size() - 1);
            var nextComponents = new ArrayList<>(components.subList(0, components.size() - 1));
            nextComponents.add(last.withAdditionalCombinators(child.leadingCombinators));
            return new ComplexSelector(leadingCombinators, nextComponents, span);
        }

        var nextComponents = new ArrayList<>(components.subList(0, components.size() - 1));
        var last = components.get(components.size() - 1);
        if (!child.leadingCombinators.isEmpty()) {
            nextComponents.add(last.withAdditionalCombinators(child.leadingCombinators));
            nextComponents.addAll(child.components);
        } else {
            nextComponents.add(last);
            nextComponents.addAll(child.components);
        }
        return new ComplexSelector(leadingCombinators, nextComponents, span);
    }

    /// Returns this complex selector with additional trailing combinators on its last component.
    ///
    /// @param combinators the combinators to append
    /// @return the extended complex selector
    public ComplexSelector withAdditionalCombinators(List<Combinator> combinators) {
        if (combinators.isEmpty()) {
            return this;
        }
        if (components.isEmpty()) {
            var leading = new ArrayList<>(leadingCombinators);
            leading.addAll(combinators);
            return new ComplexSelector(leading, components, span);
        }
        var next = new ArrayList<>(components.subList(0, components.size() - 1));
        next.add(components.get(components.size() - 1).withAdditionalCombinators(combinators));
        return new ComplexSelector(leadingCombinators, next, span);
    }

    /// Returns the CSS text of this complex selector.
    ///
    /// @return the serialized complex selector
    /// @throws SassValueException if an unresolved parent selector remains
    public String toCssString() {
        var result = new StringBuilder();
        for (var combinator : leadingCombinators) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(combinator.css());
        }
        for (var index = 0; index < components.size(); index++) {
            var component = components.get(index);
            if (!result.isEmpty()) {
                result.append(' ');
            }
            for (var simple : component.selector().components()) {
                if (simple instanceof ParentSelector) {
                    throw new SassValueException(
                            "Parent selectors aren't allowed here."
                    );
                }
                result.append(simple.toCssString());
            }
            for (var combinator : component.combinators()) {
                result.append(' ').append(combinator.css());
            }
        }
        return result.toString();
    }
}
