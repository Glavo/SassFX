// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.glavo.scssfx.SourceSpan;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Stores Sass lexical bindings and their dynamic assignment semantics.
///
/// Frame zero is the global frame. Closures share frames that existed when
/// they were created but have independent frame stacks for later scopes. A
/// Java `null` lookup result means that no binding exists; Sass null is stored
/// as a non-null value.
@ApiStatus.Internal
@NotNullByDefault
public final class Environment {
    /// Contains lexical frames from the global frame through the current frame.
    private final ArrayList<LinkedHashMap<String, VariableBinding>> variableFrames;

    /// Contains active scope handles in last-opened-first order.
    private final Deque<Scope> activeScopes;

    /// Records whether ordinary assignments may update the global frame.
    private boolean inSemiGlobalScope;

    /// Creates an environment containing one empty global frame.
    public Environment() {
        this.variableFrames = new ArrayList<>();
        this.variableFrames.add(new LinkedHashMap<>());
        this.activeScopes = new ArrayDeque<>();
        this.inSemiGlobalScope = true;
    }

    /// Creates a closure environment that shares existing frames.
    ///
    /// @param variableFrames the frames captured by reference
    private Environment(List<LinkedHashMap<String, VariableBinding>> variableFrames) {
        this.variableFrames = new ArrayList<>(variableFrames);
        this.activeScopes = new ArrayDeque<>();
        this.inSemiGlobalScope = true;
    }

    /// Returns a closure that shares every currently visible binding frame.
    ///
    /// Assignments to captured frames are visible through both environments.
    /// Scopes opened later in either environment are not added to the other.
    /// The closure begins outside any dynamic assignment scope, so ordinary
    /// assignments initially use semi-global rules.
    ///
    /// @return the closure environment
    public Environment closure() {
        return new Environment(variableFrames);
    }

    /// Returns whether the current frame is the global frame.
    ///
    /// @return whether no lexical frame is nested
    public boolean atRoot() {
        return variableFrames.size() == 1;
    }

    /// Finds a visible variable binding.
    ///
    /// This environment contains lexical frames but no module registry, so a
    /// non-null namespace is rejected rather than treated as a local name.
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
            throw missingModule(namespace);
        }
        for (var index = variableFrames.size() - 1; index >= 0; index--) {
            @Nullable VariableBinding binding = variableFrames.get(index).get(name);
            if (binding != null) {
                return binding;
            }
        }
        return null;
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
    /// At root or with {@code global}, this writes frame zero. Otherwise an
    /// existing outer local is updated. A global binding is shadowed in a
    /// lexical scope but updated from an effective flow-control scope. A new
    /// name is always created in the current frame.
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
            throw missingModule(namespace);
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

    /// Opens a dynamically scoped assignment mode and optional lexical frame.
    ///
    /// Flow-control permission is intersected with the enclosing permission.
    /// This state transition occurs even when {@code createFrame} is false.
    /// The returned handle must be closed in last-opened-first order.
    ///
    /// @param semantics   the requested assignment semantics
    /// @param createFrame whether to push an empty lexical frame
    /// @return the scope handle
    public Scope scope(ScopeSemantics semantics, boolean createFrame) {
        Objects.requireNonNull(semantics, "semantics");
        var previousSemiGlobal = inSemiGlobalScope;
        inSemiGlobalScope = semantics == ScopeSemantics.FLOW_CONTROL
                && previousSemiGlobal;
        if (createFrame) {
            variableFrames.add(new LinkedHashMap<>());
        }
        var scope = new Scope(this, previousSemiGlobal, createFrame);
        activeScopes.push(scope);
        return scope;
    }

    /// Returns an immutable snapshot of global values in declaration order.
    ///
    /// Later assignments do not change the returned map. Reassigning an
    /// existing name does not change its iteration position.
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

    /// Validates a normalized variable name.
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
    ///
    /// A handle is idempotent after a successful close. Closing active
    /// handles out of order is rejected without changing the environment.
    @ApiStatus.Internal
    @NotNullByDefault
    public static final class Scope implements AutoCloseable {
        /// Contains the owning environment.
        private final Environment environment;

        /// Contains the assignment mode restored on close.
        private final boolean previousSemiGlobal;

        /// Records whether this scope pushed a lexical frame.
        private final boolean createdFrame;

        /// Records whether this handle has already restored its environment.
        private boolean closed;

        /// Creates an active scope handle.
        ///
        /// @param environment        the owning environment
        /// @param previousSemiGlobal the mode restored on close
        /// @param createdFrame       whether a lexical frame was pushed
        private Scope(
                Environment environment,
                boolean previousSemiGlobal,
                boolean createdFrame
        ) {
            this.environment = environment;
            this.previousSemiGlobal = previousSemiGlobal;
            this.createdFrame = createdFrame;
        }

        /// Restores the preceding frame stack and assignment mode.
        ///
        /// Repeated calls after a successful close have no effect.
        ///
        /// @throws IllegalStateException if a newer scope remains active
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
            }
            environment.inSemiGlobalScope = previousSemiGlobal;
            closed = true;
        }
    }
}
