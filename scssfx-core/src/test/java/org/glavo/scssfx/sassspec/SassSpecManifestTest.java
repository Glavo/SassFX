// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import org.glavo.scssfx.DiagnosticSeverity;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies strict parsing of the versioned fixture manifest.
@NotNullByDefault
final class SassSpecManifestTest {
    /// Reads executable and explicitly skipped fixture declarations.
    @Test
    void readsVersionedFixtureDeclarations() throws Exception {
        var manifest = SassSpecManifest.parse(
                """
                        {
                          "format": 1,
                          "archives": [
                            {
                              "path": "core.hrx",
                              "cases": [
                                {
                                  "directory": "core/basic",
                                  "action": "run",
                                  "category": "CORE",
                                  "loadedUrls": ["input.scss"],
                                  "diagnostics": [
                                    {"severity": "DEPRECATION", "code": "slash-div"}
                                  ]
                                },
                                {
                                  "directory": "syntax/indented",
                                  "action": "skip",
                                  "category": "UNSUPPORTED_SYNTAX",
                                  "reason": "Indented syntax is not implemented."
                                }
                              ]
                            }
                          ]
                        }
                        """,
                "manifest.json"
        );

        assertEquals(1, manifest.format());
        var archive = manifest.archives().get(0);
        assertEquals("core.hrx", archive.path());
        assertEquals(2, archive.cases().size());

        var runCase = archive.cases().get(0);
        assertEquals(SassSpecManifest.Action.RUN, runCase.action());
        assertEquals(List.of("input.scss"), runCase.loadedUrls());
        assertEquals(
                new SassSpecManifest.DiagnosticExpectation(
                        DiagnosticSeverity.DEPRECATION,
                        "slash-div",
                        null
                ),
                runCase.diagnostics().get(0)
        );

        var skippedCase = archive.cases().get(1);
        assertEquals(SassSpecManifest.Action.SKIP, skippedCase.action());
        assertEquals("UNSUPPORTED_SYNTAX", skippedCase.category());
    }

    /// Rejects unsupported formats and incomplete skip declarations.
    @Test
    void rejectsInvalidFixtureDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SassSpecManifest.parse("{\"format\": 2, \"archives\": []}", "unsupported.json")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SassSpecManifest.parse(
                        """
                                {
                                  "format": 1,
                                  "archives": [
                                    {
                                      "path": "core.hrx",
                                      "cases": [
                                        {
                                          "directory": "syntax/indented",
                                          "action": "skip",
                                          "category": "UNSUPPORTED_SYNTAX"
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """,
                        "missing-reason.json"
                )
        );
    }
}
