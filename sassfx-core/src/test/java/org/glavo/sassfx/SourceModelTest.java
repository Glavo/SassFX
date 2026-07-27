// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies public source location and span invariants.
@NotNullByDefault
final class SourceModelTest {
    /// Verifies that source indices must be nonnegative.
    @Test
    void rejectsNegativeLocationIndices() {
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation(0, 0, -1));
    }

    /// Verifies half-open source span construction.
    @Test
    void createsHalfOpenSpan() {
        var url = URI.create("file:///style.scss");
        var span = new SourceSpan(
                url,
                new SourceLocation(1, 2, 4),
                new SourceLocation(1, 5, 7),
                "abc"
        );

        assertEquals(url, span.url());
        assertEquals("abc", span.text());
    }

    /// Verifies source span ordering and captured-text constraints.
    @Test
    void rejectsInconsistentSpan() {
        var start = new SourceLocation(0, 2, 2);
        var end = new SourceLocation(0, 1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSpan(null, start, end, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSpan(null, start, new SourceLocation(0, 4, 4), "x")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSpan(
                        null,
                        new SourceLocation(1, 0, 1),
                        new SourceLocation(0, 2, 2),
                        "x"
                )
        );
    }
}
