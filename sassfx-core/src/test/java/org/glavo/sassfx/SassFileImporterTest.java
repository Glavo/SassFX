// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies compiler-managed Sass file importers.
@NotNullByDefault
final class SassFileImporterTest {
    /// Applies standard partial and extension resolution to a returned file URL.
    @Test
    void resolvesReturnedFileStem(@TempDir Path directory) throws Exception {
        var module = Files.writeString(
                directory.resolve("_theme.scss"),
                ".theme { color: royalblue; }"
        );
        var requests = new ArrayList<URI>();
        var contexts = new ArrayList<SassCanonicalizeContext>();
        SassFileImporter importer = (url, context) -> {
            requests.add(url);
            contexts.add(context);
            return url.toString().equals("pkg:theme")
                    ? directory.resolve("theme").toUri()
                    : null;
        };

        var result = compile(
                "@use \"pkg:theme\";",
                URI.create("memory:///root.scss"),
                List.of(importer)
        );

        assertTrue(result.output().contains(".theme"));
        assertEquals(List.of(URI.create("pkg:theme")), requests);
        assertEquals(
                URI.create("memory:///root.scss"),
                contexts.get(0).containingUrl()
        );
        assertEquals(false, contexts.get(0).fromImport());
        assertTrue(result.loadedUrls().contains(module.toRealPath().toUri()));
    }

    /// Selects import-only files only for legacy imports.
    @Test
    void distinguishesModuleAndLegacyImportResolution(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_theme.scss"),
                ".ordinary { value: ordinary; }"
        );
        Files.writeString(
                directory.resolve("_theme.import.scss"),
                ".legacy { value: legacy; }"
        );
        var contexts = new ArrayList<SassCanonicalizeContext>();
        SassFileImporter importer = (url, context) -> {
            contexts.add(context);
            return directory.resolve("theme").toUri();
        };

        var moduleResult = compile(
                "@use \"pkg:theme\";",
                URI.create("memory:///module.scss"),
                List.of(importer)
        );
        var importResult = compile(
                "@import \"pkg:theme\";",
                URI.create("memory:///import.scss"),
                List.of(importer)
        );

        assertTrue(moduleResult.output().contains(".ordinary"));
        assertTrue(importResult.output().contains(".legacy"));
        assertEquals(false, contexts.get(0).fromImport());
        assertEquals(true, contexts.get(1).fromImport());
    }

    /// Bypasses the callback for an absolute file URL.
    @Test
    void bypassesCallbackForFileUrl(@TempDir Path directory) throws Exception {
        var module = Files.writeString(
                directory.resolve("_direct.scss"),
                ".direct { value: loaded; }"
        );
        var calls = new int[1];
        SassFileImporter importer = (url, context) -> {
            calls[0]++;
            return null;
        };
        var explicitStem = directory.resolve("direct").toUri();

        var result = compile(
                "@use \"" + explicitStem + "\";",
                URI.create("memory:///root.scss"),
                List.of(importer)
        );

        assertEquals(0, calls[0]);
        assertTrue(result.output().contains(".direct"));
        assertTrue(result.loadedUrls().contains(module.toRealPath().toUri()));
    }

    /// Resolves nested relative loads beside the file without another callback.
    @Test
    void ownsNestedRelativeLoads(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_entry.scss"),
                """
                        @use "nested";
                        .entry { value: entry; }
                        """
        );
        Files.writeString(
                directory.resolve("_nested.scss"),
                ".nested { value: nested; }"
        );
        var requests = new ArrayList<URI>();
        SassFileImporter importer = (url, context) -> {
            requests.add(url);
            return directory.resolve("entry").toUri();
        };

        var result = compile(
                "@use \"pkg:entry\";",
                URI.create("memory:///root.scss"),
                List.of(importer)
        );

        assertEquals(List.of(URI.create("pkg:entry")), requests);
        assertTrue(result.output().contains(".nested"));
        assertTrue(result.output().contains(".entry"));
    }

    /// Continues the ordered chain when a returned file stem has no candidate.
    @Test
    void fallsThroughWhenReturnedFileDoesNotExist(@TempDir Path directory)
            throws Exception {
        var events = new ArrayList<String>();
        SassFileImporter fileImporter = (url, context) -> {
            events.add("file");
            return directory.resolve("missing").toUri();
        };
        var contentsImporter = new EventImporter(events);

        var result = compile(
                "@use \"theme\";",
                URI.create("memory:///root.scss"),
                List.of(fileImporter, contentsImporter)
        );

        assertEquals(List.of("file", "contents"), events);
        assertTrue(result.output().contains(".fallback"));
    }

    /// Preserves mixed file and contents importer ordering.
    @Test
    void preservesMixedImporterOrder() throws Exception {
        var events = new ArrayList<String>();
        var first = new DecliningImporter("contents-1", events);
        SassFileImporter second = (url, context) -> {
            events.add("file-1");
            return null;
        };
        var third = new EventImporter(events);

        var result = compile(
                "@use \"theme\";",
                URI.create("memory:///root.scss"),
                List.of(first, second, third)
        );

        assertEquals(List.of("contents-1", "file-1", "contents"), events);
        assertTrue(result.output().contains(".fallback"));
    }

    /// Rejects relative and non-file callback results.
    @Test
    void rejectsInvalidReturnedUrls() {
        SassFileImporter relative =
                (url, context) -> URI.create("styles/theme.scss");
        SassFileImporter nonFile =
                (url, context) -> URI.create("memory:///theme.scss");

        var relativeFailure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        "@use \"theme\";",
                        URI.create("memory:///root.scss"),
                        List.of(relative)
                )
        );
        var nonFileFailure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        "@use \"theme\";",
                        URI.create("memory:///root.scss"),
                        List.of(nonFile)
                )
        );

        assertEquals(
                "The file importer must return an absolute URL, was "
                        + "\"styles/theme.scss\".",
                relativeFailure.getMessage()
        );
        assertEquals(
                "The file importer must return a file: URL, was "
                        + "\"memory:///theme.scss\".",
                nonFileFailure.getMessage()
        );
    }

    /// Passes a containing URL for absolute non-file requests.
    @Test
    void exposesContainingUrlForAbsoluteNonFileRequest() {
        var contexts = new ArrayList<SassCanonicalizeContext>();
        SassFileImporter importer = (url, context) -> {
            contexts.add(context);
            return null;
        };

        assertThrows(
                SassCompilationException.class,
                () -> compile(
                        "@use \"pkg:missing\";",
                        URI.create("memory:///root.scss"),
                        List.of(importer)
                )
        );

        assertEquals(
                URI.create("memory:///root.scss"),
                contexts.get(0).containingUrl()
        );

        assertThrows(
                SassCompilationException.class,
                () -> compile(
                        "@use \"pkg:missing\";",
                        null,
                        List.of(importer)
                )
        );
        assertNull(contexts.get(1).containingUrl());
    }

    /// Preserves callback failures as source-associated compilation causes.
    @Test
    void propagatesCallbackFailure() {
        var cause = new IOException("file importer failed");
        SassFileImporter importer = (url, context) -> {
            throw cause;
        };

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        "@use \"pkg:theme\";",
                        URI.create("memory:///root.scss"),
                        List.of(importer)
                )
        );

        assertEquals("file importer failed", failure.getMessage());
        assertEquals(
                URI.create("memory:///root.scss"),
                failure.primaryDiagnostic().span().url()
        );
        assertSame(cause, failure.getCause().getCause());
    }

    /// Compiles one root with an ordered mixed importer list.
    private static CompileResult<String> compile(
            String source,
            @Nullable URI rootUrl,
            List<SassImporter> importers
    ) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS, rootUrl),
                CssTarget.DEFAULT,
                new CompileOptions(false, List.of(), null, importers)
        );
    }

    /// Declines every request while recording its position in the chain.
    private static class DecliningImporter implements SassImporter {
        /// The event label.
        private final String label;

        /// The shared event destination.
        private final List<String> events;

        /// Creates a declining importer.
        private DecliningImporter(String label, List<String> events) {
            this.label = label;
            this.events = events;
        }

        /// Records and declines a canonicalization request.
        @Override
        public @Nullable URI canonicalize(
                URI url,
                SassCanonicalizeContext context
        ) {
            events.add(label);
            return null;
        }

        /// Is unreachable because canonicalization always declines.
        @Override
        public @Nullable SassImporterResult load(URI canonicalUrl) {
            throw new AssertionError("load must not be called");
        }
    }

    /// Supplies one in-memory fallback stylesheet.
    private static final class EventImporter extends DecliningImporter {
        /// Creates a fallback importer.
        private EventImporter(List<String> events) {
            super("contents", events);
        }

        /// Claims the fallback stylesheet.
        @Override
        public @Nullable URI canonicalize(
                URI url,
                SassCanonicalizeContext context
        ) {
            super.canonicalize(url, context);
            return URI.create("memory:///fallback.scss");
        }

        /// Returns the fallback stylesheet.
        @Override
        public @Nullable SassImporterResult load(URI canonicalUrl) {
            return new SassImporterResult(
                    ".fallback { value: loaded; }",
                    Syntax.SCSS
            );
        }
    }
}
