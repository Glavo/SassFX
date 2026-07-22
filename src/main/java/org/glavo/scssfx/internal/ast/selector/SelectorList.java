// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.parse.SelectorParser;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A comma-separated list of complex selectors.
///
/// @param components the complex selectors in source order
/// @param span       the complete selector-list span
@ApiStatus.Internal
@NotNullByDefault
public record SelectorList(
        @Unmodifiable List<ComplexSelector> components,
        SourceSpan span
) {
    /// Creates a selector list.
    ///
    /// @throws IllegalArgumentException if {@code components} is empty
    public SelectorList {
        components = List.copyOf(components);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("components must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    /// Parses a selector list from resolved selector text.
    ///
    /// @param text the selector source after interpolation
    /// @param span the span covering that text
    /// @return the parsed selector list
    /// @throws SassValueException if the selector is invalid
    public static SelectorList parse(String text, SourceSpan span) {
        return SelectorParser.parse(text, span);
    }

    /// Nests this selector list within an optional parent selector list.
    ///
    /// @param parent the resolved parent selectors, or {@code null} at the root
    /// @return the nested selector list
    /// @throws SassValueException if nesting is invalid
    public SelectorList nestWithin(@Nullable SelectorList parent) {
        if (parent == null) {
            for (var complex : components) {
                if (complex.containsParentSelector()) {
                    // Top-level parent selectors without nesting are rejected
                    // when they carry a suffix; bare & is still invalid CSS.
                    throw new SassValueException(
                            "Top-level parent selectors aren't allowed."
                    );
                }
            }
            return this;
        }

        var result = new ArrayList<ComplexSelector>();
        for (var child : components) {
            result.addAll(nestComplex(parent, child));
        }
        return new SelectorList(result, span);
    }

    /// Returns the CSS text of this selector list.
    ///
    /// @return the comma-separated CSS selectors
    public String toCssString() {
        var result = new StringBuilder();
        for (var index = 0; index < components.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(components.get(index).toCssString());
        }
        return result.toString();
    }

    /// Nests one child complex selector within every parent complex selector.
    private static List<ComplexSelector> nestComplex(
            SelectorList parent,
            ComplexSelector child
    ) {
        if (!child.containsParentSelector()) {
            var result = new ArrayList<ComplexSelector>(parent.components().size());
            for (var parentComplex : parent.components()) {
                result.add(parentComplex.concatenate(child));
            }
            return result;
        }
        return nestWithExplicitParent(parent, child);
    }

    /// Resolves explicit parent selectors inside one child complex selector.
    private static List<ComplexSelector> nestWithExplicitParent(
            SelectorList parent,
            ComplexSelector child
    ) {
        @Nullable List<ComplexSelector> current = null;

        for (var component : child.components()) {
            @Nullable List<ComplexSelector> resolved = nestCompound(parent, component);
            if (current == null) {
                if (resolved == null) {
                    current = List.of(new ComplexSelector(
                            child.leadingCombinators(),
                            List.of(component),
                            child.span()
                    ));
                } else if (child.leadingCombinators().isEmpty()) {
                    current = resolved;
                } else {
                    current = new ArrayList<>(resolved.size());
                    for (var complex : resolved) {
                        var leading = new ArrayList<>(child.leadingCombinators());
                        leading.addAll(complex.leadingCombinators());
                        current.add(new ComplexSelector(
                                leading,
                                complex.components(),
                                complex.span()
                        ));
                    }
                }
                continue;
            }

            if (resolved == null) {
                var next = new ArrayList<ComplexSelector>(current.size());
                for (var complex : current) {
                    next.add(appendComponent(complex, component));
                }
                current = next;
                continue;
            }

            var next = new ArrayList<ComplexSelector>();
            for (var prefix : current) {
                for (var resolvedComplex : resolved) {
                    next.add(prefix.concatenate(resolvedComplex));
                }
            }
            current = next;
        }
        return current == null ? List.of() : current;
    }

    /// Nests one compound component against the parent list.
    ///
    /// @return the resolved complexes, or {@code null} when no parent injection applies
    private static @Nullable List<ComplexSelector> nestCompound(
            SelectorList parent,
            ComplexSelectorComponent component
    ) {
        var compound = component.selector();
        if (!(compound.components().get(0) instanceof ParentSelector parentSimple)) {
            return null;
        }

        if (compound.components().size() == 1 && parentSimple.suffix() == null) {
            var result = new ArrayList<ComplexSelector>(parent.components().size());
            for (var parentComplex : parent.components()) {
                result.add(parentComplex.withAdditionalCombinators(component.combinators()));
            }
            return result;
        }

        var remaining = compound.components().subList(1, compound.components().size());
        var result = new ArrayList<ComplexSelector>(parent.components().size());
        for (var parentComplex : parent.components()) {
            if (parentComplex.components().isEmpty()) {
                throw new SassValueException("Parent selector is invalid for nesting.");
            }
            var last = parentComplex.components().get(parentComplex.components().size() - 1);
            if (!last.combinators().isEmpty()) {
                throw new SassValueException(
                        "Parent \"" + parentComplex.toCssString()
                                + "\" is incompatible with this selector."
                );
            }

            var mergedSimples = new ArrayList<SimpleSelector>();
            var parentSimples = last.selector().components();
            if (parentSimple.suffix() == null) {
                mergedSimples.addAll(parentSimples);
            } else {
                if (parentSimples.isEmpty()) {
                    throw new SassValueException("Parent selector is invalid for nesting.");
                }
                mergedSimples.addAll(parentSimples.subList(0, parentSimples.size() - 1));
                mergedSimples.add(parentSimples.get(parentSimples.size() - 1)
                        .addSuffix(parentSimple.suffix()));
            }
            mergedSimples.addAll(remaining);

            var prefix = parentComplex.components().subList(
                    0,
                    parentComplex.components().size() - 1
            );
            var nextComponents = new ArrayList<>(prefix);
            nextComponents.add(new ComplexSelectorComponent(
                    new CompoundSelector(mergedSimples, compound.span()),
                    component.combinators(),
                    component.span()
            ));
            result.add(new ComplexSelector(
                    parentComplex.leadingCombinators(),
                    nextComponents,
                    component.span()
            ));
        }
        return result;
    }

    /// Appends a component to a complex selector.
    private static ComplexSelector appendComponent(
            ComplexSelector complex,
            ComplexSelectorComponent component
    ) {
        var next = new ArrayList<>(complex.components());
        next.add(component);
        return new ComplexSelector(complex.leadingCombinators(), next, complex.span());
    }
}
