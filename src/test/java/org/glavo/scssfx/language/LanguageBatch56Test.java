// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.language;

import org.glavo.scssfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Language-gap probes for the batch56 wave.
@NotNullByDefault
final class LanguageBatch56Test {

    /// Compiles source text with the given syntax.
    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .strip();
    }

    @Test
    void globalMapRemoveRemovesKeys() throws Exception {
        assertEquals(
                "a {\n  b: ();\n}",
                compile(
                        """
                                @use "sass:meta";
                                a {b: meta.inspect(map-remove((c: d), c))}
                                """,
                        Syntax.SCSS
                )
        );
    }

    @Test
    void mathDivNonNumericYieldsSlashString() throws Exception {
        assertEquals(
                "a {\n  value: b/3;\n  type: string;\n}",
                compile(
                        """
                                @use "sass:meta";
                                @use "sass:math";
                                a {
                                  $result: math.div(b, 3);
                                  value: $result;
                                  type: meta.type-of($result);
                                }
                                """,
                        Syntax.SCSS
                )
        );
    }

    @Test
    void bareCharsetWithIndentReportsExpectedString() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("@charset\n  \"a\"\n", Syntax.SASS)
        );
        assertEquals("Expected string.", failure.primaryDiagnostic().message());
    }

    @Test
    void supportsAnythingColonUsesLowercaseExpected() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("@supports (a !:$) {@b}", Syntax.SCSS)
        );
        assertEquals("expected \":\".", failure.primaryDiagnostic().message());
    }

    @Test
    void doubleHyphenCallSiteIsPlainCss() throws Exception {
        assertEquals(
                "b {\n  c: --a();\n}",
                compile(
                        """
                                @function __a() {@return 1}
                                b {c: --a()}
                                """,
                        Syntax.SCSS
                )
        );
        assertEquals(
                "b {\n  c: 1;\n}",
                compile(
                        """
                                @function __a() {@return 1}
                                b {c: __a()}
                                """,
                        Syntax.SCSS
                )
        );
    }

    @Test
    void plainCssMinSimplifiesComparableNumbers() throws Exception {
        // dart-sass simplifies plain-CSS min() when every argument is a number.
        assertTrue(
                compile(
                        ".layered { width: min(10px, 20px); }",
                        Syntax.CSS
                ).contains("width: 10px")
        );
    }
}
