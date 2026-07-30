// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.CompileResult;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.OutputTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassDependencyTracker;
import org.glavo.sassfx.SassFileSource;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.SassStringSource;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Compiles one planned CLI job and publishes its selected output.
///
/// The engine owns target dispatch, dependency freshness, source-map
/// preparation, and successful or failed output publication. Batch ordering
/// and watch-state coordination remain the caller's responsibility.
@NotNullByDefault
final class CliCompilationEngine {
    /// Contains settings shared by every job in this invocation.
    private final CliExecutionContext context;

    /// Contains the stateless compiler reused by this engine.
    private final SassCompiler compiler = new SassCompiler();

    /// Creates an engine for one resolved command-line invocation.
    ///
    /// @param context the immutable execution context
    CliCompilationEngine(CliExecutionContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /// Compiles and conditionally publishes one planned job.
    ///
    /// @param job the planned input and destination
    /// @param incremental whether a fresh destination may be skipped
    /// @param dependencyTracker tracker receiving import-resolution candidates
    /// @return the dependency and publication result
    /// @throws IOException if an input or output cannot be accessed
    /// @throws SassCompilationException if Sass compilation fails
    /// @throws IllegalStateException if the context contains an unsupported
    ///                               output-target implementation
    Result compile(
            CliCompilationPlan.Job job,
            boolean incremental,
            SassDependencyTracker dependencyTracker
    ) throws IOException, SassCompilationException {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(dependencyTracker, "dependencyTracker");

        SassSource source = job.source() == null
                ? new SassStringSource(
                        Objects.requireNonNull(context.stdinContents()),
                        job.syntax(),
                        context.stdinUrl()
                )
                : fileSource(job);
        var outputTarget = context.outputTarget();
        if (outputTarget instanceof CssTarget cssTarget) {
            return compileText(
                    source,
                    job.source(),
                    job.destination(),
                    cssTarget,
                    incremental,
                    job.source() == null ? context.stdinContents() : null,
                    dependencyTracker
            );
        }
        if (outputTarget instanceof JavaFXCssTarget javaFXCssTarget) {
            return compileText(
                    source,
                    job.source(),
                    job.destination(),
                    javaFXCssTarget,
                    incremental,
                    job.source() == null ? context.stdinContents() : null,
                    dependencyTracker
            );
        }
        if (outputTarget instanceof BssTarget bssTarget) {
            return compileBss(
                    source,
                    job.source(),
                    Objects.requireNonNull(job.destination()),
                    bssTarget,
                    incremental,
                    dependencyTracker
            );
        }
        throw new IllegalStateException(
                "Unsupported output target implementation: "
                        + outputTarget.getClass().getName()
        );
    }

    /// Applies error-CSS or failed-output deletion behavior.
    ///
    /// BSS failures never replace or delete the binary destination.
    ///
    /// @param destination the failed job destination, or `null`
    /// @param failure the Sass compilation failure
    /// @throws IOException if error output cannot be written
    void handleSassFailure(
            @Nullable Path destination,
            SassCompilationException failure
    ) throws IOException {
        Objects.requireNonNull(failure, "failure");
        if (context.outputTarget() instanceof BssTarget) {
            return;
        }
        if (!context.outputPolicy().errorCss()) {
            if (destination != null) {
                try {
                    Files.deleteIfExists(destination);
                } catch (IOException ignored) {
                    // Sass treats failed best-effort cleanup as part of the
                    // original compilation failure.
                }
            }
            return;
        }

        var css = CliErrorCss.format(
                failure,
                context.diagnosticPrinter()
        );
        if (destination == null) {
            context.out().println(css);
            if (context.out().checkError()) {
                throw new IOException("could not write standard output");
            }
        } else {
            CliFileWriter.writeString(destination, css + "\n");
        }
    }

    /// Returns file dependencies from one compiler result.
    ///
    /// @param loadedUrls canonical URLs loaded by the compiler
    /// @param sourcePath the explicit root path, or `null`
    /// @return immutable normalized file paths
    static @Unmodifiable Set<Path> fileDependencies(
            Set<URI> loadedUrls,
            @Nullable Path sourcePath
    ) {
        var dependencies = new LinkedHashSet<Path>();
        if (sourcePath != null) {
            dependencies.add(pathKey(sourcePath));
        }
        for (var url : loadedUrls) {
            if (!"file".equalsIgnoreCase(url.getScheme())) {
                continue;
            }
            try {
                dependencies.add(pathKey(Path.of(url)));
            } catch (IllegalArgumentException ignored) {
                // A malformed file URL is treated as an untracked dependency
                // by hasUntrackedDependencies().
            }
        }
        return Set.copyOf(dependencies);
    }

    /// Compiles a stylesheet and publishes textual output when required.
    ///
    /// Successful diagnostics are emitted only when the destination is not
    /// fresh or incremental operation is disabled.
    ///
    /// @param source the root stylesheet
    /// @param sourcePath the root file, or `null` for standard input
    /// @param destination the output file, or `null` for standard output
    /// @param outputTarget the resolved textual output target
    /// @param incremental whether a fresh destination may be skipped
    /// @param stdinContents the standard-input text, or `null` for file input
    /// @param dependencyTracker tracker receiving import-resolution candidates
    /// @return the dependency snapshot and publication status
    /// @throws IOException if an input or output cannot be accessed
    /// @throws SassCompilationException if evaluation or serialization fails
    private Result compileText(
            SassSource source,
            @Nullable Path sourcePath,
            @Nullable Path destination,
            OutputTarget<String> outputTarget,
            boolean incremental,
            @Nullable String stdinContents,
            SassDependencyTracker dependencyTracker
    ) throws IOException, SassCompilationException {
        CompileResult<String> result = compiler.compile(
                source,
                outputTarget,
                context.compileOptions(),
                dependencyTracker
        );
        var dependencies = fileDependencies(
                result.loadedUrls(),
                sourcePath
        );
        if (incremental
                && !hasUntrackedDependencies(result.loadedUrls())
                && !modifiedSince(destination, sourcePath, dependencies)) {
            return result(dependencies, dependencyTracker, false);
        }
        printNonErrorDiagnostics(result);

        var css = result.output();
        @Nullable CliSourceMap.Output preparedMap = null;
        if (context.outputPolicy().sourceMap()) {
            preparedMap = CliSourceMap.prepare(
                    Objects.requireNonNull(result.sourceMap()),
                    destination,
                    context.outputPolicy().sourceMapUrlMode(),
                    context.outputPolicy().embedSources(),
                    context.outputPolicy().embedSourceMap(),
                    context.stdinUrl(),
                    stdinContents
            );
            css += CliSourceMap.comment(
                    preparedMap.commentUrl(),
                    outputTarget instanceof CssTarget cssTarget
                            ? cssTarget.style() == OutputStyle.COMPRESSED
                            : ((JavaFXCssTarget) outputTarget).style()
                            == OutputStyle.COMPRESSED
            );
        }
        if (destination == null) {
            context.out().print(css);
            if (!css.isEmpty() && !css.endsWith("\n")) {
                context.out().println();
            }
            if (context.out().checkError()) {
                throw new IOException("could not write standard output");
            }
        } else {
            var text = css.endsWith("\n") ? css : css + "\n";
            if (preparedMap != null
                    && !context.outputPolicy().embedSourceMap()) {
                writeTextWithSourceMap(
                        destination,
                        text,
                        preparedMap.json()
                );
            } else {
                CliFileWriter.writeString(destination, text);
            }
        }
        return result(dependencies, dependencyTracker, true);
    }

    /// Compiles a stylesheet and publishes JavaFX BSS output when required.
    ///
    /// Successful diagnostics are emitted only when the destination is not
    /// fresh or incremental operation is disabled.
    ///
    /// @param source the root stylesheet
    /// @param sourcePath the root file
    /// @param destination the required BSS destination
    /// @param outputTarget the resolved binary output target
    /// @param incremental whether a fresh destination may be skipped
    /// @param dependencyTracker tracker receiving import-resolution candidates
    /// @return the dependency snapshot and publication status
    /// @throws IOException if an input or output cannot be accessed
    /// @throws SassCompilationException if evaluation or serialization fails
    private Result compileBss(
            SassSource source,
            @Nullable Path sourcePath,
            Path destination,
            BssTarget outputTarget,
            boolean incremental,
            SassDependencyTracker dependencyTracker
    ) throws IOException, SassCompilationException {
        var result = compiler.compile(
                source,
                outputTarget,
                context.compileOptions(),
                dependencyTracker
        );
        var dependencies = fileDependencies(
                result.loadedUrls(),
                sourcePath
        );
        if (incremental
                && !hasUntrackedDependencies(result.loadedUrls())
                && !modifiedSince(destination, sourcePath, dependencies)) {
            return result(dependencies, dependencyTracker, false);
        }
        printNonErrorDiagnostics(result);

        var bss = result.output().duplicate();
        var bytes = new byte[bss.remaining()];
        bss.get(bytes);
        CliFileWriter.write(destination, bytes);
        return result(dependencies, dependencyTracker, true);
    }

    /// Emits selected non-error diagnostics before output publication.
    ///
    /// @param result the completed compilation result
    private void printNonErrorDiagnostics(CompileResult<?> result) {
        if (context.quiet()) {
            return;
        }
        for (var diagnostic : result.diagnostics()) {
            if (diagnostic.severity() != DiagnosticSeverity.ERROR
                    && context.shouldPrint(diagnostic)) {
                context.err().println(
                        context.diagnosticPrinter().format(diagnostic)
                );
            }
        }
        context.err().flush();
    }

    /// Reports whether a result contains a mutable untracked dependency.
    ///
    /// Built-in `sass:` modules are immutable compiler resources. Other
    /// non-file URLs and malformed file URLs prevent a freshness decision.
    ///
    /// @param loadedUrls canonical URLs loaded by the compiler
    /// @return whether filesystem timestamps cannot prove the output fresh
    private static boolean hasUntrackedDependencies(Set<URI> loadedUrls) {
        for (var url : loadedUrls) {
            if ("sass".equalsIgnoreCase(url.getScheme())) {
                continue;
            }
            if (!"file".equalsIgnoreCase(url.getScheme())) {
                return true;
            }
            try {
                Path.of(url);
            } catch (IllegalArgumentException ignored) {
                return true;
            }
        }
        return false;
    }

    /// Reports whether an output is older than its root or a dependency.
    ///
    /// A missing path, standard-input source, unreadable timestamp, or missing
    /// destination requires publication.
    ///
    /// @param destination the output file, or `null`
    /// @param sourcePath the root file, or `null` for standard input
    /// @param dependencies normalized file dependencies
    /// @return whether the job must publish a new output
    private static boolean modifiedSince(
            @Nullable Path destination,
            @Nullable Path sourcePath,
            Set<Path> dependencies
    ) {
        if (destination == null || sourcePath == null) {
            return true;
        }

        final java.nio.file.attribute.FileTime outputTime;
        try {
            outputTime = Files.getLastModifiedTime(destination);
        } catch (IOException ignored) {
            return true;
        }
        for (var dependency : dependencies) {
            try {
                if (Files.getLastModifiedTime(dependency)
                        .compareTo(outputTime) > 0) {
                    return true;
                }
            } catch (IOException ignored) {
                return true;
            }
        }
        return false;
    }

    /// Creates an immutable job result from the completed dependency tracker.
    ///
    /// @param dependencies normalized file dependencies
    /// @param dependencyTracker completed import-resolution tracker
    /// @param compiled whether output was published rather than skipped
    /// @return the immutable result
    private static Result result(
            Set<Path> dependencies,
            SassDependencyTracker dependencyTracker,
            boolean compiled
    ) {
        return new Result(
                dependencies,
                dependencyTracker.candidatePaths(),
                dependencyTracker.isComplete(),
                compiled
        );
    }

    /// Writes a source-map sidecar and CSS file as one recoverable operation.
    ///
    /// The sidecar is committed first. If CSS replacement fails, the previous
    /// sidecar is restored or the newly created sidecar is removed.
    ///
    /// @param destination the textual output destination
    /// @param css the complete textual output
    /// @param sourceMapJson the complete source-map JSON
    /// @throws IOException if publication or a required rollback fails
    private static void writeTextWithSourceMap(
            Path destination,
            String css,
            String sourceMapJson
    ) throws IOException {
        var sourceMapPath = Path.of(destination.toString() + ".map");
        var hadSourceMap = Files.exists(sourceMapPath);
        byte @Nullable [] previousSourceMap = hadSourceMap
                ? Files.readAllBytes(sourceMapPath)
                : null;

        CliFileWriter.writeString(sourceMapPath, sourceMapJson);
        try {
            CliFileWriter.writeString(destination, css);
        } catch (IOException failure) {
            try {
                if (previousSourceMap == null) {
                    Files.deleteIfExists(sourceMapPath);
                } else {
                    CliFileWriter.write(sourceMapPath, previousSourceMap);
                }
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    /// Creates a validated file source for one planned job.
    ///
    /// @param job the file-backed job
    /// @return the root file source
    /// @throws IOException if the source is not a regular file
    private static SassFileSource fileSource(
            CliCompilationPlan.Job job
    ) throws IOException {
        var source = Objects.requireNonNull(job.source());
        if (!Files.isRegularFile(source)) {
            throw new IOException("input is not a file: " + source);
        }
        return new SassFileSource(source, job.syntax());
    }

    /// Returns the normalized absolute identity of one path.
    ///
    /// @param path the path to normalize
    /// @return an absolute normalized path
    private static Path pathKey(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /// Describes one successful compiler attempt.
    ///
    /// @param dependencies normalized file dependencies
    /// @param resolutionCandidates filesystem candidates consulted by imports
    /// @param resolutionComplete whether custom importers were absent
    /// @param compiled whether output was published rather than skipped
    @NotNullByDefault
    record Result(
            @Unmodifiable Set<Path> dependencies,
            @Unmodifiable Set<Path> resolutionCandidates,
            boolean resolutionComplete,
            boolean compiled
    ) {
        /// Creates an immutable compilation result.
        ///
        /// Both supplied sets are defensively copied.
        Result {
            dependencies = Set.copyOf(dependencies);
            resolutionCandidates = Set.copyOf(resolutionCandidates);
        }
    }
}
