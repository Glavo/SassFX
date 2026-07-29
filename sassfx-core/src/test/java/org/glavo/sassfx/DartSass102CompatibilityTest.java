// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies the user-visible language changes through Dart Sass 1.102.0.
@NotNullByDefault
final class DartSass102CompatibilityTest {
    /// Verifies expanded legacy-color, CSS `if()`, and Rec.2020 output.
    @Test
    void emitsExpandedDartSass102Semantics() throws Exception {
        var result = compile(OutputStyle.EXPANDED);

        assertEquals(
                """
                        a {
                          legacy-comma: rgb(0%, 100%, 50%);
                          legacy-space: rgb(0%, 100%, 50%);
                          translucent: rgba(0%, 100%, 50%, 0.4);
                          saturated: rgba(100%, 49.4117647059%, 100%, 0.5);
                          integral: rgb(0, 255, 127);
                          if-value: if(css(): calc(1px * 1px); else: 0);
                          converted: color(srgb 0.5439373396 0.1170946921 0.7697591314);
                        }""",
                result.output()
        );
    }

    /// Verifies compressed color spellings and number compaction.
    @Test
    void emitsCompressedDartSass102Semantics() throws Exception {
        var result = compile(OutputStyle.COMPRESSED);

        assertEquals(
                "a{legacy-comma:rgb(0%,100%,50%);"
                        + "legacy-space:rgb(0%,100%,50%);"
                        + "translucent:rgba(0%,100%,50%,.4);"
                        + "saturated:rgba(100%,49.4117647059%,100%,.5);"
                        + "integral:#00ff7f;"
                        + "if-value:if(css(): calc(1px * 1px); else: 0);"
                        + "converted:color(srgb .5439373396 .1170946921 .7697591314)}",
                result.output()
        );
    }

    /// Compiles the shared Dart Sass 1.102.0 compatibility fixture.
    ///
    /// @param style the requested CSS output style
    /// @return the compilation result
    private static CompileResult<String> compile(OutputStyle style) throws Exception {
        var source = """
                @use 'sass:color';
                a {
                  legacy-comma: rgb(0, 255, 127.5);
                  legacy-space: rgb(0 255 127.5);
                  translucent: rgba(0, 255, 127.5, 0.4);
                  saturated: saturate(rgba(plum, 0.5), 100%);
                  integral: rgb(0, 255, 127);
                  if-value: if(css(): 1px * 1px; else: 0);
                  converted: color.to-space(color(rec2020 0.5 0.25 0.75), srgb);
                }
                """;
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                new CssTarget(style, false)
        );
    }
}
