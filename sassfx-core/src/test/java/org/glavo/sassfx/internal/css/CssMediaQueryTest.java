// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Verifies media-query parsing, serialization, and nesting intersections.
@NotNullByDefault
final class CssMediaQueryTest {
    /// Parses type and condition query forms without a CSS runtime.
    @Test
    void parsesAndSerializesMediaQueries() {
        var queries = CssMediaQuery.parseList(
                "screen and (min-width: 30px), (hover) or (pointer: fine)"
        );

        assertEquals(2, queries.size());
        assertEquals("screen and (min-width: 30px)", queries.get(0).toCssString());
        assertEquals("(hover) or (pointer: fine)", queries.get(1).toCssString());
        assertEquals("screen and (min-width: 30px)", queries.get(0).toCompressedCss());
        assertEquals("(hover)or (pointer: fine)", queries.get(1).toCompressedCss());

        var negated = CssMediaQuery.parseList("not (hover)").get(0);
        assertEquals("not (hover)", negated.toCssString());
        assertEquals("not (hover)", negated.toCompressedCss());
    }

    /// Merges compatible query lists and identifies nonrepresentable intersections.
    @Test
    void mergesNestedMediaQueryLists() {
        @Nullable List<CssMediaQuery> merged = CssMediaQuery.mergeLists(
                CssMediaQuery.parseList("screen, print"),
                CssMediaQuery.parseList("(min-width: 30px)")
        );
        assertNotNull(merged);
        assertEquals(
                List.of(
                        "screen and (min-width: 30px)",
                        "print and (min-width: 30px)"
                ),
                merged.stream().map(CssMediaQuery::toCssString).toList()
        );

        assertEquals(
                List.of(),
                CssMediaQuery.mergeLists(
                        CssMediaQuery.parseList("screen"),
                        CssMediaQuery.parseList("print")
                )
        );
        assertNull(
                CssMediaQuery.mergeLists(
                        CssMediaQuery.parseList("(hover) or (pointer: fine)"),
                        CssMediaQuery.parseList("(min-width: 30px)")
                )
        );
    }
}
