package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Module {@code @extend} interactions that require document-original preservation.
class DiamondExtendMergeTest {
    @Test
    void siblingModulesMergingSharedPlaceholder() throws Exception {
        var dir = Files.createTempDirectory("diamond-extend");
        Files.writeString(dir.resolve("_other.scss"), "%in-other.a {x: y}\n");
        Files.writeString(dir.resolve("_left.scss"), "@use \"other\";\n.a {@extend %in-other}\n");
        Files.writeString(dir.resolve("_right.scss"), "@use \"other\";\n.b {@extend %in-other}\n");
        Files.writeString(dir.resolve("input.scss"), "@use \"left\";\n@use \"right\";\n");
        var result = new SassCompiler().compile(
                SassSource.fromFile(dir.resolve("input.scss")),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(dir))
        );
        assertEquals(".a {\n  x: y;\n}", result.output().strip());
    }
}
