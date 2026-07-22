// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.BinaryOperator;
import org.glavo.scssfx.internal.ast.BooleanExpression;
import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.ListSeparator;
import org.glavo.scssfx.internal.ast.NullExpression;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.TextInterpolationPart;
import org.glavo.scssfx.internal.ast.UnaryOperationExpression;
import org.glavo.scssfx.internal.ast.UnaryOperator;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the currently supported SassScript expression grammar and source ranges.
@NotNullByDefault
final class SassExpressionParserTest {
    /// Verifies number syntax, literal units, and signed numeric tokens.
    @Test
    void parsesNumbersAndLiteralUnits() {
        var decimal = assertInstanceOf(NumberExpression.class, parse("+1.25e-2px"));
        assertEquals(0.0125, decimal.value());
        assertEquals("px", decimal.unit());
        assertSpan(decimal.span(), 0, 10, "+1.25e-2px");

        var percentage = assertInstanceOf(NumberExpression.class, parse(".5%"));
        assertEquals(0.5, percentage.value());
        assertEquals("%", percentage.unit());

        var negative = assertInstanceOf(NumberExpression.class, parse("-.5"));
        assertEquals(-0.5, negative.value());
        assertNull(negative.unit());
    }

    /// Verifies boolean, null, quoted-string, and unquoted-string literals.
    @Test
    void parsesPrimitiveLiterals() {
        assertTrue(assertInstanceOf(BooleanExpression.class, parse("true")).value());
        assertFalse(assertInstanceOf(BooleanExpression.class, parse("false")).value());
        assertInstanceOf(NullExpression.class, parse("null"));

        var identifier = assertInstanceOf(StringExpression.class, parse("True"));
        assertFalse(identifier.hasQuotes());
        assertEquals("True", identifier.text().asPlain());

        var string = assertInstanceOf(StringExpression.class, parse("\"a\\1f600 b\""));
        assertTrue(string.hasQuotes());
        assertEquals("a\uD83D\uDE00b", string.text().asPlain());
        assertSpan(string.span(), 0, 11, "\"a\\1f600 b\"");
    }

    /// Verifies variable normalization and source ranges.
    @Test
    void parsesVariables() {
        var variable = assertInstanceOf(VariableExpression.class, parse("$foo_bar"));

        assertNull(variable.namespace());
        assertEquals("foo-bar", variable.name());
        assertSpan(variable.span(), 0, 8, "$foo_bar");

        var escaped = assertInstanceOf(VariableExpression.class, parse("$f\\6f o"));
        assertEquals("foo", escaped.name());
        assertSpan(escaped.span(), 0, 7, "$f\\6f o");
    }

    /// Verifies unary forms and binary operator precedence and associativity.
    @Test
    void parsesUnaryAndBinaryOperations() {
        var unary = assertInstanceOf(UnaryOperationExpression.class, parse("not $x"));
        assertEquals(UnaryOperator.NOT, unary.operator());
        assertInstanceOf(VariableExpression.class, unary.operand());
        assertSpan(unary.span(), 0, 6, "not $x");

        var root = assertInstanceOf(BinaryOperationExpression.class, parse("1 + 2 * 3"));
        assertEquals(BinaryOperator.PLUS, root.operator());
        assertSpan(root.span(), 0, 9, "1 + 2 * 3");
        assertSpan(root.operatorSpan(), 2, 3, "+");
        assertInstanceOf(NumberExpression.class, root.left());

        var multiplication = assertInstanceOf(BinaryOperationExpression.class, root.right());
        assertEquals(BinaryOperator.TIMES, multiplication.operator());
        assertSpan(multiplication.span(), 4, 9, "2 * 3");
        assertSpan(multiplication.operatorSpan(), 6, 7, "*");

        var subtraction = assertInstanceOf(
                BinaryOperationExpression.class,
                parse("1 - 2 - 3")
        );
        assertEquals(BinaryOperator.MINUS, subtraction.operator());
        assertInstanceOf(BinaryOperationExpression.class, subtraction.left());
        assertInstanceOf(NumberExpression.class, subtraction.right());
    }

    /// Verifies signed-number boundaries, comments, and logical precedence.
    @Test
    void distinguishesSignedNumbersFromBinaryOperators() {
        var signedList = assertInstanceOf(ListExpression.class, parse("1 -2"));
        assertEquals(ListSeparator.SPACE, signedList.separator());
        assertEquals(-2.0, assertInstanceOf(
                NumberExpression.class,
                signedList.contents().get(1)
        ).value());

        var commentSubtraction = assertInstanceOf(
                BinaryOperationExpression.class,
                parse("1/*comment*/-2")
        );
        assertEquals(BinaryOperator.MINUS, commentSubtraction.operator());
        assertEquals("-", commentSubtraction.operatorSpan().text());

        var adjacent = assertInstanceOf(BinaryOperationExpression.class, parse("1+-2"));
        assertEquals(BinaryOperator.PLUS, adjacent.operator());
        assertEquals(-2.0, assertInstanceOf(NumberExpression.class, adjacent.right()).value());

        var logical = assertInstanceOf(
                BinaryOperationExpression.class,
                parse("1 < 2 and 3 == 3 or false")
        );
        assertEquals(BinaryOperator.OR, logical.operator());
        var conjunction = assertInstanceOf(BinaryOperationExpression.class, logical.left());
        assertEquals(BinaryOperator.AND, conjunction.operator());
        assertEquals(BinaryOperator.LESS_THAN, assertInstanceOf(
                BinaryOperationExpression.class,
                conjunction.left()
        ).operator());
        assertEquals(BinaryOperator.EQUALS, assertInstanceOf(
                BinaryOperationExpression.class,
                conjunction.right()
        ).operator());
    }

    /// Verifies slash history is retained only for eligible unparenthesized number trees.
    @Test
    void preservesSlashExpressionHistory() {
        var division = assertInstanceOf(BinaryOperationExpression.class, parse("1/2/3"));
        assertEquals(BinaryOperator.DIVIDED_BY, division.operator());
        assertTrue(division.allowsSlash());
        assertTrue(assertInstanceOf(
                BinaryOperationExpression.class,
                division.left()
        ).allowsSlash());

        var grouped = assertInstanceOf(ParenthesizedExpression.class, parse("(1/2)"));
        assertFalse(assertInstanceOf(
                BinaryOperationExpression.class,
                grouped.expression()
        ).allowsSlash());

        var reparsed = assertInstanceOf(ParenthesizedExpression.class, parse("(1/2 1)"));
        var list = assertInstanceOf(ListExpression.class, reparsed.expression());
        assertTrue(assertInstanceOf(
                BinaryOperationExpression.class,
                list.contents().get(0)
        ).allowsSlash());
    }

    /// Verifies space, comma, bracketed, and parenthesized list forms.
    @Test
    void parsesListsAndParentheses() {
        var comma = assertInstanceOf(ListExpression.class, parse("a b, c"));
        assertEquals(ListSeparator.COMMA, comma.separator());
        assertFalse(comma.hasBrackets());
        assertEquals(2, comma.contents().size());
        assertSpan(comma.span(), 0, 6, "a b, c");

        var space = assertInstanceOf(ListExpression.class, comma.contents().get(0));
        assertEquals(ListSeparator.SPACE, space.separator());
        assertEquals(2, space.contents().size());
        assertSpan(space.span(), 0, 3, "a b");

        var bracketed = assertInstanceOf(ListExpression.class, parse("[a b]"));
        assertEquals(ListSeparator.SPACE, bracketed.separator());
        assertTrue(bracketed.hasBrackets());
        assertSpan(bracketed.span(), 0, 5, "[a b]");

        var empty = assertInstanceOf(ListExpression.class, parse("[]"));
        assertEquals(ListSeparator.UNDECIDED, empty.separator());
        assertTrue(empty.hasBrackets());
        assertTrue(empty.contents().isEmpty());

        var parenthesized = assertInstanceOf(ParenthesizedExpression.class, parse("(a, b)"));
        assertSpan(parenthesized.span(), 0, 6, "(a, b)");
        var contents = assertInstanceOf(ListExpression.class, parenthesized.expression());
        assertEquals(ListSeparator.COMMA, contents.separator());
        assertEquals(2, contents.contents().size());
        assertSpan(contents.span(), 1, 5, "a, b");
    }

    /// Verifies singleton, empty, and trailing-comma list forms remain distinct.
    @Test
    void preservesListDelimiters() {
        var trailing = assertInstanceOf(ListExpression.class, parse("a,"));
        assertEquals(ListSeparator.COMMA, trailing.separator());
        assertEquals(1, trailing.contents().size());
        assertSpan(trailing.span(), 0, 2, "a,");

        var bracketedTrailing = assertInstanceOf(ListExpression.class, parse("[a,]"));
        assertTrue(bracketedTrailing.hasBrackets());
        assertEquals(ListSeparator.COMMA, bracketedTrailing.separator());
        assertEquals(1, bracketedTrailing.contents().size());

        var bracketedSingleton = assertInstanceOf(ListExpression.class, parse("[a]"));
        assertEquals(ListSeparator.UNDECIDED, bracketedSingleton.separator());
        assertEquals(1, bracketedSingleton.contents().size());

        var emptyParentheses = assertInstanceOf(ListExpression.class, parse("()"));
        assertFalse(emptyParentheses.hasBrackets());
        assertEquals(ListSeparator.UNDECIDED, emptyParentheses.separator());
        assertTrue(emptyParentheses.contents().isEmpty());
    }

    /// Verifies unit boundaries, escaped units, and qualified variable references.
    @Test
    void parsesUnitBoundariesAndQualifiedVariables() {
        var subtraction = assertInstanceOf(BinaryOperationExpression.class, parse("1px-2px"));
        assertEquals("px", assertInstanceOf(NumberExpression.class, subtraction.left()).unit());
        assertEquals("px", assertInstanceOf(NumberExpression.class, subtraction.right()).unit());

        var escapedUnit = assertInstanceOf(NumberExpression.class, parse("12\\70 x"));
        assertEquals("px", escapedUnit.unit());

        var qualified = assertInstanceOf(VariableExpression.class, parse("theme.$spacing"));
        assertEquals("theme", qualified.namespace());
        assertEquals("spacing", qualified.name());
        assertSpan(qualified.span(), 0, 14, "theme.$spacing");

        assertFailure("theme.$_private", "theme.$_private");
    }

    /// Verifies cross-line operator ranges retain UTF-16 line and column locations.
    @Test
    void tracksCrossLineExpressionRanges() {
        var expression = assertInstanceOf(BinaryOperationExpression.class, parse("$a\r\n+ $b"));

        assertSpan(expression.span(), 0, 8, "$a\r\n+ $b");
        assertEquals(0, expression.span().start().line());
        assertEquals(0, expression.span().start().column());
        assertEquals(1, expression.span().end().line());
        assertEquals(4, expression.span().end().column());
        assertSpan(expression.operatorSpan(), 4, 5, "+");
    }

    /// Verifies interpolation parts retain their surrounding and inner ranges.
    @Test
    void parsesInterpolatedStringsWithUtf16Ranges() {
        var expression = assertInstanceOf(StringExpression.class, parse("\"\uD83D\uDE00#{ $x }\""));
        assertTrue(expression.hasQuotes());
        assertSpan(expression.span(), 0, 11, "\"\uD83D\uDE00#{ $x }\"");
        assertEquals(2, expression.text().parts().size());

        var text = assertInstanceOf(TextInterpolationPart.class, expression.text().parts().get(0));
        assertEquals("\uD83D\uDE00", text.text());

        var interpolation = assertInstanceOf(
                ExpressionInterpolationPart.class,
                expression.text().parts().get(1)
        );
        assertSpan(interpolation.interpolationSpan(), 3, 10, "#{ $x }");
        var variable = assertInstanceOf(VariableExpression.class, interpolation.expression());
        assertEquals("x", variable.name());
        assertSpan(variable.span(), 6, 8, "$x");
    }

    /// Verifies interpolated unquoted identifiers may contain complete expressions.
    @Test
    void parsesInterpolatedUnquotedStrings() {
        var expression = assertInstanceOf(StringExpression.class, parse("f#{1 + 2}o"));

        assertFalse(expression.hasQuotes());
        assertSpan(expression.span(), 0, 10, "f#{1 + 2}o");
        assertEquals(3, expression.text().parts().size());
        assertEquals("f", assertInstanceOf(
                TextInterpolationPart.class,
                expression.text().parts().get(0)
        ).text());

        var interpolation = assertInstanceOf(
                ExpressionInterpolationPart.class,
                expression.text().parts().get(1)
        );
        assertSpan(interpolation.interpolationSpan(), 1, 9, "#{1 + 2}");
        var binary = assertInstanceOf(BinaryOperationExpression.class, interpolation.expression());
        assertEquals(BinaryOperator.PLUS, binary.operator());
        assertSpan(binary.span(), 3, 8, "1 + 2");
        assertEquals("o", assertInstanceOf(
                TextInterpolationPart.class,
                expression.text().parts().get(2)
        ).text());
    }

    /// Verifies invalid expressions report source-local parse failures.
    @Test
    void rejectsMalformedExpressions() {
        assertFailure("", "");
        assertFailure("1 +", "+");
        assertFailure(".", "");
        assertFailure("1 .", "");
        assertFailure("1e+", "");
        assertFailure("a,,b", ",");
        assertFailure("\"#{ }\"", "}");
        assertFailure("[a", "");
        assertFailure("(a", "");
        assertFailure("\"unterminated", "");
        assertFailure("$", "");
    }

    /// Verifies features reserved for later expression-parser milestones are explicit failures.
    @Test
    void rejectsDeferredExpressionForms() {
        assertFailure("foo()", "(");
        assertFailure("(a: b)", ":");
        assertFailure("#abc", "#");
        assertFailure("red", "red");
        assertFailure("ReD", "ReD");
        assertFailure("transparent", "transparent");
    }

    /// Parses one complete SassScript expression.
    ///
    /// @param text the expression source
    /// @return the parsed expression
    private static SassExpression parse(String text) {
        return new SassExpressionParser(new SourceFile(text, null)).parseExpression();
    }

    /// Asserts that parsing fails at a span containing the expected source text.
    ///
    /// @param text the expression source
    /// @param failureText the expected failing source text
    private static void assertFailure(String text, String failureText) {
        var failure = assertThrows(ParseException.class, () -> parse(text));
        assertEquals(failureText, failure.span().text());
    }

    /// Asserts the offset range and selected source text of a span.
    ///
    /// @param span the span to inspect
    /// @param startOffset the inclusive UTF-16 start offset
    /// @param endOffset the exclusive UTF-16 end offset
    /// @param text the exact selected source text
    private static void assertSpan(SourceSpan span, int startOffset, int endOffset, String text) {
        assertEquals(startOffset, span.start().offset());
        assertEquals(endOffset, span.end().offset());
        assertEquals(text, span.text());
    }
}
