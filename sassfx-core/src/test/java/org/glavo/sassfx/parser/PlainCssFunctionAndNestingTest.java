// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.parser;

import org.glavo.sassfx.*;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch47 CSS custom functions, keyframe nesting, and custom-property
/// nesting diagnostics.
@NotNullByDefault
final class PlainCssFunctionAndNestingTest {
    @Test
    void plainCssFunctionKeepsRawResultAndInterpolatedEvaluatesScript() throws Exception {
        assertEquals(
                "@function --a() {\n  result: 1 + 1;\n}".strip(),
                compile("@function --a() { result: 1 + 1; }", Syntax.SCSS).strip()
        );
        assertEquals(
                "@function --a() {\n  result: 2;\n}".strip(),
                compile("@#{function} --a() { result: 1 + 1; }", Syntax.SCSS).strip()
        );
    }

    @Test
    void rejectsNestingUnderCssFunctionResult() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                @function --a()
                                  result:
                                    b: c
                                """,
                        Syntax.SASS
                )
        );
        assertTrue(
                failure.getMessage().contains("Nothing may be indented beneath a @function result."),
                failure.getMessage()
        );
    }

    @Test
    void rejectsNestingUnderCustomProperty() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        """
                                .no-nesting
                                  --foo: bar
                                    baz: qux
                                """,
                        Syntax.SASS
                )
        );
        assertTrue(
                failure.getMessage().contains("Nothing may be indented beneath a custom property."),
                failure.getMessage()
        );
    }

    @Test
    void rejectsStyleRulesInsideKeyframeBlocks() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("@keyframes a { to { to { c: d } } }", Syntax.SCSS)
        );
        assertTrue(
                failure.getMessage().contains("Style rules may not be used within keyframe blocks."),
                failure.getMessage()
        );
    }

    @Test
    void rejectsSpecialCharsInInterpolatedFunctionResult() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("@#{function} --a() { result: {}#&%^*; }", Syntax.SCSS)
        );
        assertTrue(failure.getMessage().contains("expected \"{\""), failure.getMessage());
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
