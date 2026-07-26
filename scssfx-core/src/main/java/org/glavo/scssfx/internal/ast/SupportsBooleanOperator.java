// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies a boolean operator in a Sass `@supports` condition.
@ApiStatus.Internal
@NotNullByDefault
public enum SupportsBooleanOperator {
    /// Combines conditions with logical conjunction.
    AND("and"),

    /// Combines conditions with logical disjunction.
    OR("or");

    /// Contains the CSS spelling of this operator.
    private final String cssText;

    /// Creates a boolean operator constant.
    ///
    /// @param cssText the lowercase CSS spelling
    SupportsBooleanOperator(String cssText) {
        this.cssText = cssText;
    }

    /// Returns the canonical CSS spelling.
    ///
    /// @return the lowercase operator name
    public String cssText() {
        return cssText;
    }
}
