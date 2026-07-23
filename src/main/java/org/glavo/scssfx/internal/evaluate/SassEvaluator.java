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
import org.glavo.scssfx.internal.ast.DebugRule;
import org.glavo.scssfx.internal.ast.EachRule;
import org.glavo.scssfx.internal.ast.ElseClause;
import org.glavo.scssfx.internal.ast.ErrorRule;
import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.ForRule;
import org.glavo.scssfx.internal.ast.FontFaceRule;
import org.glavo.scssfx.internal.ast.MediaRule;
import org.glavo.scssfx.internal.ast.ForwardRule;
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
import org.glavo.scssfx.internal.ast.UseRule;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.module.ConfiguredValue;
import org.glavo.scssfx.internal.module.LoadedModule;
import org.glavo.scssfx.internal.module.ModuleCss;
import org.glavo.scssfx.internal.module.ModuleConfiguration;
import org.glavo.scssfx.internal.module.ModuleRegistry;
import org.glavo.scssfx.internal.ast.SassExpressionVisitor;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SassStatementVisitor;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.SupportsRule;
import org.glavo.scssfx.internal.ast.SupportsAnything;
import org.glavo.scssfx.internal.ast.SupportsBooleanOperator;
import org.glavo.scssfx.internal.ast.SupportsCondition;
import org.glavo.scssfx.internal.ast.SupportsDeclaration;
import org.glavo.scssfx.internal.ast.SupportsFunction;
import org.glavo.scssfx.internal.ast.SupportsInterpolation;
import org.glavo.scssfx.internal.ast.SupportsNegation;
import org.glavo.scssfx.internal.ast.SupportsOperation;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.ast.TextInterpolationPart;
import org.glavo.scssfx.internal.ast.UnaryOperationExpression;
import org.glavo.scssfx.internal.ast.VariableDeclaration;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.ast.WarnRule;
import org.glavo.scssfx.internal.ast.WhileRule;
import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.callable.Callable;
import org.glavo.scssfx.internal.callable.PlainCssCallable;
import org.glavo.scssfx.internal.callable.UserDefinedCallable;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.css.CssComment;
import org.glavo.scssfx.internal.css.CssFontFace;
import org.glavo.scssfx.internal.css.CssMediaQuery;
import org.glavo.scssfx.internal.css.CssMediaRule;
import org.glavo.scssfx.internal.css.CssSupportsRule;
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
import org.glavo.scssfx.internal.value.SassFunction;
import org.glavo.scssfx.internal.value.SassMixin;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final @Unmodifiable Map<String, BuiltInCallable> BUILT_IN_FUNCTIONS =
            BuiltInFunctions.global();

    /// Identifies function references created during this compilation.
    private final Object compilationContext;

    /// Contains variable and callable bindings for this evaluation.
    private Environment environment;

    /// Contains the module loader for this compilation, or {@code null}.
    private final @Nullable ModuleRegistry moduleRegistry;

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

    /// Contains the active font-face rule, or {@code null} outside one.
    private @Nullable CssFontFace fontFace;

    /// Contains the evaluated media-query list active for nested media rules, or {@code null}.
    private @Nullable @Unmodifiable List<CssMediaQuery> mediaQueries;

    /// Contains source queries through which a merged media rule may bubble, or {@code null}.
    private @Nullable @Unmodifiable Set<CssMediaQuery> mediaQuerySources;

    /// Contains the property-name prefix for nested declarations, or {@code null}.
    private @Nullable String declarationName;

    /// Contains the canonical URL of the stylesheet currently being executed.
    private @Nullable URI currentUrl;

    /// Contains configuration values available to the active module.
    private ModuleConfiguration currentConfiguration;

    /// Records whether a stylesheet execution is active.
    private boolean stylesheetActive;

    /// Contains the number of active style-rule ancestors.
    private int styleRuleDepth;

    /// Contains the number of active nested-property declarations.
    private int nestedDeclarationDepth;

    /// Creates an evaluator with an empty environment and no module loader.
    public SassEvaluator() {
        this(new Environment(), null);
    }

    /// Creates an evaluator that uses an existing environment.
    ///
    /// @param environment the mutable evaluation environment
    public SassEvaluator(Environment environment) {
        this(environment, null);
    }

    /// Creates an evaluator with a module registry.
    ///
    /// @param moduleRegistry the compilation module registry
    public SassEvaluator(ModuleRegistry moduleRegistry) {
        this(new Environment(), Objects.requireNonNull(moduleRegistry, "moduleRegistry"));
    }

    /// Creates an evaluator with an environment and optional module registry.
    ///
    /// @param environment    the mutable evaluation environment
    /// @param moduleRegistry the module registry, or {@code null}
    public SassEvaluator(Environment environment, @Nullable ModuleRegistry moduleRegistry) {
        this.compilationContext = new Object();
        this.environment = Objects.requireNonNull(environment, "environment");
        this.moduleRegistry = moduleRegistry;
        this.diagnostics = new ArrayList<>();
        this.currentConfiguration = ModuleConfiguration.empty();
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
    /// @throws EvaluationException if evaluation fails
    public void execute(Stylesheet stylesheet) {
        executeRoot(stylesheet, null);
    }

    /// Executes the root stylesheet and returns the combined module graph.
    ///
    /// The root environment and combined CSS remain installed on this evaluator.
    ///
    /// @param stylesheet the root stylesheet
    /// @param url        the root canonical URL, or {@code null}
    /// @return the root loaded module whose CSS has been combined
    /// @throws EvaluationException if evaluation fails
    public LoadedModule executeRoot(Stylesheet stylesheet, @Nullable URI url) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        if (moduleRegistry != null) {
            moduleRegistry.recordRoot(url);
        }
        var module = executeModuleBody(
                stylesheet,
                url,
                ModuleConfiguration.empty(),
                true
        );
        cssStylesheet = ModuleCss.combine(module);
        cssParent = cssStylesheet;
        return new LoadedModule(
                module.url(),
                module.variables(),
                module.functions(),
                module.mixins(),
                cssStylesheet,
                module.upstream(),
                module.configurableVariables(),
                module.forwardedModules()
        );
    }

    /// Executes a stylesheet as one module and returns its exports.
    ///
    /// Unlike [#executeRoot], this restores the previous evaluator state so
    /// dependency loading does not clobber the importer's environment.
    ///
    /// @param stylesheet the stylesheet to execute
    /// @param url        the canonical module URL, or {@code null}
    /// @return the loaded module
    /// @throws EvaluationException if evaluation fails
    public LoadedModule executeAsModule(Stylesheet stylesheet, @Nullable URI url) {
        return executeAsModule(
                stylesheet,
                url,
                ModuleConfiguration.empty()
        );
    }

    /// Executes a stylesheet as a module with values for root defaults.
    ///
    /// @param stylesheet   the stylesheet to execute
    /// @param url          the canonical module URL, or {@code null}
    /// @param configuration values available to root {@code !default} declarations
    /// @return the loaded module
    /// @throws EvaluationException if evaluation fails
    public LoadedModule executeAsModule(
            Stylesheet stylesheet,
            @Nullable URI url,
            ModuleConfiguration configuration
    ) {
        return executeModuleBody(
                stylesheet,
                url,
                Objects.requireNonNull(configuration, "configuration"),
                false
        );
    }

    /// Executes one stylesheet body, optionally retaining the resulting state.
    ///
    /// @param stylesheet the stylesheet to execute
    /// @param url        the canonical module URL, or {@code null}
    /// @param configuration values available to root defaults
    /// @param retainState whether to keep this module's environment installed
    /// @return the loaded module
    private LoadedModule executeModuleBody(
            Stylesheet stylesheet,
            @Nullable URI url,
            ModuleConfiguration configuration,
            boolean retainState
    ) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        var previousEnvironment = environment;
        var previousStylesheet = this.stylesheet;
        var previousCss = cssStylesheet;
        var previousParent = cssParent;
        var previousStyleRule = styleRule;
        var previousFontFace = fontFace;
        var previousMediaQueries = mediaQueries;
        var previousMediaQuerySources = mediaQuerySources;
        var previousDeclarationName = declarationName;
        var previousUrl = currentUrl;
        var previousConfiguration = currentConfiguration;
        var previousActive = stylesheetActive;
        var previousStyleRuleDepth = styleRuleDepth;
        var previousNestedDepth = nestedDeclarationDepth;

        environment = new Environment();
        this.stylesheet = stylesheet;
        cssStylesheet = new CssStylesheet(stylesheet.span());
        cssParent = cssStylesheet;
        styleRule = null;
        fontFace = null;
        mediaQueries = null;
        mediaQuerySources = null;
        declarationName = null;
        currentUrl = url;
        currentConfiguration = Objects.requireNonNull(
                configuration,
                "configuration"
        );
        stylesheetActive = true;
        styleRuleDepth = 0;
        nestedDeclarationDepth = 0;
        diagnostics.addAll(stylesheet.parseTimeWarnings());

        try {
            for (var child : stylesheet.children()) {
                child.accept(this);
            }
            for (var entry : stylesheet.globalVariables().entrySet()) {
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
            return new LoadedModule(
                    url,
                    environment.publicGlobalVariables(),
                    environment.publicGlobalFunctions(),
                    environment.publicGlobalMixins(),
                    Objects.requireNonNull(cssStylesheet, "css"),
                    environment.allModules(),
                    environment.configurableVariables(),
                    environment.forwardedModules()
            );
        } catch (RuntimeException failure) {
            environment = previousEnvironment;
            this.stylesheet = previousStylesheet;
            cssStylesheet = previousCss;
            cssParent = previousParent;
            styleRule = previousStyleRule;
            fontFace = previousFontFace;
            mediaQueries = previousMediaQueries;
            mediaQuerySources = previousMediaQuerySources;
            declarationName = previousDeclarationName;
            currentUrl = previousUrl;
            currentConfiguration = previousConfiguration;
            stylesheetActive = previousActive;
            styleRuleDepth = previousStyleRuleDepth;
            nestedDeclarationDepth = previousNestedDepth;
            throw failure;
        } finally {
            if (!retainState) {
                environment = previousEnvironment;
                this.stylesheet = previousStylesheet;
                cssStylesheet = previousCss;
                cssParent = previousParent;
                styleRule = previousStyleRule;
                fontFace = previousFontFace;
                mediaQueries = previousMediaQueries;
                mediaQuerySources = previousMediaQuerySources;
                declarationName = previousDeclarationName;
                currentUrl = previousUrl;
                currentConfiguration = previousConfiguration;
                stylesheetActive = previousActive;
                styleRuleDepth = previousStyleRuleDepth;
                nestedDeclarationDepth = previousNestedDepth;
            }
        }
    }

    /// Executes a stylesheet root through the visitor entry point.
    ///
    /// Prefer [#executeRoot(Stylesheet, URI)] or [#executeAsModule(Stylesheet, URI)]
    /// for compilation entry points.
    ///
    /// @param statement the stylesheet to execute
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitStylesheet(Stylesheet statement) {
        if (stylesheetActive && statement == stylesheet) {
            return StatementResult.CONTINUE;
        }
        executeAsModule(statement, currentUrl);
        return StatementResult.CONTINUE;
    }

    /// Evaluates values supplied by one module use rule.
    ///
    /// @param statement the use rule whose expressions are evaluated
    /// @return an empty implicit configuration or a new explicit configuration
    private ModuleConfiguration evaluateUseConfiguration(UseRule statement) {
        if (statement.configuration().isEmpty()) {
            return ModuleConfiguration.empty();
        }
        var values = new LinkedHashMap<String, ConfiguredValue>();
        for (var variable : statement.configuration()) {
            var value = evaluate(variable.expression()).withoutSlash();
            var configured = new ConfiguredValue(
                    value,
                    variable.span(),
                    expressionOrigin(variable.expression())
            );
            @Nullable ConfiguredValue previous = values.put(
                    variable.name(),
                    configured
            );
            if (previous != null) {
                throw new EvaluationException(
                        "The same variable may only be configured once.",
                        variable.nameSpan()
                );
            }
        }
        return ModuleConfiguration.explicit(values);
    }

    /// Evaluates configuration owned by one forward rule.
    ///
    /// Guarded values yield to non-null outer configuration. Hard values always
    /// replace the downstream copy without consuming a same-named outer value.
    ///
    /// @param statement the forward rule
    /// @param adjusted  the outer configuration projected through the rule
    /// @return the fresh configuration used to load the forwarded module
    private ModuleConfiguration evaluateForwardConfiguration(
            ForwardRule statement,
            ModuleConfiguration adjusted
    ) {
        var configuration = ModuleConfiguration.forForward(adjusted);
        var names = new LinkedHashSet<String>();
        for (var variable : statement.configuration()) {
            if (!names.add(variable.name())) {
                throw new EvaluationException(
                        "The same variable may only be configured once.",
                        variable.nameSpan()
                );
            }

            if (variable.guarded()) {
                @Nullable ConfiguredValue outer = adjusted.consume(
                        variable.name()
                );
                if (outer != null && outer.value() != SassNull.NULL) {
                    configuration.put(variable.name(), outer);
                    continue;
                }
            }

            var value = evaluate(variable.expression()).withoutSlash();
            configuration.put(
                    variable.name(),
                    new ConfiguredValue(
                            value,
                            variable.span(),
                            expressionOrigin(variable.expression())
                    )
            );
        }
        return configuration;
    }

    /// Fails when an explicit use configuration contains an unused value.
    ///
    /// @param configuration the configuration to inspect
    /// @throws EvaluationException if an explicit value remains unconsumed
    private static void assertConfigurationConsumed(
            ModuleConfiguration configuration
    ) {
        @Nullable ConfiguredValue unused = configuration.firstUnused();
        if (unused != null && configuration.isExplicit()) {
            throw new EvaluationException(
                    "This variable was not declared with !default in the "
                            + "@used module.",
                    unused.configurationSpan()
            );
        }
    }

    /// Loads another module and registers it in the current environment.
    ///
    /// @param statement the use rule
    /// @return the continue result
    @Override
    public StatementResult visitUseRule(UseRule statement) {
        if (moduleRegistry == null) {
            throw new EvaluationException(
                    "Module loading isn't available.",
                    statement.span()
            );
        }
        var configuration = evaluateUseConfiguration(statement);
        try {
            var module = moduleRegistry.load(
                    statement.url(),
                    currentUrl,
                    statement.span(),
                    this,
                    configuration,
                    !statement.configuration().isEmpty()
            );
            environment.addModule(module, statement.namespace(), statement.span());
            assertConfigurationConsumed(configuration);
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "module failure message"),
                    statement.span(),
                    List.of(),
                    cause
            );
        }
        return StatementResult.CONTINUE;
    }

    /// Loads and re-exports another module without adding a local namespace.
    ///
    /// @param statement the forward rule
    /// @return the continue result
    @Override
    public StatementResult visitForwardRule(ForwardRule statement) {
        if (moduleRegistry == null) {
            throw new EvaluationException(
                    "Module loading isn't available.",
                    statement.span()
            );
        }
        var adjusted = currentConfiguration.throughForward(statement);
        try {
            if (statement.configuration().isEmpty()) {
                var module = moduleRegistry.load(
                        statement.url(),
                        currentUrl,
                        statement.span(),
                        this,
                        adjusted,
                        false
                );
                environment.forwardModule(module, statement);
            } else {
                var configuration = evaluateForwardConfiguration(
                        statement,
                        adjusted
                );
                var module = moduleRegistry.load(
                        statement.url(),
                        currentUrl,
                        statement.span(),
                        this,
                        configuration,
                        true
                );
                environment.forwardModule(module, statement);

                var configuredNames = new LinkedHashSet<String>();
                var hardNames = new LinkedHashSet<String>();
                for (var variable : statement.configuration()) {
                    configuredNames.add(variable.name());
                    if (!variable.guarded()) {
                        hardNames.add(variable.name());
                    }
                }
                for (var name : adjusted.names()) {
                    if (!hardNames.contains(name)
                            && !configuration.contains(name)) {
                        adjusted.consume(name);
                    }
                }
                configuration.retainOnly(configuredNames);
                assertConfigurationConsumed(configuration);
            }
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(
                            cause.getMessage(),
                            "module failure message"
                    ),
                    statement.span(),
                    List.of(),
                    cause
            );
        }
        return StatementResult.CONTINUE;
    }

    /// Evaluates a top-level font-face rule and executes its descriptor body.
    ///
    /// @param statement the font-face rule
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitFontFaceRule(FontFaceRule statement) {
        if (!(requireCssParent() instanceof CssStylesheet)) {
            throw new EvaluationException(
                    "@font-face rules may only be used at the stylesheet root.",
                    statement.span()
            );
        }

        var rule = new CssFontFace(statement.span());
        addCssChild(rule, false);
        var previousParent = requireCssParent();
        var previousFontFace = fontFace;
        cssParent = rule;
        fontFace = rule;
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
                fontFace = previousFontFace;
                cssParent = previousParent;
            }
        }

        if (!previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
        }
        return StatementResult.CONTINUE;
    }

    /// Evaluates an {@code @media} rule and resolves nesting through CSS media queries.
    ///
    /// Compatible nested queries are merged and bubbled through their source
    /// media rules. Queries whose intersection cannot be expressed by one CSS
    /// query list remain structurally nested.
    ///
    /// @param statement the media rule
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitMediaRule(MediaRule statement) {
        if (declarationName != null) {
            throw new EvaluationException(
                    "Media rules may not be used within nested declarations.",
                    statement.span()
            );
        }
        if (fontFace != null) {
            throw new EvaluationException(
                    "Media rules may not be used within @font-face rules.",
                    statement.span()
            );
        }

        @Unmodifiable List<CssMediaQuery> queries;
        try {
            queries = CssMediaQuery.parseList(performInterpolation(statement.query()));
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "media query failure message"),
                    statement.query().span(),
                    List.of(),
                    cause
            );
        }

        @Nullable List<CssMediaQuery> mergedQueries = mediaQueries == null
                ? null
                : CssMediaQuery.mergeLists(mediaQueries, queries);
        if (mergedQueries != null && mergedQueries.isEmpty()) {
            return StatementResult.CONTINUE;
        }
        @Unmodifiable List<CssMediaQuery> effectiveQueries =
                mergedQueries == null ? queries : mergedQueries;
        @Unmodifiable Set<CssMediaQuery> effectiveSources;
        if (mergedQueries == null) {
            effectiveSources = Set.of();
        } else {
            var sources = new LinkedHashSet<CssMediaQuery>();
            if (mediaQuerySources != null) {
                sources.addAll(mediaQuerySources);
            }
            if (mediaQueries != null) {
                sources.addAll(mediaQueries);
            }
            sources.addAll(queries);
            effectiveSources = Set.copyOf(sources);
        }

        var rule = new CssMediaRule(effectiveQueries, statement.span());
        addCssChild(rule, true, effectiveSources);
        var previousParent = requireCssParent();
        var previousMediaQueries = mediaQueries;
        var previousMediaQuerySources = mediaQuerySources;
        @Nullable CssStyleRule activeStyleRule = styleRule;
        cssParent = rule;
        mediaQueries = effectiveQueries;
        mediaQuerySources = effectiveSources;
        var scope = environment.scope(
                ScopeSemantics.LEXICAL,
                hasDirectDeclarations(statement.children())
        );
        try {
            if (activeStyleRule == null) {
                for (var child : statement.children()) {
                    child.accept(this);
                }
            } else {
                var wrapper = activeStyleRule.copyWithoutChildren();
                rule.addChild(wrapper);
                var mediaParent = requireCssParent();
                cssParent = wrapper;
                try {
                    for (var child : statement.children()) {
                        child.accept(this);
                    }
                } finally {
                    cssParent = mediaParent;
                }
            }
        } finally {
            try {
                scope.close();
            } finally {
                mediaQueries = previousMediaQueries;
                mediaQuerySources = previousMediaQuerySources;
                cssParent = previousParent;
            }
        }

        if (activeStyleRule == null && !previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
        }
        return StatementResult.CONTINUE;
    }

    /// Evaluates an {@code @supports} rule while preserving its CSS condition.
    ///
    /// The rule bubbles through enclosing style rules but remains inside other
    /// conditional rules. This preserves CSS conditional nesting while allowing
    /// nested Sass declarations to be emitted through a style-rule wrapper.
    ///
    /// @param statement the supports rule
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitSupportsRule(SupportsRule statement) {
        if (declarationName != null) {
            throw new EvaluationException(
                    "Supports rules may not be used within nested declarations.",
                    statement.span()
            );
        }
        if (fontFace != null) {
            throw new EvaluationException(
                    "Supports rules may not be used within @font-face rules.",
                    statement.span()
            );
        }

        var condition = evaluateSupportsCondition(statement.condition()).strip();
        if (condition.isEmpty()) {
            throw new EvaluationException(
                    "Expected supports condition.",
                    statement.condition().span()
            );
        }

        var rule = new CssSupportsRule(condition, statement.span());
        addCssChild(rule, true);
        var previousParent = requireCssParent();
        @Nullable CssStyleRule activeStyleRule = styleRule;
        cssParent = rule;
        var scope = environment.scope(
                ScopeSemantics.LEXICAL,
                hasDirectDeclarations(statement.children())
        );
        try {
            if (activeStyleRule == null) {
                for (var child : statement.children()) {
                    child.accept(this);
                }
            } else {
                var wrapper = activeStyleRule.copyWithoutChildren();
                rule.addChild(wrapper);
                var supportsParent = requireCssParent();
                cssParent = wrapper;
                try {
                    for (var child : statement.children()) {
                        child.accept(this);
                    }
                } finally {
                    cssParent = supportsParent;
                }
            }
        } finally {
            try {
                scope.close();
            } finally {
                cssParent = previousParent;
            }
        }

        if (activeStyleRule == null && !previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
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
        if (fontFace != null) {
            throw new EvaluationException(
                    "Style rules may not be used within @font-face rules.",
                    statement.span()
            );
        }
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
        if (styleRule == null && fontFace == null) {
            throw new EvaluationException(
                    "Declarations may only be used within style rules or @font-face rules.",
                    statement.span()
            );
        }
        if (fontFace != null && statement.hasChildren()) {
            throw new EvaluationException(
                    "@font-face descriptor declarations may not be nested.",
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
            if (statement.namespace() == null && environment.atRoot()) {
                environment.markVariableConfigurable(statement.name());
                @Nullable ConfiguredValue override =
                        currentConfiguration.consume(statement.name());
                if (override != null && override.value() != SassNull.NULL) {
                    valueOperation(statement.span(), () -> {
                        environment.setVariable(
                                statement.name(),
                                override.value(),
                                override.originSpan(),
                                null,
                                true
                        );
                        return StatementResult.CONTINUE;
                    });
                    return StatementResult.CONTINUE;
                }
            }
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
        @Nullable UserDefinedCallable contentCallable = null;
        if (statement.content() != null) {
            contentCallable = new UserDefinedCallable(
                    "@content",
                    statement.content().parameters(),
                    statement.content().children(),
                    environment.closure(),
                    statement.content().span(),
                    false
            );
        }
        runMixinCallable(mixin, statement.arguments(), contentCallable, statement.span());
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
        runContentBlock(content, statement.arguments(), statement.span());
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

    /// Evaluates a `@debug` expression and records its inspect representation.
    ///
    /// Quoted and unquoted strings report their unquoted text. Other values use
    /// their inspect-mode Sass representation.
    ///
    /// @param statement the debug rule
    /// @return the continue result
    @Override
    public StatementResult visitDebugRule(DebugRule statement) {
        var value = evaluate(statement.expression());
        var message = value instanceof SassString string ? string.text() : value.toString();
        diagnostics.add(new Diagnostic(
                DiagnosticSeverity.DEBUG,
                message,
                statement.span(),
                null
        ));
        return StatementResult.CONTINUE;
    }

    /// Evaluates a `@warn` expression and records its CSS string representation.
    ///
    /// Quoted and unquoted strings report their unquoted text. Other values use
    /// CSS serialization, so `null` produces an empty warning message.
    ///
    /// @param statement the warning rule
    /// @return the continue result
    /// @throws EvaluationException if the evaluated value cannot be represented as CSS
    @Override
    public StatementResult visitWarnRule(WarnRule statement) {
        var value = evaluate(statement.expression());
        var message = value instanceof SassString string
                ? string.text()
                : valueOperation(statement.expression().span(), value::toCssString);
        diagnostics.add(new Diagnostic(
                DiagnosticSeverity.WARNING,
                message,
                statement.span(),
                null
        ));
        return StatementResult.CONTINUE;
    }

    /// Evaluates an `@error` expression and terminates stylesheet execution.
    ///
    /// @param statement the error rule
    /// @return this method does not return normally
    /// @throws EvaluationException always, with the value's inspect representation
    @Override
    public StatementResult visitErrorRule(ErrorRule statement) {
        throw new EvaluationException(
                evaluate(statement.expression()).toString(),
                statement.span()
        );
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
    /// Unqualified resolution checks lexical and {@code as *} functions, then
    /// global built-ins, and finally falls back to plain CSS. A namespaced call
    /// resolves only the explicitly loaded module with that namespace.
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
            throw new EvaluationException("Undefined function.", expression.span());
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

    /// Evaluates a structured supports condition to canonical CSS text.
    ///
    /// @param condition the parsed supports condition
    /// @return the evaluated CSS condition
    private String evaluateSupportsCondition(SupportsCondition condition) {
        return evaluateSupportsCondition(condition, null);
    }

    /// Evaluates a supports condition with the operator context of its parent.
    ///
    /// @param condition the parsed supports condition
    /// @param parentOperator the enclosing boolean operator, or null
    /// @return the evaluated CSS condition
    private String evaluateSupportsCondition(
            SupportsCondition condition,
            @Nullable SupportsBooleanOperator parentOperator
    ) {
        if (condition instanceof SupportsDeclaration declaration) {
            var name = evaluateSupportsValue(declaration.name());
            var value = evaluateSupportsValue(declaration.value());
            return "(" + name
                    + (declaration.customProperty() ? ":" : ": ")
                    + value + ")";
        }
        if (condition instanceof SupportsFunction function) {
            return performInterpolation(function.name())
                    + "(" + performInterpolation(function.arguments()) + ")";
        }
        if (condition instanceof SupportsAnything anything) {
            return "(" + performInterpolation(anything.contents()) + ")";
        }
        if (condition instanceof SupportsInterpolation interpolation) {
            return evaluateSupportsValue(interpolation.expression());
        }
        if (condition instanceof SupportsNegation negation) {
            return "not " + parenthesizeSupportsCondition(negation.condition(), null);
        }
        if (condition instanceof SupportsOperation operation) {
            var operator = operation.operator();
            return parenthesizeSupportsCondition(operation.left(), operator)
                    + " " + operator.cssText() + " "
                    + parenthesizeSupportsCondition(operation.right(), operator);
        }
        throw new AssertionError("unknown supports condition: " + condition);
    }

    /// Adds parentheses required to preserve supports boolean semantics.
    ///
    /// @param condition the child condition
    /// @param parentOperator the enclosing operator, or null
    /// @return the condition with canonical parentheses
    private String parenthesizeSupportsCondition(
            SupportsCondition condition,
            @Nullable SupportsBooleanOperator parentOperator
    ) {
        boolean parenthesize = condition instanceof SupportsNegation
                || condition instanceof SupportsOperation operation
                && (parentOperator == null
                || operation.operator() != parentOperator);
        var text = evaluateSupportsCondition(condition, parentOperator);
        return parenthesize ? "(" + text + ")" : text;
    }

    /// Evaluates a SassScript value for supports condition serialization.
    ///
    /// @param expression the expression producing the value
    /// @return the unquoted CSS representation
    private String evaluateSupportsValue(SassExpression expression) {
        var value = evaluate(expression);
        return valueOperation(expression.span(), () -> value.toCssString(false));
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

    /// Adds a CSS child, optionally bubbling through active structural parents.
    ///
    /// @param node              the child to append
    /// @param throughStyleRules whether style-rule parents should be skipped
    private void addCssChild(CssNode node, boolean throughStyleRules) {
        addCssChild(node, throughStyleRules, Set.of());
    }

    /// Adds a CSS child while allowing a merged media rule to bubble through its sources.
    ///
    /// @param node              the child to append
    /// @param throughStyleRules whether style-rule parents should be skipped
    /// @param mediaQuerySources queries owned by media ancestors that may be skipped
    private void addCssChild(
            CssNode node,
            boolean throughStyleRules,
            Set<CssMediaQuery> mediaQuerySources
    ) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(mediaQuerySources, "mediaQuerySources");
        var parent = requireCssParent();
        while (shouldBubbleThrough(parent, throughStyleRules, mediaQuerySources)) {
            @Nullable CssParentNode grandparent = parent.parent();
            if (grandparent == null) {
                throw new IllegalStateException("bubbled CSS parent escaped the root");
            }
            parent = grandparent;
        }
        if ((throughStyleRules || !mediaQuerySources.isEmpty()) && parent.hasFollowingSibling()) {
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
        parent.addChild(node);
    }

    /// Returns whether a CSS parent must be skipped while bubbling one child.
    ///
    /// @param parent            the candidate parent
    /// @param throughStyleRules whether style-rule parents should be skipped
    /// @param mediaQuerySources queries that identify mergeable media ancestors
    /// @return whether the child should be attached above {@code parent}
    private static boolean shouldBubbleThrough(
            CssParentNode parent,
            boolean throughStyleRules,
            Set<CssMediaQuery> mediaQuerySources
    ) {
        if (throughStyleRules && parent instanceof CssStyleRule) {
            return true;
        }
        return parent instanceof CssMediaRule mediaRule
                && !mediaQuerySources.isEmpty()
                && mediaRule.queries().stream().allMatch(mediaQuerySources::contains);
    }

    /// Copies the active CSS ancestor path after any visible later sibling.
    ///
    /// A nested conditional rule may bubble outside several active parents.
    /// Later declarations must resume in copies of every affected ancestor so
    /// their source order remains after the bubbled rule.
    private void copyParentAfterSibling() {
        var path = new ArrayList<CssParentNode>();
        var current = requireCssParent();
        while (true) {
            path.add(current);
            @Nullable CssParentNode parent = current.parent();
            if (parent == null) {
                break;
            }
            current = parent;
        }

        var copyStart = -1;
        for (var index = 0; index < path.size() - 1; index++) {
            if (path.get(index).hasFollowingSibling()) {
                copyStart = index;
            }
        }
        if (copyStart < 0) {
            return;
        }

        var copiedParent = path.get(copyStart).copyWithoutChildren();
        var copies = new IdentityHashMap<CssParentNode, CssParentNode>();
        copies.put(path.get(copyStart), copiedParent);
        path.get(copyStart + 1).addChild(copiedParent);
        CssParentNode copiedLeaf = copiedParent;
        for (var index = copyStart - 1; index >= 0; index--) {
            var childCopy = path.get(index).copyWithoutChildren();
            copies.put(path.get(index), childCopy);
            copiedLeaf.addChild(childCopy);
            copiedLeaf = childCopy;
        }

        cssParent = copiedLeaf;
        if (styleRule != null) {
            @Nullable CssParentNode copiedStyleRule = copies.get(styleRule);
            if (copiedStyleRule instanceof CssStyleRule rule) {
                styleRule = rule;
            }
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
        return runCallable(callable, evaluateArguments(arguments, span), span);
    }

    /// Invokes a callable with arguments that have already been evaluated.
    ///
    /// @param callable  the callable to invoke
    /// @param evaluated the evaluated positional and keyword arguments
    /// @param span      the invocation span
    /// @return the callable result
    private SassValue runCallable(
            Callable callable,
            EvaluatedArguments evaluated,
            SourceSpan span
    ) {
        if (callable instanceof BuiltInCallable builtIn) {
            return valueOperation(span, () -> {
                var bound = bindForBuiltin(builtIn, evaluated, span);
                var result = builtIn.invoke(
                        new BuiltInCallable.Context(
                                environment,
                                BUILT_IN_FUNCTIONS,
                                currentUrl,
                                span,
                                compilationContext,
                                this::runFunctionValue,
                                this::runMixinValue,
                                this::reportDeprecation
                        ),
                        bound.values()
                );
                checkUnusedKeywords(bound.rest(), span);
                return result;
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

    /// Invokes a first-class function reference with preserved evaluated arguments.
    ///
    /// @param function  the function reference to invoke
    /// @param arguments the positional and keyword arguments to forward
    /// @param span      the dynamic invocation span
    /// @return the callable result
    private SassValue runFunctionValue(
            SassFunction function,
            SassArgumentList arguments,
            SourceSpan span
    ) {
        function.assertCompilationContext(compilationContext);
        return runCallable(
                function.callable(),
                new EvaluatedArguments(
                        List.copyOf(arguments.asList()),
                        new LinkedHashMap<>(arguments.keywords()),
                        arguments.separator()
                ),
                span
        );
    }

    /// Includes a first-class mixin reference with preserved evaluated arguments.
    ///
    /// @param mixin     the mixin reference to include
    /// @param arguments the positional and keyword arguments to forward
    /// @param content   the direct content block, or {@code null}
    /// @param span      the dynamic include span
    private void runMixinValue(
            SassMixin mixin,
            SassArgumentList arguments,
            @Nullable UserDefinedCallable content,
            SourceSpan span
    ) {
        mixin.assertCompilationContext(compilationContext);
        runMixinCallable(
                mixin.callable(),
                new EvaluatedArguments(
                        List.copyOf(arguments.asList()),
                        new LinkedHashMap<>(arguments.keywords()),
                        arguments.separator()
                ),
                content,
                span
        );
    }

    /// Records a deprecation raised by a contextual built-in function.
    ///
    /// @param message the caller-facing deprecation message
    /// @param code    the stable deprecation identifier
    /// @param span    the source span that triggered the deprecation
    private void reportDeprecation(String message, String code, SourceSpan span) {
        diagnostics.add(new Diagnostic(DiagnosticSeverity.DEPRECATION, message, span, code));
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

    /// Includes a mixin with unevaluated call-site arguments.
    ///
    /// @param mixin     the resolved mixin, or {@code null}
    /// @param arguments the unevaluated invocation arguments
    /// @param content   the direct content block, or {@code null}
    /// @param span      the include span
    private void runMixinCallable(
            @Nullable Callable mixin,
            ArgumentList arguments,
            @Nullable UserDefinedCallable content,
            SourceSpan span
    ) {
        if (mixin == null) {
            throw new EvaluationException("Undefined mixin.", span);
        }
        runMixinCallable(mixin, evaluateArguments(arguments, span), content, span);
    }

    /// Includes a mixin with already-evaluated arguments.
    ///
    /// @param mixin     the resolved mixin
    /// @param evaluated the evaluated invocation arguments
    /// @param content   the direct content block, or {@code null}
    /// @param span      the include span
    private void runMixinCallable(
            Callable mixin,
            EvaluatedArguments evaluated,
            @Nullable UserDefinedCallable content,
            SourceSpan span
    ) {
        if (mixin instanceof UserDefinedCallable userMixin) {
            if (content != null && !userMixin.acceptsContent()) {
                throw new EvaluationException("Mixin doesn't accept a content block.", span);
            }
            runUserDefinedMixin(userMixin, evaluated, content, span);
            return;
        }
        if (mixin instanceof BuiltInCallable builtIn) {
            if (content != null && !builtIn.acceptsContent()) {
                throw new EvaluationException("Mixin doesn't accept a content block.", span);
            }
            runBuiltInMixin(builtIn, evaluated, content, span);
            return;
        }
        throw new EvaluationException("Undefined mixin.", span);
    }

    /// Executes a built-in mixin body with its direct content binding.
    ///
    /// @param mixin     the built-in mixin
    /// @param evaluated the evaluated invocation arguments
    /// @param content   the direct content block, or {@code null}
    /// @param span      the include span
    private void runBuiltInMixin(
            BuiltInCallable mixin,
            EvaluatedArguments evaluated,
            @Nullable UserDefinedCallable content,
            SourceSpan span
    ) {
        environment.withContent(content, () -> environment.withMixin(() -> {
            runCallable(mixin, evaluated, span);
            return null;
        }));
    }

    /// Executes a user-defined mixin body with unevaluated call-site arguments.
    ///
    /// @param mixin     the user-defined mixin
    /// @param arguments the unevaluated invocation arguments
    /// @param content   the direct content block, or {@code null}
    /// @param span      the include span
    private void runUserDefinedMixin(
            UserDefinedCallable mixin,
            ArgumentList arguments,
            @Nullable UserDefinedCallable content,
            SourceSpan span
    ) {
        runUserDefinedMixin(mixin, evaluateArguments(arguments, span), content, span);
    }

    /// Executes a user-defined mixin body with already-evaluated arguments.
    ///
    /// @param mixin     the user-defined mixin
    /// @param evaluated the evaluated invocation arguments
    /// @param content   the direct content block, or {@code null}
    /// @param span      the include span
    private void runUserDefinedMixin(
            UserDefinedCallable mixin,
            EvaluatedArguments evaluated,
            @Nullable UserDefinedCallable content,
            SourceSpan span
    ) {
        withEnvironment(mixin.environment().closure(), () -> {
            environment.withContent(content, () -> environment.withMixin(() -> {
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
            }));
            return null;
        });
    }

    /// Executes a content block without masking its captured outer content.
    ///
    /// @param content   the content block captured at the include site
    /// @param arguments the unevaluated arguments passed by {@code @content}
    /// @param span      the content invocation span
    private void runContentBlock(
            UserDefinedCallable content,
            ArgumentList arguments,
            SourceSpan span
    ) {
        runContentBlock(content, evaluateArguments(arguments, span), span);
    }

    /// Executes a content block with already-evaluated arguments.
    ///
    /// @param content   the content block captured at the include site
    /// @param evaluated the evaluated arguments passed by {@code @content}
    /// @param span      the content invocation span
    private void runContentBlock(
            UserDefinedCallable content,
            EvaluatedArguments evaluated,
            SourceSpan span
    ) {
        withEnvironment(content.environment().closure(), () -> {
            environment.withMixin(() -> {
                var scope = environment.scope(ScopeSemantics.LEXICAL, true);
                try {
                    @Nullable SassArgumentList rest = bindParameters(
                            content.parameters(),
                            evaluated,
                            span
                    );
                    executeChildren(content.children());
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
                named.putAll(argumentList.keywords());
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
        return new EvaluatedArguments(List.copyOf(positional), named, separator);
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

    /// Converts evaluated arguments into bound values for a built-in callable.
    private BoundBuiltInArguments bindForBuiltin(
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
            return new BoundBuiltInArguments(List.copyOf(bound), null);
        }

        var restPositional = positional.size() > params.size()
                ? List.copyOf(positional.subList(params.size(), positional.size()))
                : List.<SassValue>of();
        var separator = evaluated.separator() == ListSeparator.UNDECIDED
                ? ListSeparator.COMMA
                : evaluated.separator();
        var rest = new SassArgumentList(restPositional, separator, named);
        bound.add(rest);
        return new BoundBuiltInArguments(List.copyOf(bound), rest);
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

    /// Contains bound values and an optional rest argument list for one built-in call.
    ///
    /// @param values the immutable values passed to the built-in callback
    /// @param rest   the rest argument list whose keywords must be consumed, or {@code null}
    @NotNullByDefault
    private record BoundBuiltInArguments(
            @Unmodifiable List<SassValue> values,
            @Nullable SassArgumentList rest
    ) {
    }

    /// Contains evaluated invocation arguments.
    ///
    /// @param positional the evaluated positional arguments
    /// @param named      the evaluated keyword arguments by normalized name
    /// @param separator  the separator preserved from a spread argument list
    @NotNullByDefault
    private record EvaluatedArguments(
            @Unmodifiable List<SassValue> positional,
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
