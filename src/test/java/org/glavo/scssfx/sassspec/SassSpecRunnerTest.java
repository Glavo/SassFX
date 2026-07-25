// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Runs versioned HRX compatibility fixtures through the public file compiler API.
///
/// The default Gradle test task excludes this tagged suite. The dedicated
/// {@code sassSpec} task runs the dynamic fixtures and writes their stable
/// coverage report.
@Tag("sass-spec")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@NotNullByDefault
final class SassSpecRunnerTest {
    /// Contains all classpath resources used by this compatibility suite.
    private static final String RESOURCE_ROOT = "sass-spec/";

    /// Identifies the versioned fixture selection manifest.
    private static final String MANIFEST_RESOURCE = RESOURCE_ROOT + "index.json";

    /// Names the system property that selects the JSON report directory.
    private static final String REPORT_DIRECTORY_PROPERTY = "scssfx.sassSpecReportDir";

    /// Provides a test-owned filesystem root for materialized fixture cases.
    @TempDir
    private Path temporaryDirectory;

    /// Holds the summary created by the dynamic factory, or {@code null} when setup failed.
    private @Nullable SassSpecSummary summary;

    /// Caches one materialized archive root per HRX resource path for the suite lifetime.
    private final Map<String, Path> materializedArchives = new HashMap<>();

    /// Creates one dynamic test for every manifest-classified fixture directory.
    ///
    /// @return dynamic fixture tests in manifest order
    /// @throws IOException if a manifest or archive resource cannot be read
    @TestFactory
    Stream<DynamicTest> runsCuratedFixtures() throws IOException {
        List<ResolvedFixture> fixtures = loadFixtures();
        var currentSummary = new SassSpecSummary(
                fixtures.stream().map(ResolvedFixture::fixture).toList()
        );
        summary = currentSummary;
        System.out.println(currentSummary.plannedLine());

        return IntStream.range(0, fixtures.size())
                .mapToObj(index -> {
                    ResolvedFixture fixture = fixtures.get(index);
                    return DynamicTest.dynamicTest(
                            fixture.displayName(),
                            () -> executeFixture(fixture, index, currentSummary)
                    );
                });
    }

    /// Emits the completed summary and writes it when the Gradle task configured a report directory.
    ///
    /// @throws IOException if the configured report cannot be written
    @AfterAll
    void reportsSummary() throws IOException {
        @Nullable SassSpecSummary currentSummary = summary;
        if (currentSummary == null) {
            return;
        }

        System.out.println(currentSummary.completedLine());
        @Nullable String reportDirectory = System.getProperty(REPORT_DIRECTORY_PROPERTY);
        if (reportDirectory != null && !reportDirectory.isBlank()) {
            currentSummary.writeReport(Path.of(reportDirectory));
        }
    }

    /// Reads, parses, and completely classifies all selected archive fixtures.
    ///
    /// @return immutable resolved fixtures in manifest order
    /// @throws IOException if a classpath resource cannot be read or parsed
    private static List<ResolvedFixture> loadFixtures() throws IOException {
        SassSpecManifest manifest = SassSpecManifest.parse(
                readResource(MANIFEST_RESOURCE),
                MANIFEST_RESOURCE
        );
        var fixtures = new ArrayList<ResolvedFixture>();
        for (SassSpecManifest.Archive archiveDeclaration : manifest.archives()) {
            String resource = RESOURCE_ROOT + archiveDeclaration.path();
            HrxArchive archive = HrxArchive.parse(readResource(resource), resource);
            verifyClassification(archiveDeclaration, archive);
            for (SassSpecManifest.Case fixture : archiveDeclaration.cases()) {
                fixtures.add(new ResolvedFixture(archiveDeclaration.path(), archive, fixture));
            }
        }
        return List.copyOf(fixtures);
    }

    /// Ensures that every archive directory containing an input stylesheet has exactly one action.
    ///
    /// @param declaration the archive declaration from the manifest
    /// @param archive the parsed virtual archive
    /// @throws IllegalArgumentException if the manifest silently omits or invents fixture cases
    private static void verifyClassification(
            SassSpecManifest.Archive declaration,
            HrxArchive archive
    ) {
        var discovered = new TreeSet<String>();
        for (String path : archive.paths()) {
            if (!path.endsWith("/input.scss")
                    && !path.endsWith("/input.sass")
                    && !path.endsWith("/input.css")) {
                continue;
            }
            int separator = path.lastIndexOf('/');
            if (separator <= 0) {
                throw new IllegalArgumentException(
                        "sass-spec input stylesheets must be located in a case directory: " + path
                );
            }
            discovered.add(path.substring(0, separator));
        }

        var declared = new TreeSet<String>();
        for (SassSpecManifest.Case fixture : declaration.cases()) {
            declared.add(fixture.directory());
        }
        if (!discovered.equals(declared)) {
            throw new IllegalArgumentException(
                    "sass-spec manifest classification does not match archive " + declaration.path() +
                            ": discovered=" + discovered + " declared=" + declared
            );
        }
    }

    /// Executes one fixture or aborts it through its explicit skip classification.
    ///
    /// @param resolved the parsed archive and selected fixture
    /// @param index the stable fixture index used for a temporary directory
    /// @param currentSummary the summary to update
    /// @throws Throwable if an executable fixture fails its expectation
    private void executeFixture(
            ResolvedFixture resolved,
            int index,
            SassSpecSummary currentSummary
    ) throws Throwable {
        SassSpecManifest.Case fixture = resolved.fixture();
        if (fixture.action() == SassSpecManifest.Action.SKIP) {
            currentSummary.recordSkipped(fixture.category());
            String reason = Objects.requireNonNull(fixture.reason(), "skip reason");
            Assumptions.assumeTrue(false, reason);
            return;
        }

        try {
            Path suiteRoot = suiteRootFor(resolved);
            String mountPrefix = archiveMountPrefix(
                    resolved.archiveResource(),
                    resolved.archive()
            );
            Path caseRoot = suiteRoot.resolve(mountPrefix)
                    .resolve(resolved.fixture().directory())
                    .normalize();
            Path input = selectInput(caseRoot, resolved.displayName());
            assertFixture(resolved, suiteRoot, caseRoot, input);
            currentSummary.recordPassed();
        } catch (Throwable failure) {
            currentSummary.recordFailed();
            throw failure;
        }
    }

    /// Returns a cached suite root with one HRX archive mounted at its logical path.
    ///
    /// Upstream sass-spec HRX paths are relative to the archive file location under
    /// {@code spec/}. Materializing {@code callable/arguments.hrx}'s
    /// {@code mixin/_utils.scss} as {@code callable/arguments/mixin/_utils.scss}
    /// under the suite root allows {@code @use "callable/arguments/mixin/utils"}
    /// to resolve through a single load path.
    ///
    /// @param resolved the parsed archive and selected fixture
    /// @return the suite root used as a compiler load path
    /// @throws IOException if a source file cannot be materialized
    private Path suiteRootFor(ResolvedFixture resolved) throws IOException {
        @Nullable Path existing = materializedArchives.get(resolved.archiveResource());
        if (existing != null) {
            return existing;
        }
        Path suiteRoot = Files.createDirectories(
                temporaryDirectory.resolve("suite-" + materializedArchives.size())
        );
        String mountPrefix = archiveMountPrefix(
                resolved.archiveResource(),
                resolved.archive()
        );
        for (Map.Entry<String, String> entry : resolved.archive().files().entrySet()) {
            String archivePath = entry.getKey();
            if (isExpectationArchivePath(archivePath)) {
                continue;
            }
            String mountedPath = mountPrefix.isEmpty()
                    ? archivePath
                    : mountPrefix + "/" + archivePath;
            writeSuiteFile(suiteRoot, mountedPath, entry.getValue());
        }
        copySupportStylesheets(suiteRoot);
        materializedArchives.put(resolved.archiveResource(), suiteRoot);
        return suiteRoot;
    }

    /// Copies plain SCSS support files needed for cross-path {@code @use} resolution.
    private static void copySupportStylesheets(Path suiteRoot) throws IOException {
        copyResourceIfPresent(suiteRoot, "upstream/core_functions/color/_utils.scss");
        copyResourceIfPresent(suiteRoot, "upstream/core_functions/list/_utils.scss");
        copyResourceIfPresent(
                suiteRoot,
                "upstream/core_functions/color/hwb/three_args/w3c/_test-hue.scss"
        );
    }

    /// Copies one classpath resource into the suite root under its logical path.
    private static void copyResourceIfPresent(Path suiteRoot, String resourcePath) throws IOException {
        String resource = "sass-spec/" + resourcePath;
        try (InputStream stream = SassSpecRunnerTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return;
            }
            String logical = resourcePath.startsWith("upstream/")
                    ? resourcePath.substring("upstream/".length())
                    : resourcePath;
            writeSuiteFile(
                    suiteRoot,
                    logical,
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
            );
        }
    }

    /// Writes one suite file under a safe relative path.
    private static void writeSuiteFile(Path suiteRoot, String relativePath, String content)
            throws IOException {
        Path target = suiteRoot.resolve(relativePath).normalize();
        if (!target.startsWith(suiteRoot)) {
            throw new IllegalArgumentException("Unsafe materialized fixture path: " + relativePath);
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    /// Returns the logical mount prefix for an archive resource path.
    ///
    /// {@code curated.hrx} mounts at the suite root. Upstream archives under
    /// {@code upstream/} mount at their path relative to {@code upstream/}
    /// without the {@code .hrx} suffix.
    ///
    /// @param archiveResource the classpath-relative archive path
    /// @return the slash-separated mount prefix, possibly empty
    /// Mounts an HRX at its parent when case paths already include the stem.
    ///
    /// @param archiveResource the archive resource path
    /// @param archive         the parsed archive
    /// @return the suite-relative mount prefix
    private static String archiveMountPrefix(String archiveResource, HrxArchive archive) {
        String path = archiveResource;
        if (path.startsWith("upstream/")) {
            path = path.substring("upstream/".length());
        }
        if (path.endsWith(".hrx")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.equals("curated")) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String stem = slash >= 0 ? path.substring(slash + 1) : path;
        String prefix = stem + "/";
        boolean allPrefixed = !archive.files().isEmpty();
        for (String archivePath : archive.files().keySet()) {
            if (isExpectationArchivePath(archivePath)) {
                continue;
            }
            if (!archivePath.equals(stem) && !archivePath.startsWith(prefix)) {
                allPrefixed = false;
                break;
            }
        }
        if (allPrefixed && slash >= 0) {
            return path.substring(0, slash);
        }
        return path;
    }

    /// Selects the unique input stylesheet under one case directory.
    ///
    /// @param caseRoot the materialized case directory
    /// @param displayName the fixture label used in failures
    /// @return the unique input file
    private static Path selectInput(Path caseRoot, String displayName) {
        Path scssInput = caseRoot.resolve("input.scss");
        Path sassInput = caseRoot.resolve("input.sass");
        Path cssInput = caseRoot.resolve("input.css");
        int inputCount = 0;
        @Nullable Path selected = null;
        if (Files.isRegularFile(scssInput)) {
            inputCount++;
            selected = scssInput;
        }
        if (Files.isRegularFile(sassInput)) {
            inputCount++;
            selected = sassInput;
        }
        if (Files.isRegularFile(cssInput)) {
            inputCount++;
            selected = cssInput;
        }
        if (inputCount != 1 || selected == null) {
            throw new IllegalArgumentException(
                    "Executable sass-spec fixtures must provide exactly one input.scss, input.sass, or input.css: " +
                            displayName
            );
        }
        return selected;
    }

    /// Determines whether a virtual archive path is metadata or a compiler expectation.
    ///
    /// @param archivePath the archive-relative virtual file path
    /// @return whether the file must stay out of the materialized source tree
    private static boolean isExpectationArchivePath(String archivePath) {
        int separator = archivePath.lastIndexOf('/');
        String name = separator < 0 ? archivePath : archivePath.substring(separator + 1);
        return name.equals("options.yml") ||
                name.equals("scssfx-expect.json") ||
                name.equals("error") ||
                name.startsWith("error-") ||
                name.equals("warning") ||
                name.startsWith("warning-") ||
                name.equals("output.css") ||
                (name.startsWith("output-") && name.endsWith(".css"));
    }

    /// Compares an executable fixture against either its CSS output or primary error expectation.
    ///
    /// @param resolved the parsed archive and selected fixture
    /// @param archiveRoot the materialized archive root used as a load path
    /// @param caseRoot the materialized case directory
    /// @param input the materialized input stylesheet
    /// @throws IOException if compilation cannot read a materialized file
    /// @throws SassCompilationException if a success fixture cannot compile
    private static void assertFixture(
            ResolvedFixture resolved,
            Path archiveRoot,
            Path caseRoot,
            Path input
    ) throws IOException, SassCompilationException {
        @Nullable String expectedOutput = preferredContent(resolved, "output-scssfx.css", "output.css");
        @Nullable String expectedError = preferredContent(resolved, "error-scssfx", "error");
        if ((expectedOutput == null) == (expectedError == null)) {
            throw new IllegalArgumentException(
                    "Executable sass-spec fixtures must define exactly one output or error expectation: " +
                            resolved.displayName()
            );
        }

        var options = new CompileOptions(false, List.of(archiveRoot));
        if (expectedOutput != null) {
            CompileResult<String> result = new SassCompiler().compile(
                    SassSource.fromFile(input),
                    CssTarget.DEFAULT,
                    options
            );
            assertEquals(
                    normalizeCss(expectedOutput),
                    normalizeCss(result.output()),
                    "CSS output for " + resolved.displayName()
            );
            assertDiagnostics(resolved, result.diagnostics());
            assertLoadedUrls(resolved, caseRoot, result.loadedUrls());
            return;
        }

        SassSpecManifest.Case fixture = resolved.fixture();
        if (!fixture.loadedUrls().isEmpty() || !fixture.diagnostics().isEmpty()) {
            throw new IllegalArgumentException(
                    "Error fixtures must not declare successful-compilation expectations: " + resolved.displayName()
            );
        }
        String expectedMessage = extractErrorMessage(Objects.requireNonNull(expectedError, "expected error"));
        SassCompilationException failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(input),
                        CssTarget.DEFAULT,
                        options
                ),
                "Compilation error for " + resolved.displayName()
        );
        assertEquals(DiagnosticSeverity.ERROR, failure.primaryDiagnostic().severity());
        assertEquals(expectedMessage, failure.primaryDiagnostic().message());
    }

    /// Returns the project-specific expectation when available, otherwise the generic expectation.
    ///
    /// @param resolved the parsed archive and selected fixture
    /// @param preferredName the project-specific expectation file name
    /// @param fallbackName the generic expectation file name
    /// @return the selected contents, or {@code null} when neither file exists
    private static @Nullable String preferredContent(
            ResolvedFixture resolved,
            String preferredName,
            String fallbackName
    ) {
        String prefix = resolved.fixture().directory() + "/";
        @Nullable String preferred = resolved.archive().content(prefix + preferredName);
        if (preferred != null) {
            return preferred;
        }
        return resolved.archive().content(prefix + fallbackName);
    }

    /// Verifies structured diagnostics when the manifest declares expectations.
    ///
    /// Archives without explicit diagnostic entries accept any non-error
    /// diagnostics so full-suite imports can focus on CSS and primary errors.
    ///
    /// @param resolved the fixture whose diagnostic expectations apply
    /// @param actual the compiler diagnostics in reporting order
    private static void assertDiagnostics(ResolvedFixture resolved, List<Diagnostic> actual) {
        List<SassSpecManifest.DiagnosticExpectation> expected = resolved.fixture().diagnostics();
        if (expected.isEmpty()) {
            return;
        }
        assertEquals(expected.size(), actual.size(), "Diagnostic count for " + resolved.displayName());
        for (int index = 0; index < expected.size(); index++) {
            SassSpecManifest.DiagnosticExpectation expectedDiagnostic = expected.get(index);
            Diagnostic actualDiagnostic = actual.get(index);
            assertEquals(
                    expectedDiagnostic.severity(),
                    actualDiagnostic.severity(),
                    "Diagnostic severity for " + resolved.displayName() + " at index " + index
            );
            @Nullable String expectedCode = expectedDiagnostic.code();
            if (expectedCode != null) {
                assertEquals(
                        expectedCode,
                        actualDiagnostic.code(),
                        "Diagnostic code for " + resolved.displayName() + " at index " + index
                );
            }
            @Nullable String expectedMessage = expectedDiagnostic.message();
            if (expectedMessage != null) {
                assertEquals(
                        expectedMessage,
                        actualDiagnostic.message(),
                        "Diagnostic message for " + resolved.displayName() + " at index " + index
                );
            }
        }
    }

    /// Verifies loaded URLs when the manifest declares an explicit expectation set.
    ///
    /// @param resolved the fixture whose URL expectations apply
    /// @param caseRoot the materialized case root
    /// @param loadedUrls the compiler-reported canonical URLs
    /// @throws IOException if canonical URL paths cannot be normalized
    private static void assertLoadedUrls(
            ResolvedFixture resolved,
            Path caseRoot,
            Set<URI> loadedUrls
    ) throws IOException {
        if (resolved.fixture().loadedUrls().isEmpty()) {
            return;
        }
        var actual = new TreeSet<String>();
        for (URI loadedUrl : loadedUrls) {
            if (!"file".equalsIgnoreCase(loadedUrl.getScheme())) {
                throw new AssertionError("Expected a file URL but found " + loadedUrl + ".");
            }
            Path loadedPath = Path.of(loadedUrl).toAbsolutePath().normalize();
            Path normalizedRoot = caseRoot.toAbsolutePath().normalize();
            if (!loadedPath.startsWith(normalizedRoot)) {
                throw new AssertionError("Loaded URL escaped the fixture root: " + loadedUrl + ".");
            }
            actual.add(normalizePath(normalizedRoot.relativize(loadedPath)));
        }

        assertEquals(
                new TreeSet<>(resolved.fixture().loadedUrls()),
                actual,
                "Loaded URLs for " + resolved.displayName()
        );
    }

    /// Extracts the primary error message from an HRX {@code error} fixture file.
    ///
    /// @param expectedError the complete expected error text
    /// @return the text following the first {@code Error: } line prefix
    /// @throws IllegalArgumentException if no primary error line is present
    /// Extracts the primary diagnostic text from a sass-spec {@code error} file.
    ///
    /// Multi-line messages continue until the source-span dump that begins with
    /// a line equal to {@code "  ,"} (two spaces and a comma), matching dart-sass
    /// formatted exception layout.
    private static String extractErrorMessage(String expectedError) {
        String[] lines = normalizeLineEndings(expectedError).split("\n", -1);
        for (var index = 0; index < lines.length; index++) {
            if (!lines[index].startsWith("Error: ")) {
                continue;
            }
            var message = new StringBuilder(lines[index].substring("Error: ".length()));
            for (var next = index + 1; next < lines.length; next++) {
                var line = lines[next];
                // Span dumps begin with two- or four-space ", " leaders (single-span
                // vs multi-span dart-sass layouts), ",-->", or box-drawing markers.
                if (isErrorSpanDumpLine(line)) {
                    break;
                }
                message.append('\n').append(line);
            }
            return message.toString().stripTrailing();
        }
        throw new IllegalArgumentException("sass-spec error expectations must contain an 'Error: ' line.");
    }

    /// Returns whether a line starts a dart-sass source-span dump rather than
    /// continuing the primary error message.
    ///
    /// @param line one line of an {@code error} expectation file
    /// @return whether the line begins a span dump
    private static boolean isErrorSpanDumpLine(String line) {
        if (line.startsWith("  ┌")
                || line.startsWith("  ╷")
                || line.startsWith("  ╔")
                || line.startsWith("  ,-->")
                || line.startsWith("    ,-->")) {
            return true;
        }
        // Span dumps use a comma leader whose indent grows with the line-number
        // column width: two spaces for single-digit lines ("  ,"), three for
        // double-digit lines ("   ,"), four for deeper multi-span layouts.
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index >= 2
                && index <= 4
                && index < line.length()
                && line.charAt(index) == ',';
    }

    /// Normalizes transport-level line endings and trailing blank lines.
    ///
    /// Upstream sass-spec {@code output.css} files commonly end with one or more
    /// blank lines; the compiler emits a single stylesheet without a trailing
    /// blank line. Only trailing whitespace is discarded so interior formatting
    /// remains part of the assertion.
    ///
    /// @param css the CSS text to normalize
    /// @return CSS with normalized line terminators and no trailing blank lines
    private static String normalizeCss(String css) {
        String normalized = normalizeLineEndings(css);
        int end = normalized.length();
        while (end > 0) {
            char ch = normalized.charAt(end - 1);
            if (ch == '\n' || ch == ' ' || ch == '\t') {
                end--;
                continue;
            }
            break;
        }
        return normalized.substring(0, end);
    }

    /// Converts CRLF and CR line separators to LF without changing other whitespace.
    ///
    /// @param text the text to normalize
    /// @return text with LF line separators
    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /// Converts a filesystem-relative path to portable slash-separated fixture notation.
    ///
    /// @param path the relative filesystem path
    /// @return the portable fixture path
    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    /// Reads one UTF-8 classpath resource.
    ///
    /// @param resource the resource path
    /// @return the complete UTF-8 text
    /// @throws IOException if the resource is missing or cannot be read
    private static String readResource(String resource) throws IOException {
        ClassLoader classLoader = SassSpecRunnerTest.class.getClassLoader();
        @Nullable InputStream input = classLoader.getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("Missing sass-spec resource: " + resource);
        }
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /// Combines one archive declaration with its parsed virtual file tree and selected case.
    ///
    /// @param archiveResource the manifest-relative HRX archive resource path
    /// @param archive the parsed virtual file tree
    /// @param fixture the selected fixture declaration
    @NotNullByDefault
    private record ResolvedFixture(
            String archiveResource,
            HrxArchive archive,
            SassSpecManifest.Case fixture
    ) {
        /// Creates one resolved fixture.
        private ResolvedFixture {
            Objects.requireNonNull(archiveResource, "archiveResource");
            Objects.requireNonNull(archive, "archive");
            Objects.requireNonNull(fixture, "fixture");
        }

        /// Returns a stable JUnit display name for the virtual fixture.
        ///
        /// @return the archive and fixture directory separated by a colon
        private String displayName() {
            return archiveResource + ":" + fixture.directory();
        }
    }
}
