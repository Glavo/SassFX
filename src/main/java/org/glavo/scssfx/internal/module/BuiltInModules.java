// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.callable.Callable;
import org.glavo.scssfx.internal.css.CssStylesheet;
import org.glavo.scssfx.internal.evaluate.VariableBinding;
import org.glavo.scssfx.internal.function.BuiltInFunctions;
import org.glavo.scssfx.internal.value.SassNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Creates the built-in Sass modules available during one compilation.
///
/// Each catalog owns fresh module snapshots so their CSS IR is never shared
/// between compilations. The exported values and callables remain immutable.
@ApiStatus.Internal
@NotNullByDefault
public final class BuiltInModules {
    /// Identifies the canonical math module URL.
    private static final URI MATH_URL = URI.create("sass:math");

    /// Identifies the canonical list module URL.
    private static final URI LIST_URL = URI.create("sass:list");

    /// Identifies the canonical map module URL.
    private static final URI MAP_URL = URI.create("sass:map");

    /// Identifies the shared zero-length source location for synthetic modules.
    private static final SourceLocation ORIGIN = new SourceLocation(0, 0, 0);

    /// Contains modules keyed by canonical built-in URL.
    private final @Unmodifiable Map<URI, LoadedModule> modules;

    /// Creates a catalog with all currently implemented built-in modules.
    public BuiltInModules() {
        var catalog = new LinkedHashMap<URI, LoadedModule>();
        catalog.put(MATH_URL, createMathModule());
        catalog.put(LIST_URL, createModule(LIST_URL, Map.of(), BuiltInFunctions.listModule()));
        catalog.put(MAP_URL, createModule(MAP_URL, Map.of(), BuiltInFunctions.mapModule()));
        modules = Collections.unmodifiableMap(catalog);
    }

    /// Finds the known built-in module identified by an unresolved URL.
    ///
    /// @param url the module URL requested by a Sass directive
    /// @return the canonical built-in module, or {@code null} when the URL is not known
    public @Nullable LoadedModule find(String url) {
        Objects.requireNonNull(url, "url");
        try {
            return modules.get(URI.create(url));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /// Creates the math module with its documented numeric constants.
    ///
    /// @return a fresh math module snapshot
    private static LoadedModule createMathModule() {
        var span = syntheticSpan(MATH_URL);
        var variables = new LinkedHashMap<String, VariableBinding>();
        variables.put("e", new VariableBinding(SassNumber.of(Math.E, null), span));
        variables.put("pi", new VariableBinding(SassNumber.of(Math.PI, null), span));
        variables.put(
                "epsilon",
                new VariableBinding(SassNumber.of(2.220446049250313E-16, null), span)
        );
        variables.put(
                "max-safe-integer",
                new VariableBinding(SassNumber.of(9_007_199_254_740_991D, null), span)
        );
        variables.put(
                "min-safe-integer",
                new VariableBinding(SassNumber.of(-9_007_199_254_740_991D, null), span)
        );
        variables.put(
                "max-number",
                new VariableBinding(SassNumber.of(Double.MAX_VALUE, null), span)
        );
        variables.put(
                "min-number",
                new VariableBinding(SassNumber.of(Double.MIN_VALUE, null), span)
        );
        return createModule(MATH_URL, variables, BuiltInFunctions.mathModule());
    }

    /// Creates an empty-CSS module from exported variables and functions.
    ///
    /// @param url the canonical module URL
    /// @param variables the public variable bindings
    /// @param functions the public callable table
    /// @return a module with no CSS, upstream modules, or configurable variables
    private static LoadedModule createModule(
            URI url,
            Map<String, VariableBinding> variables,
            Map<String, ? extends Callable> functions
    ) {
        var callableExports = new LinkedHashMap<String, Callable>();
        callableExports.putAll(functions);
        return new LoadedModule(
                url,
                variables,
                callableExports,
                Map.of(),
                new CssStylesheet(syntheticSpan(url)),
                List.of(),
                Set.of(),
                List.of()
        );
    }

    /// Creates a zero-length span associated with a built-in module URL.
    ///
    /// @param url the built-in module URL
    /// @return the synthetic source span
    private static SourceSpan syntheticSpan(URI url) {
        return new SourceSpan(url, ORIGIN, ORIGIN, "");
    }
}
