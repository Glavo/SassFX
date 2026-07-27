// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.sassspec;

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

    /// Keeps the last body when a path is redeclared and rejects conflicts.
    @Test
    void rejectsInvalidVirtualPaths() {
        var archive = HrxArchive.parse(
                "<===> input.scss\na {}\n<===> input.scss\nb {}\n",
                "duplicate.hrx"
        );
        assertEquals("b {}\n", archive.content("input.scss"));
        assertThrows(
                IllegalArgumentException.class,
                () -> HrxArchive.parse("<===> node\n<===> node/input.scss\na {}\n", "conflict.hrx")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> HrxArchive.parse("<===> ../input.scss\na {}\n", "unsafe.hrx")
        );
    }

    /// Discards bare HRX comment sections between files.
    @Test
    void ignoresBareCommentSections() {
        var archive = HrxArchive.parse(
                """
                        <===> a/input.scss
                        a {b: c}
                        <===>
                        ================================================================================
                        <===> a/output.css
                        a {
                          b: c;
                        }
                        """,
                "comments.hrx"
        );
        assertEquals(List.of("a/input.scss", "a/output.css"), List.copyOf(archive.paths()));
    }
}
