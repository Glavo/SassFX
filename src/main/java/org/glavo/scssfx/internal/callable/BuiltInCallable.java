// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.callable;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.evaluate.Environment;
import org.glavo.scssfx.internal.evaluate.VariableBinding;
import org.glavo.scssfx.internal.module.LoadedModule;
import org.glavo.scssfx.internal.value.SassArgumentList;
import org.glavo.scssfx.internal.value.SassFunction;
import org.glavo.scssfx.internal.value.SassMixin;
import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/// A built-in Sass function with named parameters and optional rest support.
@ApiStatus.Internal
@NotNullByDefault
public final class BuiltInCallable implements Callable {
    /// Contains the normalized function name.
    private final String name;

    /// Contains ordinary parameters in source order.
    private final @Unmodifiable List<Param> parameters;

    /// Contains the rest parameter name, or {@code null}.
    private final @Nullable String restParameter;

    /// Contains the number of required leading parameters.
    private final int minArgs;

    /// Records whether includes may supply a content block.
    private final boolean acceptsContent;

    /// Contains the implementation invoked after argument binding.
    private final ContextCallback callback;

    /// Creates a built-in callable without content-block support.
    private BuiltInCallable(
            String name,
            List<Param> parameters,
            @Nullable String restParameter,
            int minArgs,
            ContextCallback callback
    ) {
        this(name, parameters, restParameter, minArgs, false, callback);
    }

    /// Creates a built-in callable.
    private BuiltInCallable(
            String name,
            List<Param> parameters,
            @Nullable String restParameter,
            int minArgs,
            boolean acceptsContent,
            ContextCallback callback
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.parameters = List.copyOf(parameters);
        this.restParameter = restParameter;
        this.minArgs = minArgs;
        this.acceptsContent = acceptsContent;
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    /// Creates a built-in function with required positional parameters.
    ///
    /// @param name       the function name
    /// @param paramNames the parameter names in order
    /// @param callback   the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            List<String> paramNames,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        var params = new ArrayList<Param>(paramNames.size());
        for (var paramName : paramNames) {
            params.add(new Param(paramName, null));
        }
        return new BuiltInCallable(name, params, null, params.size(), valueOnly(callback));
    }

    /// Creates a built-in function with optional trailing parameters.
    ///
    /// @param name     the function name
    /// @param params   the parameters including defaults
    /// @param minArgs  the number of required leading parameters
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            List<Param> params,
            int minArgs,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        return new BuiltInCallable(name, params, null, minArgs, valueOnly(callback));
    }

    /// Creates a built-in function with a rest parameter.
    ///
    /// @param name           the function name
    /// @param paramNames     ordinary parameter names
    /// @param restParameter  the rest parameter name
    /// @param callback       the implementation
    /// @return the callable
    public static BuiltInCallable withRest(
            String name,
            List<String> paramNames,
            String restParameter,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        var params = new ArrayList<Param>(paramNames.size());
        for (var paramName : paramNames) {
            params.add(new Param(paramName, null));
        }
        return new BuiltInCallable(
                name,
                params,
                Objects.requireNonNull(restParameter, "restParameter"),
                params.size(),
                valueOnly(callback)
        );
    }

    /// Compatibility factory used by older call sites with arity-only APIs.
    ///
    /// @param name     the function name
    /// @param arity    the exact arity
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            int arity,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        var params = new ArrayList<Param>(arity);
        for (var index = 0; index < arity; index++) {
            params.add(new Param("arg" + (index + 1), null));
        }
        return new BuiltInCallable(name, params, null, arity, valueOnly(callback));
    }

    /// Compatibility factory for arity ranges without names.
    ///
    /// @param name     the function name
    /// @param minArgs  the minimum arity
    /// @param maxArgs  the maximum arity
    /// @param callback the implementation
    /// @return the callable
    public static BuiltInCallable of(
            String name,
            int minArgs,
            int maxArgs,
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        if (maxArgs == Integer.MAX_VALUE) {
            var params = new ArrayList<Param>(Math.max(0, minArgs - 1));
            for (var index = 0; index < Math.max(0, minArgs - 1); index++) {
                params.add(new Param("arg" + (index + 1), null));
            }
            return new BuiltInCallable(name, params, "args", minArgs, valueOnly(callback));
        }
        var params = new ArrayList<Param>(maxArgs);
        for (var index = 0; index < maxArgs; index++) {
            params.add(new Param("arg" + (index + 1), null));
        }
        return new BuiltInCallable(name, params, null, minArgs, valueOnly(callback));
    }

    /// Creates a built-in function whose implementation receives invocation context.
    ///
    /// @param name     the function name
    /// @param params   the parameters including defaults
    /// @param minArgs  the number of required leading parameters
    /// @param callback the context-aware implementation
    /// @return the callable
    public static BuiltInCallable contextual(
            String name,
            List<Param> params,
            int minArgs,
            ContextCallback callback
    ) {
        return new BuiltInCallable(name, params, null, minArgs, callback);
    }

    /// Creates a context-aware built-in function with a rest parameter.
    ///
    /// @param name          the function name
    /// @param params        the ordinary parameters including defaults
    /// @param restParameter the rest parameter name
    /// @param minArgs       the number of required leading parameters
    /// @param callback      the context-aware implementation
    /// @return the callable
    public static BuiltInCallable contextualWithRest(
            String name,
            List<Param> params,
            String restParameter,
            int minArgs,
            ContextCallback callback
    ) {
        return new BuiltInCallable(
                name,
                params,
                Objects.requireNonNull(restParameter, "restParameter"),
                minArgs,
                callback
        );
    }

    /// Creates a context-aware built-in mixin with a rest parameter.
    ///
    /// The returned callable may receive a content block when included through
    /// the evaluator's mixin path.
    ///
    /// @param name          the mixin name
    /// @param params        the ordinary parameters including defaults
    /// @param restParameter the rest parameter name
    /// @param minArgs       the number of required leading parameters
    /// @param callback      the context-aware implementation
    /// @return the callable
    public static BuiltInCallable contextualMixinWithRest(
            String name,
            List<Param> params,
            String restParameter,
            int minArgs,
            ContextCallback callback
    ) {
        return new BuiltInCallable(
                name,
                params,
                Objects.requireNonNull(restParameter, "restParameter"),
                minArgs,
                true,
                callback
        );
    }

    /// Returns the normalized function name.
    ///
    /// @return the hyphenated name
    @Override
    public String name() {
        return name;
    }

    /// Returns ordinary parameters.
    ///
    /// @return the parameter list
    public @Unmodifiable List<Param> parameters() {
        return parameters;
    }

    /// Returns the rest parameter name.
    ///
    /// @return the rest name, or {@code null}
    public @Nullable String restParameter() {
        return restParameter;
    }

    /// Returns the number of required leading parameters.
    ///
    /// @return the minimum arity
    public int minArgs() {
        return minArgs;
    }

    /// Returns whether includes may pass a content block to this callable.
    ///
    /// @return whether the callable accepts content
    public boolean acceptsContent() {
        return acceptsContent;
    }

    /// Returns an equivalent callable exposed under a different normalized name.
    ///
    /// The returned callable retains this function's parameter contract and
    /// callback, so only name-based lookup and argument diagnostics change.
    ///
    /// @param name the replacement normalized name
    /// @return an equivalent callable with the replacement name
    public BuiltInCallable withName(String name) {
        return new BuiltInCallable(
                name,
                parameters,
                restParameter,
                minArgs,
                acceptsContent,
                callback
        );
    }

    /// Invokes this function with already-bound positional values and live context.
    ///
    /// @param context the context for this synchronous invocation
    /// @param bound   the bound argument values
    /// @return the function result
    public SassValue invoke(Context context, @Unmodifiable List<SassValue> bound) {
        return callback.apply(
                Objects.requireNonNull(context, "context"),
                Objects.requireNonNull(bound, "bound")
        );
    }

    /// Adapts a value-only callback to a context-aware callback.
    ///
    /// @param callback the value-only callback
    /// @return a callback that ignores its invocation context
    private static ContextCallback valueOnly(
            Function<@Unmodifiable List<SassValue>, SassValue> callback
    ) {
        var nonNullCallback = Objects.requireNonNull(callback, "callback");
        return (context, bound) -> nonNullCallback.apply(bound);
    }

    /// Implements one context-aware built-in function body.
    @FunctionalInterface
    @NotNullByDefault
    public interface ContextCallback {
        /// Evaluates a built-in call.
        ///
        /// @param context the synchronous invocation context
        /// @param bound   the already-bound argument values
        /// @return the resulting Sass value
        SassValue apply(Context context, @Unmodifiable List<SassValue> bound);
    }

    /// Invokes one function reference through the evaluator's normal call path.
    @FunctionalInterface
    @NotNullByDefault
    public interface FunctionValueInvoker {
        /// Invokes a function reference with already-evaluated argument values.
        ///
        /// @param function  the function reference to invoke
        /// @param arguments the preserved positional and keyword arguments
        /// @param span      the dynamic call span
        /// @return the resulting Sass value
        SassValue apply(SassFunction function, SassArgumentList arguments, SourceSpan span);
    }

    /// Invokes one mixin reference through the evaluator's normal include path.
    @FunctionalInterface
    @NotNullByDefault
    public interface MixinValueInvoker {
        /// Includes a mixin reference with already-evaluated argument values.
        ///
        /// @param mixin     the mixin reference to include
        /// @param arguments the preserved positional and keyword arguments
        /// @param content   the direct content block, or {@code null}
        /// @param span      the dynamic include span
        void apply(
                SassMixin mixin,
                SassArgumentList arguments,
                @Nullable UserDefinedCallable content,
                SourceSpan span
        );
    }

    /// Records one deprecation emitted during a contextual built-in call.
    @FunctionalInterface
    @NotNullByDefault
    public interface DeprecationReporter {
        /// Records one deprecation diagnostic.
        ///
        /// @param message the caller-facing deprecation message
        /// @param code    the stable deprecation identifier
        /// @param span    the source span that triggered the diagnostic
        void report(String message, String code, SourceSpan span);
    }

    /// Describes one synchronous built-in invocation through limited evaluator capabilities.
    ///
    /// This object is valid only while its callback runs. It intentionally
    /// exposes lookup operations rather than the mutable evaluator or its
    /// environment, and must not be retained after the callback returns.
    @NotNullByDefault
    public static final class Context {
        /// Contains the live lexical and module environment.
        private final Environment environment;

        /// Contains global built-ins keyed by normalized name.
        private final @Unmodifiable Map<String, BuiltInCallable> globalFunctions;

        /// Contains the stylesheet URL active at the call site, or {@code null}.
        private final @Nullable URI currentUrl;

        /// Contains the complete call span.
        private final SourceSpan span;

        /// Identifies the compilation active at this invocation.
        private final Object compilationContext;

        /// Invokes function references without exposing the evaluator.
        private final FunctionValueInvoker functionValueInvoker;

        /// Includes mixin references without exposing the evaluator.
        private final MixinValueInvoker mixinValueInvoker;

        /// Records callback-local deprecation diagnostics.
        private final DeprecationReporter deprecationReporter;

        /// Creates an invocation context with immutable global-function lookup.
        ///
        /// @param environment       the active lexical and module environment
        /// @param globalFunctions   immutable global built-ins keyed by normalized name
        /// @param currentUrl        the active stylesheet URL, or {@code null}
        /// @param span              the complete call span
        /// @param compilationContext the active compilation identity token
        /// @param functionValueInvoker invokes function references through normal evaluation
        /// @param mixinValueInvoker includes mixin references through normal evaluation
        /// @param deprecationReporter records callback-local deprecation diagnostics
        public Context(
                Environment environment,
                @Unmodifiable Map<String, BuiltInCallable> globalFunctions,
                @Nullable URI currentUrl,
                SourceSpan span,
                Object compilationContext,
                FunctionValueInvoker functionValueInvoker,
                MixinValueInvoker mixinValueInvoker,
                DeprecationReporter deprecationReporter
        ) {
            this.environment = Objects.requireNonNull(environment, "environment");
            this.globalFunctions = Objects.requireNonNull(globalFunctions, "globalFunctions");
            this.currentUrl = currentUrl;
            this.span = Objects.requireNonNull(span, "span");
            this.compilationContext = Objects.requireNonNull(compilationContext, "compilationContext");
            this.functionValueInvoker = Objects.requireNonNull(functionValueInvoker, "functionValueInvoker");
            this.mixinValueInvoker = Objects.requireNonNull(mixinValueInvoker, "mixinValueInvoker");
            this.deprecationReporter = Objects.requireNonNull(deprecationReporter, "deprecationReporter");
        }

        /// Returns the active stylesheet URL.
        ///
        /// @return the stylesheet URL, or {@code null} outside stylesheet execution
        public @Nullable URI currentUrl() {
            return currentUrl;
        }

        /// Returns the complete invocation span.
        ///
        /// @return the call span
        public SourceSpan span() {
            return span;
        }

        /// Returns whether a visible variable exists in the current lexical scope.
        ///
        /// An empty name does not identify a Sass variable and returns {@code false}.
        ///
        /// @param name the normalized variable name without a dollar sign
        /// @return whether a binding exists, including one whose value is Sass null
        public boolean variableExists(String name) {
            return name.isEmpty() ? false : environment.variableExists(name, null);
        }

        /// Returns whether a root-frame or global-module variable exists.
        ///
        /// An empty name returns {@code false} after checking a supplied module exists.
        ///
        /// @param name   the normalized variable name without a dollar sign
        /// @param module the module namespace, or {@code null} for root-frame and global-module lookup
        /// @return whether the requested global binding exists, including Sass null
        /// @throws org.glavo.scssfx.internal.value.SassValueException if a supplied module is absent
        public boolean globalVariableExists(String name, @Nullable String module) {
            if (name.isEmpty()) {
                if (module != null) {
                    environment.module(module);
                }
                return false;
            }
            return environment.globalVariableExists(name, module);
        }

        /// Returns whether a function exists in the active environment or global built-ins.
        ///
        /// An empty name returns {@code false} after checking a supplied module exists.
        ///
        /// @param name   the normalized function name
        /// @param module the module namespace, or {@code null} for lexical and global-module lookup
        /// @return whether a function exists
        /// @throws org.glavo.scssfx.internal.value.SassValueException if a supplied module is absent
        public boolean functionExists(String name, @Nullable String module) {
            if (name.isEmpty()) {
                if (module != null) {
                    environment.module(module);
                }
                return false;
            }
            return environment.getFunction(name, module) != null
                    || module == null && globalFunctions.containsKey(name);
        }

        /// Returns whether a mixin exists in the active environment.
        ///
        /// An empty name returns {@code false} after checking a supplied module exists.
        ///
        /// @param name   the normalized mixin name
        /// @param module the module namespace, or {@code null} for lexical and global-module lookup
        /// @return whether a mixin exists
        /// @throws org.glavo.scssfx.internal.value.SassValueException if a supplied module is absent
        public boolean mixinExists(String name, @Nullable String module) {
            if (name.isEmpty()) {
                if (module != null) {
                    environment.module(module);
                }
                return false;
            }
            return environment.getMixin(name, module) != null;
        }

        /// Returns immutable public variable values from one explicitly named module.
        ///
        /// @param module the module namespace
        /// @return the public values keyed by normalized variable name
        /// @throws org.glavo.scssfx.internal.value.SassValueException if no named module exists
        public @Unmodifiable Map<String, SassValue> moduleVariables(String module) {
            LoadedModule loaded = environment.module(module);
            var values = new LinkedHashMap<String, SassValue>(loaded.variables().size());
            for (var entry : loaded.variables().entrySet()) {
                VariableBinding binding = entry.getValue();
                values.put(entry.getKey(), binding.value());
            }
            return Collections.unmodifiableMap(values);
        }

        /// Resolves a visible Sass function as a first-class function reference.
        ///
        /// A named module restricts lookup to that explicit namespace. An
        /// unqualified lookup falls back to global built-ins only after lexical
        /// and {@code as *} module lookup has failed.
        ///
        /// @param name   the non-empty normalized function name
        /// @param module the explicit module namespace, or {@code null}
        /// @return the function reference, or {@code null} when absent
        /// @throws org.glavo.scssfx.internal.value.SassValueException if a supplied module is absent
        public @Nullable SassFunction function(String name, @Nullable String module) {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty()) {
                if (module != null) {
                    environment.module(module);
                }
                return null;
            }
            @Nullable Callable callable = environment.getFunction(name, module);
            if (callable == null && module == null) {
                callable = globalFunctions.get(name);
            }
            return callable == null ? null : new SassFunction(callable, compilationContext);
        }

        /// Resolves a visible Sass mixin as a first-class mixin reference.
        ///
        /// A named module restricts lookup to that explicit namespace. An
        /// unqualified lookup searches lexical and {@code as *} module exports.
        ///
        /// @param name   the non-empty normalized mixin name
        /// @param module the explicit module namespace, or {@code null}
        /// @return the mixin reference, or {@code null} when absent
        /// @throws org.glavo.scssfx.internal.value.SassValueException if a supplied module is absent
        public @Nullable SassMixin mixin(String name, @Nullable String module) {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty()) {
                if (module != null) {
                    environment.module(module);
                }
                return null;
            }
            @Nullable Callable callable = environment.getMixin(name, module);
            return callable == null ? null : new SassMixin(callable, compilationContext);
        }

        /// Creates a first-class reference to a plain CSS function.
        ///
        /// @param name the CSS function name
        /// @return a function reference that serializes calls as plain CSS
        public SassFunction plainCssFunction(String name) {
            return new SassFunction(new PlainCssCallable(name), compilationContext);
        }

        /// Returns public functions from one explicitly named module as function references.
        ///
        /// @param module the explicit module namespace
        /// @return immutable public functions keyed by normalized name
        /// @throws org.glavo.scssfx.internal.value.SassValueException if no named module exists
        public @Unmodifiable Map<String, SassFunction> moduleFunctions(String module) {
            LoadedModule loaded = environment.module(module);
            var functions = new LinkedHashMap<String, SassFunction>(loaded.functions().size());
            for (var entry : loaded.functions().entrySet()) {
                functions.put(entry.getKey(), new SassFunction(entry.getValue(), compilationContext));
            }
            return Collections.unmodifiableMap(functions);
        }

        /// Returns public mixins from one explicitly named module as mixin references.
        ///
        /// @param module the explicit module namespace
        /// @return immutable public mixins keyed by normalized name
        /// @throws org.glavo.scssfx.internal.value.SassValueException if no named module exists
        public @Unmodifiable Map<String, SassMixin> moduleMixins(String module) {
            LoadedModule loaded = environment.module(module);
            var mixins = new LinkedHashMap<String, SassMixin>(loaded.mixins().size());
            for (var entry : loaded.mixins().entrySet()) {
                mixins.put(entry.getKey(), new SassMixin(entry.getValue(), compilationContext));
            }
            return Collections.unmodifiableMap(mixins);
        }

        /// Includes a mixin reference with an already-evaluated argument list.
        ///
        /// The current direct content block is forwarded to the dynamic target.
        ///
        /// @param mixin     the mixin reference to include
        /// @param arguments the positional and keyword arguments to forward
        /// @throws org.glavo.scssfx.internal.value.SassValueException if the reference belongs to another compilation
        public void apply(SassMixin mixin, SassArgumentList arguments) {
            Objects.requireNonNull(mixin, "mixin").assertCompilationContext(compilationContext);
            mixinValueInvoker.apply(
                    mixin,
                    Objects.requireNonNull(arguments, "arguments"),
                    environment.content(),
                    span
            );
        }

        /// Returns whether the current mixin invocation received a content block.
        ///
        /// @return whether a direct content block is available
        /// @throws org.glavo.scssfx.internal.value.SassValueException outside mixin execution
        public boolean contentExists() {
            if (!environment.inMixin()) {
                throw new org.glavo.scssfx.internal.value.SassValueException(
                        "content-exists() may only be called within a mixin."
                );
            }
            return environment.content() != null;
        }

        /// Invokes a function reference with an already-evaluated argument list.
        ///
        /// @param function  the function reference to invoke
        /// @param arguments the positional and keyword arguments to forward
        /// @return the resulting Sass value
        /// @throws org.glavo.scssfx.internal.value.SassValueException if the reference belongs to another compilation
        public SassValue call(SassFunction function, SassArgumentList arguments) {
            Objects.requireNonNull(function, "function").assertCompilationContext(compilationContext);
            return functionValueInvoker.apply(
                    function,
                    Objects.requireNonNull(arguments, "arguments"),
                    span
            );
        }

        /// Records a deprecation for this contextual call.
        ///
        /// @param message the caller-facing deprecation message
        /// @param code    the stable deprecation identifier
        public void deprecate(String message, String code) {
            deprecationReporter.report(
                    Objects.requireNonNull(message, "message"),
                    Objects.requireNonNull(code, "code"),
                    span
            );
        }
    }

    /// One built-in parameter declaration.
    ///
    /// @param name          the normalized parameter name
    /// @param defaultValue  the default value, or {@code null} when required
    public record Param(String name, @Nullable SassValue defaultValue) {
        /// Creates a parameter.
        public Param {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name must not be empty");
            }
        }

        /// Creates a required parameter.
        ///
        /// @param name the parameter name
        /// @return the parameter
        public static Param required(String name) {
            return new Param(name, null);
        }

        /// Creates an optional parameter.
        ///
        /// @param name         the parameter name
        /// @param defaultValue the default value
        /// @return the parameter
        public static Param optional(String name, SassValue defaultValue) {
            return new Param(name, Objects.requireNonNull(defaultValue, "defaultValue"));
        }
    }
}
