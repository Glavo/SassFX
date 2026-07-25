package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Sibling modules must not cross-extend products injected into a shared rule.
class ScopeDiamondExtendTest {
    @Test
    void siblingsDoNotCrossExtendSharedRule() throws Exception {
        var dir = Files.createTempDirectory("scope-diamond");
        Files.writeString(dir.resolve("_shared.scss"), "in-shared {x: y}\n");
        Files.writeString(dir.resolve("_left.scss"),
                "@use \"shared\";\nleft-extendee {@extend in-shared}\nleft-extender {@extend right-extendee !optional}\n");
        Files.writeString(dir.resolve("_right.scss"),
                "@use \"shared\";\nright-extendee {@extend in-shared}\nright-extender {@extend left-extendee !optional}\n");
        Files.writeString(dir.resolve("input.scss"), "@use \"left\";\n@use \"right\";\n");
        var result = new SassCompiler().compile(
                SassSource.fromFile(dir.resolve("input.scss")),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(dir))
        );
        assertEquals(
                "in-shared, right-extendee, left-extendee {\n  x: y;\n}",
                result.output().strip()
        );
    }
}
