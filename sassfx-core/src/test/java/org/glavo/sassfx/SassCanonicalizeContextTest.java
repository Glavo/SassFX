// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies containing-URL access tracking for importer adapters.
@NotNullByDefault
final class SassCanonicalizeContextTest {
    /// Distinguishes observation from transport-only inspection.
    @Test
    void tracksContainingUrlAccess() {
        var containingUrl = URI.create("memory:///entry.scss");
        var context = new SassCanonicalizeContext(containingUrl, false);

        assertEquals(containingUrl, context.peekContainingUrl());
        assertFalse(context.isContainingUrlAccessed());

        context.markContainingUrlAccessed();
        context.markContainingUrlAccessed();
        assertTrue(context.isContainingUrlAccessed());

        var direct = new SassCanonicalizeContext(containingUrl, false);
        assertEquals(containingUrl, direct.containingUrl());
        assertTrue(direct.isContainingUrlAccessed());
    }

    /// Records direct access even when no containing URL is available.
    @Test
    void tracksAbsentContainingUrlAccess() {
        var context = new SassCanonicalizeContext(null, true);

        assertNull(context.peekContainingUrl());
        assertFalse(context.isContainingUrlAccessed());
        assertNull(context.containingUrl());
        assertTrue(context.isContainingUrlAccessed());
        assertTrue(context.fromImport());
    }
}
