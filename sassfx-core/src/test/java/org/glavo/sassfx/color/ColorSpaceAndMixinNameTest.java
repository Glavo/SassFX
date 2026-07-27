// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.color;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch48 must-fail diagnostics and color white residual conversion.
@NotNullByDefault
final class ColorSpaceAndMixinNameTest {
    @Test
    void rejectsIncludeNamesBeginningWithDoubleHyphen() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("@mixin __a() {b: c} d {@include --a}", Syntax.SCSS)
        );
        assertTrue(
                failure.getMessage().contains(
                        "Sass @mixin names beginning with -- are forbidden"
                ),
                failure.getMessage()
        );
    }

    @Test
    void rejectsEmptyCustomPropertyInSupports() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("@supports (--a:) {@c}", Syntax.SCSS)
        );
        assertTrue(failure.getMessage().contains("Expected token."), failure.getMessage());
    }

    @Test
    void rejectsMultipleStatementsOnOneIndentedLine() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                a
                                  b: c; d: e;
                                """,
                        Syntax.SASS
                )
        );
        assertTrue(
                failure.getMessage().contains(
                        "multiple statements on one line are not supported in the indented syntax."
                ),
                failure.getMessage()
        );
    }

    @Test
    void rejectsDirDotScssDirectoryIndexForUse(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.scss");
        Files.writeString(input, "@use \"dir.scss\";\n");
        Files.createDirectories(directory.resolve("dir.scss"));
        Files.writeString(directory.resolve("dir.scss").resolve("index.scss"), ".foo { a: b }\n");

        var failure = assertThrows(
                Exception.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(input),
                        CssTarget.DEFAULT
                )
        );
        assertTrue(
                failure.getMessage().contains("Can't find stylesheet to import."),
                failure.getMessage()
        );
    }

    @Test
    void oklabWhiteKeepsResidualHueAndSaturationInHsl() throws Exception {
        assertEquals(
                "a {\n  b: hsl(161.8181818182, 266.6666666667%, 100%);\n}".strip(),
                compile(
                        "@use \"sass:color\"; a { b: color.to-space(oklab(100% 0 0), hsl) }",
                        Syntax.SCSS
                ).strip()
        );
    }

    @Test
    void linearAndWideGamutWhitesStayAchromaticInHsl() throws Exception {
        assertEquals(
                "a {\n  b: hsl(0, 0%, 100%);\n}".strip(),
                compile(
                        "@use \"sass:color\"; a { b: color.to-space(color(a98-rgb 1 1 1), hsl) }",
                        Syntax.SCSS
                ).strip()
        );
        assertEquals(
                "a {\n  b: hsl(0, 0%, 100%);\n}".strip(),
                compile(
                        "@use \"sass:color\"; a { b: color.to-space(color(display-p3 1 1 1), hsl) }",
                        Syntax.SCSS
                ).strip()
        );
        assertEquals(
                "a {\n  b: hsl(0, 0%, 100%);\n}".strip(),
                compile(
                        "@use \"sass:color\"; a { b: color.to-space(color(srgb-linear 1 1 1), hsl) }",
                        Syntax.SCSS
                ).strip()
        );
    }

    @Test
    void prophotoWhiteKeepsResidualHueAndSaturationInHsl() throws Exception {
        assertEquals(
                "a {\n  b: hsl(180, 50%, 100%);\n}".strip(),
                compile(
                        "@use \"sass:color\"; a { b: color.to-space(color(prophoto-rgb 1 1 1), hsl) }",
                        Syntax.SCSS
                ).strip()
        );
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
