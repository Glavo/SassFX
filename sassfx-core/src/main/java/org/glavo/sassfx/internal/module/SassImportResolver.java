// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.SassCanonicalizeContext;
import org.glavo.sassfx.SassDeprecation;
import org.glavo.sassfx.SassFileImporter;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassImporterResult;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Resolves Sass loads through custom importers and filesystem load paths.
@ApiStatus.Internal
@NotNullByDefault
public final class SassImportResolver {
    /// Ignores import deprecations for direct resolver tests and utilities.
    private static final ImportDeprecationHandler NO_DEPRECATIONS =
            (deprecation, dependency) -> {
            };

    /// Contains custom importers in user-defined precedence order.
    private final @Unmodifiable List<SassImporter> importers;

    /// Resolves filesystem loads after custom importers decline a request.
    private final FilesystemImporter filesystemImporter;

    /// Receives incremental filesystem-resolution metadata.
    private final @Nullable SassResolutionTracker resolutionTracker;

    /// Associates loaded canonical URLs with the importer that owns them.
    private final LinkedHashMap<URI, SassImporter> owners = new LinkedHashMap<>();

    /// Caches loaded importer results by canonical URL.
    private final LinkedHashMap<URI, ImportResult> loads = new LinkedHashMap<>();

    /// Caches context-independent canonicalization outcomes per importer
    /// identity.
    private final IdentityHashMap<
            SassImporter,
            LinkedHashMap<CanonicalizeKey, CachedCanonicalization>
            > canonicalizations = new IdentityHashMap<>();

    /// Associates canonical URLs with alternate source-map URLs.
    private final LinkedHashMap<URI, URI> sourceMapUrls = new LinkedHashMap<>();

    /// Creates a resolver for one compilation.
    ///
    /// @param importers custom importers in precedence order
    /// @param loadPaths filesystem load paths searched after custom importers
    public SassImportResolver(List<SassImporter> importers, List<Path> loadPaths) {
        this(importers, loadPaths, null);
    }

    /// Creates a resolver that records incremental resolution metadata.
    ///
    /// @param importers custom importers in precedence order
    /// @param loadPaths filesystem load paths searched after custom importers
    /// @param resolutionTracker the tracker receiving resolution metadata, or
    /// `null` when tracking is disabled
    public SassImportResolver(
            List<SassImporter> importers,
            List<Path> loadPaths,
            @Nullable SassResolutionTracker resolutionTracker
    ) {
        Objects.requireNonNull(importers, "importers");
        this.importers = List.copyOf(importers);
        this.filesystemImporter = new FilesystemImporter(
                loadPaths,
                resolutionTracker
        );
        this.resolutionTracker = resolutionTracker;
    }

    /// Resolves and loads a module-style request.
    ///
    /// @param url the unresolved stylesheet URL
    /// @param baseUrl the containing canonical URL, or {@code null}
    /// @return the loaded stylesheet, or {@code null} when unresolved
    /// @throws IOException if an importer or filesystem read fails
    public @Nullable ImportResult canonicalizeAndLoad(
            String url,
            @Nullable URI baseUrl
    ) throws IOException {
        return canonicalizeAndLoad(
                url,
                baseUrl,
                false,
                NO_DEPRECATIONS
        );
    }

    /// Resolves and loads a module-style request while reporting deprecations.
    ///
    /// @param url the unresolved stylesheet URL
    /// @param baseUrl the containing canonical URL, or {@code null}
    /// @param deprecationHandler receives resolution deprecations
    /// @return the loaded stylesheet, or {@code null} when unresolved
    /// @throws IOException if an importer or filesystem read fails
    public @Nullable ImportResult canonicalizeAndLoad(
            String url,
            @Nullable URI baseUrl,
            ImportDeprecationHandler deprecationHandler
    ) throws IOException {
        return canonicalizeAndLoad(
                url,
                baseUrl,
                false,
                deprecationHandler
        );
    }

    /// Resolves and loads a legacy-import request.
    ///
    /// @param url the unresolved stylesheet URL
    /// @param baseUrl the containing canonical URL, or {@code null}
    /// @return the loaded stylesheet, or {@code null} when unresolved
    /// @throws IOException if an importer or filesystem read fails
    public @Nullable ImportResult canonicalizeAndLoadImport(
            String url,
            @Nullable URI baseUrl
    ) throws IOException {
        return canonicalizeAndLoad(
                url,
                baseUrl,
                true,
                NO_DEPRECATIONS
        );
    }

    /// Resolves and loads a legacy import while reporting deprecations.
    ///
    /// @param url the unresolved stylesheet URL
    /// @param baseUrl the containing canonical URL, or {@code null}
    /// @param deprecationHandler receives resolution deprecations
    /// @return the loaded stylesheet, or {@code null} when unresolved
    /// @throws IOException if an importer or filesystem read fails
    public @Nullable ImportResult canonicalizeAndLoadImport(
            String url,
            @Nullable URI baseUrl,
            ImportDeprecationHandler deprecationHandler
    ) throws IOException {
        return canonicalizeAndLoad(
                url,
                baseUrl,
                true,
                deprecationHandler
        );
    }

    /// Returns source-map URL substitutions reported by custom importers.
    ///
    /// @return an immutable snapshot keyed by canonical URL
    public @Unmodifiable Map<URI, URI> sourceMapUrls() {
        return Map.copyOf(sourceMapUrls);
    }

    /// Returns source text for every canonical URL loaded by this resolver.
    ///
    /// @return an immutable map keyed by canonical stylesheet URL
    public @Unmodifiable Map<URI, String> sourceContents() {
        var result = new LinkedHashMap<URI, String>(loads.size());
        for (var entry : loads.entrySet()) {
            result.put(
                    entry.getKey(),
                    entry.getValue().source().content()
            );
        }
        return Map.copyOf(result);
    }

    /// Resolves a request using importer provenance, global importers, and the
    /// filesystem in that order.
    private @Nullable ImportResult canonicalizeAndLoad(
            String url,
            @Nullable URI baseUrl,
            boolean fromImport,
            ImportDeprecationHandler deprecationHandler
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(deprecationHandler, "deprecationHandler");
        var requestedUrl = parseUrl(url);

        if (baseUrl != null && !requestedUrl.isAbsolute()) {
            @Nullable SassImporter owner = owners.get(baseUrl);
            if (owner != null) {
                var relativeResult = tryImporter(
                        owner,
                        resolveAgainstCanonicalUrl(baseUrl, requestedUrl),
                        new SassCanonicalizeContext(null, fromImport),
                        dependencyOf(baseUrl),
                        deprecationHandler
                );
                if (relativeResult != null) {
                    return relativeResult;
                }
            } else if ("file".equalsIgnoreCase(baseUrl.getScheme())) {
                @Nullable ImportResult relative =
                        filesystemImporter.canonicalizeAndLoadRelative(
                                url,
                                baseUrl,
                                fromImport
                        );
                if (relative != null) {
                    return cacheFilesystem(relative, dependencyOf(baseUrl));
                }
            }
        }

        for (var importer : importers) {
            var passContainingUrl = baseUrl != null
                    && (!requestedUrl.isAbsolute()
                    || importer.isNonCanonicalScheme(
                            Objects.requireNonNullElse(
                                    requestedUrl.getScheme(),
                                    ""
                            )
                    )
                    || importer instanceof SassFileImporter
                    && !"file".equalsIgnoreCase(requestedUrl.getScheme()));
            var context = new SassCanonicalizeContext(
                    passContainingUrl ? baseUrl : null,
                    fromImport
            );
            var result = tryImporter(
                    importer,
                    requestedUrl,
                    context,
                    true,
                    deprecationHandler
            );
            if (result != null) {
                return result;
            }
        }

        if ("file".equalsIgnoreCase(requestedUrl.getScheme())) {
            @Nullable ImportResult direct =
                    filesystemImporter.canonicalizeAndLoadFileUrl(
                            requestedUrl,
                            fromImport
                    );
            if (direct != null) {
                return cacheFilesystem(
                        direct,
                        baseUrl != null && dependencyOf(baseUrl)
                );
            }
        }

        @Nullable ImportResult loaded =
                filesystemImporter.canonicalizeAndLoadFromLoadPaths(
                        url,
                        fromImport
                );
        if (loaded != null) {
            return cacheFilesystem(loaded, true);
        }

        @Nullable ImportResult currentWorkingDirectory =
                filesystemImporter.canonicalizeAndLoadFromCurrentWorkingDirectory(
                        url,
                        fromImport
                );
        if (currentWorkingDirectory == null) {
            return null;
        }
        var deprecation = new ImportDeprecation(
                SassDeprecation.FS_IMPORTER_CWD,
                "Using the current working directory as an implicit load path is "
                        + "deprecated. Either add it as an explicit load path or "
                        + "importer, or load this stylesheet from a different URL."
        );
        deprecationHandler.report(
                deprecation,
                baseUrl != null && dependencyOf(baseUrl)
        );
        return cacheFilesystem(
                currentWorkingDirectory,
                baseUrl != null && dependencyOf(baseUrl)
        );
    }

    /// Attempts one importer and loads a claimed canonical URL.
    private @Nullable ImportResult tryImporter(
            SassImporter importer,
            URI requestedUrl,
            SassCanonicalizeContext context,
            boolean dependency,
            ImportDeprecationHandler deprecationHandler
    ) throws IOException {
        var importerCache = canonicalizations.computeIfAbsent(
                importer,
                ignored -> new LinkedHashMap<>()
        );
        var key = new CanonicalizeKey(
                requestedUrl,
                context.fromImport()
        );
        @Nullable CachedCanonicalization cached = importerCache.get(key);
        if (cached != null) {
            if (cached.result() == null) {
                return null;
            }
            reportCanonicalizationDeprecation(
                    cached.result(),
                    dependency,
                    deprecationHandler
            );
            return loadTrackedCanonicalized(
                    cached.result(),
                    dependency
            );
        }

        @Nullable CanonicalizedImport canonicalized =
                importer instanceof SassFileImporter fileImporter
                        ? canonicalizeFileImporter(
                                fileImporter,
                                requestedUrl,
                                context
                        )
                        : canonicalizeContentsImporter(
                                importer,
                                requestedUrl,
                                context
                        );
        var cacheable = context.containingUrlWithoutMarking() == null
                || !context.wasContainingUrlAccessed();
        if (cacheable) {
            importerCache.put(
                    key,
                    new CachedCanonicalization(canonicalized)
            );
        }
        if (canonicalized == null) {
            return null;
        }
        reportCanonicalizationDeprecation(
                canonicalized,
                dependency,
                deprecationHandler
        );
        return loadTrackedCanonicalized(canonicalized, dependency);
    }

    /// Reports a relative canonical URL before its importer is loaded.
    ///
    /// @param canonicalized the importer canonicalization result
    /// @param dependency whether the importer is a dependency
    /// @param deprecationHandler receives the deprecation
    private static void reportCanonicalizationDeprecation(
            CanonicalizedImport canonicalized,
            boolean dependency,
            ImportDeprecationHandler deprecationHandler
    ) {
        if (canonicalized.deprecation() != null) {
            deprecationHandler.report(
                    canonicalized.deprecation(),
                    dependency
            );
        }
    }

    /// Loads one custom-importer result and marks filesystem tracking
    /// incomplete.
    ///
    /// @param canonicalized the custom importer result
    /// @param dependency whether the result is a dependency
    /// @return the loaded stylesheet
    /// @throws IOException if loading fails
    private ImportResult loadTrackedCanonicalized(
            CanonicalizedImport canonicalized,
            boolean dependency
    ) throws IOException {
        if (resolutionTracker != null) {
            resolutionTracker.markIncomplete();
        }
        return loadCanonicalized(canonicalized, dependency);
    }

    /// Canonicalizes a request with a contents importer.
    ///
    /// @param importer the importer receiving the request
    /// @param requestedUrl the unresolved URL
    /// @param context the request context
    /// @return the canonicalized import, or {@code null}
    /// @throws IOException if the importer fails
    private static @Nullable CanonicalizedImport canonicalizeContentsImporter(
            SassImporter importer,
            URI requestedUrl,
            SassCanonicalizeContext context
    ) throws IOException {
        @Nullable URI canonicalUrl = importer.canonicalize(
                requestedUrl,
                context
        );
        if (canonicalUrl == null) {
            return null;
        }
        @Nullable ImportDeprecation deprecation = null;
        if (!canonicalUrl.isAbsolute()) {
            deprecation = new ImportDeprecation(
                    SassDeprecation.RELATIVE_CANONICAL,
                    "Importer " + importer + " canonicalized " + requestedUrl
                            + " to " + canonicalUrl + ".\n"
                            + "Relative canonical URLs are deprecated and will "
                            + "eventually be disallowed."
            );
        } else if (importer.isNonCanonicalScheme(canonicalUrl.getScheme())) {
            throw new IllegalStateException(
                    "Importer " + importer + " canonicalized " + requestedUrl
                            + " to " + canonicalUrl
                            + ", which uses a scheme declared as non-canonical."
            );
        }
        return new CanonicalizedImport(importer, canonicalUrl, deprecation);
    }

    /// Loads a canonicalized importer result, reusing canonical contents.
    ///
    /// @param canonicalized the importer and canonical URL
    /// @param dependency whether the caller is a dependency load
    /// @return the loaded stylesheet
    /// @throws IOException if loading fails
    private ImportResult loadCanonicalized(
            CanonicalizedImport canonicalized,
            boolean dependency
    ) throws IOException {
        var canonicalUrl = canonicalized.canonicalUrl();
        @Nullable ImportResult cached = loads.get(canonicalUrl);
        if (cached != null) {
            return cached.withDependency(dependency);
        }

        var importer = canonicalized.importer();
        if (importer instanceof SassFileImporter) {
            @Nullable ImportResult loaded =
                    FilesystemImporter.loadCanonicalFileUrl(canonicalUrl);
            if (loaded == null) {
                throw new IllegalStateException(
                        "Can't find stylesheet to import."
                );
            }
            loaded = loaded.withDependency(dependency);
            loads.put(canonicalUrl, loaded);
            owners.put(canonicalUrl, importer);
            return loaded;
        }

        @Nullable SassImporterResult importerResult =
                importer.load(canonicalUrl);
        if (importerResult == null) {
            throw new IllegalStateException("Can't find stylesheet to import.");
        }

        @Nullable URI sourceMapUrl = importerResult.sourceMapUrl();
        var sourceUrl = sourceMapUrl == null
                ? dataUrl(importerResult.contents())
                : sourceMapUrl;
        var result = new ImportResult(
                new SourceFile(importerResult.contents(), canonicalUrl),
                importerResult.syntax(),
                canonicalUrl,
                dependency
        );
        loads.put(canonicalUrl, result);
        owners.put(canonicalUrl, importer);
        sourceMapUrls.put(canonicalUrl, sourceUrl);
        return result;
    }

    /// Canonicalizes a request with a compiler-managed file importer.
    ///
    /// @param importer the file importer receiving the request
    /// @param requestedUrl the unresolved URL
    /// @param context the request context
    /// @return the canonicalized import, or {@code null}
    /// @throws IOException if the importer or filesystem canonicalization fails
    private static @Nullable CanonicalizedImport canonicalizeFileImporter(
            SassFileImporter importer,
            URI requestedUrl,
            SassCanonicalizeContext context
    ) throws IOException {
        @Nullable URI fileUrl;
        if ("file".equalsIgnoreCase(requestedUrl.getScheme())) {
            fileUrl = requestedUrl;
        } else {
            fileUrl = importer.findFileUrl(requestedUrl, context);
            if (fileUrl == null) {
                return null;
            }
            if (!fileUrl.isAbsolute()) {
                throw new IllegalStateException(
                        "The file importer must return an absolute URL, was \""
                                + fileUrl + "\"."
                );
            }
            if (!"file".equalsIgnoreCase(fileUrl.getScheme())) {
                throw new IllegalStateException(
                        "The file importer must return a file: URL, was \""
                                + fileUrl + "\"."
                );
            }
            if (fileUrl.getQuery() != null || fileUrl.getFragment() != null) {
                throw new IllegalStateException(
                        "The file importer must return a file: URL without a "
                                + "query or fragment, was \"" + fileUrl + "\"."
                );
            }
        }

        @Nullable URI canonicalUrl = FilesystemImporter.canonicalizeFileUrl(
                fileUrl,
                context.fromImport()
        );
        return canonicalUrl == null
                ? null
                : new CanonicalizedImport(importer, canonicalUrl, null);
    }

    /// Caches one stylesheet loaded by the default filesystem importer.
    private ImportResult cacheFilesystem(
            ImportResult result,
            boolean dependency
    ) {
        @Nullable ImportResult cached = loads.get(result.canonicalUrl());
        if (cached != null) {
            return cached.withDependency(dependency);
        }
        result = result.withDependency(dependency);
        loads.put(result.canonicalUrl(), result);
        return result;
    }

    /// Returns dependency provenance previously assigned to a containing URL.
    private boolean dependencyOf(URI canonicalUrl) {
        @Nullable ImportResult result = loads.get(canonicalUrl);
        return result != null && result.dependency();
    }

    /// Creates a browser-accessible UTF-8 data URL for importer contents.
    private static URI dataUrl(String contents) {
        var encoded = Base64.getEncoder().encodeToString(
                contents.getBytes(StandardCharsets.UTF_8)
        );
        return URI.create("data:text/plain;charset=UTF-8;base64," + encoded);
    }

    /// Parses an importer URL while preserving non-URI path text as an encoded
    /// relative URI.
    private static URI parseUrl(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ignored) {
            try {
                return new URI(null, null, value, null);
            } catch (URISyntaxException failure) {
                throw new IllegalArgumentException("Invalid stylesheet URL: " + value, failure);
            }
        }
    }

    /// Resolves a relative request against a canonical importer URL.
    ///
    /// Java treats URLs such as {@code custom:foo/bar} as opaque and therefore
    /// leaves relative operands unresolved. Sass canonical URLs use URI path
    /// semantics for these schemes, so this method temporarily supplies a
    /// hierarchical root and then restores the original scheme.
    ///
    /// @param baseUrl the absolute canonical URL
    /// @param requestedUrl the relative request
    /// @return the resolved absolute URL
    private static URI resolveAgainstCanonicalUrl(
            URI baseUrl,
            URI requestedUrl
    ) {
        if (!baseUrl.isOpaque()) {
            return baseUrl.resolve(requestedUrl);
        }
        if (requestedUrl.isAbsolute()) {
            return requestedUrl;
        }
        if (requestedUrl.getRawPath() != null
                && requestedUrl.getRawPath().startsWith("/")) {
            return URI.create(baseUrl.getScheme() + ":" + requestedUrl);
        }

        var hierarchicalBase = URI.create(
                "sassfx-importer:///"
                        + baseUrl.getRawSchemeSpecificPart()
        );
        var resolved = hierarchicalBase.resolve(requestedUrl);
        var path = Objects.requireNonNullElse(resolved.getRawPath(), "");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        var text = new StringBuilder(baseUrl.getScheme())
                .append(':')
                .append(path);
        if (resolved.getRawQuery() != null) {
            text.append('?').append(resolved.getRawQuery());
        }
        if (resolved.getRawFragment() != null) {
            text.append('#').append(resolved.getRawFragment());
        }
        return URI.create(text.toString());
    }

    /// Identifies one importer canonicalization request.
    ///
    /// @param url the URL passed to the importer
    /// @param fromImport whether a legacy import initiated the request
    private record CanonicalizeKey(URI url, boolean fromImport) {
        /// Creates an immutable cache key.
        private CanonicalizeKey {
            Objects.requireNonNull(url, "url");
        }
    }

    /// Stores one cacheable canonicalization outcome.
    ///
    /// @param result the claimed canonical URL, or {@code null} when the
    ///               importer declined the request
    private record CachedCanonicalization(
            @Nullable CanonicalizedImport result
    ) {
    }

    /// Associates a canonical URL with the importer that owns it.
    ///
    /// @param importer the importer that returned the URL
    /// @param canonicalUrl the canonical URL
    /// @param deprecation the canonicalization deprecation, or {@code null}
    private record CanonicalizedImport(
            SassImporter importer,
            URI canonicalUrl,
            @Nullable ImportDeprecation deprecation
    ) {
        /// Creates a validated canonicalization result.
        private CanonicalizedImport {
            Objects.requireNonNull(importer, "importer");
            Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        }
    }
}
