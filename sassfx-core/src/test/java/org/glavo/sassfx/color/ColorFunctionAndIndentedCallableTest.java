// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.color;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Language-gap probes for the batch57 wave.
@NotNullByDefault
final class ColorFunctionAndIndentedCallableTest {

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }

    @Test
    void colorIsInGamutTooFewArgsReportsMissingArgument() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                @use "sass:color";
                                a {b: color.is-in-gamut()}
                                """,
                        Syntax.SCSS
                )
        );
        assertEquals(
                "Missing argument $color.",
                failure.primaryDiagnostic().message()
        );
    }

    @Test
    void labSlashAlphaKeepsNestedCalcSlash() throws Exception {
        var css = compile(
                """
                        @use "sass:meta";
                        a { b: meta.inspect(lab(1% 2 3 / calc(var(--a) / 2))); }
                        """,
                Syntax.SCSS
        );
        assertTrue(
                css.contains("lab(1% 2 3/calc(var(--a) / 2))"),
                css
        );
    }

    @Test
    void supportsTrailingIdentAfterInterpolationIsRejected() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("@supports #{a}b {@c}", Syntax.SCSS)
        );
        assertEquals(
                "Expected @supports condition.",
                failure.primaryDiagnostic().message()
        );
    }

    @Test
    void indentedErrorJoinsSameIndentExpression() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("@error\na\n", Syntax.SASS)
        );
        assertEquals("a", failure.primaryDiagnostic().message());
    }

    @Test
    void indentedFunctionParameterListContinues() throws Exception {
        assertEquals(
                "",
                compile(
                        """
                                @function a
                                  ()
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void indentedFunctionNameContinuesAfterKeyword() throws Exception {
        assertEquals(
                "",
                compile(
                        """
                                @function
                                  a()
                                """,
                        Syntax.SASS
                ).strip()
        );
    }

    @Test
    void indentedMixinNameContinuesAfterKeyword() throws Exception {
        assertEquals(
                "",
                compile(
                        """
                                @mixin
                                  a
                                """,
                        Syntax.SASS
                ).strip()
        );
    }
}

