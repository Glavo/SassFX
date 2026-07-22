// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies output target configuration contracts.
@NotNullByDefault
final class OutputTargetTest {
    /// Verifies the standard CSS target defaults.
    @Test
    void providesCssDefaults() {
        OutputTarget<String> target = CssTarget.DEFAULT;
        var css = assertInstanceOf(CssTarget.class, target);

        assertEquals(OutputStyle.EXPANDED, css.style());
        assertEquals(true, css.charset());
    }

    /// Verifies the JavaFX target defaults.
    @Test
    void providesJavaFxDefaults() {
        var target = JavaFxCssTarget.DEFAULT;

        assertEquals(JavaFxCompatibility.JAVA_FX_17, target.compatibility());
        assertEquals(OutputStyle.EXPANDED, target.style());
    }

    /// Verifies that compatibility levels select their exact BSS versions.
    @Test
    void mapsCompatibilityToBssVersion() {
        OutputTarget<ByteBuffer> target = BssTarget.DEFAULT;
        var bss = assertInstanceOf(BssTarget.class, target);

        assertEquals(6, bss.version());
        assertEquals(9, new BssTarget(JavaFxCompatibility.JAVA_FX_27).version());
    }

    /// Verifies that required target components reject null.
    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> new CssTarget(null, true));
        assertThrows(
                NullPointerException.class,
                () -> new JavaFxCssTarget(null, OutputStyle.EXPANDED)
        );
        assertThrows(
                NullPointerException.class,
                () -> new JavaFxCssTarget(JavaFxCompatibility.JAVA_FX_17, null)
        );
        assertThrows(NullPointerException.class, () -> new BssTarget(null));
    }
}
