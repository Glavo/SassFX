// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.internal.callable.Callable;
import org.glavo.sassfx.internal.css.CssComment;
import org.glavo.sassfx.internal.css.CssStylesheet;
import org.glavo.sassfx.internal.evaluate.VariableBinding;
import org.glavo.sassfx.internal.extend.PendingExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
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
/// @param preModuleComments     loud comments that must precede each upstream module's CSS
/// @param extensions            `@extend` directives collected while evaluating this module
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
        @Unmodifiable List<ForwardedModuleView> forwardedModules,
        @Unmodifiable Map<LoadedModule, List<CssComment>> preModuleComments,
        @Unmodifiable List<PendingExtension> extensions
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
        Objects.requireNonNull(preModuleComments, "preModuleComments");
        Objects.requireNonNull(extensions, "extensions");
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        mixins = Collections.unmodifiableMap(new LinkedHashMap<>(mixins));
        upstream = List.copyOf(upstream);
        configurableVariables = Collections.unmodifiableSet(
                new LinkedHashSet<>(configurableVariables)
        );
        forwardedModules = List.copyOf(forwardedModules);
        var comments = new IdentityHashMap<LoadedModule, List<CssComment>>();
        for (var entry : preModuleComments.entrySet()) {
            comments.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        preModuleComments = Collections.unmodifiableMap(comments);
        extensions = List.copyOf(extensions);
    }

    /// Creates a loaded module with no pre-module comments or extensions.
    ///
    /// @param url                   the canonical module URL, or {@code null}
    /// @param variables             public variables
    /// @param functions             public functions
    /// @param mixins                public mixins
    /// @param css                   the module's CSS IR
    /// @param upstream              modules loaded by this module
    /// @param configurableVariables root {@code !default} variable names
    /// @param forwardedModules      forwarded export views
    public LoadedModule(
            @Nullable URI url,
            Map<String, VariableBinding> variables,
            Map<String, Callable> functions,
            Map<String, Callable> mixins,
            CssStylesheet css,
            List<LoadedModule> upstream,
            Set<String> configurableVariables,
            List<ForwardedModuleView> forwardedModules
    ) {
        this(
                url,
                variables,
                functions,
                mixins,
                css,
                upstream,
                configurableVariables,
                forwardedModules,
                Map.of(),
                List.of()
        );
    }

    /// Returns whether this module or any upstream module emits CSS.
    ///
    /// @return whether combining this module can produce CSS text
    public boolean transitivelyContainsCss() {
        if (!css.children().isEmpty()) {
            return true;
        }
        for (var module : upstream) {
            if (module.transitivelyContainsCss()) {
                return true;
            }
        }
        return false;
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
