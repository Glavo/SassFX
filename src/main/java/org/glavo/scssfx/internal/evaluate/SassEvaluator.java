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
import org.glavo.scssfx.internal.ast.EachRule;
import org.glavo.scssfx.internal.ast.ElseClause;
import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.ForRule;
import org.glavo.scssfx.internal.ast.ArgumentList;
import org.glavo.scssfx.internal.ast.ContentRule;
import org.glavo.scssfx.internal.ast.FunctionExpression;
import org.glavo.scssfx.internal.ast.FunctionRule;
import org.glavo.scssfx.internal.ast.IfRule;
import org.glavo.scssfx.internal.ast.IncludeRule;
import org.glavo.scssfx.internal.ast.InterpolatedFunctionExpression;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.LegacyIfExpression;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.MapExpression;
import org.glavo.scssfx.internal.ast.MixinRule;
import org.glavo.scssfx.internal.ast.NullExpression;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParameterList;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.ReturnRule;
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
import org.glavo.scssfx.internal.ast.WhileRule;
import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.callable.Callable;
import org.glavo.scssfx.internal.callable.PlainCssCallable;
import org.glavo.scssfx.internal.callable.UserDefinedCallable;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.css.CssComment;
import org.glavo.scssfx.internal.function.BuiltInFunctions;
import org.glavo.scssfx.internal.css.CssDeclaration;
import org.glavo.scssfx.internal.css.CssNode;
import org.glavo.scssfx.internal.css.CssParentNode;
import org.glavo.scssfx.internal.css.CssStyleRule;
import org.glavo.scssfx.internal.css.CssStylesheet;
import org.glavo.scssfx.internal.css.CssValue;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassArgumentList;
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/// Evaluates Sass AST expressions and statements into CSS IR.
///
/// One evaluator represents one compilation environment. It may evaluate
/// standalone expressions and execute at most one stylesheet. Statement
/// execution establishes variable semantics and writes the supported statement
/// subset into an internal [CssStylesheet].
@ApiStatus.Internal
@NotNullByDefault
public final class SassEvaluator implements
        SassExpressionVisitor<SassValue>,
        SassStatementVisitor<StatementResult> {
    /// Contains the stable deprecation identifier for new global variables.
    private static final String NEW_GLOBAL_CODE = "new-global";

    /// Contains the stable deprecation identifier for slash division.
    private static final String SLASH_DIV_CODE = "slash-div";

    /// Contains the global built-in function table.
    private static final Map<String, BuiltInCallable> BUILT_IN_FUNCTIONS =
            BuiltInFunctions.global();

    /// Contains variable and callable bindings for this evaluation.
    private Environment environment;

    /// Contains parse-time and evaluation-time diagnostics in reporting order.
    private final ArrayList<Diagnostic> diagnostics;

    /// Contains the stylesheet being executed, or {@code null} before execution.
    private @Nullable Stylesheet stylesheet;

    /// Contains the CSS IR root produced by stylesheet execution.
    private @Nullable CssStylesheet cssStylesheet;

    /// Contains the CSS parent that currently receives children.
    private @Nullable CssParentNode cssParent;

    /// Contains the innermost active style rule, or {@code null} outside rules.
    private @Nullable CssStyleRule styleRule;

    /// Contains the property-name prefix for nested declarations, or {@code null}.
    private @Nullable String declarationName;

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

    /// Returns the CSS IR root produced by stylesheet execution.
    ///
    /// @return the CSS stylesheet, or {@code null} before execution completes
    public @Nullable CssStylesheet cssStylesheet() {
        return cssStylesheet;
    }

    /// Evaluates one expression in the current environment.
    ///
    /// @param expression the expression to evaluate
    /// @return the immutable semantic value
    /// @throws EvaluationException if evaluation fails
    public SassValue evaluate(SassExpression expression) {
        return Objects.requireNonNull(expression, "expression").accept(this);
    }

    /// Executes one stylesheet in source order and builds CSS IR.
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
        cssStylesheet = new CssStylesheet(statement.span());
        cssParent = cssStylesheet;
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

    /// Evaluates a selector, emits a CSS style rule, and executes its children.
    ///
    /// Nested style rules bubble through existing style-rule parents so the CSS
    /// IR remains a flat sequence of resolved rules under the stylesheet root.
    ///
    /// @param statement the style rule
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitStyleRule(StyleRule statement) {
        if (nestedDeclarationDepth > 0 || declarationName != null) {
            throw new EvaluationException(
                    "Style rules may not be used within nested declarations.",
                    statement.span()
            );
        }

        var selectorText = performInterpolation(statement.selector());
        SelectorList parsed;
        try {
            parsed = SelectorList.parse(selectorText, statement.selector().span());
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "selector failure message"),
                    statement.selector().span(),
                    List.of(),
                    cause
            );
        }
        @Nullable SelectorList parentSelector =
                styleRule == null ? null : styleRule.selector().value();
        SelectorList nestedSelector;
        try {
            nestedSelector = parsed.nestWithin(parentSelector);
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "selector failure message"),
                    statement.selector().span(),
                    List.of(),
                    cause
            );
        }
        var rule = new CssStyleRule(
                new CssValue<>(nestedSelector, statement.selector().span()),
                statement.span()
        );
        addCssChild(rule, true);

        var previousParent = requireCssParent();
        var previousStyleRule = styleRule;
        cssParent = rule;
        styleRule = rule;
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
                styleRule = previousStyleRule;
                cssParent = previousParent;
            }
        }

        if (previousStyleRule == null && !previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
        }
        return StatementResult.CONTINUE;
    }

    /// Evaluates a property name and value and writes a CSS declaration.
    ///
    /// Nested property blocks prefix child names with the parent name. Blank
    /// values are omitted unless they are empty lists or custom properties.
    ///
    /// @param statement the property declaration
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitDeclaration(Declaration statement) {
        if (styleRule == null) {
            throw new EvaluationException(
                    "Declarations may only be used within style rules.",
                    statement.span()
            );
        }
        if (declarationName != null && !statement.parsedAsSassScript()) {
            throw new EvaluationException(
                    statement.name().initialPlain().startsWith("--")
                            ? "Declarations whose names begin with \"--\" may not be nested."
                            : "Declarations parsed as raw CSS may not be nested.",
                    statement.span()
            );
        }

        var resolvedName = performInterpolation(statement.name());
        if (declarationName != null) {
            resolvedName = declarationName + "-" + resolvedName;
        }
        var name = new CssValue<>(resolvedName, statement.name().span());

        @Nullable SassExpression valueExpression = statement.value();
        if (valueExpression != null) {
            var value = evaluate(valueExpression);
            if (!value.isBlank()
                    || isEmptyList(value)
                    || name.value().startsWith("--")) {
                copyParentAfterSibling();
                requireCssParent().addChild(new CssDeclaration(
                        name,
                        new CssValue<>(value, valueExpression.span()),
                        statement.span(),
                        statement.parsedAsSassScript()
                ));
            }
        }

        @Nullable List<SassStatement> children = statement.children();
        if (children != null) {
            var previousDeclarationName = declarationName;
            declarationName = name.value();
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
                    declarationName = previousDeclarationName;
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

    /// Evaluates interpolation embedded in a loud comment and emits CSS IR.
    ///
    /// @param statement the loud comment
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitLoudComment(LoudComment statement) {
        var text = performInterpolation(statement.text());
        if (!text.endsWith("*/")) {
            text += " */";
        }
        copyParentAfterSibling();
        requireCssParent().addChild(new CssComment(text, statement.span()));
        return StatementResult.CONTINUE;
    }

    /// Executes the first truthy `@if`/`@else if` branch, or the `@else` branch.
    ///
    /// @param statement the if rule
    /// @return the statement result, which may carry a function return
    @Override
    public StatementResult visitIfRule(IfRule statement) {
        @Nullable List<SassStatement> children = null;
        for (var clause : statement.clauses()) {
            if (evaluate(clause.expression()).isTruthy()) {
                children = clause.children();
                break;
            }
        }
        if (children == null) {
            @Nullable ElseClause lastClause = statement.lastClause();
            if (lastClause == null) {
                return StatementResult.CONTINUE;
            }
            children = lastClause.children();
        }
        return executeInFlowScope(children, hasDirectDeclarations(children));
    }

    /// Iterates over a list view, binding one or more local loop variables.
    ///
    /// @param statement the each rule
    /// @return the statement result, which may carry a function return
    @Override
    public StatementResult visitEachRule(EachRule statement) {
        var listValue = evaluate(statement.list());
        var origin = expressionOrigin(statement.list());
        return inFlowScope(true, () -> {
            for (var element : listValue.asList()) {
                if (statement.variables().size() == 1) {
                    environment.setLocalVariable(
                            statement.variables().get(0),
                            element.withoutSlash(),
                            origin
                    );
                } else {
                    setMultipleVariables(statement.variables(), element, origin);
                }
                var result = executeChildren(statement.children());
                if (result instanceof StatementResult.ReturnValue) {
                    return result;
                }
            }
            return StatementResult.CONTINUE;
        });
    }

    /// Iterates an integer index from one bound toward another.
    ///
    /// @param statement the for rule
    /// @return the statement result, which may carry a function return
    @Override
    public StatementResult visitForRule(ForRule statement) {
        var fromNumber = valueOperation(
                statement.from().span(),
                () -> evaluate(statement.from()).assertNumber()
        );
        var toNumber = valueOperation(
                statement.to().span(),
                () -> evaluate(statement.to()).assertNumber()
        );
        var fromInt = valueOperation(statement.from().span(), fromNumber::assertInt);
        var toCoerced = valueOperation(
                statement.to().span(),
                () -> toNumber.coerce(fromNumber.numeratorUnits(), fromNumber.denominatorUnits())
        );
        var toInt = valueOperation(statement.to().span(), toCoerced::assertInt);
        var direction = fromInt > toInt ? -1 : 1;
        if (!statement.exclusive()) {
            toInt += direction;
        }
        if (fromInt == toInt) {
            return StatementResult.CONTINUE;
        }

        var origin = expressionOrigin(statement.from());
        var end = toInt;
        return inFlowScope(true, () -> {
            for (var index = fromInt; index != end; index += direction) {
                environment.setLocalVariable(
                        statement.variable(),
                        SassNumber.withUnits(
                                index,
                                fromNumber.numeratorUnits(),
                                fromNumber.denominatorUnits()
                        ),
                        origin
                );
                var result = executeChildren(statement.children());
                if (result instanceof StatementResult.ReturnValue) {
                    return result;
                }
            }
            return StatementResult.CONTINUE;
        });
    }

    /// Repeatedly executes children while the condition remains truthy.
    ///
    /// @param statement the while rule
    /// @return the statement result, which may carry a function return
    @Override
    public StatementResult visitWhileRule(WhileRule statement) {
        return inFlowScope(
                hasDirectDeclarations(statement.children()),
                () -> {
                    while (evaluate(statement.condition()).isTruthy()) {
                        var result = executeChildren(statement.children());
                        if (result instanceof StatementResult.ReturnValue) {
                            return result;
                        }
                    }
                    return StatementResult.CONTINUE;
                }
        );
    }

    /// Registers a mixin in the current environment.
    ///
    /// @param statement the mixin rule
    /// @return the continue result
    @Override
    public StatementResult visitMixinRule(MixinRule statement) {
        environment.setMixin(new UserDefinedCallable(
                statement.name(),
                statement.parameters(),
                statement.children(),
                environment.closure(),
                statement.span(),
                statement.hasContent()
        ));
        return StatementResult.CONTINUE;
    }

    /// Registers a function in the current environment.
    ///
    /// @param statement the function rule
    /// @return the continue result
    @Override
    public StatementResult visitFunctionRule(FunctionRule statement) {
        environment.setFunction(new UserDefinedCallable(
                statement.name(),
                statement.parameters(),
                statement.children(),
                environment.closure(),
                statement.span(),
                false
        ));
        return StatementResult.CONTINUE;
    }

    /// Includes a previously declared mixin.
    ///
    /// @param statement the include rule
    /// @return the statement result
    @Override
    public StatementResult visitIncludeRule(IncludeRule statement) {
        @Nullable Callable mixin;
        try {
            mixin = environment.getMixin(statement.name(), statement.namespace());
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "value failure message"),
                    statement.span(),
                    List.of(),
                    cause
            );
        }
        if (!(mixin instanceof UserDefinedCallable userMixin)) {
            throw new EvaluationException("Undefined mixin.", statement.span());
        }
        @Nullable UserDefinedCallable contentCallable = null;
        if (statement.content() != null) {
            if (!userMixin.acceptsContent()) {
                throw new EvaluationException(
                        "Mixin doesn't accept a content block.",
                        statement.span()
                );
            }
            contentCallable = new UserDefinedCallable(
                    "@content",
                    statement.content().parameters(),
                    statement.content().children(),
                    environment.closure(),
                    statement.content().span(),
                    false
            );
        }
        runUserDefinedMixin(userMixin, statement.arguments(), contentCallable, statement.span());
        return StatementResult.CONTINUE;
    }

    /// Invokes the content block supplied by the enclosing include.
    ///
    /// @param statement the content rule
    /// @return the statement result
    @Override
    public StatementResult visitContentRule(ContentRule statement) {
        @Nullable UserDefinedCallable content = environment.content();
        if (content == null) {
            return StatementResult.CONTINUE;
        }
        if (!statement.arguments().isEmpty()) {
            throw new EvaluationException(
                    "Content arguments aren't supported.",
                    statement.span()
            );
        }
        runUserDefinedMixin(content, statement.arguments(), null, statement.span());
        return StatementResult.CONTINUE;
    }

    /// Returns a value from the current user-defined function.
    ///
    /// @param statement the return rule
    /// @return the return result
    @Override
    public StatementResult visitReturnRule(ReturnRule statement) {
        return new StatementResult.ReturnValue(evaluate(statement.expression()).withoutSlash());
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

    /// Resolves and invokes a statically named function.
    ///
    /// Resolution order is user-defined, then built-in, then plain-CSS fallback.
    /// Namespaced lookups fail because this evaluator has no module registry.
    ///
    /// @param expression the function expression
    /// @return the function result
    /// @throws EvaluationException if invocation fails
    @Override
    public SassValue visitFunctionExpression(FunctionExpression expression) {
        @Nullable Callable callable;
        try {
            callable = environment.getFunction(expression.name(), expression.namespace());
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "value failure message"),
                    expression.span(),
                    List.of(),
                    cause
            );
        }
        if (callable == null && expression.namespace() == null) {
            callable = BUILT_IN_FUNCTIONS.get(expression.name());
        }
        if (callable == null && expression.namespace() == null) {
            callable = new PlainCssCallable(expression.originalName());
        }
        if (callable == null) {
            throw new EvaluationException(
                    "There is no module with the namespace \"" + expression.namespace() + "\".",
                    expression.span()
            );
        }
        return runCallable(callable, expression.arguments(), expression.span());
    }

    /// Serializes an interpolated function call as plain CSS text.
    ///
    /// @param expression the interpolated function expression
    /// @return an unquoted CSS function string
    @Override
    public SassValue visitInterpolatedFunctionExpression(
            InterpolatedFunctionExpression expression
    ) {
        var name = performInterpolation(expression.name());
        return runCallable(
                new PlainCssCallable(name),
                expression.arguments(),
                expression.span()
        );
    }

    /// Evaluates the short-circuiting legacy `if()` expression.
    ///
    /// @param expression the if expression
    /// @return the selected branch value
    @Override
    public SassValue visitLegacyIfExpression(LegacyIfExpression expression) {
        var arguments = expression.arguments();
        if (!arguments.named().isEmpty()
                || arguments.rest() != null
                || arguments.positional().size() != 3) {
            throw new EvaluationException(
                    "Only 3 positional arguments are allowed in if().",
                    expression.span()
            );
        }
        var condition = evaluate(arguments.positional().get(0));
        var selected = condition.isTruthy()
                ? arguments.positional().get(1)
                : arguments.positional().get(2);
        return evaluate(selected);
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

    /// Adds a CSS child, bubbling through style-rule parents when requested.
    ///
    /// @param node                 the child to append
    /// @param throughStyleRules whether style-rule parents should be skipped
    private void addCssChild(CssNode node, boolean throughStyleRules) {
        var parent = requireCssParent();
        if (throughStyleRules) {
            while (parent instanceof CssStyleRule) {
                @Nullable CssParentNode grandparent = parent.parent();
                if (grandparent == null) {
                    throw new IllegalStateException("style rule escaped the CSS root");
                }
                parent = grandparent;
            }
            if (parent.hasFollowingSibling()) {
                @Nullable CssParentNode grandparent = parent.parent();
                if (grandparent == null) {
                    throw new IllegalStateException("CSS parent escaped the tree");
                }
                var siblings = grandparent.children();
                var last = siblings.get(siblings.size() - 1);
                if (parent.equalsIgnoringChildren(last) && last instanceof CssParentNode lastParent) {
                    parent = lastParent;
                } else {
                    var copy = parent.copyWithoutChildren();
                    grandparent.addChild(copy);
                    parent = copy;
                }
            }
        }
        parent.addChild(node);
    }

    /// Copies the current CSS parent after a later sibling when needed.
    ///
    /// After a nested style rule bubbles beside the active rule, later
    /// declarations must attach to a fresh copy of that rule rather than reopen
    /// the earlier sibling.
    private void copyParentAfterSibling() {
        var parent = requireCssParent();
        @Nullable CssParentNode grandparent = parent.parent();
        if (grandparent == null) {
            return;
        }
        var siblings = grandparent.children();
        if (siblings.isEmpty() || siblings.get(siblings.size() - 1) == parent) {
            return;
        }
        var copy = parent.copyWithoutChildren();
        grandparent.addChild(copy);
        cssParent = copy;
        if (parent instanceof CssStyleRule && styleRule == parent) {
            styleRule = (CssStyleRule) copy;
        }
    }

    /// Returns the active CSS parent or fails if stylesheet execution has not begun.
    ///
    /// @return the current CSS parent
    private CssParentNode requireCssParent() {
        if (cssParent == null) {
            throw new IllegalStateException("CSS parent is unavailable outside stylesheet execution");
        }
        return cssParent;
    }

    /// Returns whether a value is an empty list.
    ///
    /// Empty lists are retained so serialization can report that they are not
    /// valid CSS values.
    ///
    /// @param value the evaluated value
    /// @return whether the list view is empty
    private static boolean isEmptyList(SassValue value) {
        return value.asList().isEmpty();
    }

    /// Evaluates arguments and invokes a callable.
    ///
    /// @param callable  the callable to invoke
    /// @param arguments the unevaluated argument list
    /// @param span      the invocation span
    /// @return the callable result
    private SassValue runCallable(
            Callable callable,
            ArgumentList arguments,
            SourceSpan span
    ) {
        var evaluated = evaluateArguments(arguments, span);
        if (callable instanceof BuiltInCallable builtIn) {
            return valueOperation(span, () -> {
                var bound = bindForBuiltin(builtIn, evaluated, span);
                return builtIn.invoke(bound);
            });
        }
        if (callable instanceof PlainCssCallable plainCss) {
            if (!evaluated.named().isEmpty()) {
                throw new EvaluationException(
                        "Plain CSS functions don't support keyword arguments.",
                        span
                );
            }
            return valueOperation(
                    span,
                    () -> serializePlainCss(plainCss.name(), evaluated.positional())
            );
        }
        if (callable instanceof UserDefinedCallable userDefined) {
            return runUserDefinedFunction(userDefined, evaluated, span);
        }
        throw new IllegalStateException("unsupported callable: " + callable.getClass().getName());
    }

    /// Executes a user-defined function body and returns its `@return` value.
    ///
    /// @param function  the user function
    /// @param evaluated the evaluated arguments
    /// @param span      the call span
    /// @return the returned value
    private SassValue runUserDefinedFunction(
            UserDefinedCallable function,
            EvaluatedArguments evaluated,
            SourceSpan span
    ) {
        return withEnvironment(function.environment().closure(), () -> {
            var scope = environment.scope(ScopeSemantics.LEXICAL, true);
            try {
                @Nullable SassArgumentList rest = bindParameters(
                        function.parameters(),
                        evaluated,
                        span
                );
                var result = executeChildren(function.children());
                checkUnusedKeywords(rest, span);
                if (result instanceof StatementResult.ReturnValue returned) {
                    return returned.value();
                }
                throw new EvaluationException(
                        "Function finished without @return.",
                        function.span()
                );
            } finally {
                scope.close();
            }
        });
    }

    /// Executes a user-defined mixin body.
    ///
    /// @param mixin      the mixin or content callable
    /// @param arguments  the unevaluated arguments
    /// @param content    the optional content block
    /// @param span       the include or content span
    private void runUserDefinedMixin(
            UserDefinedCallable mixin,
            ArgumentList arguments,
            @Nullable UserDefinedCallable content,
            SourceSpan span
    ) {
        var evaluated = evaluateArguments(arguments, span);
        withEnvironment(mixin.environment().closure(), () -> {
            environment.withContent(content, () -> {
                var scope = environment.scope(ScopeSemantics.LEXICAL, true);
                try {
                    @Nullable SassArgumentList rest = bindParameters(
                            mixin.parameters(),
                            evaluated,
                            span
                    );
                    executeChildren(mixin.children());
                    checkUnusedKeywords(rest, span);
                    return null;
                } finally {
                    scope.close();
                }
            });
            return null;
        });
    }

    /// Evaluates an argument invocation into positional and named values.
    ///
    /// @param arguments the unevaluated arguments
    /// @param span      the invocation span
    /// @return the evaluated arguments
    private EvaluatedArguments evaluateArguments(ArgumentList arguments, SourceSpan span) {
        var positional = new ArrayList<SassValue>();
        for (var argument : arguments.positional()) {
            positional.add(evaluate(argument).withoutSlash());
        }
        var named = new LinkedHashMap<String, SassValue>();
        for (var entry : arguments.named().entrySet()) {
            named.put(entry.getKey(), evaluate(entry.getValue()).withoutSlash());
        }
        var separator = ListSeparator.UNDECIDED;
        if (arguments.rest() != null) {
            var rest = evaluate(arguments.rest()).withoutSlash();
            if (rest instanceof SassMap map) {
                addRestMap(named, map, span);
            } else if (rest instanceof SassArgumentList argumentList) {
                positional.addAll(argumentList.asList());
                separator = argumentList.separator();
                named.putAll(argumentList.keywordsWithoutMarking());
            } else if (rest instanceof SassList list) {
                positional.addAll(list.contents());
                separator = list.separator();
            } else {
                positional.add(rest);
            }
        }
        if (arguments.keywordRest() != null) {
            var keywordRest = evaluate(arguments.keywordRest()).withoutSlash();
            if (!(keywordRest instanceof SassMap map)) {
                throw new EvaluationException(
                        "Variable keyword arguments must be a map (was " + keywordRest + ").",
                        span
                );
            }
            addRestMap(named, map, span);
        }
        return new EvaluatedArguments(positional, named, separator);
    }

    /// Merges a rest map into the named-argument table.
    private void addRestMap(
            LinkedHashMap<String, SassValue> named,
            SassMap map,
            SourceSpan span
    ) {
        for (var entry : map.contents().entrySet()) {
            if (!(entry.getKey() instanceof SassString key) || key.hasQuotes()) {
                throw new EvaluationException(
                        "Variable keyword argument map must have string keys.",
                        span
                );
            }
            var name = key.text().replace('_', '-');
            if (named.containsKey(name)) {
                throw new EvaluationException(
                        "Argument $" + name + " was passed both by position and by name.",
                        span
                );
            }
            named.put(name, entry.getValue());
        }
    }

    /// Binds evaluated arguments into the current local frame.
    ///
    /// @param parameters the declared parameters
    /// @param evaluated  the evaluated arguments
    /// @param span       the invocation span
    /// @return the rest argument list when a rest parameter exists
    private @Nullable SassArgumentList bindParameters(
            ParameterList parameters,
            EvaluatedArguments evaluated,
            SourceSpan span
    ) {
        var declared = parameters.parameters();
        var positional = new ArrayList<>(evaluated.positional());
        var named = new LinkedHashMap<>(evaluated.named());

        for (var index = 0; index < declared.size(); index++) {
            var name = declared.get(index).name();
            if (index < positional.size() && named.containsKey(name)) {
                throw new EvaluationException(
                        "Argument $" + name + " was passed both by position and by name.",
                        span
                );
            }
        }

        if (parameters.restParameter() == null && positional.size() > declared.size()) {
            throw new EvaluationException(
                    "Only " + declared.size() + " "
                            + (declared.size() == 1 ? "argument" : "arguments")
                            + " allowed, but " + positional.size() + " "
                            + (positional.size() == 1 ? "was" : "were")
                            + " passed.",
                    span
            );
        }

        for (var index = 0; index < Math.min(positional.size(), declared.size()); index++) {
            environment.setLocalVariable(declared.get(index).name(), positional.get(index), span);
        }
        for (var index = positional.size(); index < declared.size(); index++) {
            var parameter = declared.get(index);
            @Nullable SassValue namedValue = named.remove(parameter.name());
            if (namedValue != null) {
                environment.setLocalVariable(parameter.name(), namedValue, span);
                continue;
            }
            if (parameter.defaultValue() == null) {
                throw new EvaluationException(
                        "Missing argument $" + parameter.name() + ".",
                        span
                );
            }
            environment.setLocalVariable(
                    parameter.name(),
                    evaluate(parameter.defaultValue()).withoutSlash(),
                    parameter.defaultValue().span()
            );
        }

        if (parameters.restParameter() == null) {
            if (!named.isEmpty()) {
                throw unknownNamed(named.keySet(), span);
            }
            return null;
        }

        var restPositional = positional.size() > declared.size()
                ? List.copyOf(positional.subList(declared.size(), positional.size()))
                : List.<SassValue>of();
        var separator = evaluated.separator() == ListSeparator.UNDECIDED
                ? ListSeparator.COMMA
                : evaluated.separator();
        var rest = new SassArgumentList(restPositional, separator, named);
        environment.setLocalVariable(parameters.restParameter(), rest, span);
        return rest;
    }

    /// Converts evaluated arguments into a positional list for a built-in callable.
    private List<SassValue> bindForBuiltin(
            BuiltInCallable builtIn,
            EvaluatedArguments evaluated,
            SourceSpan span
    ) {
        var params = builtIn.parameters();
        var positional = new ArrayList<>(evaluated.positional());
        var named = new LinkedHashMap<>(evaluated.named());
        var bound = new ArrayList<SassValue>();

        for (var index = 0; index < params.size(); index++) {
            var param = params.get(index);
            if (index < positional.size()) {
                if (named.containsKey(param.name())) {
                    throw new EvaluationException(
                            "Argument $" + param.name()
                                    + " was passed both by position and by name.",
                            span
                    );
                }
                bound.add(positional.get(index));
                continue;
            }
            @Nullable SassValue namedValue = named.remove(param.name());
            if (namedValue != null) {
                bound.add(namedValue);
                continue;
            }
            if (param.defaultValue() != null) {
                bound.add(param.defaultValue());
                continue;
            }
            if (builtIn.restParameter() == null && index >= builtIn.minArgs()) {
                break;
            }
            throw new EvaluationException("Missing argument $" + param.name() + ".", span);
        }

        if (builtIn.restParameter() == null) {
            if (positional.size() > params.size()) {
                throw new EvaluationException(
                        "Only " + params.size() + " "
                                + (params.size() == 1 ? "argument" : "arguments")
                                + " allowed, but " + positional.size() + " "
                                + (positional.size() == 1 ? "was" : "were")
                                + " passed.",
                        span
                );
            }
            if (!named.isEmpty()) {
                throw unknownNamed(named.keySet(), span);
            }
            return bound;
        }

        var restPositional = positional.size() > params.size()
                ? List.copyOf(positional.subList(params.size(), positional.size()))
                : List.<SassValue>of();
        var separator = evaluated.separator() == ListSeparator.UNDECIDED
                ? ListSeparator.COMMA
                : evaluated.separator();
        var rest = new SassArgumentList(restPositional, separator, named);
        bound.add(rest);
        // Built-ins that accept rest are assumed to consume keywords by reading
        // the argument list as a plain list; unused named keywords error here.
        if (!named.isEmpty()) {
            throw unknownNamed(named.keySet(), span);
        }
        return bound;
    }

    /// Throws when leftover named arguments remain.
    private static EvaluationException unknownNamed(
            java.util.Set<String> names,
            SourceSpan span
    ) {
        var list = names.stream().sorted().map(name -> "$" + name).toList();
        var message = list.size() == 1
                ? "No parameter named " + list.get(0) + "."
                : "No parameters named " + String.join(" or ", list) + ".";
        return new EvaluationException(message, span);
    }

    /// Reports unused keyword arguments after a rest parameter call.
    private static void checkUnusedKeywords(
            @Nullable SassArgumentList rest,
            SourceSpan span
    ) {
        if (rest == null || rest.wereKeywordsAccessed() || rest.keywordsWithoutMarking().isEmpty()) {
            return;
        }
        throw unknownNamed(rest.keywordsWithoutMarking().keySet(), span);
    }

    /// Contains evaluated invocation arguments.
    private record EvaluatedArguments(
            List<SassValue> positional,
            LinkedHashMap<String, SassValue> named,
            ListSeparator separator
    ) {
    }

    /// Runs a body with a temporary evaluation environment.
    ///
    /// @param next the environment to install
    /// @param body the body to run
    /// @param <T>  the result type
    /// @return the body result
    private <T> T withEnvironment(Environment next, Supplier<T> body) {
        var previous = environment;
        environment = next;
        try {
            return body.get();
        } finally {
            environment = previous;
        }
    }

    /// Serializes a plain-CSS function call.
    ///
    /// @param name       the function name
    /// @param positional the evaluated arguments
    /// @return an unquoted CSS function string
    /// @throws SassValueException if an argument cannot be represented in CSS
    private static SassString serializePlainCss(String name, List<SassValue> positional) {
        var result = new StringBuilder(name).append('(');
        for (var index = 0; index < positional.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(positional.get(index).toCssString());
        }
        result.append(')');
        return new SassString(result.toString(), false);
    }

    /// Executes children inside a flow-control scope.
    ///
    /// @param children    the children to execute
    /// @param createFrame whether a local frame is required
    /// @return the statement result
    private StatementResult executeInFlowScope(
            List<SassStatement> children,
            boolean createFrame
    ) {
        return inFlowScope(createFrame, () -> executeChildren(children));
    }

    /// Opens a flow-control scope, runs a body, and closes the scope.
    ///
    /// @param createFrame whether a local frame is required
    /// @param body        the body to run
    /// @return the statement result
    private StatementResult inFlowScope(
            boolean createFrame,
            Supplier<StatementResult> body
    ) {
        var scope = environment.scope(ScopeSemantics.FLOW_CONTROL, createFrame);
        try {
            return body.get();
        } finally {
            scope.close();
        }
    }

    /// Executes direct children in source order and propagates function returns.
    ///
    /// @param children the children to execute
    /// @return the statement result
    private StatementResult executeChildren(List<SassStatement> children) {
        for (var child : children) {
            var result = child.accept(this);
            if (result instanceof StatementResult.ReturnValue) {
                return result;
            }
        }
        return StatementResult.CONTINUE;
    }

    /// Destructures a list-like value into multiple local loop variables.
    ///
    /// Missing elements become Sass null. Extra elements are ignored.
    ///
    /// @param variables  the normalized local variable names
    /// @param value      the value being destructured
    /// @param originSpan the origin associated with assigned values
    private void setMultipleVariables(
            List<String> variables,
            SassValue value,
            SourceSpan originSpan
    ) {
        var elements = value.asList();
        var assigned = Math.min(variables.size(), elements.size());
        for (var index = 0; index < assigned; index++) {
            environment.setLocalVariable(
                    variables.get(index),
                    elements.get(index).withoutSlash(),
                    originSpan
            );
        }
        for (var index = assigned; index < variables.size(); index++) {
            environment.setLocalVariable(variables.get(index), SassNull.NULL, originSpan);
        }
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
    /// @return whether a variable, mixin, or function declaration is present
    private static boolean hasDirectDeclarations(List<SassStatement> statements) {
        return statements.stream().anyMatch(statement ->
                statement instanceof VariableDeclaration
                        || statement instanceof MixinRule
                        || statement instanceof FunctionRule
        );
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
