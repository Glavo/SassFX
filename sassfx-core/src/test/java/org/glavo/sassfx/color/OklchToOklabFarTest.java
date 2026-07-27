// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.color;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies OKLCH→OKLab far conversion matches dart-sass 1.101.3 bit-for-bit
/// after the LMS round-trip (including intermediate floating-point noise).
class OklchToOklabFarTest {
    @Test
    void farMatchesDartSass() throws IOException, SassCompilationException {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        "@use \"sass:color\"; a {b: color.to-space(oklch(10% 999999 0deg), oklab)}",
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of())
        );
        assertEquals(
                """
                        a {
                          b: oklab(9.9999999976% 999998.9999999992 0);
                        }""",
                result.output()
        );
    }
}
