// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.sassspec;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CompileResult;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serial;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Runs the full imported sass-spec corpus outside JUnit for faster batch reporting.
@NotNullByDefault
public final class SassSpecBatchMain {
    private static final String RESOURCE_ROOT = "sass-spec/";
    private static final String MANIFEST_RESOURCE = RESOURCE_ROOT + "index.json";

    /// Maximum wall time allowed for one fixture before it is recorded as a failure.
    private static final long CASE_TIMEOUT_SECONDS = 5L;

    private SassSpecBatchMain() {
    }

    /// Executes every manifest fixture and writes a JSON summary.
    ///
    /// @param args optional {@code --report-dir <path>}
    /// @throws Exception if setup fails
    public static void main(String[] args) throws Exception {
        Path reportDir = Path.of("build/reports/sass-spec");
        for (var index = 0; index < args.length; index++) {
            if ("--report-dir".equals(args[index]) && index + 1 < args.length) {
                reportDir = Path.of(args[index + 1]);
            }
        }
        Files.createDirectories(reportDir);

        var fixtures = loadFixtures();
        Path temporaryDirectory = Files.createTempDirectory("sassfx-sass-spec-batch-");
        Path suiteRoot = materializeSharedSuiteRoot(fixtures, temporaryDirectory);
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int upstreamTotal = 0;
        int upstreamPassed = 0;
        int upstreamFailed = 0;
        int ownedTotal = 0;
        int ownedPassed = 0;
        int ownedFailed = 0;
        int outputPassed = 0;
        int outputFailed = 0;
        int diagnosticPassed = 0;
        int diagnosticFailed = 0;
        var failures = new ArrayList<String>();
        var byCategory = new LinkedHashMap<String, int[]>();

        System.out.println("sass-spec batch: total=" + fixtures.size());
        // Cached pool abandons stuck case threads after timeout so later fixtures
        // can continue; hung extend cases may still burn CPU in the background.
        ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            var thread = new Thread(runnable, "sass-spec-batch-case");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (var index = 0; index < fixtures.size(); index++) {
                var resolved = fixtures.get(index);
                var fixture = resolved.fixture();
                byCategory.computeIfAbsent(fixture.category(), key -> new int[3]);
                boolean upstream = isUpstreamArchive(resolved.archiveResource());
                if (fixture.action() == SassSpecManifest.Action.SKIP) {
                    skipped++;
                    byCategory.get(fixture.category())[2]++;
                    continue;
                }
                if (upstream) {
                    upstreamTotal++;
                } else {
                    ownedTotal++;
                }
                boolean expectsOutput = resolved.archive().content(
                        fixture.directory() + "/output.css"
                ) != null;
                try {
                    String mountPrefix = archiveMountPrefix(
                            resolved.archiveResource(),
                            resolved.archive()
                    );
                    Path caseRoot = suiteRoot.resolve(mountPrefix)
                            .resolve(fixture.directory())
                            .normalize();
                    Path input = selectInput(caseRoot);
                    Future<?> future = executor.submit(() -> {
                        try {
                            runOne(resolved, suiteRoot, caseRoot, input);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    });
                    try {
                        future.get(CASE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (TimeoutException timeout) {
                        future.cancel(true);
                        throw new RuntimeException(
                                "Timed out after " + CASE_TIMEOUT_SECONDS + "s",
                                timeout
                        );
                    } catch (ExecutionException execution) {
                        Throwable cause = execution.getCause();
                        if (cause instanceof RuntimeException runtime
                                && runtime.getCause() instanceof Exception nested) {
                            throw nested;
                        }
                        if (cause instanceof Exception exception) {
                            throw exception;
                        }
                        throw execution;
                    }
                    passed++;
                    byCategory.get(fixture.category())[0]++;
                    if (upstream) {
                        upstreamPassed++;
                    } else {
                        ownedPassed++;
                    }
                    if (expectsOutput) {
                        outputPassed++;
                    } else {
                        diagnosticPassed++;
                    }
                } catch (Throwable failure) {
                    failed++;
                    byCategory.get(fixture.category())[1]++;
                    if (upstream) {
                        upstreamFailed++;
                    } else {
                        ownedFailed++;
                    }
                    Throwable root = unwrap(failure);
                    if (root instanceof OutputMismatch
                            || (expectsOutput && !(root instanceof DiagnosticMismatch))) {
                        outputFailed++;
                    } else {
                        diagnosticFailed++;
                    }
                    String message = failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage();
                    String line = resolved.displayName() + " :: " + message.replace('\n', ' ');
                    failures.add(line);
                    if (failed <= 50 || failed % 200 == 0) {
                        System.out.println("FAIL " + line);
                    }
                }
                if ((index + 1) % 500 == 0) {
                    System.out.println(
                            "progress " + (index + 1) + "/" + fixtures.size()
                                    + " passed=" + passed + " failed=" + failed
                                    + " skipped=" + skipped
                    );
                    System.out.flush();
                }
            }
        } finally {
            executor.shutdownNow();
        }

        int total = fixtures.size();
        int enabled = total - skipped;
        double compatibility = enabled == 0 ? 1.0 : (double) passed / enabled;
        double upstreamCompat = (upstreamPassed + upstreamFailed) == 0
                ? 0.0
                : (double) upstreamPassed / (upstreamPassed + upstreamFailed);
        double ownedCompat = (ownedPassed + ownedFailed) == 0
                ? 0.0
                : (double) ownedPassed / (ownedPassed + ownedFailed);
        double outputCompat = (outputPassed + outputFailed) == 0
                ? 0.0
                : (double) outputPassed / (outputPassed + outputFailed);
        double diagnosticCompat = (diagnosticPassed + diagnosticFailed) == 0
                ? 0.0
                : (double) diagnosticPassed / (diagnosticPassed + diagnosticFailed);
        var summary = new StringBuilder();
        summary.append('{')
                .append("\"total\":").append(total).append(',')
                .append("\"enabled\":").append(enabled).append(',')
                .append("\"passed\":").append(passed).append(',')
                .append("\"failed\":").append(failed).append(',')
                .append("\"skipped\":").append(skipped).append(',')
                .append("\"compatibility\":").append(compatibility).append(',')
                .append("\"coverage\":").append(compatibility).append(',')
                .append("\"upstream\":{")
                .append("\"total\":").append(upstreamTotal).append(',')
                .append("\"passed\":").append(upstreamPassed).append(',')
                .append("\"failed\":").append(upstreamFailed).append(',')
                .append("\"compatibility\":").append(upstreamCompat)
                .append("},")
                .append("\"owned\":{")
                .append("\"total\":").append(ownedTotal).append(',')
                .append("\"passed\":").append(ownedPassed).append(',')
                .append("\"failed\":").append(ownedFailed).append(',')
                .append("\"compatibility\":").append(ownedCompat)
                .append("},")
                .append("\"output\":{")
                .append("\"passed\":").append(outputPassed).append(',')
                .append("\"failed\":").append(outputFailed).append(',')
                .append("\"compatibility\":").append(outputCompat)
                .append("},")
                .append("\"diagnostic\":{")
                .append("\"passed\":").append(diagnosticPassed).append(',')
                .append("\"failed\":").append(diagnosticFailed).append(',')
                .append("\"compatibility\":").append(diagnosticCompat)
                .append("},")
                .append("\"skippedByCategory\":{");
        boolean first = true;
        for (var entry : byCategory.entrySet()) {
            if (entry.getValue()[2] == 0) {
                continue;
            }
            if (!first) {
                summary.append(',');
            }
            first = false;
            summary.append('"').append(jsonEscape(entry.getKey())).append("\":").append(entry.getValue()[2]);
        }
        summary.append("}}");
        Files.writeString(reportDir.resolve("summary.json"), summary.toString(), StandardCharsets.UTF_8);
        Files.write(reportDir.resolve("failures.txt"), failures, StandardCharsets.UTF_8);
        System.out.println(
                "sass-spec batch complete: total=" + total
                        + " passed=" + passed
                        + " failed=" + failed
                        + " skipped=" + skipped
                        + " upstreamCompat=" + String.format(java.util.Locale.ROOT, "%.4f", upstreamCompat)
                        + " ownedCompat=" + String.format(java.util.Locale.ROOT, "%.4f", ownedCompat)
                        + " outputCompat=" + String.format(java.util.Locale.ROOT, "%.4f", outputCompat)
                        + " diagnosticCompat=" + String.format(java.util.Locale.ROOT, "%.4f", diagnosticCompat)
        );
        if (failed != 0) {
            System.exit(1);
        }
    }

    private static void runOne(
            ResolvedFixture resolved,
            Path suiteRoot,
            Path caseRoot,
            Path input
    ) throws Exception {
        assertNoSassFXOverrides(resolved);
        String prefix = resolved.fixture().directory() + "/";
        @Nullable String expectedOutput = resolved.archive().content(prefix + "output.css");
        @Nullable String expectedError = resolved.archive().content(prefix + "error");
        if ((expectedOutput == null) == (expectedError == null)) {
            throw new IllegalArgumentException("Fixture must define exactly one of output/error");
        }
        var options = CompileOptions.DEFAULT.withLoadPaths(List.of(suiteRoot));
        boolean upstream = isUpstreamArchive(resolved.archiveResource());
        if (expectedOutput != null) {
            CompileResult<String> result = new SassCompiler().compile(
                    SassSource.fromFile(input),
                    CssTarget.DEFAULT,
                    options
            );
            String expected = CssOutputCompare.normalize(expectedOutput);
            String actual = CssOutputCompare.normalize(result.output());
            if (!CssOutputCompare.equals(expectedOutput, result.output())) {
                throw new OutputMismatch(upstream,
                        "CSS mismatch expected=<<<" + expected + ">>> actual=<<<" + actual + ">>>");
            }
            return;
        }
        String expectedMessage = extractErrorMessage(Objects.requireNonNull(expectedError));
        try {
            new SassCompiler().compile(SassSource.fromFile(input), CssTarget.DEFAULT, options);
            throw new DiagnosticMismatch(upstream, "Expected compilation failure");
        } catch (SassCompilationException failure) {
            if (failure.primaryDiagnostic().severity() != DiagnosticSeverity.ERROR) {
                throw new DiagnosticMismatch(upstream, "Primary diagnostic was not ERROR");
            }
            String actual = failure.primaryDiagnostic().message();
            if (!expectedMessage.equals(actual)) {
                throw new DiagnosticMismatch(upstream,
                        "Error mismatch expected=<<<" + expectedMessage + ">>> actual=<<<" + actual + ">>>");
            }
        }
    }

    /// Rejects project-local expectation overrides in imported fixtures.
    private static void assertNoSassFXOverrides(ResolvedFixture resolved) {
        String prefix = resolved.fixture().directory() + "/";
        for (String name : List.of(
                "output-sassfx.css",
                "error-sassfx",
                "sassfx-expect.json"
        )) {
            if (resolved.archive().content(prefix + name) != null) {
                throw new IllegalStateException(
                        "sassfx expectation overrides are forbidden: "
                                + resolved.displayName() + "/" + name
                );
            }
        }
        for (String path : resolved.archive().paths()) {
            if (!path.startsWith(prefix)) {
                continue;
            }
            String base = path.substring(path.lastIndexOf('/') + 1);
            if (base.startsWith("output-sassfx")
                    || base.startsWith("error-sassfx")
                    || base.equals("sassfx-expect.json")) {
                throw new IllegalStateException(
                        "sassfx expectation overrides are forbidden: " + path
                );
            }
        }
    }

    /// Returns whether an archive path is the upstream sass-spec pin corpus.
    private static boolean isUpstreamArchive(String archiveResource) {
        return archiveResource.startsWith("upstream/");
    }

    /// Unwraps executor wrappers to the assertion root.
    private static Throwable unwrap(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null
                && !(root instanceof OutputMismatch)
                && !(root instanceof DiagnosticMismatch)
                && (root instanceof RuntimeException || root instanceof java.util.concurrent.ExecutionException)) {
            root = root.getCause();
        }
        return root;
    }

    /// Marks an output-CSS assertion failure with corpus ownership.
    private static final class OutputMismatch extends AssertionError {
        /// The serialization format version.
        @Serial
        private static final long serialVersionUID = 1L;

        /// Records whether the fixture belongs to the upstream corpus.
        private final boolean upstream;

        /// Creates an output mismatch.
        ///
        /// @param upstream whether the fixture belongs to the upstream corpus
        /// @param message the assertion detail
        private OutputMismatch(boolean upstream, String message) {
            super(message);
            this.upstream = upstream;
        }

        /// Returns whether the fixture belongs to the upstream corpus.
        ///
        /// @return whether the fixture is upstream
        private boolean upstream() {
            return upstream;
        }
    }

    /// Marks a diagnostic assertion failure with corpus ownership.
    private static final class DiagnosticMismatch extends AssertionError {
        /// The serialization format version.
        @Serial
        private static final long serialVersionUID = 1L;

        /// Records whether the fixture belongs to the upstream corpus.
        private final boolean upstream;

        /// Creates a diagnostic mismatch.
        ///
        /// @param upstream whether the fixture belongs to the upstream corpus
        /// @param message the assertion detail
        private DiagnosticMismatch(boolean upstream, String message) {
            super(message);
            this.upstream = upstream;
        }

        /// Returns whether the fixture belongs to the upstream corpus.
        ///
        /// @return whether the fixture is upstream
        private boolean upstream() {
            return upstream;
        }
    }

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
                fixtures.add(new ResolvedFixture(
                        archiveDeclaration.path(),
                        archive,
                        SassSpecDartSassTodos.apply(archive, fixture)
                ));
            }
        }
        return List.copyOf(fixtures);
    }

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
                    "sass-spec manifest classification does not match archive " + declaration.path()
            );
        }
    }

    /// Materializes every imported HRX archive and plain support stylesheet under one
    /// suite root so cross-archive {@code @use} load paths resolve.
    private static Path materializeSharedSuiteRoot(
            List<ResolvedFixture> fixtures,
            Path temporaryDirectory
    ) throws IOException {
        Path suiteRoot = Files.createDirectories(temporaryDirectory.resolve("suite"));
        var seenArchives = new HashMap<String, Boolean>();
        for (ResolvedFixture resolved : fixtures) {
            if (seenArchives.put(resolved.archiveResource(), Boolean.TRUE) != null) {
                continue;
            }
            String mountPrefix = archiveMountPrefix(
                    resolved.archiveResource(),
                    resolved.archive()
            );
            for (Map.Entry<String, String> entry : resolved.archive().files().entrySet()) {
                String archivePath = entry.getKey();
                if (isExpectationArchivePath(archivePath)) {
                    continue;
                }
                String mountedPath = mountPrefix.isEmpty() ? archivePath : mountPrefix + "/" + archivePath;
                writeSuiteFile(suiteRoot, mountedPath, entry.getValue());
            }
        }
        copySupportStylesheets(suiteRoot);
        return suiteRoot;
    }

    /// Copies plain SCSS/Sass support files shipped beside upstream HRX archives.
    private static void copySupportStylesheets(Path suiteRoot) throws IOException {
        try (InputStream stream = SassSpecBatchMain.class.getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + "support-files.txt")) {
            if (stream == null) {
                // Fall back to known support files under the upstream resource tree.
                copyResourceIfPresent(suiteRoot, "upstream/core_functions/color/_utils.scss");
                copyResourceIfPresent(suiteRoot, "upstream/core_functions/list/_utils.scss");
                copyResourceIfPresent(
                        suiteRoot,
                        "upstream/core_functions/color/hwb/three_args/w3c/_test-hue.scss"
                );
                return;
            }
            String listing = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : listing.split("\n")) {
                String path = line.trim();
                if (!path.isEmpty() && !path.startsWith("#")) {
                    copyResourceIfPresent(suiteRoot, path);
                }
            }
        }
    }

    /// Copies one classpath resource into the suite root under its logical path.
    private static void copyResourceIfPresent(Path suiteRoot, String resourcePath) throws IOException {
        String resource = RESOURCE_ROOT + resourcePath;
        try (InputStream stream = SassSpecBatchMain.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return;
            }
            String logical = resourcePath.startsWith("upstream/")
                    ? resourcePath.substring("upstream/".length())
                    : resourcePath;
            writeSuiteFile(suiteRoot, logical, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /// Writes one suite file under a safe relative path.
    private static void writeSuiteFile(Path suiteRoot, String relativePath, String content)
            throws IOException {
        Path target = suiteRoot.resolve(relativePath).normalize();
        if (!target.startsWith(suiteRoot)) {
            throw new IllegalArgumentException("Unsafe path: " + relativePath);
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    /// Computes the suite-root mount prefix for one HRX archive.
    ///
    /// When every case path inside the archive is already prefixed with the
    /// archive stem (for example {@code reds.hrx} containing {@code reds/input.scss}),
    /// the mount is the parent directory so relative {@code @use} paths such as
    /// {@code ../test-hue} resolve next to sibling support files.
    ///
    /// @param archiveResource the archive resource path
    /// @param archive         the parsed archive contents
    /// @return the mount prefix relative to the suite root
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

    private static Path selectInput(Path caseRoot) {
        Path scssInput = caseRoot.resolve("input.scss");
        Path sassInput = caseRoot.resolve("input.sass");
        Path cssInput = caseRoot.resolve("input.css");
        int count = 0;
        @Nullable Path selected = null;
        if (Files.isRegularFile(scssInput)) {
            count++;
            selected = scssInput;
        }
        if (Files.isRegularFile(sassInput)) {
            count++;
            selected = sassInput;
        }
        if (Files.isRegularFile(cssInput)) {
            count++;
            selected = cssInput;
        }
        if (count != 1 || selected == null) {
            throw new IllegalArgumentException("Expected exactly one input stylesheet in " + caseRoot);
        }
        return selected;
    }

    private static boolean isExpectationArchivePath(String archivePath) {
        int separator = archivePath.lastIndexOf('/');
        String name = separator < 0 ? archivePath : archivePath.substring(separator + 1);
        // Project-local sassfx overrides are forbidden; only upstream expectation
        // names are recognized so accidental override files cannot shadow output.css.
        return name.equals("options.yml")
                || name.equals("error")
                || name.startsWith("error-")
                || name.equals("warning")
                || name.startsWith("warning-")
                || name.equals("output.css")
                || (name.startsWith("output-") && name.endsWith(".css") && !name.contains("sassfx"));
    }

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
        throw new IllegalArgumentException("Missing Error: line");
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
        if (index >= 2 && index <= 4 && index < line.length() && line.charAt(index) == ',') {
            return true;
        }
        return false;
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String readResource(String resource) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        @Nullable InputStream stream = loader.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("Missing resource: " + resource);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String jsonEscape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ResolvedFixture(
            String archiveResource,
            HrxArchive archive,
            SassSpecManifest.Case fixture
    ) {
        private String displayName() {
            return archiveResource + ":" + fixture.directory();
        }
    }
}
