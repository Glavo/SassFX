// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.parser;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies space-separated lists keep a gap before leading-hyphen values.
///
/// Matches dart-sass / sass-spec {@code zero-compression} and {@code issue_1722}:
/// {@code 0 -#{...}} is a space list whose second element is an interpolated
/// identifier beginning with {@code -}, not a binary subtraction.
class NegativeSpaceListTest {
    @Test
    void keepsSpaceBeforeInterpolatedHyphenIdentifier()
            throws IOException, SassCompilationException {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @use "sass:map";
                                $score: (item-height: 1.12em);
                                $orig: 0.12em;
                                .test {
                                  a: 0 -#{map.get($score, item-height)};
                                  b: 0 -#{$orig};
                                  c: 0 -0.12em;
                                  d: foo -#{"bar"};
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of())
        );
        var css = result.output();
        assertTrue(css.contains("a: 0 -1.12em"), css);
        assertTrue(css.contains("b: 0 -0.12em"), css);
        assertTrue(css.contains("c: 0 -0.12em"), css);
        assertTrue(css.contains("d: foo -bar"), css);
    }
}
