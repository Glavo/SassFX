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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A fully evaluated Sass module and its public exports.
///
/// @param url        the canonical module URL, or {@code null} for anonymous roots
/// @param variables  public variables
/// @param functions  public functions
/// @param mixins     public mixins
/// @param css        the module's CSS IR
/// @param upstream   modules loaded by this module in source order
@ApiStatus.Internal
@NotNullByDefault
public record LoadedModule(
        @Nullable URI url,
        @Unmodifiable Map<String, VariableBinding> variables,
        @Unmodifiable Map<String, Callable> functions,
        @Unmodifiable Map<String, Callable> mixins,
        CssStylesheet css,
        @Unmodifiable List<LoadedModule> upstream
) {
    /// Creates a loaded module snapshot.
    public LoadedModule {
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(functions, "functions");
        Objects.requireNonNull(mixins, "mixins");
        Objects.requireNonNull(css, "css");
        variables = Map.copyOf(variables);
        functions = Map.copyOf(functions);
        mixins = Map.copyOf(mixins);
        upstream = List.copyOf(upstream);
    }
}
