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
        return SelectorParser.parse(text, span, false);
    }

    /// Parses a selector list with optional plain-CSS restrictions.
    ///
    /// @param text     the selector source after interpolation
    /// @param span     the span covering that text
    /// @param plainCss whether plain CSS selector restrictions apply
    /// @return the parsed selector list
    /// @throws SassValueException if the selector is invalid
    public static SelectorList parse(String text, SourceSpan span, boolean plainCss) {
        return SelectorParser.parse(text, span, plainCss);
    }

    /// Returns whether this list contains a parent-selector reference.
    ///
    /// @return whether one selector represents or contains {@code &}
    public boolean containsParentSelector() {
        for (var complex : components) {
            if (complex.containsParentSelector()) {
                return true;
            }
        }
        return false;
    }

    /// Returns the number of structurally represented parent selectors.
    ///
    /// @return the recursive parent-selector count
    public int parentSelectorCount() {
        var count = 0;
        for (var complex : components) {
            count += complex.parentSelectorCount();
        }
        return count;
    }

    /// Returns whether one parent selector has an identifier suffix.
    ///
    /// @return whether a direct or recursive parent selector has a suffix
    public boolean hasParentSelectorSuffix() {
        for (var complex : components) {
            if (complex.hasParentSelectorSuffix()) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether an opaque pseudo argument contains a parent marker.
    ///
    /// @return whether parent replacement must fail explicitly
    public boolean hasUnresolvedParentReference() {
        for (var complex : components) {
            if (complex.hasUnresolvedParentReference()) {
                return true;
            }
        }
        return false;
    }

    /// Replaces recursively represented parent selectors with {@code parent}.
    ///
    /// Selector branches that do not contain a parent selector remain unchanged.
    /// This operation never implicitly prefixes the supplied parent list.
    ///
    /// @param parent the selector list substituted for each {@code &}
    /// @return a selector list with recursive parent references resolved
    /// @throws SassValueException if an opaque argument contains {@code &}
    public SelectorList replaceParentSelectors(SelectorList parent) {
        Objects.requireNonNull(parent, "parent");
        if (hasUnresolvedParentReference()) {
            throw unresolvedParentReference();
        }
        if (!containsParentSelector()) {
            return this;
        }

        var result = new ArrayList<ComplexSelector>();
        for (var complex : components) {
            var replaced = replaceNestedParentSelectors(complex, parent);
            if (replaced.containsDirectParentSelector()) {
                result.addAll(nestWithExplicitParent(parent, replaced));
            } else {
                result.add(replaced);
            }
        }
        return new SelectorList(result, span);
    }

    /// Nests this selector list within an optional parent selector list.
    ///
    /// Implicit parent concatenation is enabled.
    ///
    /// @param parent the resolved parent selectors, or {@code null} at the root
    /// @return the nested selector list
    /// @throws SassValueException if nesting is invalid
    public SelectorList nestWithin(@Nullable SelectorList parent) {
        return nestWithin(parent, true);
    }

    /// Nests this selector list within an optional parent selector list.
    ///
    /// When {@code implicitParent} is {@code false}, complexes without an
    /// explicit parent reference are left unchanged. Explicit {@code &}
    /// references still resolve against {@code parent} when it is non-null.
    ///
    /// @param parent         the resolved parent selectors, or {@code null} at the root
    /// @param implicitParent whether parentless complexes should append to {@code parent}
    /// @return the nested selector list
    /// @throws SassValueException if nesting is invalid
    public SelectorList nestWithin(
            @Nullable SelectorList parent,
            boolean implicitParent
    ) {
        if (hasUnresolvedParentReference()) {
            throw unresolvedParentReference();
        }
        if (parent == null) {
            if (containsParentSelector()) {
                throw new SassValueException("Top-level parent selectors aren't allowed.");
            }
            return this;
        }

        // Parent-major product order matches dart-sass selector.nest/append:
        // nest("a, b", "c, d") → "a c, a d, b c, b d". Nested {@code &} inside
        // selector pseudos still resolve against the full parent list at once
        // (nest("a, b", ":is(&)") → ":is(a, b)"). Complexes with multiple direct
        // {@code &} also use the full parent list so each {@code &} multiplies
        // independently (nest("c, d", "&.e &.f") → "c.e c.f, c.e d.f, ...").
        var result = new ArrayList<ComplexSelector>();
        for (var child : components) {
            if (!implicitParent && !child.containsParentSelector()) {
                result.add(child);
            } else if (child.containsParentSelector()
                    && !child.containsDirectParentSelector()) {
                result.addAll(nestComplex(parent, child));
            } else if (child.containsDirectParentSelector()
                    && child.parentSelectorCount() > 1) {
                result.addAll(nestComplex(parent, child));
            }
        }
        for (var parentComplex : parent.components()) {
            var singleParent = new SelectorList(List.of(parentComplex), parent.span());
            for (var child : components) {
                if (!implicitParent && !child.containsParentSelector()) {
                    continue;
                }
                if (child.containsParentSelector()
                        && !child.containsDirectParentSelector()) {
                    continue;
                }
                if (child.containsDirectParentSelector()
                        && child.parentSelectorCount() > 1) {
                    continue;
                }
                result.addAll(nestComplex(singleParent, child));
            }
        }
        return new SelectorList(result, span);
    }

    /// Returns whether every complex selector in this list is CSS-invisible.
    ///
    /// An empty list is treated as invisible.
    ///
    /// @return whether CSS emission omits this entire list
    public boolean isInvisible() {
        if (components.isEmpty()) {
            return true;
        }
        for (var complex : components) {
            if (!complex.isInvisible()) {
                return false;
            }
        }
        return true;
    }

    /// Returns the CSS text of this selector list, retaining placeholders.
    ///
    /// @return the comma-separated CSS selectors
    public String toCssString() {
        return toCssString(true);
    }

    /// Returns the CSS text of this selector list.
    ///
    /// When {@code inspect} is {@code false}, invisible complexes are dropped and
    /// remaining complexes are serialized without placeholder selectors.
    ///
    /// @param inspect whether placeholder selectors and full structure are retained
    /// @return the comma-separated CSS selectors
    public String toCssString(boolean inspect) {
        var result = new StringBuilder();
        var first = true;
        for (var complex : components) {
            if (!inspect && (complex.isInvisible() || complex.isBogus())) {
                continue;
            }
            if (!first) {
                result.append(", ");
            }
            first = false;
            result.append(complex.toCssString(inspect));
        }
        return result.toString();
    }

    /// Nests one child complex selector within every parent complex selector.
    ///
    /// @param parent the parent selector list
    /// @param child  the child complex selector
    /// @return the nested complex selectors
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

        var replaced = replaceNestedParentSelectors(child, parent);
        if (!replaced.containsDirectParentSelector()) {
            return List.of(replaced);
        }
        return nestWithExplicitParent(parent, replaced);
    }

    /// Replaces parent references nested in pseudo-selector arguments.
    ///
    /// Direct parent selectors remain in place for explicit-parent nesting.
    ///
    /// @param child  the complex selector to transform
    /// @param parent the replacement parent list
    /// @return {@code child} with recursive pseudo arguments transformed
    private static ComplexSelector replaceNestedParentSelectors(
            ComplexSelector child,
            SelectorList parent
    ) {
        var nextComponents = new ArrayList<ComplexSelectorComponent>(child.components().size());
        var changed = false;
        for (var component : child.components()) {
            var simples = component.selector().components();
            var nextSimples = new ArrayList<SimpleSelector>(simples.size());
            var componentChanged = false;
            for (var simple : simples) {
                SimpleSelector replacement = simple;
                if (simple instanceof PseudoSelector pseudo && pseudo.containsParentSelector()) {
                    replacement = pseudo.replaceParentSelectors(parent);
                    componentChanged = true;
                }
                nextSimples.add(replacement);
            }
            if (componentChanged) {
                nextComponents.add(new ComplexSelectorComponent(
                        new CompoundSelector(nextSimples, component.selector().span()),
                        component.combinators(),
                        component.span()
                ));
                changed = true;
            } else {
                nextComponents.add(component);
            }
        }
        return changed ? new ComplexSelector(
                child.leadingCombinators(),
                nextComponents,
                child.span()
        ) : child;
    }

    /// Resolves explicit direct parent selectors inside one child complex selector.
    ///
    /// @param parent the replacement parent list
    /// @param child  the child complex selector
    /// @return the resolved complex selectors
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
    /// @param parent    the replacement parent list
    /// @param component the compound component to inspect
    /// @return the resolved complexes, or {@code null} when no direct parent starts it
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
    ///
    /// @param complex   the existing complex selector
    /// @param component the component to append
    /// @return the extended complex selector
    private static ComplexSelector appendComponent(
            ComplexSelector complex,
            ComplexSelectorComponent component
    ) {
        var next = new ArrayList<>(complex.components());
        next.add(component);
        return new ComplexSelector(complex.leadingCombinators(), next, complex.span());
    }

    /// Creates the diagnostic for a parent marker in opaque pseudo content.
    ///
    /// @return the value exception to throw
    private static SassValueException unresolvedParentReference() {
        return new SassValueException(
                "Parent selectors in non-selector pseudo arguments aren't supported."
        );
    }
}