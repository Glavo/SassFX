// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies inspect-mode map serialization for nested comma lists.
@NotNullByDefault
final class SassMapInspectTest {
    /// Comma lists used as map values must keep outer parentheses so trailing
    /// separators remain unambiguous in inspect output.
    @Test
    void wrapsCommaListsUsedAsMapValues() {
        var entries = new LinkedHashMap<SassValue, SassValue>();
        entries.put(
                new SassString("positional", false),
                new SassList(List.of(SassNumber.of(1, null)), ListSeparator.COMMA, false)
        );
        entries.put(new SassString("named", false), new SassMap(Map.of()));

        assertEquals(
                "(positional: ((1,)), named: ())",
                new SassMap(entries).toString()
        );
    }

    /// Empty and multi-element comma lists also receive grouping parentheses.
    @Test
    void wrapsEmptyAndMultiElementCommaLists() {
        var empty = new LinkedHashMap<SassValue, SassValue>();
        empty.put(
                new SassString("positional", false),
                new SassList(List.of(), ListSeparator.COMMA, false)
        );
        assertEquals("(positional: (()))", new SassMap(empty).toString());

        var multi = new LinkedHashMap<SassValue, SassValue>();
        multi.put(
                new SassString("positional", false),
                new SassList(
                        List.of(SassNumber.of(1, null), SassNumber.of(2, null)),
                        ListSeparator.COMMA,
                        false
                )
        );
        assertEquals("(positional: (1, 2))", new SassMap(multi).toString());
    }
}
