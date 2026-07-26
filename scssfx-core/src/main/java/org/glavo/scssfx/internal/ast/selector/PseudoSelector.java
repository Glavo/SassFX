// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/// Represents a pseudo-class or pseudo-element selector.
///
/// Functional pseudo selectors retain a grammar-specific argument model so
/// selector-taking pseudos can participate in parent-selector substitution.
///
/// @param name     the pseudo identifier
/// @param element  whether this uses pseudo-element syntax
/// @param argument the functional argument, or {@code null} when absent
/// @param span     the source span
@ApiStatus.Internal
@NotNullByDefault
public record PseudoSelector(
        CssIdentifier name,
        boolean element,
        @Nullable PseudoArgument argument,
        SourceSpan span
) implements SimpleSelector {
    /// Creates a pseudo selector.
    public PseudoSelector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }

    /// Returns whether this pseudo selector uses class syntax.
    ///
    /// @return whether this selector is prefixed with one colon
    public boolean isClass() {
        return !element;
    }

    @Override
    public String toCssString() {
        var result = new StringBuilder(element ? "::" : ":");
        result.append(name.toCssString());
        if (argument != null) {
            result.append('(').append(argument.toCssString()).append(')');
        }
        return result.toString();
    }

    @Override
    public boolean isInvisible() {
        if (!(argument instanceof SelectorPseudoArgument selectorArgument)) {
            return false;
        }
        // `:not(%foo)` stays visible: it means "not nothing" and serializes away.
        if ("not".equals(name.value().toLowerCase(Locale.ROOT))) {
            return false;
        }
        return selectorArgument.selectors().isInvisible();
    }

    @Override
    public boolean containsParentSelector() {
        return argument != null && argument.containsParentSelector();
    }

    @Override
    public int parentSelectorCount() {
        return argument == null ? 0 : argument.parentSelectorCount();
    }

    @Override
    public boolean hasParentSelectorSuffix() {
        return argument != null && argument.hasParentSelectorSuffix();
    }

    @Override
    public boolean hasUnresolvedParentReference() {
        return argument != null && argument.hasUnresolvedParentReference();
    }

    /// Replaces parent selectors represented by this pseudo argument.
    ///
    /// @param parent the selector list that replaces each parent selector
    /// @return this selector with recursively replaced parent references
    /// @throws SassValueException if an opaque argument contains {@code &}
    PseudoSelector replaceParentSelectors(SelectorList parent) {
        Objects.requireNonNull(parent, "parent");
        return argument == null ? this : new PseudoSelector(
                name,
                element,
                argument.replaceParentSelectors(parent),
                span
        );
    }

    @Override
    public SimpleSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        throw new SassValueException("Pseudo selector can't have a suffix.");
    }
}
