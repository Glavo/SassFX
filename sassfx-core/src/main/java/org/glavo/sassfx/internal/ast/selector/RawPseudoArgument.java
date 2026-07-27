// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Retains a functional pseudo-selector argument whose grammar is not a selector list.
///
/// The text may be empty, which distinguishes a call such as {@code :lang()}
/// from a pseudo selector without parentheses.
///
/// @param css the argument CSS without enclosing parentheses
@ApiStatus.Internal
@NotNullByDefault
public record RawPseudoArgument(String css) implements PseudoArgument {
    /// Creates one opaque pseudo-selector argument.
    public RawPseudoArgument {
        Objects.requireNonNull(css, "css");
    }

    @Override
    public String toCssString() {
        // Collapse CSS whitespace runs to a single space and strip outer
        // whitespace so An+B formulas such as {@code 2n  +  1} and indented
        // continuations such as {@code a:b(\n  c)} match dart-sass emission
        // (sass-spec issue_1650, indented pseudo args).
        return collapseCssWhitespace(css);
    }

    /// Collapses internal CSS whitespace runs to one space and strips outer
    /// whitespace, including newlines and form feeds.
    ///
    /// @param text the raw argument text
    /// @return the normalized argument text
    private static String collapseCssWhitespace(String text) {
        var start = 0;
        var end = text.length();
        while (start < end && isCssWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && isCssWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return "";
        }
        var result = new StringBuilder(end - start);
        var previousWasWhitespace = false;
        for (var index = start; index < end; index++) {
            var character = text.charAt(index);
            if (isCssWhitespace(character)) {
                if (!previousWasWhitespace) {
                    result.append(' ');
                    previousWasWhitespace = true;
                }
            } else {
                result.append(character);
                previousWasWhitespace = false;
            }
        }
        return result.toString();
    }

    /// Returns whether {@code character} is CSS whitespace.
    private static boolean isCssWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\n'
                || character == '\r'
                || character == '\f';
    }

    /// Opaque arguments never carry structural parent-selector AST nodes.
    ///
    /// A literal {@code &} character may still appear in the raw text; that is
    /// tracked only by [#hasUnresolvedParentReference()] so nesting can reject
    /// {@code :lang(&)} without treating {@code :c(*&^)} as a parent selector
    /// for selector algebra.
    @Override
    public boolean containsParentSelector() {
        return false;
    }

    @Override
    public int parentSelectorCount() {
        return 0;
    }

    @Override
    public boolean hasParentSelectorSuffix() {
        return false;
    }

    @Override
    public boolean hasUnresolvedParentReference() {
        return containsParentMarker(css);
    }

    @Override
    public PseudoArgument replaceParentSelectors(SelectorList parent) {
        Objects.requireNonNull(parent, "parent");
        if (containsParentMarker(css)) {
            throw new SassValueException(
                    "Parent selectors in non-selector pseudo arguments aren't supported."
            );
        }
        return this;
    }

    /// Returns whether text contains one unescaped parent marker outside quotes.
    ///
    /// @param text the pseudo argument text
    /// @return whether the text contains an unresolved {@code &}
    static boolean containsParentMarker(String text) {
        Objects.requireNonNull(text, "text");
        var quote = 0;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (character == '\\' && index + 1 < text.length()) {
                index++;
                continue;
            }
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '&') {
                return true;
            }
        }
        return false;
    }
}
