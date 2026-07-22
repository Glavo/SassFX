// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.evaluate.EvaluationException;
import org.glavo.scssfx.internal.evaluate.SassEvaluator;
import org.glavo.scssfx.internal.evaluate.VariableBinding;
import org.glavo.scssfx.internal.source.SourceFile;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassBoolean;
import org.glavo.scssfx.internal.value.SassList;
import org.glavo.scssfx.internal.value.SassMap;
import org.glavo.scssfx.internal.value.SassNull;
import org.glavo.scssfx.internal.value.SassNumber;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies evaluation across parsed expressions, statements, scopes, and diagnostics.
@NotNullByDefault
final class SassEvaluatorTest {
    /// Verifies primitive, interpolation, list, and arithmetic evaluation.
    @Test
    void evaluatesParsedExpressions() {
        assertEquals(SassNumber.of(12, "px"), evaluate("12px"));
        assertEquals(new SassString("a3b", true), evaluate("\"a#{1 + 2}b\""));
        assertEquals(
                new SassList(
                        List.of(SassNumber.of(1, null), SassNumber.of(2, null)),
                        ListSeparator.COMMA,
                        true
                ),
                evaluate("[1, 2]")
        );
        assertSame(SassBoolean.TRUE, evaluate("1in == 96px"));
        assertEquals(SassNumber.of(2, "in"), evaluate("1in + 96px"));
    }

    /// Verifies that boolean operators return operands and avoid unreachable failures.
    @Test
    void shortCircuitsBooleanOperators() {
        assertSame(SassBoolean.FALSE, evaluate("false and $missing"));
        assertSame(SassBoolean.TRUE, evaluate("true or $missing"));
        assertEquals(SassNumber.of(7, null), evaluate("null or 7"));
        assertEquals(SassNumber.of(7, null), evaluate("0 and 7"));

        assertExpressionFailure("true and $missing", "Undefined variable.", "$missing");
        assertExpressionFailure("false or $missing", "Undefined variable.", "$missing");
    }

    /// Verifies that undefined self and forward references report the reference span.
    @Test
    void rejectsSelfAndForwardReferences() {
        assertStylesheetFailure("$x: $x;", "Undefined variable.", "$x");
        assertStylesheetFailure(
                "$first: $second; $second: 2;",
                "Undefined variable.",
                "$second"
        );
        assertStylesheetFailure(
                "a { color: $missing; }",
                "Undefined variable.",
                "$missing"
        );
    }

    /// Verifies normalized names, reassignment, and value-origin propagation.
    @Test
    void assignsNormalizedVariablesAndPreservesOrigins() {
        var evaluator = execute(
                "$source_value: 1;"
                        + "$source-value: $source_value + 1;"
                        + "$copy: $source_value;"
        );

        assertEquals(SassNumber.of(2, null), global(evaluator, "source-value"));
        assertEquals(SassNumber.of(2, null), global(evaluator, "copy"));

        var bindings = evaluator.environment().globalBindingsSnapshot();
        var source = binding(bindings, "source-value");
        var copy = binding(bindings, "copy");
        assertEquals(source.originSpan(), copy.originSpan());
        assertEquals("$source_value + 1", copy.originSpan().text());
    }

    /// Verifies guarded assignment for false, zero, Sass null, and missing values.
    @Test
    void appliesDefaultAssignmentGuards() {
        var evaluator = execute(
                "$false-value: false;"
                        + "$false-value: $missing !default;"
                        + "$zero-value: 0;"
                        + "$zero-value: $missing !default;"
                        + "$null-value: null;"
                        + "$null-value: 3 !default;"
                        + "$new-value: 4 !default;"
        );

        assertSame(SassBoolean.FALSE, global(evaluator, "false-value"));
        assertEquals(SassNumber.of(0, null), global(evaluator, "zero-value"));
        assertEquals(SassNumber.of(3, null), global(evaluator, "null-value"));
        assertEquals(SassNumber.of(4, null), global(evaluator, "new-value"));

        assertStylesheetFailure(
                "$value: null; $value: $missing !default;",
                "Undefined variable.",
                "$missing"
        );
    }

    /// Verifies lexical style scopes and explicit global assignments.
    @Test
    void isolatesStyleLocalsAndCapturesGlobals() {
        var evaluator = execute(
                "$x: global;"
                        + "$before: null;"
                        + "$after: null;"
                        + "$outside: null;"
                        + "a {"
                        + "  $before: $x !global;"
                        + "  $x: local;"
                        + "  $after: $x !global;"
                        + "}"
                        + "$outside: $x;"
        );

        assertEquals(string("global"), global(evaluator, "x"));
        assertEquals(string("global"), global(evaluator, "before"));
        assertEquals(string("local"), global(evaluator, "after"));
        assertEquals(string("global"), global(evaluator, "outside"));
    }

    /// Verifies nested rules update an existing outer local rather than a global binding.
    @Test
    void updatesOuterLocalsFromNestedRules() {
        var evaluator = execute(
                "$x: global;"
                        + "$inner-seen: null;"
                        + "$outer-after: null;"
                        + "a {"
                        + "  $x: outer;"
                        + "  b {"
                        + "    $x: inner;"
                        + "    $inner-seen: $x !global;"
                        + "  }"
                        + "  $outer-after: $x !global;"
                        + "}"
        );

        assertEquals(string("global"), global(evaluator, "x"));
        assertEquals(string("inner"), global(evaluator, "inner-seen"));
        assertEquals(string("inner"), global(evaluator, "outer-after"));
    }

    /// Verifies global writes do not replace a visible local and guards inspect that local.
    @Test
    void keepsVisibleLocalsDuringGlobalWritesAndGuards() {
        var writeEvaluator = execute(
                "$x: global;"
                        + "$captured: null;"
                        + "a {"
                        + "  $x: local;"
                        + "  $x: changed !global;"
                        + "  $captured: $x !global;"
                        + "}"
        );
        assertEquals(string("changed"), global(writeEvaluator, "x"));
        assertEquals(string("local"), global(writeEvaluator, "captured"));

        var guardEvaluator = execute(
                "$x: global;"
                        + "a {"
                        + "  $x: local;"
                        + "  $x: changed !default !global;"
                        + "}"
        );
        assertEquals(string("global"), global(guardEvaluator, "x"));
    }

    /// Verifies a null global can be shadowed by a guarded declaration in lexical scope.
    @Test
    void shadowsNullGlobalsWithDefaultLocals() {
        var evaluator = execute(
                "$x: null;"
                        + "$captured: null;"
                        + "a {"
                        + "  $x: local !default;"
                        + "  $captured: $x !global;"
                        + "}"
        );

        assertSame(SassNull.NULL, global(evaluator, "x"));
        assertEquals(string("local"), global(evaluator, "captured"));
    }

    /// Verifies new-global warnings, parser-warning order, spans, and immutable snapshots.
    @Test
    void reportsGlobalAndParseDiagnosticsInOrder() {
        var source = "$root: 1 !global !global;";
        var evaluator = execute(source);
        var diagnostics = evaluator.diagnostics();

        assertEquals(2, diagnostics.size());
        assertEquals("duplicate-var-flags", diagnostics.get(0).code());
        assertEquals("!global", diagnosticSpan(diagnostics.get(0).span()).text());
        assertEquals(DiagnosticSeverity.DEPRECATION, diagnostics.get(1).severity());
        assertEquals("new-global", diagnostics.get(1).code());
        assertEquals("$root: 1 !global !global", diagnosticSpan(diagnostics.get(1).span()).text());
        assertEquals(
                "As of Dart Sass 2.0.0, !global assignments won't be able to "
                        + "declare new variables.\n\n"
                        + "Since this assignment is at the root of the stylesheet, the !global flag is\n"
                        + "unnecessary and can safely be removed.",
                diagnostics.get(1).message()
        );
        assertThrows(UnsupportedOperationException.class, diagnostics::clear);

        assertEquals(
                List.of(),
                execute("$existing: null; a { $existing: 1 !global; }").diagnostics()
        );

        var nested = execute("a { $nested: 1 !global; }").diagnostics();
        assertEquals(1, nested.size());
        assertEquals("new-global", nested.get(0).code());
        assertEquals(
                "As of Dart Sass 2.0.0, !global assignments won't be able to "
                        + "declare new variables.\n\n"
                        + "Recommendation: add `$nested: null` at the stylesheet root.",
                nested.get(0).message()
        );
    }

    /// Verifies namespace failures occur before or after right-hand evaluation as required.
    @Test
    void preservesNamespacedAssignmentFailureOrder() {
        assertStylesheetFailure("theme.$x: $missing;", "Undefined variable.", "$missing");
        assertStylesheetFailure(
                "theme.$x: 1;",
                "There is no module with the namespace \"theme\".",
                "theme.$x: 1"
        );
        assertStylesheetFailure(
                "theme.$x: $missing !default;",
                "There is no module with the namespace \"theme\".",
                "theme.$x: $missing !default"
        );
        assertExpressionFailure(
                "theme.$x",
                "There is no module with the namespace \"theme\".",
                "theme.$x"
        );
    }

    /// Verifies map values are evaluated before duplicate keys are reported.
    @Test
    void evaluatesMapValuesBeforeDuplicateChecks() {
        assertExpressionFailure(
                "(a: 1, a: $missing)",
                "Undefined variable.",
                "$missing"
        );

        var failure = assertThrows(
                EvaluationException.class,
                () -> evaluate("(1in: first, 96px: second)")
        );
        assertEquals("Duplicate key.", failure.getMessage());
        assertEquals("96px", diagnosticSpan(failure.primaryDiagnostic().span()).text());
        assertEquals(1, failure.relatedSpans().size());
        assertEquals("first key", failure.relatedSpans().get(0).label());
        assertEquals("1in", failure.relatedSpans().get(0).span().text());

        var arithmeticFailure = assertThrows(
                EvaluationException.class,
                () -> evaluate("(1 + 2px: first, 3px: second)")
        );
        assertEquals("Duplicate key.", arithmeticFailure.getMessage());
        assertEquals(
                "3px",
                diagnosticSpan(arithmeticFailure.primaryDiagnostic().span()).text()
        );
    }

    /// Verifies successful maps retain source order and canonical semantic values.
    @Test
    void evaluatesMapEntriesInSourceOrder() {
        var map = assertInstanceOf(SassMap.class, evaluate("(first: 1, second: 2)"));

        assertEquals(
                List.of(string("first"), string("second")),
                List.copyOf(map.contents().keySet())
        );
        assertEquals(SassNumber.of(1, null), map.contents().get(string("first")));
        assertEquals(SassNumber.of(2, null), map.contents().get(string("second")));
    }

    /// Verifies global metadata is materialized only after statement execution succeeds.
    @Test
    void materializesGlobalMetadataAfterChildren() {
        var emptySource = new SourceFile("", null);
        var metadataOnly = new Stylesheet(
                List.of(),
                emptySource.span(0, 0),
                false,
                List.of(),
                Map.of("declared", emptySource.span(0, 0))
        );
        var evaluator = new SassEvaluator();
        evaluator.execute(metadataOnly);
        assertSame(SassNull.NULL, global(evaluator, "declared"));

        var declaredNull = parse("$declared: null !global;");
        var nullEvaluator = new SassEvaluator();
        nullEvaluator.execute(declaredNull);
        assertEquals(
                Objects.requireNonNull(
                        declaredNull.globalVariables().get("declared"),
                        "declared metadata"
                ),
                binding(
                        nullEvaluator.environment().globalBindingsSnapshot(),
                        "declared"
                ).originSpan()
        );

        var parsed = parse("$copy: $later;");
        var withForwardMetadata = new Stylesheet(
                parsed.children(),
                parsed.span(),
                parsed.plainCss(),
                parsed.parseTimeWarnings(),
                Map.of("later", parsed.span())
        );
        var failure = assertThrows(
                EvaluationException.class,
                () -> new SassEvaluator().execute(withForwardMetadata)
        );
        assertEquals("Undefined variable.", failure.getMessage());
        assertEquals("$later", diagnosticSpan(failure.primaryDiagnostic().span()).text());
    }

    /// Verifies unsupported callable evaluation fails explicitly at the call span.
    @Test
    void rejectsUnsupportedFunctionCalls() {
        assertExpressionFailure("fn(1)", "Function calls aren't supported.", "fn(1)");
        assertExpressionFailure(
                "f#{n}(1)",
                "Function calls aren't supported.",
                "f#{n}(1)"
        );
    }

    /// Parses and evaluates a standalone Sass expression.
    ///
    /// @param source the expression source
    /// @return the semantic value
    private static SassValue evaluate(String source) {
        return new SassEvaluator().evaluate(parseExpression(source));
    }

    /// Parses and executes a stylesheet.
    ///
    /// @param source the SCSS source
    /// @return the evaluator containing final globals and diagnostics
    private static SassEvaluator execute(String source) {
        var evaluator = new SassEvaluator();
        evaluator.execute(parse(source));
        return evaluator;
    }

    /// Parses a standalone expression from a complete source file.
    ///
    /// @param source the expression source
    /// @return the parsed expression
    private static SassExpression parseExpression(String source) {
        return new SassExpressionParser(new SourceFile(source, null)).parseExpression();
    }

    /// Parses one SCSS stylesheet.
    ///
    /// @param source the stylesheet source
    /// @return the parsed stylesheet
    private static Stylesheet parse(String source) {
        return new ScssParser(new SourceFile(source, null)).parse();
    }

    /// Returns an unquoted Sass string for expected values.
    ///
    /// @param text the string text
    /// @return the unquoted string
    private static SassString string(String text) {
        return new SassString(text, false);
    }

    /// Returns one required global value.
    ///
    /// @param evaluator the completed evaluator
    /// @param name      the normalized global name
    /// @return the global value
    private static SassValue global(SassEvaluator evaluator, String name) {
        return Objects.requireNonNull(
                evaluator.environment().globalVariablesSnapshot().get(name),
                "missing global " + name
        );
    }

    /// Returns one required binding from a snapshot.
    ///
    /// @param bindings the global binding snapshot
    /// @param name     the normalized binding name
    /// @return the binding
    private static VariableBinding binding(
            Map<String, VariableBinding> bindings,
            String name
    ) {
        return Objects.requireNonNull(bindings.get(name), "missing binding " + name);
    }

    /// Returns a required diagnostic source span.
    ///
    /// @param span the possibly absent diagnostic span
    /// @return the non-null span
    private static SourceSpan diagnosticSpan(@Nullable SourceSpan span) {
        return Objects.requireNonNull(span, "diagnostic span");
    }

    /// Verifies one expression failure's message and primary source text.
    ///
    /// @param source   the expression source
    /// @param message  the expected message
    /// @param spanText the expected primary span text
    private static void assertExpressionFailure(
            String source,
            String message,
            String spanText
    ) {
        assertFailure(
                assertThrows(EvaluationException.class, () -> evaluate(source)),
                message,
                spanText
        );
    }

    /// Verifies one stylesheet failure's message and primary source text.
    ///
    /// @param source   the stylesheet source
    /// @param message  the expected message
    /// @param spanText the expected primary span text
    private static void assertStylesheetFailure(
            String source,
            String message,
            String spanText
    ) {
        assertFailure(
                assertThrows(EvaluationException.class, () -> execute(source)),
                message,
                spanText
        );
    }

    /// Verifies structured details shared by expression and stylesheet failures.
    ///
    /// @param failure  the failure to inspect
    /// @param message  the expected message
    /// @param spanText the expected primary span text
    private static void assertFailure(
            EvaluationException failure,
            String message,
            String spanText
    ) {
        assertEquals(message, failure.getMessage());
        assertEquals(spanText, diagnosticSpan(failure.primaryDiagnostic().span()).text());
        assertEquals(DiagnosticSeverity.ERROR, failure.primaryDiagnostic().severity());
        assertEquals(1, failure.sassTrace().size());
    }
}
