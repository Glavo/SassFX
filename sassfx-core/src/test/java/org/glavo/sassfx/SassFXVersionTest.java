// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies generated SassFX implementation metadata.
@NotNullByDefault
final class SassFXVersionTest {
    /// Reads the root Gradle project version from the generated resource.
    @Test
    void reportsBuildVersion() {
        var expected = Objects.requireNonNull(
                System.getProperty("sassfx.test.expectedVersion"),
                "Missing expected build version"
        );
        assertEquals(expected, SassFXVersion.current());
    }
}
