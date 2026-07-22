// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.BinaryOperator;
import org.glavo.scssfx.internal.ast.ColorExpression;
import org.glavo.scssfx.internal.ast.Declaration;
import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies SCSS declarations, nested properties, style rules, and statement comments.
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

    /// Verifies ordinary declaration names, values, hacks, interpolation, and spans.
    @Test
    void parsesSassScriptDeclarations() {
        var rule = assertInstanceOf(
                StyleRule.class,
                parse("a { width: 1 + 2; display: block; w\\69 dth: 2px; "
                        + "#{prefix}-size: 3px; *zoom: 1; .legacy: 2; "
                        + ":legacy: 3; #legacy: 4; "
                        + "priority: 1px ! /* comment */ IMPORTANT; "
                        + "-#{name}: 5px}").children().get(0)
        );

        assertEquals(10, rule.children().size());
        var width = assertInstanceOf(Declaration.class, rule.children().get(0));
        assertEquals("width", width.name().asPlain());
        assertEquals("width: 1 + 2", width.span().text());
        assertTrue(width.parsedAsSassScript());
        assertNull(width.children());
        var sum = assertInstanceOf(BinaryOperationExpression.class, width.value());
        assertEquals(BinaryOperator.PLUS, sum.operator());

        var display = assertInstanceOf(Declaration.class, rule.children().get(1));
        assertEquals("block", assertInstanceOf(
                StringExpression.class,
                display.value()
        ).text().asPlain());

        var escaped = assertInstanceOf(Declaration.class, rule.children().get(2));
        assertEquals("width", escaped.name().asPlain());
        assertEquals("w\\69 dth", escaped.name().span().text());

        var interpolated = assertInstanceOf(Declaration.class, rule.children().get(3));
        assertFalse(interpolated.name().isPlain());
        assertEquals("#{prefix}-size", interpolated.name().toString());

        assertEquals("*zoom", assertInstanceOf(
                Declaration.class,
                rule.children().get(4)
        ).name().asPlain());
        assertEquals(".legacy", assertInstanceOf(
                Declaration.class,
                rule.children().get(5)
        ).name().asPlain());
        assertEquals(":legacy", assertInstanceOf(
                Declaration.class,
                rule.children().get(6)
        ).name().asPlain());
        var hashHack = assertInstanceOf(Declaration.class, rule.children().get(7));
        assertEquals("#legacy", hashHack.name().asPlain());
        assertEquals("#legacy: 4", hashHack.span().text());

        var priority = assertInstanceOf(Declaration.class, rule.children().get(8));
        assertEquals("1px !important", Objects.requireNonNull(priority.value()).toString());
        assertEquals("priority: 1px ! /* comment */ IMPORTANT", priority.span().text());

        var hyphenInterpolation = assertInstanceOf(
                Declaration.class,
                rule.children().get(9)
        );
        assertEquals("-#{name}", hyphenInterpolation.name().toString());
        assertEquals("-#{name}: 5px", hyphenInterpolation.span().text());
    }

    /// Verifies custom-property values remain raw while interpolation is structured.
    @Test
    void parsesRawCustomPropertyDeclarations() {
        var text = "a {"
                + "--literal: $value;"
                + "--interpolated: #{ $value }--1;"
                + "--balanced: {alpha: [one; two]};"
                + "--empty:;"
                + "--space:   one   two\r\n    three;"
                + "--url: url( #{path} );"
                + "#{--x}: $value;"
                + "--#{suffix}: raw;"
                + "--slashes://not-a-comment\r\nnext;"
                + "--string: \"a\\26 b#{value}\";"
                + "--quoted-url: url(\"foo\");"
                + "}";
        var rule = assertInstanceOf(StyleRule.class, parse(text).children().get(0));

        assertEquals(11, rule.children().size());
        var literal = assertInstanceOf(Declaration.class, rule.children().get(0));
        assertFalse(literal.parsedAsSassScript());
        assertEquals(" $value", assertInstanceOf(
                StringExpression.class,
                literal.value()
        ).text().asPlain());
        assertEquals("--literal: $value", literal.span().text());

        var interpolated = assertInstanceOf(Declaration.class, rule.children().get(1));
        var interpolatedValue = assertInstanceOf(StringExpression.class, interpolated.value());
        assertFalse(interpolatedValue.text().isPlain());
        assertEquals(" #{$value}--1", interpolatedValue.text().toString());
        assertEquals(" #{ $value }--1", interpolatedValue.span().text());

        var balanced = assertInstanceOf(Declaration.class, rule.children().get(2));
        assertEquals(" {alpha: [one; two]}", assertInstanceOf(
                StringExpression.class,
                balanced.value()
        ).text().asPlain());

        var empty = assertInstanceOf(Declaration.class, rule.children().get(3));
        var emptyValue = assertInstanceOf(StringExpression.class, empty.value());
        assertTrue(emptyValue.text().parts().isEmpty());
        assertEquals("", emptyValue.span().text());

        var spaced = assertInstanceOf(Declaration.class, rule.children().get(4));
        assertEquals(" one two\n    three", assertInstanceOf(
                StringExpression.class,
                spaced.value()
        ).text().asPlain());

        var url = assertInstanceOf(Declaration.class, rule.children().get(5));
        assertEquals(" url(#{path})", assertInstanceOf(
                StringExpression.class,
                url.value()
        ).text().toString());

        var evaluatedName = assertInstanceOf(Declaration.class, rule.children().get(6));
        assertTrue(evaluatedName.parsedAsSassScript());
        assertInstanceOf(VariableExpression.class, evaluatedName.value());

        var customInterpolatedName = assertInstanceOf(
                Declaration.class,
                rule.children().get(7)
        );
        assertFalse(customInterpolatedName.parsedAsSassScript());
        assertEquals("--", customInterpolatedName.name().initialPlain());
        assertEquals(" raw", assertInstanceOf(
                StringExpression.class,
                customInterpolatedName.value()
        ).text().asPlain());

        var slashes = assertInstanceOf(Declaration.class, rule.children().get(8));
        assertEquals("//not-a-comment\nnext", assertInstanceOf(
                StringExpression.class,
                slashes.value()
        ).text().asPlain());

        var string = assertInstanceOf(Declaration.class, rule.children().get(9));
        var stringValue = assertInstanceOf(StringExpression.class, string.value());
        assertEquals(" \"a\\26 b#{value}\"", stringValue.text().toString());
        assertEquals(" \"a\\26 b#{value}\"", stringValue.span().text());

        var quotedUrl = assertInstanceOf(Declaration.class, rule.children().get(10));
        assertEquals(" url(\"foo\")", assertInstanceOf(
                StringExpression.class,
                quotedUrl.value()
        ).text().asPlain());
    }

    /// Verifies valueless, valued, recursive, empty, and commented nested properties.
    @Test
    void parsesNestedPropertyDeclarations() {
        var text = "a {"
                + "font: { /* nested */ family: Arial; size: 12px; "
                + "variant: { caps: small-caps } }"
                + "border: 1px { style: solid; }"
                + "empty: {}"
                + "}";
        var rule = assertInstanceOf(StyleRule.class, parse(text).children().get(0));

        assertEquals(3, rule.children().size());
        var font = assertInstanceOf(Declaration.class, rule.children().get(0));
        var fontChildren = Objects.requireNonNull(font.children());
        assertTrue(font.hasChildren());
        assertNull(font.value());
        assertEquals(4, fontChildren.size());
        assertInstanceOf(LoudComment.class, fontChildren.get(0));
        var family = assertInstanceOf(Declaration.class, fontChildren.get(1));
        assertEquals("Arial", assertInstanceOf(
                StringExpression.class,
                family.value()
        ).text().asPlain());

        var variant = assertInstanceOf(Declaration.class, fontChildren.get(3));
        var variantChildren = Objects.requireNonNull(variant.children());
        assertTrue(variant.hasChildren());
        assertEquals(1, variantChildren.size());
        assertTrue(variant.span().text().endsWith("}"));

        var border = assertInstanceOf(Declaration.class, rule.children().get(1));
        var borderChildren = Objects.requireNonNull(border.children());
        assertInstanceOf(NumberExpression.class, border.value());
        assertEquals(1, borderChildren.size());
        assertTrue(border.span().text().endsWith("}"));

        var empty = assertInstanceOf(Declaration.class, rule.children().get(2));
        var emptyChildren = Objects.requireNonNull(empty.children());
        assertTrue(empty.hasChildren());
        assertTrue(emptyChildren.isEmpty());
        assertThrows(UnsupportedOperationException.class, emptyChildren::clear);
    }

    /// Verifies transactional declaration-versus-selector disambiguation.
    @Test
    void disambiguatesDeclarationsAndNestedStyleRules() {
        var adjacent = assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo:bar { baz: qux; }")
        );
        assertEquals("foo:bar ", adjacent.selector().asPlain());

        var spaced = assertInstanceOf(
                Declaration.class,
                parseSingleChild("foo: bar { baz: qux; }")
        );
        assertTrue(spaced.hasChildren());

        var numeric = assertInstanceOf(
                Declaration.class,
                parseSingleChild("foo:1 { baz: qux; }")
        );
        assertTrue(numeric.hasChildren());

        assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo:#{bar} { baz: qux; }")
        );
        var hyphenInterpolation = assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo:-#{bar} { baz: qux; }")
        );
        assertEquals("foo:-#{bar} ", hyphenInterpolation.selector().toString());
        assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo::bar { baz: qux; }")
        );
        assertInstanceOf(
                StyleRule.class,
                parseSingleChild(".foo:bar { baz: qux; }")
        );
        assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo, bar { baz: qux; }")
        );
        assertInstanceOf(
                StyleRule.class,
                parseSingleChild("#{namespace}.$value {}")
        );
        assertInstanceOf(
                StyleRule.class,
                parseSingleChild("namespace#{suffix}.$value {}")
        );
        var normalizedWhitespace = assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo   .bar {}")
        );
        assertEquals("foo .bar ", normalizedWhitespace.selector().asPlain());
        var normalizedComment = assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo /* hidden */ .bar {}")
        );
        assertEquals("foo .bar ", normalizedComment.selector().asPlain());

        assertInstanceOf(Declaration.class, parseSingleChild("foo:bar;"));
        var interpolated = assertInstanceOf(
                Declaration.class,
                parseSingleChild("foo:#{$bar};")
        );
        assertTrue(interpolated.parsedAsSassScript());

        assertInstanceOf(
                StyleRule.class,
                parseSingleChild("foo:red { baz: qux; }")
        );
        var definiteDeclaration = assertInstanceOf(
                Declaration.class,
                parseSingleChild("foo:red;")
        );
        assertInstanceOf(ColorExpression.class, definiteDeclaration.value());
    }

    /// Verifies malformed and unavailable declaration forms fail without fallback.
    @Test
    void rejectsMalformedDeclarations() {
        var customNested = assertThrows(
                ParseException.class,
                () -> parse("a { font: { --x /* trivia */: value; } }")
        );
        assertEquals("--x", customNested.span().text());

        var nestedColor = assertInstanceOf(
                Declaration.class,
                parseSingleChild("font: { color:red}")
        );
        var nestedColorChildren = Objects.requireNonNull(nestedColor.children());
        assertInstanceOf(
                ColorExpression.class,
                assertInstanceOf(Declaration.class, nestedColorChildren.get(0)).value()
        );

        var nestedSelector = assertThrows(
                ParseException.class,
                () -> parse("a { font: { .child {} } }")
        );
        assertEquals("{", nestedSelector.span().text());

        var namespace = assertThrows(
                ParseException.class,
                () -> parse("a { theme.$value: 1; }")
        );
        assertEquals(".", namespace.span().text());

        var customChildren = assertThrows(
                ParseException.class,
                () -> parse("a { --x:{} }")
        );
        assertEquals("{", customChildren.span().text());

        var emptyValue = assertThrows(
                ParseException.class,
                () -> parse("a { width:; }")
        );
        assertEquals(";", emptyValue.span().text());

        var unclosedRawBracket = assertThrows(
                ParseException.class,
                () -> parse("a { --x: [one; }")
        );
        assertEquals("}", unclosedRawBracket.span().text());

        var missingSeparator = assertThrows(
                ParseException.class,
                () -> parse("a { width: 1px height: 2px; }")
        );
        assertEquals(":", missingSeparator.span().text());
    }

    /// Verifies malformed and unavailable statement productions fail precisely.
    @Test
    void rejectsUnsupportedOrMalformedStatements() {
        var unterminated = assertThrows(ParseException.class, () -> parse("/* comment"));
        assertEquals("", unterminated.span().text());

        var interpolation = assertThrows(ParseException.class, () -> parse("/* #{} */"));
        assertEquals("}", interpolation.span().text());

        var colorInterpolation = assertInstanceOf(
                StyleRule.class,
                parse("a#{red} {}").children().get(0)
        ).selector();
        var colorExpression = assertInstanceOf(
                ExpressionInterpolationPart.class,
                colorInterpolation.parts().get(1)
        ).expression();
        assertInstanceOf(ColorExpression.class, colorExpression);

        var atRule = assertThrows(ParseException.class, () -> parse("@media {}"));
        assertEquals("@", atRule.span().text());

        var variable = assertThrows(ParseException.class, () -> parse("$name {}"));
        assertEquals("$", variable.span().text());

        var unmatchedBracket = assertThrows(ParseException.class, () -> parse("a) {}"));
        assertEquals(")", unmatchedBracket.span().text());

        var rootDeclaration = assertThrows(
                ParseException.class,
                () -> parse("width: 1px;")
        );
        assertEquals(";", rootDeclaration.span().text());

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

    /// Parses the only child statement of a synthetic style rule.
    ///
    /// @param text the child statement source
    /// @return the parsed child statement
    private static SassStatement parseSingleChild(String text) {
        var stylesheet = parse("a {" + text + "}");
        var rule = assertInstanceOf(StyleRule.class, stylesheet.children().get(0));
        assertEquals(1, rule.children().size());
        return rule.children().get(0);
    }
}
