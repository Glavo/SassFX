// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies SCSS roots, interpolated style rules, and statement-level comments.
@NotNullByDefault
final class ScssParserTest {
    /// Verifies empty input, a leading BOM, whitespace, and empty statements.
    @Test
    void parsesEmptyStylesheetsAndTrivia() {
        var empty = parse("");
        assertTrue(empty.children().isEmpty());
        assertEquals("", empty.span().text());

        var text = "\uFEFF \t\r\n\f;;; ";
        var trivia = parse(text);
        assertTrue(trivia.children().isEmpty());
        assertEquals(text, trivia.span().text());
        assertFalse(trivia.plainCss());
    }

    /// Verifies mixed loud and multi-line silent comment statements.
    @Test
    void parsesStatementComments() {
        var text = ";/*a\rb\f c\r\nd*/ // one\n  // two\n;;";
        var stylesheet = parse(text);

        assertEquals(2, stylesheet.children().size());
        var loud = assertInstanceOf(LoudComment.class, stylesheet.children().get(0));
        assertEquals("/*a\nb\n c\nd*/", loud.text().asPlain());
        assertEquals("/*a\rb\f c\r\nd*/", loud.span().text());

        var silent = assertInstanceOf(SilentComment.class, stylesheet.children().get(1));
        assertEquals("// one\n  // two\n", silent.text());
        assertEquals(silent.text(), silent.span().text());
    }

    /// Verifies empty loud comments and the first closing delimiter.
    @Test
    void parsesLoudCommentBoundaries() {
        var stylesheet = parse("/**//* */");

        assertEquals("/**/", stylesheet.children().get(0).toString());
        assertEquals("/* */", stylesheet.children().get(1).toString());
    }

    /// Verifies plain selectors and comment-only style rule blocks.
    @Test
    void parsesPlainStyleRules() {
        var text = "a:not([title=\"x\"]), .b {;/* child */ // silent\n}";
        var stylesheet = parse(text);

        var rule = assertInstanceOf(StyleRule.class, stylesheet.children().get(0));
        assertEquals("a:not([title=\"x\"]), .b ", rule.selector().asPlain());
        assertEquals(text, rule.span().text());
        assertEquals(2, rule.children().size());
        assertInstanceOf(LoudComment.class, rule.children().get(0));
        assertInstanceOf(SilentComment.class, rule.children().get(1));
        assertThrows(UnsupportedOperationException.class, () -> rule.children().clear());
    }

    /// Verifies whitespace between rules is consumed but excluded from rule spans.
    @Test
    void parsesAdjacentStyleRules() {
        var stylesheet = parse("a {} \r\n b {}");

        assertEquals(2, stylesheet.children().size());
        assertEquals("a {}", stylesheet.children().get(0).span().text());
        assertEquals("b {}", stylesheet.children().get(1).span().text());
    }

    /// Verifies raw selector strings preserve CRLF line continuations.
    @Test
    void parsesRawSelectorStringTokens() {
        var text = "[title=\"a\\\r\nb\"] {}";
        var rule = assertInstanceOf(StyleRule.class, parse(text).children().get(0));

        assertEquals("[title=\"a\\\r\nb\"] ", rule.selector().asPlain());
    }

    /// Verifies raw URL normalization and fallback to ordinary selector tokens.
    @Test
    void normalizesRawSelectorUrls() {
        var normalized = assertInstanceOf(
                StyleRule.class,
                parse("a:url( foo ) {}").children().get(0)
        );
        assertEquals("a:url(foo) ", normalized.selector().asPlain());

        var escaped = assertInstanceOf(
                StyleRule.class,
                parse("a:u\\72l( foo ) {}").children().get(0)
        );
        assertEquals("a:url(foo) ", escaped.selector().asPlain());

        var fallback = assertInstanceOf(
                StyleRule.class,
                parse("a:url(\"foo\") {}").children().get(0)
        );
        assertEquals("a:url(\"foo\") ", fallback.selector().asPlain());
    }

    /// Verifies selectors, selector strings, raw URLs, and loud comments retain interpolation.
    @Test
    void parsesStylesheetInterpolations() {
        var selector = assertInstanceOf(
                StyleRule.class,
                parse("a#{1 + 2}[x=\"#{value}\"]:url(#{path}) {}").children().get(0)
        ).selector();

        assertFalse(selector.isPlain());
        assertEquals(7, selector.parts().size());
        assertEquals(3, selector.parts().stream()
                .filter(ExpressionInterpolationPart.class::isInstance)
                .count());
        var first = assertInstanceOf(ExpressionInterpolationPart.class, selector.parts().get(1));
        assertEquals("#{1 + 2}", first.interpolationSpan().text());

        var comment = assertInstanceOf(
                LoudComment.class,
                parse("/* value: #{1 + 2} */").children().get(0)
        );
        assertFalse(comment.text().isPlain());
        var commentExpression = assertInstanceOf(
                ExpressionInterpolationPart.class,
                comment.text().parts().get(1)
        );
        assertEquals("#{1 + 2}", commentExpression.interpolationSpan().text());
    }

    /// Verifies malformed and unavailable statement productions fail precisely.
    @Test
    void rejectsUnsupportedOrMalformedStatements() {
        var unterminated = assertThrows(ParseException.class, () -> parse("/* comment"));
        assertEquals("", unterminated.span().text());

        var interpolation = assertThrows(ParseException.class, () -> parse("/* #{} */"));
        assertEquals("}", interpolation.span().text());

        var colorInterpolation = assertThrows(
                ParseException.class,
                () -> parse("a#{red} {}")
        );
        assertEquals("red", colorInterpolation.span().text());

        var atRule = assertThrows(ParseException.class, () -> parse("@media {}"));
        assertEquals("@", atRule.span().text());

        var variable = assertThrows(ParseException.class, () -> parse("$name {}"));
        assertEquals("$", variable.span().text());

        var unmatchedBracket = assertThrows(ParseException.class, () -> parse("a) {}"));
        assertEquals(")", unmatchedBracket.span().text());

        var statement = assertThrows(
                ParseException.class,
                () -> parse("a { color: red; }")
        );
        assertEquals("c", statement.span().text());

        var block = assertThrows(ParseException.class, () -> parse("a {"));
        assertEquals("", block.span().text());
    }

    /// Parses a complete SCSS source string.
    ///
    /// @param text the SCSS source
    /// @return the stylesheet syntax tree
    private static Stylesheet parse(String text) {
        return new ScssParser(new SourceFile(text, null)).parse();
    }
}
