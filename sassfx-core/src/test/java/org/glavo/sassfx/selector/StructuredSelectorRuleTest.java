// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.selector;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies ordinary style rules use the structured selector parser.
@NotNullByDefault
final class StructuredSelectorRuleTest {
    /// Verifies namespaces, escaped identifiers, attributes, and pseudo
    /// selectors compile together without a JavaFX runtime dependency.
    @Test
    void compilesStructuredCssSelectors() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                svg|a.\\31 foo[data-kind|=primary i]:not(.blocked),
                                *|button[|title="save"]::before {
                                  color: red;
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        svg|a.\\31 foo[data-kind|=primary i]:not(.blocked),
                        *|button[|title=save]::before {
                          color: red;
                        }""",
                result.output()
        );
    }
}
