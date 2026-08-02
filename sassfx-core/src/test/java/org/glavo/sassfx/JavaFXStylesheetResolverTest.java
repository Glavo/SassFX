// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies custom JavaFX retained-stylesheet resolution.
@NotNullByDefault
final class JavaFXStylesheetResolverTest {
    /// Resolves nested custom-scheme imports and records their canonical URLs.
    @Test
    void resolvesNestedCustomSchemeImports() throws Exception {
        var rootUrl = URI.create("memory:/root.scss");
        var themeUrl = URI.create("memory:/theme.css");
        var nestedUrl = URI.create("memory:/nested.css");
        @Unmodifiable Map<URI, String> contents = Map.of(
                themeUrl,
                """
                        @import "nested.css";
                        ThemePane { -fx-opacity: 0.75; }
                        """,
                nestedUrl,
                "NestedPane { -fx-opacity: 0.25; }"
        );
        var requests = new ArrayList<String>();
        JavaFXStylesheetResolver resolver = (resource, baseUrl) -> {
            requests.add(baseUrl + " -> " + resource);
            var canonical = baseUrl == null
                    ? URI.create(resource)
                    : baseUrl.resolve(resource);
            @Nullable String content = contents.get(canonical);
            return content == null
                    ? null
                    : new JavaFXStylesheetResolver.ResolvedStylesheet(
                            canonical,
                            content
                    );
        };
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @import "theme.css" (prefers-color-scheme: dark);
                                RootPane { -fx-opacity: 1; }
                                """,
                        Syntax.SCSS,
                        rootUrl
                ),
                new BssTarget(JavaFXTarget.JAVAFX27)
                        .withStylesheetResolver(resolver),
                CompileOptions.DEFAULT
        );
        var binaryText = new String(
                remainingBytes(result.output()),
                StandardCharsets.ISO_8859_1
        );

        assertEquals(9, Short.toUnsignedInt(result.output().getShort(0)));
        assertEquals(
                List.of(
                        "memory:/root.scss -> theme.css",
                        "memory:/theme.css -> nested.css"
                ),
                requests
        );
        assertEquals(
                java.util.Set.of(rootUrl, themeUrl, nestedUrl),
                result.loadedUrls()
        );
        assertTrue(binaryText.contains("RootPane"));
        assertTrue(binaryText.contains("ThemePane"));
        assertTrue(binaryText.contains("NestedPane"));
    }

    /// Resolves an extensionless custom URL and receives a null containing URL.
    @Test
    void resolvesExtensionlessUrlWithoutContainingUrl() throws Exception {
        var calls = new AtomicInteger();
        JavaFXStylesheetResolver resolver = (resource, baseUrl) -> {
            calls.incrementAndGet();
            assertEquals("memory:/theme", resource);
            assertNull(baseUrl);
            return new JavaFXStylesheetResolver.ResolvedStylesheet(
                    URI.create(resource),
                    "ThemePane { -fx-opacity: 0.5; }"
            );
        };

        var result = new SassCompiler().compile(
                SassSource.fromString(
                        "@import url(\"memory:/theme\");",
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX17)
                        .withStylesheetResolver(resolver),
                CompileOptions.DEFAULT
        );

        assertEquals(1, calls.get());
        assertEquals(
                java.util.Set.of(URI.create("memory:/theme")),
                result.loadedUrls()
        );
    }

    /// May resolve repeated imports while loaded URLs retain canonical set semantics.
    @Test
    void resolvesRepeatedCanonicalImport() throws Exception {
        var canonicalUrl = URI.create("memory:/theme.css");
        var calls = new AtomicInteger();
        JavaFXStylesheetResolver resolver = (resource, baseUrl) -> {
            calls.incrementAndGet();
            return new JavaFXStylesheetResolver.ResolvedStylesheet(
                    canonicalUrl,
                    "ThemePane { -fx-opacity: 0.5; }"
            );
        };

        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @import "first.css";
                                @import "alias.css";
                                """,
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX17)
                        .withStylesheetResolver(resolver),
                CompileOptions.DEFAULT
        );

        assertEquals(2, calls.get());
        assertEquals(java.util.Set.of(canonicalUrl), result.loadedUrls());
    }

    /// Falls back to exact filesystem lookup when the custom resolver declines.
    @Test
    void fallsBackToFilesystemLookup(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("theme.css"),
                "ImportedPane { -fx-opacity: 0.5; }"
        );
        var calls = new AtomicInteger();
        JavaFXStylesheetResolver resolver = (resource, baseUrl) -> {
            calls.incrementAndGet();
            return null;
        };
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        "@import \"theme.css\"; RootPane { -fx-opacity: 1; }",
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX17)
                        .withStylesheetResolver(resolver),
                CompileOptions.DEFAULT.withLoadPaths(List.of(directory))
        );
        var binaryText = new String(
                remainingBytes(result.output()),
                StandardCharsets.ISO_8859_1
        );

        assertEquals(1, calls.get());
        assertTrue(binaryText.contains("ImportedPane"));
        assertTrue(binaryText.contains("RootPane"));
    }

    /// Associates custom resolver IO failures with the importing rule.
    @Test
    void reportsResolverIoFailure() {
        var rootUrl = URI.create("memory:/root.scss");
        var cause = new IOException("resource store unavailable");
        JavaFXStylesheetResolver resolver = (resource, baseUrl) -> {
            throw cause;
        };

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "@import \"theme.css\";",
                                Syntax.SCSS,
                                rootUrl
                        ),
                        new BssTarget(JavaFXTarget.JAVAFX27)
                                .withStylesheetResolver(resolver),
                        CompileOptions.DEFAULT
                )
        );

        assertEquals(
                "Unable to load JavaFX CSS import \"theme.css\".",
                failure.getMessage()
        );
        assertEquals(rootUrl, failure.primaryDiagnostic().span().url());
        assertSame(cause, failure.getCause().getCause());
    }

    /// Detects cycles by the resolver-provided canonical identity.
    @Test
    void detectsCanonicalCustomImportCycle() {
        var rootUrl = URI.create("memory:/root.scss");
        var themeUrl = URI.create("memory:/theme.css");
        JavaFXStylesheetResolver resolver = (resource, baseUrl) ->
                new JavaFXStylesheetResolver.ResolvedStylesheet(
                        themeUrl,
                        "@import \"self.css\";"
                );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "@import \"theme.css\";",
                                Syntax.SCSS,
                                rootUrl
                        ),
                        new BssTarget(JavaFXTarget.JAVAFX27)
                                .withStylesheetResolver(resolver),
                        CompileOptions.DEFAULT
                )
        );

        assertEquals(
                "Recursive JavaFX CSS import of \"memory:/theme.css\".",
                failure.getMessage()
        );
        assertEquals(themeUrl, failure.primaryDiagnostic().span().url());
    }

    /// Rejects a non-absolute canonical stylesheet URL.
    @Test
    void rejectsRelativeCanonicalUrl() {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> new JavaFXStylesheetResolver.ResolvedStylesheet(
                        URI.create("relative.css"),
                        ""
                )
        );

        assertEquals("canonicalUrl must be absolute", failure.getMessage());
    }

    /// Copies all remaining output bytes without changing the supplied buffer.
    ///
    /// @param buffer the binary compiler output
    /// @return a newly allocated byte array
    private static byte @Unmodifiable [] remainingBytes(
            @Unmodifiable ByteBuffer buffer
    ) {
        var copy = buffer.duplicate();
        var bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
