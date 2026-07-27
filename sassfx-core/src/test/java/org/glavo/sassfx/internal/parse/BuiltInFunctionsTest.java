// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.glavo.sassfx.internal.ast.LegacyIfExpression;
import org.glavo.sassfx.internal.evaluate.EvaluationException;
import org.glavo.sassfx.internal.evaluate.SassEvaluator;
import org.glavo.sassfx.internal.source.SourceFile;
import org.glavo.sassfx.internal.value.ListSeparator;
import org.glavo.sassfx.internal.value.SassBoolean;
import org.glavo.sassfx.internal.value.SassColor;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies built-in functions, plain-CSS fallback, and short-circuit `if()`.
@NotNullByDefault
final class BuiltInFunctionsTest {
    /// Verifies core list, string, math, meta, and color builtins.
    @Test
    void evaluatesCoreBuiltIns() {
        assertEquals(SassNumber.of(3, null), evaluate("length(a b c)"));
        assertEquals(new SassString("b", false), evaluate("nth(a b c, 2)"));
        assertEquals(
                new SassList(
                        List.of(
                                new SassString("a", false),
                                new SassString("b", false),
                                new SassString("c", false)
                        ),
                        ListSeparator.SPACE,
                        false
                ),
                evaluate("join(a b, c)")
        );
        assertEquals(
                new SassList(
                        List.of(new SassString("a", false), new SassString("b", false)),
                        ListSeparator.COMMA,
                        false
                ),
                evaluate("append(a, b, comma)")
        );
        assertEquals(new SassString("foo", true), evaluate("quote(foo)"));
        assertEquals(new SassString("foo", false), evaluate("unquote(\"foo\")"));
        assertEquals(new SassString("number", false), evaluate("type-of(1px)"));
        assertEquals(new SassString("color", false), evaluate("type-of(red)"));
        assertEquals(new SassString("px", true), evaluate("unit(1px)"));
        assertSame(SassBoolean.TRUE, evaluate("comparable(1px, 2px)"));
        assertEquals(SassNumber.of(20, "%"), evaluate("percentage(0.2)"));
        assertEquals(SassNumber.of(1, "px"), evaluate("abs(-1px)"));
        assertEquals(SassNumber.of(1, null), evaluate("min(1, 2, 3)"));
        assertEquals(SassNumber.of(3, null), evaluate("max(1, 2, 3)"));
        assertEquals(SassNumber.of(2, null), evaluate("round(1.5)"));
        assertEquals(SassNumber.of(-2, null), evaluate("round(-1.5)"));
        assertEquals(SassNumber.of(0, null), evaluate("round(-0.1)"));
        assertEquals(SassNumber.of(2, null), evaluate("ceil(1.1)"));
        assertEquals(SassNumber.of(1, null), evaluate("floor(1.9)"));
        assertEquals(SassColor.rgb(10, 20, 30, 1, null), evaluate("rgb(10, 20, 30)"));
        assertEquals(SassColor.rgb(10, 20, 30, 0.5, null), evaluate("rgba(10, 20, 30, 0.5)"));
    }

    /// Verifies that `if()` short-circuits the unselected branch.
    @Test
    void shortCircuitsLegacyIf() {
        assertInstanceOf(
                LegacyIfExpression.class,
                new SassExpressionParser(new SourceFile("if(true, 1, 2)", null))
                        .parseExpression()
        );
        assertEquals(SassNumber.of(1, null), evaluate("if(true, 1, 1/0)"));
        assertEquals(SassNumber.of(2, null), evaluate("if(false, 1/0, 2)"));
    }

    /// Verifies unknown functions serialize as plain CSS.
    @Test
    void serializesUnknownFunctionsAsPlainCss() {
        assertEquals(new SassString("blur(2px)", false), evaluate("blur(2px)"));
    }

    /// Verifies namespaced function lookups remain structured failures.
    @Test
    void rejectsUnknownModules() {
        var failure = assertThrows(
                EvaluationException.class,
                () -> evaluate("math.div(1, 2)")
        );
        assertEquals(
                "There is no module with the namespace \"math\".",
                failure.getMessage()
        );
    }

    /// Compiles builtins through the public compiler API.
    @Test
    void compilesBuiltInsToCss() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                $items: join(a, b);
                                a {
                                  content: quote(hello);
                                  color: rgb(1, 2, 3);
                                  width: if(true, 1px, 2px);
                                  order: nth($items, 2);
                                  filter: blur(2px);
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        a {
                          content: "hello";
                          color: rgb(1, 2, 3);
                          width: 1px;
                          order: b;
                          filter: blur(2px);
                        }""",
                result.output()
        );
    }

    /// Evaluates one expression.
    ///
    /// @param source the expression source
    /// @return the evaluated value
    private static SassValue evaluate(String source) {
        return new SassEvaluator().evaluate(
                new SassExpressionParser(new SourceFile(source, null)).parseExpression()
        );
    }
}
