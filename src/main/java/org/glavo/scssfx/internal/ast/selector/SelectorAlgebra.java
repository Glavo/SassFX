// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

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
    /// Parent selectors and repeated or trailing combinators between compounds
    /// are rejected. Leading combinators (including multi-combinator relative
    /// selectors) are accepted so weave-based unification can match dart-sass.
    ///
    /// @param selector the selector list to validate
    /// @param name     the Sass argument name used in diagnostics
    /// @throws SassValueException if the selector uses unsupported syntax
    public static void assertSupported(SelectorList selector, String name) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(name, "name");

        for (var complex : selector.components()) {
            // Combinator-only, multi-combinator, and trailing-combinator forms are
            // accepted (with empty results from algebra) so selector built-ins match
            // dart-sass 1.x bogus-combinator deprecation behavior rather than hard
            // failures.
            for (var component : complex.components()) {
                assertSupportedCompound(component.selector(), name);
            }
        }
    }

    /// Verifies that every selector is a single compound target for extension.
    ///
    /// Used by {@code selector.extend} / {@code selector.replace}, which still
    /// accept multi-simple compounds but reject combinators.
    ///
    /// @param selector the extendee or original selector list
    /// @param name     the Sass argument name used in diagnostics
    /// @throws SassValueException if a target contains a combinator
    public static void assertCompoundTargets(SelectorList selector, String name) {
        assertSupported(selector, name);
        for (var complex : selector.components()) {
            if (isComplexExtendTarget(complex)) {
                throw new SassValueException("Can't extend complex selector "
                        + complex.toCssString() + ".");
            }
        }
    }

    /// Verifies that every selector is a single simple-selector target for the
    /// stylesheet {@code @extend} directive.
    ///
    /// Dart Sass rejects both complex selectors and multi-simple compounds as
    /// {@code @extend} targets, with directive-specific diagnostic wording.
    ///
    /// @param selector the extendee selector list
    /// @throws SassValueException if a target is complex or multi-simple
    public static void assertExtendDirectiveTargets(SelectorList selector) {
        assertSupported(selector, "extendee");
        for (var complex : selector.components()) {
            if (isComplexExtendTarget(complex)) {
                throw new SassValueException("complex selectors may not be extended.");
            }
            var compound = complex.components().get(0).selector();
            if (compound.components().size() > 1) {
                var suggestion = new StringBuilder();
                for (var index = 0; index < compound.components().size(); index++) {
                    if (index > 0) {
                        suggestion.append(", ");
                    }
                    suggestion.append(compound.components().get(index).toCssString());
                }
                throw new SassValueException(
                        "compound selectors may no longer be extended.\n"
                                + "Consider `@extend " + suggestion + "` instead.\n"
                                + "See https://sass-lang.com/d/extend-compound for details."
                );
            }
        }
    }

    /// Returns whether {@code complex} uses combinators or multiple compounds.
    ///
    /// @param complex the complex selector to inspect
    /// @return whether the selector is not a single compound without combinators
    private static boolean isComplexExtendTarget(ComplexSelector complex) {
        return complex.components().size() != 1
                || !complex.leadingCombinators().isEmpty()
                || !complex.components().get(0).combinators().isEmpty();
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

    /// Returns whether {@code selector} contains any compound that matches one
    /// of the single-compound targets in {@code extendee}.
    ///
    /// Used by the stylesheet {@code @extend} engine so a mandatory extend is
    /// satisfied when a target appears even if unification adds no new
    /// alternative (for example incompatible namespace/universal pairs).
    ///
    /// @param selector the selector list to search
    /// @param extendee the single-compound targets to match
    /// @return whether at least one target matches a compound in {@code selector}
    /// @throws SassValueException if an input uses unsupported selector syntax
    public static boolean containsExtendee(SelectorList selector, SelectorList extendee) {
        assertSupported(selector, "selector");
        assertCompoundTargets(extendee, "extendee");
        for (var complex : selector.components()) {
            for (var component : complex.components()) {
                for (var targetComplex : extendee.components()) {
                    var target = targetComplex.components().get(0).selector();
                    if (compoundMatchesExtendee(target, component.selector())) {
                        return true;
                    }
                }
            }
            if (containsExtendeeInPseudos(complex, extendee)) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether nested selector-pseudo arguments contain an extendee.
    private static boolean containsExtendeeInPseudos(
            ComplexSelector complex,
            SelectorList extendee
    ) {
        for (var component : complex.components()) {
            for (var simple : component.selector().components()) {
                if (!(simple instanceof PseudoSelector pseudo)) {
                    continue;
                }
                @Nullable SelectorList nested = selectorArgument(pseudo);
                if (nested != null && containsExtendee(nested, extendee)) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Extends each supported occurrence of {@code extendee} in {@code selector}
    /// with {@code extender}, retaining the original selectors.
    ///
    /// Extendee list entries are applied in source order. Later entries also
    /// transform alternatives introduced by earlier entries, matching Sass's
    /// list-target extension behavior for the represented subset.
    ///
    /// Document-original complexes (the input list) are never trimmed. Callers
    /// that apply successive extensions across stylesheet evaluation should use
    /// [#extend(SelectorList, SelectorList, SelectorList, Set)] with a stable
    /// original-key set so intermediate extension products remain trimmable.
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
        return extend(selector, extendee, extender, originalKeysOf(selector));
    }

    /// Extends {@code selector} while protecting the given original complex keys
    /// from subselector trimming (dart-sass first/second laws of extend).
    ///
    /// @param selector     the selector list to extend
    /// @param extendee     the single-compound targets to match
    /// @param extender     the selector alternatives to insert
    /// @param originalKeys semantic keys of document-original complexes; mutated
    ///                     when an original is rewritten in place via nested
    ///                     pseudo-argument transforms
    /// @return the original and extended selector alternatives
    /// @throws SassValueException if an input uses unsupported selector syntax
    public static SelectorList extend(
            SelectorList selector,
            SelectorList extendee,
            SelectorList extender,
            Set<String> originalKeys
    ) {
        assertSupported(selector, "selector");
        assertCompoundTargets(extendee, "extendee");
        assertSupported(extender, "extender");
        Objects.requireNonNull(originalKeys, "originalKeys");

        var current = new ArrayList<>(selector.components());
        for (var target : extendee.components()) {
            var targetCompound = target.components().get(0).selector();
            current = new ArrayList<>(transformTarget(
                    current,
                    targetCompound,
                    extender,
                    false,
                    originalKeys
            ));
        }
        return new SelectorList(current, selector.span());
    }

    /// Returns semantic keys for every complex in {@code selector}.
    ///
    /// @param selector the selector list
    /// @return a mutable set of complex keys suitable for [#extend]
    public static Set<String> originalKeysOf(SelectorList selector) {
        var keys = new HashSet<String>();
        for (var complex : selector.components()) {
            keys.add(complexKey(complex));
        }
        return keys;
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
        // Replacement drops matched originals, so no original-key protection.
        var originalKeys = Set.<String>of();
        for (var target : original.components()) {
            var targetCompound = target.components().get(0).selector();
            current = new ArrayList<>(transformTarget(
                    current,
                    targetCompound,
                    replacement,
                    true,
                    originalKeys
            ));
        }
        return new SelectorList(current, selector.span());
    }

    /// Unifies two supported complex selectors.
    ///
    /// Follows dart-sass {@code unifyComplex}: final compounds are unified first,
    /// then parent prefixes are woven so incompatible combinator topologies can
    /// still produce intersections when Sass allows them.
    ///
    /// @param selector1 the first complex selector
    /// @param selector2 the second complex selector
    /// @return every supported intersection in stable source order
    private static @Unmodifiable List<ComplexSelector> unifyComplex(
            ComplexSelector selector1,
            ComplexSelector selector2
    ) {
        // Bogus multi/trailing combinators never unify to a real intersection.
        if (hasBogusCombinators(selector1) || hasBogusCombinators(selector2)) {
            return List.of();
        }
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

        var complexes = List.of(selector1, selector2);
        @Nullable CompoundSelector unifiedBase = null;
        @Nullable List<Combinator> leadingCombinator = null;
        for (var complex : complexes) {
            // Single-compound selectors may contribute one shared leading combinator.
            if (complex.components().size() == 1 && !complex.leadingCombinators().isEmpty()) {
                if (leadingCombinator == null) {
                    leadingCombinator = complex.leadingCombinators();
                } else if (!leadingCombinator.equals(complex.leadingCombinators())) {
                    return List.of();
                }
            }
            var base = complex.components().get(complex.components().size() - 1);
            if (unifiedBase == null) {
                unifiedBase = base.selector();
            } else {
                unifiedBase = unifyCompound(unifiedBase, base.selector());
                if (unifiedBase == null) {
                    return List.of();
                }
            }
        }

        var withoutBases = new ArrayList<ComplexSelector>();
        for (var complex : complexes) {
            if (complex.components().size() > 1) {
                withoutBases.add(new ComplexSelector(
                        complex.leadingCombinators(),
                        complex.components().subList(0, complex.components().size() - 1),
                        complex.span()
                ));
            }
        }

        var base = new ComplexSelector(
                leadingCombinator == null ? List.of() : leadingCombinator,
                List.of(new ComplexSelectorComponent(
                        Objects.requireNonNull(unifiedBase),
                        List.of(),
                        selector1.span()
                )),
                selector1.span()
        );

        if (withoutBases.isEmpty()) {
            return List.of(base);
        }

        var toWeave = new ArrayList<ComplexSelector>(withoutBases.size());
        for (var index = 0; index < withoutBases.size() - 1; index++) {
            toWeave.add(withoutBases.get(index));
        }
        toWeave.add(withoutBases.get(withoutBases.size() - 1).concatenate(base));
        return weave(toWeave, selector1.span());
    }

    /// Returns whether one selector contains a pseudo selector whose result
    /// cannot be reduced by the ordinary compound shortcut.
    ///
    /// @param selector the complex selector to inspect
    /// @return whether pseudo structure must be retained through unification
    private static boolean hasComplicatedPseudoSemantics(ComplexSelector selector) {
        for (var component : selector.components()) {
            if (hasComplicatedSuperselectorSemantics(component.selector())) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether a compound needs parent context for superselector checks.
    ///
    /// @param compound the compound selector to inspect
    /// @return whether pseudo elements, hostish, or selector-taking pseudos are present
    private static boolean hasComplicatedSuperselectorSemantics(CompoundSelector compound) {
        for (var simple : compound.components()) {
            // :host/:host-context skip the early superselector short-circuit so
            // unify goes through host-compatibility checks (dart-sass).
            if (simple instanceof PseudoSelector pseudo
                    && (isPseudoElement(pseudo)
                    || isHostishPseudo(pseudo)
                    || selectorArgument(pseudo) != null)) {
                return true;
            }
        }
        return false;
    }

    /// Expands parenthesized selector products produced while unifying complex
    /// selectors, matching dart-sass {@code weave}.
    ///
    /// @param complexes the complex selectors to weave left-to-right
    /// @param span      the span for newly constructed selectors
    /// @return every woven complex selector, or an empty list when impossible
    private static @Unmodifiable List<ComplexSelector> weave(
            List<ComplexSelector> complexes,
            SourceSpan span
    ) {
        if (complexes.isEmpty()) {
            return List.of();
        }
        if (complexes.size() == 1) {
            return List.of(complexes.get(0));
        }

        var prefixes = new ArrayList<ComplexSelector>();
        prefixes.add(complexes.get(0));
        for (var index = 1; index < complexes.size(); index++) {
            var complex = complexes.get(index);
            if (complex.components().size() == 1) {
                for (var prefixIndex = 0; prefixIndex < prefixes.size(); prefixIndex++) {
                    prefixes.set(prefixIndex, prefixes.get(prefixIndex).concatenate(complex));
                }
                continue;
            }

            var nextPrefixes = new ArrayList<ComplexSelector>();
            for (var prefix : prefixes) {
                @Nullable List<ComplexSelector> parents = weaveParents(prefix, complex, span);
                if (parents == null) {
                    continue;
                }
                var last = complex.components().get(complex.components().size() - 1);
                for (var parentPrefix : parents) {
                    if (nextPrefixes.size() >= MAX_WEAVE_RESULTS) {
                        throw new SassValueException(
                                "Selector algebra produced too many descendant interleavings."
                        );
                    }
                    var components = new ArrayList<>(parentPrefix.components());
                    components.add(last);
                    nextPrefixes.add(new ComplexSelector(
                            parentPrefix.leadingCombinators(),
                            components,
                            span
                    ));
                }
            }
            prefixes = nextPrefixes;
            if (prefixes.isEmpty()) {
                return List.of();
            }
        }
        return deduplicate(prefixes);
    }

    /// Interweaves {@code prefix}'s components with every component of {@code base}
    /// except the last, matching dart-sass {@code _weaveParents}.
    ///
    /// @param prefix the left-hand parent path
    /// @param base   the right-hand complex selector including its final target
    /// @param span   the span for newly constructed selectors
    /// @return parent prefixes ready for the final target, or {@code null}
    private static @Nullable List<ComplexSelector> weaveParents(
            ComplexSelector prefix,
            ComplexSelector base,
            SourceSpan span
    ) {
        @Nullable List<Combinator> leading = mergeLeadingCombinators(
                prefix.leadingCombinators(),
                base.leadingCombinators()
        );
        if (leading == null) {
            return null;
        }

        var queue1 = new ArrayDeque<>(prefix.components());
        var queue2 = new ArrayDeque<>(base.components().subList(0, base.components().size() - 1));

        @Nullable List<List<List<ComplexSelectorComponent>>> trailing =
                mergeTrailingCombinators(queue1, queue2, span);
        if (trailing == null) {
            return null;
        }

        @Nullable ComplexSelectorComponent rootish1 = firstIfRootish(queue1);
        @Nullable ComplexSelectorComponent rootish2 = firstIfRootish(queue2);
        if (rootish1 != null && rootish2 != null) {
            @Nullable CompoundSelector rootish = unifyCompound(rootish1.selector(), rootish2.selector());
            if (rootish == null) {
                return null;
            }
            queue1.addFirst(new ComplexSelectorComponent(rootish, rootish1.combinators(), rootish1.span()));
            queue2.addFirst(new ComplexSelectorComponent(rootish, rootish2.combinators(), rootish1.span()));
        } else if (rootish1 != null || rootish2 != null) {
            var rootish = rootish1 != null ? rootish1 : Objects.requireNonNull(rootish2);
            queue1.addFirst(rootish);
            queue2.addFirst(rootish);
        }

        var groups1 = groupSelectors(queue1);
        var groups2 = groupSelectors(queue2);
        var lcs = longestCommonSubsequence(
                List.copyOf(groups2),
                List.copyOf(groups1),
                SelectorAlgebra::selectCommonGroup
        );

        var choices = new ArrayList<List<List<ComplexSelectorComponent>>>();
        for (var group : lcs) {
            choices.add(flattenGroupChunks(chunks(
                    groups1,
                    groups2,
                    sequence -> !sequence.isEmpty()
                            && complexIsParentSuperselector(
                                    Objects.requireNonNull(sequence.peekFirst()),
                                    group
                            )
            )));
            choices.add(List.of(group));
            if (!groups1.isEmpty()) {
                groups1.removeFirst();
            }
            if (!groups2.isEmpty()) {
                groups2.removeFirst();
            }
        }
        choices.add(flattenGroupChunks(chunks(groups1, groups2, ArrayDeque::isEmpty)));
        choices.addAll(trailing);

        var result = new ArrayList<ComplexSelector>();
        for (var path : paths(choices.stream().filter(choice -> !choice.isEmpty()).toList())) {
            if (result.size() >= MAX_WEAVE_RESULTS) {
                throw new SassValueException(
                        "Selector algebra produced too many descendant interleavings."
                );
            }
            var components = new ArrayList<ComplexSelectorComponent>();
            for (var segment : path) {
                components.addAll(segment);
            }
            result.add(new ComplexSelector(leading, components, span));
        }
        return result;
    }

    /// Flattens each chunk of component groups into one contiguous component list.
    private static List<List<ComplexSelectorComponent>> flattenGroupChunks(
            List<List<List<ComplexSelectorComponent>>> groupChunks
    ) {
        var flattened = new ArrayList<List<ComplexSelectorComponent>>(groupChunks.size());
        for (var chunk : groupChunks) {
            var components = new ArrayList<ComplexSelectorComponent>();
            for (var group : chunk) {
                components.addAll(group);
            }
            flattened.add(components);
        }
        return flattened;
    }

    /// Selects a common LCS group when two groups are equal, related by parent
    /// superselector, or must unify because they share a unique simple selector.
    private static @Nullable List<ComplexSelectorComponent> selectCommonGroup(
            List<ComplexSelectorComponent> group1,
            List<ComplexSelectorComponent> group2
    ) {
        if (groupsEqual(group1, group2)) {
            return group1;
        }
        if (complexIsParentSuperselector(group1, group2)) {
            return group2;
        }
        if (complexIsParentSuperselector(group2, group1)) {
            return group1;
        }
        if (!mustUnify(group1, group2)) {
            return null;
        }
        var unified = unifyComplex(
                new ComplexSelector(List.of(), group1, group1.get(0).span()),
                new ComplexSelector(List.of(), group2, group2.get(0).span())
        );
        return unified.size() == 1 ? unified.get(0).components() : null;
    }

    /// Returns whether two component groups are structurally identical.
    private static boolean groupsEqual(
            List<ComplexSelectorComponent> first,
            List<ComplexSelectorComponent> second
    ) {
        if (first.size() != second.size()) {
            return false;
        }
        for (var index = 0; index < first.size(); index++) {
            var a = first.get(index);
            var b = second.get(index);
            if (!compoundKey(a.selector()).equals(compoundKey(b.selector()))
                    || !a.combinators().equals(b.combinators())) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether {@code complex1} and {@code complex2} share a unique simple
    /// selector that forces unification during weaving.
    private static boolean mustUnify(
            List<ComplexSelectorComponent> complex1,
            List<ComplexSelectorComponent> complex2
    ) {
        var unique = new HashSet<String>();
        for (var component : complex1) {
            for (var simple : component.selector().components()) {
                if (isUniqueSimple(simple)) {
                    unique.add(simpleKey(simple));
                }
            }
        }
        if (unique.isEmpty()) {
            return false;
        }
        for (var component : complex2) {
            for (var simple : component.selector().components()) {
                if (isUniqueSimple(simple) && unique.contains(simpleKey(simple))) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Returns whether a simple selector may appear only once in a compound.
    private static boolean isUniqueSimple(SimpleSelector simple) {
        return simple instanceof IdSelector
                || simple instanceof PseudoSelector pseudo && isPseudoElement(pseudo);
    }

    /// Returns a leading combinator list compatible with both inputs.
    private static @Nullable List<Combinator> mergeLeadingCombinators(
            List<Combinator> first,
            List<Combinator> second
    ) {
        if (first.size() > 1 || second.size() > 1) {
            return null;
        }
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first.equals(second) ? first : null;
    }

    /// Extracts and merges trailing combinator components from both queues.
    ///
    /// @return choice lists prepended for the final weave, or {@code null}
    private static @Nullable List<List<List<ComplexSelectorComponent>>> mergeTrailingCombinators(
            ArrayDeque<ComplexSelectorComponent> components1,
            ArrayDeque<ComplexSelectorComponent> components2,
            SourceSpan span
    ) {
        var result = new ArrayList<List<List<ComplexSelectorComponent>>>();
        while (true) {
            var combinators1 = components1.isEmpty()
                    ? List.<Combinator>of()
                    : components1.peekLast().combinators();
            var combinators2 = components2.isEmpty()
                    ? List.<Combinator>of()
                    : components2.peekLast().combinators();
            if (combinators1.isEmpty() && combinators2.isEmpty()) {
                return result;
            }
            if (combinators1.size() > 1 || combinators2.size() > 1) {
                return null;
            }

            @Nullable Combinator combinator1 = combinators1.isEmpty() ? null : combinators1.get(0);
            @Nullable Combinator combinator2 = combinators2.isEmpty() ? null : combinators2.get(0);

            if (combinator1 == Combinator.FOLLOWING_SIBLING
                    && combinator2 == Combinator.FOLLOWING_SIBLING) {
                var component1 = components1.removeLast();
                var component2 = components2.removeLast();
                if (compoundIsSuperselector(component1.selector(), component2.selector(), null)) {
                    result.add(0, List.of(List.of(component2)));
                } else if (compoundIsSuperselector(component2.selector(), component1.selector(), null)) {
                    result.add(0, List.of(List.of(component1)));
                } else {
                    var choices = new ArrayList<List<ComplexSelectorComponent>>();
                    choices.add(List.of(component1, component2));
                    choices.add(List.of(component2, component1));
                    @Nullable CompoundSelector unified =
                            unifyCompound(component1.selector(), component2.selector());
                    if (unified != null) {
                        choices.add(List.of(new ComplexSelectorComponent(
                                unified,
                                component1.combinators(),
                                span
                        )));
                    }
                    result.add(0, choices);
                }
                continue;
            }

            if ((combinator1 == Combinator.FOLLOWING_SIBLING && combinator2 == Combinator.NEXT_SIBLING)
                    || (combinator1 == Combinator.NEXT_SIBLING
                    && combinator2 == Combinator.FOLLOWING_SIBLING)) {
                var followingComponents = combinator1 == Combinator.FOLLOWING_SIBLING
                        ? components1
                        : components2;
                var nextComponents = combinator1 == Combinator.NEXT_SIBLING
                        ? components1
                        : components2;
                var next = nextComponents.removeLast();
                var following = followingComponents.removeLast();
                if (compoundIsSuperselector(following.selector(), next.selector(), null)) {
                    result.add(0, List.of(List.of(next)));
                } else {
                    var choices = new ArrayList<List<ComplexSelectorComponent>>();
                    choices.add(List.of(following, next));
                    @Nullable CompoundSelector unified =
                            unifyCompound(following.selector(), next.selector());
                    if (unified != null) {
                        choices.add(List.of(new ComplexSelectorComponent(
                                unified,
                                next.combinators(),
                                span
                        )));
                    }
                    result.add(0, choices);
                }
                continue;
            }

            if ((combinator1 == Combinator.CHILD
                    && (combinator2 == Combinator.NEXT_SIBLING
                    || combinator2 == Combinator.FOLLOWING_SIBLING))
                    || (combinator2 == Combinator.CHILD
                    && (combinator1 == Combinator.NEXT_SIBLING
                    || combinator1 == Combinator.FOLLOWING_SIBLING))) {
                var siblingComponents = combinator1 == Combinator.CHILD ? components2 : components1;
                result.add(0, List.of(List.of(siblingComponents.removeLast())));
                continue;
            }

            if (combinator1 != null && combinator1 == combinator2) {
                @Nullable CompoundSelector unified = unifyCompound(
                        components1.removeLast().selector(),
                        components2.removeLast().selector()
                );
                if (unified == null) {
                    return null;
                }
                result.add(0, List.of(List.of(new ComplexSelectorComponent(
                        unified,
                        combinators1,
                        span
                ))));
                continue;
            }

            if (combinator1 != null && combinator2 == null) {
                if (combinator1 == Combinator.CHILD
                        && !components2.isEmpty()
                        && compoundIsSuperselector(
                                components2.peekLast().selector(),
                                components1.peekLast().selector(),
                                null
                        )) {
                    components2.removeLast();
                }
                result.add(0, List.of(List.of(components1.removeLast())));
                continue;
            }

            if (combinator2 != null && combinator1 == null) {
                if (combinator2 == Combinator.CHILD
                        && !components1.isEmpty()
                        && compoundIsSuperselector(
                                components1.peekLast().selector(),
                                components2.peekLast().selector(),
                                null
                        )) {
                    components1.removeLast();
                }
                result.add(0, List.of(List.of(components2.removeLast())));
                continue;
            }

            return null;
        }
    }

    /// Removes and returns the first component when it is a rootish pseudo.
    private static @Nullable ComplexSelectorComponent firstIfRootish(
            ArrayDeque<ComplexSelectorComponent> queue
    ) {
        if (queue.isEmpty()) {
            return null;
        }
        var first = queue.peekFirst();
        for (var simple : first.selector().components()) {
            if (simple instanceof PseudoSelector pseudo
                    && !isPseudoElement(pseudo)
                    && isRootishPseudo(normalizedPseudoName(pseudo.name().value()))) {
                queue.removeFirst();
                return first;
            }
        }
        return null;
    }

    /// Returns whether a pseudo class may appear only at the start of a complex selector.
    private static boolean isRootishPseudo(String normalizedName) {
        return switch (normalizedName) {
            case "root", "scope", "host", "host-context" -> true;
            default -> false;
        };
    }

    /// Groups components into the longest runs ending at an empty combinator list.
    private static ArrayDeque<List<ComplexSelectorComponent>> groupSelectors(
            Iterable<ComplexSelectorComponent> complex
    ) {
        var groups = new ArrayDeque<List<ComplexSelectorComponent>>();
        var group = new ArrayList<ComplexSelectorComponent>();
        for (var component : complex) {
            group.add(component);
            if (component.combinators().isEmpty()) {
                groups.add(List.copyOf(group));
                group = new ArrayList<>();
            }
        }
        if (!group.isEmpty()) {
            groups.add(List.copyOf(group));
        }
        return groups;
    }

    /// Returns all orderings of the initial subsequences of {@code queue1} and
    /// {@code queue2} until {@code done} reports completion for each queue.
    private static <T> List<List<T>> chunks(
            ArrayDeque<T> queue1,
            ArrayDeque<T> queue2,
            Predicate<ArrayDeque<T>> done
    ) {
        var chunk1 = new ArrayList<T>();
        while (!queue1.isEmpty() && !done.test(queue1)) {
            chunk1.add(queue1.removeFirst());
        }
        var chunk2 = new ArrayList<T>();
        while (!queue2.isEmpty() && !done.test(queue2)) {
            chunk2.add(queue2.removeFirst());
        }
        if (chunk1.isEmpty() && chunk2.isEmpty()) {
            return List.of();
        }
        if (chunk1.isEmpty()) {
            return List.of(chunk2);
        }
        if (chunk2.isEmpty()) {
            return List.of(chunk1);
        }
        var firstThenSecond = new ArrayList<T>(chunk1.size() + chunk2.size());
        firstThenSecond.addAll(chunk1);
        firstThenSecond.addAll(chunk2);
        var secondThenFirst = new ArrayList<T>(chunk1.size() + chunk2.size());
        secondThenFirst.addAll(chunk2);
        secondThenFirst.addAll(chunk1);
        return List.of(firstThenSecond, secondThenFirst);
    }

    /// Returns every path through the given choice lists.
    private static <T> List<List<T>> paths(List<List<T>> choices) {
        List<List<T>> paths = List.of(List.of());
        for (var choice : choices) {
            var next = new ArrayList<List<T>>();
            for (var option : choice) {
                for (var path : paths) {
                    var combined = new ArrayList<T>(path.size() + 1);
                    combined.addAll(path);
                    combined.add(option);
                    next.add(combined);
                }
            }
            paths = next;
        }
        return paths;
    }

    /// Computes a longest common subsequence with optional element unification.
    private static <T> List<T> longestCommonSubsequence(
            List<T> list1,
            List<T> list2,
            BiFunction<T, T, @Nullable T> select
    ) {
        if (list1.isEmpty() || list2.isEmpty()) {
            return List.of();
        }
        // Dynamic programming table of LCS lengths, then reconstruct selections.
        var lengths = new int[list1.size() + 1][list2.size() + 1];
        @SuppressWarnings("unchecked")
        T[][] selections = (T[][]) new Object[list1.size()][list2.size()];
        for (var i = 0; i < list1.size(); i++) {
            for (var j = 0; j < list2.size(); j++) {
                @Nullable T selected = select.apply(list1.get(i), list2.get(j));
                if (selected != null) {
                    lengths[i + 1][j + 1] = lengths[i][j] + 1;
                    selections[i][j] = selected;
                } else {
                    lengths[i + 1][j + 1] = Math.max(lengths[i + 1][j], lengths[i][j + 1]);
                }
            }
        }
        var result = new ArrayList<T>();
        var i = list1.size();
        var j = list2.size();
        while (i > 0 && j > 0) {
            @Nullable T selected = selections[i - 1][j - 1];
            if (selected != null && lengths[i][j] == lengths[i - 1][j - 1] + 1) {
                result.add(selected);
                i--;
                j--;
            } else if (lengths[i][j - 1] > lengths[i - 1][j]) {
                j--;
            } else {
                i--;
            }
        }
        Collections.reverse(result);
        return result;
    }

    /// Like {@link #complexIsSuperselector}, but compares parent paths as though
    /// they shared an implicit final compound.
    private static boolean complexIsParentSuperselector(
            List<ComplexSelectorComponent> complex1,
            List<ComplexSelectorComponent> complex2
    ) {
        if (complex1.size() > complex2.size()) {
            return false;
        }
        var span = complex1.isEmpty()
                ? (complex2.isEmpty() ? null : complex2.get(0).span())
                : complex1.get(0).span();
        if (span == null) {
            return true;
        }
        var base = new ComplexSelectorComponent(
                new CompoundSelector(
                        List.of(new PlaceholderSelector("<temp>", span)),
                        span
                ),
                List.of(),
                span
        );
        var first = new ArrayList<>(complex1);
        first.add(base);
        var second = new ArrayList<>(complex2);
        second.add(base);
        return Boolean.TRUE.equals(complexIsSuperselector(
                new ComplexSelector(List.of(), first, span),
                new ComplexSelector(List.of(), second, span)
        ));
    }

    /// Determines whether one complex selector is a superselector of another.
    ///
    /// Matches dart-sass {@code complexIsSuperselector}: the broader selector is
    /// walked as an ordered subsequence of the narrower selector with compatible
    /// combinators between matched compounds.
    ///
    /// @param superselector the candidate broader selector
    /// @param subselector   the candidate narrower selector
    /// @return {@code true} or {@code false}
    private static @Nullable Boolean complexIsSuperselector(
            ComplexSelector superselector,
            ComplexSelector subselector
    ) {
        // Relative selectors with leading combinators are not superselectors of
        // ordinary complex selectors (and vice versa) in the Sass algebra.
        if (!superselector.leadingCombinators().isEmpty()
                || !subselector.leadingCombinators().isEmpty()) {
            return false;
        }
        var complex1 = superselector.components();
        var complex2 = subselector.components();
        if (complex1.isEmpty() || complex2.isEmpty()) {
            return false;
        }
        // Trailing combinators are never comparable as super/sub selectors.
        if (!complex1.get(complex1.size() - 1).combinators().isEmpty()
                || !complex2.get(complex2.size() - 1).combinators().isEmpty()) {
            return false;
        }

        var i1 = 0;
        var i2 = 0;
        @Nullable Combinator previousCombinator = null;
        while (true) {
            var remaining1 = complex1.size() - i1;
            var remaining2 = complex2.size() - i2;
            if (remaining1 == 0 || remaining2 == 0) {
                return false;
            }
            // More complex selectors are never superselectors of less complex ones.
            if (remaining1 > remaining2) {
                return false;
            }

            var component1 = complex1.get(i1);
            if (component1.combinators().size() > 1) {
                return false;
            }
            if (remaining1 == 1) {
                for (var parent = i2; parent < complex2.size() - 1; parent++) {
                    if (complex2.get(parent).combinators().size() > 1) {
                        return false;
                    }
                }
                @Nullable List<ComplexSelectorComponent> parents =
                        hasComplicatedSuperselectorSemantics(component1.selector())
                                ? complex2.subList(i2, complex2.size() - 1)
                                : null;
                return compoundIsSuperselector(
                        component1.selector(),
                        complex2.get(complex2.size() - 1).selector(),
                        parents
                );
            }

            // Find the first endOfSubselector in complex2 such that
            // complex2[i2..endOfSubselector] matches component1.selector.
            var endOfSubselector = i2;
            while (true) {
                var component2 = complex2.get(endOfSubselector);
                if (component2.combinators().size() > 1) {
                    return false;
                }
                @Nullable List<ComplexSelectorComponent> parents =
                        hasComplicatedSuperselectorSemantics(component1.selector())
                                ? complex2.subList(i2, endOfSubselector)
                                : null;
                if (compoundIsSuperselector(component1.selector(), component2.selector(), parents)) {
                    break;
                }
                endOfSubselector++;
                if (endOfSubselector == complex2.size() - 1) {
                    return false;
                }
            }

            if (!compatibleWithPreviousCombinator(
                    previousCombinator,
                    complex2.subList(i2, endOfSubselector)
            )) {
                return false;
            }

            var component2 = complex2.get(endOfSubselector);
            @Nullable Combinator combinator1 = component1.combinators().isEmpty()
                    ? null
                    : component1.combinators().get(0);
            @Nullable Combinator combinator2 = component2.combinators().isEmpty()
                    ? null
                    : component2.combinators().get(0);
            if (!isSupercombinator(combinator1, combinator2)) {
                return false;
            }

            i1++;
            i2 = endOfSubselector + 1;
            previousCombinator = combinator1;

            if (complex1.size() - i1 == 1) {
                if (combinator1 == Combinator.FOLLOWING_SIBLING) {
                    // `.foo ~ .bar` requires remaining interstitial edges to be sibling edges.
                    for (var index = i2; index < complex2.size() - 1; index++) {
                        @Nullable Combinator edge = complex2.get(index).combinators().isEmpty()
                                ? null
                                : complex2.get(index).combinators().get(0);
                        if (!isSupercombinator(combinator1, edge)) {
                            return false;
                        }
                    }
                } else if (combinator1 != null) {
                    // `.foo > .bar` and `.foo + .bar` cannot leave leftover intermediates.
                    if (complex2.size() - i2 > 1) {
                        return false;
                    }
                }
            }
        }
    }

    /// Returns whether interstitial parents are allowed after {@code previous}.
    private static boolean compatibleWithPreviousCombinator(
            @Nullable Combinator previous,
            List<ComplexSelectorComponent> parents
    ) {
        if (parents.isEmpty() || previous == null) {
            return true;
        }
        // Child and next-sibling require an immediate match — no interstitial parents.
        if (previous != Combinator.FOLLOWING_SIBLING) {
            return false;
        }
        // Following-sibling allows intermediate siblings only.
        for (var parent : parents) {
            @Nullable Combinator edge = parent.combinators().isEmpty()
                    ? null
                    : parent.combinators().get(0);
            if (edge != Combinator.FOLLOWING_SIBLING && edge != Combinator.NEXT_SIBLING) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether {@code first} is a supercombinator of {@code second}.
    ///
    /// That is, whether {@code X first Y} is a superselector of {@code X second Y}.
    private static boolean isSupercombinator(
            @Nullable Combinator first,
            @Nullable Combinator second
    ) {
        if (Objects.equals(first, second)) {
            return true;
        }
        // Descendant (null) is a supercombinator of child.
        if (first == null && second == Combinator.CHILD) {
            return true;
        }
        // Following sibling is a supercombinator of next sibling.
        return first == Combinator.FOLLOWING_SIBLING && second == Combinator.NEXT_SIBLING;
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
                    subComponents.get(index).selector(),
                    null
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
    /// Matches dart-sass {@code unifyCompound}: once {@code second} contributes a
    /// pseudo-element, later pseudo-classes from {@code second} are collected
    /// separately and appended after that element so forms such as
    /// {@code ::scrollbar:horizontal} keep their original trailing order.
    ///
    /// @param first  the first compound selector
    /// @param second the second compound selector
    /// @return the merged compound, or {@code null} for incompatible types or IDs
    private static @Nullable CompoundSelector unifyCompound(
            CompoundSelector first,
            CompoundSelector second
    ) {
        var result = new ArrayList<>(first.components());
        var afterPseudoElement = new ArrayList<SimpleSelector>();
        var pseudoElementFound = false;
        for (var simple : second.components()) {
            // Pseudos after a pseudo-element in {@code second} stay after it.
            if (pseudoElementFound && simple instanceof PseudoSelector) {
                if (!mergeSimple(afterPseudoElement, simple)) {
                    return null;
                }
                continue;
            }
            if (simple instanceof PseudoSelector pseudo && isPseudoElement(pseudo)) {
                pseudoElementFound = true;
            }
            if (!mergeSimple(result, simple)) {
                return null;
            }
        }
        result.addAll(afterPseudoElement);
        return new CompoundSelector(result, first.span());
    }

    /// Merges one simple selector into a mutable compound component list.
    ///
    /// @param result the mutable selector components
    /// @param simple the additional selector
    /// @return whether the merged compound remains satisfiable
    private static boolean mergeSimple(ArrayList<SimpleSelector> result, SimpleSelector simple) {
        // :host/:host-context only unify with other host pseudos or selector-taking
        // pseudos (dart-sass PseudoSelector.unify).
        if (compoundContainsHostish(result) && !isHostCompatibleSimple(simple)) {
            return false;
        }
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

    /// Returns whether the compound already contains {@code :host} or
    /// {@code :host-context}.
    ///
    /// @param compound the compound components
    /// @return whether a hostish pseudo is present
    private static boolean compoundContainsHostish(ArrayList<SimpleSelector> compound) {
        for (var simple : compound) {
            if (simple instanceof PseudoSelector pseudo && isHostishPseudo(pseudo)) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether a simple may unify with {@code :host} / {@code :host-context}.
    ///
    /// @param simple the simple selector
    /// @return whether host unification permits this simple
    private static boolean isHostCompatibleSimple(SimpleSelector simple) {
        if (!(simple instanceof PseudoSelector pseudo) || isPseudoElement(pseudo)) {
            return false;
        }
        return isHostishPseudo(pseudo) || selectorArgument(pseudo) != null;
    }

    /// Returns whether every simple in {@code compound} may appear with {@code :host}.
    ///
    /// @param compound the compound components
    /// @return whether the compound is host-compatible
    private static boolean compoundIsHostCompatible(ArrayList<SimpleSelector> compound) {
        for (var existing : compound) {
            if (!isHostCompatibleSimple(existing)) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether a pseudo is {@code :host} or {@code :host-context}.
    ///
    /// @param pseudo the pseudo selector
    /// @return whether it is a shadow-DOM host pseudo
    private static boolean isHostishPseudo(PseudoSelector pseudo) {
        if (!pseudo.isClass()) {
            return false;
        }
        var name = normalizedPseudoName(pseudo.name().value());
        return "host".equals(name) || "host-context".equals(name);
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
            // A second {@code :host} still fails when the compound already
            // contains host-incompatible simples such as classes
            // (dart-sass: {@code unify(":host.c", ":host")} → null).
            if (isHostishPseudo(pseudo) && !compoundIsHostCompatible(result)) {
                return false;
            }
            return true;
        }
        // dart-sass: non-host simple.unify([host]) redirects to host.unify([simple]),
        // which places host after the incoming selector-taking pseudo.
        if (result.size() == 1
                && result.get(0) instanceof PseudoSelector only
                && isHostishPseudo(only)
                && !isHostishPseudo(pseudo)) {
            if (!isHostCompatibleSimple(pseudo)) {
                return false;
            }
            result.set(0, pseudo);
            result.add(only);
            return true;
        }
        if (isHostishPseudo(pseudo)) {
            if (!compoundIsHostCompatible(result)) {
                return false;
            }
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
    /// @param parents       parent components of {@code subselector} when known
    /// @return whether every required simple selector is covered
    private static boolean compoundIsSuperselector(
            CompoundSelector superselector,
            CompoundSelector subselector,
            @Nullable List<ComplexSelectorComponent> parents
    ) {
        if (!hasComplicatedSuperselectorSemantics(superselector)
                && !hasComplicatedSuperselectorSemantics(subselector)) {
            if (superselector.components().size() > subselector.components().size()) {
                return false;
            }
            for (var simple1 : superselector.components()) {
                var covered = false;
                for (var simple2 : subselector.components()) {
                    if (simpleIsSuperselector(simple1, simple2)) {
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

        var superPseudoElement = pseudoElementIndex(superselector);
        var subPseudoElement = pseudoElementIndex(subselector);
        if (superPseudoElement >= 0 || subPseudoElement >= 0) {
            if (superPseudoElement < 0 || subPseudoElement < 0) {
                return false;
            }
            var superPseudo = (PseudoSelector) superselector.components().get(superPseudoElement);
            var subPseudo = (PseudoSelector) subselector.components().get(subPseudoElement);
            return pseudoIsSuperselector(superPseudo, subPseudo)
                    && componentsAreSuperselector(
                            superselector.components().subList(0, superPseudoElement),
                            subselector.components().subList(0, subPseudoElement),
                            parents
                    )
                    && componentsAreSuperselector(
                            superselector.components().subList(
                                    superPseudoElement + 1,
                                    superselector.components().size()
                            ),
                            subselector.components().subList(
                                    subPseudoElement + 1,
                                    subselector.components().size()
                            ),
                            null
                    );
        }
        return componentsAreSuperselector(
                superselector.components(),
                subselector.components(),
                parents
        );
    }

    /// Returns whether an extend target compound matches a source compound.
    ///
    /// Unlike [#compoundIsSuperselector], a target without a pseudo-element may
    /// match a source that has one so {@code .foo} extends {@code .foo::bar} and
    /// {@code .baz} extends {@code ::foo.baz}. General {@code is-superselector}
    /// keeps the stricter PE pairing rules.
    private static boolean compoundMatchesExtendee(
            CompoundSelector target,
            CompoundSelector source
    ) {
        var targetPe = pseudoElementIndex(target);
        var sourcePe = pseudoElementIndex(source);
        if (targetPe < 0 && sourcePe >= 0) {
            return componentsAreSuperselector(
                    target.components(),
                    source.components(),
                    null
            );
        }
        return compoundIsSuperselector(target, source, null);
    }

    /// Returns whether one simple-selector segment covers another segment.
    ///
    /// @param superComponents the candidate broader simple selectors
    /// @param subComponents   the candidate narrower simple selectors
    /// @param parents         parent components of the narrower path when known
    /// @return whether every broader constraint is implied by the narrower segment
    private static boolean componentsAreSuperselector(
            List<SimpleSelector> superComponents,
            List<SimpleSelector> subComponents,
            @Nullable List<ComplexSelectorComponent> parents
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
                    && selectorPseudoIsSuperselector(pseudo, subCompound, parents);
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
    /// @param pseudo      the candidate broader pseudo selector
    /// @param subselector the compound selected by the narrower path
    /// @param parents     parent components of {@code subselector} when known
    /// @return whether the pseudo argument covers the compound
    private static boolean selectorPseudoIsSuperselector(
            PseudoSelector pseudo,
            CompoundSelector subselector,
            @Nullable List<ComplexSelectorComponent> parents
    ) {
        @Nullable SelectorList selectors = selectorArgument(pseudo);
        if (selectors == null) {
            return false;
        }
        var normalized = normalizedPseudoName(pseudo.name().value());
        return switch (normalized) {
            case "is", "matches", "any", "where" -> {
                for (var simple : subselector.components()) {
                    if (simple instanceof PseudoSelector subPseudo
                            && pseudoIsSuperselector(pseudo, subPseudo)) {
                        yield true;
                    }
                }
                // :is(A) is a superselector of a path covered by any argument branch.
                var path = new ArrayList<ComplexSelectorComponent>();
                if (parents != null) {
                    path.addAll(parents);
                }
                path.add(new ComplexSelectorComponent(subselector, List.of(), subselector.span()));
                for (var candidate : selectors.components()) {
                    if (candidate.leadingCombinators().isEmpty()
                            && Boolean.TRUE.equals(complexIsSuperselector(
                                    candidate,
                                    new ComplexSelector(List.of(), path, subselector.span())
                            ))) {
                        yield true;
                    }
                }
                yield false;
            }
            case "has", "host", "host-context" -> {
                for (var simple : subselector.components()) {
                    if (simple instanceof PseudoSelector subPseudo
                            && !isPseudoElement(subPseudo)
                            && subPseudo.name().hasSameValue(pseudo.name())
                            && selectorArgument(subPseudo) != null
                            && isSuperselector(
                                    selectors,
                                    Objects.requireNonNull(selectorArgument(subPseudo))
                            )) {
                        yield true;
                    }
                }
                yield false;
            }
            case "slotted" -> {
                for (var simple : subselector.components()) {
                    if (simple instanceof PseudoSelector subPseudo
                            && isPseudoElement(subPseudo)
                            && subPseudo.name().hasSameValue(pseudo.name())
                            && selectorArgument(subPseudo) != null
                            && isSuperselector(
                                    selectors,
                                    Objects.requireNonNull(selectorArgument(subPseudo))
                            )) {
                        yield true;
                    }
                }
                yield false;
            }
            case "not" -> selectorNotIsSuperselector(selectors, subselector, pseudo);
            case "current" -> {
                for (var simple : subselector.components()) {
                    if (simple instanceof PseudoSelector subPseudo
                            && pseudoIsSuperselector(pseudo, subPseudo)) {
                        yield true;
                    }
                }
                yield false;
            }
            case "nth-child", "nth-last-child" -> {
                for (var simple : subselector.components()) {
                    if (simple instanceof PseudoSelector subPseudo
                            && pseudoIsSuperselector(pseudo, subPseudo)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> {
                for (var simple : subselector.components()) {
                    if (simple instanceof PseudoSelector subPseudo
                            && pseudoIsSuperselector(pseudo, subPseudo)) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    /// Returns whether {@code :not(...)} covers a compound under Sass rules.
    private static boolean selectorNotIsSuperselector(
            SelectorList selectors,
            CompoundSelector subselector,
            PseudoSelector notPseudo
    ) {
        for (var complex : selectors.components()) {
            if (complex.components().isEmpty()) {
                return false;
            }
            var last = complex.components().get(complex.components().size() - 1).selector();
            var covered = false;
            for (var simple2 : subselector.components()) {
                if (simple2 instanceof TypeSelector type2) {
                    for (var simple1 : last.components()) {
                        if (simple1 instanceof TypeSelector type1 && !semanticEquals(type1, type2)) {
                            covered = true;
                            break;
                        }
                    }
                } else if (simple2 instanceof IdSelector id2) {
                    for (var simple1 : last.components()) {
                        if (simple1 instanceof IdSelector id1 && !semanticEquals(id1, id2)) {
                            covered = true;
                            break;
                        }
                    }
                } else if (simple2 instanceof PseudoSelector pseudo2
                        && !isPseudoElement(pseudo2)
                        && pseudo2.name().hasSameValue(notPseudo.name())
                        && selectorArgument(pseudo2) != null) {
                    if (isSuperselector(
                            Objects.requireNonNull(selectorArgument(pseudo2)),
                            new SelectorList(List.of(complex), complex.span())
                    )) {
                        covered = true;
                    }
                }
                if (covered) {
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
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
        // {@code :host}/{@code :host-context} select the shadow host rather than
        // ordinary elements, so a light-DOM universal is never a superselector
        // of them (dart-sass).
        if (subselector instanceof PseudoSelector pseudo && isHostishPseudo(pseudo)) {
            return false;
        }
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
            boolean replacement,
            Set<String> originalKeys
    ) {
        var bases = new ArrayList<ComplexSelector>(selectors.size());
        for (var candidate : selectors) {
            @Nullable ComplexSelector nested = transformNestedPseudoArguments(
                    candidate,
                    target,
                    inserted,
                    replacement
            );
            if (nested == null) {
                bases.add(candidate);
            } else {
                // Nested rewrites of a document original remain originals so they
                // are not trimmed (dart-sass updates {@code _originals} similarly).
                String beforeKey = complexKey(candidate);
                String afterKey = complexKey(nested);
                if (originalKeys.contains(beforeKey) && !beforeKey.equals(afterKey)) {
                    originalKeys.add(afterKey);
                }
                bases.add(nested);
            }
        }

        var result = new ArrayList<ComplexSelector>();
        if (!replacement) {
            // Match dart-sass: for each basis, keep the original then its
            // extensions immediately after it. Appending all bases first then
            // all alternatives yields the wrong interleaving for multi-extend.
            for (var basis : bases) {
                result.add(basis);
                result.addAll(replacementAlternatives(basis, target, inserted));
            }
            return trimSubselectors(deduplicate(result), originalKeys);
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

    /// Removes non-original complexes that are subselectors of another complex
    /// already present, matching dart-sass extend trimming for matching sets.
    ///
    /// @param selectors    the candidates after extension
    /// @param originalKeys semantic keys of document-original complexes
    /// @return selectors with redundant extension-produced subselectors removed
    private static @Unmodifiable List<ComplexSelector> trimSubselectors(
            List<ComplexSelector> selectors,
            Set<String> originalKeys
    ) {
        if (selectors.size() <= 1) {
            return selectors;
        }
        var result = new ArrayList<ComplexSelector>();
        for (var index = 0; index < selectors.size(); index++) {
            var candidate = selectors.get(index);
            // Document originals always stay (dart-sass first/second laws of extend).
            if (originalKeys.contains(complexKey(candidate))) {
                result.add(candidate);
                continue;
            }
            var redundant = false;
            for (var otherIndex = 0; otherIndex < selectors.size(); otherIndex++) {
                if (otherIndex == index) {
                    continue;
                }
                var other = selectors.get(otherIndex);
                if (Boolean.TRUE.equals(complexIsSuperselector(other, candidate))
                        && !Boolean.TRUE.equals(complexIsSuperselector(candidate, other))) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
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
                if (simple instanceof PseudoSelector pseudo
                        && !isPseudoElement(pseudo)
                        && "not".equals(normalizedPseudoName(pseudo.name().value()))) {
                    @Nullable List<SimpleSelector> expanded = transformNotPseudo(
                            pseudo,
                            target,
                            inserted,
                            replacement
                    );
                    if (expanded != null) {
                        simples.addAll(expanded);
                        componentChanged = true;
                        continue;
                    }
                }
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

    /// Extends the argument of a {@code :not()} pseudo selector.
    ///
    /// When the original argument is a single complex selector, the result is
    /// split into multiple adjacent {@code :not()} simples so older browsers
    /// that only accept compound {@code :not()} arguments keep working.
    ///
    /// @return the replacement simple selectors, or {@code null} when unchanged
    private static @Nullable List<SimpleSelector> transformNotPseudo(
            PseudoSelector notPseudo,
            CompoundSelector target,
            SelectorList inserted,
            boolean replacement
    ) {
        @Nullable SelectorList originalSelectors = selectorArgument(notPseudo);
        if (originalSelectors == null) {
            return null;
        }
        var nestedOriginals = replacement
                ? Set.<String>of()
                : originalKeysOf(originalSelectors);
        var transformed = transformTarget(
                originalSelectors.components(),
                target,
                inserted,
                replacement,
                nestedOriginals
        );
        var transformedList = new SelectorList(transformed, originalSelectors.span());
        if (selectorListSemanticallyEquals(originalSelectors, transformedList)) {
            return null;
        }

        // Expand bare :is/:matches/:where lists so :not(:is(a, b)) becomes :not(a):not(b).
        // Nested :not(...) (and other selector-taking pseudos) in the extender are
        // ignored, matching dart-sass's simplified :not extend algorithm.
        var expanded = new ArrayList<ComplexSelector>();
        for (var complex : transformed) {
            expanded.addAll(expandForNotArgument(complex));
        }

        if (originalSelectors.components().size() == 1) {
            var result = new ArrayList<SimpleSelector>(expanded.size());
            for (var complex : expanded) {
                result.add(new PseudoSelector(
                        notPseudo.name(),
                        notPseudo.element(),
                        new SelectorPseudoArgument(new SelectorList(List.of(complex), complex.span())),
                        notPseudo.span()
                ));
            }
            return result;
        }
        return List.of(new PseudoSelector(
                notPseudo.name(),
                notPseudo.element(),
                new SelectorPseudoArgument(new SelectorList(expanded, originalSelectors.span())),
                notPseudo.span()
        ));
    }

    /// Expands a complex selector for use as a {@code :not()} argument branch.
    ///
    /// Bare {@code :is}/{:matches}/{:where} lists are flattened. Nested
    /// {@code :not()} and other selector-taking pseudos are dropped.
    private static List<ComplexSelector> expandForNotArgument(ComplexSelector complex) {
        if (complex.components().size() != 1
                || !complex.leadingCombinators().isEmpty()
                || !complex.components().get(0).combinators().isEmpty()) {
            return List.of(complex);
        }
        var simples = complex.components().get(0).selector().components();
        if (simples.size() != 1 || !(simples.get(0) instanceof PseudoSelector pseudo)) {
            return List.of(complex);
        }
        if (isPseudoElement(pseudo)) {
            return List.of(complex);
        }
        var normalized = normalizedPseudoName(pseudo.name().value());
        return switch (normalized) {
            case "is", "matches", "where" -> {
                @Nullable SelectorList args = selectorArgument(pseudo);
                yield args == null ? List.of(complex) : args.components();
            }
            default -> {
                // Nested :not(...) and other functional selector pseudos are ignored.
                if (selectorArgument(pseudo) != null) {
                    yield List.of();
                }
                yield List.of(complex);
            }
        };
    }

    /// Expands a complex selector that is only a single {@code :is}/{:matches}/{:where}
    /// into its argument complexes; otherwise returns the complex unchanged.
    private static List<ComplexSelector> expandUnionPseudoComplex(ComplexSelector complex) {
        if (complex.components().size() != 1
                || !complex.leadingCombinators().isEmpty()
                || !complex.components().get(0).combinators().isEmpty()) {
            return List.of(complex);
        }
        var simples = complex.components().get(0).selector().components();
        if (simples.size() != 1 || !(simples.get(0) instanceof PseudoSelector pseudo)) {
            return List.of(complex);
        }
        if (isPseudoElement(pseudo) || !isUnionSelectorPseudo(pseudo)) {
            return List.of(complex);
        }
        @Nullable SelectorList args = selectorArgument(pseudo);
        return args == null ? List.of(complex) : args.components();
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
        var nestedOriginals = replacement
                ? Set.<String>of()
                : originalKeysOf(originalSelectors);
        var transformed = transformTarget(
                originalSelectors.components(),
                target,
                inserted,
                replacement,
                nestedOriginals
        );
        // Flatten nested same-named union pseudos (e.g. :is(.c, :is(.d, .e)) → :is(.c, .d, .e)).
        // Drop same-named nth-child branches with a different An+B formula so
        // extend does not nest incompatible formulas (sass/sass#2828).
        // Drop same-normalized union pseudos with a different vendor prefix so
        // :-ms-matches is not extended by :-moz-matches (and vice versa).
        var expanded = new ArrayList<ComplexSelector>();
        for (var complex : transformed) {
            if (isIncompatibleNthChildBranch(complex, pseudo)
                    || isIncompatiblePrefixedUnionPseudoBranch(complex, pseudo)) {
                continue;
            }
            expanded.addAll(expandSameNameUnionPseudo(complex, pseudo));
        }
        var transformedSelectors = new SelectorList(expanded, originalSelectors.span());
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

    /// Returns whether {@code complex} is a same-named nth-child/nth-last-child
    /// with a different An+B formula than {@code outer}.
    ///
    /// Such branches must not be retained inside the outer {@code of} list.
    private static boolean isIncompatibleNthChildBranch(
            ComplexSelector complex,
            PseudoSelector outer
    ) {
        if (!(outer.argument() instanceof NthPseudoArgument outerNth)) {
            return false;
        }
        @Nullable PseudoSelector inner = singleCompoundPseudo(complex);
        if (inner == null
                || inner.element() != outer.element()
                || !inner.name().hasSameValue(outer.name())) {
            return false;
        }
        if (!(inner.argument() instanceof NthPseudoArgument innerNth)) {
            return false;
        }
        return !outerNth.formula().equals(innerNth.formula());
    }

    /// Returns whether {@code complex} is a union pseudo with the same normalized
    /// name as {@code outer} but a different vendor-prefixed spelling.
    ///
    /// Vendor-prefixed {@code :matches}/{@code :is}/{@code :any}/{@code :where}
    /// only unify with the same exact prefix; a differently prefixed sibling must
    /// not be nested into the outer argument list.
    private static boolean isIncompatiblePrefixedUnionPseudoBranch(
            ComplexSelector complex,
            PseudoSelector outer
    ) {
        @Nullable PseudoSelector inner = singleCompoundPseudo(complex);
        if (inner == null || inner.element() != outer.element()) {
            return false;
        }
        if (inner.name().hasSameValue(outer.name())) {
            return false;
        }
        var outerNormalized = normalizedPseudoName(outer.name().value());
        return switch (outerNormalized) {
            case "is", "matches", "where", "any", "current" ->
                    normalizedPseudoName(inner.name().value()).equals(outerNormalized);
            default -> false;
        };
    }

    /// Returns the sole simple pseudo of a single-compound complex selector.
    ///
    /// @param complex the complex selector to inspect
    /// @return the pseudo, or {@code null} when the shape is not a bare pseudo
    private static @Nullable PseudoSelector singleCompoundPseudo(ComplexSelector complex) {
        if (complex.components().size() != 1
                || !complex.leadingCombinators().isEmpty()
                || !complex.components().get(0).combinators().isEmpty()) {
            return null;
        }
        var simples = complex.components().get(0).selector().components();
        if (simples.size() != 1 || !(simples.get(0) instanceof PseudoSelector inner)) {
            return null;
        }
        return inner;
    }

    /// Expands a complex selector that is only a same-named union pseudo into its
    /// argument complexes, matching dart-sass {@code _extendPseudo} flattening.
    private static List<ComplexSelector> expandSameNameUnionPseudo(
            ComplexSelector complex,
            PseudoSelector outer
    ) {
        if (complex.components().size() != 1
                || !complex.leadingCombinators().isEmpty()
                || !complex.components().get(0).combinators().isEmpty()) {
            return List.of(complex);
        }
        var simples = complex.components().get(0).selector().components();
        if (simples.size() != 1 || !(simples.get(0) instanceof PseudoSelector inner)) {
            return List.of(complex);
        }
        if (inner.element() != outer.element() || !inner.name().hasSameValue(outer.name())) {
            return List.of(complex);
        }
        var normalized = normalizedPseudoName(outer.name().value());
        // Flatten only idempotent union pseudos. Non-idempotent forms such as
        // :has/:host keep nested same-named branches (e.g. :has(.c, :has(.d))).
        return switch (normalized) {
            case "is", "matches", "where", "any", "current" -> {
                @Nullable SelectorList args = selectorArgument(inner);
                yield args == null ? List.of(complex) : args.components();
            }
            case "nth-child", "nth-last-child" -> {
                if (!(outer.argument() instanceof NthPseudoArgument outerNth)
                        || !(inner.argument() instanceof NthPseudoArgument innerNth)
                        || !outerNth.formula().equals(innerNth.formula())) {
                    yield List.of(complex);
                }
                @Nullable SelectorList args = selectorArgument(inner);
                yield args == null ? List.of(complex) : args.components();
            }
            default -> List.of(complex);
        };
    }

    /// Returns whether one pseudo selector may recursively transform its selector argument.
    ///
    /// Includes non-idempotent relationship pseudos such as {@code :has()} and
    /// {@code :host()} so extender selectors are added to their argument lists
    /// (dart-sass non-idempotent extend fixtures).
    ///
    /// @param pseudo the pseudo selector to inspect
    /// @return whether its nested selector list may be transformed locally
    private static boolean supportsRecursivePseudoArgumentTransformation(PseudoSelector pseudo) {
        if (selectorArgument(pseudo) == null) {
            return false;
        }
        return switch (normalizedPseudoName(pseudo.name().value())) {
            case "is", "matches", "where", "any", "current", "slotted", "nth-child",
                    "nth-last-child", "has", "host", "host-context" -> true;
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
        // Multi-combinator complexes (e.g. ".c ~ ~ .d", "+ ~ .e") never produce
        // extension alternatives. Single trailing combinators remain extendable.
        if (hasMultipleCombinators(selector)) {
            return List.of();
        }
        var result = new ArrayList<ComplexSelector>();
        for (var index = 0; index < selector.components().size(); index++) {
            var component = selector.components().get(index);
            if (!compoundMatchesExtendee(target, component.selector())) {
                continue;
            }
            for (var alternative : inserted.components()) {
                if (hasMultipleCombinators(alternative)) {
                    continue;
                }
                result.addAll(replaceOccurrence(
                        selector,
                        index,
                        target,
                        alternative
                ));
            }
        }
        return deduplicate(result);
    }

    /// Returns whether a complex selector has multi leading or multi mid-chain
    /// combinators (invalid CSS), excluding lone trailing combinators.
    private static boolean hasMultipleCombinators(ComplexSelector complex) {
        if (complex.leadingCombinators().size() > 1) {
            return true;
        }
        for (var component : complex.components()) {
            if (component.combinators().size() > 1) {
                return true;
            }
        }
        return false;
    }

    /// Replaces one matched compound component with a complex selector alternative.
    ///
    /// When the extender has ancestor compounds, their prefixes are woven with the
    /// source prefix using dart-sass {@code weave}, producing every order-preserving
    /// interleaving before the residual suffix is appended.
    ///
    /// @param source      the source complex selector
    /// @param componentAt the matched component index
    /// @param target      the matched compound selector
    /// @param inserted    the replacement complex selector
    /// @return the replacement selectors, or empty when unification fails
    private static @Unmodifiable List<ComplexSelector> replaceOccurrence(
            ComplexSelector source,
            int componentAt,
            CompoundSelector target,
            ComplexSelector inserted
    ) {
        // Combinator-only extenders cannot merge into compounds; dart-sass keeps
        // them as free-standing relative selectors beside the original.
        if (inserted.components().isEmpty()) {
            return List.of(inserted);
        }
        var sourceComponent = source.components().get(componentAt);
        var residual = removeTargetSimples(sourceComponent.selector(), target);
        var finalInserted = inserted.components().get(inserted.components().size() - 1);
        @Nullable CompoundSelector mergedFinal = mergeResidual(finalInserted.selector(), residual);
        if (mergedFinal == null) {
            return List.of();
        }

        // Trailing combinators on the matched source compound and on the extender
        // must agree when both are present; otherwise the replacement is dropped.
        // When only one side has trailing combinators, those link to the suffix.
        var sourceLinks = sourceComponent.combinators();
        var extenderLinks = finalInserted.combinators();
        @Nullable List<Combinator> linkCombinators;
        if (sourceLinks.isEmpty()) {
            linkCombinators = extenderLinks;
        } else if (extenderLinks.isEmpty()) {
            linkCombinators = sourceLinks;
        } else if (sourceLinks.equals(extenderLinks)) {
            linkCombinators = sourceLinks;
        } else {
            return List.of();
        }

        var span = source.span();
        var suffix = source.components().subList(componentAt + 1, source.components().size());
        var extenderComponents = new ArrayList<ComplexSelectorComponent>(
                inserted.components().size()
        );
        for (var index = 0; index < inserted.components().size(); index++) {
            var component = inserted.components().get(index);
            if (index == inserted.components().size() - 1) {
                extenderComponents.add(new ComplexSelectorComponent(
                        mergedFinal,
                        linkCombinators,
                        sourceComponent.span()
                ));
            } else {
                extenderComponents.add(component);
            }
        }
        var extender = new ComplexSelector(
                inserted.leadingCombinators(),
                extenderComponents,
                span
        );

        List<ComplexSelector> woven;
        if (componentAt == 0) {
            // No source ancestors to weave; keep source leading combinators when the
            // extender has none, otherwise require compatible leading combinators.
            @Nullable List<Combinator> leading = mergeLeadingCombinators(
                    source.leadingCombinators(),
                    extender.leadingCombinators()
            );
            if (leading == null) {
                return List.of();
            }
            woven = List.of(new ComplexSelector(leading, extender.components(), span));
        } else {
            var prefix = new ComplexSelector(
                    source.leadingCombinators(),
                    source.components().subList(0, componentAt),
                    span
            );
            woven = weave(List.of(prefix, extender), span);
        }

        if (suffix.isEmpty()) {
            return woven;
        }
        var result = new ArrayList<ComplexSelector>(woven.size());
        for (var complex : woven) {
            var components = new ArrayList<ComplexSelectorComponent>(
                    complex.components().size() + suffix.size()
            );
            components.addAll(complex.components());
            components.addAll(suffix);
            result.add(new ComplexSelector(complex.leadingCombinators(), components, span));
        }
        return result;
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
    /// Used by {@code @extend} to track which module introduced each complex.
    ///
    /// @param selector the selector to key
    /// @return the semantic key
    public static String complexKey(ComplexSelector selector) {
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

    /// Returns a lowercase pseudo name with one vendor prefix removed.
    ///
    /// Matches dart-sass {@code unvendor}: a single {@code -prefix-} segment is
    /// stripped so {@code :-pfx-is(...)} participates in {@code :is} algebra.
    ///
    /// @param name the decoded pseudo identifier
    /// @return the normalized pseudo name used for semantic dispatch
    private static String normalizedPseudoName(String name) {
        var normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.length() < 2
                || normalized.charAt(0) != '-'
                || normalized.charAt(1) == '-') {
            return normalized;
        }
        for (var index = 2; index < normalized.length(); index++) {
            if (normalized.charAt(index) == '-') {
                return normalized.substring(index + 1);
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

    /// Returns whether a complex selector uses multi or trailing combinators.
    ///
    /// Combinator-only relative selectors (leading combinators with no compounds)
    /// are not considered bogus for this check: selector functions may retain them
    /// as identity results, matching dart-sass 1.x.
    ///
    /// @param complex the complex selector
    /// @return whether algebra should decline to unify it
    private static boolean hasBogusCombinators(ComplexSelector complex) {
        // Multiple leading combinators (e.g. "> + .c") are invalid CSS.
        if (complex.leadingCombinators().size() > 1) {
            return true;
        }
        for (var component : complex.components()) {
            if (component.combinators().size() > 1) {
                return true;
            }
        }
        if (!complex.components().isEmpty()
                && !complex.components().get(complex.components().size() - 1).combinators().isEmpty()) {
            return true;
        }
        return false;
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
