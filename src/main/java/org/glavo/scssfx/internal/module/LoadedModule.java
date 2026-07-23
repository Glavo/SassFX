// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.internal.callable.Callable;
import org.glavo.scssfx.internal.css.CssStylesheet;
import org.glavo.scssfx.internal.evaluate.VariableBinding;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Contains a fully evaluated Sass module and its public export bindings.
///
/// Export tables are structurally immutable. Variable bindings remain live so
/// assignments are visible through every namespace and forwarded view.
///
/// @param url                   the canonical module URL, or {@code null} for anonymous roots
/// @param variables             public variables
/// @param functions             public functions
/// @param mixins                public mixins
/// @param css                   the module's CSS IR
/// @param upstream              modules loaded by this module in source order
/// @param configurableVariables variables declared at the module root with
///                              {@code !default}
/// @param forwardedModules      forwarded export views used for configuration reachability
@ApiStatus.Internal
@NotNullByDefault
public record LoadedModule(
        @Nullable URI url,
        @Unmodifiable Map<String, VariableBinding> variables,
        @Unmodifiable Map<String, Callable> functions,
        @Unmodifiable Map<String, Callable> mixins,
        CssStylesheet css,
        @Unmodifiable List<LoadedModule> upstream,
        @Unmodifiable Set<String> configurableVariables,
        @Unmodifiable List<ForwardedModuleView> forwardedModules
) {
    /// Creates a loaded module while retaining variable-binding identities.
    public LoadedModule {
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(mixins, "mixins");
        Objects.requireNonNull(css, "css");
        Objects.requireNonNull(upstream, "upstream");
        Objects.requireNonNull(configurableVariables, "configurableVariables");
        Objects.requireNonNull(forwardedModules, "forwardedModules");
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        mixins = Collections.unmodifiableMap(new LinkedHashMap<>(mixins));
        upstream = List.copyOf(upstream);
        configurableVariables = Collections.unmodifiableSet(
                new LinkedHashSet<>(configurableVariables)
        );
        forwardedModules = List.copyOf(forwardedModules);
    }

    /// Returns whether any supplied name could configure this module.
    ///
    /// @param names normalized variable names at this module boundary
    /// @return {@code true} when a direct or forwarded declaration is configurable
    public boolean couldHaveBeenConfigured(@Unmodifiable Set<String> names) {
        Objects.requireNonNull(names, "names");
        for (var name : names) {
            if (configurableVariables.contains(name)) {
                return true;
            }
        }
        for (var forwarded : forwardedModules) {
            if (forwarded.couldHaveBeenConfigured(names)) {
                return true;
            }
        }
        return false;
    }
}
