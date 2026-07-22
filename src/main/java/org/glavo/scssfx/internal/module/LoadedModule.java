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

/// A fully evaluated Sass module and its public exports.
///
/// @param url                   the canonical module URL, or {@code null} for anonymous roots
/// @param variables             public variables
/// @param functions             public functions
/// @param mixins                public mixins
/// @param css                   the module's CSS IR
/// @param upstream              modules loaded by this module in source order
/// @param configurableVariables variables declared at the module root with
///                              {@code !default}
@ApiStatus.Internal
@NotNullByDefault
public record LoadedModule(
        @Nullable URI url,
        @Unmodifiable Map<String, VariableBinding> variables,
        @Unmodifiable Map<String, Callable> functions,
        @Unmodifiable Map<String, Callable> mixins,
        CssStylesheet css,
        @Unmodifiable List<LoadedModule> upstream,
        @Unmodifiable Set<String> configurableVariables
) {
    /// Creates a loaded module snapshot.
    public LoadedModule {
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(mixins, "mixins");
        Objects.requireNonNull(css, "css");
        Objects.requireNonNull(upstream, "upstream");
        Objects.requireNonNull(configurableVariables, "configurableVariables");
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        mixins = Collections.unmodifiableMap(new LinkedHashMap<>(mixins));
        upstream = List.copyOf(upstream);
        configurableVariables = Collections.unmodifiableSet(
                new LinkedHashSet<>(configurableVariables)
        );
    }

    /// Returns whether any supplied name could configure this module.
    ///
    /// @param names normalized variable names
    /// @return {@code true} when at least one name was declared with
    /// {@code !default} at the module root
    public boolean couldHaveBeenConfigured(Set<String> names) {
        Objects.requireNonNull(names, "names");
        for (var name : names) {
            if (configurableVariables.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
