// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// A CSS combinator that joins compound selectors.
@ApiStatus.Internal
@NotNullByDefault
public enum Combinator {
    /// The child combinator `>`.
    CHILD(">"),

    /// The next-sibling combinator `+`.
    NEXT_SIBLING("+"),

    /// The following-sibling combinator `~`.
    FOLLOWING_SIBLING("~");

    /// Contains the CSS spelling of this combinator.
    private final String css;

    /// Creates a combinator with its CSS spelling.
    ///
    /// @param css the CSS text
    Combinator(String css) {
        this.css = css;
    }

    /// Returns the CSS spelling of this combinator.
    ///
    /// @return the combinator text
    public String css() {
        return css;
    }
}
