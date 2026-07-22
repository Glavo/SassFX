// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.evaluate.EvaluationException;
import org.glavo.scssfx.internal.evaluate.SassEvaluator;
import org.glavo.scssfx.internal.parse.StylesheetParser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Loads and caches modules once per canonical URL for one compilation.
@ApiStatus.Internal
@NotNullByDefault
public final class ModuleRegistry {
    /// Resolves stylesheet files.
    private final FilesystemImporter importer;

    /// Resolves known Sass built-in module URLs.
    private final BuiltInModules builtInModules;

    /// Contains fully loaded modules keyed by canonical URL.
    private final LinkedHashMap<URI, LoadedModule> loaded = new LinkedHashMap<>();

    /// Contains the configuration origin used for each fully loaded module.
    private final LinkedHashMap<URI, ModuleConfiguration> loadedConfigurations =
            new LinkedHashMap<>();

    /// Contains modules currently being loaded, for cycle detection.
    private final LinkedHashMap<URI, SourceSpan> active = new LinkedHashMap<>();

    /// Contains every canonical URL loaded during compilation.
    private final LinkedHashSet<URI> loadedUrls = new LinkedHashSet<>();

    /// Creates a registry.
    ///
    /// @param loadPaths directories searched after the containing file
    public ModuleRegistry(List<Path> loadPaths) {
        this.importer = new FilesystemImporter(loadPaths);
        this.builtInModules = new BuiltInModules();
    }

    /// Loads a module URL, reusing a previously loaded instance when present.
    ///
    /// @param url                 the unresolved module URL
    /// @param baseUrl             the containing stylesheet URL, or {@code null}
    /// @param loadSpan            the module directive span
    /// @param evaluator           the evaluator used to execute newly loaded modules
    /// @param configuration       values available to root {@code !default} declarations
    /// @param hasOwnConfiguration whether the loading directive itself has a {@code with} clause
    /// @return the loaded module
    /// @throws EvaluationException if loading or evaluation fails
    public LoadedModule load(
            String url,
            @Nullable URI baseUrl,
            SourceSpan loadSpan,
            SassEvaluator evaluator,
            ModuleConfiguration configuration,
            boolean hasOwnConfiguration
    ) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(loadSpan, "loadSpan");
        Objects.requireNonNull(evaluator, "evaluator");
        Objects.requireNonNull(configuration, "configuration");

        @Nullable LoadedModule builtIn = builtInModules.find(url);
        if (builtIn != null) {
            if (hasOwnConfiguration) {
                throw new EvaluationException(
                        "Built-in modules can't be configured.",
                        loadSpan
                );
            }
            return builtIn;
        }

        ImportResult imported;
        try {
            imported = importer.canonicalizeAndLoad(url, baseUrl);
        } catch (IOException | IllegalStateException failure) {
            throw new EvaluationException(
                    Objects.requireNonNullElse(failure.getMessage(), "Can't find stylesheet to import."),
                    loadSpan,
                    List.of(),
                    failure
            );
        }
        if (imported == null) {
            throw new EvaluationException("Can't find stylesheet to import.", loadSpan);
        }

        var canonical = imported.canonicalUrl();
        loadedUrls.add(canonical);
        @Nullable LoadedModule existing = loaded.get(canonical);
        if (existing != null) {
            var originalConfiguration = Objects.requireNonNull(
                    loadedConfigurations.get(canonical),
                    "loaded module configuration"
            );
            if (configuration.isExplicit()
                    && !originalConfiguration.sameOriginal(configuration)
                    && existing.couldHaveBeenConfigured(configuration.names())) {
                throw new EvaluationException(
                        "This module was already loaded, so it can't be "
                                + "configured using \"with\".",
                        loadSpan
                );
            }
            return existing;
        }
        if (active.containsKey(canonical)) {
            throw new EvaluationException(
                    "Module loop: this module is already being loaded.",
                    loadSpan
            );
        }

        active.put(canonical, loadSpan);
        try {
            Stylesheet stylesheet = StylesheetParser.parse(imported.source(), imported.syntax());
            var module = evaluator.executeAsModule(
                    stylesheet,
                    canonical,
                    configuration
            );
            loaded.put(canonical, module);
            loadedConfigurations.put(canonical, configuration);
            return module;
        } finally {
            active.remove(canonical);
        }
    }

    /// Records a root stylesheet URL as loaded.
    ///
    /// @param url the root canonical URL, or {@code null}
    public void recordRoot(@Nullable URI url) {
        if (url != null) {
            loadedUrls.add(url);
        }
    }

    /// Returns every canonical URL loaded so far.
    ///
    /// @return the loaded URLs in first-seen order
    public @Unmodifiable Set<URI> loadedUrls() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(loadedUrls));
    }
}
