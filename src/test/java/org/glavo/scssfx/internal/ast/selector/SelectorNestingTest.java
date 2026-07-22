// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies selector parsing and parent nesting.
@NotNullByDefault
final class SelectorNestingTest {
    /// Verifies root selectors, descendant nesting, parent injection, and lists.
    @Test
    void nestsSelectors() {
        assertEquals("a", nest(null, " a "));
        assertEquals("a b", nest("a", "b"));
        assertEquals("a:hover", nest("a", "&:hover"));
        assertEquals("a-child", nest("a", "&-child"));
        assertEquals("a c, b c, a d, b d", nest("a, b", "c, d"));
        assertEquals("a > b", nest("a", "> b"));
        assertEquals(".foo.bar", nest(".foo", "&.bar"));
        assertEquals("a:hover, b:hover", nest("a, b", "&:hover"));
    }

    /// Nests a child selector within an optional parent.
    ///
    /// @param parent the parent selector text, or {@code null}
    /// @param child  the child selector text
    /// @return the nested CSS selector text
    private static String nest(@Nullable String parent, String child) {
        var childList = SelectorList.parse(child, span(child));
        @Nullable SelectorList parentList =
                parent == null ? null : SelectorList.parse(parent, span(parent));
        return childList.nestWithin(parentList).toCssString();
    }

    /// Creates a synthetic span for selector text.
    ///
    /// @param text the selector text
    /// @return a span covering the text
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
