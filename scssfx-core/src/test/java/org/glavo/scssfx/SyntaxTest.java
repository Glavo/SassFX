// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Verifies stylesheet syntax inference.
@NotNullByDefault
final class SyntaxTest {
    /// Verifies all supported file extensions without case sensitivity.
    @Test
    void recognizesSupportedExtensions() {
        assertEquals(Syntax.SCSS, Syntax.forPath(Path.of("theme.scss")));
        assertEquals(Syntax.SASS, Syntax.forPath(Path.of("theme.SASS")));
        assertEquals(Syntax.CSS, Syntax.forPath(Path.of("theme.CsS")));
    }

    /// Verifies that only the final extension controls inference.
    @Test
    void usesFinalExtension() {
        assertEquals(Syntax.SCSS, Syntax.forPath(Path.of("theme.css.scss")));
    }

    /// Verifies paths that do not identify a supported syntax.
    @Test
    void rejectsUnknownOrMissingExtensions() {
        assertNull(Syntax.forPath(Path.of("theme")));
        assertNull(Syntax.forPath(Path.of("theme.")));
        assertNull(Syntax.forPath(Path.of(".scss")));
        assertNull(Syntax.forPath(Path.of("theme.less")));
    }
}
