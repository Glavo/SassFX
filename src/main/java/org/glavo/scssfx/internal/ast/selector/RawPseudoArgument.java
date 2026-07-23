// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.internal.value.SassValueException;
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
        return css;
    }

    @Override
    public boolean containsParentSelector() {
        return containsParentMarker(css);
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
