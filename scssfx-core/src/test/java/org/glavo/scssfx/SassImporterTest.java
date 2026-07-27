// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies custom Sass importer ordering, context, identity, and metadata.
@NotNullByDefault
final class SassImporterTest {
    /// Gives a custom-loaded stylesheet ownership of its relative loads.
    @Test
    void resolvesRelativeLoadsWithOwningImporter() throws Exception {
        var importer = new RecordingImporter(Map.of(
                URI.create("virtual:///entry.scss"),
                new SassImporterResult(
                        """
                                @use "nested";
                                .entry { color: red; }
                                """,
                        Syntax.SCSS
                ),
                URI.create("virtual:///nested.scss"),
                new SassImporterResult(".nested { color: blue; }", Syntax.SCSS)
        ));

        var result = compile(
                """
                        @use "entry";
                        """,
                List.of(importer),
                false
        );

        assertEquals(
                """
                        .nested {
                          color: blue;
                        }

                        .entry {
                          color: red;
                        }""",
                result.output()
        );
        assertEquals(
                List.of(
                        URI.create("entry"),
                        URI.create("virtual:/nested")
                ),
                importer.requests
        );
        assertEquals(
                URI.create("memory:///root.scss"),
                importer.contexts.get(0).containingUrl()
        );
        assertEquals(null, importer.contexts.get(1).containingUrl());
        assertEquals(
                List.of(
                        URI.create("virtual:///entry.scss"),
                        URI.create("virtual:///nested.scss")
                ),
                importer.loads
        );
        assertTrue(result.loadedUrls().contains(URI.create("virtual:///entry.scss")));
        assertTrue(result.loadedUrls().contains(URI.create("virtual:///nested.scss")));
    }

    /// Resolves relative loads against opaque canonical URLs using Sass URL
    /// path semantics.
    @Test
    void resolvesRelativeLoadsAgainstOpaqueCanonicalUrls() throws Exception {
        var requests = new ArrayList<URI>();
        var sources = Map.of(
                URI.create("custom:foo/bar"),
                new SassImporterResult(
                        "@use \"upstream\";",
                        Syntax.SCSS
                ),
                URI.create("custom:foo/upstream"),
                new SassImporterResult(
                        ".upstream { value: loaded; }",
                        Syntax.SCSS
                )
        );
        SassImporter importer = new SassImporter() {
            /// Canonicalizes the entrypoint and its resolved opaque child URL.
            @Override
            public @Nullable URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                requests.add(url);
                if (url.equals(URI.create("entry"))) {
                    return URI.create("custom:foo/bar");
                }
                return sources.containsKey(url) ? url : null;
            }

            /// Loads one canonical in-memory source.
            @Override
            public @Nullable SassImporterResult load(URI canonicalUrl) {
                return sources.get(canonicalUrl);
            }
        };

        var result = compile(
                "@use \"entry\";",
                List.of(importer),
                false
        );

        assertEquals(
                List.of(
                        URI.create("entry"),
                        URI.create("custom:foo/upstream")
                ),
                requests
        );
        assertTrue(result.output().contains("value: loaded"));
    }

    /// Exposes legacy import context and loads one canonical source only once.
    @Test
    void cachesCanonicalLoadsForLegacyImports() throws Exception {
        var importer = new RecordingImporter(Map.of(
                URI.create("virtual:///legacy.scss"),
                new SassImporterResult(".legacy { value: 1; }", Syntax.SCSS)
        ));

        var result = compile(
                """
                        @import "legacy";
                        @import "legacy";
                        """,
                List.of(importer),
                false
        );

        assertEquals(1, importer.contexts.size());
        assertTrue(importer.contexts.stream().allMatch(
                SassCanonicalizeContext::fromImport
        ));
        assertEquals(1, importer.loads.size());
        assertEquals(2, countOccurrences(result.output(), ".legacy"));
    }

    /// Caches canonicalization only when the importer does not observe the
    /// containing URL.
    @Test
    void cachesOnlyContextIndependentCanonicalizations() throws Exception {
        assertEquals(1, contextualCanonicalizationCount(false));
        assertEquals(2, contextualCanonicalizationCount(true));
    }

    /// Consults importers in order and falls back to filesystem load paths.
    @Test
    void preservesImporterOrderAndFilesystemFallback(@TempDir Path directory)
            throws Exception {
        Files.writeString(directory.resolve("_file.scss"), ".file { value: ok; }");
        var first = new RecordingImporter(Map.of());
        var second = new RecordingImporter(Map.of(
                URI.create("virtual:///custom.scss"),
                new SassImporterResult(".custom { value: ok; }", Syntax.SCSS)
        ));
        var options = new CompileOptions(
                false,
                List.of(directory),
                null,
                List.of(first, second)
        );

        var custom = new SassCompiler().compile(
                SassSource.fromString(
                        "@use \"custom\";",
                        Syntax.SCSS,
                        URI.create("memory:///custom-root.scss")
                ),
                CssTarget.DEFAULT,
                options
        );
        var file = new SassCompiler().compile(
                SassSource.fromString(
                        "@use \"file\";",
                        Syntax.SCSS,
                        URI.create("memory:///file-root.scss")
                ),
                CssTarget.DEFAULT,
                options
        );

        assertTrue(custom.output().contains(".custom"));
        assertTrue(file.output().contains(".file"));
        assertEquals(2, first.requests.size());
        assertEquals(2, second.requests.size());
        assertEquals(1, second.loads.size());
    }

    /// Treats a claimed URL with no loaded contents as a terminal failure.
    @Test
    void doesNotContinueAfterClaimedUrlFailsToLoad() {
        var claiming = new RecordingImporter(Map.of()) {
            /// Claims every request.
            @Override
            public @Nullable URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                requests.add(url);
                contexts.add(context);
                return URI.create("virtual:///missing.scss");
            }
        };
        var later = new RecordingImporter(Map.of(
                URI.create("virtual:///missing.scss"),
                new SassImporterResult(".unexpected {}", Syntax.SCSS)
        ));

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile("@use \"missing\";", List.of(claiming, later), false)
        );

        assertTrue(failure.getMessage().contains("Can't find stylesheet to import."));
        assertTrue(later.requests.isEmpty());
    }

    /// Retains already-loaded source text when an imported stylesheet fails.
    @Test
    void exposesFailureSourceContentsWithoutRereadingUrls() {
        var importedUrl = URI.create("virtual:///failure.scss");
        var importedContents = """
                .failure {
                  value: 1px +
                      1em;
                }
                """;
        var importer = new RecordingImporter(Map.of(
                importedUrl,
                new SassImporterResult(importedContents, Syntax.SCSS)
        ));

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        "@use \"failure\";",
                        List.of(importer),
                        false
                )
        );

        assertEquals(
                importedContents,
                failure.sourceContents().get(importedUrl)
        );
        assertEquals(
                "@use \"failure\";",
                failure.sourceContents().get(
                        URI.create("memory:///root.scss")
                )
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> failure.sourceContents().clear()
        );
    }

    /// Uses importer-provided URLs in source maps while retaining canonical identity.
    @Test
    void recordsImporterSourceMapUrl() throws Exception {
        var sourceMapUrl = URI.create("https://example.test/sources/theme.scss");
        var importer = new RecordingImporter(Map.of(
                URI.create("virtual:///theme.scss"),
                new SassImporterResult(
                        ".theme { color: red; }",
                        Syntax.SCSS,
                        sourceMapUrl
                )
        ));

        var result = compile("@use \"theme\";", List.of(importer), true);

        assertTrue(result.sourceMap().json().contains(sourceMapUrl.toString()));
        assertFalse(result.sourceMap().json().contains("virtual:///theme.scss"));
        assertTrue(result.loadedUrls().contains(URI.create("virtual:///theme.scss")));
    }

    /// Passes the containing URL only for absolute schemes declared non-canonical.
    @Test
    void passesContainingUrlForDeclaredNonCanonicalSchemes() throws Exception {
        var source = new SassImporterResult(".package { color: red; }", Syntax.SCSS);
        var packageImporter = new RecordingImporter(Map.of(
                URI.create("virtual:///package.scss"),
                source
        )) {
            /// Identifies package requests as context-dependent.
            @Override
            public boolean isNonCanonicalScheme(String scheme) {
                return "pkg".equals(scheme);
            }

            /// Canonicalizes the package test URL.
            @Override
            public @Nullable URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                requests.add(url);
                contexts.add(context);
                return URI.create("pkg:demo").equals(url)
                        ? URI.create("virtual:///package.scss")
                        : null;
            }
        };
        var absoluteImporter = new RecordingImporter(Map.of(
                URI.create("virtual:///absolute.scss"),
                source
        )) {
            /// Canonicalizes the ordinary absolute test URL.
            @Override
            public @Nullable URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                requests.add(url);
                contexts.add(context);
                return URI.create("https://example.test/theme").equals(url)
                        ? URI.create("virtual:///absolute.scss")
                        : null;
            }
        };

        compile("@use \"pkg:demo\" as package;", List.of(packageImporter), false);
        compile(
                "@use \"https://example.test/theme\" as theme;",
                List.of(absoluteImporter),
                false
        );

        assertEquals(
                URI.create("memory:///root.scss"),
                packageImporter.contexts.get(0).containingUrl()
        );
        assertEquals(null, absoluteImporter.contexts.get(0).containingUrl());
    }

    /// Snapshots importer lists in compile options.
    @Test
    void snapshotsImporterOptions() {
        var importer = new RecordingImporter(Map.of());
        var importers = new ArrayList<SassImporter>(List.of(importer));
        var options = new CompileOptions(false, List.of(), null, importers);
        importers.clear();

        assertEquals(List.of(importer), options.importers());
        assertThrows(UnsupportedOperationException.class, () -> options.importers().clear());
    }

    /// Compiles a string root with the supplied importer order.
    private static CompileResult<String> compile(
            String source,
            List<SassImporter> importers,
            boolean sourceMap
    ) throws IOException, SassCompilationException {
        return new SassCompiler().compile(
                SassSource.fromString(
                        source,
                        Syntax.SCSS,
                        URI.create("memory:///root.scss")
                ),
                CssTarget.DEFAULT,
                new CompileOptions(sourceMap, List.of(), null, importers)
        );
    }

    /// Counts non-overlapping occurrences of one substring.
    private static int countOccurrences(String text, String value) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    /// Compiles two containing modules through one context-sensitive importer.
    ///
    /// @param useContainingUrl whether the importer observes the containing URL
    /// @return the number of canonicalizations of the shared request
    /// @throws IOException if compilation cannot load a source
    /// @throws SassCompilationException if the fixture does not compile
    private static int contextualCanonicalizationCount(
            boolean useContainingUrl
    ) throws IOException, SassCompilationException {
        var containers = Map.of(
                URI.create("container:left"),
                new SassImporterResult("@use \"shared\";", Syntax.SCSS),
                URI.create("container:right"),
                new SassImporterResult("@use \"shared\";", Syntax.SCSS)
        );
        SassImporter containerImporter = new SassImporter() {
            /// Canonicalizes the two containing modules.
            @Override
            public @Nullable URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                if (url.equals(URI.create("left"))) {
                    return URI.create("container:left");
                }
                if (url.equals(URI.create("right"))) {
                    return URI.create("container:right");
                }
                return null;
            }

            /// Loads a containing module.
            @Override
            public @Nullable SassImporterResult load(URI canonicalUrl) {
                return containers.get(canonicalUrl);
            }
        };
        var requests = new ArrayList<URI>();
        SassImporter sharedImporter = new SassImporter() {
            /// Canonicalizes the shared request.
            @Override
            public @Nullable URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                if (!url.equals(URI.create("shared"))) {
                    return null;
                }
                requests.add(url);
                if (useContainingUrl) {
                    assertTrue(context.containingUrl() != null);
                }
                return URI.create("shared:canonical");
            }

            /// Loads the shared module.
            @Override
            public @Nullable SassImporterResult load(URI canonicalUrl) {
                return canonicalUrl.equals(URI.create("shared:canonical"))
                        ? new SassImporterResult(
                                ".shared { value: loaded; }",
                                Syntax.SCSS
                        )
                        : null;
            }
        };

        compile(
                """
                        @use "left";
                        @use "right";
                        """,
                List.of(containerImporter, sharedImporter),
                false
        );
        return requests.size();
    }

    /// Resolves short test URLs into an in-memory virtual URL space.
    private static class RecordingImporter implements SassImporter {
        /// Contains virtual sources keyed by canonical URL.
        private final Map<URI, SassImporterResult> sources;

        /// Records canonicalization inputs.
        protected final ArrayList<URI> requests = new ArrayList<>();

        /// Records canonicalization contexts.
        protected final ArrayList<SassCanonicalizeContext> contexts =
                new ArrayList<>();

        /// Records load inputs.
        protected final ArrayList<URI> loads = new ArrayList<>();

        /// Creates a recording importer.
        private RecordingImporter(Map<URI, SassImporterResult> sources) {
            this.sources = new LinkedHashMap<>(sources);
        }

        /// Canonicalizes recognized virtual requests.
        @Override
        public @Nullable URI canonicalize(
                URI url,
                SassCanonicalizeContext context
        ) {
            requests.add(url);
            contexts.add(context);
            if (url.isAbsolute()) {
                var path = url.getPath();
                if (!"virtual".equals(url.getScheme()) || path == null) {
                    return null;
                }
                var canonical = URI.create(
                        "virtual:///" + stripExtension(stripLeadingSlash(path)) + ".scss"
                );
                return sources.containsKey(canonical) ? canonical : null;
            }
            var canonical = URI.create(
                    "virtual:///" + stripExtension(url.toString()) + ".scss"
            );
            return sources.containsKey(canonical) ? canonical : null;
        }

        /// Loads one recognized canonical URL.
        @Override
        public @Nullable SassImporterResult load(URI canonicalUrl) {
            loads.add(canonicalUrl);
            return sources.get(canonicalUrl);
        }

        /// Removes one supported Sass extension.
        private static String stripExtension(String value) {
            if (value.endsWith(".scss")) {
                return value.substring(0, value.length() - ".scss".length());
            }
            return value;
        }

        /// Removes a leading slash from a virtual URI path.
        private static String stripLeadingSlash(String value) {
            return value.startsWith("/") ? value.substring(1) : value;
        }
    }
}
