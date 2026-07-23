// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /// Verifies parent selectors nested inside selector-taking pseudo arguments.
    @Test
    void nestsRecursivePseudoArguments() {
        assertEquals(
                ":is(.parent, .fallback)",
                nest(".parent", ":is(&, .fallback)")
        );
        assertEquals(
                ".parent:not(.parent)",
                nest(".parent", "&:not(&)")
        );
        assertEquals(
                ":is(.a, .b)",
                nest(".a, .b", ":is(&)")
        );
        assertEquals(
                ":has(> .parent)",
                nest(".parent", ":has(> &)")
        );
        assertEquals(
                ":nth-child(2n + 1 of .parent)",
                nest(".parent", ":nth-child(2n + 1 of &)")
        );
    }

    /// Verifies parent markers in opaque pseudo arguments fail before serialization.
    @Test
    void rejectsOpaquePseudoParentMarkers() {
        var failure = assertThrows(
                SassValueException.class,
                () -> nest(".parent", ":lang(&)")
        );
        assertEquals(
                "Parent selectors in non-selector pseudo arguments aren't supported.",
                failure.getMessage()
        );
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
