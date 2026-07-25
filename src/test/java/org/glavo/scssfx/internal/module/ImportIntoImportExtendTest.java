package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportIntoImportExtendTest {
    @Test
    void importedLegacyImportExtendAppliesToBoth() throws Exception {
        var dir = Files.createTempDirectory("import-into-import");
        Files.writeString(dir.resolve("_shared.scss"), "shared {x: y}\n");
        Files.writeString(dir.resolve("_used.scss"), "@use \"shared\";\nin-used {@extend shared}\n");
        Files.writeString(dir.resolve("_imported.scss"), "@import \"shared\";\nin-imported {@extend shared}\n");
        Files.writeString(dir.resolve("input.scss"), "@use \"used\";\n@import \"imported\";\n");
        var result = new SassCompiler().compile(
                SassSource.fromFile(dir.resolve("input.scss")),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(dir))
        );
        assertEquals(
                """
                        shared, in-used, in-imported {
                          x: y;
                        }

                        shared, in-imported {
                          x: y;
                        }""".strip(),
                result.output().strip()
        );
    }
}
