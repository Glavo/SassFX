package org.glavo.scssfx.color;

import org.glavo.scssfx.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OklchToOklabFarTest {
    @Test
    void far() throws Exception {
        var r = new SassCompiler().compile(
            SassSource.fromString(
                "@use \"sass:color\"; a {b: color.to-space(oklch(10% 999999 0deg), oklab)}",
                Syntax.SCSS),
            CssTarget.DEFAULT,
            new CompileOptions(false, List.of()));
        System.out.println(r.output());
        // Must go through LMS round-trip (not exact 10% / 999999).
        assertFalse(r.output().contains("oklab(10% 999999 0)"), r.output());
        assertTrue(r.output().contains("oklab(9.99999999"), r.output());
    }
}
