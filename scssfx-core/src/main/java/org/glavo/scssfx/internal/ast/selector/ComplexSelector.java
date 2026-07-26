// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A complex selector made of compound components joined by combinators.
///
/// @param leadingCombinators combinators before the first compound
/// @param components         the ordered compounds and their trailing combinators
/// @param span               the complex selector span
/// @param lineBreak          whether a line break preceded this complex after a
///                           comma in the source selector list (dart-sass
///                           {@code ComplexSelector.lineBreak})
@ApiStatus.Internal
@NotNullByDefault
public record ComplexSelector(
        @Unmodifiable List<Combinator> leadingCombinators,
        @Unmodifiable List<ComplexSelectorComponent> components,
        SourceSpan span,
        boolean lineBreak
) {
    /// Creates a complex selector without a preceding line break.
    ///
    /// @param leadingCombinators combinators before the first compound
    /// @param components         the ordered compounds and their trailing combinators
    /// @param span               the complex selector span
    public ComplexSelector(
            List<Combinator> leadingCombinators,
            List<ComplexSelectorComponent> components,
            SourceSpan span
    ) {
        this(leadingCombinators, components, span, false);
    }

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

    /// Returns this complex with an explicit preceding-line-break flag.
    ///
    /// @param lineBreak whether a line break precedes this complex after a comma
    /// @return this complex, or a copy with the updated flag
    public ComplexSelector withLineBreak(boolean lineBreak) {
        return this.lineBreak == lineBreak
                ? this
                : new ComplexSelector(leadingCombinators, components, span, lineBreak);
    }

    /// Returns whether this complex selector contains a parent selector.
    ///
    /// @return whether {@code &} is present directly or in a recursive pseudo argument
    public boolean containsParentSelector() {
        for (var component : components) {
            for (var simple : component.selector().components()) {
                if (simple.containsParentSelector()) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Returns whether this complex selector contains a direct parent selector.
    ///
    /// @return whether one compound directly contains {@code &}
    public boolean containsDirectParentSelector() {
        for (var component : components) {
            for (var simple : component.selector().components()) {
                if (simple instanceof ParentSelector) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Returns the number of structurally represented parent selectors.
    ///
    /// @return the recursive parent-selector count
    public int parentSelectorCount() {
        var count = 0;
        for (var component : components) {
            for (var simple : component.selector().components()) {
                count += simple.parentSelectorCount();
            }
        }
        return count;
    }

    /// Returns whether a parent selector in this complex selector has a suffix.
    ///
    /// @return whether a direct or recursive parent selector has a suffix
    public boolean hasParentSelectorSuffix() {
        for (var component : components) {
            for (var simple : component.selector().components()) {
                if (simple.hasParentSelectorSuffix()) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Returns whether a parent reference cannot be replaced structurally.
    ///
    /// @return whether an opaque pseudo argument contains {@code &}
    public boolean hasUnresolvedParentReference() {
        for (var component : components) {
            for (var simple : component.selector().components()) {
                if (simple.hasUnresolvedParentReference()) {
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
        boolean nextLineBreak = lineBreak || child.lineBreak;
        if (components.isEmpty()) {
            var leading = new ArrayList<>(leadingCombinators);
            leading.addAll(child.leadingCombinators);
            return new ComplexSelector(leading, child.components, span, nextLineBreak);
        }
        if (child.components.isEmpty()) {
            var last = components.get(components.size() - 1);
            var nextComponents = new ArrayList<>(components.subList(0, components.size() - 1));
            nextComponents.add(last.withAdditionalCombinators(child.leadingCombinators));
            return new ComplexSelector(leadingCombinators, nextComponents, span, nextLineBreak);
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
        return new ComplexSelector(leadingCombinators, nextComponents, span, nextLineBreak);
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
            return new ComplexSelector(leading, components, span, lineBreak);
        }
        var next = new ArrayList<>(components.subList(0, components.size() - 1));
        next.add(components.get(components.size() - 1).withAdditionalCombinators(combinators));
        return new ComplexSelector(leadingCombinators, next, span, lineBreak);
    }

    /// Returns whether this complex selector is omitted from emitted CSS.
    ///
    /// A complex selector is invisible when any of its compounds contains an
    /// invisible simple selector, matching dart-sass {@code Selector.isInvisible}.
    ///
    /// @return whether CSS emission drops this complex selector
    public boolean isInvisible() {
        for (var component : components) {
            if (component.selector().isInvisible()) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether non-inspect CSS emission drops this complex selector.
    ///
    /// A complex is bogus when it has consecutive combinators, a trailing
    /// combinator after the last compound, relative selector arguments inside
    /// pseudos that reject them ({@code :is}, {@code :where}, {@code :not},
    /// {@code :matches}, {@code :any}), or any selector-taking pseudo whose
    /// argument complexes are themselves bogus. A single leading combinator
    /// such as {@code > a} is still serialized (with a deprecation).
    /// {@code :has()} keeps a single leading combinator in its argument.
    ///
    /// @return whether non-inspect CSS emission drops this complex selector
    public boolean isBogus() {
        if (hasBogusCombinatorStructure()) {
            return true;
        }
        for (var component : components) {
            for (var simple : component.selector().components()) {
                if (!(simple instanceof PseudoSelector pseudo)) {
                    continue;
                }
                if (rejectsRelativeSelectorArguments(pseudo)
                        && selectorArgumentHasRelativeComplex(pseudo)) {
                    return true;
                }
                if (selectorArgumentHasBogusComplex(pseudo)) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Returns whether this complex has consecutive or trailing combinators.
    ///
    /// Multiple leading combinators ({@code + ~ a}), multiple combinators
    /// between compounds ({@code a > + b}), or a trailing combinator with no
    /// following compound ({@code a >}) are invalid CSS and omitted.
    ///
    /// @return whether combinator structure alone makes this complex unemittable
    private boolean hasBogusCombinatorStructure() {
        // A relative selector with only leading combinators and no compound
        // ({@code +} alone, including after {@code + {@extend a}}) is not valid CSS.
        if (!leadingCombinators.isEmpty() && components.isEmpty()) {
            return true;
        }
        if (leadingCombinators.size() > 1) {
            return true;
        }
        for (var index = 0; index < components.size(); index++) {
            var combinators = components.get(index).combinators();
            if (combinators.size() > 1) {
                return true;
            }
            if (index == components.size() - 1 && !combinators.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether a selector-taking pseudo has any fully bogus argument.
    ///
    /// @param pseudo the pseudo selector
    /// @return whether an argument complex is {@link #isBogus()}
    private static boolean selectorArgumentHasBogusComplex(PseudoSelector pseudo) {
        if (!(pseudo.argument() instanceof SelectorPseudoArgument selectorArgument)) {
            return false;
        }
        for (var complex : selectorArgument.selectors().components()) {
            if (complex.isBogus()) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether a pseudo rejects relative selector-list arguments.
    ///
    /// @param pseudo the pseudo selector
    /// @return whether relative arguments make the complex unemittable
    private static boolean rejectsRelativeSelectorArguments(PseudoSelector pseudo) {
        if (pseudo.element() || !(pseudo.argument() instanceof SelectorPseudoArgument)) {
            return false;
        }
        var name = pseudo.name().value().toLowerCase(java.util.Locale.ROOT);
        if (name.length() >= 2 && name.charAt(0) == '-' && name.charAt(1) != '-') {
            for (var index = 2; index < name.length(); index++) {
                if (name.charAt(index) == '-') {
                    name = name.substring(index + 1);
                    break;
                }
            }
        }
        return switch (name) {
            case "is", "matches", "where", "any", "not" -> true;
            default -> false;
        };
    }

    /// Returns whether a selector-taking pseudo has any relative argument complex.
    ///
    /// @param pseudo the pseudo selector
    /// @return whether an argument complex begins with a combinator
    private static boolean selectorArgumentHasRelativeComplex(PseudoSelector pseudo) {
        if (!(pseudo.argument() instanceof SelectorPseudoArgument selectorArgument)) {
            return false;
        }
        for (var complex : selectorArgument.selectors().components()) {
            if (!complex.leadingCombinators().isEmpty()) {
                return true;
            }
            // Nested relative-rejecting pseudos also make the outer form unemittable.
            if (complex.isBogus()) {
                return true;
            }
        }
        return false;
    }

    /// Returns the CSS text of this complex selector.
    ///
    /// @return the serialized complex selector
    /// @throws SassValueException if an unresolved parent selector remains
    public String toCssString() {
        return toCssString(true);
    }

    /// Returns the CSS text of this complex selector.
    ///
    /// When {@code inspect} is {@code false}, placeholders are omitted and
    /// selector-taking pseudos are rewritten the way dart-sass serializes CSS.
    ///
    /// @param inspect whether placeholder selectors and full structure are retained
    /// @return the serialized complex selector
    /// @throws SassValueException if an unresolved parent selector remains
    public String toCssString(boolean inspect) {
        var result = new StringBuilder();
        for (var combinator : leadingCombinators) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(combinator.css());
        }
        for (var component : components) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            var compoundStart = result.length();
            for (var simple : component.selector().components()) {
                if (simple.hasUnresolvedParentReference()) {
                    throw new SassValueException(
                            "Parent selectors aren't allowed here."
                    );
                }
                if (!inspect) {
                    if (simple instanceof PlaceholderSelector) {
                        continue;
                    }
                    if (simple instanceof PseudoSelector pseudo) {
                        @Nullable String emitted = emitPseudo(pseudo);
                        if (emitted == null) {
                            continue;
                        }
                        result.append(emitted);
                        continue;
                    }
                }
                result.append(simple.toCssString());
            }
            if (!inspect && result.length() == compoundStart) {
                // All simples were placeholders or omitted `:not(%…)` forms.
                result.append('*');
            }
            for (var combinator : component.combinators()) {
                result.append(' ').append(combinator.css());
            }
        }
        return result.toString();
    }

    /// Serializes one pseudo selector for CSS emission.
    ///
    /// @param pseudo the pseudo selector
    /// @return the emitted text, or {@code null} when the pseudo is omitted
    private static @Nullable String emitPseudo(PseudoSelector pseudo) {
        if (!(pseudo.argument() instanceof SelectorPseudoArgument selectorArgument)) {
            return pseudo.toCssString();
        }
        var argumentList = selectorArgument.selectors();
        var name = pseudo.name().value();
        if ("not".equalsIgnoreCase(name) && argumentList.isInvisible()) {
            return null;
        }
        var emittedArgs = argumentList.toCssString(false);
        if (emittedArgs.isEmpty() && !"not".equalsIgnoreCase(name)) {
            // Fully invisible selector-taking pseudos make the complex invisible;
            // emission should not reach an empty non-`:not` argument list.
            return null;
        }
        var result = new StringBuilder(pseudo.element() ? "::" : ":");
        result.append(pseudo.name().toCssString());
        result.append('(').append(emittedArgs).append(')');
        return result.toString();
    }
}
