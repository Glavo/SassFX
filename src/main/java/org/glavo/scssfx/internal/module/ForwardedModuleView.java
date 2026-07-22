// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.internal.ast.ForwardRule;
import org.glavo.scssfx.internal.callable.Callable;
import org.glavo.scssfx.internal.evaluate.VariableBinding;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Exposes a transformed member view while retaining the original module identity.
///
/// Prefixes are applied before show and hide filters for exports. Configuration
/// candidates are unprefixed before variable filters are applied, matching Sass
/// module configuration behavior.
///
/// @param original          the module that was evaluated
/// @param variables         forwarded variable bindings
/// @param functions         forwarded functions
/// @param mixins            forwarded mixins
/// @param prefix            the configuration prefix, or {@code null}
/// @param shownVariables    the variable allowlist, or {@code null}
/// @param hiddenVariables   the variable blocklist, or {@code null}
@ApiStatus.Internal
@NotNullByDefault
public record ForwardedModuleView(
        LoadedModule original,
        @Unmodifiable Map<String, VariableBinding> variables,
        @Unmodifiable Map<String, Callable> functions,
        @Unmodifiable Map<String, Callable> mixins,
        @Nullable String prefix,
        @Nullable @Unmodifiable Set<String> shownVariables,
        @Nullable @Unmodifiable Set<String> hiddenVariables
) {
    /// Creates an immutable forwarded-module view.
    public ForwardedModuleView {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(mixins, "mixins");
        if (prefix != null && prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be empty");
        }
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        mixins = Collections.unmodifiableMap(new LinkedHashMap<>(mixins));
        shownVariables = immutableSet(shownVariables);
        hiddenVariables = immutableSet(hiddenVariables);
    }

    /// Creates the export view described by a forward rule.
    ///
    /// @param module the loaded target module
    /// @param rule   the forwarding rule
    /// @return the transformed export view
    public static ForwardedModuleView create(
            LoadedModule module,
            ForwardRule rule
    ) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(rule, "rule");
        return new ForwardedModuleView(
                module,
                forwardedMap(
                        module.variables(),
                        rule.prefix(),
                        rule.shownVariables(),
                        rule.hiddenVariables()
                ),
                forwardedMap(
                        module.functions(),
                        rule.prefix(),
                        rule.shownMixinsAndFunctions(),
                        rule.hiddenMixinsAndFunctions()
                ),
                forwardedMap(
                        module.mixins(),
                        rule.prefix(),
                        rule.shownMixinsAndFunctions(),
                        rule.hiddenMixinsAndFunctions()
                ),
                rule.prefix(),
                rule.shownVariables(),
                rule.hiddenVariables()
        );
    }

    /// Returns whether candidate names could configure the original module.
    ///
    /// Prefixes are removed before the variable filter is applied. Candidates
    /// that remain are delegated recursively to the original module.
    ///
    /// @param names variable names visible at the forwarding module boundary
    /// @return whether any candidate may reach a configurable declaration
    public boolean couldHaveBeenConfigured(@Unmodifiable Set<String> names) {
        Objects.requireNonNull(names, "names");
        var innerNames = new LinkedHashSet<String>();
        for (var candidate : names) {
            var name = candidate;
            if (prefix != null) {
                if (!name.startsWith(prefix)) {
                    continue;
                }
                name = name.substring(prefix.length());
            }
            if (isVisible(name, shownVariables, hiddenVariables)) {
                innerNames.add(name);
            }
        }
        return original.couldHaveBeenConfigured(innerNames);
    }

    /// Applies prefixing and filtering to one member map.
    ///
    /// @param members the original member map
    /// @param prefix  the prefix, or {@code null}
    /// @param shown   the allowlist, or {@code null}
    /// @param hidden  the blocklist, or {@code null}
    /// @param <T>     the member type
    /// @return an immutable transformed map retaining member identities
    private static <T> @Unmodifiable Map<String, T> forwardedMap(
            Map<String, T> members,
            @Nullable String prefix,
            @Nullable Set<String> shown,
            @Nullable Set<String> hidden
    ) {
        var result = new LinkedHashMap<String, T>();
        for (var entry : members.entrySet()) {
            var name = prefix == null ? entry.getKey() : prefix + entry.getKey();
            if (isVisible(name, shown, hidden)) {
                result.put(name, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns an immutable copy of a nullable variable filter.
    ///
    /// @param values the source set, or {@code null}
    /// @return an immutable set, or {@code null}
    private static @Nullable @Unmodifiable Set<String> immutableSet(
            @Nullable Set<String> values
    ) {
        return values == null ? null : Set.copyOf(values);
    }

    /// Returns whether one name passes an active member filter.
    ///
    /// @param name   the name at the filter's comparison boundary
    /// @param shown  the allowlist, or {@code null}
    /// @param hidden the blocklist, or {@code null}
    /// @return whether the member is visible
    private static boolean isVisible(
            String name,
            @Nullable Set<String> shown,
            @Nullable Set<String> hidden
    ) {
        return shown != null
                ? shown.contains(name)
                : hidden == null || !hidden.contains(name);
    }
}
