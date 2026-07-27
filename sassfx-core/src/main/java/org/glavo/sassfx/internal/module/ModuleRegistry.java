// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.internal.ast.Stylesheet;
import org.glavo.sassfx.internal.evaluate.EvaluationException;
import org.glavo.sassfx.internal.evaluate.SassEvaluator;
import org.glavo.sassfx.internal.parse.ParseException;
import org.glavo.sassfx.internal.parse.StylesheetParser;
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
import java.util.Map;
import java.util.Set;

/// Loads modules and legacy imports for one compilation.
@ApiStatus.Internal
@NotNullByDefault
public final class ModuleRegistry {
    /// Resolves stylesheet files.
    private final SassImportResolver importer;

    /// Resolves known Sass built-in module URLs.
    private final BuiltInModules builtInModules;

    /// Contains fully loaded modules keyed by canonical URL.
    private final LinkedHashMap<URI, LoadedModule> loaded = new LinkedHashMap<>();

    /// Contains the configuration origin used for each fully loaded module.
    private final LinkedHashMap<URI, ModuleConfiguration> loadedConfigurations =
            new LinkedHashMap<>();

    /// Contains stylesheets currently being loaded, for cycle detection.
    private final LinkedHashMap<URI, SourceSpan> active = new LinkedHashMap<>();

    /// Contains every canonical URL loaded during compilation.
    private final LinkedHashSet<URI> loadedUrls = new LinkedHashSet<>();

    /// Contains parsed legacy-import stylesheets keyed by canonical URL.
    private final LinkedHashMap<URI, Stylesheet> parsedImports = new LinkedHashMap<>();

    /// Creates a registry.
    ///
    /// @param loadPaths directories searched after custom importers
    /// @param importers custom importers in precedence order
    public ModuleRegistry(List<Path> loadPaths, List<SassImporter> importers) {
        this(loadPaths, importers, null);
    }

    /// Creates a registry that records incremental resolution metadata.
    ///
    /// @param loadPaths directories searched after custom importers
    /// @param importers custom importers in precedence order
    /// @param resolutionTracker the tracker receiving resolution metadata, or
    /// `null` when tracking is disabled
    public ModuleRegistry(
            List<Path> loadPaths,
            List<SassImporter> importers,
            @Nullable SassResolutionTracker resolutionTracker
    ) {
        this.importer = new SassImportResolver(
                importers,
                loadPaths,
                resolutionTracker
        );
        this.builtInModules = new BuiltInModules();
    }

    /// Returns the number of user modules fully loaded so far.
    ///
    /// Built-in modules are not counted. Callers compare this value before and
    /// after [#load] to detect a first-time load.
    ///
    /// @return the loaded user-module count
    public int loadedModuleCount() {
        return loaded.size();
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
        return load(url, baseUrl, loadSpan, evaluator, configuration, hasOwnConfiguration, false);
    }

    /// Loads a module URL with optional load-css diagnostic wording.
    ///
    /// @param url                 the unresolved module URL
    /// @param baseUrl             the containing stylesheet URL, or {@code null}
    /// @param loadSpan            the module directive span
    /// @param evaluator           the evaluator used to execute newly loaded modules
    /// @param configuration       values available to root {@code !default} declarations
    /// @param hasOwnConfiguration whether the loading directive itself has a {@code with} clause
    /// @param fromLoadCss         whether the load is from {@code meta.load-css()}
    /// @return the loaded module
    /// @throws EvaluationException if loading or evaluation fails
    public LoadedModule load(
            String url,
            @Nullable URI baseUrl,
            SourceSpan loadSpan,
            SassEvaluator evaluator,
            ModuleConfiguration configuration,
            boolean hasOwnConfiguration,
            boolean fromLoadCss
    ) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(loadSpan, "loadSpan");
        Objects.requireNonNull(evaluator, "evaluator");
        Objects.requireNonNull(configuration, "configuration");

        @Nullable LoadedModule builtIn = builtInModules.find(url);
        if (builtIn != null) {
            if (hasOwnConfiguration) {
                // load-css includes the module URL; @use/@forward use the generic form.
                if (fromLoadCss && url.startsWith("sass:")) {
                    throw new EvaluationException(
                            "Built-in module " + url + " can't be configured.",
                            loadSpan
                    );
                }
                throw new EvaluationException(
                        "Built-in modules can't be configured.",
                        loadSpan
                );
            }
            return builtIn;
        }

        ImportResult imported;
        try {
            imported = importer.canonicalizeAndLoad(
                    url,
                    baseUrl,
                    (deprecation, dependency) ->
                            evaluator.reportImportDeprecation(
                                    deprecation,
                                    loadSpan,
                                    dependency
                            )
            );
        } catch (EvaluationException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
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
                // load-css names the stylesheet (names aren't obvious from the
                // include span). @use/@forward and nested loads use the generic
                // form, matching dart-sass namesInErrors.
                throw new EvaluationException(
                        fromLoadCss
                                ? displayUrl(canonical)
                                + " was already loaded, so it can't be configured using "
                                + "\"with\"."
                                : "This module was already loaded, so it can't be configured "
                                + "using \"with\".",
                        loadSpan
                );
            }
            return existing;
        }
        if (active.containsKey(canonical)) {
            // load-css names the stylesheet; @use/@forward use the generic form.
            throw new EvaluationException(
                    fromLoadCss
                            ? "Module loop: " + displayUrl(canonical)
                            + " is already being loaded."
                            : "Module loop: this module is already being loaded.",
                    loadSpan
            );
        }

        active.put(canonical, loadSpan);
        try {
            Stylesheet stylesheet = StylesheetParser.parse(imported.source(), imported.syntax());
            var module = evaluator.executeAsModule(
                    stylesheet,
                    canonical,
                    configuration,
                    imported.dependency()
            );
            loaded.put(canonical, module);
            loadedConfigurations.put(canonical, configuration);
            return module;
        } finally {
            active.remove(canonical);
        }
    }

    /// Loads and executes a legacy Sass import in the caller's environment.
    ///
    /// Parsed syntax trees are cached, but evaluation is repeated for every
    /// import occurrence. The active-load table is shared with module loading
    /// so mixed `@use` and `@import` cycles are rejected.
    ///
    /// @param url       the unresolved import URL
    /// @param baseUrl   the containing stylesheet URL, or {@code null}
    /// @param loadSpan  the dynamic import URL span
    /// @param evaluator the evaluator receiving imported statements
    /// @throws EvaluationException if resolution, parsing, or evaluation fails
    public void loadImport(
            String url,
            @Nullable URI baseUrl,
            SourceSpan loadSpan,
            SassEvaluator evaluator
    ) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(loadSpan, "loadSpan");
        Objects.requireNonNull(evaluator, "evaluator");

        loadImport(url, baseUrl, loadSpan, evaluator, true);
    }

    /// Loads a stylesheet as a legacy import without preferring import-only files.
    ///
    /// Used when {@code @forward} appears inside an {@code @import}-ed file so
    /// {@code other.import.scss} can forward {@code other.scss} without recursion.
    ///
    /// @param url       the unresolved import URL
    /// @param baseUrl   the containing stylesheet URL, or {@code null}
    /// @param loadSpan  the dynamic import or forward span
    /// @param evaluator the evaluator receiving imported statements
    /// @throws EvaluationException if resolution, parsing, or evaluation fails
    public void loadImportAsModuleCandidate(
            String url,
            @Nullable URI baseUrl,
            SourceSpan loadSpan,
            SassEvaluator evaluator
    ) {
        loadImport(url, baseUrl, loadSpan, evaluator, false);
    }

    /// Loads and executes a legacy Sass import.
    ///
    /// @param url       the unresolved import URL
    /// @param baseUrl   the containing stylesheet URL, or {@code null}
    /// @param loadSpan  the load span
    /// @param evaluator the evaluator receiving imported statements
    /// @param forImport whether import-only candidates ({@code *.import.scss}) win
    /// @throws EvaluationException if resolution, parsing, or evaluation fails
    private void loadImport(
            String url,
            @Nullable URI baseUrl,
            SourceSpan loadSpan,
            SassEvaluator evaluator,
            boolean forImport
    ) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(loadSpan, "loadSpan");
        Objects.requireNonNull(evaluator, "evaluator");

        ImportResult imported;
        try {
            imported = forImport
                    ? importer.canonicalizeAndLoadImport(
                            url,
                            baseUrl,
                            (deprecation, dependency) ->
                                    evaluator.reportImportDeprecation(
                                            deprecation,
                                            loadSpan,
                                            dependency
                                    )
                    )
                    : importer.canonicalizeAndLoad(
                            url,
                            baseUrl,
                            (deprecation, dependency) ->
                                    evaluator.reportImportDeprecation(
                                            deprecation,
                                            loadSpan,
                                            dependency
                                    )
                    );
        } catch (EvaluationException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new EvaluationException(
                    Objects.requireNonNullElse(
                            failure.getMessage(),
                            "Can't find stylesheet to import."
                    ),
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
        if (active.containsKey(canonical)) {
            throw new EvaluationException(
                    "This file is already being loaded.",
                    loadSpan
            );
        }

        active.put(canonical, loadSpan);
        try {
            @Nullable Stylesheet cached = parsedImports.get(canonical);
            Stylesheet stylesheet;
            try {
                stylesheet = cached == null
                        ? StylesheetParser.parse(imported.source(), imported.syntax())
                        : cached;
            } catch (ParseException failure) {
                throw new EvaluationException(
                        Objects.requireNonNull(failure.getMessage(), "parse failure message"),
                        failure.span(),
                        List.of(),
                        failure
                );
            }
            if (cached == null) {
                parsedImports.put(canonical, stylesheet);
            }
            evaluator.executeLegacyImport(
                    stylesheet,
                    canonical,
                    imported.dependency()
            );
        } finally {
            active.remove(canonical);
        }
    }

    /// Records a root stylesheet URL as loaded and active.
    ///
    /// @param url the root canonical URL, or {@code null}
    /// @param span the root stylesheet span
    public void recordRoot(@Nullable URI url, SourceSpan span) {
        Objects.requireNonNull(span, "span");
        if (url != null) {
            loadedUrls.add(url);
            active.put(url, span);
        }
    }

    /// Returns every canonical URL loaded so far.
    ///
    /// @return the loaded URLs in first-seen order
    public @Unmodifiable Set<URI> loadedUrls() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(loadedUrls));
    }

    /// Returns alternate source-map URLs reported by custom importers.
    ///
    /// @return an immutable map keyed by canonical stylesheet URL
    public @Unmodifiable Map<URI, URI> sourceMapUrls() {
        return importer.sourceMapUrls();
    }

    /// Returns source text for every loaded canonical stylesheet.
    ///
    /// @return an immutable map keyed by canonical stylesheet URL
    public @Unmodifiable Map<URI, String> sourceContents() {
        return importer.sourceContents();
    }

    /// Returns a short display name for a canonical stylesheet URL.
    ///
    /// Module-loop diagnostics use the last path segment when present so
    /// messages match dart-sass ({@code input.scss}) rather than the full URI.
    ///
    /// @param url the canonical URL
    /// @return a human-readable stylesheet name
    private static String displayUrl(URI url) {
        Objects.requireNonNull(url, "url");
        @Nullable String path = url.getPath();
        if (path != null && !path.isEmpty()) {
            var slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            var name = slash >= 0 ? path.substring(slash + 1) : path;
            if (!name.isEmpty()) {
                return name;
            }
        }
        return url.toString();
    }
}
