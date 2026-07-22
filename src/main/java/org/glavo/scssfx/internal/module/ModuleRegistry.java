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

    /// Contains fully loaded modules keyed by canonical URL.
    private final LinkedHashMap<URI, LoadedModule> loaded = new LinkedHashMap<>();

    /// Contains modules currently being loaded, for cycle detection.
    private final LinkedHashMap<URI, SourceSpan> active = new LinkedHashMap<>();

    /// Contains every canonical URL loaded during compilation.
    private final LinkedHashSet<URI> loadedUrls = new LinkedHashSet<>();

    /// Creates a registry.
    ///
    /// @param loadPaths directories searched after the containing file
    public ModuleRegistry(List<Path> loadPaths) {
        this.importer = new FilesystemImporter(loadPaths);
    }

    /// Loads a module URL, reusing a previously loaded instance when present.
    ///
    /// @param url       the unresolved module URL
    /// @param baseUrl   the containing stylesheet URL, or {@code null}
    /// @param loadSpan  the `@use` span
    /// @param evaluator the evaluator used to execute newly loaded modules
    /// @return the loaded module
    /// @throws EvaluationException if loading or evaluation fails
    public LoadedModule load(
            String url,
            @Nullable URI baseUrl,
            SourceSpan loadSpan,
            SassEvaluator evaluator
    ) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(loadSpan, "loadSpan");
        Objects.requireNonNull(evaluator, "evaluator");
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
            var module = evaluator.executeAsModule(stylesheet, canonical);
            loaded.put(canonical, module);
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
        return Set.copyOf(loadedUrls);
    }
}
