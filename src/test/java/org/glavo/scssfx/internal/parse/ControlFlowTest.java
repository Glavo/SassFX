// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.EachRule;
import org.glavo.scssfx.internal.ast.ForRule;
import org.glavo.scssfx.internal.ast.IfRule;
import org.glavo.scssfx.internal.ast.MediaRule;
import org.glavo.scssfx.internal.ast.SupportsRule;
import org.glavo.scssfx.internal.ast.SupportsAnything;
import org.glavo.scssfx.internal.ast.SupportsBooleanOperator;
import org.glavo.scssfx.internal.ast.SupportsInterpolation;
import org.glavo.scssfx.internal.ast.SupportsOperation;
import org.glavo.scssfx.internal.ast.SupportsDeclaration;
import org.glavo.scssfx.internal.ast.SupportsNegation;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.ast.WhileRule;
import org.glavo.scssfx.internal.evaluate.EvaluationException;
import org.glavo.scssfx.internal.evaluate.SassEvaluator;
import org.glavo.scssfx.internal.source.SourceFile;
import org.glavo.scssfx.internal.value.SassNull;
import org.glavo.scssfx.internal.value.SassNumber;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies control-flow parsing, evaluation, and CSS output.
@NotNullByDefault
final class ControlFlowTest {
    /// Verifies `@if` / `@else if` / `@else` parse structure.
    @Test
    void parsesIfElseChains() {
        var stylesheet = parse(
                "@if true { $a: 1; } @else if false { $b: 2; } @else { $c: 3; }"
        );
        var rule = assertInstanceOf(IfRule.class, stylesheet.children().get(0));
        assertEquals(2, rule.clauses().size());
        assertEquals(1, rule.clauses().get(0).children().size());
        assertEquals(1, Objects.requireNonNull(rule.lastClause()).children().size());
    }

    /// Verifies deprecated `@elseif` still parses and reports a deprecation.
    @Test
    void parsesDeprecatedElseif() {
        var stylesheet = parse("@if false {} @elseif true { $x: 1; }");
        var rule = assertInstanceOf(IfRule.class, stylesheet.children().get(0));
        assertEquals(2, rule.clauses().size());
        assertEquals(1, stylesheet.parseTimeWarnings().size());
        assertEquals("elseif", stylesheet.parseTimeWarnings().get(0).code());
        assertEquals(
                DiagnosticSeverity.DEPRECATION,
                stylesheet.parseTimeWarnings().get(0).severity()
        );
    }

    /// Verifies `@each`, `@for`, and `@while` parse structure and keywords.
    @Test
    void parsesEachForAndWhile() {
        var each = assertInstanceOf(
                EachRule.class,
                parse("@each $foo_bar, $y in 1, 2 {}").children().get(0)
        );
        assertEquals(List.of("foo-bar", "y"), each.variables());

        var exclusive = assertInstanceOf(
                ForRule.class,
                parse("@for $i from 1 + 2 to 5 {}").children().get(0)
        );
        assertTrue(exclusive.exclusive());
        assertInstanceOf(BinaryOperationExpression.class, exclusive.from());

        var inclusive = assertInstanceOf(
                ForRule.class,
                parse("@for $i from 1 through 3 {}").children().get(0)
        );
        assertFalse(inclusive.exclusive());

        assertInstanceOf(
                WhileRule.class,
                parse("@while $n > 0 {}").children().get(0)
        );
    }

    /// Verifies control flow is accepted inside style rules and nested properties.
    @Test
    void parsesControlFlowInNestedContexts() {
        var style = assertInstanceOf(
                StyleRule.class,
                parse("a { @if true { color: red; } }").children().get(0)
        );
        assertInstanceOf(IfRule.class, style.children().get(0));

        var nested = parse("a { font: { @if true { size: 1px; } } }");
        assertEquals(1, nested.children().size());
    }

    /// Verifies media and supports rules retain their conditions and children.
    @Test
    void parsesConditionalRulesAndRejectsUnknownAndBareElseAtRules() {
        var media = assertInstanceOf(
                MediaRule.class,
                parse("@media screen { Pane { color: red; } }").children().get(0)
        );
        assertEquals(
                "screen",
                Objects.requireNonNull(media.query().asPlain()).strip()
        );
        assertEquals(1, media.children().size());

        var supports = assertInstanceOf(
                SupportsRule.class,
                parse("@supports not (display: grid) { Pane { color: red; } }")
                        .children().get(0)
        );
        var negation = assertInstanceOf(
                SupportsNegation.class,
                supports.condition()
        );
        var declaration = assertInstanceOf(
                SupportsDeclaration.class,
                negation.condition()
        );
        assertEquals("display", declaration.name().toString());
        assertEquals("grid", declaration.value().toString());

        var general = assertInstanceOf(
                SupportsAnything.class,
                assertInstanceOf(
                        SupportsRule.class,
                        parse("@supports (font-tech(color-COLRv1)) { Pane {} }")
                                .children().get(0)
                ).condition()
        );
        assertEquals("font-tech(color-COLRv1)", general.contents().asPlain());
        assertEquals(1, supports.children().size());

        var unknown = assertThrows(ParseException.class, () -> parse("@unknown {}"));
        assertEquals("This stylesheet statement is not available.", unknown.getMessage());

        var bareElse = assertThrows(ParseException.class, () -> parse("@else {}"));
        assertEquals("This at-rule is not allowed here.", bareElse.getMessage());
    }

    /// Parses interpolation as an operand within a grouped supports operation.
    @Test
    void parsesGroupedSupportsOperationsWithInterpolation() {
        var rule = assertInstanceOf(
                SupportsRule.class,
                parse("@supports (#{$condition} and (display: grid)) {}").children().get(0)
        );
        var operation = assertInstanceOf(SupportsOperation.class, rule.condition());

        assertEquals(SupportsBooleanOperator.AND, operation.operator());
        assertInstanceOf(SupportsInterpolation.class, operation.left());
        var declaration = assertInstanceOf(SupportsDeclaration.class, operation.right());
        assertEquals("display", declaration.name().toString());
        assertEquals("grid", declaration.value().toString());
    }

    /// Verifies `@if` truthiness, branch selection, and semi-global assignment.
    @Test
    void evaluatesIfBranchesAndSemiGlobalAssignment() {
        var evaluator = execute(
                "$x: 1;"
                        + "$z: 0;"
                        + "@if false { $x: 0; }"
                        + "@else if null { $x: 3; }"
                        + "@else { $x: 4; }"
                        + "@if true { $y: 5; }"
                        + "@if 0 { $z: 6; }"
        );
        // Existing globals are updated through flow-control semi-global rules.
        assertEquals(SassNumber.of(4, null), global(evaluator, "x"));
        assertEquals(SassNumber.of(6, null), global(evaluator, "z"));
        // A new name inside a frame-creating branch stays local.
        assertNull(evaluator.environment().getVariable("y", null));
    }

    /// Verifies `@each` list and map iteration plus multi-variable destructuring.
    @Test
    void evaluatesEachIteration() {
        var values = execute(
                "$a: null; $b: null; $c: null;"
                        + "$i: 0;"
                        + "@each $n in 1 2 3 {"
                        + "  $i: $i + 1 !global;"
                        + "  @if $i == 1 { $a: $n !global; }"
                        + "  @if $i == 2 { $b: $n !global; }"
                        + "  @if $i == 3 { $c: $n !global; }"
                        + "}"
        );
        assertEquals(SassNumber.of(1, null), global(values, "a"));
        assertEquals(SassNumber.of(2, null), global(values, "b"));
        assertEquals(SassNumber.of(3, null), global(values, "c"));
        assertEquals(SassNumber.of(3, null), global(values, "i"));

        var map = execute(
                "$k1: null; $v1: null; $k2: null; $v2: null;"
                        + "$i: 0;"
                        + "@each $k, $v in (a: 1, b: 2) {"
                        + "  $i: $i + 1 !global;"
                        + "  @if $i == 1 { $k1: $k !global; $v1: $v !global; }"
                        + "  @if $i == 2 { $k2: $k !global; $v2: $v !global; }"
                        + "}"
        );
        assertEquals(new SassString("a", false), global(map, "k1"));
        assertEquals(SassNumber.of(1, null), global(map, "v1"));
        assertEquals(new SassString("b", false), global(map, "k2"));
        assertEquals(SassNumber.of(2, null), global(map, "v2"));

        var destructure = execute(
                "$a: 9; $b: 9; $c: 9;"
                        + "@each $a, $b, $c in (1 2,) {"
                        + "  $a: $a !global;"
                        + "  $b: $b !global;"
                        + "  $c: $c !global;"
                        + "}"
        );
        assertEquals(SassNumber.of(1, null), global(destructure, "a"));
        assertEquals(SassNumber.of(2, null), global(destructure, "b"));
        assertSame(SassNull.NULL, global(destructure, "c"));
    }

    /// Verifies `@for` exclusive/inclusive bounds, reverse iteration, and units.
    @Test
    void evaluatesForLoops() {
        assertEquals("1 2 3", collectForText(1, 4, true));
        assertEquals("1 2 3 4", collectForText(1, 4, false));
        assertEquals("3 2", collectForText(3, 1, true));
        assertEquals("3 2 1", collectForText(3, 1, false));

        var units = execute(
                "$a: null; $b: null;"
                        + "$i: 0;"
                        + "@for $n from 1px through 2px {"
                        + "  $i: $i + 1 !global;"
                        + "  @if $i == 1 { $a: $n !global; }"
                        + "  @if $i == 2 { $b: $n !global; }"
                        + "}"
        );
        assertEquals(SassNumber.of(1, "px"), global(units, "a"));
        assertEquals(SassNumber.of(2, "px"), global(units, "b"));
    }

    /// Verifies `@for` rejects non-numbers and non-integers with expression spans.
    @Test
    void rejectsInvalidForBounds() {
        var notNumber = assertThrows(
                EvaluationException.class,
                () -> execute("@for $i from a to 2 {}")
        );
        assertEquals("a is not a number.", notNumber.getMessage());
        assertEquals("a", Objects.requireNonNull(notNumber.primaryDiagnostic().span()).text());

        var notInt = assertThrows(
                EvaluationException.class,
                () -> execute("@for $i from 1.5 to 3 {}")
        );
        assertEquals("1.5 is not an int.", notInt.getMessage());
        assertEquals("1.5", Objects.requireNonNull(notInt.primaryDiagnostic().span()).text());
    }

    /// Verifies `@while` iterates until the condition becomes falsey.
    @Test
    void evaluatesWhileLoops() {
        var evaluator = execute(
                "$n: 3;"
                        + "$sum: 0;"
                        + "@while $n > 0 {"
                        + "  $sum: $sum + $n;"
                        + "  $n: $n - 1;"
                        + "}"
        );
        assertEquals(SassNumber.of(0, null), global(evaluator, "n"));
        assertEquals(SassNumber.of(6, null), global(evaluator, "sum"));

        var skipped = execute("$x: 1; @while false { $x: 2; }");
        assertEquals(SassNumber.of(1, null), global(skipped, "x"));
    }

    /// Verifies control flow emits the selected CSS declarations.
    @Test
    void compilesControlFlowToCss() throws Exception {
        var compiler = new SassCompiler();
        var result = compiler.compile(
                SassSource.fromString(
                        """
                                $names: a, b;
                                @each $name in $names {
                                  .#{$name} {
                                    @if $name == a {
                                      color: red;
                                    } @else {
                                      color: blue;
                                    }
                                  }
                                }
                                @for $i from 1 through 2 {
                                  .n-#{$i} { order: $i; }
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        .a {
                          color: red;
                        }

                        .b {
                          color: blue;
                        }

                        .n-1 {
                          order: 1;
                        }

                        .n-2 {
                          order: 2;
                        }""",
                result.output()
        );
    }

    /// Collects `@for` index values through string concatenation.
    ///
    /// @param from      the start bound
    /// @param to        the end bound
    /// @param exclusive whether to use `to`
    /// @return the visited indexes as a space-separated string
    private static String collectForText(int from, int to, boolean exclusive) {
        var keyword = exclusive ? "to" : "through";
        var source = "$out: \"\";"
                + "@for $i from " + from + " " + keyword + " " + to + " {"
                + "  @if $out == \"\" { $out: \"#{$i}\" !global; }"
                + "  @else { $out: \"#{$out} #{$i}\" !global; }"
                + "}";
        var evaluator = execute(source);
        return ((SassString) global(evaluator, "out")).text();
    }

    /// Parses an SCSS stylesheet.
    ///
    /// @param source the source text
    /// @return the parsed stylesheet
    private static Stylesheet parse(String source) {
        return new ScssParser(new SourceFile(source, null)).parse();
    }

    /// Executes a stylesheet and returns the evaluator.
    ///
    /// @param source the source text
    /// @return the evaluator after execution
    private static SassEvaluator execute(String source) {
        var evaluator = new SassEvaluator();
        evaluator.execute(parse(source));
        return evaluator;
    }

    /// Returns a required global value.
    ///
    /// @param evaluator the evaluator
    /// @param name      the normalized variable name
    /// @return the bound value
    private static SassValue global(SassEvaluator evaluator, String name) {
        return Objects.requireNonNull(
                evaluator.environment().getVariable(name, null),
                "missing global " + name
        );
    }
}
