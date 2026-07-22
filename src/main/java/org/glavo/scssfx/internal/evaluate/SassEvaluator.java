// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.BinaryOperator;
import org.glavo.scssfx.internal.ast.BooleanExpression;
import org.glavo.scssfx.internal.ast.ColorExpression;
import org.glavo.scssfx.internal.ast.Declaration;
import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.FunctionExpression;
import org.glavo.scssfx.internal.ast.InterpolatedFunctionExpression;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.MapExpression;
import org.glavo.scssfx.internal.ast.NullExpression;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.SassExpressionVisitor;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SassStatementVisitor;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.ast.TextInterpolationPart;
import org.glavo.scssfx.internal.ast.UnaryOperationExpression;
import org.glavo.scssfx.internal.ast.VariableDeclaration;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.value.SassBoolean;
import org.glavo.scssfx.internal.value.SassList;
import org.glavo.scssfx.internal.value.SassMap;
import org.glavo.scssfx.internal.value.SassNull;
import org.glavo.scssfx.internal.value.SassNumber;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// Evaluates Sass AST expressions and variable-bearing statements.
///
/// One evaluator represents one compilation environment. It may evaluate
/// standalone expressions and execute at most one stylesheet. Statement
/// execution establishes variable semantics and validates and evaluates
/// declaration expressions without producing CSS output.
@ApiStatus.Internal
@NotNullByDefault
public final class SassEvaluator implements
        SassExpressionVisitor<SassValue>,
        SassStatementVisitor<StatementResult> {
    /// Contains the stable deprecation identifier for new global variables.
    private static final String NEW_GLOBAL_CODE = "new-global";

    /// Contains the stable deprecation identifier for slash division.
    private static final String SLASH_DIV_CODE = "slash-div";

    /// Contains variable bindings for this evaluation.
    private final Environment environment;

    /// Contains parse-time and evaluation-time diagnostics in reporting order.
    private final ArrayList<Diagnostic> diagnostics;

    /// Contains the stylesheet being executed, or {@code null} before execution.
    private @Nullable Stylesheet stylesheet;

    /// Records whether a stylesheet execution has already begun.
    private boolean stylesheetStarted;

    /// Contains the number of active style-rule ancestors.
    private int styleRuleDepth;

    /// Contains the number of active nested-property declarations.
    private int nestedDeclarationDepth;

    /// Creates an evaluator with an empty environment.
    public SassEvaluator() {
        this(new Environment());
    }

    /// Creates an evaluator that uses an existing environment.
    ///
    /// The environment is used directly rather than copied, allowing callers
    /// to inspect bindings and to prepare controlled evaluation state.
    ///
    /// @param environment the mutable evaluation environment
    public SassEvaluator(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.diagnostics = new ArrayList<>();
    }

    /// Returns the environment used by this evaluator.
    ///
    /// @return the mutable evaluation environment
    public Environment environment() {
        return environment;
    }

    /// Returns an immutable snapshot of diagnostics reported so far.
    ///
    /// The snapshot remains unchanged if evaluation later reports additional
    /// diagnostics.
    ///
    /// @return diagnostics in reporting order
    public @Unmodifiable List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    /// Evaluates one expression in the current environment.
    ///
    /// @param expression the expression to evaluate
    /// @return the immutable semantic value
    /// @throws EvaluationException if evaluation fails
    public SassValue evaluate(SassExpression expression) {
        return Objects.requireNonNull(expression, "expression").accept(this);
    }

    /// Executes one stylesheet in source order.
    ///
    /// @param stylesheet the stylesheet to execute
    /// @throws IllegalStateException if stylesheet execution was already started
    /// @throws EvaluationException   if evaluation fails
    public void execute(Stylesheet stylesheet) {
        Objects.requireNonNull(stylesheet, "stylesheet").accept(this);
    }

    /// Executes a stylesheet root and completes declared global metadata afterward.
    ///
    /// @param statement the stylesheet to execute
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitStylesheet(Stylesheet statement) {
        if (stylesheetStarted) {
            throw new IllegalStateException("an evaluator can execute only one stylesheet");
        }
        stylesheetStarted = true;
        stylesheet = statement;
        diagnostics.addAll(statement.parseTimeWarnings());

        for (var child : statement.children()) {
            child.accept(this);
        }

        // Global declarations discovered during parsing are materialized only
        // after execution, preserving forward-reference and warning order.
        for (var entry : statement.globalVariables().entrySet()) {
            @Nullable SassValue value = environment.getVariable(entry.getKey(), null);
            if (value == null || value == SassNull.NULL) {
                environment.setVariable(
                        entry.getKey(),
                        SassNull.NULL,
                        entry.getValue(),
                        null,
                        true
                );
            }
        }
        return StatementResult.CONTINUE;
    }

    /// Evaluates a selector before executing its children in lexical scope.
    ///
    /// @param statement the style rule
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitStyleRule(StyleRule statement) {
        if (nestedDeclarationDepth > 0) {
            throw new EvaluationException(
                    "Style rules may not be used within nested declarations.",
                    statement.span()
            );
        }
        performInterpolation(statement.selector());

        styleRuleDepth++;
        var scope = environment.scope(
                ScopeSemantics.LEXICAL,
                hasDirectDeclarations(statement.children())
        );
        try {
            for (var child : statement.children()) {
                child.accept(this);
            }
        } finally {
            try {
                scope.close();
            } finally {
                styleRuleDepth--;
            }
        }
        return StatementResult.CONTINUE;
    }

    /// Evaluates a property name and value before its nested-property scope.
    ///
    /// @param statement the property declaration
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitDeclaration(Declaration statement) {
        if (styleRuleDepth == 0) {
            throw new EvaluationException(
                    "Declarations may only be used within style rules.",
                    statement.span()
            );
        }
        if (nestedDeclarationDepth > 0 && !statement.parsedAsSassScript()) {
            throw new EvaluationException(
                    statement.name().initialPlain().startsWith("--")
                            ? "Declarations whose names begin with \"--\" may not be nested."
                            : "Declarations parsed as raw CSS may not be nested.",
                    statement.span()
            );
        }

        performInterpolation(statement.name());
        @Nullable SassExpression value = statement.value();
        if (value != null) {
            evaluate(value);
        }

        @Nullable List<SassStatement> children = statement.children();
        if (children != null) {
            nestedDeclarationDepth++;
            var scope = environment.scope(
                    ScopeSemantics.LEXICAL,
                    hasDirectDeclarations(children)
            );
            try {
                for (var child : children) {
                    child.accept(this);
                }
            } finally {
                try {
                    scope.close();
                } finally {
                    nestedDeclarationDepth--;
                }
            }
        }
        return StatementResult.CONTINUE;
    }

    /// Executes a variable declaration with guarded and global assignment semantics.
    ///
    /// @param statement the variable declaration
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitVariableDeclaration(VariableDeclaration statement) {
        if (statement.isGuarded()) {
            @Nullable VariableBinding existing = nullableValueOperation(
                    statement.span(),
                    () -> environment.findVariable(statement.name(), statement.namespace())
            );
            if (existing != null && existing.value() != SassNull.NULL) {
                return StatementResult.CONTINUE;
            }
        }

        if (statement.isGlobal() && !valueOperation(
                statement.span(),
                () -> environment.globalVariableExists(statement.name(), null)
        )) {
            diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.DEPRECATION,
                    newGlobalMessage(statement),
                    statement.span(),
                    NEW_GLOBAL_CODE
            ));
        }

        var value = evaluate(statement.expression()).withoutSlash();
        var originSpan = expressionOrigin(statement.expression());
        valueOperation(statement.span(), () -> {
            environment.setVariable(
                    statement.name(),
                    value,
                    originSpan,
                    statement.namespace(),
                    statement.isGlobal()
            );
            return StatementResult.CONTINUE;
        });
        return StatementResult.CONTINUE;
    }

    /// Ignores a silent comment because it produces no semantic value.
    ///
    /// @param statement the silent comment
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitSilentComment(SilentComment statement) {
        Objects.requireNonNull(statement, "statement");
        return StatementResult.CONTINUE;
    }

    /// Evaluates interpolation embedded in a loud comment.
    ///
    /// @param statement the loud comment
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitLoudComment(LoudComment statement) {
        performInterpolation(statement.text());
        return StatementResult.CONTINUE;
    }

    /// Evaluates a string and its embedded expressions.
    ///
    /// @param expression the string expression
    /// @return the semantic Sass string
    @Override
    public SassString visitStringExpression(StringExpression expression) {
        return new SassString(performInterpolation(expression.text()), expression.hasQuotes());
    }

    /// Evaluates a number literal.
    ///
    /// @param expression the number expression
    /// @return the semantic Sass number
    @Override
    public SassNumber visitNumberExpression(NumberExpression expression) {
        return SassNumber.of(expression.value(), expression.unit());
    }

    /// Evaluates a boolean literal.
    ///
    /// @param expression the boolean expression
    /// @return the boolean singleton
    @Override
    public SassBoolean visitBooleanExpression(BooleanExpression expression) {
        return SassBoolean.of(expression.value());
    }

    /// Evaluates a null literal.
    ///
    /// @param expression the null expression
    /// @return the Sass null singleton
    @Override
    public SassNull visitNullExpression(NullExpression expression) {
        Objects.requireNonNull(expression, "expression");
        return SassNull.NULL;
    }

    /// Resolves a variable from the current lexical or module environment.
    ///
    /// @param expression the variable reference
    /// @return the bound value
    /// @throws EvaluationException if the variable or namespace is undefined
    @Override
    public SassValue visitVariableExpression(VariableExpression expression) {
        @Nullable VariableBinding binding = nullableValueOperation(
                expression.span(),
                () -> environment.findVariable(expression.name(), expression.namespace())
        );
        if (binding == null) {
            throw new EvaluationException("Undefined variable.", expression.span());
        }
        return binding.value();
    }

    /// Evaluates a parenthesized expression after enforcing plain-CSS restrictions.
    ///
    /// @param expression the parenthesized expression
    /// @return the inner value
    @Override
    public SassValue visitParenthesizedExpression(ParenthesizedExpression expression) {
        if (isPlainCss()) {
            throw new EvaluationException(
                    "Parentheses aren't allowed in plain CSS.",
                    expression.span()
            );
        }
        return evaluate(expression.expression());
    }

    /// Evaluates a unary operation after its operand.
    ///
    /// @param expression the unary operation
    /// @return the operation result
    @Override
    public SassValue visitUnaryOperationExpression(UnaryOperationExpression expression) {
        var operand = evaluate(expression.operand());
        return valueOperation(expression.span(), () -> switch (expression.operator()) {
            case PLUS -> operand.unaryPlus();
            case MINUS -> operand.unaryMinus();
            case DIVIDE -> operand.unaryDivide();
            case NOT -> operand.unaryNot();
        });
    }

    /// Evaluates a binary operation with Sass short-circuit behavior.
    ///
    /// @param expression the binary operation
    /// @return the operation result
    @Override
    public SassValue visitBinaryOperationExpression(BinaryOperationExpression expression) {
        if (isPlainCss()
                && expression.operator() != BinaryOperator.SINGLE_EQUALS
                && expression.operator() != BinaryOperator.DIVIDED_BY) {
            throw new EvaluationException(
                    "Operators aren't allowed in plain CSS.",
                    expression.operatorSpan()
            );
        }

        var left = evaluate(expression.left());
        if (expression.operator() == BinaryOperator.OR) {
            return left.isTruthy() ? left : evaluate(expression.right());
        }
        if (expression.operator() == BinaryOperator.AND) {
            return left.isTruthy() ? evaluate(expression.right()) : left;
        }

        var right = evaluate(expression.right());
        return switch (expression.operator()) {
            case SINGLE_EQUALS -> valueOperation(
                    expression.span(),
                    () -> left.singleEquals(right)
            );
            case EQUALS -> SassBoolean.of(left.equals(right));
            case NOT_EQUALS -> SassBoolean.of(!left.equals(right));
            case GREATER_THAN -> valueOperation(
                    expression.span(),
                    () -> left.greaterThan(right)
            );
            case GREATER_THAN_OR_EQUALS -> valueOperation(
                    expression.span(),
                    () -> left.greaterThanOrEquals(right)
            );
            case LESS_THAN -> valueOperation(
                    expression.span(),
                    () -> left.lessThan(right)
            );
            case LESS_THAN_OR_EQUALS -> valueOperation(
                    expression.span(),
                    () -> left.lessThanOrEquals(right)
            );
            case PLUS -> valueOperation(expression.span(), () -> left.plus(right));
            case MINUS -> valueOperation(expression.span(), () -> left.minus(right));
            case TIMES -> valueOperation(expression.span(), () -> left.times(right));
            case DIVIDED_BY -> divide(left, right, expression);
            case MODULO -> valueOperation(expression.span(), () -> left.modulo(right));
            case OR, AND -> throw new AssertionError("short-circuit operators handled above");
        };
    }

    /// Evaluates list elements in source order.
    ///
    /// @param expression the list expression
    /// @return the immutable semantic list
    @Override
    public SassList visitListExpression(ListExpression expression) {
        var contents = new ArrayList<SassValue>(expression.contents().size());
        for (var element : expression.contents()) {
            contents.add(evaluate(element));
        }
        return new SassList(contents, expression.separator(), expression.hasBrackets());
    }

    /// Rejects static function calls because this evaluator has no callable environment.
    ///
    /// @param expression the function expression
    /// @return no value
    /// @throws EvaluationException always
    @Override
    public SassValue visitFunctionExpression(FunctionExpression expression) {
        throw new EvaluationException("Function calls aren't supported.", expression.span());
    }

    /// Rejects interpolated function calls because this evaluator has no callable environment.
    ///
    /// @param expression the interpolated function expression
    /// @return no value
    /// @throws EvaluationException always
    @Override
    public SassValue visitInterpolatedFunctionExpression(
            InterpolatedFunctionExpression expression
    ) {
        throw new EvaluationException("Function calls aren't supported.", expression.span());
    }

    /// Evaluates map entries and rejects duplicate semantic keys.
    ///
    /// Each value is evaluated before its key is checked for duplication,
    /// matching Sass error ordering.
    ///
    /// @param expression the map expression
    /// @return the immutable insertion-ordered Sass map
    @Override
    public SassMap visitMapExpression(MapExpression expression) {
        var contents = new LinkedHashMap<SassValue, SassValue>(expression.pairs().size());
        var keySpans = new LinkedHashMap<SassValue, SourceSpan>(expression.pairs().size());
        for (var pair : expression.pairs()) {
            var key = evaluate(pair.key());
            var value = evaluate(pair.value());
            if (contents.containsKey(key)) {
                var firstKeySpan = Objects.requireNonNull(keySpans.get(key), "first key span");
                throw new EvaluationException(
                        "Duplicate key.",
                        pair.key().span(),
                        List.of(new RelatedSpan(firstKeySpan, "first key")),
                        null
                );
            }
            contents.put(key, value);
            keySpans.put(key, pair.key().span());
        }
        return new SassMap(contents);
    }

    /// Returns the color literal's existing semantic value.
    ///
    /// @param expression the color expression
    /// @return the parsed color
    @Override
    public SassValue visitColorExpression(ColorExpression expression) {
        return expression.value();
    }

    /// Evaluates one division and applies slash-presentation compatibility rules.
    ///
    /// @param left       the evaluated left operand
    /// @param right      the evaluated right operand
    /// @param expression the division expression
    /// @return the division result
    private SassValue divide(
            SassValue left,
            SassValue right,
            BinaryOperationExpression expression
    ) {
        var result = valueOperation(expression.span(), () -> left.dividedBy(right));
        if (!(left instanceof SassNumber leftNumber)
                || !(right instanceof SassNumber rightNumber)) {
            return result;
        }
        if (expression.allowsSlash()
                && operandAllowsSlash(expression.left())
                && operandAllowsSlash(expression.right())) {
            if (!(result instanceof SassNumber number)) {
                throw new IllegalStateException("number division did not produce a number");
            }
            return number.withSlash(leftNumber, rightNumber);
        }

        diagnostics.add(new Diagnostic(
                DiagnosticSeverity.DEPRECATION,
                "Using / for division outside of calc() is deprecated and will be removed "
                        + "in Dart Sass 2.0.0.\n\n"
                        + "Recommendation: math.div(" + expression.left() + ", "
                        + expression.right() + ") or calc(" + expression + ")\n\n"
                        + "More info and automated migrator: https://sass-lang.com/d/slash-div",
                expression.span(),
                SLASH_DIV_CODE
        ));
        return result;
    }

    /// Returns whether an operand may participate in slash-number presentation.
    ///
    /// @param expression the unevaluated operand
    /// @return whether slash presentation is permitted
    private static boolean operandAllowsSlash(SassExpression expression) {
        return !(expression instanceof FunctionExpression)
                && !(expression instanceof InterpolatedFunctionExpression);
    }

    /// Evaluates interpolation parts to their unquoted textual representation.
    ///
    /// @param interpolation the interpolation to evaluate
    /// @return the concatenated text
    private String performInterpolation(Interpolation interpolation) {
        var result = new StringBuilder();
        for (var part : interpolation.parts()) {
            if (part instanceof TextInterpolationPart text) {
                result.append(text.text());
                continue;
            }
            var expression = ((ExpressionInterpolationPart) part).expression();
            var value = evaluate(expression);
            if (value instanceof SassString string) {
                result.append(string.text());
            } else {
                result.append(valueOperation(
                        expression.span(),
                        () -> value.toCssString(false)
                ));
            }
        }
        return result.toString();
    }

    /// Returns the source span from which an expression's stored value originated.
    ///
    /// A bare variable reference propagates the binding's original source.
    /// Other expression forms use their own span.
    ///
    /// @param expression the value-producing expression
    /// @return the origin span
    private SourceSpan expressionOrigin(SassExpression expression) {
        if (expression instanceof VariableExpression variable) {
            @Nullable VariableBinding binding = nullableValueOperation(
                    variable.span(),
                    () -> environment.findVariable(variable.name(), variable.namespace())
            );
            if (binding != null) {
                return binding.originSpan();
            }
        }
        return expression.span();
    }

    /// Returns whether direct children require a lexical declaration frame.
    ///
    /// @param statements the direct children
    /// @return whether a variable declaration is present
    private static boolean hasDirectDeclarations(List<SassStatement> statements) {
        return statements.stream().anyMatch(VariableDeclaration.class::isInstance);
    }

    /// Returns the exact `new-global` deprecation message for a declaration.
    ///
    /// @param declaration the global declaration
    /// @return the root or nested recommendation
    private String newGlobalMessage(VariableDeclaration declaration) {
        var prefix = "As of Dart Sass 2.0.0, !global assignments won't be able to "
                + "declare new variables.\n\n";
        return environment.atRoot()
                ? prefix
                + "Since this assignment is at the root of the stylesheet, the !global flag is\n"
                + "unnecessary and can safely be removed."
                : prefix + "Recommendation: add `" + declaration.originalName()
                + ": null` at the stylesheet root.";
    }

    /// Returns whether expression restrictions use plain-CSS mode.
    ///
    /// @return whether an active stylesheet was parsed as plain CSS
    private boolean isPlainCss() {
        return stylesheet != null && stylesheet.plainCss();
    }

    /// Runs a span-free value operation and attaches source information on failure.
    ///
    /// @param span      the source range invoking the operation
    /// @param operation the value operation
    /// @param <T>       the result type
    /// @return the operation result
    /// @throws EvaluationException if the value layer rejects the operation
    private static <T> T valueOperation(SourceSpan span, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "value failure message"),
                    span,
                    List.of(),
                    cause
            );
        }
    }

    /// Runs a nullable span-free value operation and attaches source information on failure.
    ///
    /// @param span      the source range invoking the operation
    /// @param operation the nullable value operation
    /// @param <T>       the non-null result component type
    /// @return the operation result, or {@code null}
    /// @throws EvaluationException if the value layer rejects the operation
    private static <T> @Nullable T nullableValueOperation(
            SourceSpan span,
            Supplier<@Nullable T> operation
    ) {
        try {
            return operation.get();
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "value failure message"),
                    span,
                    List.of(),
                    cause
            );
        }
    }
}
