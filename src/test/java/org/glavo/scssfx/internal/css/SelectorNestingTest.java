// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies first-pass string selector nesting.
@NotNullByDefault
final class SelectorNestingTest {
    /// Verifies root selectors, descendant nesting, parent injection, and lists.
    @Test
    void nestsSelectors() {
        assertEquals("a", SelectorNesting.nest(null, " a "));
        assertEquals("a b", SelectorNesting.nest("a", "b"));
        assertEquals("a:hover", SelectorNesting.nest("a", "&:hover"));
        assertEquals("a-child", SelectorNesting.nest("a", "&-child"));
        assertEquals(
                "a c, a d, b c, b d",
                SelectorNesting.nest("a, b", "c, d")
        );
    }
}
