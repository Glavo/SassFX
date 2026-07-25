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
import org.glavo.scssfx.internal.ast.DynamicImport;
import org.glavo.scssfx.internal.ast.EachRule;
import org.glavo.scssfx.internal.ast.ElseClause;
import org.glavo.scssfx.internal.ast.ErrorRule;
import org.glavo.scssfx.internal.ast.AtRootQuery;
import org.glavo.scssfx.internal.ast.AtRootRule;
import org.glavo.scssfx.internal.ast.ExtendRule;
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
import org.glavo.scssfx.internal.ast.IfConditionExpression;
import org.glavo.scssfx.internal.ast.IfExpression;
import org.glavo.scssfx.internal.ast.LegacyIfExpression;
import org.glavo.scssfx.internal.ast.ImportRule;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.MapExpression;
import org.glavo.scssfx.internal.ast.MixinRule;
import org.glavo.scssfx.internal.ast.NullExpression;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParameterList;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.ReturnRule;
import org.glavo.scssfx.internal.ast.SelectorExpression;
import org.glavo.scssfx.internal.ast.UseRule;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.module.ConfiguredValue;
import org.glavo.scssfx.internal.module.ForwardedModuleView;
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
import org.glavo.scssfx.internal.ast.StaticImport;
import org.glavo.scssfx.internal.ast.UnaryOperationExpression;
import org.glavo.scssfx.internal.ast.UnaryOperator;
import org.glavo.scssfx.internal.ast.UnknownAtRule;
import org.glavo.scssfx.internal.ast.VariableDeclaration;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.ast.WarnRule;
import org.glavo.scssfx.internal.ast.WhileRule;
import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.callable.Callable;
import org.glavo.scssfx.internal.callable.PlainCssCallable;
import org.glavo.scssfx.internal.css.CssImport;
import org.glavo.scssfx.internal.callable.UserDefinedCallable;
import org.glavo.scssfx.internal.ast.selector.ComplexSelector;
import org.glavo.scssfx.internal.ast.selector.PlaceholderSelector;
import org.glavo.scssfx.internal.ast.selector.SelectorAlgebra;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.extend.PendingExtension;
import org.glavo.scssfx.internal.css.CssComment;
import org.glavo.scssfx.internal.css.CssFontFace;
import org.glavo.scssfx.internal.css.CssMediaQuery;
import org.glavo.scssfx.internal.css.CssMediaRule;
import org.glavo.scssfx.internal.css.CssSupportsRule;
import org.glavo.scssfx.internal.css.CssUnknownAtRule;
import org.glavo.scssfx.internal.function.BuiltInFunctions;
import org.glavo.scssfx.internal.css.CssDeclaration;
import org.glavo.scssfx.internal.css.CssNode;
import org.glavo.scssfx.internal.css.CssParentNode;
import org.glavo.scssfx.internal.css.CssStyleRule;
import org.glavo.scssfx.internal.css.CssStylesheet;
import org.glavo.scssfx.internal.css.CssValue;
import org.glavo.scssfx.internal.value.CalculationOperation;
import org.glavo.scssfx.internal.value.CalculationOperator;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassArgumentList;
import org.glavo.scssfx.internal.value.SassBoolean;
import org.glavo.scssfx.internal.value.SassCalculation;
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

    /// Contains global function names that retain native CSS meaning in plain CSS.
    private static final @Unmodifiable Set<String> PLAIN_CSS_ALLOWED_FUNCTIONS = Set.of(
            "abs", "alpha", "color", "grayscale", "hsl", "hsla", "hwb",
            "invert", "lab", "lch", "max", "min", "oklab", "oklch",
            "opacity", "rgb", "rgba", "round", "saturate"
    );

    /// Contains calculation functions whose argument grammar is preserved verbatim in plain CSS.
    private static final @Unmodifiable Set<String> PLAIN_CSS_CALCULATION_FUNCTIONS = Set.of(
            "abs", "calc", "clamp", "max", "min", "round"
    );

    /// CSS calculation names that always use calculation evaluation in Sass.
    private static final @Unmodifiable Set<String> CALCULATION_FUNCTIONS = Set.of(
            "calc", "clamp", "hypot", "sin", "cos", "tan", "asin", "acos", "atan",
            "sqrt", "exp", "sign", "mod", "rem", "atan2", "pow", "log", "calc-size"
    );

    /// Global functions that become CSS calculations when every argument is calculation-safe.
    private static final @Unmodifiable Set<String> LEGACY_CALCULATION_FUNCTIONS = Set.of(
            "min", "max", "round", "abs"
    );

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

    /// Contains the nearest style rule ignoring `@at-root` exclusion, or {@code null}.
    private @Nullable CssStyleRule styleRuleForParent;

    /// Records whether the active `@at-root` query excludes style-rule parents.
    private boolean atRootExcludingStyleRule;

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


    /// Records whether evaluation is producing a {@code @supports} condition value.
    ///
    /// Calculations are left unsimplified in that context so CSS sees the original
    /// function form.
    private boolean inSupportsDeclaration;

    /// Contains the number of active style-rule ancestors.
    private int styleRuleDepth;

    /// Contains the number of active nested-property declarations.
    private int nestedDeclarationDepth;

    /// Records whether evaluation is inside a {@code @keyframes} rule body.
    private boolean inKeyframes;

    /// Nesting depth of legacy {@code @import} evaluation.
    ///
    /// Nested {@code @use} inside an imported stylesheet must isolate namespaces
    /// and may re-emit already-loaded module CSS so import paths duplicate it.
    private int legacyImportDepth;

    /// Maps upstream modules to loud comments that must precede their CSS.
    private final IdentityHashMap<LoadedModule, List<CssComment>> preModuleComments =
            new IdentityHashMap<>();

    /// Contains style rules eligible for end-of-module `@extend` rewriting.
    private final ArrayList<CssStyleRule> extendableStyleRules = new ArrayList<>();

    /// Contains `@extend` directives collected during the active module body.
    private final ArrayList<PendingExtension> pendingExtensions = new ArrayList<>();

    /// Modules whose CSS/extensions were re-emitted outside the root CSS graph
    /// (for example {@code @use} inside {@code @import}). Indexed for
    /// {@code @extend} visibility so private/upstream checks still see them.
    private final ArrayList<LoadedModule> extensionVisibilityModules = new ArrayList<>();

    /// Maps each style rule to the canonical URL of the module that defined it.
    ///
    /// Used so `@extend` only rewrites selectors visible to the extending module
    /// (the defining module and its transitive upstream), matching dart-sass
    /// module isolation.
    private final IdentityHashMap<CssStyleRule, URI> styleRuleOrigins =
            new IdentityHashMap<>();

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
            moduleRegistry.recordRoot(url, stylesheet.span());
        }
        var module = executeModuleBody(
                stylesheet,
                url,
                ModuleConfiguration.empty(),
                true
        );
        cssStylesheet = ModuleCss.combine(module);
        cssParent = cssStylesheet;
        var extensions = new ArrayList<PendingExtension>();
        collectExtensions(module, extensions, new IdentityHashMap<>());
        var styleRules = new ArrayList<CssStyleRule>();
        collectStyleRules(cssStylesheet, styleRules);
        applyExtensions(styleRules, extensions, module);
        return new LoadedModule(
                module.url(),
                module.variables(),
                module.functions(),
                module.mixins(),
                cssStylesheet,
                module.upstream(),
                module.configurableVariables(),
                module.forwardedModules(),
                module.preModuleComments(),
                module.extensions()
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

    /// Executes a legacy imported stylesheet in the current environment.
    ///
    /// CSS is emitted into the active parent at the import location. Variables,
    /// functions, and mixins declared by the imported stylesheet remain visible
    /// to subsequent caller statements. The imported source URL is installed
    /// temporarily so nested relative imports resolve beside that file.
    ///
    /// @param imported the parsed imported stylesheet
    /// @param url      the imported stylesheet's canonical URL
    /// @throws EvaluationException if the imported stylesheet cannot be evaluated
    public void executeLegacyImport(Stylesheet imported, URI url) {
        Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(url, "url");
        if (!stylesheetActive) {
            throw new IllegalStateException("legacy imports require active stylesheet execution");
        }
        var previousStylesheet = stylesheet;
        var previousUrl = currentUrl;
        var previousConfiguration = currentConfiguration;
        // Nested {@code @use} members stay local to the imported file: CSS is still
        // emitted, but namespaces and {@code as *} exports are restored afterward
        // so transitive members do not leak through {@code @import}. Clear the
        // importer's namespaces before evaluating so nested {@code @use "shared"}
        // does not collide with an importer that already used the same namespace.
        var moduleSnapshot = environment.snapshotModuleTables();
        environment.clearModuleNamespaces();
        stylesheet = imported;
        currentUrl = url;
        legacyImportDepth++;
        // When the imported stylesheet forwards other files, snapshot visible
        // variables as an implicit configuration (dart-sass toImplicitConfiguration)
        // so importer $vars configure downstream !default through @forward.
        boolean hasForwards = false;
        for (var child : imported.children()) {
            if (child instanceof ForwardRule) {
                hasForwards = true;
                break;
            }
        }
        if (hasForwards) {
            currentConfiguration = environment.toImplicitConfiguration();
        } else {
            currentConfiguration = previousConfiguration == null
                    ? ModuleConfiguration.empty()
                    : previousConfiguration;
        }
        diagnostics.addAll(imported.parseTimeWarnings());
        try {
            for (var child : imported.children()) {
                if (child instanceof ForwardRule forward) {
                    // @forward inside an @import-ed file loads the target as another
                    // legacy import so its public members become visible to the
                    // importer. Full module-boundary forwarding remains separate.
                    executeForwardAsLegacyImport(forward);
                    continue;
                }
                if (child instanceof UseRule use) {
                    // dart-sass still evaluates @use inside @import-ed files so
                    // the used module's CSS is included and its members remain
                    // available to the imported file under their namespace.
                    visitUseRule(use);
                    continue;
                }
                child.accept(this);
            }
            for (var entry : imported.globalVariables().entrySet()) {
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
        } finally {
            legacyImportDepth--;
            environment.restoreModuleTables(moduleSnapshot);
            stylesheet = previousStylesheet;
            currentUrl = previousUrl;
            currentConfiguration = previousConfiguration;
        }
    }

    /// Loads a {@code @forward} target as an isolated module and surfaces it to
    /// the current legacy-import environment.
    ///
    /// Resolution uses module candidates (not {@code *.import.scss}). Evaluation
    /// runs in a separate module environment under the outer configuration
    /// projected through the forward rule (implicit importer snapshot and any
    /// {@code with} clause). Members are installed as live bindings with the
    /// forward's prefix and show/hide filters applied, so importer assignments
    /// stay shared with the module. CSS is re-emitted on every import occurrence.
    ///
    /// @param forward the forward rule
    private void executeForwardAsLegacyImport(ForwardRule forward) {
        if (moduleRegistry == null) {
            throw new EvaluationException(
                    "Module loading isn't available.",
                    forward.span()
            );
        }
        try {
            var adjusted = currentConfiguration.throughForward(forward);
            LoadedModule module;
            if (forward.configuration().isEmpty()) {
                module = moduleRegistry.load(
                        forward.url(),
                        currentUrl,
                        forward.span(),
                        this,
                        adjusted,
                        false
                );
            } else {
                var configuration = evaluateForwardConfiguration(
                        forward,
                        adjusted
                );
                module = moduleRegistry.load(
                        forward.url(),
                        currentUrl,
                        forward.span(),
                        this,
                        configuration,
                        true
                );
                var configuredNames = new LinkedHashSet<String>();
                var hardNames = new LinkedHashSet<String>();
                for (var variable : forward.configuration()) {
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
            importForwardedMembers(ForwardedModuleView.create(module, forward));
            if (module.transitivelyContainsCss()) {
                var loadedExtensions = new ArrayList<PendingExtension>();
                collectExtensions(module, loadedExtensions, new IdentityHashMap<>());
                pendingExtensions.addAll(loadedExtensions);
                extensionVisibilityModules.add(module);
                // Preserve defining-module origins so private placeholders and
                // upstream visibility still apply after CSS is re-emitted here.
                injectModuleCss(ModuleCss.combine(module), false);
            }
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "module failure message"),
                    forward.span(),
                    List.of(),
                    cause
            );
        }
    }

    /// Installs a module's public members into the current environment without a
    /// namespace (legacy {@code @import} visibility).
    ///
    /// Variables are shared as live {@link VariableBinding}s. Callables from a
    /// later import replace earlier ones with the same name.
    ///
    /// @param module the module whose members become local
    private void importModuleMembers(LoadedModule module) {
        for (var entry : module.variables().entrySet()) {
            environment.importVariableBinding(entry.getKey(), entry.getValue());
        }
        for (var entry : module.functions().entrySet()) {
            environment.setFunction(entry.getValue());
        }
        for (var entry : module.mixins().entrySet()) {
            environment.setMixin(entry.getValue());
        }
    }

    /// Installs a forward view's public members into the current environment
    /// without a namespace (legacy {@code @import} of a forwarding stylesheet).
    ///
    /// Prefix and show/hide filters from the forward rule are already applied by
    /// the view. Variables remain live bindings shared with the original module.
    ///
    /// @param view the transformed export view
    private void importForwardedMembers(ForwardedModuleView view) {
        for (var entry : view.variables().entrySet()) {
            environment.importVariableBinding(entry.getKey(), entry.getValue());
        }
        for (var entry : view.functions().entrySet()) {
            environment.setFunction(entry.getValue());
        }
        for (var entry : view.mixins().entrySet()) {
            environment.setMixin(entry.getValue());
        }
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
        var previousStyleRuleForParent = styleRuleForParent;
        var previousAtRootExcludingStyleRule = atRootExcludingStyleRule;
        var previousFontFace = fontFace;
        var previousMediaQueries = mediaQueries;
        var previousMediaQuerySources = mediaQuerySources;
        var previousDeclarationName = declarationName;
        var previousUrl = currentUrl;
        var previousConfiguration = currentConfiguration;
        var previousActive = stylesheetActive;
        var previousStyleRuleDepth = styleRuleDepth;
        var previousNestedDepth = nestedDeclarationDepth;
        var previousInKeyframes = inKeyframes;
        var previousLegacyImportDepth = legacyImportDepth;
        var previousPreModuleComments =
                new IdentityHashMap<>(preModuleComments);

        environment = new Environment();
        this.stylesheet = stylesheet;
        cssStylesheet = new CssStylesheet(stylesheet.span());
        cssParent = cssStylesheet;
        styleRule = null;
        styleRuleForParent = null;
        atRootExcludingStyleRule = false;
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
        inKeyframes = false;
        // Module bodies always use module-graph {@code @use} semantics, even when
        // the load was triggered under a legacy {@code @import}. Import-path CSS
        // re-emission applies only to {@code @use} written in the import body.
        legacyImportDepth = 0;
        preModuleComments.clear();
        var previousExtendableRules = new ArrayList<>(extendableStyleRules);
        var previousPendingExtensions = new ArrayList<>(pendingExtensions);
        var previousExtensionVisibility = new ArrayList<>(extensionVisibilityModules);
        extendableStyleRules.clear();
        pendingExtensions.clear();
        extensionVisibilityModules.clear();
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
                    environment.forwardedModules(),
                    Map.copyOf(preModuleComments),
                    List.copyOf(pendingExtensions)
            );
        } catch (RuntimeException failure) {
            environment = previousEnvironment;
            this.stylesheet = previousStylesheet;
            cssStylesheet = previousCss;
            cssParent = previousParent;
            styleRule = previousStyleRule;
            styleRuleForParent = previousStyleRuleForParent;
            atRootExcludingStyleRule = previousAtRootExcludingStyleRule;
            fontFace = previousFontFace;
            mediaQueries = previousMediaQueries;
            mediaQuerySources = previousMediaQuerySources;
            declarationName = previousDeclarationName;
            currentUrl = previousUrl;
            currentConfiguration = previousConfiguration;
            stylesheetActive = previousActive;
            styleRuleDepth = previousStyleRuleDepth;
            nestedDeclarationDepth = previousNestedDepth;
            inKeyframes = previousInKeyframes;
            legacyImportDepth = previousLegacyImportDepth;
            preModuleComments.clear();
            preModuleComments.putAll(previousPreModuleComments);
            extendableStyleRules.clear();
            extendableStyleRules.addAll(previousExtendableRules);
            pendingExtensions.clear();
            pendingExtensions.addAll(previousPendingExtensions);
            extensionVisibilityModules.clear();
            extensionVisibilityModules.addAll(previousExtensionVisibility);
            throw failure;
        } finally {
            if (!retainState) {
                environment = previousEnvironment;
                this.stylesheet = previousStylesheet;
                cssStylesheet = previousCss;
                cssParent = previousParent;
                styleRule = previousStyleRule;
                styleRuleForParent = previousStyleRuleForParent;
                atRootExcludingStyleRule = previousAtRootExcludingStyleRule;
                fontFace = previousFontFace;
                mediaQueries = previousMediaQueries;
                mediaQuerySources = previousMediaQuerySources;
                declarationName = previousDeclarationName;
                currentUrl = previousUrl;
                currentConfiguration = previousConfiguration;
                stylesheetActive = previousActive;
                styleRuleDepth = previousStyleRuleDepth;
                nestedDeclarationDepth = previousNestedDepth;
                inKeyframes = previousInKeyframes;
                legacyImportDepth = previousLegacyImportDepth;
                preModuleComments.clear();
                preModuleComments.putAll(previousPreModuleComments);
                extendableStyleRules.clear();
                extendableStyleRules.addAll(previousExtendableRules);
                pendingExtensions.clear();
                pendingExtensions.addAll(previousPendingExtensions);
                extensionVisibilityModules.clear();
                extensionVisibilityModules.addAll(previousExtensionVisibility);
            } else {
                extendableStyleRules.clear();
                pendingExtensions.clear();
                legacyImportDepth = previousLegacyImportDepth;
                // Keep extensionVisibilityModules for root executeRoot apply phase.
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

    /// Fails when an explicit configuration contains an unused value.
    ///
    /// @param configuration the configuration to inspect
    /// @param nameInError   whether diagnostics include the {@code $name} prefix
    ///                      ({@code true} for {@code meta.load-css()}, {@code false}
    ///                      for {@code @use}/{@code @forward} matching dart-sass)
    /// @throws EvaluationException if an explicit value remains unconsumed
    private static void assertConfigurationConsumed(
            ModuleConfiguration configuration,
            boolean nameInError
    ) {
        @Nullable var unused = configuration.firstUnusedEntry();
        if (unused != null && configuration.isExplicit()) {
            String message = nameInError
                    ? "$" + unused.getKey()
                    + " was not declared with !default in the @used module."
                    : "This variable was not declared with !default in the @used module.";
            throw new EvaluationException(
                    message,
                    unused.getValue().configurationSpan()
            );
        }
    }

    /// Fails when an explicit {@code @use}/{@code @forward} configuration is unused.
    ///
    /// @param configuration the configuration to inspect
    private static void assertConfigurationConsumed(ModuleConfiguration configuration) {
        assertConfigurationConsumed(configuration, false);
    }

    /// Executes each legacy Sass or static CSS import argument in source order.
    ///
    /// @param statement the import rule
    /// @return the continue result
    @Override
    public StatementResult visitImportRule(ImportRule statement) {
        for (var importArgument : statement.imports()) {
            if (importArgument instanceof DynamicImport dynamic) {
                if (moduleRegistry == null) {
                    throw new EvaluationException(
                            "Stylesheet loading isn't available.",
                            dynamic.span()
                    );
                }
                // Install imported members into the current lexical frame.
                // Nested style rules already open their own frames, so members
                // remain usable after the import within the rule and do not
                // leak once that frame closes (dart-sass nested @import).
                moduleRegistry.loadImport(
                        dynamic.url(),
                        currentUrl,
                        dynamic.span(),
                        this
                );
                continue;
            }

            var staticImport = (StaticImport) importArgument;
            var argument = new StringBuilder(performInterpolation(staticImport.url()));
            if (staticImport.modifiersBeforeSupports() != null) {
                appendImportModifier(
                        argument,
                        performInterpolation(staticImport.modifiersBeforeSupports())
                );
            }
            if (staticImport.supports() != null) {
                var supports = evaluateSupportsCondition(staticImport.supports()).strip();
                if (supports.isEmpty()) {
                    throw new EvaluationException(
                            "Expected @supports condition.",
                            staticImport.supports().span()
                    );
                }
                if (staticImport.supports() instanceof SupportsDeclaration) {
                    supports = supports.substring(1, supports.length() - 1);
                }
                appendImportModifier(argument, "supports(" + supports + ")");
            }
            if (staticImport.modifiersAfterSupports() != null) {
                appendImportModifier(
                        argument,
                        performInterpolation(staticImport.modifiersAfterSupports())
                );
            }
            var cssImport = new CssImport(argument.toString(), staticImport.span());
            var parent = requireCssParent();
            if (parent instanceof CssStylesheet root) {
                root.addImport(cssImport);
            } else {
                addCssChild(cssImport, false);
            }
        }
        return StatementResult.CONTINUE;
    }

    /// Appends one non-empty static-import modifier with canonical separation.
    ///
    /// @param argument the import argument under construction
    /// @param modifier the evaluated modifier text
    private static void appendImportModifier(StringBuilder argument, String modifier) {
        var stripped = modifier.strip();
        if (stripped.isEmpty()) {
            return;
        }
        argument.append(' ').append(stripped);
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
            var loadedBefore = moduleRegistry.loadedModuleCount();
            var module = moduleRegistry.load(
                    statement.url(),
                    currentUrl,
                    statement.span(),
                    this,
                    configuration,
                    !statement.configuration().isEmpty()
            );
            boolean newlyLoaded = moduleRegistry.loadedModuleCount() > loadedBefore;
            if (legacyImportDepth > 0) {
                // {@code @use} inside {@code @import} emits CSS at the import site
                // (nested under any active style rule) and must not also register
                // the module on the root CSS graph, or ModuleCss.combine would
                // re-emit it unnested at the root. Extensions still need to be
                // collected into the importer's pending list so root-level
                // applyExtensions can rewrite the injected CSS (dart-sass
                // compound_through_import / isolated import+use chains).
                if (module.transitivelyContainsCss()) {
                    var loadedExtensions = new ArrayList<PendingExtension>();
                    collectExtensions(module, loadedExtensions, new IdentityHashMap<>());
                    pendingExtensions.addAll(loadedExtensions);
                    extensionVisibilityModules.add(module);
                    // Keep original module origins on cloned style rules so
                    // {@code @extend} visibility matches the used module graph.
                    injectModuleCss(ModuleCss.combine(module), false);
                }
                environment.addModule(module, statement.namespace(), statement.span(), false);
            } else {
                if (newlyLoaded) {
                    registerCommentsForModule(module);
                }
                environment.addModule(module, statement.namespace(), statement.span(), true);
            }
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

    /// Moves top-level loud comments in front of an upstream module's CSS.
    ///
    /// @param module the newly loaded upstream module
    private void registerCommentsForModule(LoadedModule module) {
        if (cssStylesheet == null || !module.transitivelyContainsCss()) {
            return;
        }
        var comments = cssStylesheet.takeComments();
        if (comments.isEmpty()) {
            return;
        }
        preModuleComments
                .computeIfAbsent(module, ignored -> new ArrayList<>())
                .addAll(comments);
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

    /// Evaluates a font-face rule and executes its descriptor body.
    ///
    /// Nested {@code @font-face} rules bubble through enclosing style rules to
    /// the stylesheet root, matching dart-sass. Nesting under other at-rules
    /// remains rejected.
    ///
    /// @param statement the font-face rule
    /// @return the continue result, [StatementResult#CONTINUE]
    @Override
    public StatementResult visitFontFaceRule(FontFaceRule statement) {
        var rule = new CssFontFace(statement.span());
        // Bubble through style rules so nested font-face lands beside the
        // outermost style rule rather than remaining nested.
        addCssChild(rule, true);
        if (!(rule.parent() instanceof CssStylesheet)) {
            throw new EvaluationException(
                    "@font-face rules may only be used at the stylesheet root.",
                    statement.span()
            );
        }
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

        // Resume outer style-rule declarations after the bubbled font-face.
        copyParentAfterSibling();
        if (!previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
        }
        return StatementResult.CONTINUE;
    }

    /// Evaluates an {@code @media} rule and resolves nesting through CSS media queries.
    ///
    /// Compatible nested queries are merged and bubbled through their source
    /// media rules. Queries whose intersection cannot be expressed by one CSS
    /// query list remain structurally nested. Once native CSS nesting is active,
    /// the rule stays in place without merge or bubbling.
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
            // Plain (non-interpolated) media queries normalize nested and/or/not
            // keywords like dart-sass; interpolated text keeps author casing.
            queries = CssMediaQuery.parseList(
                    performInterpolation(statement.query()),
                    statement.query().asPlain() != null
            );
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "media query failure message"),
                    statement.query().span(),
                    List.of(),
                    cause
            );
        }

        // Native CSS nesting and {@code @keyframes} blocks keep {@code @media}
        // in place. Bubbling out of a keyframe selector (e.g. {@code to}) would
        // wrap the keyframe step in media, which dart-sass does not do.
        if (hasCssNesting() || inKeyframes) {
            var nestedRule = new CssMediaRule(queries, statement.span());
            addCssChild(nestedRule, false);
            var previousParent = requireCssParent();
            cssParent = nestedRule;
            try {
                for (var child : statement.children()) {
                    child.accept(this);
                }
            } finally {
                cssParent = previousParent;
            }
            return StatementResult.CONTINUE;
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

        // dart-sass visitMediaRule does not mark media rules as group ends, so
        // consecutive root-level {@code @media} blocks are not separated by a
        // blank line in expanded output.
        return StatementResult.CONTINUE;
    }

    /// Evaluates an {@code @supports} rule while preserving its CSS condition.
    ///
    /// The rule bubbles through enclosing style rules but remains inside other
    /// conditional rules. This preserves CSS conditional nesting while allowing
    /// nested Sass declarations to be emitted through a style-rule wrapper. Once
    /// native CSS nesting is active, the rule stays in place without bubbling.
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
                    "Expected @supports condition.",
                    statement.condition().span()
            );
        }

        if (hasCssNesting()) {
            var nestedRule = new CssSupportsRule(condition, statement.span());
            addCssChild(nestedRule, false);
            var previousParent = requireCssParent();
            cssParent = nestedRule;
            try {
                for (var child : statement.children()) {
                    child.accept(this);
                }
            } finally {
                cssParent = previousParent;
            }
            return StatementResult.CONTINUE;
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

        // dart-sass visitSupportsRule does not mark supports rules as group ends.
        return StatementResult.CONTINUE;
    }

    /// Emits an opaque plain-CSS at-rule and evaluates its optional block.
    ///
    /// Childless rules stay under the current parent. Block rules bubble through
    /// enclosing style rules unless native CSS nesting is already active. When
    /// bubbling from a style rule, that rule is copied into the at-rule so nested
    /// declarations keep a selector. {@code @keyframes} bodies are not wrapped.
    ///
    /// @param statement the opaque at-rule
    /// @return the continue result
    @Override
    public StatementResult visitUnknownAtRule(UnknownAtRule statement) {
        var name = performInterpolation(statement.name()).strip();
        var rule = new CssUnknownAtRule(
                name,
                performInterpolation(statement.value()).strip(),
                statement.hasChildren(),
                statement.span()
        );
        if (statement.children() == null) {
            copyParentAfterSibling();
            requireCssParent().addChild(rule);
            return StatementResult.CONTINUE;
        }

        var nestInPlace = hasCssNesting();
        addCssChild(rule, !nestInPlace);
        var previousParent = requireCssParent();
        var previousInKeyframes = inKeyframes;
        @Nullable CssStyleRule activeStyleRule = styleRule;
        if (isKeyframesName(name)) {
            inKeyframes = true;
        }
        cssParent = rule;
        var scope = environment.scope(
                ScopeSemantics.LEXICAL,
                hasDirectDeclarations(statement.children())
        );
        try {
            if (nestInPlace
                    || activeStyleRule == null
                    || inKeyframes
                    || name.equalsIgnoreCase("font-face")) {
                for (var child : statement.children()) {
                    child.accept(this);
                }
            } else {
                var wrapper = activeStyleRule.copyWithoutChildren();
                rule.addChild(wrapper);
                var atRuleParent = requireCssParent();
                cssParent = wrapper;
                try {
                    for (var child : statement.children()) {
                        child.accept(this);
                    }
                } finally {
                    cssParent = atRuleParent;
                }
            }
        } finally {
            try {
                scope.close();
            } finally {
                inKeyframes = previousInKeyframes;
                cssParent = previousParent;
            }
        }
        if (activeStyleRule == null && !previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
        }
        return StatementResult.CONTINUE;
    }

    /// Returns whether {@code name} is {@code keyframes}, ignoring a vendor prefix.
    ///
    /// @param name the at-rule name without {@code @}
    /// @return whether the rule is a keyframes rule
    private static boolean isKeyframesName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.equals("keyframes")) {
            return true;
        }
        if (!name.startsWith("-")) {
            return false;
        }
        var secondDash = name.indexOf('-', 1);
        return secondDash > 1 && name.substring(secondDash + 1).equals("keyframes");
    }

    /// Evaluates a selector, emits a CSS style rule, and executes its children.
    ///
    /// Nested Sass style rules bubble through existing style-rule parents so the
    /// CSS IR remains a flat sequence of resolved rules. Plain-CSS nesting keeps
    /// nested rules in place and may leave parent selectors unexpanded.
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
        // Empty interpolation such as {@code #{&}} outside a style rule yields
        // an empty selector, which dart-sass reports as "expected selector."
        if (selectorText.isBlank()) {
            throw new EvaluationException(
                    "expected selector.",
                    statement.selector().span()
            );
        }
        SelectorList parsed;
        try {
            parsed = SelectorList.parse(
                    selectorText,
                    statement.selector().span(),
                    isPlainCss(),
                    inKeyframes
            );
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "selector failure message"),
                    statement.selector().span(),
                    List.of(),
                    cause
            );
        }
        if (isPlainCss() && containsPlaceholderSelector(parsed)) {
            throw new EvaluationException(
                    "Placeholder selectors aren't allowed in plain CSS.",
                    statement.selector().span()
            );
        }
        if (isPlainCss() && parsed.hasParentSelectorSuffix()) {
            throw new EvaluationException(
                    "Parent selectors can't have suffixes in plain CSS.",
                    statement.selector().span()
            );
        }
        // Leading combinators are legal relative selectors in Sass (with
        // deprecation) and under native plain-CSS nesting. They are forbidden
        // as root statements of a plain CSS stylesheet — including when that
        // file is nested under a Sass style rule via {@code @import}.
        if (isPlainCss()
                && hasLeadingCombinator(parsed)
                && (styleRuleForParent == null
                        || atRootExcludingStyleRule
                        || !styleRuleForParent.fromPlainCss())) {
            throw new EvaluationException(
                    "Top-level leading combinators aren't allowed in plain CSS.",
                    statement.selector().span()
            );
        }

        if (inKeyframes) {
            // Nested style rules inside an existing keyframe selector ({@code from}/
            // {@code to}/percent) are invalid. Keyframe selectors themselves are
            // still allowed when the current parent is the {@code @keyframes} rule
            // (even if an outer style rule is still on the evaluation stack).
            if (styleRule != null && requireCssParent() == styleRule) {
                throw new EvaluationException(
                        "Style rules may not be used within keyframe blocks.",
                        statement.span()
                );
            }
            var keyframeRule = new CssStyleRule(
                    new CssValue<>(parsed, statement.selector().span()),
                    statement.span()
            );
            addCssChild(keyframeRule, true);
            var previousParent = requireCssParent();
            var previousStyleRule = styleRule;
            cssParent = keyframeRule;
            styleRule = keyframeRule;
            styleRuleDepth++;
            // Style rules always open a lexical frame so nested @import members
            // and local declarations stay confined to the rule body.
            var scope = environment.scope(ScopeSemantics.LEXICAL, true);
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
            return StatementResult.CONTINUE;
        }

        @Nullable CssStyleRule effectiveStyleRule =
                atRootExcludingStyleRule ? null : styleRule;
        boolean merge;
        if (effectiveStyleRule == null) {
            merge = true;
        } else if (effectiveStyleRule.fromPlainCss()) {
            merge = false;
        } else {
            merge = !(isPlainCss() && parsed.containsParentSelector());
        }

        @Nullable SelectorList parentSelector =
                styleRuleForParent == null ? null : styleRuleForParent.selector().value();
        boolean implicitParent = !atRootExcludingStyleRule;
        SelectorList nestedSelector;
        try {
            if (!merge) {
                nestedSelector = parsed;
            } else if (isPlainCss() && parentSelector == null) {
                nestedSelector = parsed;
            } else {
                nestedSelector = parsed.nestWithin(parentSelector, implicitParent);
            }
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
                statement.span(),
                isPlainCss(),
                // Snapshot media active at definition time (not after bubbling).
                mediaQueries
        );
        styleRuleOrigins.put(rule, currentUrl);
        addCssChild(rule, merge);
        if (!isPlainCss()) {
            extendableStyleRules.add(rule);
        }

        var previousParent = requireCssParent();
        var previousStyleRule = styleRule;
        var previousStyleRuleForParent = styleRuleForParent;
        var previousAtRootExcludingStyleRule = atRootExcludingStyleRule;
        cssParent = rule;
        styleRule = rule;
        styleRuleForParent = rule;
        atRootExcludingStyleRule = false;
        styleRuleDepth++;
        // Always open a frame: nested @import injects members that must not
        // leak after the rule ends (even when the body has no local decls).
        var scope = environment.scope(ScopeSemantics.LEXICAL, true);
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
                styleRuleForParent = previousStyleRuleForParent;
                atRootExcludingStyleRule = previousAtRootExcludingStyleRule;
                cssParent = previousParent;
            }
        }

        if (previousStyleRule == null && !previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
        }
        return StatementResult.CONTINUE;
    }

    /// Records one `@extend` against the active style rule for later application.
    ///
    /// @param statement the extend rule
    /// @return the continue result
    @Override
    public StatementResult visitExtendRule(ExtendRule statement) {
        if (styleRule == null) {
            throw new EvaluationException(
                    "@extend may only be used within style rules.",
                    statement.span()
            );
        }
        var selectorText = performInterpolation(statement.selector()).strip();
        SelectorList target;
        try {
            target = SelectorList.parse(selectorText, statement.selector().span());
            SelectorAlgebra.assertExtendDirectiveTargets(target);
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "selector failure message"),
                    statement.selector().span(),
                    List.of(),
                    cause
            );
        }
        pendingExtensions.add(new PendingExtension(
                styleRule.selector().value(),
                target,
                statement.optional(),
                mediaQueries,
                currentUrl,
                statement.span()
        ));
        return StatementResult.CONTINUE;
    }

    /// Evaluates an {@code @at-root} rule under a trimmed CSS parent path.
    ///
    /// @param statement the at-root rule
    /// @return the continue result
    @Override
    public StatementResult visitAtRootRule(AtRootRule statement) {
        AtRootQuery query;
        try {
            query = statement.query() == null
                    ? AtRootQuery.DEFAULT
                    : AtRootQuery.parse(performInterpolation(statement.query()).strip());
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "at-root query failure"),
                    statement.query() == null ? statement.span() : statement.query().span(),
                    List.of(),
                    cause
            );
        }

        var included = new ArrayList<CssParentNode>();
        var current = requireCssParent();
        while (true) {
            if (!query.excludes(current)) {
                included.add(current);
            }
            @Nullable CssParentNode parent = current.parent();
            if (parent == null) {
                break;
            }
            current = parent;
        }

        var previousParent = requireCssParent();
        var previousStyleRule = styleRule;
        var previousAtRootExcludingStyleRule = atRootExcludingStyleRule;
        var previousMediaQueries = mediaQueries;
        var previousMediaQuerySources = mediaQuerySources;
        var previousInKeyframes = inKeyframes;

        CssParentNode root;
        if (included.isEmpty()) {
            root = Objects.requireNonNull(cssStylesheet, "css");
        } else {
            root = included.get(included.size() - 1);
            for (var index = included.size() - 2; index >= 0; index--) {
                var copy = included.get(index).copyWithoutChildren();
                root.addChild(copy);
                root = copy;
            }
        }

        cssParent = root;
        if (query.excludesStyleRules()) {
            atRootExcludingStyleRule = true;
            styleRule = null;
        }
        if (query.excludesName("media")) {
            mediaQueries = null;
            mediaQuerySources = null;
        }
        if (query.excludesName("keyframes")) {
            inKeyframes = false;
        }

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
                cssParent = previousParent;
                styleRule = previousStyleRule;
                atRootExcludingStyleRule = previousAtRootExcludingStyleRule;
                mediaQueries = previousMediaQueries;
                mediaQuerySources = previousMediaQuerySources;
                inKeyframes = previousInKeyframes;
            }
        }
        return StatementResult.CONTINUE;
    }

    /// Collects `@extend` directives from one module graph in dependency order.
    ///
    /// @param module     the module to visit
    /// @param extensions the accumulator
    /// @param seen       modules already visited
    private static void collectExtensions(
            LoadedModule module,
            ArrayList<PendingExtension> extensions,
            IdentityHashMap<LoadedModule, Boolean> seen
    ) {
        if (seen.put(module, Boolean.TRUE) != null) {
            return;
        }
        for (var upstream : module.upstream()) {
            collectExtensions(upstream, extensions, seen);
        }
        extensions.addAll(module.extensions());
    }

    /// Collects every style rule under a CSS parent.
    ///
    /// @param parent the parent to walk
    /// @param rules  the accumulator
    private static void collectStyleRules(
            CssParentNode parent,
            ArrayList<CssStyleRule> rules
    ) {
        for (var child : parent.children()) {
            if (child instanceof CssStyleRule styleRule) {
                rules.add(styleRule);
                collectStyleRules(styleRule, rules);
            } else if (child instanceof CssParentNode nested) {
                collectStyleRules(nested, rules);
            }
        }
    }

    /// Applies collected `@extend` directives across a complete stylesheet.
    ///
    /// @param styleRules  every style rule in the combined CSS tree
    /// @param extensions  every extension from the module graph
    /// @param root        the entry module used for upstream reachability
    private void applyExtensions(
            List<CssStyleRule> styleRules,
            List<PendingExtension> extensions,
            LoadedModule root
    ) {
        if (extensions.isEmpty()) {
            return;
        }
        var modulesByUrl = indexModulesByUrl(root);
        // Modules re-emitted outside the root CSS graph (import-path {@code @use})
        // still need to be visible for extension reachability checks.
        var seenVisibility = new IdentityHashMap<LoadedModule, Boolean>();
        for (var extra : extensionVisibilityModules) {
            indexModulesByUrl(extra, modulesByUrl, seenVisibility);
        }
        for (var extension : extensions) {
            var found = false;
            for (var rule : styleRules) {
                var result = applyExtensionToRule(
                        extension,
                        rule,
                        true,
                        modulesByUrl
                );
                found |= result.found();
            }
            // Fixed-point for extension chains introduced by earlier matches.
            var changed = true;
            while (changed) {
                changed = false;
                for (var rule : styleRules) {
                    var result = applyExtensionToRule(
                            extension,
                            rule,
                            false,
                            modulesByUrl
                    );
                    found |= result.found();
                    changed |= result.changed();
                }
            }
            // "Found" means a matching compound existed, even when unification
            // produced no additional selector alternative (namespace/universal
            // cases that keep the original form only).
            if (!found && !extension.optional()) {
                throw new EvaluationException(
                        "The target selector was not found.\n"
                                + "Use \"@extend " + extension.target().toCssString()
                                + " !optional\" to avoid this error.",
                        extension.span()
                );
            }
        }
        for (var rule : styleRules) {
            var stripped = stripPlaceholderComplexes(rule.selector().value());
            if (!selectorCssEquals(rule.selector().value(), stripped)) {
                rule.setSelector(new CssValue<>(stripped, rule.selector().span()));
            }
        }
    }

    /// Indexes every module in the dependency graph by canonical URL.
    private static Map<URI, LoadedModule> indexModulesByUrl(LoadedModule root) {
        var result = new LinkedHashMap<URI, LoadedModule>();
        var seen = new IdentityHashMap<LoadedModule, Boolean>();
        indexModulesByUrl(root, result, seen);
        return result;
    }

    private static void indexModulesByUrl(
            LoadedModule module,
            Map<URI, LoadedModule> result,
            IdentityHashMap<LoadedModule, Boolean> seen
    ) {
        if (seen.put(module, Boolean.TRUE) != null) {
            return;
        }
        if (module.url() != null) {
            result.putIfAbsent(module.url(), module);
        }
        for (var upstream : module.upstream()) {
            indexModulesByUrl(upstream, result, seen);
        }
    }

    /// Returns whether an extension from {@code originUrl} may rewrite a rule
    /// defined in {@code ruleUrl}.
    ///
    /// An extension sees its own module and every module that module transitively
    /// {@code @use}s. Sibling modules are invisible to each other.
    private static boolean moduleCanExtend(
            @Nullable URI originUrl,
            @Nullable URI ruleUrl,
            Map<URI, LoadedModule> modulesByUrl
    ) {
        if (Objects.equals(originUrl, ruleUrl)) {
            return true;
        }
        // Anonymous roots can see every rule in the final combined stylesheet.
        if (originUrl == null) {
            return true;
        }
        @Nullable LoadedModule origin = modulesByUrl.get(originUrl);
        if (origin == null) {
            return Objects.equals(originUrl, ruleUrl);
        }
        return moduleReachable(origin, ruleUrl, new IdentityHashMap<>());
    }

    /// Returns whether {@code ruleUrl} is {@code module} or one of its upstreams.
    private static boolean moduleReachable(
            LoadedModule module,
            @Nullable URI ruleUrl,
            IdentityHashMap<LoadedModule, Boolean> seen
    ) {
        if (seen.put(module, Boolean.TRUE) != null) {
            return false;
        }
        if (Objects.equals(module.url(), ruleUrl)) {
            return true;
        }
        for (var upstream : module.upstream()) {
            if (moduleReachable(upstream, ruleUrl, seen)) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether an extension target is private to its defining module.
    ///
    /// Private selectors are placeholders, classes, or IDs whose names begin with
    /// {@code -} or {@code _}.
    private static boolean isPrivateTarget(SelectorList target) {
        for (var complex : target.components()) {
            for (var component : complex.components()) {
                for (var simple : component.selector().components()) {
                    if (isPrivateSimple(simple)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isPrivateSimple(
            org.glavo.scssfx.internal.ast.selector.SimpleSelector simple
    ) {
        String name;
        if (simple instanceof org.glavo.scssfx.internal.ast.selector.PlaceholderSelector placeholder) {
            name = placeholder.name().value();
        } else if (simple instanceof org.glavo.scssfx.internal.ast.selector.ClassSelector classSelector) {
            name = classSelector.name().value();
        } else if (simple instanceof org.glavo.scssfx.internal.ast.selector.IdSelector idSelector) {
            name = idSelector.name().value();
        } else {
            return false;
        }
        return !name.isEmpty() && (name.charAt(0) == '-' || name.charAt(0) == '_');
    }

    /// Result of attempting to apply one extension to one style rule.
    ///
    /// @param found   whether the extension target matched a compound in the rule
    /// @param changed whether the rule selector was rewritten
    private record ExtensionApplyResult(boolean found, boolean changed) {
        static final ExtensionApplyResult NONE = new ExtensionApplyResult(false, false);
        static final ExtensionApplyResult FOUND_ONLY = new ExtensionApplyResult(true, false);
    }

    /// Applies one extension to one style rule when media contexts allow it.
    ///
    /// @param extension     the pending extension
    /// @param rule          the candidate style rule
    /// @param rejectCrossMedia whether a cross-media match should error
    /// @param modulesByUrl  modules keyed by canonical URL for visibility checks
    /// @return whether the target was found and whether the rule selector changed
    private ExtensionApplyResult applyExtensionToRule(
            PendingExtension extension,
            CssStyleRule rule,
            boolean rejectCrossMedia,
            Map<URI, LoadedModule> modulesByUrl
    ) {
        @Nullable URI ruleOrigin = styleRuleOrigins.get(rule);
        // Private targets may only be extended inside the defining module.
        if (isPrivateTarget(extension.target())
                && !Objects.equals(extension.originUrl(), ruleOrigin)) {
            return ExtensionApplyResult.NONE;
        }
        if (!moduleCanExtend(extension.originUrl(), ruleOrigin, modulesByUrl)) {
            return ExtensionApplyResult.NONE;
        }
        var before = rule.selector().value();
        if (!SelectorAlgebra.containsExtendee(before, extension.target())) {
            return ExtensionApplyResult.NONE;
        }
        SelectorList after;
        try {
            after = SelectorAlgebra.extend(
                    before,
                    extension.target(),
                    extension.extender()
            );
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "extend failure message"),
                    extension.span(),
                    List.of(),
                    cause
            );
        }
        if (selectorCssEquals(before, after)) {
            return ExtensionApplyResult.FOUND_ONLY;
        }
        if (!mediaContextsCompatible(extension.mediaContext(), mediaContextOf(rule))) {
            if (rejectCrossMedia) {
                throw new EvaluationException(
                        "You may not @extend selectors across media queries.",
                        extension.span()
                );
            }
            // Target matched but media forbids rewriting; still counts as found.
            return ExtensionApplyResult.FOUND_ONLY;
        }
        rule.setSelector(new CssValue<>(after, rule.selector().span()));
        return new ExtensionApplyResult(true, true);
    }

    /// Returns the media context used for {@code @extend} compatibility checks.
    ///
    /// Uses the media queries active when the style rule was defined, not the
    /// CSS parent after nested {@code @media} bubbling. That preserves forms
    /// such as {@code %foo { @media … { … } }} extended from outside media.
    ///
    /// @param rule the style rule
    /// @return the defining media queries, or {@code null}
    private static @Nullable List<CssMediaQuery> mediaContextOf(CssStyleRule rule) {
        return rule.definingMediaContext();
    }

    /// Returns whether two media contexts may exchange extensions.
    ///
    /// @param left  the first context, or {@code null} outside media
    /// @param right the second context, or {@code null} outside media
    /// @return whether the contexts are compatible
    private static boolean mediaContextsCompatible(
            @Nullable List<CssMediaQuery> left,
            @Nullable List<CssMediaQuery> right
    ) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    /// Returns whether two selector lists serialize identically.
    private static boolean selectorCssEquals(SelectorList left, SelectorList right) {
        return left.toCssString().equals(right.toCssString());
    }

    /// Removes CSS-invisible complexes from a selector list after extensions.
    ///
    /// Invisible complexes include pure placeholders, compounds that contain
    /// placeholders, and selector-taking pseudos other than {@code :not} whose
    /// arguments are entirely invisible. Remaining complexes still retain
    /// placeholders inside {@code :not} and mixed selector-taking pseudos; CSS
    /// serialization drops or rewrites those forms when emitting output.
    private static SelectorList stripPlaceholderComplexes(SelectorList selectors) {
        if (!selectors.isInvisible()
                && selectors.components().stream().noneMatch(ComplexSelector::isInvisible)) {
            return selectors;
        }
        var kept = new ArrayList<ComplexSelector>();
        for (var complex : selectors.components()) {
            if (!complex.isInvisible()) {
                kept.add(complex);
            }
        }
        if (kept.size() == selectors.components().size()) {
            return selectors;
        }
        // Pure-placeholder complexes (e.g. {@code %foobar} from {@code %foo { &bar }})
        // may leave nothing visible; keep the original invisible list so the CSS
        // rule is omitted rather than building an empty selector list.
        if (kept.isEmpty()) {
            return selectors;
        }
        return new SelectorList(kept, selectors.span());
    }

    /// Returns whether the active CSS position already uses native nesting.
    ///
    /// Native nesting is active once a style rule is nested inside another style
    /// rule, possibly through intervening conditional parents.
    ///
    /// @return whether merge and bubbling should be skipped
    private boolean hasCssNesting() {
        @Nullable CssParentNode current = styleRule;
        while (current != null) {
            @Nullable CssParentNode parent = current.parent();
            if (parent instanceof CssStyleRule) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    /// Returns whether a selector list contains a Sass placeholder selector.
    ///
    /// @param selectors the selector list to inspect
    /// @return whether any compound contains a placeholder
    private static boolean containsPlaceholderSelector(SelectorList selectors) {
        for (var complex : selectors.components()) {
            for (var component : complex.components()) {
                for (var simple : component.selector().components()) {
                    if (simple instanceof PlaceholderSelector) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /// Returns whether any complex selector begins with a leading combinator.
    ///
    /// @param selectors the selector list to inspect
    /// @return whether a leading combinator is present
    private static boolean hasLeadingCombinator(SelectorList selectors) {
        for (var complex : selectors.components()) {
            if (!complex.leadingCombinators().isEmpty()) {
                return true;
            }
        }
        return false;
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
        if (styleRule == null
                && fontFace == null
                && !(requireCssParent() instanceof CssUnknownAtRule)) {
            throw new EvaluationException(
                    "Declarations may only be used within style rules.",
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
        if (isPlainCss()) {
            throw new EvaluationException(
                    "Sass variables aren't allowed in plain CSS.",
                    statement.nameSpan()
            );
        }
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
        // Use long bounds so inclusive endpoints near Integer.MAX_VALUE/MIN_VALUE do not
        // overflow and turn the loop into an infinite wraparound.
        long start = fromInt;
        long stop = toInt;
        long direction = start > stop ? -1L : 1L;
        if (!statement.exclusive()) {
            stop += direction;
        }
        if (start == stop) {
            return StatementResult.CONTINUE;
        }

        var origin = expressionOrigin(statement.from());
        long end = stop;
        return inFlowScope(true, () -> {
            for (long index = start; index != end; index += direction) {
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

    /// Evaluates a parent-selector expression as the current style-rule selector.
    ///
    /// Outside a style rule, {@code &} evaluates to Sass {@code null} so
    /// constructs such as {@code @if &} are false and {@code #{&}} expands to
    /// empty text (which then fails selector parsing with
    /// {@code expected selector.}). Inside a style rule, the returned value
    /// uses the same nested list form as {@code selector.parse()}.
    ///
    /// @param expression the parent-selector expression
    /// @return the parent selector list, or Sass null outside a style rule
    @Override
    public SassValue visitSelectorExpression(SelectorExpression expression) {
        Objects.requireNonNull(expression, "expression");
        if (styleRuleForParent == null) {
            return SassNull.NULL;
        }
        return selectorListAsSassValue(styleRuleForParent.selector().value());
    }

    /// Converts a resolved selector list into Sass's nested list representation.
    ///
    /// @param selector the style-rule selector
    /// @return a comma-separated list of space-separated complex-selector parts
    private static SassList selectorListAsSassValue(
            org.glavo.scssfx.internal.ast.selector.SelectorList selector
    ) {
        var complexes = new ArrayList<SassValue>();
        for (var complex : selector.components()) {
            var parts = new ArrayList<SassValue>();
            for (var combinator : complex.leadingCombinators()) {
                parts.add(new SassString(combinator.css(), false));
            }
            for (var component : complex.components()) {
                parts.add(new SassString(component.selector().toCssString(), false));
                for (var combinator : component.combinators()) {
                    parts.add(new SassString(combinator.css(), false));
                }
            }
            complexes.add(new SassList(parts, ListSeparator.SPACE, false));
        }
        return new SassList(complexes, ListSeparator.COMMA, false);
    }

    /// Resolves a variable from the current lexical or module environment.
    ///
    /// @param expression the variable reference
    /// @return the bound value
    /// @throws EvaluationException if the variable or namespace is undefined
    @Override
    public SassValue visitVariableExpression(VariableExpression expression) {
        if (isPlainCss()) {
            throw new EvaluationException(
                    "Sass variables aren't allowed in plain CSS.",
                    expression.span()
            );
        }
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
    /// Plain-CSS stylesheets reject namespaced and Sass-only built-in functions.
    /// Native calculation syntax is retained verbatim; other native calls
    /// evaluate their arguments without resolving Sass functions.
    ///
    /// @param expression the function expression
    /// @return the function result
    /// @throws EvaluationException if invocation fails
    @Override
    public SassValue visitFunctionExpression(FunctionExpression expression) {
        if (isPlainCss()) {
            if (expression.namespace() != null) {
                throw new EvaluationException(
                        "Module namespaces aren't allowed in plain CSS.",
                        expression.span()
                );
            }
            var name = expression.name();
            if (BUILT_IN_FUNCTIONS.containsKey(name)
                    && !PLAIN_CSS_ALLOWED_FUNCTIONS.contains(name)) {
                throw new EvaluationException(
                        "This function isn't allowed in plain CSS.",
                        expression.span()
                );
            }
            // Reject Sass-only argument forms before any plain-CSS callable path.
            assertPlainCssArguments(expression.arguments(), expression.span());
            if (PLAIN_CSS_CALCULATION_FUNCTIONS.contains(name)) {
                assertPlainCssCalculationCall(expression);
                // Evaluate calculations so simplifiable forms such as
                // {@code calc(1px)} become {@code 1px}. Non-simplifying
                // operations keep a calculation value that serializes with the
                // same operators; spacing may differ slightly from source.
                return visitCalculation(expression, null);
            }
            return runCallable(
                    new PlainCssCallable(expression.originalName()),
                    expression.arguments(),
                    expression.span()
            );
        }

        // Local/user-defined functions shadow calculation and built-in names.
        // Call sites whose original name begins with {@code --} are always plain
        // CSS (dart-sass), even when a Sass function was registered under the
        // underscore-normalized form ({@code @function __a} does not capture
        // {@code --a()}).
        @Nullable Callable callable = null;
        boolean forcePlainCss = expression.originalName().startsWith("--");
        if (!forcePlainCss) {
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
        } else if (expression.namespace() != null) {
            throw new EvaluationException("Undefined function.", expression.span());
        }
        if (callable == null && expression.namespace() == null) {
            var lowerName = expression.name().toLowerCase(java.util.Locale.ROOT);
            if (CALCULATION_FUNCTIONS.contains(lowerName)) {
                return visitCalculation(expression, null);
            }
            if (LEGACY_CALCULATION_FUNCTIONS.contains(lowerName)
                    && expression.arguments().named().isEmpty()
                    && expression.arguments().rest() == null
                    && expression.arguments().positional().stream().allMatch(this::isCalculationSafe)) {
                // Global min/max/round/abs use calculation evaluation when every
                // argument is calculation-safe, with legacy unitless mixing allowed
                // inside operate() for deprecation compatibility with dart-sass.
                return visitCalculation(expression, lowerName);
            }
            if (!forcePlainCss) {
                callable = BUILT_IN_FUNCTIONS.get(expression.name());
            }
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
    /// Argument binding matches the pseudo-declaration
    /// {@code @function if($condition, $if-true, $if-false)}. Only the selected
    /// branch is evaluated.
    ///
    /// @param expression the if expression
    /// @return the selected branch value
    @Override
    public SassValue visitLegacyIfExpression(LegacyIfExpression expression) {
        reportDeprecation(
                "The Sass if() syntax is deprecated in favor of the modern CSS syntax.\n\n"
                        + "More info: https://sass-lang.com/d/if-function",
                "if-function",
                expression.span()
        );
        var arguments = expression.arguments();
        if (arguments.keywordRest() != null) {
            throw new EvaluationException(
                    "Only 3 positional arguments are allowed in if().",
                    expression.span()
            );
        }
        // Rest arguments must be expanded before binding. Evaluating the rest
        // eagerly matches dart-sass (short-circuit still applies to named /
        // pure-positional branches without a rest splat).
        if (arguments.rest() != null) {
            return evaluateLegacyIfWithRest(expression);
        }
        var positional = arguments.positional();
        var named = new LinkedHashMap<>(arguments.named());
        // Bind required parameters first so missing-argument diagnostics win over
        // excess-argument checks for partial invocations such as if().
        var conditionExpr = bindLegacyIfArgument(
                positional,
                named,
                0,
                "condition",
                expression.span()
        );
        var ifTrueExpr = bindLegacyIfArgument(
                positional,
                named,
                1,
                "if-true",
                expression.span()
        );
        var ifFalseExpr = bindLegacyIfArgument(
                positional,
                named,
                2,
                "if-false",
                expression.span()
        );
        if (positional.size() > 3) {
            throw new EvaluationException(
                    "Only 3 arguments allowed, but " + positional.size()
                            + (positional.size() == 1 ? " was" : " were")
                            + " passed.",
                    expression.span()
            );
        }
        if (!named.isEmpty()) {
            throw unknownNamed(named.keySet(), expression.span());
        }
        var condition = evaluate(conditionExpr);
        var selected = condition.isTruthy() ? ifTrueExpr : ifFalseExpr;
        return evaluate(selected).withoutSlash();
    }

    /// Evaluates a legacy {@code if()} call that uses a rest argument.
    ///
    /// Positional expressions and the rest splat are evaluated into a flat
    /// value list, then bound to {@code $condition}, {@code $if-true}, and
    /// {@code $if-false}. Named arguments fill missing slots after rest
    /// expansion.
    ///
    /// @param expression the if expression
    /// @return the selected branch value without slash metadata
    private SassValue evaluateLegacyIfWithRest(LegacyIfExpression expression) {
        var arguments = expression.arguments();
        var values = new ArrayList<SassValue>();
        for (var argument : arguments.positional()) {
            values.add(evaluate(argument).withoutSlash());
        }
        var rest = evaluate(Objects.requireNonNull(arguments.rest(), "rest"))
                .withoutSlash();
        if (rest instanceof SassMap) {
            throw new EvaluationException(
                    "Only 3 positional arguments are allowed in if().",
                    expression.span()
            );
        } else if (rest instanceof SassList list) {
            for (var element : list.contents()) {
                values.add(element.withoutSlash());
            }
        } else {
            values.add(rest);
        }
        var named = new LinkedHashMap<String, SassValue>();
        for (var entry : arguments.named().entrySet()) {
            named.put(entry.getKey(), evaluate(entry.getValue()).withoutSlash());
        }
        var condition = takeLegacyIfValue(
                values,
                named,
                0,
                "condition",
                expression.span()
        );
        var ifTrue = takeLegacyIfValue(
                values,
                named,
                1,
                "if-true",
                expression.span()
        );
        var ifFalse = takeLegacyIfValue(
                values,
                named,
                2,
                "if-false",
                expression.span()
        );
        if (!values.isEmpty()) {
            throw new EvaluationException(
                    "Only 3 arguments allowed, but "
                            + (arguments.positional().size()
                            + (rest instanceof SassList list
                            ? list.contents().size()
                            : 1)
                            + arguments.named().size())
                            + " were passed.",
                    expression.span()
            );
        }
        if (!named.isEmpty()) {
            throw unknownNamed(named.keySet(), expression.span());
        }
        return (condition.isTruthy() ? ifTrue : ifFalse).withoutSlash();
    }

    /// Takes one bound legacy {@code if()} value from positional slots or named
    /// leftovers.
    ///
    /// @param values remaining positional values (consumed from the front)
    /// @param named  remaining named values (consumed on use)
    /// @param index  the parameter index (for error text only)
    /// @param name   the parameter name without {@code $}
    /// @param span   the complete {@code if()} span
    /// @return the bound value
    private static SassValue takeLegacyIfValue(
            ArrayList<SassValue> values,
            LinkedHashMap<String, SassValue> named,
            int index,
            String name,
            SourceSpan span
    ) {
        if (!values.isEmpty()) {
            return values.remove(0);
        }
        @Nullable SassValue namedValue = named.remove(name);
        if (namedValue != null) {
            return namedValue;
        }
        throw new EvaluationException(
                "Missing argument $" + name + ".",
                span
        );
    }

    /// Binds one legacy {@code if()} parameter from positional or named arguments.
    ///
    /// @param positional the positional argument expressions
    /// @param named      remaining named arguments (consumed on use)
    /// @param index      the positional index of this parameter
    /// @param name       the parameter name without {@code $}
    /// @param span       the complete {@code if()} span for diagnostics
    /// @return the bound argument expression
    private SassExpression bindLegacyIfArgument(
            List<SassExpression> positional,
            Map<String, SassExpression> named,
            int index,
            String name,
            SourceSpan span
    ) {
        if (index < positional.size()) {
            if (named.containsKey(name)) {
                throw new EvaluationException(
                        "Argument $" + name + " was passed both by position and by name.",
                        span
                );
            }
            return positional.get(index);
        }
        @Nullable SassExpression namedValue = named.remove(name);
        if (namedValue != null) {
            return namedValue;
        }
        throw new EvaluationException("Missing argument $" + name + ".", span);
    }

    /// Evaluates a modern CSS-style {@code if()} expression.
    ///
    /// Fully resolved Sass conditions select a single branch. When any
    /// condition remains plain CSS, the whole expression serializes as an
    /// unquoted {@code if(...)} string.
    ///
    /// @param expression the CSS if expression
    /// @return the selected branch value or a plain-CSS if string
    @Override
    public SassValue visitIfExpression(IfExpression expression) {
        @Nullable ArrayList<String[]> cssResults = null;
        for (var branch : expression.branches()) {
            Object conditionResult = branch.condition() == null
                    ? Boolean.TRUE
                    : evaluateIfCondition(branch.condition());
            if (conditionResult instanceof String cssCondition) {
                if (cssResults == null) {
                    cssResults = new ArrayList<>();
                }
                cssResults.add(new String[]{cssCondition, evaluate(branch.value()).toCssString()});
            } else if (conditionResult instanceof Boolean matched && matched) {
                if (cssResults != null) {
                    cssResults.add(new String[]{"else", evaluate(branch.value()).toCssString()});
                    break;
                }
                return evaluate(branch.value());
            }
        }
        if (cssResults == null) {
            return SassNull.NULL;
        }
        var builder = new StringBuilder("if(");
        for (var index = 0; index < cssResults.size(); index++) {
            if (index > 0) {
                builder.append("; ");
            }
            builder.append(cssResults.get(index)[0])
                    .append(": ")
                    .append(cssResults.get(index)[1]);
        }
        return new SassString(builder.append(')').toString(), false);
    }

    /// Evaluates one CSS {@code if()} condition to a boolean or CSS text.
    ///
    /// @param condition the condition to evaluate
    /// @return {@link Boolean} for resolved Sass results, otherwise CSS text
    private Object evaluateIfCondition(IfConditionExpression condition) {
        if (condition instanceof IfConditionExpression.Parenthesized parenthesized) {
            Object nested = evaluateIfCondition(parenthesized.expression());
            return nested instanceof String text ? "(" + text + ")" : nested;
        }
        if (condition instanceof IfConditionExpression.Negation negation) {
            Object nested = evaluateIfCondition(negation.expression());
            if (nested instanceof String text) {
                return "not " + text;
            }
            return !((Boolean) nested);
        }
        if (condition instanceof IfConditionExpression.Operation operation) {
            @Nullable ArrayList<String> cssParts = null;
            for (var part : operation.expressions()) {
                Object result = evaluateIfCondition(part);
                if (result instanceof String text) {
                    if (cssParts == null) {
                        cssParts = new ArrayList<>();
                    }
                    cssParts.add(text);
                } else if (result instanceof Boolean bool) {
                    if (!bool && operation.operator() == IfConditionExpression.BooleanOperator.AND) {
                        return false;
                    }
                    if (bool && operation.operator() == IfConditionExpression.BooleanOperator.OR) {
                        return true;
                    }
                }
            }
            if (cssParts == null) {
                return operation.operator() == IfConditionExpression.BooleanOperator.AND;
            }
            if (cssParts.size() == 1
                    && operation.expressions().stream()
                    .filter(expression -> evaluateIfCondition(expression) instanceof String)
                    .findFirst()
                    .orElse(null) instanceof IfConditionExpression.Parenthesized) {
                // Drop redundant outer parentheses around a lone CSS group.
                var single = cssParts.get(0);
                if (single.length() >= 2 && single.charAt(0) == '(' && single.charAt(single.length() - 1) == ')') {
                    return single.substring(1, single.length() - 1);
                }
            }
            return String.join(" " + operation.operator().cssName() + " ", cssParts);
        }
        if (condition instanceof IfConditionExpression.Function function) {
            return performInterpolation(function.name())
                    + "(" + performInterpolation(function.arguments()) + ")";
        }
        if (condition instanceof IfConditionExpression.Sass sass) {
            if (isPlainCss()) {
                throw new EvaluationException(
                        "sass() conditions aren't allowed in plain CSS",
                        sass.span()
                );
            }
            return evaluate(sass.expression()).isTruthy();
        }
        if (condition instanceof IfConditionExpression.Raw raw) {
            return performInterpolation(raw.text());
        }
        throw new AssertionError("unknown if condition: " + condition);
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
    /// Number/number pairs with {@code allowsSlash} retain slash-number
    /// presentation so CSS color channel parsers can recover alpha from the
    /// last space-list element. Non-numeric pairs fall through to the default
    /// slash-string join ({@code 50%/none}, {@code var(--a)/0.4}) which color
    /// constructors split again.
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
    /// Pure CSS calculation functions keep slash presentation so forms such as
    /// {@code calc(1)/0.5} remain slash-separated numbers for color channels.
    /// Legacy global math ({@code abs}/{@code min}/{@code max}/{@code round}) and
    /// user-defined functions force real division, matching dart-sass.
    ///
    /// @param expression the unevaluated operand
    /// @return whether slash presentation is permitted
    private boolean operandAllowsSlash(SassExpression expression) {
        if (!(expression instanceof FunctionExpression function)) {
            return true;
        }
        if (function.namespace() != null) {
            return false;
        }
        var lower = function.name().toLowerCase(java.util.Locale.ROOT);
        if (!CALCULATION_FUNCTIONS.contains(lower)) {
            return false;
        }
        try {
            return environment.getFunction(function.name(), null) == null;
        } catch (SassValueException ignored) {
            return false;
        }
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
            // Names unquote so {@code ("--theme": …)} becomes {@code (--theme: …)}.
            // Values keep quotes so {@code (a: "b")} remains quoted.
            var name = evaluateSupportsValue(declaration.name(), false);
            var value = evaluateSupportsValue(declaration.value(), true);
            // Custom-property values already include post-colon whitespace/comments
            // captured by the parser, so only a bare colon is emitted. Ordinary
            // declarations use the canonical ": " separator. Newlines left after
            // omitted silent comments collapse to a single space so
            // {@code --a: //\n  b} matches dart-sass {@code --a:  b}.
            if (declaration.customProperty()) {
                value = collapseSupportsCustomPropertyNewlines(value);
            }
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
            // Interpolation forms such as {@code @supports #{$cond}} unquote so an
            // empty string still yields an empty condition (must-fail diagnostic).
            return evaluateSupportsValue(interpolation.expression(), false);
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
    /// Calculations are left unsimplified so CSS sees the original tree
    /// ({@code calc(1 + 2)}).
    ///
    /// @param expression the expression producing the value
    /// @param quote      whether quoted strings retain surrounding quotes
    /// @return the CSS representation for the supports condition
    private String evaluateSupportsValue(SassExpression expression, boolean quote) {
        var previous = inSupportsDeclaration;
        inSupportsDeclaration = true;
        try {
            var value = evaluate(expression);
            return valueOperation(expression.span(), () -> value.toCssString(quote));
        } finally {
            inSupportsDeclaration = previous;
        }
    }

    /// Collapses newline-led indentation in custom-property supports values.
    ///
    /// Silent comments omit their text but leave the following newline and
    /// indent in the raw residual. Dart Sass folds those into a single space
    /// when emitting custom-property supports queries.
    ///
    /// @param value the raw custom-property value text
    /// @return the value with newline runs folded to one space
    private static String collapseSupportsCustomPropertyNewlines(String value) {
        var result = new StringBuilder(value.length());
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            // Fold only line breaks. Leave form feed so {@code \f} escapes stay.
            if (character == '\n' || character == '\r') {
                if (character == '\r'
                        && index + 1 < value.length()
                        && value.charAt(index + 1) == '\n') {
                    index++;
                }
                while (index + 1 < value.length()) {
                    var next = value.charAt(index + 1);
                    if (next == ' ' || next == '\t') {
                        index++;
                        continue;
                    }
                    break;
                }
                result.append(' ');
                continue;
            }
            result.append(character);
        }
        return result.toString();
    }

    /// Evaluates interpolation parts to their unquoted textual representation.
    ///
    /// Nested calculations inside {@code #{…}} fully simplify even when the
    /// surrounding supports declaration leaves bare {@code calc()} unsimplified.
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
            var previousSupports = inSupportsDeclaration;
            inSupportsDeclaration = false;
            SassValue value;
            try {
                value = evaluate(expression);
            } finally {
                inSupportsDeclaration = previousSupports;
            }
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
    /// Plain CSS callables keep slash-number presentation so forms such as
    /// {@code rgb(1 2 3 / 50%)} serialize with a slash alpha. Sass callables
    /// strip slash metadata so division values become ordinary numbers.
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
        boolean stripSlash = !(callable instanceof PlainCssCallable);
        return runCallable(
                callable,
                evaluateArguments(arguments, span, stripSlash),
                span
        );
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
                                this::reportDeprecation,
                                this::loadCss
                        ),
                        bound.values()
                );
                checkUnusedKeywords(bound.rest(), span);
                // Bare slash-numbers returned by builtins (e.g. list.nth of
                // {@code 1/2}) lose slash presentation so CSS emits the
                // quotient. Lists keep nested slash metadata so forms such as
                // {@code 1 2/3 4} still serialize with slashes.
                return result instanceof SassNumber ? result.withoutSlash() : result;
            });
        }
        if (callable instanceof PlainCssCallable plainCss) {
            if (!evaluated.named().isEmpty()) {
                // In plain CSS sources, {@code $name:} is reported as a Sass
                // variable. In SCSS, unknown CSS functions reject keywords
                // with the plain-CSS callable diagnostic.
                throw new EvaluationException(
                        isPlainCss()
                                ? "Sass variables aren't allowed in plain CSS."
                                : "Plain CSS functions don't support keyword arguments.",
                        span
                );
            }
            return valueOperation(
                    span,
                    () -> simplifyOrSerializePlainCss(plainCss.name(), evaluated.positional())
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

    /// Loads a stylesheet and injects its combined CSS at the current include point.
    ///
    /// The loaded module is not registered as a namespace. Its CSS is cloned so
    /// shared module graphs remain intact for later `@use` or `load-css` calls.
    ///
    /// @param url           the unresolved stylesheet URL
    /// @param configuration values for root {@code !default} variables
    /// @param configured    whether the caller supplied a configuration map
    /// @param span          the include span
    private void loadCss(
            String url,
            ModuleConfiguration configuration,
            boolean configured,
            SourceSpan span
    ) {
        if (moduleRegistry == null) {
            throw new EvaluationException("Module loading isn't available.", span);
        }
        try {
            var module = moduleRegistry.load(
                    url,
                    currentUrl,
                    span,
                    this,
                    configuration,
                    configured,
                    true
            );
            assertConfigurationConsumed(configuration, true);
            var loadedExtensions = new ArrayList<PendingExtension>();
            collectExtensions(module, loadedExtensions, new IdentityHashMap<>());
            pendingExtensions.addAll(loadedExtensions);
            // load-css reattributes origins to the caller so extensions treat
            // the injected CSS as part of the including stylesheet.
            injectModuleCss(ModuleCss.combine(module), true);
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "module failure message"),
                    span,
                    List.of(),
                    cause
            );
        }
    }

    /// Clones and injects one combined module stylesheet under the current CSS parent.
    ///
    /// @param stylesheet         the combined module CSS
    /// @param reattributeOrigins whether cloned style rules adopt {@link #currentUrl}
    ///                           as their defining module ({@code true} for
    ///                           {@code meta.load-css()}; {@code false} when
    ///                           re-emitting {@code @use} CSS inside {@code @import})
    private void injectModuleCss(CssStylesheet stylesheet, boolean reattributeOrigins) {
        for (var child : stylesheet.children()) {
            injectCssNode(child, reattributeOrigins);
        }
    }

    /// Clones one CSS node into the active evaluation parent with nesting applied.
    ///
    /// @param node               the source CSS node from a loaded module
    /// @param reattributeOrigins whether cloned style rules adopt {@link #currentUrl}
    private void injectCssNode(CssNode node, boolean reattributeOrigins) {
        if (node instanceof CssComment comment) {
            copyParentAfterSibling();
            requireCssParent().addChild(new CssComment(comment.text(), comment.span()));
            return;
        }
        if (node instanceof CssImport importRule) {
            requireCssParent();
            if (cssStylesheet == null) {
                throw new IllegalStateException("CSS root is unavailable");
            }
            cssStylesheet.addImport(new CssImport(importRule.argument(), importRule.span()));
            return;
        }
        if (node instanceof CssDeclaration declaration) {
            copyParentAfterSibling();
            requireCssParent().addChild(new CssDeclaration(
                    declaration.name(),
                    declaration.value(),
                    declaration.span(),
                    declaration.parsedAsSassScript()
            ));
            return;
        }
        if (node instanceof CssStyleRule rule) {
            injectStyleRule(rule, reattributeOrigins);
            return;
        }
        if (node instanceof CssMediaRule mediaRule) {
            injectMediaRule(mediaRule, reattributeOrigins);
            return;
        }
        if (node instanceof CssSupportsRule supportsRule) {
            injectSupportsRule(supportsRule, reattributeOrigins);
            return;
        }
        if (node instanceof CssUnknownAtRule unknownAtRule) {
            injectUnknownAtRule(unknownAtRule, reattributeOrigins);
            return;
        }
        if (node instanceof CssFontFace fontFace) {
            injectFontFace(fontFace, reattributeOrigins);
            return;
        }
        throw new EvaluationException(
                "Unsupported CSS node in meta.load-css().",
                node.span()
        );
    }

    /// Injects one style rule, nesting selectors into the active style rule when present.
    ///
    /// @param rule               the source style rule
    /// @param reattributeOrigins whether the clone's defining module is {@link #currentUrl}
    private void injectStyleRule(CssStyleRule rule, boolean reattributeOrigins) {
        @Nullable CssStyleRule effectiveStyleRule =
                atRootExcludingStyleRule ? null : styleRule;
        boolean merge;
        if (effectiveStyleRule == null) {
            merge = true;
        } else if (effectiveStyleRule.fromPlainCss()) {
            merge = false;
        } else {
            merge = true;
        }
        @Nullable SelectorList parentSelector =
                styleRuleForParent == null ? null : styleRuleForParent.selector().value();
        boolean implicitParent = !atRootExcludingStyleRule;
        SelectorList nestedSelector;
        try {
            if (!merge) {
                nestedSelector = rule.selector().value();
            } else {
                nestedSelector = rule.selector().value().nestWithin(parentSelector, implicitParent);
            }
        } catch (SassValueException cause) {
            throw new EvaluationException(
                    Objects.requireNonNull(cause.getMessage(), "selector failure message"),
                    rule.selector().span(),
                    List.of(),
                    cause
            );
        }
        var injected = new CssStyleRule(
                new CssValue<>(nestedSelector, rule.selector().span()),
                rule.span(),
                rule.fromPlainCss(),
                rule.definingMediaContext()
        );
        if (reattributeOrigins) {
            // load-css treats injected CSS as part of the caller's stylesheet.
            styleRuleOrigins.put(injected, currentUrl);
        } else {
            // Re-emitting {@code @use} CSS (e.g. inside {@code @import}) keeps the
            // defining module so private placeholders and upstream visibility work.
            @Nullable URI sourceOrigin = styleRuleOrigins.get(rule);
            styleRuleOrigins.put(injected, sourceOrigin != null ? sourceOrigin : currentUrl);
        }
        addCssChild(injected, merge);
        if (!isPlainCss()) {
            extendableStyleRules.add(injected);
        }
        var previousParent = requireCssParent();
        var previousStyleRule = styleRule;
        var previousStyleRuleForParent = styleRuleForParent;
        var previousAtRootExcludingStyleRule = atRootExcludingStyleRule;
        cssParent = injected;
        styleRule = injected;
        styleRuleForParent = injected;
        atRootExcludingStyleRule = false;
        styleRuleDepth++;
        try {
            for (var child : rule.children()) {
                injectCssNode(child, reattributeOrigins);
            }
        } finally {
            styleRuleDepth--;
            styleRule = previousStyleRule;
            styleRuleForParent = previousStyleRuleForParent;
            atRootExcludingStyleRule = previousAtRootExcludingStyleRule;
            cssParent = previousParent;
        }
        if (previousStyleRule == null && !previousParent.children().isEmpty()) {
            previousParent.children().get(previousParent.children().size() - 1).setGroupEnd(true);
        }
    }

    /// Injects one media rule using the same nesting and bubbling rules as evaluation.
    private void injectMediaRule(CssMediaRule mediaRule, boolean reattributeOrigins) {
        if (hasCssNesting()) {
            var nested = new CssMediaRule(mediaRule.queries(), mediaRule.span());
            addCssChild(nested, false);
            var previousParent = requireCssParent();
            cssParent = nested;
            try {
                for (var child : mediaRule.children()) {
                    injectCssNode(child, reattributeOrigins);
                }
            } finally {
                cssParent = previousParent;
            }
            return;
        }
        @Nullable List<CssMediaQuery> mergedQueries = mediaQueries == null
                ? null
                : CssMediaQuery.mergeLists(mediaQueries, mediaRule.queries());
        if (mergedQueries != null && mergedQueries.isEmpty()) {
            return;
        }
        @Unmodifiable List<CssMediaQuery> effectiveQueries =
                mergedQueries == null ? mediaRule.queries() : mergedQueries;
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
            sources.addAll(mediaRule.queries());
            effectiveSources = Set.copyOf(sources);
        }
        var injected = new CssMediaRule(effectiveQueries, mediaRule.span());
        addCssChild(injected, true, effectiveSources);
        var previousParent = requireCssParent();
        var previousMediaQueries = mediaQueries;
        var previousMediaQuerySources = mediaQuerySources;
        @Nullable CssStyleRule activeStyleRule = styleRule;
        cssParent = injected;
        mediaQueries = effectiveQueries;
        mediaQuerySources = effectiveSources;
        try {
            if (activeStyleRule == null) {
                for (var child : mediaRule.children()) {
                    injectCssNode(child, reattributeOrigins);
                }
            } else {
                var wrapper = activeStyleRule.copyWithoutChildren();
                injected.addChild(wrapper);
                var mediaParent = requireCssParent();
                cssParent = wrapper;
                try {
                    for (var child : mediaRule.children()) {
                        injectCssNode(child, reattributeOrigins);
                    }
                } finally {
                    cssParent = mediaParent;
                }
            }
        } finally {
            mediaQueries = previousMediaQueries;
            mediaQuerySources = previousMediaQuerySources;
            cssParent = previousParent;
        }
    }

    /// Injects one supports rule using the same nesting and bubbling rules as evaluation.
    private void injectSupportsRule(CssSupportsRule supportsRule, boolean reattributeOrigins) {
        if (hasCssNesting()) {
            var nested = new CssSupportsRule(supportsRule.condition(), supportsRule.span());
            addCssChild(nested, false);
            var previousParent = requireCssParent();
            cssParent = nested;
            try {
                for (var child : supportsRule.children()) {
                    injectCssNode(child, reattributeOrigins);
                }
            } finally {
                cssParent = previousParent;
            }
            return;
        }
        var injected = new CssSupportsRule(supportsRule.condition(), supportsRule.span());
        addCssChild(injected, true);
        var previousParent = requireCssParent();
        @Nullable CssStyleRule activeStyleRule = styleRule;
        cssParent = injected;
        try {
            if (activeStyleRule == null) {
                for (var child : supportsRule.children()) {
                    injectCssNode(child, reattributeOrigins);
                }
            } else {
                var wrapper = activeStyleRule.copyWithoutChildren();
                injected.addChild(wrapper);
                var supportsParent = requireCssParent();
                cssParent = wrapper;
                try {
                    for (var child : supportsRule.children()) {
                        injectCssNode(child, reattributeOrigins);
                    }
                } finally {
                    cssParent = supportsParent;
                }
            }
        } finally {
            cssParent = previousParent;
        }
    }

    /// Injects one opaque at-rule under the current CSS parent.
    private void injectUnknownAtRule(CssUnknownAtRule rule, boolean reattributeOrigins) {
        var injected = new CssUnknownAtRule(
                rule.name(),
                rule.value(),
                rule.hasBlock(),
                rule.span()
        );
        addCssChild(injected, false);
        if (!rule.hasBlock()) {
            return;
        }
        var previousParent = requireCssParent();
        cssParent = injected;
        try {
            for (var child : rule.children()) {
                injectCssNode(child, reattributeOrigins);
            }
        } finally {
            cssParent = previousParent;
        }
        injected.setGroupEnd(true);
    }

    /// Injects one font-face rule, bubbling through style rules to the root.
    private void injectFontFace(CssFontFace fontFace, boolean reattributeOrigins) {
        var injected = new CssFontFace(fontFace.span());
        addCssChild(injected, true);
        if (!(injected.parent() instanceof CssStylesheet)) {
            throw new EvaluationException(
                    "@font-face rules may only be used at the stylesheet root.",
                    fontFace.span()
            );
        }
        var previousParent = requireCssParent();
        var previousFontFace = this.fontFace;
        cssParent = injected;
        this.fontFace = injected;
        try {
            for (var child : fontFace.children()) {
                injectCssNode(child, reattributeOrigins);
            }
        } finally {
            this.fontFace = previousFontFace;
            cssParent = previousParent;
        }
        copyParentAfterSibling();
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
        // Content blocks run in the include site's environment without the
        // mixin flag, so meta.content-exists() fails inside {@code @content}.
        withEnvironment(content.environment().closure(), () -> {
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
    }

    /// Evaluates an argument invocation into positional and named values.
    ///
    /// @param arguments the unevaluated arguments
    /// @param span      the invocation span
    /// @return the evaluated arguments with slash metadata stripped
    private EvaluatedArguments evaluateArguments(ArgumentList arguments, SourceSpan span) {
        return evaluateArguments(arguments, span, true);
    }

    /// Evaluates an argument invocation into positional and named values.
    ///
    /// @param arguments  the unevaluated arguments
    /// @param span       the invocation span
    /// @param stripSlash whether to strip slash-division presentation metadata
    /// @return the evaluated arguments
    private EvaluatedArguments evaluateArguments(
            ArgumentList arguments,
            SourceSpan span,
            boolean stripSlash
    ) {
        var positional = new ArrayList<SassValue>();
        for (var argument : arguments.positional()) {
            var value = evaluate(argument);
            positional.add(stripSlash ? value.withoutSlash() : value);
        }
        var named = new LinkedHashMap<String, SassValue>();
        for (var entry : arguments.named().entrySet()) {
            var value = evaluate(entry.getValue());
            named.put(entry.getKey(), stripSlash ? value.withoutSlash() : value);
        }
        var separator = ListSeparator.UNDECIDED;
        if (arguments.rest() != null) {
            // Expand rest first, then strip slash on each element. Avoid calling
            // SassList.withoutSlash on the whole rest (that rewrites a trailing
            // slash-number as a nested slash list for color channels).
            var rest = evaluate(arguments.rest());
            if (rest instanceof SassMap map) {
                addRestMap(named, map, span, stripSlash);
            } else if (rest instanceof SassArgumentList argumentList) {
                for (var element : argumentList.asList()) {
                    positional.add(stripSlash ? element.withoutSlash() : element);
                }
                separator = argumentList.separator();
                for (var entry : argumentList.keywords().entrySet()) {
                    named.put(
                            entry.getKey(),
                            stripSlash ? entry.getValue().withoutSlash() : entry.getValue()
                    );
                }
            } else if (rest instanceof SassList list) {
                for (var element : list.contents()) {
                    positional.add(stripSlash ? element.withoutSlash() : element);
                }
                separator = list.separator();
            } else {
                positional.add(stripSlash ? rest.withoutSlash() : rest);
            }
        }
        if (arguments.keywordRest() != null) {
            var keywordRest = evaluate(arguments.keywordRest());
            if (stripSlash) {
                keywordRest = keywordRest.withoutSlash();
            }
            if (!(keywordRest instanceof SassMap map)) {
                throw new EvaluationException(
                        "Variable keyword arguments must be a map (was " + keywordRest + ").",
                        span
                );
            }
            addRestMap(named, map, span, stripSlash);
        }
        return new EvaluatedArguments(List.copyOf(positional), named, separator);
    }

    /// Merges a rest map into the named-argument table.
    ///
    /// Keys must be Sass strings (quoted or unquoted). Values keep their
    /// evaluated form after any outer {@code withoutSlash} rewrite.
    private void addRestMap(
            LinkedHashMap<String, SassValue> named,
            SassMap map,
            SourceSpan span
    ) {
        addRestMap(named, map, span, true);
    }

    /// Merges a rest map into the named-argument table.
    ///
    /// @param named      the named-argument table to extend
    /// @param map        the rest map
    /// @param span       the invocation span for diagnostics
    /// @param stripSlash whether to strip slash presentation on values
    private void addRestMap(
            LinkedHashMap<String, SassValue> named,
            SassMap map,
            SourceSpan span,
            boolean stripSlash
    ) {
        for (var entry : map.contents().entrySet()) {
            if (!(entry.getKey() instanceof SassString key)) {
                // dart-sass uses inspect form and a multi-line diagnostic:
                // "(a #b) is not a string in (a #b: c)." — parenthesize the
                // stand-alone key but not the key again inside the map pair.
                var bareKey = entry.getKey().toString();
                var displayKey = keywordMapKeyDisplay(entry.getKey(), bareKey);
                var valueText = entry.getValue().toString();
                throw new EvaluationException(
                        "Variable keyword argument map must have string keys.\n"
                                + displayKey + " is not a string in ("
                                + bareKey + ": " + valueText + ").",
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
            named.put(
                    name,
                    stripSlash ? entry.getValue().withoutSlash() : entry.getValue()
            );
        }
    }

    /// Returns the parenthesized stand-alone key form for keyword-map diagnostics.
    ///
    /// Multi-element unbracketed lists are parenthesized so {@code a#b} surfaces
    /// as {@code (a #b)} when named alone, while the map pair still uses the bare
    /// inspect text ({@code a #b: c}).
    ///
    /// @param key  the invalid map key
    /// @param bare the key's inspect spelling
    /// @return the display text for the stand-alone key clause
    private static String keywordMapKeyDisplay(SassValue key, String bare) {
        if (key instanceof SassList list
                && !list.hasBrackets()
                && list.asList().size() > 1
                && !bare.startsWith("(")) {
            return "(" + bare + ")";
        }
        return bare;
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
                // Named excess uses "positional arguments" so the message
                // distinguishes arity from unknown-named diagnostics.
                var argumentWord = !named.isEmpty()
                        ? (params.size() == 1 ? "positional argument" : "positional arguments")
                        : (params.size() == 1 ? "argument" : "arguments");
                throw new EvaluationException(
                        "Only " + params.size() + " " + argumentWord
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
    /// Evaluates a CSS calculation function with calculation-context arguments.
    private SassValue visitCalculation(
            FunctionExpression expression,
            @Nullable String inLegacySassFunction
    ) {
        var arguments = expression.arguments();
        if (!arguments.named().isEmpty()) {
            throw new EvaluationException(
                    "Keyword arguments can't be used with calculations.",
                    expression.span()
            );
        }
        if (arguments.rest() != null) {
            throw new EvaluationException(
                    "Rest arguments can't be used with calculations.",
                    expression.span()
            );
        }
        checkCalculationArguments(expression);
        try {
            var calcArgs = new ArrayList<Object>(arguments.positional().size());
            for (var argument : arguments.positional()) {
                calcArgs.add(visitCalculationExpression(argument, inLegacySassFunction));
            }
            var name = expression.name().toLowerCase(java.util.Locale.ROOT);
            if (inSupportsDeclaration) {
                return SassCalculation.unsimplified(name, calcArgs);
            }
            return simplifyCalculation(name, calcArgs, inLegacySassFunction, expression.span());
        } catch (SassValueException exception) {
            throw new EvaluationException(
                    Objects.requireNonNull(exception.getMessage(), "calculation failure"),
                    expression.span(),
                    List.of(),
                    exception
            );
        }
    }

    /// Verifies that a calculation call has a dart-sass-compatible argument count.
    ///
    /// @param expression the calculation call
    private void checkCalculationArguments(FunctionExpression expression) {
        var name = expression.name().toLowerCase(java.util.Locale.ROOT);
        int count = expression.arguments().positional().size();
        @Nullable Integer maxArgs = switch (name) {
            case "calc", "sqrt", "sin", "cos", "tan", "asin", "acos", "atan",
                    "abs", "exp", "sign" -> 1;
            case "min", "max", "hypot" -> null;
            case "pow", "atan2", "log", "mod", "rem", "calc-size" -> 2;
            case "round", "clamp" -> 3;
            default -> throw new EvaluationException(
                    "Unknown calculation " + name + "().",
                    expression.span()
            );
        };
        if (count == 0) {
            throw new EvaluationException("Missing argument.", expression.span());
        }
        if (maxArgs != null && count > maxArgs) {
            throw new EvaluationException(
                    "Only " + maxArgs + " argument" + (maxArgs == 1 ? "" : "s")
                            + " allowed, but " + count
                            + (count == 1 ? " was" : " were") + " passed.",
                    expression.span()
            );
        }
    }

    /// Evaluates one expression as a calculation argument.
    private Object visitCalculationExpression(SassExpression expression) {
        return visitCalculationExpression(expression, null);
    }

    /// Evaluates one expression as a calculation argument with optional legacy mode.
    ///
    /// @param expression             the argument expression
    /// @param inLegacySassFunction   the legacy global function name, or {@code null}
    /// @return a calculation operand
    private Object visitCalculationExpression(
            SassExpression expression,
            @Nullable String inLegacySassFunction
    ) {
        if (expression instanceof ParenthesizedExpression parenthesized) {
            Object inner = visitCalculationExpression(
                    parenthesized.expression(),
                    inLegacySassFunction
            );
            if (inner instanceof SassString string && !string.hasQuotes()) {
                return new SassString("(" + string.text() + ")", false);
            }
            return inner;
        }
        if (expression instanceof StringExpression stringExpression && !stringExpression.hasQuotes()) {
            @Nullable String plain = stringExpression.text().asPlain();
            if (plain != null) {
                return switch (plain.toLowerCase(java.util.Locale.ROOT)) {
                    case "pi" -> SassNumber.of(Math.PI, null);
                    case "e" -> SassNumber.of(Math.E, null);
                    case "infinity", "+infinity" -> SassNumber.of(Double.POSITIVE_INFINITY, null);
                    case "-infinity" -> SassNumber.of(Double.NEGATIVE_INFINITY, null);
                    case "nan" -> SassNumber.of(Double.NaN, null);
                    default -> new SassString(performInterpolation(stringExpression.text()), false);
                };
            }
            return new SassString(performInterpolation(stringExpression.text()), false);
        }
        if (expression instanceof BinaryOperationExpression binary) {
            var operator = switch (binary.operator()) {
                case PLUS -> CalculationOperator.PLUS;
                case MINUS -> CalculationOperator.MINUS;
                case TIMES -> CalculationOperator.TIMES;
                case DIVIDED_BY -> CalculationOperator.DIVIDED_BY;
                default -> throw new EvaluationException(
                        "This operation can't be used in a calculation.",
                        binary.operatorSpan()
                );
            };
            if (operator == CalculationOperator.PLUS || operator == CalculationOperator.MINUS) {
                assertCalculationSumWhitespace(binary);
            }
            try {
                Object left = visitCalculationExpression(binary.left(), inLegacySassFunction);
                Object right = visitCalculationExpression(binary.right(), inLegacySassFunction);
                // @supports keeps the original calc tree so CSS sees calc(1 + 2)
                // rather than a simplified calc(3). Interpolation still simplifies
                // because it evaluates outside this calculation visitor.
                if (inSupportsDeclaration) {
                    return new CalculationOperation(operator, left, right);
                }
                return SassCalculation.operate(
                        operator,
                        left,
                        right,
                        inLegacySassFunction,
                        message -> reportDeprecation(message, "global-builtin", binary.span())
                );
            } catch (SassValueException exception) {
                throw new EvaluationException(
                        Objects.requireNonNull(exception.getMessage(), "calculation op failure"),
                        binary.span(),
                        List.of(),
                        exception
                );
            }
        }
        if (expression instanceof NumberExpression
                || expression instanceof VariableExpression
                || expression instanceof FunctionExpression
                || expression instanceof LegacyIfExpression) {
            SassValue result = evaluate(expression);
            if (result instanceof SassNumber || result instanceof SassCalculation) {
                return result;
            }
            if (result instanceof SassString string && !string.hasQuotes()) {
                return string;
            }
            throw new EvaluationException(
                    "Value " + inspectCalculationValue(result) + " can't be used in a calculation.",
                    expression.span()
            );
        }
        if (expression instanceof ListExpression list
                && !list.hasBrackets()
                && list.separator() == ListSeparator.SPACE
                && list.contents().size() > 1) {
            var elements = new ArrayList<Object>(list.contents().size());
            for (var element : list.contents()) {
                elements.add(visitCalculationExpression(element, inLegacySassFunction));
            }
            checkAdjacentCalculationValues(elements, list);
            for (var index = 0; index < elements.size(); index++) {
                if (elements.get(index) instanceof CalculationOperation
                        && list.contents().get(index) instanceof ParenthesizedExpression) {
                    elements.set(
                            index,
                            new SassString("(" + elements.get(index) + ")", false)
                    );
                }
            }
            var builder = new StringBuilder();
            for (var index = 0; index < elements.size(); index++) {
                if (index > 0) {
                    builder.append(' ');
                }
                Object element = elements.get(index);
                if (element instanceof SassString string && !string.hasQuotes()) {
                    builder.append(string.text());
                } else if (element instanceof SassNumber number) {
                    builder.append(number.toCalculationCssString());
                } else if (element instanceof SassCalculation calculation) {
                    builder.append(calculation.toCssString());
                } else {
                    builder.append(element);
                }
            }
            return new SassString(builder.toString(), false);
        }
        throw new EvaluationException(
                "This expression can't be used in a calculation.",
                expression.span()
        );
    }

    /// Requires an explicit operator between adjacent non-string calculation values.
    ///
    /// @param elements the evaluated space-list elements
    /// @param list     the original space list expression
    private void checkAdjacentCalculationValues(
            List<Object> elements,
            ListExpression list
    ) {
        for (var index = 1; index < elements.size(); index++) {
            Object previous = elements.get(index - 1);
            Object current = elements.get(index);
            if (previous instanceof SassString || current instanceof SassString) {
                continue;
            }
            var currentNode = list.contents().get(index);
            if (currentNode instanceof UnaryOperationExpression unary
                    && (unary.operator() == UnaryOperator.PLUS
                    || unary.operator() == UnaryOperator.MINUS)) {
                throw new EvaluationException(
                        "\"+\" and \"-\" must be surrounded by whitespace in calculations.",
                        unary.span()
                );
            }
            if (currentNode instanceof NumberExpression number) {
                var text = number.span().text();
                if (!text.isEmpty()
                        && (text.charAt(0) == '-' || text.charAt(0) == '+')) {
                    throw new EvaluationException(
                            "\"+\" and \"-\" must be surrounded by whitespace in calculations.",
                            number.span()
                    );
                }
            }
            throw new EvaluationException(
                    "Missing math operator.",
                    list.span()
            );
        }
    }

    /// Requires whitespace on both sides of a calculation {@code +} or {@code -}.
    ///
    /// @param binary the addition or subtraction operation
    private static void assertCalculationSumWhitespace(BinaryOperationExpression binary) {
        var leftEnd = binary.left().span().end().offset();
        var operatorStart = binary.operatorSpan().start().offset();
        var operatorEnd = binary.operatorSpan().end().offset();
        var rightStart = binary.right().span().start().offset();
        if (operatorStart <= leftEnd || rightStart <= operatorEnd) {
            throw new EvaluationException(
                    "\"+\" and \"-\" must be surrounded by whitespace in calculations.",
                    binary.operatorSpan()
            );
        }
    }

    /// Returns the inspect form used in calculation value diagnostics.
    ///
    /// Multi-element space-separated lists are parenthesized to match dart-sass
    /// ({@code Value (1 2 3) can't be used in a calculation.}).
    private static String inspectCalculationValue(SassValue value) {
        if (value instanceof SassList list
                && !list.hasBrackets()
                && list.separator() == ListSeparator.SPACE
                && list.contents().size() > 1) {
            return "(" + list + ")";
        }
        return value.toString();
    }

    /// Returns whether an expression is valid in a CSS calculation argument.
    private boolean isCalculationSafe(SassExpression expression) {
        if (expression instanceof NumberExpression
                || expression instanceof VariableExpression
                || expression instanceof FunctionExpression
                || expression instanceof LegacyIfExpression
                || expression instanceof InterpolatedFunctionExpression) {
            return true;
        }
        if (expression instanceof ParenthesizedExpression parenthesized) {
            return isCalculationSafe(parenthesized.expression());
        }
        if (expression instanceof BinaryOperationExpression binary) {
            return (binary.operator() == BinaryOperator.PLUS
                    || binary.operator() == BinaryOperator.MINUS
                    || binary.operator() == BinaryOperator.TIMES
                    || binary.operator() == BinaryOperator.DIVIDED_BY)
                    && isCalculationSafe(binary.left())
                    && isCalculationSafe(binary.right());
        }
        if (expression instanceof StringExpression string && !string.hasQuotes()) {
            return true;
        }
        if (expression instanceof ListExpression list) {
            return !list.hasBrackets()
                    && list.separator() == ListSeparator.SPACE
                    && list.contents().size() > 1
                    && list.contents().stream().allMatch(this::isCalculationSafe);
        }
        return false;
    }

    /// Dispatches a calculation by name to the matching simplifier.
    private SassValue simplifyCalculation(
            String name,
            List<Object> args,
            @Nullable String inLegacySassFunction,
            SourceSpan span
    ) {
        return switch (name) {
            case "calc" -> {
                if (args.size() != 1) {
                    throw new SassValueException(
                            "1 argument required, but only " + args.size()
                                    + (args.size() == 1 ? " was" : " were") + " passed."
                    );
                }
                yield SassCalculation.calc(args.get(0));
            }
            case "sqrt" -> SassCalculation.singleArgument(
                    "sqrt",
                    unaryArg(args),
                    number -> SassNumber.of(Math.sqrt(number.assertNoUnits().value()), null),
                    true
            );
            case "sin" -> SassCalculation.singleArgument(
                    "sin",
                    unaryArg(args),
                    number -> SassNumber.of(Math.sin(calculationRadians(number)), null),
                    false
            );
            case "cos" -> SassCalculation.singleArgument(
                    "cos",
                    unaryArg(args),
                    number -> SassNumber.of(Math.cos(calculationRadians(number)), null),
                    false
            );
            case "tan" -> SassCalculation.singleArgument(
                    "tan",
                    unaryArg(args),
                    number -> SassNumber.of(Math.tan(calculationRadians(number)), null),
                    false
            );
            case "asin" -> SassCalculation.singleArgument(
                    "asin",
                    unaryArg(args),
                    number -> calculationDegrees(Math.asin(number.value())),
                    true
            );
            case "acos" -> SassCalculation.singleArgument(
                    "acos",
                    unaryArg(args),
                    number -> calculationDegrees(Math.acos(number.value())),
                    true
            );
            case "atan" -> SassCalculation.singleArgument(
                    "atan",
                    unaryArg(args),
                    number -> calculationDegrees(Math.atan(number.value())),
                    true
            );
            case "abs" -> SassCalculation.singleArgument(
                    "abs",
                    unaryArg(args),
                    number -> SassNumber.withUnits(
                            Math.abs(number.value()),
                            number.numeratorUnits(),
                            number.denominatorUnits()
                    ),
                    false
            );
            case "exp" -> SassCalculation.singleArgument(
                    "exp",
                    unaryArg(args),
                    number -> SassNumber.of(Math.exp(number.assertNoUnits().value()), null),
                    false
            );
            case "sign" -> SassCalculation.singleArgument(
                    "sign",
                    unaryArg(args),
                    number -> {
                        double value = number.value();
                        double sign = value > 0 ? 1.0 : value < 0 ? -1.0 : value;
                        return SassNumber.withUnits(
                                sign,
                                number.numeratorUnits(),
                                number.denominatorUnits()
                        );
                    },
                    false
            );
            case "min" -> SassCalculation.min(args);
            case "max" -> SassCalculation.max(args);
            case "hypot" -> SassCalculation.hypot(args);
            case "clamp" -> SassCalculation.clamp(
                    args.isEmpty() ? nullArg() : args.get(0),
                    args.size() > 1 ? args.get(1) : null,
                    args.size() > 2 ? args.get(2) : null
            );
            case "pow" -> SassCalculation.pow(
                    firstArg(args),
                    args.size() > 1 ? args.get(1) : null
            );
            case "log" -> SassCalculation.log(
                    firstArg(args),
                    args.size() > 1 ? args.get(1) : null
            );
            case "atan2" -> SassCalculation.atan2(
                    firstArg(args),
                    args.size() > 1 ? args.get(1) : null
            );
            case "mod" -> SassCalculation.mod(
                    firstArg(args),
                    args.size() > 1 ? args.get(1) : null
            );
            case "rem" -> SassCalculation.rem(
                    firstArg(args),
                    args.size() > 1 ? args.get(1) : null
            );
            case "round" -> SassCalculation.round(
                    firstArg(args),
                    args.size() > 1 ? args.get(1) : null,
                    args.size() > 2 ? args.get(2) : null,
                    inLegacySassFunction,
                    message -> reportDeprecation(message, "global-builtin", span)
            );
            case "calc-size" -> SassCalculation.calcSize(
                    firstArg(args),
                    args.size() > 1 ? args.get(1) : null
            );
            default -> throw new SassValueException("Unknown calculation " + name + "().");
        };
    }

    /// Returns the first calculation argument, allowing multi-arg callers to read more.
    private static Object firstArg(List<Object> args) {
        if (args.isEmpty()) {
            throw new SassValueException("Missing argument.");
        }
        return args.get(0);
    }

    /// Returns the sole argument for a unary calculation function.
    private static Object unaryArg(List<Object> args) {
        if (args.isEmpty()) {
            throw new SassValueException("Missing argument.");
        }
        if (args.size() > 1) {
            // Special numbers may expand to more tokens at CSS evaluation time.
            for (var arg : args) {
                if (arg instanceof SassString || arg instanceof SassCalculation) {
                    return args.get(0);
                }
            }
            throw new SassValueException(
                    "Only 1 argument allowed, but " + args.size() + " were passed."
            );
        }
        return args.get(0);
    }

    private static Object nullArg() {
        throw new SassValueException("Missing argument.");
    }

    private static double calculationRadians(SassNumber number) {
        if (number.isUnitless()) {
            return number.value();
        }
        try {
            return number.coerce(List.of("rad"), List.of()).value();
        } catch (SassValueException exception) {
            throw new SassValueException(
                    "$number: Expected " + number
                            + " to have an angle unit (deg, grad, rad, turn)."
            );
        }
    }

    private static SassNumber calculationDegrees(double radians) {
        if (Double.isNaN(radians)) {
            return SassNumber.of(Double.NaN, "deg");
        }
        return SassNumber.of(radians * (180.0 / Math.PI), "deg");
    }

    /// Simplifies known calculation forms when they reduce to a single number.
    private static SassValue simplifyOrSerializePlainCss(
            String name,
            List<SassValue> positional
    ) {
        if ("calc".equals(name) && positional.size() == 1) {
            SassValue only = positional.get(0);
            if (only instanceof SassNumber number) {
                return number;
            }
            if (only instanceof SassString string && !string.hasQuotes()) {
                @Nullable Double special = specialCalculationNumber(string.text());
                if (special != null) {
                    return SassNumber.of(special, null);
                }
            }
        }
        return serializePlainCss(name, positional);
    }

    /// Parses CSS calculation keywords that represent non-finite numbers.
    private static @Nullable Double specialCalculationNumber(String text) {
        return switch (text) {
            case "infinity", "+infinity" -> Double.POSITIVE_INFINITY;
            case "-infinity" -> Double.NEGATIVE_INFINITY;
            case "NaN" -> Double.NaN;
            default -> null;
        };
    }

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

    /// Rejects Sass-only argument shapes in plain CSS function calls.
    ///
    /// @param arguments the invocation arguments
    /// @param span      the function call span used when a span is unavailable
    private void assertPlainCssArguments(ArgumentList arguments, SourceSpan span) {
        if (!arguments.named().isEmpty()) {
            @Nullable SourceSpan namedSpan = arguments.namedSpans().values().stream()
                    .findFirst()
                    .orElse(span);
            throw new EvaluationException(
                    "Sass variables aren't allowed in plain CSS.",
                    namedSpan
            );
        }
        if (arguments.rest() != null) {
            throw new EvaluationException(
                    "expected \")\".",
                    arguments.rest().span()
            );
        }
        if (arguments.keywordRest() != null) {
            throw new EvaluationException(
                    "expected \")\".",
                    arguments.keywordRest().span()
            );
        }
        for (var argument : arguments.positional()) {
            rejectPlainCssForbiddenExpression(argument);
        }
    }

    /// Validates calculation-function arity and forbidden nested Sass forms.
    ///
    /// @param expression the calculation call
    private void assertPlainCssCalculationCall(FunctionExpression expression) {
        var arguments = expression.arguments();
        var name = expression.name().toLowerCase(java.util.Locale.ROOT);
        if (arguments.positional().isEmpty()
                && arguments.rest() == null
                && "calc".equals(name)) {
            throw new EvaluationException("Missing argument.", expression.span());
        }
        for (var argument : arguments.positional()) {
            rejectPlainCssForbiddenExpression(argument);
        }
    }

    /// Walks an expression tree and rejects Sass-only constructs in plain CSS.
    ///
    /// @param expression the expression to inspect
    private void rejectPlainCssForbiddenExpression(SassExpression expression) {
        if (expression instanceof VariableExpression variable) {
            throw new EvaluationException(
                    "Sass variables aren't allowed in plain CSS.",
                    variable.span()
            );
        }
        if (expression instanceof FunctionExpression function) {
            if (function.namespace() != null) {
                throw new EvaluationException(
                        "Module namespaces aren't allowed in plain CSS.",
                        function.span()
                );
            }
            assertPlainCssArguments(function.arguments(), function.span());
            return;
        }
        if (expression instanceof BinaryOperationExpression binary) {
            rejectPlainCssForbiddenExpression(binary.left());
            rejectPlainCssForbiddenExpression(binary.right());
            return;
        }
        if (expression instanceof UnaryOperationExpression unary) {
            rejectPlainCssForbiddenExpression(unary.operand());
            return;
        }
        if (expression instanceof ParenthesizedExpression parenthesized) {
            rejectPlainCssForbiddenExpression(parenthesized.expression());
            return;
        }
        if (expression instanceof ListExpression list) {
            for (var element : list.contents()) {
                rejectPlainCssForbiddenExpression(element);
            }
            return;
        }
        if (expression instanceof InterpolatedFunctionExpression
                || expression instanceof LegacyIfExpression
                || expression instanceof IfExpression) {
            // Nested if()/interpolated forms inside calc are not plain CSS calc
            // operands; fall through to normal evaluation paths that already
            // reject interpolation in plain CSS.
            return;
        }
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
