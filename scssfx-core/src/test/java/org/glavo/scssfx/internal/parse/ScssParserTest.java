// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.BinaryOperator;
import org.glavo.scssfx.internal.ast.ColorExpression;
import org.glavo.scssfx.internal.ast.Declaration;
import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.ForwardRule;
import org.glavo.scssfx.internal.ast.FunctionExpression;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.MediaRule;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.ast.UseRule;
import org.glavo.scssfx.internal.ast.VariableDeclaration;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.source.SourceFile;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    /// Verifies top-level and nested variable declarations, normalized names, and source ranges.
    @Test
    void parsesTopLevelAndNestedVariableDeclarations() {
        var source = "$top_level: 1 + 2;a {$nested_value: red}";
        var stylesheet = parse(source);

        assertEquals(2, stylesheet.children().size());
        var topLevel = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(0)
        );
        assertNull(topLevel.namespace());
        assertEquals("top-level", topLevel.name());
        assertEquals("$top_level", topLevel.originalName());
        assertFalse(topLevel.isGuarded());
        assertFalse(topLevel.isGlobal());
        assertNull(topLevel.comment());
        assertInstanceOf(BinaryOperationExpression.class, topLevel.expression());
        assertSpan(topLevel.nameSpan(), 0, 10, "$top_level");
        assertNull(topLevel.namespaceSpan());
        assertSpan(topLevel.span(), 0, 17, "$top_level: 1 + 2");

        var rule = assertInstanceOf(StyleRule.class, stylesheet.children().get(1));
        assertEquals(1, rule.children().size());
        var nested = assertInstanceOf(VariableDeclaration.class, rule.children().get(0));
        assertEquals("nested-value", nested.name());
        assertEquals("$nested_value", nested.originalName());
        assertInstanceOf(ColorExpression.class, nested.expression());
        assertSpan(nested.nameSpan(), 21, 34, "$nested_value");
        assertSpan(nested.span(), 21, 39, "$nested_value: red");

        assertTrue(stylesheet.parseTimeWarnings().isEmpty());
        assertTrue(stylesheet.globalVariables().isEmpty());
    }

    /// Verifies variable declarations at block, nested-property, and end-of-input boundaries.
    @Test
    void parsesVariableDeclarationStatementBoundaries() {
        var adjacent = parse("a {$x: 1}$top: 2");
        assertEquals(2, adjacent.children().size());
        var rule = assertInstanceOf(StyleRule.class, adjacent.children().get(0));
        assertInstanceOf(VariableDeclaration.class, rule.children().get(0));
        var topLevel = assertInstanceOf(
                VariableDeclaration.class,
                adjacent.children().get(1)
        );
        assertEquals("top", topLevel.name());
        assertEquals("$top: 2", topLevel.span().text());

        var nestedRule = assertInstanceOf(
                StyleRule.class,
                parse("a {font: {$nested: 3;size: $nested;}}").children().get(0)
        );
        var font = assertInstanceOf(Declaration.class, nestedRule.children().get(0));
        var fontChildren = Objects.requireNonNull(font.children());
        assertEquals(2, fontChildren.size());
        var nested = assertInstanceOf(VariableDeclaration.class, fontChildren.get(0));
        assertEquals("nested", nested.name());
        assertEquals("$nested: 3", nested.span().text());
        var size = assertInstanceOf(Declaration.class, fontChildren.get(1));
        var reference = assertInstanceOf(VariableExpression.class, size.value());
        assertEquals("nested", reference.name());

        var namespacedRule = assertInstanceOf(
                StyleRule.class,
                parse("a {theme.$value: 4}").children().get(0)
        );
        var namespaced = assertInstanceOf(
                VariableDeclaration.class,
                namespacedRule.children().get(0)
        );
        assertEquals("theme", namespaced.namespace());
        assertEquals("value", namespaced.name());
        assertEquals("theme.$value: 4", namespaced.span().text());

        var missingSeparator = assertThrows(
                ParseException.class,
                () -> parse("$x: 1 $y: 2;")
        );
        assertEquals(":", missingSeparator.span().text());
    }

    /// Verifies both flag orders, intervening trivia, escaped flags, and qualified assignments.
    @Test
    void parsesVariableFlagsAndNamespacedAssignments() {
        var source = "$first_value: 1 !default/**/ !global  ;\n"
                + "$second: 2 !global\n  /* gap */ !d\\65 fault;\n"
                + "theme_tools.$accent_color: blue !default;\n"
                + "_private_namespace.$public: 3;";
        var stylesheet = parse(source);

        assertEquals(4, stylesheet.children().size());
        var first = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(0)
        );
        assertEquals("first-value", first.name());
        assertTrue(first.isGuarded());
        assertTrue(first.isGlobal());
        assertSpan(first.span(), 0, 38, "$first_value: 1 !default/**/ !global  ");

        var second = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(1)
        );
        assertEquals("second", second.name());
        assertTrue(second.isGuarded());
        assertTrue(second.isGlobal());
        assertEquals("$second: 2 !global\n  /* gap */ !d\\65 fault", second.span().text());

        var namespaced = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(2)
        );
        assertEquals("theme_tools", namespaced.namespace());
        assertEquals("accent-color", namespaced.name());
        assertEquals("theme_tools.$accent_color", namespaced.originalName());
        assertTrue(namespaced.isGuarded());
        assertFalse(namespaced.isGlobal());
        var namespacedStart = source.indexOf("theme_tools");
        assertSpan(
                Objects.requireNonNull(namespaced.namespaceSpan()),
                namespacedStart,
                namespacedStart + 11,
                "theme_tools"
        );
        assertSpan(
                namespaced.nameSpan(),
                namespacedStart + 12,
                namespacedStart + 25,
                "$accent_color"
        );
        assertEquals(
                "theme_tools.$accent_color: blue !default",
                namespaced.span().text()
        );

        var privateNamespace = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(3)
        );
        assertEquals("_private_namespace", privateNamespace.namespace());
        assertEquals("public", privateNamespace.name());

        assertTrue(stylesheet.parseTimeWarnings().isEmpty());
        assertEquals(
                java.util.List.of("first-value", "second"),
                stylesheet.globalVariables().keySet().stream().toList()
        );
        assertEquals(first.span(), stylesheet.globalVariables().get("first-value"));
        assertEquals(second.span(), stylesheet.globalVariables().get("second"));
    }

    /// Verifies that important values remain expressions rather than declaration flags.
    @Test
    void distinguishesImportantValuesFromVariableFlags() {
        var source = "$priority: 1px ! /* hidden */ IMPORTANT !default;";
        var declaration = assertInstanceOf(
                VariableDeclaration.class,
                parse(source).children().get(0)
        );

        assertEquals("1px !important", declaration.expression().toString());
        assertTrue(declaration.isGuarded());
        assertFalse(declaration.isGlobal());
        assertSpan(
                declaration.span(),
                0,
                source.length() - 1,
                source.substring(0, source.length() - 1)
        );

        assertParseError(
                "$value: 1 !DEFAULT;",
                "Invalid flag name.",
                10,
                18,
                "!DEFAULT"
        );
    }

    /// Verifies duplicate-flag diagnostics and first-global-assignment metadata.
    @Test
    void recordsVariableDeclarationMetadata() {
        var source = "$same_name: 0;$same_name: 1 !global;$same_name: 2 !global;"
                + "$guarded: 3 !default !default;"
                + "$global: 4 !global !global;"
                + "$both: 5 !default !global !default !global;";
        var stylesheet = parse(source);

        var warnings = stylesheet.parseTimeWarnings();
        assertEquals(4, warnings.size());

        var guardedFirst = source.indexOf("!default", source.indexOf("$guarded"));
        var guardedDuplicate = source.indexOf("!default", guardedFirst + 1);
        assertDuplicateFlagWarning(warnings.get(0), "!default", guardedDuplicate);

        var globalFirst = source.indexOf("!global", source.indexOf("$global"));
        var globalDuplicate = source.indexOf("!global", globalFirst + 1);
        assertDuplicateFlagWarning(warnings.get(1), "!global", globalDuplicate);

        var bothStart = source.indexOf("$both");
        var bothDefaultFirst = source.indexOf("!default", bothStart);
        var bothGlobalFirst = source.indexOf("!global", bothDefaultFirst);
        var bothDefaultDuplicate = source.indexOf("!default", bothGlobalFirst);
        var bothGlobalDuplicate = source.indexOf("!global", bothDefaultDuplicate);
        assertDuplicateFlagWarning(warnings.get(2), "!default", bothDefaultDuplicate);
        assertDuplicateFlagWarning(warnings.get(3), "!global", bothGlobalDuplicate);
        assertThrows(UnsupportedOperationException.class, warnings::clear);

        var globals = stylesheet.globalVariables();
        assertEquals(
                java.util.List.of("same-name", "global", "both"),
                globals.keySet().stream().toList()
        );
        var firstSameNameGlobal = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(1)
        );
        assertEquals(firstSameNameGlobal.span(), globals.get("same-name"));
        assertEquals(
                assertInstanceOf(VariableDeclaration.class, stylesheet.children().get(4)).span(),
                globals.get("global")
        );
        assertEquals(
                assertInstanceOf(VariableDeclaration.class, stylesheet.children().get(5)).span(),
                globals.get("both")
        );
        assertThrows(UnsupportedOperationException.class, globals::clear);
    }

    /// Verifies that immediately preceding silent comments are attached to declarations.
    @Test
    void attachesPrecedingSilentCommentsToVariables() {
        var source = "/// First line\n/// second\n$documented: 1;\n"
                + "// ordinary\n$ordinary: 2;";
        var stylesheet = parse(source);

        assertEquals(4, stylesheet.children().size());
        var documentation = assertInstanceOf(
                SilentComment.class,
                stylesheet.children().get(0)
        );
        var documented = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(1)
        );
        assertSame(documentation, documented.comment());
        assertEquals("First line\nsecond", documentation.documentation());

        var ordinaryComment = assertInstanceOf(
                SilentComment.class,
                stylesheet.children().get(2)
        );
        var ordinary = assertInstanceOf(
                VariableDeclaration.class,
                stylesheet.children().get(3)
        );
        assertSame(ordinaryComment, ordinary.comment());
        assertNull(ordinaryComment.documentation());
    }

    /// Verifies committed variable-declaration failures and their exact source ranges.
    @Test
    void rejectsMalformedVariableDeclarations() {
        assertParseError("$value 1;", "expected \":\".", 7, 8, "1");
        assertParseError("$value:;", "Expected expression.", 7, 8, ";");
        assertParseError(
                "$value: 1 !unknown;",
                "Invalid flag name.",
                10,
                18,
                "!unknown"
        );
        assertParseError(
                "theme.$value: 1 !global;",
                "!global isn't allowed for variables in other modules.",
                16,
                23,
                "!global"
        );
        assertParseError(
                "theme.$_secret: 1;",
                "Private members can't be accessed from outside their modules.",
                0,
                14,
                "theme.$_secret"
        );
        assertParseError(
                "theme.$-secret: 1;",
                "Private members can't be accessed from outside their modules.",
                0,
                14,
                "theme.$-secret"
        );
        assertParseError(
                "theme.$\\5f secret: 1;",
                "Private members can't be accessed from outside their modules.",
                0,
                17,
                "theme.$\\5f secret"
        );
        assertParseError(
                "theme.$value {}",
                "expected \":\".",
                13,
                14,
                "{"
        );

        var nearMiss = assertInstanceOf(
                StyleRule.class,
                parse("theme . $value {}").children().get(0)
        );
        assertEquals("theme . $value ", nearMiss.selector().asPlain());
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

    /// Verifies strict-unary deprecations, normalized messages, spans, and parsed operations.
    @Test
    void recordsStrictUnaryDeprecations() {
        var source = "a {plus: 1 +2;minus: 1 -$x;chain: 1 +2 +3;"
                + "grouped: (1 +2);comment: 1 /**/+2;line: 1\n+2}";
        var stylesheet = parse(source);
        var rule = assertInstanceOf(StyleRule.class, stylesheet.children().get(0));

        assertEquals(6, rule.children().size());
        var plus = assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(0)).value()
        );
        assertEquals(BinaryOperator.PLUS, plus.operator());
        assertInstanceOf(NumberExpression.class, plus.left());
        assertInstanceOf(NumberExpression.class, plus.right());

        var minus = assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(1)).value()
        );
        assertEquals(BinaryOperator.MINUS, minus.operator());
        assertInstanceOf(VariableExpression.class, minus.right());

        var chain = assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(2)).value()
        );
        assertEquals(BinaryOperator.PLUS, chain.operator());
        assertInstanceOf(BinaryOperationExpression.class, chain.left());
        assertInstanceOf(NumberExpression.class, chain.right());

        var grouped = assertInstanceOf(
                ParenthesizedExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(3)).value()
        );
        assertInstanceOf(BinaryOperationExpression.class, grouped.expression());

        var comment = assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(4)).value()
        );
        assertEquals(BinaryOperator.PLUS, comment.operator());

        var line = assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(5)).value()
        );
        assertEquals(BinaryOperator.PLUS, line.operator());

        var warnings = stylesheet.parseTimeWarnings();
        assertEquals(7, warnings.size());

        var plusStart = source.indexOf("1 +2");
        assertStrictUnaryWarning(warnings.get(0), "1", "+", "2", plusStart, "1 +2");

        var minusStart = source.indexOf("1 -$x");
        assertStrictUnaryWarning(warnings.get(1), "1", "-", "$x", minusStart, "1 -$x");

        var chainStart = source.indexOf("1 +2 +3");
        assertStrictUnaryWarning(warnings.get(2), "1", "+", "2", chainStart, "1 +2");
        assertStrictUnaryWarning(
                warnings.get(3),
                "1 + 2",
                "+",
                "3",
                chainStart,
                "1 +2 +3"
        );

        var groupedStart = source.indexOf("1 +2", source.indexOf("grouped"));
        assertStrictUnaryWarning(warnings.get(4), "1", "+", "2", groupedStart, "1 +2");

        var commentStart = source.indexOf("1 /**/+2");
        assertStrictUnaryWarning(
                warnings.get(5),
                "1",
                "+",
                "2",
                commentStart,
                "1 /**/+2"
        );

        var lineStart = source.indexOf("1\n+2");
        assertStrictUnaryWarning(warnings.get(6), "1", "+", "2", lineStart, "1\n+2");
    }

    /// Verifies syntax that unambiguously selects binary or unary interpretation emits no warning.
    @Test
    void acceptsUnambiguousUnaryAndBinarySyntax() {
        var source = "a {adjacent: 1+2;spaced: 1 + 2;intended: 1 -2;"
                + "parenthesized: 1 (+2);before-comment: 1/* before */ +2;"
                + "after-comment: 1 +/**/2;other: 1 *2;"
                + "right-spaced: 1+ 2;minus-spaced: 1 - ($x);"
                + "comment-both: 1/**/+/**/2}";
        var stylesheet = parse(source);
        var rule = assertInstanceOf(StyleRule.class, stylesheet.children().get(0));

        assertTrue(stylesheet.parseTimeWarnings().isEmpty());
        assertEquals(10, rule.children().size());
        assertEquals(BinaryOperator.PLUS, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(0)).value()
        ).operator());
        assertEquals(BinaryOperator.PLUS, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(1)).value()
        ).operator());

        var intended = assertInstanceOf(
                ListExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(2)).value()
        );
        assertEquals(-2.0, assertInstanceOf(
                NumberExpression.class,
                intended.contents().get(1)
        ).value());

        var parenthesized = assertInstanceOf(
                ListExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(3)).value()
        );
        var signed = assertInstanceOf(
                ParenthesizedExpression.class,
                parenthesized.contents().get(1)
        );
        assertEquals(2.0, assertInstanceOf(
                NumberExpression.class,
                signed.expression()
        ).value());

        assertEquals(BinaryOperator.PLUS, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(4)).value()
        ).operator());
        assertEquals(BinaryOperator.PLUS, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(5)).value()
        ).operator());
        assertEquals(BinaryOperator.TIMES, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(6)).value()
        ).operator());
        assertEquals(BinaryOperator.PLUS, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(7)).value()
        ).operator());
        assertEquals(BinaryOperator.MINUS, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(8)).value()
        ).operator());
        assertEquals(BinaryOperator.PLUS, assertInstanceOf(
                BinaryOperationExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(9)).value()
        ).operator());
    }

    /// Verifies parser-specific replay and fallback diagnostic retention.
    @Test
    void retainsWarningsAcrossExpressionReparsingAndFallback() {
        var source = "a {comma: (1 +2, 3);space: (1 +2 3);"
                + "fallback: url(#{1 +2} x)}";
        var stylesheet = parse(source);
        var rule = assertInstanceOf(StyleRule.class, stylesheet.children().get(0));

        assertEquals(3, rule.children().size());
        var comma = assertInstanceOf(
                ParenthesizedExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(0)).value()
        );
        var commaList = assertInstanceOf(ListExpression.class, comma.expression());
        assertEquals(ListSeparator.COMMA, commaList.separator());
        assertInstanceOf(BinaryOperationExpression.class, commaList.contents().get(0));

        var space = assertInstanceOf(
                ParenthesizedExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(1)).value()
        );
        var spaceList = assertInstanceOf(ListExpression.class, space.expression());
        assertEquals(ListSeparator.SPACE, spaceList.separator());
        assertInstanceOf(BinaryOperationExpression.class, spaceList.contents().get(0));

        var url = assertInstanceOf(
                FunctionExpression.class,
                assertInstanceOf(Declaration.class, rule.children().get(2)).value()
        );
        assertEquals("url", url.name());
        assertEquals(1, url.arguments().positional().size());
        assertInstanceOf(ListExpression.class, url.arguments().positional().get(0));

        var warnings = stylesheet.parseTimeWarnings();
        assertEquals(4, warnings.size());
        var commaStart = source.indexOf("1 +2");
        assertStrictUnaryWarning(warnings.get(0), "1", "+", "2", commaStart, "1 +2");
        var spaceStart = source.indexOf("1 +2", commaStart + 1);
        assertStrictUnaryWarning(warnings.get(1), "1", "+", "2", spaceStart, "1 +2");
        var urlStart = source.indexOf("1 +2", spaceStart + 1);
        assertStrictUnaryWarning(warnings.get(2), "1", "+", "2", urlStart, "1 +2");
        assertStrictUnaryWarning(warnings.get(3), "1", "+", "2", urlStart, "1 +2");
    }

    /// Verifies declaration lookahead retains speculative warnings during selector fallback.
    @Test
    void retainsWarningsDuringDeclarationSelectorFallback() {
        var source = "a {foo:bar +2 {};baz:qux#{1 +2} {}}";
        var stylesheet = parse(source);
        var outer = assertInstanceOf(StyleRule.class, stylesheet.children().get(0));

        assertEquals(2, outer.children().size());
        var speculativeOnly = assertInstanceOf(StyleRule.class, outer.children().get(0));
        assertEquals("foo:bar +2 ", speculativeOnly.selector().asPlain());

        var interpolated = assertInstanceOf(StyleRule.class, outer.children().get(1));
        assertTrue(interpolated.selector().parts().stream()
                .anyMatch(ExpressionInterpolationPart.class::isInstance));

        var warnings = stylesheet.parseTimeWarnings();
        assertEquals(3, warnings.size());
        var speculativeStart = source.indexOf("bar +2");
        assertStrictUnaryWarning(
                warnings.get(0),
                "bar",
                "+",
                "2",
                speculativeStart,
                "bar +2"
        );
        var interpolationStart = source.indexOf("1 +2");
        assertStrictUnaryWarning(
                warnings.get(1),
                "1",
                "+",
                "2",
                interpolationStart,
                "1 +2"
        );
        assertStrictUnaryWarning(
                warnings.get(2),
                "1",
                "+",
                "2",
                interpolationStart,
                "1 +2"
        );
    }

    /// Verifies strict-unary and duplicate-variable-flag diagnostics retain source order.
    @Test
    void ordersDifferentParseTimeWarningsBySource() {
        var source = "$x: 1 +2 !default !default;a {b: 1 -$x}";
        var stylesheet = parse(source);
        var warnings = stylesheet.parseTimeWarnings();

        assertEquals(3, warnings.size());
        var firstOperation = source.indexOf("1 +2");
        assertStrictUnaryWarning(
                warnings.get(0),
                "1",
                "+",
                "2",
                firstOperation,
                "1 +2"
        );
        var firstDefault = source.indexOf("!default");
        var duplicateDefault = source.indexOf("!default", firstDefault + 1);
        assertDuplicateFlagWarning(warnings.get(1), "!default", duplicateDefault);
        var secondOperation = source.indexOf("1 -$x");
        assertStrictUnaryWarning(
                warnings.get(2),
                "1",
                "-",
                "$x",
                secondOperation,
                "1 -$x"
        );
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

    /// Verifies module directives may be interleaved with root variables and comments.
    @Test
    void parsesModuleDirectivesAndUseConfiguration() {
        var source = """
                $theme: blue;
                /* before */
                @use "foo.test.scss" with (
                  $primary_color: $theme,
                  $gap: 1px + 2px,
                );
                foo.$after: 1;
                // between
                @forward "bridge";
                @use "other" as *;
                """;
        var stylesheet = parse(source);

        assertEquals(7, stylesheet.children().size());
        assertInstanceOf(VariableDeclaration.class, stylesheet.children().get(0));
        assertInstanceOf(LoudComment.class, stylesheet.children().get(1));

        var use = assertInstanceOf(UseRule.class, stylesheet.children().get(2));
        assertEquals("foo.test.scss", use.url());
        assertEquals("foo", use.namespace());
        assertEquals(2, use.configuration().size());
        var primary = use.configuration().get(0);
        assertEquals("primary-color", primary.name());
        assertEquals("$primary_color", primary.nameSpan().text());
        assertEquals("$primary_color: $theme", primary.span().text());
        var primaryValue = assertInstanceOf(VariableExpression.class, primary.expression());
        assertEquals("theme", primaryValue.name());
        var gap = use.configuration().get(1);
        assertEquals("gap", gap.name());
        assertEquals("$gap: 1px + 2px", gap.span().text());
        assertInstanceOf(BinaryOperationExpression.class, gap.expression());
        assertThrows(UnsupportedOperationException.class, () -> use.configuration().clear());

        assertInstanceOf(VariableDeclaration.class, stylesheet.children().get(3));
        assertInstanceOf(SilentComment.class, stylesheet.children().get(4));
        var forward = assertInstanceOf(ForwardRule.class, stylesheet.children().get(5));
        assertEquals("bridge", forward.url());
        assertEquals("@forward \"bridge\"", forward.span().text());
        assertNull(forward.prefix());
        assertNull(forward.shownMixinsAndFunctions());
        assertNull(forward.shownVariables());
        assertNull(forward.hiddenMixinsAndFunctions());
        assertNull(forward.hiddenVariables());
        assertTrue(forward.configuration().isEmpty());
        var globalUse = assertInstanceOf(UseRule.class, stylesheet.children().get(6));
        assertNull(globalUse.namespace());
        assertTrue(globalUse.configuration().isEmpty());
    }

    /// Verifies ordinary rules close the module-directive window at the stylesheet root.
    @Test
    void rejectsLateAndNestedModuleDirectives() {
        var lateForward = assertThrows(
                ParseException.class,
                () -> parse("@use \"a\"; a {} $x: 1; /* comment */ @forward \"b\";")
        );
        assertEquals(
                "@forward rules must be written before any other rules.",
                lateForward.getMessage()
        );

        var lateUse = assertThrows(
                ParseException.class,
                () -> parse("@forward \"a\"; a {} $x: 1; @use \"b\";")
        );
        assertEquals("@use rules must be written before any other rules.", lateUse.getMessage());

        var nestedUse = assertThrows(
                ParseException.class,
                () -> parse("@if true { @use \"a\"; }")
        );
        assertEquals("This at-rule is not allowed here.", nestedUse.getMessage());

        var nestedForward = assertThrows(
                ParseException.class,
                () -> parse("@if true { @forward \"a\"; }")
        );
        assertEquals("This at-rule is not allowed here.", nestedForward.getMessage());
    }

    /// Verifies forward prefixes, member filters, and guarded configuration.
    @Test
    void parsesAdvancedForwardClauses() {
        var stylesheet = parse("""
                @forward "a" as theme_* show public_fn, $public_var with (
                  $first_value: 1,
                  $second: 2 !default,
                );
                @forward "b" hide private_fn, $private_var;
                """);

        var shown = assertInstanceOf(
                ForwardRule.class,
                stylesheet.children().get(0)
        );
        assertEquals("theme-", shown.prefix());
        assertEquals(
                Set.of("public-fn"),
                shown.shownMixinsAndFunctions()
        );
        assertEquals(Set.of("public-var"), shown.shownVariables());
        assertNull(shown.hiddenMixinsAndFunctions());
        assertNull(shown.hiddenVariables());
        assertEquals(2, shown.configuration().size());
        assertFalse(shown.configuration().get(0).guarded());
        assertTrue(shown.configuration().get(1).guarded());
        assertEquals(
                "$second: 2 !default",
                shown.configuration().get(1).span().text()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> Objects.requireNonNull(
                        shown.shownVariables()
                ).clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> shown.configuration().clear()
        );

        var hidden = assertInstanceOf(
                ForwardRule.class,
                stylesheet.children().get(1)
        );
        assertNull(hidden.prefix());
        assertNull(hidden.shownMixinsAndFunctions());
        assertNull(hidden.shownVariables());
        assertEquals(
                Set.of("private-fn"),
                hidden.hiddenMixinsAndFunctions()
        );
        assertEquals(Set.of("private-var"), hidden.hiddenVariables());
        assertTrue(hidden.configuration().isEmpty());
    }

    /// Rejects malformed or out-of-order advanced forward clauses.
    @Test
    void rejectsInvalidAdvancedForwardClauses() {
        var duplicate = assertThrows(
                ParseException.class,
                () -> parse(
                        "@forward \"a\" with ($foo_bar: 1, $foo-bar: 2);"
                )
        );
        assertEquals(
                "The same variable may only be configured once.",
                duplicate.getMessage()
        );

        var invalidFlag = assertThrows(
                ParseException.class,
                () -> parse("@forward \"a\" with ($value: 1 !global);")
        );
        assertEquals("Invalid flag name.", invalidFlag.getMessage());

        assertThrows(
                ParseException.class,
                () -> parse("@use \"a\" with ($value: 1 !default);")
        );
        assertThrows(
                ParseException.class,
                () -> parse("@forward \"a\" as prefix;")
        );
        assertThrows(
                ParseException.class,
                () -> parse("@forward \"a\" show;")
        );
        assertThrows(
                ParseException.class,
                () -> parse("@forward \"a\" with ($value: 1) show value;")
        );
        assertThrows(
                ParseException.class,
                () -> parse("@forward \"a\" show value as prefix-*;")
        );
    }

    /// Verifies configuration names and default module namespaces are normalized and validated.
    @Test
    void validatesUseConfigurationAndDefaultNamespaces() {
        var duplicate = assertThrows(
                ParseException.class,
                () -> parse("@use \"a\" with ($foo_bar: 1, $foo-bar: 2);")
        );
        assertEquals(
                "The same variable may only be configured once.",
                duplicate.getMessage()
        );
        assertEquals("$foo-bar", duplicate.span().text());

        var dotted = assertInstanceOf(
                UseRule.class,
                parse("@use \"foo.test.scss\";").children().get(0)
        );
        assertEquals("foo", dotted.namespace());

        var explicit = assertInstanceOf(
                UseRule.class,
                parse("@use \"foo+.scss\" as valid;").children().get(0)
        );
        assertEquals("valid", explicit.namespace());

        var invalid = assertThrows(
                ParseException.class,
                () -> parse("@use \"foo+.scss\";")
        );
        assertTrue(invalid.getMessage().contains(
                "The default namespace \"foo+\" is not a valid Sass identifier."
        ));
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

        var media = assertInstanceOf(
                MediaRule.class,
                parse("@media screen {}").children().get(0)
        );
        assertEquals("@media screen {}", media.span().text());

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

    /// Verifies one duplicate-variable-flag diagnostic.
    ///
    /// @param diagnostic the diagnostic to verify
    /// @param flag the repeated flag spelling
    /// @param startOffset the expected flag offset
    private static void assertDuplicateFlagWarning(
            Diagnostic diagnostic,
            String flag,
            int startOffset
    ) {
        assertEquals(DiagnosticSeverity.DEPRECATION, diagnostic.severity());
        assertEquals(
                flag + " should only be written once for each variable.\n"
                        + "This will be an error in Dart Sass 2.0.0.",
                diagnostic.message()
        );
        assertEquals("duplicate-var-flags", diagnostic.code());
        assertSpan(
                Objects.requireNonNull(diagnostic.span()),
                startOffset,
                startOffset + flag.length(),
                flag
        );
    }

    /// Verifies one strict-unary deprecation diagnostic.
    ///
    /// @param diagnostic the diagnostic to verify
    /// @param left the normalized left operand
    /// @param operator the ambiguous operator
    /// @param right the normalized right operand
    /// @param startOffset the expected operation offset
    /// @param sourceText the expected operation source text
    private static void assertStrictUnaryWarning(
            Diagnostic diagnostic,
            String left,
            String operator,
            String right,
            int startOffset,
            String sourceText
    ) {
        assertEquals(DiagnosticSeverity.DEPRECATION, diagnostic.severity());
        assertEquals("strict-unary", diagnostic.code());
        assertEquals(
                "This operation is parsed as:\n"
                        + "\n"
                        + "    " + left + " " + operator + " " + right + "\n"
                        + "\n"
                        + "but you may have intended it to mean:\n"
                        + "\n"
                        + "    " + left + " (" + operator + right + ")\n"
                        + "\n"
                        + "Add a space after " + operator + " to clarify that it's meant to be "
                        + "a binary operation, or wrap\n"
                        + "it in parentheses to make it a unary operation. This will be an error "
                        + "in future\n"
                        + "versions of Sass.\n"
                        + "\n"
                        + "More info and automated migrator: "
                        + "https://sass-lang.com/d/strict-unary",
                diagnostic.message()
        );
        assertSpan(
                Objects.requireNonNull(diagnostic.span()),
                startOffset,
                startOffset + sourceText.length(),
                sourceText
        );
    }

    /// Verifies a parse failure and its exact source range.
    ///
    /// @param source the malformed SCSS source
    /// @param message the expected diagnostic message
    /// @param startOffset the expected inclusive source offset
    /// @param endOffset the expected exclusive source offset
    /// @param text the expected source text
    private static void assertParseError(
            String source,
            String message,
            int startOffset,
            int endOffset,
            String text
    ) {
        var error = assertThrows(ParseException.class, () -> parse(source));
        assertEquals(message, error.getMessage());
        assertSpan(error.span(), startOffset, endOffset, text);
    }

    /// Verifies an exact half-open source span.
    ///
    /// @param span the source span to verify
    /// @param startOffset the expected inclusive offset
    /// @param endOffset the expected exclusive offset
    /// @param text the expected captured source text
    private static void assertSpan(
            SourceSpan span,
            int startOffset,
            int endOffset,
            String text
    ) {
        assertEquals(startOffset, span.start().offset());
        assertEquals(endOffset, span.end().offset());
        assertEquals(text, span.text());
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
