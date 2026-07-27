// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies importer and string-compilation API deprecations.
@NotNullByDefault
final class ImporterApiDeprecationTest {
    /// Reports relative string source URLs while retaining their identity.
    @Test
    void reportsRelativeStringSourceUrl() throws Exception {
        var url = URI.create("styles/root.scss");
        var result = compile(
                new SassStringSource(
                        "a { color: red; }",
                        Syntax.SCSS,
                        url
                ),
                options(Set.of(), Set.of(), false, List.of(), List.of())
        );

        assertEquals(Set.of(url), result.loadedUrls());
        assertEquals(1, result.diagnostics().size());
        var diagnostic = result.diagnostics().get(0);
        assertEquals(
                SassDeprecation.COMPILE_STRING_RELATIVE_URL.id(),
                diagnostic.code()
        );
        assertTrue(diagnostic.message().contains("(styles/root.scss)"));
    }

    /// Applies silence and fatal policies to relative string source URLs.
    @Test
    void processesRelativeStringSourceUrlPolicy() throws Exception {
        var source = new SassStringSource(
                "a { color: red; }",
                Syntax.SCSS,
                URI.create("/root.scss")
        );
        var silenced = compile(
                source,
                options(
                        Set.of(SassDeprecation.COMPILE_STRING_RELATIVE_URL),
                        Set.of(),
                        false,
                        List.of(),
                        List.of()
                )
        );
        assertTrue(silenced.diagnostics().isEmpty());

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        source,
                        options(
                                Set.of(),
                                Set.of(SassDeprecation.COMPILE_STRING_RELATIVE_URL),
                                false,
                                List.of(),
                                List.of()
                        )
                )
        );
        assertEquals(
                SassDeprecation.COMPILE_STRING_RELATIVE_URL.id(),
                failure.primaryDiagnostic().code()
        );
    }

    /// Loads relative importer canonical URLs and reports their deprecation.
    @Test
    void reportsRelativeImporterCanonicalUrl() throws Exception {
        var importer = new RelativeImporter();
        var result = compile(
                SassSource.fromString("@use \"entry\";", Syntax.SCSS),
                options(Set.of(), Set.of(), false, List.of(importer), List.of())
        );

        assertEquals(
                """
                        .relative {
                          color: blue;
                        }""",
                result.output()
        );
        assertEquals(Set.of(URI.create("entry.scss")), result.loadedUrls());
        assertEquals(1, result.diagnostics().size());
        var diagnostic = result.diagnostics().get(0);
        assertEquals(SassDeprecation.RELATIVE_CANONICAL.id(), diagnostic.code());
        assertEquals(
                """
                        Importer relative-test-importer canonicalized entry to entry.scss.
                        Relative canonical URLs are deprecated and will eventually be disallowed.""",
                diagnostic.message()
        );
    }

    /// Suppresses importer canonicalization deprecations for quiet dependencies.
    @Test
    void suppressesRelativeCanonicalForQuietDependencies() throws Exception {
        var result = compile(
                SassSource.fromString("@use \"entry\";", Syntax.SCSS),
                options(
                        Set.of(),
                        Set.of(SassDeprecation.RELATIVE_CANONICAL),
                        true,
                        List.of(new RelativeImporter()),
                        List.of()
                )
        );

        assertTrue(result.diagnostics().isEmpty());
    }

    /// Applies silence and fatal policies to relative canonical URLs.
    @Test
    void processesRelativeCanonicalPolicy() throws Exception {
        var source = SassSource.fromString("@use \"entry\";", Syntax.SCSS);
        var silenced = compile(
                source,
                options(
                        Set.of(SassDeprecation.RELATIVE_CANONICAL),
                        Set.of(),
                        false,
                        List.of(new RelativeImporter()),
                        List.of()
                )
        );
        assertTrue(silenced.diagnostics().isEmpty());

        var fatalImporter = new RelativeImporter();
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        source,
                        options(
                                Set.of(),
                                Set.of(SassDeprecation.RELATIVE_CANONICAL),
                                false,
                                List.of(fatalImporter),
                                List.of()
                        )
                )
        );
        assertEquals(
                SassDeprecation.RELATIVE_CANONICAL.id(),
                failure.primaryDiagnostic().code()
        );
        assertEquals(0, fatalImporter.loadCount);
    }

    /// Reports successful fallback loads through the process working directory.
    @Test
    void reportsImplicitCurrentWorkingDirectoryLoad() throws Exception {
        var result = compile(
                SassSource.fromString(
                        "@use \"src/test/resources/importer/cwd-deprecation\";",
                        Syntax.SCSS
                ),
                options(Set.of(), Set.of(), false, List.of(), List.of())
        );

        assertEquals(
                """
                        .cwd-deprecation {
                          color: green;
                        }""",
                result.output()
        );
        assertEquals(1, result.diagnostics().size());
        assertEquals(
                SassDeprecation.FS_IMPORTER_CWD.id(),
                result.diagnostics().get(0).code()
        );
    }

    /// Does not report CWD fallback when the directory is an explicit load path.
    @Test
    void acceptsExplicitCurrentWorkingDirectoryLoadPath() throws Exception {
        var result = compile(
                SassSource.fromString(
                        "@use \"src/test/resources/importer/cwd-deprecation\";",
                        Syntax.SCSS
                ),
                options(
                        Set.of(),
                        Set.of(),
                        false,
                        List.of(),
                        List.of(Path.of("."))
                )
        );

        assertTrue(result.diagnostics().isEmpty());
    }

    /// Promotes a successful implicit CWD fallback when configured as fatal.
    @Test
    void processesImplicitCurrentWorkingDirectoryPolicy() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        SassSource.fromString(
                                "@use \"src/test/resources/importer/cwd-deprecation\";",
                                Syntax.SCSS
                        ),
                        options(
                                Set.of(),
                                Set.of(SassDeprecation.FS_IMPORTER_CWD),
                                false,
                                List.of(),
                                List.of()
                        )
                )
        );

        assertEquals(
                SassDeprecation.FS_IMPORTER_CWD.id(),
                failure.primaryDiagnostic().code()
        );
    }

    /// Compiles one source with explicit diagnostic and importer options.
    private static CompileResult<String> compile(
            SassSource source,
            CompileOptions options
    ) throws Exception {
        return new SassCompiler().compile(source, CssTarget.DEFAULT, options);
    }

    /// Creates compilation options for one deprecation scenario.
    private static CompileOptions options(
            Set<SassDeprecation> silence,
            Set<SassDeprecation> fatal,
            boolean quietDeps,
            List<SassImporter> importers,
            List<Path> loadPaths
    ) {
        return new CompileOptions(
                false,
                loadPaths,
                null,
                importers,
                List.of(),
                new SassDiagnosticOptions(
                        SassLogger.NO_OP,
                        quietDeps,
                        false,
                        silence,
                        fatal,
                        Set.of()
                )
        );
    }

    /// Returns one stylesheet under a relative canonical URL.
    private static final class RelativeImporter implements SassImporter {
        /// Counts calls to [#load(URI)].
        private int loadCount;

        /// Canonicalizes the entry request to a relative stylesheet URL.
        @Override
        public @Nullable URI canonicalize(
                URI url,
                SassCanonicalizeContext context
        ) {
            return "entry".equals(url.toString())
                    ? URI.create("entry.scss")
                    : null;
        }

        /// Loads the relative entry stylesheet.
        @Override
        public @Nullable SassImporterResult load(URI canonicalUrl) {
            loadCount++;
            return URI.create("entry.scss").equals(canonicalUrl)
                    ? new SassImporterResult(
                            ".relative { color: blue; }",
                            Syntax.SCSS
                    )
                    : null;
        }

        /// Returns the stable importer label used in diagnostics.
        @Override
        public String toString() {
            return "relative-test-importer";
        }
    }
}
