// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies expression AST immutability, range invariants, and source rendering.
@NotNullByDefault
final class SassExpressionModelTest {
    /// Verifies quoted strings escape text without rewriting embedded expressions.
    @Test
    void serializesInterpolatedStringsByPart() {
        var source = new SourceFile("a#{\"x\"}", null);
        var embedded = new StringExpression(
                Interpolation.plain("x", source.span(3, 6)),
                true
        );
        var expression = new StringExpression(
                new Interpolation(
                        List.of(
                                new TextInterpolationPart("a"),
                                new ExpressionInterpolationPart(embedded, source.span(1, 7))
                        ),
                        source.span(0, 7)
                ),
                true
        );

        assertEquals("\"a#{\"x\"}\"", expression.toString());
    }

    /// Verifies control characters use CSS hexadecimal escapes in quoted strings.
    @Test
    void serializesQuotedStringControlCharacters() {
        var text = "a\nb\rc\fd";
        var expression = StringExpression.plain(text, completeSpan(text));
        expression = new StringExpression(expression.text(), true);

        assertEquals("\"a\\a b\\d c\\c d\"", expression.toString());
    }

    /// Verifies slash lists retain their explicit separator and immutable contents.
    @Test
    void representsSlashSeparatedLists() {
        var source = new SourceFile("a/b", null);
        var contents = new ArrayList<SassExpression>();
        contents.add(StringExpression.plain("a", source.span(0, 1)));
        contents.add(StringExpression.plain("b", source.span(2, 3)));
        var expression = new ListExpression(
                contents,
                ListSeparator.SLASH,
                false,
                source.span(0, 3)
        );
        contents.clear();

        assertEquals(2, expression.contents().size());
        assertEquals("a / b", expression.toString());
        assertThrows(UnsupportedOperationException.class, () -> expression.contents().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ListExpression(
                        expression.contents(),
                        ListSeparator.UNDECIDED,
                        false,
                        expression.span()
                )
        );
    }

    /// Verifies binary operations reject source ranges that do not describe their tokens.
    @Test
    void validatesBinaryOperationRanges() {
        var source = new SourceFile("1 + 2", null);
        var left = new NumberExpression(1, null, source.span(0, 1));
        var right = new NumberExpression(2, null, source.span(4, 5));
        var valid = new BinaryOperationExpression(
                BinaryOperator.PLUS,
                left,
                right,
                false,
                source.span(2, 3),
                source.span(0, 5)
        );

        assertEquals("1 + 2", valid.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BinaryOperationExpression(
                        BinaryOperator.PLUS,
                        left,
                        right,
                        false,
                        source.span(1, 2),
                        source.span(0, 5)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new BinaryOperationExpression(
                        BinaryOperator.TIMES,
                        left,
                        right,
                        true,
                        source.span(2, 3),
                        source.span(0, 5)
                )
        );
    }

    /// Creates a source span covering the supplied text.
    ///
    /// @param text the complete source text
    /// @return the complete source range
    private static org.glavo.scssfx.SourceSpan completeSpan(String text) {
        return new SourceFile(text, null).span(0, text.length());
    }
}
