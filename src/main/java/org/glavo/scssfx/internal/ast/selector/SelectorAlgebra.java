// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Implements selector algebra for the structurally modeled selector subset.
///
/// The selector AST retains identifier values, namespace forms, attributes,
/// pseudo selectors, placeholders, and parent selectors. This engine supports
/// type and universal selectors in every modeled namespace form, as well as
/// class, ID, attribute, placeholder, pseudo-class, and pseudo-element
/// constraints. Functional pseudo selectors retain recursively parsed selector
/// arguments whenever the parser can model their structure.
/// Transformation methods validate inputs so selector forms without modeled
/// algebra semantics are never treated as textually comparable.
@ApiStatus.Internal
@NotNullByDefault
public final class SelectorAlgebra {
    /// Limits the number of descendant-selector interleavings produced by one
    /// unification operation.
    private static final int MAX_WEAVE_RESULTS = 4_096;

    /// Prevents instantiation.
    private SelectorAlgebra() {
    }

    /// Verifies that {@code selector} can be evaluated by this algebra engine.
    ///
    /// Parent selectors, relative selectors, and repeated or trailing combinators
    /// are rejected because their algebra semantics are not yet modeled.
    ///
    /// @param selector the selector list to validate
    /// @param name     the Sass argument name used in diagnostics
    /// @throws SassValueException if the selector uses unsupported syntax
    public static void assertSupported(SelectorList selector, String name) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(name, "name");

        for (var complex : selector.components()) {
            if (!complex.leadingCombinators().isEmpty() || complex.components().isEmpty()) {
                throw unsupportedRelativeSelector(name);
            }
            for (var index = 0; index < complex.components().size(); index++) {
                var component = complex.components().get(index);
                if (component.combinators().size() > 1) {
                    throw unsupportedCombinators(name);
                }
                if (index == complex.components().size() - 1
                        && !component.combinators().isEmpty()) {
                    throw unsupportedCombinators(name);
                }
                assertSupportedCompound(component.selector(), name);
            }
        }
    }

    /// Verifies that every selector is a single compound target for extension.
    ///
    /// @param selector the extendee or original selector list
    /// @param name     the Sass argument name used in diagnostics
    /// @throws SassValueException if a target contains a combinator
    public static void assertCompoundTargets(SelectorList selector, String name) {
        assertSupported(selector, name);
        for (var complex : selector.components()) {
            if (complex.components().size() != 1
                    || !complex.components().get(0).combinators().isEmpty()) {
                throw new SassValueException("Can't extend complex selector "
                        + complex.toCssString() + ".");
            }
        }
    }

    /// Returns selectors that match elements selected by both inputs.
    ///
    /// The returned list preserves the outer source order of {@code selector1}
    /// and {@code selector2} and removes semantic duplicates. Returns
    /// {@code null} when every pair has an empty intersection.
    ///
    /// @param selector1 the first selector list
    /// @param selector2 the second selector list
    /// @return the unified selector list, or {@code null}
    /// @throws SassValueException if an input uses unsupported selector syntax
    public static @Nullable SelectorList unify(SelectorList selector1, SelectorList selector2) {
        assertSupported(selector1, "selector1");
        assertSupported(selector2, "selector2");

        var result = new ArrayList<ComplexSelector>();
        for (var complex1 : selector1.components()) {
            for (var complex2 : selector2.components()) {
                result.addAll(unifyComplex(complex1, complex2));
            }
        }
        var unique = deduplicate(result);
        return unique.isEmpty() ? null : new SelectorList(unique, selector1.span());
    }

    /// Returns whether {@code superselector} matches every element matched by
    /// {@code subselector} within the supported selector subset.
    ///
    /// @param superselector the candidate broader selector list
    /// @param subselector   the candidate narrower selector list
    /// @return whether every subselector component is covered
    /// @throws SassValueException if an input uses unsupported or
    ///                            non-comparable explicit combinators
    public static boolean isSuperselector(
            SelectorList superselector,
            SelectorList subselector
    ) {
        assertSupported(superselector, "super");
        assertSupported(subselector, "sub");

        for (var subComplex : subselector.components()) {
            var covered = false;
            var unsupported = false;
            for (var superComplex : superselector.components()) {
                @Nullable Boolean relation = complexIsSuperselector(superComplex, subComplex);
                if (Boolean.TRUE.equals(relation)) {
                    covered = true;
                    break;
                }
                if (relation == null) {
                    unsupported = true;
                }
            }
            if (!covered) {
                if (unsupported) {
                    throw unsupportedComparison();
                }
                return false;
            }
        }
        return true;
    }

    /// Extends each supported occurrence of {@code extendee} in {@code selector}
    /// with {@code extender}, retaining the original selectors.
    ///
    /// Extendee list entries are applied in source order. Later entries also
    /// transform alternatives introduced by earlier entries, matching Sass's
    /// list-target extension behavior for the represented subset.
    ///
    /// @param selector the selector list to extend
    /// @param extendee the single-compound targets to match
    /// @param extender the selector alternatives to insert
    /// @return the original and extended selector alternatives
    /// @throws SassValueException if an input uses unsupported selector syntax
    public static SelectorList extend(
            SelectorList selector,
            SelectorList extendee,
            SelectorList extender
    ) {
        assertSupported(selector, "selector");
        assertCompoundTargets(extendee, "extendee");
        assertSupported(extender, "extender");

        var current = new ArrayList<>(selector.components());
        for (var target : extendee.components()) {
            var targetCompound = target.components().get(0).selector();
            current = new ArrayList<>(transformTarget(
                    current,
                    targetCompound,
                    extender,
                    false
            ));
        }
        return new SelectorList(current, selector.span());
    }

    /// Replaces each supported occurrence of {@code original} in {@code selector}
    /// with {@code replacement}.
    ///
    /// Original list entries are applied in source order. A selector that has no
    /// occurrence of one original entry remains available to subsequent entries.
    ///
    /// @param selector    the selector list to transform
    /// @param original    the single-compound targets to replace
    /// @param replacement the selector alternatives to insert
    /// @return the transformed selector alternatives
    /// @throws SassValueException if an input uses unsupported selector syntax
    public static SelectorList replace(
            SelectorList selector,
            SelectorList original,
            SelectorList replacement
    ) {
        assertSupported(selector, "selector");
        assertCompoundTargets(original, "original");
        assertSupported(replacement, "replacement");

        var current = new ArrayList<>(selector.components());
        for (var target : original.components()) {
            var targetCompound = target.components().get(0).selector();
            current = new ArrayList<>(transformTarget(
                    current,
                    targetCompound,
                    replacement,
                    true
            ));
        }
        return new SelectorList(current, selector.span());
    }

    /// Unifies two supported complex selectors.
    ///
    /// @param selector1 the first complex selector
    /// @param selector2 the second complex selector
    /// @return every supported intersection in stable source order
    private static @Unmodifiable List<ComplexSelector> unifyComplex(
            ComplexSelector selector1,
            ComplexSelector selector2
    ) {
        if (!hasComplicatedPseudoSemantics(selector1)
                && !hasComplicatedPseudoSemantics(selector2)) {
            @Nullable Boolean firstIsSuperselector = complexIsSuperselector(selector1, selector2);
            @Nullable Boolean secondIsSuperselector = complexIsSuperselector(selector2, selector1);
            if (Boolean.TRUE.equals(firstIsSuperselector)
                    && !Boolean.TRUE.equals(secondIsSuperselector)) {
                return List.of(selector2);
            }
            if (Boolean.TRUE.equals(secondIsSuperselector)
                    && !Boolean.TRUE.equals(firstIsSuperselector)) {
                return List.of(selector1);
            }
        }

        // Equivalent selectors continue through compound merging so the first
        // input retains its CSS spelling while semantic duplicates are removed.
        if (isDescendantOnly(selector1) && isDescendantOnly(selector2)) {
            @Nullable CompoundSelector base = unifyCompound(
                    selector1.components().get(selector1.components().size() - 1).selector(),
                    selector2.components().get(selector2.components().size() - 1).selector()
            );
            if (base == null) {
                return List.of();
            }
            return weaveDescendantPrefixes(selector1, selector2, base);
        }

        if (sameTopology(selector1, selector2)) {
            var components = new ArrayList<ComplexSelectorComponent>();
            for (var index = 0; index < selector1.components().size(); index++) {
                var first = selector1.components().get(index);
                var second = selector2.components().get(index);
                @Nullable CompoundSelector unified = unifyCompound(first.selector(), second.selector());
                if (unified == null) {
                    return List.of();
                }
                components.add(new ComplexSelectorComponent(
                        unified,
                        first.combinators(),
                        first.span()
                ));
            }
            return List.of(new ComplexSelector(List.of(), components, selector1.span()));
        }

        throw unsupportedTopology(selector1, selector2);
    }

    /// Returns whether two complex selectors have identical component counts and
    /// combinators.
    ///
    /// @param selector1 the first complex selector
    /// @param selector2 the second complex selector
    /// @return whether component-wise unification preserves both paths
    private static boolean sameTopology(ComplexSelector selector1, ComplexSelector selector2) {
        if (selector1.components().size() != selector2.components().size()) {
            return false;
        }
        for (var index = 0; index + 1 < selector1.components().size(); index++) {
            if (!Objects.equals(relationAfter(selector1, index), relationAfter(selector2, index))) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether one selector contains a pseudo selector whose result
    /// cannot be reduced by the ordinary compound shortcut.
    ///
    /// @param selector the complex selector to inspect
    /// @return whether pseudo structure must be retained through unification
    private static boolean hasComplicatedPseudoSemantics(ComplexSelector selector) {
        for (var component : selector.components()) {
            for (var simple : component.selector().components()) {
                if (simple instanceof PseudoSelector pseudo
                        && (isPseudoElement(pseudo)
                        || pseudo.argument() instanceof SelectorPseudoArgument
                        || pseudo.argument() instanceof NthPseudoArgument)) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Returns whether every edge in {@code selector} is an implicit descendant
    /// combinator.
    ///
    /// @param selector the selector to inspect
    /// @return whether the selector contains no explicit combinator
    private static boolean isDescendantOnly(ComplexSelector selector) {
        for (var component : selector.components()) {
            if (!component.combinators().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /// Weaves the prefixes of two descendant-only selectors before their unified
    /// final compound.
    ///
    /// @param selector1 the first descendant selector
    /// @param selector2 the second descendant selector
    /// @param base      the unified final compound
    /// @return every order-preserving prefix interleaving
    private static @Unmodifiable List<ComplexSelector> weaveDescendantPrefixes(
            ComplexSelector selector1,
            ComplexSelector selector2,
            CompoundSelector base
    ) {
        var prefixes1 = selector1.components().subList(0, selector1.components().size() - 1);
        var prefixes2 = selector2.components().subList(0, selector2.components().size() - 1);
        var paths = new ArrayList<List<ComplexSelectorComponent>>();
        weavePrefixes(prefixes1, 0, prefixes2, 0, new ArrayList<>(), paths);

        var result = new ArrayList<ComplexSelector>(paths.size());
        for (var path : paths) {
            var components = new ArrayList<ComplexSelectorComponent>(path.size() + 1);
            for (var component : path) {
                components.add(new ComplexSelectorComponent(
                        component.selector(),
                        List.of(),
                        component.span()
                ));
            }
            var finalComponent = selector1.components().get(selector1.components().size() - 1);
            components.add(new ComplexSelectorComponent(base, List.of(), finalComponent.span()));
            result.add(new ComplexSelector(List.of(), components, selector1.span()));
        }
        return deduplicate(result);
    }

    /// Recursively emits all order-preserving interleavings of two prefix paths.
    ///
    /// @param first       the first prefix path
    /// @param firstIndex  the next component in the first path
    /// @param second      the second prefix path
    /// @param secondIndex the next component in the second path
    /// @param current     the partially woven path
    /// @param result      the output paths
    private static void weavePrefixes(
            List<ComplexSelectorComponent> first,
            int firstIndex,
            List<ComplexSelectorComponent> second,
            int secondIndex,
            ArrayList<ComplexSelectorComponent> current,
            ArrayList<List<ComplexSelectorComponent>> result
    ) {
        if (result.size() >= MAX_WEAVE_RESULTS) {
            throw new SassValueException("Selector algebra produced too many descendant interleavings.");
        }
        if (firstIndex == first.size() && secondIndex == second.size()) {
            result.add(List.copyOf(current));
            return;
        }
        if (firstIndex < first.size()) {
            current.add(first.get(firstIndex));
            weavePrefixes(first, firstIndex + 1, second, secondIndex, current, result);
            current.remove(current.size() - 1);
        }
        if (secondIndex < second.size()) {
            current.add(second.get(secondIndex));
            weavePrefixes(first, firstIndex, second, secondIndex + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    /// Determines whether one complex selector is a superselector of another.
    ///
    /// A {@code null} result means the current structural model cannot compare
    /// the two explicit-combinator paths without risking an incorrect answer.
    ///
    /// @param superselector the candidate broader selector
    /// @param subselector   the candidate narrower selector
    /// @return {@code true}, {@code false}, or {@code null} when unsupported
    private static @Nullable Boolean complexIsSuperselector(
            ComplexSelector superselector,
            ComplexSelector subselector
    ) {
        var superComponents = superselector.components();
        var subComponents = subselector.components();
        if (!compoundIsSuperselector(
                superComponents.get(superComponents.size() - 1).selector(),
                subComponents.get(subComponents.size() - 1).selector()
        )) {
            return false;
        }
        if (superComponents.size() == 1) {
            return true;
        }

        if (isDescendantOnly(superselector)) {
            return descendantPathIsSuperselector(superselector, subselector);
        }
        if (superComponents.size() != subComponents.size()) {
            return null;
        }
        for (var index = 0; index < superComponents.size(); index++) {
            if (!compoundIsSuperselector(
                    superComponents.get(index).selector(),
                    subComponents.get(index).selector()
            )) {
                return false;
            }
            if (index + 1 < superComponents.size() && !relationImplies(
                    relationAfter(subselector, index),
                    relationAfter(superselector, index)
            )) {
                return false;
            }
        }
        return true;
    }

    /// Determines whether a descendant-only selector is a superselector of a
    /// selector whose final element is already known to match.
    ///
    /// @param superselector the descendant-only broader selector
    /// @param subselector   the candidate narrower selector
    /// @return whether the ancestor requirements form an ordered subsequence
    private static boolean descendantPathIsSuperselector(
            ComplexSelector superselector,
            ComplexSelector subselector
    ) {
        var subComponents = subselector.components();
        var firstAncestor = 0;
        for (var index = 0; index + 1 < subComponents.size(); index++) {
            @Nullable Combinator relation = relationAfter(subselector, index);
            if (relation == Combinator.NEXT_SIBLING || relation == Combinator.FOLLOWING_SIBLING) {
                firstAncestor = index + 1;
            }
        }

        var required = 0;
        var superComponents = superselector.components();
        for (var index = firstAncestor; index + 1 < subComponents.size()
                && required + 1 < superComponents.size(); index++) {
            if (compoundIsSuperselector(
                    superComponents.get(required).selector(),
                    subComponents.get(index).selector()
            )) {
                required++;
            }
        }
        return required + 1 == superComponents.size();
    }

    /// Returns whether a subselector relationship implies a superselector
    /// relationship between the same adjacent components.
    ///
    /// @param subRelation   the relationship required by the narrower selector
    /// @param superRelation the relationship required by the broader selector
    /// @return whether the former implies the latter
    private static boolean relationImplies(
            @Nullable Combinator subRelation,
            @Nullable Combinator superRelation
    ) {
        if (superRelation == null) {
            return subRelation == null || subRelation == Combinator.CHILD;
        }
        return switch (superRelation) {
            case CHILD -> subRelation == Combinator.CHILD;
            case NEXT_SIBLING -> subRelation == Combinator.NEXT_SIBLING;
            case FOLLOWING_SIBLING -> subRelation == Combinator.NEXT_SIBLING
                    || subRelation == Combinator.FOLLOWING_SIBLING;
        };
    }

    /// Returns the effective relationship after a non-final component.
    ///
    /// A {@code null} value represents the implicit descendant combinator.
    ///
    /// @param selector the complex selector
    /// @param index    the component index, which must not be final
    /// @return the explicit combinator, or {@code null} for descendant
    private static @Nullable Combinator relationAfter(ComplexSelector selector, int index) {
        var combinators = selector.components().get(index).combinators();
        return combinators.isEmpty() ? null : combinators.get(0);
    }

    /// Unifies two compounds in the structurally modeled selector subset.
    ///
    /// @param first  the first compound selector
    /// @param second the second compound selector
    /// @return the merged compound, or {@code null} for incompatible types or IDs
    private static @Nullable CompoundSelector unifyCompound(
            CompoundSelector first,
            CompoundSelector second
    ) {
        var result = new ArrayList<>(first.components());
        for (var simple : second.components()) {
            if (!mergeSimple(result, simple)) {
                return null;
            }
        }
        return new CompoundSelector(result, first.span());
    }

    /// Merges one simple selector into a mutable compound component list.
    ///
    /// @param result the mutable selector components
    /// @param simple the additional selector
    /// @return whether the merged compound remains satisfiable
    private static boolean mergeSimple(ArrayList<SimpleSelector> result, SimpleSelector simple) {
        if (simple instanceof TypeSelector type) {
            return mergeType(result, type);
        }
        if (simple instanceof UniversalSelector universal) {
            return mergeUniversal(result, universal);
        }
        if (simple instanceof IdSelector id) {
            return mergeId(result, id);
        }
        if (simple instanceof ClassSelector classSelector) {
            addBeforePseudoClassesWhenAbsent(result, classSelector);
            return true;
        }
        if (simple instanceof AttributeSelector attribute) {
            addBeforePseudoClassesWhenAbsent(result, attribute);
            return true;
        }
        if (simple instanceof PlaceholderSelector placeholder) {
            addBeforePseudoClassesWhenAbsent(result, placeholder);
            return true;
        }
        if (simple instanceof PseudoSelector pseudo) {
            return mergePseudo(result, pseudo);
        }
        throw new AssertionError("unsupported selector reached algebra merge");
    }

    /// Merges a type selector into a compound.
    ///
    /// @param result the mutable selector components
    /// @param type   the type selector to merge
    /// @return whether the type constraints are compatible
    private static boolean mergeType(ArrayList<SimpleSelector> result, TypeSelector type) {
        return mergeElementSelector(result, type);
    }

    /// Merges a universal selector into a compound.
    ///
    /// @param result    the mutable selector components
    /// @param universal the universal selector to merge
    /// @return whether the namespace constraints are compatible
    private static boolean mergeUniversal(
            ArrayList<SimpleSelector> result,
            UniversalSelector universal
    ) {
        return mergeElementSelector(result, universal);
    }

    /// Merges one type or universal selector into a compound.
    ///
    /// The incoming selector is unified with the compound's existing element
    /// selector when present. A default or wildcard universal is omitted when
    /// another simple selector already constrains the compound.
    ///
    /// @param result  the mutable selector components
    /// @param element the type or universal selector to merge
    /// @return whether the element constraints are compatible
    private static boolean mergeElementSelector(
            ArrayList<SimpleSelector> result,
            SimpleSelector element
    ) {
        for (var index = 0; index < result.size(); index++) {
            var existing = result.get(index);
            if (existing instanceof TypeSelector || existing instanceof UniversalSelector) {
                @Nullable SimpleSelector unified = unifyElementSelectors(element, existing);
                if (unified == null) {
                    return false;
                }
                result.set(index, unified);
                return true;
            }
        }

        if (element instanceof UniversalSelector universal
                && !result.isEmpty()
                && isNonrestrictiveUniversal(universal)) {
            return true;
        }
        result.add(0, element);
        return true;
    }

    /// Returns the intersection of two type or universal selectors.
    ///
    /// @param first  the incoming element selector
    /// @param second the existing element selector
    /// @return the narrower element selector, or {@code null} when namespaces
    ///         or element names are incompatible
    private static @Nullable SimpleSelector unifyElementSelectors(
            SimpleSelector first,
            SimpleSelector second
    ) {
        var firstNamespace = elementNamespace(first);
        var secondNamespace = elementNamespace(second);
        @Nullable SelectorNamespace namespace = unifyNamespaces(firstNamespace, secondNamespace);
        if (namespace == null) {
            return null;
        }

        @Nullable CssIdentifier firstName = elementName(first);
        @Nullable CssIdentifier secondName = elementName(second);
        @Nullable CssIdentifier name;
        if (identifiersHaveSameValue(firstName, secondName) || secondName == null) {
            name = firstName;
        } else if (firstName == null) {
            name = secondName;
        } else {
            return null;
        }

        if (name == null) {
            return new UniversalSelector(namespace, first.span());
        }
        return new TypeSelector(new QualifiedName(name, namespace), first.span());
    }

    /// Returns the namespace constraint imposed by one element selector.
    ///
    /// @param selector the type or universal selector
    /// @return the selector namespace
    private static SelectorNamespace elementNamespace(SimpleSelector selector) {
        if (selector instanceof TypeSelector type) {
            return type.name().namespace();
        }
        if (selector instanceof UniversalSelector universal) {
            return universal.namespace();
        }
        throw new AssertionError("non-element selector reached namespace algebra");
    }

    /// Returns the local element name imposed by one element selector.
    ///
    /// @param selector the type or universal selector
    /// @return the local name, or {@code null} for a universal selector
    private static @Nullable CssIdentifier elementName(SimpleSelector selector) {
        if (selector instanceof TypeSelector type) {
            return type.name().name();
        }
        if (selector instanceof UniversalSelector) {
            return null;
        }
        throw new AssertionError("non-element selector reached namespace algebra");
    }

    /// Returns the intersection of two namespace constraints.
    ///
    /// @param first  the incoming namespace constraint
    /// @param second the existing namespace constraint
    /// @return the narrower namespace, or {@code null} when incompatible
    private static @Nullable SelectorNamespace unifyNamespaces(
            SelectorNamespace first,
            SelectorNamespace second
    ) {
        if (namespacesHaveSameValue(first, second)
                || second.kind() == SelectorNamespaceKind.ANY) {
            return first;
        }
        if (first.kind() == SelectorNamespaceKind.ANY) {
            return second;
        }
        return null;
    }

    /// Returns whether two namespaces impose the same modeled constraint.
    ///
    /// @param first  the first namespace
    /// @param second the second namespace
    /// @return whether both namespace forms have the same decoded value
    private static boolean namespacesHaveSameValue(
            SelectorNamespace first,
            SelectorNamespace second
    ) {
        if (first.kind() != second.kind()) {
            return false;
        }
        if (first.kind() != SelectorNamespaceKind.NAMED) {
            return true;
        }

        @Nullable CssIdentifier firstName = first.name();
        @Nullable CssIdentifier secondName = second.name();
        return firstName != null
                && secondName != null
                && firstName.hasSameValue(secondName);
    }

    /// Returns whether two nullable CSS identifiers have the same decoded value.
    ///
    /// @param first  the first identifier, or {@code null}
    /// @param second the second identifier, or {@code null}
    /// @return whether both identifiers are absent or semantically equal
    private static boolean identifiersHaveSameValue(
            @Nullable CssIdentifier first,
            @Nullable CssIdentifier second
    ) {
        return first == null ? second == null : second != null && first.hasSameValue(second);
    }

    /// Returns whether a universal selector adds no namespace restriction to a
    /// compound that already has another simple selector.
    ///
    /// @param universal the universal selector to inspect
    /// @return whether the universal may be omitted in that compound
    private static boolean isNonrestrictiveUniversal(UniversalSelector universal) {
        return universal.namespace().kind() == SelectorNamespaceKind.DEFAULT
                || universal.namespace().kind() == SelectorNamespaceKind.ANY;
    }

    /// Removes a lone nonrestrictive universal before adding another simple selector.
    ///
    /// @param result the mutable compound components
    private static void removeLoneNonrestrictiveUniversal(ArrayList<SimpleSelector> result) {
        if (result.size() == 1
                && result.get(0) instanceof UniversalSelector universal
                && isNonrestrictiveUniversal(universal)) {
            result.remove(0);
        }
    }

    /// Merges an ID selector into a compound.
    ///
    /// @param result the mutable selector components
    /// @param id     the ID selector to merge
    /// @return whether the ID constraints are compatible
    private static boolean mergeId(ArrayList<SimpleSelector> result, IdSelector id) {
        for (var existing : result) {
            if (existing instanceof IdSelector existingId) {
                return existingId.name().hasSameValue(id.name());
            }
        }
        addBeforePseudoClassesWhenAbsent(result, id);
        return true;
    }

    /// Merges a pseudo class or pseudo element into a compound selector.
    ///
    /// Pseudo elements are exclusive targets: a compound cannot select two
    /// different pseudo elements. Pseudo classes introduced by a second input
    /// are inserted before the first pseudo element so their source-relative
    /// ordering remains stable.
    ///
    /// @param result the mutable selector components
    /// @param pseudo  the pseudo selector to merge
    /// @return whether the merged compound remains satisfiable
    private static boolean mergePseudo(
            ArrayList<SimpleSelector> result,
            PseudoSelector pseudo
    ) {
        if (containsSemantically(result, pseudo)) {
            return true;
        }
        if (isPseudoElement(pseudo)) {
            for (var existing : result) {
                if (existing instanceof PseudoSelector existingPseudo
                        && isPseudoElement(existingPseudo)) {
                    return false;
                }
            }
            result.add(pseudo);
            return true;
        }

        for (var index = 0; index < result.size(); index++) {
            var existing = result.get(index);
            if (existing instanceof PseudoSelector existingPseudo
                    && isPseudoElement(existingPseudo)) {
                result.add(index, pseudo);
                return true;
            }
        }
        result.add(pseudo);
        return true;
    }

    /// Adds one non-pseudo selector before the first pseudo class when absent.
    ///
    /// This preserves canonical compound ordering such as {@code .button:hover}
    /// when a class, ID, attribute, or placeholder is unified with a pseudo
    /// class.
    ///
    /// @param result the mutable compound components
    /// @param simple the non-pseudo selector to add
    private static void addBeforePseudoClassesWhenAbsent(
            ArrayList<SimpleSelector> result,
            SimpleSelector simple
    ) {
        if (containsSemantically(result, simple)) {
            return;
        }
        removeLoneNonrestrictiveUniversal(result);
        for (var index = 0; index < result.size(); index++) {
            if (result.get(index) instanceof PseudoSelector) {
                result.add(index, simple);
                return;
            }
        }
        result.add(simple);
    }

    /// Returns whether one compound matches every element matched by another.
    ///
    /// Pseudo elements select a different target rather than merely adding a
    /// constraint. When either compound contains one, both compounds must use
    /// compatible pseudo elements and their surrounding selector segments are
    /// compared independently.
    ///
    /// @param superselector the candidate broader compound
    /// @param subselector   the candidate narrower compound
    /// @return whether every required simple selector is covered
    private static boolean compoundIsSuperselector(
            CompoundSelector superselector,
            CompoundSelector subselector
    ) {
        var superPseudoElement = pseudoElementIndex(superselector);
        var subPseudoElement = pseudoElementIndex(subselector);
        if (superPseudoElement != subPseudoElement) {
            if (superPseudoElement < 0 || subPseudoElement < 0) {
                return false;
            }
        }
        if (superPseudoElement >= 0) {
            var superPseudo = (PseudoSelector) superselector.components().get(superPseudoElement);
            var subPseudo = (PseudoSelector) subselector.components().get(subPseudoElement);
            return pseudoIsSuperselector(superPseudo, subPseudo)
                    && componentsAreSuperselector(
                            superselector.components().subList(0, superPseudoElement),
                            subselector.components().subList(0, subPseudoElement)
                    )
                    && componentsAreSuperselector(
                            superselector.components().subList(
                                    superPseudoElement + 1,
                                    superselector.components().size()
                            ),
                            subselector.components().subList(
                                    subPseudoElement + 1,
                                    subselector.components().size()
                            )
                    );
        }
        return componentsAreSuperselector(
                superselector.components(),
                subselector.components()
        );
    }

    /// Returns whether one simple-selector segment covers another segment.
    ///
    /// @param superComponents the candidate broader simple selectors
    /// @param subComponents   the candidate narrower simple selectors
    /// @return whether every broader constraint is implied by the narrower segment
    private static boolean componentsAreSuperselector(
            List<SimpleSelector> superComponents,
            List<SimpleSelector> subComponents
    ) {
        if (superComponents.isEmpty()) {
            return true;
        }
        if (subComponents.isEmpty()) {
            for (var superSimple : superComponents) {
                if (!(superSimple instanceof UniversalSelector universal)
                        || !isNonrestrictiveUniversal(universal)) {
                    return false;
                }
            }
            return true;
        }

        var subCompound = new CompoundSelector(subComponents, subComponents.get(0).span());
        for (var superSimple : superComponents) {
            var covered = superSimple instanceof PseudoSelector pseudo
                    && selectorArgument(pseudo) != null
                    && selectorPseudoIsSuperselector(pseudo, subCompound);
            if (!covered) {
                for (var subSimple : subComponents) {
                    if (simpleIsSuperselector(superSimple, subSimple)) {
                        covered = true;
                        break;
                    }
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    /// Returns the index of the compound's pseudo element, or {@code -1} when
    /// no pseudo element is present.
    ///
    /// @param selector the compound selector to inspect
    /// @return the pseudo-element component index, or {@code -1}
    private static int pseudoElementIndex(CompoundSelector selector) {
        for (var index = 0; index < selector.components().size(); index++) {
            var simple = selector.components().get(index);
            if (simple instanceof PseudoSelector pseudo && isPseudoElement(pseudo)) {
                return index;
            }
        }
        return -1;
    }

    /// Returns whether one simple selector matches every element matched by another.
    ///
    /// @param superselector the candidate broader simple selector
    /// @param subselector   the candidate narrower simple selector
    /// @return whether the narrower selector implies the broader selector
    private static boolean simpleIsSuperselector(
            SimpleSelector superselector,
            SimpleSelector subselector
    ) {
        if (superselector instanceof UniversalSelector universal) {
            return universalIsSuperselector(universal, subselector);
        }
        if (superselector instanceof TypeSelector type && subselector instanceof TypeSelector subType) {
            return typeIsSuperselector(type, subType);
        }
        if (superselector instanceof PseudoSelector superPseudo
                && subselector instanceof PseudoSelector subPseudo) {
            return pseudoIsSuperselector(superPseudo, subPseudo);
        }
        if (subselector instanceof PseudoSelector pseudo && isSubselectorPseudo(pseudo)) {
            @Nullable SelectorList selectors = selectorArgument(pseudo);
            return selectors != null && argumentBranchesRequireSimple(superselector, selectors);
        }
        return semanticEquals(superselector, subselector);
    }

    /// Returns whether one pseudo selector covers another pseudo selector.
    ///
    /// @param superselector the candidate broader pseudo selector
    /// @param subselector   the candidate narrower pseudo selector
    /// @return whether the pseudo arguments impose compatible containment
    private static boolean pseudoIsSuperselector(
            PseudoSelector superselector,
            PseudoSelector subselector
    ) {
        if (semanticEquals(superselector, subselector)) {
            return true;
        }
        if (isPseudoElement(superselector) != isPseudoElement(subselector)
                || !superselector.name().hasSameValue(subselector.name())
                || !supportsPseudoArgumentContainment(superselector)) {
            return false;
        }
        return pseudoArgumentsAreSuperselector(
                superselector.argument(),
                subselector.argument()
        );
    }

    /// Returns whether a selector-taking pseudo selector covers a compound.
    ///
    /// @param pseudo       the candidate broader pseudo selector
    /// @param subselector  the compound selected by the narrower path
    /// @return whether the pseudo argument covers the compound
    private static boolean selectorPseudoIsSuperselector(
            PseudoSelector pseudo,
            CompoundSelector subselector
    ) {
        for (var simple : subselector.components()) {
            if (simple instanceof PseudoSelector subPseudo
                    && pseudoIsSuperselector(pseudo, subPseudo)) {
                return true;
            }
        }

        @Nullable SelectorList selectors = selectorArgument(pseudo);
        if (selectors == null || !isUnionSelectorPseudo(pseudo)) {
            return false;
        }
        var subComplex = singleCompoundComplex(subselector);
        for (var candidate : selectors.components()) {
            if (candidate.leadingCombinators().isEmpty()
                    && Boolean.TRUE.equals(complexIsSuperselector(candidate, subComplex))) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether every branch of a selector pseudo implies one simple selector.
    ///
    /// @param superselector the simple selector required by every branch
    /// @param selectors     the selector-list pseudo argument
    /// @return whether each argument branch satisfies the simple selector
    private static boolean argumentBranchesRequireSimple(
            SimpleSelector superselector,
            SelectorList selectors
    ) {
        for (var complex : selectors.components()) {
            if (!complex.leadingCombinators().isEmpty() || complex.components().isEmpty()) {
                return false;
            }
            var finalCompound = complex.components().get(complex.components().size() - 1).selector();
            var covered = false;
            for (var simple : finalCompound.components()) {
                if (simpleIsSuperselector(superselector, simple)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    /// Returns a single-compound complex selector for compound comparison.
    ///
    /// @param compound the compound selector to wrap
    /// @return a complex selector containing only {@code compound}
    private static ComplexSelector singleCompoundComplex(CompoundSelector compound) {
        return new ComplexSelector(
                List.of(),
                List.of(new ComplexSelectorComponent(compound, List.of(), compound.span())),
                compound.span()
        );
    }

    /// Returns the nested selector list carried by one pseudo selector.
    ///
    /// @param pseudo the pseudo selector to inspect
    /// @return the nested selector list, or {@code null} when absent
    private static @Nullable SelectorList selectorArgument(PseudoSelector pseudo) {
        @Nullable PseudoArgument argument = pseudo.argument();
        if (argument instanceof SelectorPseudoArgument selectorArgument) {
            return selectorArgument.selectors();
        }
        if (argument instanceof NthPseudoArgument nthArgument) {
            return nthArgument.selectors();
        }
        return null;
    }

    /// Returns whether a pseudo selector's selector argument has union semantics.
    ///
    /// @param pseudo the pseudo selector to inspect
    /// @return whether each matching branch contributes an alternative match
    private static boolean isUnionSelectorPseudo(PseudoSelector pseudo) {
        if (isPseudoElement(pseudo)) {
            return false;
        }
        return switch (normalizedPseudoName(pseudo.name().value())) {
            case "is", "matches", "where", "any" -> true;
            default -> false;
        };
    }

    /// Returns whether a pseudo selector's argument can be compared covariantly.
    ///
    /// @param pseudo the pseudo selector to inspect
    /// @return whether a superselector argument may cover a subselector argument
    private static boolean supportsPseudoArgumentContainment(PseudoSelector pseudo) {
        return switch (normalizedPseudoName(pseudo.name().value())) {
            case "is", "matches", "where", "any", "has", "host", "host-context",
                    "slotted", "nth-child", "nth-last-child" -> true;
            default -> false;
        };
    }

    /// Returns whether one pseudo argument covers another pseudo argument.
    ///
    /// @param superArgument the candidate broader argument, or {@code null}
    /// @param subArgument   the candidate narrower argument, or {@code null}
    /// @return whether the argument structure has the required containment
    private static boolean pseudoArgumentsAreSuperselector(
            @Nullable PseudoArgument superArgument,
            @Nullable PseudoArgument subArgument
    ) {
        if (superArgument instanceof SelectorPseudoArgument superSelectors
                && subArgument instanceof SelectorPseudoArgument subSelectors) {
            return isSuperselector(superSelectors.selectors(), subSelectors.selectors());
        }
        if (superArgument instanceof NthPseudoArgument superNth
                && subArgument instanceof NthPseudoArgument subNth) {
            if (!superNth.formula().equals(subNth.formula())) {
                return false;
            }
            @Nullable SelectorList superSelectors = superNth.selectors();
            @Nullable SelectorList subSelectors = subNth.selectors();
            if (superSelectors == null || subSelectors == null) {
                return superSelectors == subSelectors;
            }
            return isSuperselector(superSelectors, subSelectors);
        }
        return false;
    }

    /// Returns whether a pseudo selector can be treated as a subselector of its
    /// selector-list argument.
    ///
    /// @param pseudo the pseudo selector to inspect
    /// @return whether every matching branch constrains the outer target
    private static boolean isSubselectorPseudo(PseudoSelector pseudo) {
        if (selectorArgument(pseudo) == null || isPseudoElement(pseudo)) {
            return false;
        }
        var normalizedName = normalizedPseudoName(pseudo.name().value());
        return isUnionSelectorPseudo(pseudo)
                || normalizedName.equals("nth-child")
                || normalizedName.equals("nth-last-child");
    }

    /// Returns whether a universal selector matches every element selected by a
    /// simple selector.
    ///
    /// @param superselector the candidate broader universal selector
    /// @param subselector   the candidate narrower simple selector
    /// @return whether the universal namespace covers the simple selector
    private static boolean universalIsSuperselector(
            UniversalSelector superselector,
            SimpleSelector subselector
    ) {
        var namespace = superselector.namespace();
        if (namespace.kind() == SelectorNamespaceKind.ANY) {
            return true;
        }
        if (subselector instanceof TypeSelector type) {
            return namespacesHaveSameValue(namespace, type.name().namespace());
        }
        if (subselector instanceof UniversalSelector universal) {
            return namespacesHaveSameValue(namespace, universal.namespace());
        }
        return namespace.isDefault();
    }

    /// Returns whether one type selector matches every element selected by another.
    ///
    /// @param superselector the candidate broader type selector
    /// @param subselector   the candidate narrower type selector
    /// @return whether the local name and namespace constraints are covered
    private static boolean typeIsSuperselector(
            TypeSelector superselector,
            TypeSelector subselector
    ) {
        return superselector.name().name().hasSameValue(subselector.name().name())
                && (superselector.name().namespace().kind() == SelectorNamespaceKind.ANY
                || namespacesHaveSameValue(
                        superselector.name().namespace(),
                        subselector.name().namespace()
                ));
    }

    /// Applies one extension or replacement target to a selector list.
    ///
    /// Selector arguments are transformed before direct compound occurrences so
    /// a recursive pseudo result remains the basis for any outer replacement.
    /// In extend mode the basis is retained; in replace mode it is retained only
    /// when no direct occurrence matches.
    ///
    /// @param selectors   the current complex-selector alternatives
    /// @param target      the single compound target
    /// @param inserted    the selector alternatives to insert
    /// @param replacement whether matched originals must be removed
    /// @return the transformed alternatives in stable source order
    private static @Unmodifiable List<ComplexSelector> transformTarget(
            List<ComplexSelector> selectors,
            CompoundSelector target,
            SelectorList inserted,
            boolean replacement
    ) {
        var bases = new ArrayList<ComplexSelector>(selectors.size());
        for (var candidate : selectors) {
            @Nullable ComplexSelector nested = transformNestedPseudoArguments(
                    candidate,
                    target,
                    inserted,
                    replacement
            );
            bases.add(nested == null ? candidate : nested);
        }

        var result = new ArrayList<ComplexSelector>();
        if (!replacement) {
            // Preserve the established Sass source ordering: retain every basis
            // before appending alternatives introduced for the current target.
            result.addAll(bases);
            for (var basis : bases) {
                result.addAll(replacementAlternatives(basis, target, inserted));
            }
            return deduplicate(result);
        }
        for (var basis : bases) {
            var alternatives = replacementAlternatives(basis, target, inserted);
            if (alternatives.isEmpty()) {
                result.add(basis);
            } else {
                result.addAll(alternatives);
            }
        }
        return deduplicate(result);
    }

    /// Rewrites recursively modeled pseudo-selector arguments in one complex selector.
    ///
    /// @param source       the source complex selector
    /// @param target       the compound target
    /// @param inserted     the selector alternatives to insert
    /// @param replacement  whether matched nested alternatives are replaced
    /// @return the rewritten selector, or {@code null} when no nested argument changed
    private static @Nullable ComplexSelector transformNestedPseudoArguments(
            ComplexSelector source,
            CompoundSelector target,
            SelectorList inserted,
            boolean replacement
    ) {
        if (!mayTransformPseudoArgumentsForTarget(target)) {
            return null;
        }
        var components = new ArrayList<ComplexSelectorComponent>(source.components().size());
        var changed = false;
        for (var component : source.components()) {
            var simples = new ArrayList<SimpleSelector>(component.selector().components().size());
            var componentChanged = false;
            for (var simple : component.selector().components()) {
                SimpleSelector transformed = simple;
                if (simple instanceof PseudoSelector pseudo) {
                    @Nullable PseudoSelector transformedPseudo = transformPseudoArgument(
                            pseudo,
                            target,
                            inserted,
                            replacement
                    );
                    if (transformedPseudo != null) {
                        transformed = transformedPseudo;
                        componentChanged = true;
                    }
                }
                simples.add(transformed);
            }
            if (componentChanged) {
                components.add(new ComplexSelectorComponent(
                        new CompoundSelector(simples, component.selector().span()),
                        component.combinators(),
                        component.span()
                ));
                changed = true;
            } else {
                components.add(component);
            }
        }
        return changed ? new ComplexSelector(source.leadingCombinators(), components, source.span()) : null;
    }

    /// Returns whether a target may safely be applied inside pseudo selector arguments.
    ///
    /// A structured selector pseudo can be a superselector of a raw nested
    /// branch without being that branch's syntactic target. Recursive extension
    /// therefore accepts only targets made of locally matching simple selectors.
    ///
    /// @param target the compound target to inspect
    /// @return whether the target has no structured selector pseudo component
    private static boolean mayTransformPseudoArgumentsForTarget(CompoundSelector target) {
        for (var simple : target.components()) {
            if (simple instanceof PseudoSelector pseudo && selectorArgument(pseudo) != null) {
                return false;
            }
        }
        return true;
    }

    /// Rewrites one pseudo selector's nested selector-list argument when supported.
    ///
    /// @param pseudo       the pseudo selector to inspect
    /// @param target       the compound target
    /// @param inserted     the selector alternatives to insert
    /// @param replacement  whether matched nested alternatives are replaced
    /// @return a rewritten pseudo selector, or {@code null} when its argument is unchanged
    private static @Nullable PseudoSelector transformPseudoArgument(
            PseudoSelector pseudo,
            CompoundSelector target,
            SelectorList inserted,
            boolean replacement
    ) {
        if (!supportsRecursivePseudoArgumentTransformation(pseudo)) {
            return null;
        }
        @Nullable SelectorList originalSelectors = selectorArgument(pseudo);
        if (originalSelectors == null) {
            return null;
        }
        var transformedSelectors = new SelectorList(
                transformTarget(
                        originalSelectors.components(),
                        target,
                        inserted,
                        replacement
                ),
                originalSelectors.span()
        );
        if (selectorListSemanticallyEquals(originalSelectors, transformedSelectors)) {
            return null;
        }

        @Nullable PseudoArgument argument = pseudo.argument();
        PseudoArgument transformedArgument;
        if (argument instanceof SelectorPseudoArgument) {
            transformedArgument = new SelectorPseudoArgument(transformedSelectors);
        } else if (argument instanceof NthPseudoArgument nthArgument) {
            transformedArgument = new NthPseudoArgument(nthArgument.formula(), transformedSelectors);
        } else {
            throw new AssertionError("pseudo selector has an unmodeled nested argument");
        }
        return new PseudoSelector(
                pseudo.name(),
                pseudo.element(),
                transformedArgument,
                pseudo.span()
        );
    }

    /// Returns whether one pseudo selector may recursively transform its selector argument.
    ///
    /// Non-monotonic selector pseudos such as {@code :not()} and nested
    /// relationship pseudos such as {@code :has()} retain exact structural
    /// support, but are deliberately not rewritten through this local path.
    ///
    /// @param pseudo the pseudo selector to inspect
    /// @return whether its nested selector list may be transformed locally
    private static boolean supportsRecursivePseudoArgumentTransformation(PseudoSelector pseudo) {
        if (selectorArgument(pseudo) == null) {
            return false;
        }
        return switch (normalizedPseudoName(pseudo.name().value())) {
            case "is", "matches", "where", "any", "current", "slotted", "nth-child",
                    "nth-last-child" -> true;
            default -> false;
        };
    }

    /// Returns every alternative produced by replacing one matching compound.
    ///
    /// @param selector the complex selector to inspect
    /// @param target   the compound selector to match
    /// @param inserted the selector alternatives to insert
    /// @return each single-occurrence replacement alternative
    private static @Unmodifiable List<ComplexSelector> replacementAlternatives(
            ComplexSelector selector,
            CompoundSelector target,
            SelectorList inserted
    ) {
        var result = new ArrayList<ComplexSelector>();
        for (var index = 0; index < selector.components().size(); index++) {
            var component = selector.components().get(index);
            if (!compoundIsSuperselector(target, component.selector())) {
                continue;
            }
            for (var alternative : inserted.components()) {
                @Nullable ComplexSelector replacement = replaceOccurrence(
                        selector,
                        index,
                        target,
                        alternative
                );
                if (replacement != null) {
                    result.add(replacement);
                }
            }
        }
        return deduplicate(result);
    }

    /// Replaces one matched compound component with a complex selector alternative.
    ///
    /// @param source      the source complex selector
    /// @param componentAt the matched component index
    /// @param target      the matched compound selector
    /// @param inserted    the replacement complex selector
    /// @return the replacement selector, or {@code null} when residual simple
    ///         selectors conflict with the replacement's final compound
    private static @Nullable ComplexSelector replaceOccurrence(
            ComplexSelector source,
            int componentAt,
            CompoundSelector target,
            ComplexSelector inserted
    ) {
        var sourceComponent = source.components().get(componentAt);
        var residual = removeTargetSimples(sourceComponent.selector(), target);
        var finalInserted = inserted.components().get(inserted.components().size() - 1);
        @Nullable CompoundSelector mergedFinal = mergeResidual(finalInserted.selector(), residual);
        if (mergedFinal == null) {
            return null;
        }

        var components = new ArrayList<ComplexSelectorComponent>(
                source.components().size() + inserted.components().size() - 1
        );
        components.addAll(source.components().subList(0, componentAt));
        for (var index = 0; index < inserted.components().size(); index++) {
            var component = inserted.components().get(index);
            if (index == inserted.components().size() - 1) {
                components.add(new ComplexSelectorComponent(
                        mergedFinal,
                        sourceComponent.combinators(),
                        sourceComponent.span()
                ));
            } else {
                components.add(component);
            }
        }
        components.addAll(source.components().subList(componentAt + 1, source.components().size()));
        return new ComplexSelector(source.leadingCombinators(), components, source.span());
    }

    /// Removes the exact target simple selectors that contributed to a match.
    ///
    /// Universal targets remove an explicit universal selector when present but
    /// leave a source type selector in place because it remains a restriction.
    ///
    /// @param source the matching source compound
    /// @param target the matched target compound
    /// @return the remaining simple selectors in source order
    private static @Unmodifiable List<SimpleSelector> removeTargetSimples(
            CompoundSelector source,
            CompoundSelector target
    ) {
        var remaining = new ArrayList<>(source.components());
        for (var targetSimple : target.components()) {
            for (var index = 0; index < remaining.size(); index++) {
                if (semanticEquals(targetSimple, remaining.get(index))) {
                    remaining.remove(index);
                    break;
                }
            }
        }
        return List.copyOf(remaining);
    }

    /// Merges the residual source simple selectors into a replacement compound.
    ///
    /// @param replacement the final replacement compound
    /// @param residual    the source restrictions not consumed by the target
    /// @return the merged compound, or {@code null} for incompatible constraints
    private static @Nullable CompoundSelector mergeResidual(
            CompoundSelector replacement,
            List<SimpleSelector> residual
    ) {
        if (residual.isEmpty()) {
            return replacement;
        }
        return unifyCompound(new CompoundSelector(residual, replacement.span()), replacement);
    }

    /// Removes semantic duplicates while preserving the first source ordering.
    ///
    /// @param selectors the selectors to normalize
    /// @return an immutable source-order-distinct selector list
    private static @Unmodifiable List<ComplexSelector> deduplicate(
            List<ComplexSelector> selectors
    ) {
        Map<String, ComplexSelector> unique = new LinkedHashMap<>();
        for (var selector : selectors) {
            unique.putIfAbsent(complexKey(selector), selector);
        }
        return List.copyOf(unique.values());
    }

    /// Returns a source-span-independent semantic key for a complex selector.
    ///
    /// @param selector the selector to key
    /// @return the semantic key
    private static String complexKey(ComplexSelector selector) {
        var result = new StringBuilder();
        for (var index = 0; index < selector.components().size(); index++) {
            if (index > 0) {
                result.append('\u001e');
            }
            result.append(compoundKey(selector.components().get(index).selector()));
            if (index + 1 < selector.components().size()) {
                @Nullable Combinator relation = relationAfter(selector, index);
                result.append('\u001f').append(relation == null ? ' ' : relation.css());
            }
        }
        return result.toString();
    }

    /// Returns a source-span-independent semantic key for one compound selector.
    ///
    /// Pseudo-element boundaries are retained because pseudo classes appearing
    /// before and after the element target need not be interchangeable. Within
    /// either segment, ordinary conjunction constraints remain order-insensitive.
    ///
    /// @param selector the compound selector to key
    /// @return the semantic key
    private static String compoundKey(CompoundSelector selector) {
        var hasNonUniversalSelector = false;
        for (var simple : selector.components()) {
            if (!(simple instanceof UniversalSelector)) {
                hasNonUniversalSelector = true;
                break;
            }
        }
        var pseudoElement = pseudoElementIndex(selector);
        if (pseudoElement < 0) {
            return simpleComponentsKey(selector.components(), hasNonUniversalSelector);
        }
        return simpleComponentsKey(
                selector.components().subList(0, pseudoElement),
                hasNonUniversalSelector
        ) + "\u001c" + simpleKey(selector.components().get(pseudoElement)) + "\u001c"
                + simpleComponentsKey(
                        selector.components().subList(
                                pseudoElement + 1,
                                selector.components().size()
                        ),
                        hasNonUniversalSelector
                );
    }

    /// Returns an order-independent key for one segment of simple selectors.
    ///
    /// @param components              the simple selectors in the segment
    /// @param hasNonUniversalSelector whether the complete compound has a
    ///                                restrictive non-universal selector
    /// @return the segment key
    private static String simpleComponentsKey(
            List<SimpleSelector> components,
            boolean hasNonUniversalSelector
    ) {
        var keys = new ArrayList<String>();
        for (var simple : components) {
            if (simple instanceof UniversalSelector universal
                    && hasNonUniversalSelector
                    && isNonrestrictiveUniversal(universal)) {
                continue;
            }
            keys.add(simpleKey(simple));
        }
        Collections.sort(keys);
        var unique = new ArrayList<String>(keys.size());
        @Nullable String previous = null;
        for (var key : keys) {
            if (!key.equals(previous)) {
                unique.add(key);
                previous = key;
            }
        }
        return String.join("\u001d", unique);
    }

    /// Returns a source-span-independent semantic key for one simple selector.
    ///
    /// @param simple the simple selector to key
    /// @return the semantic key
    private static String simpleKey(SimpleSelector simple) {
        if (simple instanceof TypeSelector type) {
            return elementKey("type", type.name().namespace(), type.name().name());
        }
        if (simple instanceof ClassSelector classSelector) {
            return "class:" + classSelector.name().value();
        }
        if (simple instanceof IdSelector id) {
            return "id:" + id.name().value();
        }
        if (simple instanceof UniversalSelector universal) {
            return elementKey("universal", universal.namespace(), null);
        }
        if (simple instanceof AttributeSelector attribute) {
            return attributeKey(attribute);
        }
        if (simple instanceof PlaceholderSelector placeholder) {
            return "placeholder:" + placeholder.name().value();
        }
        if (simple instanceof PseudoSelector pseudo) {
            return pseudoKey(pseudo);
        }
        throw new AssertionError("unsupported selector reached algebra key generation");
    }

    /// Returns a source-span-independent key for one type or universal selector.
    ///
    /// @param kind      the element-selector kind
    /// @param namespace the namespace constraint
    /// @param name      the local name, or {@code null} for a universal selector
    /// @return the structural element-selector key
    private static String elementKey(
            String kind,
            SelectorNamespace namespace,
            @Nullable CssIdentifier name
    ) {
        var result = new StringBuilder(kind);
        appendKeyPart(result, namespace.kind().name());
        @Nullable CssIdentifier namespaceName = namespace.name();
        appendKeyPart(result, namespaceName == null ? null : namespaceName.value());
        appendKeyPart(result, name == null ? null : name.value());
        return result.toString();
    }

    /// Returns a source-span-independent key for an attribute selector.
    ///
    /// @param attribute the selector to encode
    /// @return the structural attribute key
    private static String attributeKey(AttributeSelector attribute) {
        var result = new StringBuilder("attribute");
        var name = attribute.name();
        appendKeyPart(result, name.namespace().kind().name());
        @Nullable CssIdentifier namespaceName = name.namespace().name();
        appendKeyPart(result, namespaceName == null ? null : namespaceName.value());
        appendKeyPart(result, name.name().value());
        @Nullable AttributeMatcher matcher = attribute.matcher();
        appendKeyPart(result, matcher == null ? null : matcher.name());
        appendKeyPart(result, attribute.value());
        @Nullable CssIdentifier modifier = attribute.modifier();
        appendKeyPart(result, modifier == null ? null : modifier.value());
        return result.toString();
    }

    /// Returns a source-span-independent key for one pseudo selector.
    ///
    /// @param pseudo the pseudo selector to encode
    /// @return the structural pseudo-selector key
    private static String pseudoKey(PseudoSelector pseudo) {
        var result = new StringBuilder("pseudo");
        appendKeyPart(result, pseudo.name().value());
        appendKeyPart(result, Boolean.toString(isPseudoElement(pseudo)));
        appendKeyPart(result, pseudoArgumentKey(pseudo.argument()));
        return result.toString();
    }

    /// Returns a structural key for one nullable pseudo-selector argument.
    ///
    /// @param argument the pseudo argument, or {@code null}
    /// @return the source-span-independent argument key
    private static String pseudoArgumentKey(@Nullable PseudoArgument argument) {
        var result = new StringBuilder();
        if (argument == null) {
            appendKeyPart(result, null);
        } else if (argument instanceof RawPseudoArgument raw) {
            appendKeyPart(result, "raw");
            appendKeyPart(result, raw.css());
        } else if (argument instanceof SelectorPseudoArgument selectors) {
            appendKeyPart(result, "selector");
            appendKeyPart(result, selectorListKey(selectors.selectors()));
        } else if (argument instanceof NthPseudoArgument nth) {
            appendKeyPart(result, "nth");
            appendKeyPart(result, nth.formula());
            @Nullable SelectorList selectors = nth.selectors();
            appendKeyPart(result, selectors == null ? null : selectorListKey(selectors));
        } else {
            throw new AssertionError("unmodeled pseudo argument reached key generation");
        }
        return result.toString();
    }

    /// Returns an order-independent key for a selector-list argument.
    ///
    /// @param selector the selector list to encode
    /// @return the selector-list semantic key
    private static String selectorListKey(SelectorList selector) {
        var keys = new ArrayList<String>(selector.components().size());
        for (var component : selector.components()) {
            keys.add(complexKey(component));
        }
        Collections.sort(keys);
        var unique = new ArrayList<String>(keys.size());
        @Nullable String previous = null;
        for (var key : keys) {
            if (!key.equals(previous)) {
                unique.add(key);
                previous = key;
            }
        }
        return String.join("\u001b", unique);
    }

    /// Returns whether two selector lists have the same modeled union semantics.
    ///
    /// @param first  the first selector list
    /// @param second the second selector list
    /// @return whether both lists encode the same selector alternatives
    private static boolean selectorListSemanticallyEquals(
            SelectorList first,
            SelectorList second
    ) {
        return selectorListKey(first).equals(selectorListKey(second));
    }

    /// Appends one nullable value using a length-delimited key representation.
    ///
    /// @param result the key buffer
    /// @param value  the value to append, or {@code null}
    private static void appendKeyPart(StringBuilder result, @Nullable String value) {
        result.append('|');
        if (value == null) {
            result.append('-');
            return;
        }
        result.append(value.length()).append(':').append(value);
    }

    /// Returns whether two simple selectors have the same modeled semantics.
    ///
    /// @param first  the first selector
    /// @param second the second selector
    /// @return whether the selectors are equal independent of source span
    private static boolean semanticEquals(SimpleSelector first, SimpleSelector second) {
        if (first instanceof TypeSelector firstType && second instanceof TypeSelector secondType) {
            return firstType.name().hasSameValue(secondType.name());
        }
        if (first instanceof ClassSelector firstClass && second instanceof ClassSelector secondClass) {
            return firstClass.name().hasSameValue(secondClass.name());
        }
        if (first instanceof IdSelector firstId && second instanceof IdSelector secondId) {
            return firstId.name().hasSameValue(secondId.name());
        }
        if (first instanceof AttributeSelector firstAttribute
                && second instanceof AttributeSelector secondAttribute) {
            return firstAttribute.hasSameValue(secondAttribute);
        }
        if (first instanceof PlaceholderSelector firstPlaceholder
                && second instanceof PlaceholderSelector secondPlaceholder) {
            return firstPlaceholder.name().hasSameValue(secondPlaceholder.name());
        }
        if (first instanceof PseudoSelector firstPseudo
                && second instanceof PseudoSelector secondPseudo) {
            return firstPseudo.name().hasSameValue(secondPseudo.name())
                    && isPseudoElement(firstPseudo) == isPseudoElement(secondPseudo)
                    && pseudoArgumentsHaveSameValue(
                            firstPseudo.argument(),
                            secondPseudo.argument()
                    );
        }
        if (first instanceof UniversalSelector firstUniversal
                && second instanceof UniversalSelector secondUniversal) {
            return namespacesHaveSameValue(firstUniversal.namespace(), secondUniversal.namespace());
        }
        return false;
    }

    /// Returns whether two pseudo arguments have the same modeled structural value.
    ///
    /// @param first  the first argument, or {@code null}
    /// @param second the second argument, or {@code null}
    /// @return whether both arguments impose the same represented constraint
    private static boolean pseudoArgumentsHaveSameValue(
            @Nullable PseudoArgument first,
            @Nullable PseudoArgument second
    ) {
        if (first == null || second == null) {
            return first == second;
        }
        if (first instanceof RawPseudoArgument firstRaw
                && second instanceof RawPseudoArgument secondRaw) {
            return firstRaw.css().equals(secondRaw.css());
        }
        if (first instanceof SelectorPseudoArgument firstSelectors
                && second instanceof SelectorPseudoArgument secondSelectors) {
            return selectorListSemanticallyEquals(
                    firstSelectors.selectors(),
                    secondSelectors.selectors()
            );
        }
        if (first instanceof NthPseudoArgument firstNth
                && second instanceof NthPseudoArgument secondNth) {
            if (!firstNth.formula().equals(secondNth.formula())) {
                return false;
            }
            @Nullable SelectorList firstSelectors = firstNth.selectors();
            @Nullable SelectorList secondSelectors = secondNth.selectors();
            return firstSelectors == null ? secondSelectors == null
                    : secondSelectors != null
                    && selectorListSemanticallyEquals(firstSelectors, secondSelectors);
        }
        return false;
    }

    /// Returns whether a component list already contains one semantic simple selector.
    ///
    /// @param components the selector components to inspect
    /// @param simple     the candidate selector
    /// @return whether the candidate is already present
    private static boolean containsSemantically(
            List<SimpleSelector> components,
            SimpleSelector simple
    ) {
        for (var existing : components) {
            if (semanticEquals(existing, simple)) {
                return true;
            }
        }
        return false;
    }

    /// Verifies that one compound is fully represented by the algebra engine.
    ///
    /// @param compound the compound selector to validate
    /// @param name     the Sass argument name used in diagnostics
    private static void assertSupportedCompound(CompoundSelector compound, String name) {
        var elementSelectors = 0;
        var pseudoElements = 0;
        for (var simple : compound.components()) {
            if (simple instanceof AttributeSelector || simple instanceof PlaceholderSelector) {
                continue;
            }
            if (simple instanceof PseudoSelector pseudo) {
                assertSupportedPseudo(pseudo, name);
                if (isPseudoElement(pseudo)) {
                    pseudoElements++;
                }
                continue;
            }
            if (!(simple instanceof TypeSelector)
                    && !(simple instanceof ClassSelector)
                    && !(simple instanceof IdSelector)
                    && !(simple instanceof UniversalSelector)) {
                throw unsupportedSimpleSelector(name);
            }
            if (simple instanceof TypeSelector || simple instanceof UniversalSelector) {
                elementSelectors++;
            }
        }
        if (elementSelectors > 1) {
            throw new SassValueException("$" + name
                    + ": Selector algebra requires at most one type or universal selector per compound.");
        }
        if (pseudoElements > 1) {
            throw new SassValueException("$" + name
                    + ": Selector algebra requires at most one pseudo-element selector per compound.");
        }
    }

    /// Verifies recursively modeled content within one pseudo selector.
    ///
    /// @param pseudo the pseudo selector to validate
    /// @param name   the Sass argument name used in diagnostics
    private static void assertSupportedPseudo(PseudoSelector pseudo, String name) {
        if (pseudo.containsParentSelector()) {
            throw unsupportedPseudoSelector(name);
        }
        @Nullable PseudoArgument argument = pseudo.argument();
        if (argument instanceof SelectorPseudoArgument selectors) {
            assertSupported(selectors.selectors(), name);
        } else if (argument instanceof NthPseudoArgument nth) {
            @Nullable SelectorList selectors = nth.selectors();
            if (selectors != null) {
                assertSupported(selectors, name);
            }
        }
    }

    /// Returns whether one pseudo selector targets a pseudo element.
    ///
    /// Legacy single-colon spellings retain pseudo-element semantics while
    /// preserving their original CSS spelling for output.
    ///
    /// @param pseudo the pseudo selector to inspect
    /// @return whether the selector changes the selected element target
    private static boolean isPseudoElement(PseudoSelector pseudo) {
        return pseudo.element() || isLegacyPseudoElementName(pseudo.name().value());
    }

    /// Returns a lowercase pseudo name with one recognized vendor prefix removed.
    ///
    /// @param name the decoded pseudo identifier
    /// @return the normalized pseudo name used for semantic dispatch
    private static String normalizedPseudoName(String name) {
        var normalized = name.toLowerCase(Locale.ROOT);
        for (var prefix : List.of("-webkit-", "-moz-", "-ms-", "-o-")) {
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
        }
        return normalized;
    }

    /// Returns whether a pseudo name denotes a legacy pseudo-element spelling.
    ///
    /// @param name the decoded pseudo name
    /// @return whether a single-colon spelling still denotes a pseudo-element
    private static boolean isLegacyPseudoElementName(String name) {
        return name.equalsIgnoreCase("before")
                || name.equalsIgnoreCase("after")
                || name.equalsIgnoreCase("first-line")
                || name.equalsIgnoreCase("first-letter");
    }

    /// Creates the unsupported-relative-selector diagnostic.
    ///
    /// @param name the Sass argument name
    /// @return the constructed exception
    private static SassValueException unsupportedRelativeSelector(String name) {
        return new SassValueException("$" + name
                + ": Relative selectors aren't supported by selector algebra.");
    }

    /// Creates the unsupported-combinator diagnostic.
    ///
    /// @param name the Sass argument name
    /// @return the constructed exception
    private static SassValueException unsupportedCombinators(String name) {
        return new SassValueException("$" + name
                + ": Repeated or trailing combinators aren't supported by selector algebra.");
    }

    /// Creates the unsupported-simple-selector diagnostic.
    ///
    /// @param name the Sass argument name
    /// @return the constructed exception
    private static SassValueException unsupportedSimpleSelector(String name) {
        return new SassValueException("$" + name
                + ": Selector algebra supports type and universal selectors in every namespace form, "
                + "plus class, ID, attribute, placeholder, pseudo-class, and pseudo-element selectors.");
    }

    /// Creates the unsupported-pseudo-selector diagnostic.
    ///
    /// @param name the Sass argument name
    /// @return the constructed exception
    private static SassValueException unsupportedPseudoSelector(String name) {
        return new SassValueException("$" + name
                + ": Selector algebra can't operate on pseudo selectors containing parent selectors.");
    }

    /// Creates the unsupported-comparison diagnostic.
    ///
    /// @return the constructed exception
    private static SassValueException unsupportedComparison() {
        return new SassValueException(
                "Selector algebra can't compare selectors with non-aligned explicit combinators yet."
        );
    }

    /// Creates the unsupported-unification-topology diagnostic.
    ///
    /// @param selector1 the first incompatible selector
    /// @param selector2 the second incompatible selector
    /// @return the constructed exception
    private static SassValueException unsupportedTopology(
            ComplexSelector selector1,
            ComplexSelector selector2
    ) {
        return new SassValueException("Selector algebra can't unify "
                + selector1.toCssString() + " with " + selector2.toCssString()
                + " because their explicit combinator structures differ.");
    }
}
