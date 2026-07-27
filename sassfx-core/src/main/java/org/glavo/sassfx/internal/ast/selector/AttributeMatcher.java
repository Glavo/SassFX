// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Defines the value comparison used by an attribute selector.
@ApiStatus.Internal
@NotNullByDefault
public enum AttributeMatcher {
    /// Requires an exact attribute value match.
    EQUALS("="),

    /// Requires one whitespace-separated attribute value to match.
    INCLUDES("~="),

    /// Requires an exact value or a value followed by a hyphen.
    DASH_MATCH("|="),

    /// Requires the attribute value to begin with the supplied value.
    PREFIX_MATCH("^="),

    /// Requires the attribute value to end with the supplied value.
    SUFFIX_MATCH("$="),

    /// Requires the attribute value to contain the supplied value.
    SUBSTRING_MATCH("*=");

    /// Contains the CSS operator spelling.
    private final String css;

    /// Creates an attribute matcher.
    ///
    /// @param css the CSS operator spelling
    AttributeMatcher(String css) {
        this.css = css;
    }

    /// Returns the CSS spelling of this matcher.
    ///
    /// @return the operator text
    public String css() {
        return css;
    }
}
