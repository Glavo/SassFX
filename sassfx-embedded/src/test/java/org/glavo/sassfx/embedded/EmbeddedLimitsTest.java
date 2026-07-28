// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Embedded Sass endpoint resource-limit contracts.
@NotNullByDefault
final class EmbeddedLimitsTest {
    /// Accepts the documented default endpoint limits.
    @Test
    void providesPositiveDefaults() {
        var limits = EmbeddedLimits.DEFAULT;

        assertTrue(limits.maxPacketLength() > 0);
        assertTrue(limits.maxConcurrentCompilations() > 0);
        assertTrue(limits.maxQueuedCompilations() > 0);
        assertTrue(limits.maxInboundEvents() > 0);
        assertDoesNotThrow(() -> new EmbeddedCompiler(limits));
    }

    /// Rejects every nonpositive limit independently.
    @Test
    void rejectsNonpositiveLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddedLimits(0, 1, 1, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddedLimits(1, 0, 1, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddedLimits(1, 1, 0, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddedLimits(1, 1, 1, 0)
        );
    }
}
