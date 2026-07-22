// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies immutable root-source, options, and result models.
@NotNullByDefault
final class CompilationModelTest {
    /// Verifies inferred and explicit file-source syntax.
    @Test
    void createsFileSources() {
        assertEquals(
                new SassFileSource(Path.of("style.scss"), Syntax.SCSS),
                SassSource.fromFile(Path.of("style.scss"))
        );
        assertEquals(
                Syntax.CSS,
                SassSource.fromFile(Path.of("style.input"), Syntax.CSS).syntax()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SassSource.fromFile(Path.of("style.input"))
        );
    }

    /// Verifies string-source canonical URL constraints.
    @Test
    void createsStringSources() {
        var withoutUrl = SassSource.fromString("a {}", Syntax.SCSS);
        assertNull(withoutUrl.canonicalUrl());

        var url = URI.create("memory:style.scss");
        var withUrl = SassSource.fromString("a {}", Syntax.SCSS, url);
        assertEquals(url, withUrl.canonicalUrl());
        assertThrows(
                IllegalArgumentException.class,
                () -> SassSource.fromString("a {}", Syntax.SCSS, URI.create("style.scss"))
        );
    }

    /// Verifies that compile options snapshot load paths.
    @Test
    void snapshotsCompileOptions() {
        var paths = new ArrayList<>(List.of(Path.of("styles")));
        var options = new CompileOptions(true, paths);
        paths.clear();

        assertEquals(true, options.sourceMap());
        assertEquals(List.of(Path.of("styles")), options.loadPaths());
        assertThrows(UnsupportedOperationException.class, () -> options.loadPaths().clear());
    }

    /// Verifies that compile results snapshot metadata collections.
    @Test
    void snapshotsCompileResultMetadata() {
        var url = URI.create("file:///style.scss");
        var urls = new HashSet<>(Set.of(url));
        var diagnostic = new Diagnostic(DiagnosticSeverity.WARNING, "Warning.", null, null);
        var diagnostics = new ArrayList<>(List.of(diagnostic));
        var sourceMap = new SourceMap("{\"version\":3}");

        var result = new CompileResult<>("a {}", sourceMap, urls, diagnostics);
        urls.clear();
        diagnostics.clear();

        assertEquals("a {}", result.output());
        assertEquals(sourceMap, result.sourceMap());
        assertEquals(Set.of(url), result.loadedUrls());
        assertEquals(List.of(diagnostic), result.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> result.loadedUrls().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
    }

    /// Verifies that source-map data is one version 3 JSON object.
    @Test
    void validatesSourceMapJson() {
        assertEquals("{\"version\":3}", new SourceMap("{\"version\":3}").json());
        assertThrows(IllegalArgumentException.class, () -> new SourceMap("not json"));
        assertThrows(IllegalArgumentException.class, () -> new SourceMap("[]"));
        assertThrows(IllegalArgumentException.class, () -> new SourceMap("{}"));
        assertThrows(IllegalArgumentException.class, () -> new SourceMap("{\"version\":4}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceMap("{\"version\":3}{\"version\":3}")
        );
    }
}
