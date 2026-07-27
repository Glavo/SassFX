package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Import-path {@code @extend} rewrites both the import CSS copy and gen-0 originals.
class ImportIntoUseExtendTest {
    @Test
    void importedExtendAppliesToOriginalAndCopy() throws Exception {
        var dir = Files.createTempDirectory("import-into-use");
        Files.writeString(dir.resolve("_shared.scss"), "shared {x: y}\n");
        Files.writeString(dir.resolve("_used.scss"), "@use \"shared\";\nin-used {@extend shared}\n");
        Files.writeString(dir.resolve("_imported.scss"), "@use \"shared\";\nin-imported {@extend shared}\n");
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
