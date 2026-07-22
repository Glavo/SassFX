// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.ForwardRule;
import org.glavo.scssfx.internal.callable.Callable;
import org.glavo.scssfx.internal.callable.UserDefinedCallable;
import org.glavo.scssfx.internal.module.ForwardedModuleView;
import org.glavo.scssfx.internal.module.LoadedModule;
import org.glavo.scssfx.internal.module.MemberNames;
import org.glavo.scssfx.internal.value.SassValue;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/// Stores Sass lexical bindings and their dynamic assignment semantics.
///
/// Frame zero is the global frame. Closures share frames that existed when
/// they were created but have independent frame stacks for later scopes. A
/// Java `null` lookup result means that no binding exists; Sass null is stored
/// as a non-null value.
@ApiStatus.Internal
@NotNullByDefault
public final class Environment {
    /// Contains lexical variable frames from the global frame through the current frame.
    private final ArrayList<LinkedHashMap<String, VariableBinding>> variableFrames;

    /// Contains lexical function frames parallel to the variable frames.
    private final ArrayList<LinkedHashMap<String, Callable>> functionFrames;

    /// Contains lexical mixin frames parallel to the variable frames.
    private final ArrayList<LinkedHashMap<String, Callable>> mixinFrames;

    /// Contains the active content block callable, or {@code null}.
    private @Nullable UserDefinedCallable content;

    /// Contains modules loaded with an explicit namespace.
    private final LinkedHashMap<String, LoadedModule> modules;

    /// Contains modules loaded with {@code as *}.
    private final ArrayList<LoadedModule> globalModules;

    /// Contains every module added to this environment in source order.
    private final ArrayList<LoadedModule> allModules;

    /// Contains modules whose public members are re-exported downstream.
    private final ArrayList<ForwardedModuleView> forwardedModules;

    /// Contains root variables declared with {@code !default}.
    private final LinkedHashSet<String> configurableVariables;

    /// Contains active scope handles in last-opened-first order.
    private final Deque<Scope> activeScopes;

    /// Records whether ordinary assignments may update the global frame.
    private boolean inSemiGlobalScope;

    /// Creates an environment containing one empty global frame.
    public Environment() {
        this.variableFrames = new ArrayList<>();
        this.variableFrames.add(new LinkedHashMap<>());
        this.functionFrames = new ArrayList<>();
        this.functionFrames.add(new LinkedHashMap<>());
        this.mixinFrames = new ArrayList<>();
        this.mixinFrames.add(new LinkedHashMap<>());
        this.content = null;
        this.modules = new LinkedHashMap<>();
        this.globalModules = new ArrayList<>();
        this.allModules = new ArrayList<>();
        this.forwardedModules = new ArrayList<>();
        this.configurableVariables = new LinkedHashSet<>();
        this.activeScopes = new ArrayDeque<>();
        this.inSemiGlobalScope = true;
    }

    /// Creates a closure environment that shares existing frames and modules.
    private Environment(
            List<LinkedHashMap<String, VariableBinding>> variableFrames,
            List<LinkedHashMap<String, Callable>> functionFrames,
            List<LinkedHashMap<String, Callable>> mixinFrames,
            @Nullable UserDefinedCallable content,
            LinkedHashMap<String, LoadedModule> modules,
            ArrayList<LoadedModule> globalModules,
            ArrayList<LoadedModule> allModules,
            ArrayList<ForwardedModuleView> forwardedModules,
            LinkedHashSet<String> configurableVariables
    ) {
        this.variableFrames = new ArrayList<>(variableFrames);
        this.functionFrames = new ArrayList<>(functionFrames);
        this.mixinFrames = new ArrayList<>(mixinFrames);
        this.content = content;
        this.modules = modules;
        this.globalModules = globalModules;
        this.allModules = allModules;
        this.forwardedModules = forwardedModules;
        this.configurableVariables = configurableVariables;
        this.activeScopes = new ArrayDeque<>();
        this.inSemiGlobalScope = true;
    }

    /// Returns a closure that shares every currently visible binding frame.
    ///
    /// Assignments to captured frames are visible through both environments.
    /// Scopes opened later in either environment are not added to the other.
    /// Module registries are shared by reference.
    ///
    /// @return the closure environment
    public Environment closure() {
        return new Environment(
                variableFrames,
                functionFrames,
                mixinFrames,
                content,
                modules,
                globalModules,
                allModules,
                forwardedModules,
                configurableVariables
        );
    }

    /// Returns whether the current frame is the global frame.
    ///
    /// @return whether no lexical frame is nested
    public boolean atRoot() {
        return variableFrames.size() == 1;
    }

    /// Finds a visible variable binding.
    ///
    /// @param name      the normalized variable name without a dollar sign
    /// @param namespace the module namespace, or {@code null} for lexical lookup
    /// @return the nearest binding, or {@code null} when no lexical binding exists
    /// @throws SassValueException if a namespace is supplied
    public @Nullable VariableBinding findVariable(
            String name,
            @Nullable String namespace
    ) {
        validateName(name);
        if (namespace != null) {
            return requireModule(namespace).variables().get(name);
        }
        for (var index = variableFrames.size() - 1; index >= 0; index--) {
            @Nullable VariableBinding binding = variableFrames.get(index).get(name);
            if (binding != null) {
                return binding;
            }
        }
        return fromOneGlobalModule(name, LoadedModule::variables);
    }

    /// Returns the value of a visible variable.
    ///
    /// @param name      the normalized variable name without a dollar sign
    /// @param namespace the module namespace, or {@code null} for lexical lookup
    /// @return the value, or {@code null} when no lexical binding exists
    /// @throws SassValueException if a namespace is supplied
    public @Nullable SassValue getVariable(String name, @Nullable String namespace) {
        @Nullable VariableBinding binding = findVariable(name, namespace);
        return binding == null ? null : binding.value();
    }

    /// Returns whether a visible variable binding exists.
    ///
    /// @param name      the normalized variable name without a dollar sign
    /// @param namespace the module namespace, or {@code null} for lexical lookup
    /// @return whether lookup finds a binding, including one whose value is Sass null
    /// @throws SassValueException if a namespace is supplied
    public boolean variableExists(String name, @Nullable String namespace) {
        return findVariable(name, namespace) != null;
    }

    /// Returns whether a variable exists in the global frame.
    ///
    /// @param name      the normalized variable name without a dollar sign
    /// @param namespace the module namespace, or {@code null} for this environment
    /// @return whether the global binding exists, including Sass null
    /// @throws SassValueException if a namespace is supplied
    public boolean globalVariableExists(String name, @Nullable String namespace) {
        validateName(name);
        if (namespace != null) {
            throw missingModule(namespace);
        }
        return variableFrames.get(0).containsKey(name);
    }

    /// Assigns a variable according to lexical, semi-global, and explicit-global rules.
    ///
    /// @param name       the normalized variable name without a dollar sign
    /// @param value      the assigned value
    /// @param originSpan the source that produced the value
    /// @param namespace  the module namespace, or {@code null} for lexical assignment
    /// @param global     whether to write frame zero explicitly
    /// @throws SassValueException if a namespace is supplied
    public void setVariable(
            String name,
            SassValue value,
            SourceSpan originSpan,
            @Nullable String namespace,
            boolean global
    ) {
        validateName(name);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(originSpan, "originSpan");
        if (namespace != null) {
            requireModule(namespace);
            throw new SassValueException(
                    "Modifying module variables isn't supported yet."
            );
        }

        var binding = new VariableBinding(value, originSpan);
        if (global || atRoot()) {
            variableFrames.get(0).put(name, binding);
            return;
        }

        var index = bindingFrame(name);
        if (index < 0 || !inSemiGlobalScope && index == 0) {
            index = variableFrames.size() - 1;
        }
        variableFrames.get(index).put(name, binding);
    }

    /// Assigns a variable in the current frame regardless of outer bindings.
    ///
    /// @param name       the normalized variable name without a dollar sign
    /// @param value      the assigned value
    /// @param originSpan the source that produced the value
    public void setLocalVariable(String name, SassValue value, SourceSpan originSpan) {
        validateName(name);
        variableFrames.get(variableFrames.size() - 1).put(
                name,
                new VariableBinding(
                        Objects.requireNonNull(value, "value"),
                        Objects.requireNonNull(originSpan, "originSpan")
                )
        );
    }

    /// Registers a function in the current frame.
    ///
    /// @param callable the function callable
    public void setFunction(Callable callable) {
        Objects.requireNonNull(callable, "callable");
        functionFrames.get(functionFrames.size() - 1).put(callable.name(), callable);
    }

    /// Registers a mixin in the current frame.
    ///
    /// @param callable the mixin callable
    public void setMixin(Callable callable) {
        Objects.requireNonNull(callable, "callable");
        mixinFrames.get(mixinFrames.size() - 1).put(callable.name(), callable);
    }

    /// Finds a visible function.
    ///
    /// @param name      the normalized function name
    /// @param namespace the module namespace, or {@code null}
    /// @return the function, or {@code null} when absent
    /// @throws SassValueException if a namespace is supplied
    public @Nullable Callable getFunction(String name, @Nullable String namespace) {
        validateName(name);
        if (namespace != null) {
            return requireModule(namespace).functions().get(name);
        }
        for (var index = functionFrames.size() - 1; index >= 0; index--) {
            @Nullable Callable callable = functionFrames.get(index).get(name);
            if (callable != null) {
                return callable;
            }
        }
        return fromOneGlobalModule(name, LoadedModule::functions);
    }

    /// Finds a visible mixin.
    ///
    /// @param name      the normalized mixin name
    /// @param namespace the module namespace, or {@code null}
    /// @return the mixin, or {@code null} when absent
    /// @throws SassValueException if a namespace is supplied
    public @Nullable Callable getMixin(String name, @Nullable String namespace) {
        validateName(name);
        if (namespace != null) {
            return requireModule(namespace).mixins().get(name);
        }
        for (var index = mixinFrames.size() - 1; index >= 0; index--) {
            @Nullable Callable callable = mixinFrames.get(index).get(name);
            if (callable != null) {
                return callable;
            }
        }
        return fromOneGlobalModule(name, LoadedModule::mixins);
    }

    /// Adds a loaded module to this environment.
    ///
    /// @param module    the loaded module
    /// @param namespace the namespace, or {@code null} for {@code as *}
    /// @param useSpan   the `@use` span used for conflict diagnostics
    /// @throws SassValueException if the namespace is already taken or {@code as *}
    /// conflicts with existing members
    public void addModule(
            LoadedModule module,
            @Nullable String namespace,
            SourceSpan useSpan
    ) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(useSpan, "useSpan");
        if (namespace == null) {
            for (var name : module.variables().keySet()) {
                if (variableExists(name, null)) {
                    throw new SassValueException(
                            "This module and the new module both define a variable named \"$"
                                    + name + "\"."
                    );
                }
            }
            globalModules.add(module);
        } else {
            if (namespace.isEmpty()) {
                throw new IllegalArgumentException("namespace must not be empty");
            }
            if (modules.containsKey(namespace)) {
                throw new SassValueException(
                        "There's already a module with namespace \"" + namespace + "\"."
                );
            }
            modules.put(namespace, module);
        }
        allModules.add(module);
    }

    /// Re-exports a transformed module view without adding local bindings.
    ///
    /// The original module is added to the CSS dependency graph. Prefixing and
    /// filtering affect only exported members and configurable names.
    ///
    /// @param module the loaded module
    /// @param rule   the forwarding rule that defines the export view
    /// @throws SassValueException if another forwarded module exposes a
    /// conflicting member
    public void forwardModule(LoadedModule module, ForwardRule rule) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(rule, "rule");
        var view = ForwardedModuleView.create(module, rule);
        assertNoForwardConflicts(
                view,
                "variable",
                ForwardedModuleView::variables
        );
        assertNoForwardConflicts(
                view,
                "function",
                ForwardedModuleView::functions
        );
        assertNoForwardConflicts(
                view,
                "mixin",
                ForwardedModuleView::mixins
        );
        forwardedModules.add(view);
        allModules.add(view.original());
    }

    /// Returns modules added to this environment in source order.
    ///
    /// @return the upstream modules
    public @Unmodifiable List<LoadedModule> allModules() {
        return List.copyOf(allModules);
    }

    /// Returns forward views used to resolve configuration reachability.
    ///
    /// @return an immutable source-order snapshot
    public @Unmodifiable List<ForwardedModuleView> forwardedModules() {
        return List.copyOf(forwardedModules);
    }

    /// Returns public global variables for module export.
    ///
    /// @return the public global variable bindings
    public @Unmodifiable Map<String, VariableBinding> publicGlobalVariables() {
        var result = new LinkedHashMap<String, VariableBinding>();
        for (var module : forwardedModules) {
            result.putAll(module.variables());
        }
        for (var entry : variableFrames.get(0).entrySet()) {
            if (MemberNames.isPublic(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns public global functions for module export.
    ///
    /// @return the public global functions
    public @Unmodifiable Map<String, Callable> publicGlobalFunctions() {
        var result = new LinkedHashMap<String, Callable>();
        for (var module : forwardedModules) {
            result.putAll(module.functions());
        }
        for (var entry : functionFrames.get(0).entrySet()) {
            if (MemberNames.isPublic(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns public global mixins for module export.
    ///
    /// @return the public global mixins
    public @Unmodifiable Map<String, Callable> publicGlobalMixins() {
        var result = new LinkedHashMap<String, Callable>();
        for (var module : forwardedModules) {
            result.putAll(module.mixins());
        }
        for (var entry : mixinFrames.get(0).entrySet()) {
            if (MemberNames.isPublic(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /// Records that a root variable could be configured by a module load.
    ///
    /// @param name the normalized variable name
    public void markVariableConfigurable(String name) {
        validateName(name);
        configurableVariables.add(name);
    }

    /// Returns root variables declared with {@code !default}.
    ///
    /// @return an immutable declaration-order snapshot
    public @Unmodifiable Set<String> configurableVariables() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(configurableVariables)
        );
    }

    /// Rejects conflicts between a new forward and existing forwarded modules.
    ///
    /// @param view    the prospective forwarded view
    /// @param kind    the member kind used in diagnostics
    /// @param members the member-map accessor for that kind
    /// @param <T>     the member identity type
    /// @throws SassValueException if different member identities share a name
    private <T> void assertNoForwardConflicts(
            ForwardedModuleView view,
            String kind,
            Function<ForwardedModuleView, Map<String, T>> members
    ) {
        var newMembers = members.apply(view);
        for (var forwarded : forwardedModules) {
            var oldMembers = members.apply(forwarded);
            for (var entry : newMembers.entrySet()) {
                @Nullable T oldMember = oldMembers.get(entry.getKey());
                if (oldMember == null || oldMember == entry.getValue()) {
                    continue;
                }
                var displayName = "variable".equals(kind)
                        ? "$" + entry.getKey()
                        : entry.getKey();
                throw new SassValueException(
                        "Two forwarded modules both define a " + kind
                                + " named " + displayName + "."
                );
            }
        }
    }

    /// Returns a namespaced module or fails.
    private LoadedModule requireModule(String namespace) {
        @Nullable LoadedModule module = modules.get(namespace);
        if (module == null) {
            throw missingModule(namespace);
        }
        return module;
    }

    /// Looks up a member across {@code as *} modules.
    private <T> @Nullable T fromOneGlobalModule(
            String name,
            java.util.function.Function<LoadedModule, Map<String, T>> getter
    ) {
        @Nullable T found = null;
        for (var module : globalModules) {
            @Nullable T value = getter.apply(module).get(name);
            if (value != null) {
                if (found != null) {
                    throw new SassValueException(
                            "This variable is available from multiple global modules."
                    );
                }
                found = value;
            }
        }
        return found;
    }

    /// Returns the active content block callable.
    ///
    /// @return the content callable, or {@code null}
    public @Nullable UserDefinedCallable content() {
        return content;
    }

    /// Runs a body with a temporary content block binding.
    ///
    /// @param content the content callable, or {@code null}
    /// @param body    the body to run
    /// @param <T>     the result type
    /// @return the body result
    public <T> T withContent(@Nullable UserDefinedCallable content, Supplier<T> body) {
        Objects.requireNonNull(body, "body");
        var previous = this.content;
        this.content = content;
        try {
            return body.get();
        } finally {
            this.content = previous;
        }
    }

    /// Opens a dynamically scoped assignment mode and optional lexical frame.
    ///
    /// @param semantics   the requested assignment semantics
    /// @param createFrame whether to push empty lexical frames
    /// @return the scope handle
    public Scope scope(ScopeSemantics semantics, boolean createFrame) {
        Objects.requireNonNull(semantics, "semantics");
        var previousSemiGlobal = inSemiGlobalScope;
        inSemiGlobalScope = semantics == ScopeSemantics.FLOW_CONTROL
                && previousSemiGlobal;
        if (createFrame) {
            variableFrames.add(new LinkedHashMap<>());
            functionFrames.add(new LinkedHashMap<>());
            mixinFrames.add(new LinkedHashMap<>());
        }
        var scope = new Scope(this, previousSemiGlobal, createFrame);
        activeScopes.push(scope);
        return scope;
    }

    /// Returns an immutable snapshot of global values in declaration order.
    ///
    /// @return the global value snapshot
    public @Unmodifiable Map<String, SassValue> globalVariablesSnapshot() {
        var result = new LinkedHashMap<String, SassValue>(variableFrames.get(0).size());
        for (var entry : variableFrames.get(0).entrySet()) {
            result.put(entry.getKey(), entry.getValue().value());
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns an immutable snapshot of global bindings in declaration order.
    ///
    /// @return the global binding snapshot
    public @Unmodifiable Map<String, VariableBinding> globalBindingsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variableFrames.get(0)));
    }

    /// Returns the nearest frame containing a name.
    ///
    /// @param name the normalized variable name
    /// @return the frame index, or {@code -1} when absent
    private int bindingFrame(String name) {
        for (var index = variableFrames.size() - 1; index >= 0; index--) {
            if (variableFrames.get(index).containsKey(name)) {
                return index;
            }
        }
        return -1;
    }

    /// Validates a normalized name.
    ///
    /// @param name the name to validate
    /// @throws IllegalArgumentException if the name is empty
    private static void validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
    }

    /// Creates the standard missing-module failure.
    ///
    /// @param namespace the missing namespace
    /// @return the span-free value-layer failure
    private static SassValueException missingModule(String namespace) {
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        return new SassValueException(
                "There is no module with the namespace \"" + namespace + "\"."
        );
    }

    /// Restores an environment after one dynamic scope.
    @ApiStatus.Internal
    @NotNullByDefault
    public static final class Scope implements AutoCloseable {
        /// Contains the owning environment.
        private final Environment environment;

        /// Contains the assignment mode restored on close.
        private final boolean previousSemiGlobal;

        /// Records whether this scope pushed lexical frames.
        private final boolean createdFrame;

        /// Records whether this handle has already restored its environment.
        private boolean closed;

        /// Creates an active scope handle.
        private Scope(
                Environment environment,
                boolean previousSemiGlobal,
                boolean createdFrame
        ) {
            this.environment = environment;
            this.previousSemiGlobal = previousSemiGlobal;
            this.createdFrame = createdFrame;
        }

        /// Restores the preceding frame stacks and assignment mode.
        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (environment.activeScopes.peek() != this) {
                throw new IllegalStateException("scopes must be closed in last-opened-first order");
            }
            environment.activeScopes.pop();
            if (createdFrame) {
                environment.variableFrames.remove(environment.variableFrames.size() - 1);
                environment.functionFrames.remove(environment.functionFrames.size() - 1);
                environment.mixinFrames.remove(environment.mixinFrames.size() - 1);
            }
            environment.inSemiGlobalScope = previousSemiGlobal;
            closed = true;
        }
    }
}
