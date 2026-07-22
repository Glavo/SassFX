// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies HRX archive parsing and virtual-path validation.
@NotNullByDefault
final class HrxArchiveTest {
    /// Preserves archive order, file contents, and terminal line terminators.
    @Test
    void parsesVirtualFiles() {
        var archive = HrxArchive.parse(
                """
                        <===> core/input.scss
                        $color: red;
                        <===> core/output.css
                        a {
                          color: red;
                        }
                        """,
                "fixture.hrx"
        );

        assertEquals(List.of("core/input.scss", "core/output.css"), List.copyOf(archive.paths()));
        assertEquals("$color: red;\n", archive.content("core/input.scss"));
        assertEquals("""
                        a {
                          color: red;
                        }
                        """, archive.content("core/output.css"));
    }

    /// Rejects duplicate, conflicting, and unsafe virtual paths.
    @Test
    void rejectsInvalidVirtualPaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HrxArchive.parse("<===> input.scss\na {}\n<===> input.scss\nb {}\n", "duplicate.hrx")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> HrxArchive.parse("<===> node\n<===> node/input.scss\na {}\n", "conflict.hrx")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> HrxArchive.parse("<===> ../input.scss\na {}\n", "unsafe.hrx")
        );
    }
}
