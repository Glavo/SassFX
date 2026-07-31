// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.gradle;

import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CompileResult;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.OutputTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassFileSource;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Compiles a directory tree of Sass entrypoints.
///
/// Files ending in `.scss`, `.sass`, or `.css` are task inputs. Files whose
/// names begin with `_` are treated as partials: they participate in Gradle
/// input tracking and import resolution but do not produce their own output.
/// Generated paths retain each entrypoint's path relative to
/// [#getSourceDirectory()] and replace its final extension with `.css` or
/// `.bss`.
///
/// Compilation first writes a task-local staging tree. The configured output
/// directory is synchronized only after every entrypoint succeeds, so one
/// compilation failure cannot publish a partially updated output tree.
@CacheableTask
@NotNullByDefault
public abstract class SassFXCompile extends DefaultTask {
    /// Creates a compilation task with standard CSS conventions.
    public SassFXCompile() {
        getTarget().convention("css");
        getStyle().convention("expanded");
        getCharset().convention(true);
    }

    /// Returns the directory containing entrypoints and partials.
    ///
    /// @return the source directory property
    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    /// Returns the Sass, SCSS, and CSS files tracked as task inputs.
    ///
    /// The returned tree is live and follows [#getSourceDirectory()].
    ///
    /// @return the source input tree
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public final FileTree getSourceFiles() {
        return getSourceDirectory().getAsFileTree().matching(
                patterns -> patterns.include("**/*.scss", "**/*.sass", "**/*.css")
        );
    }

    /// Returns additional filesystem roots searched for Sass imports.
    ///
    /// Entire configured directories are inputs because any file under a load
    /// path may affect import resolution.
    ///
    /// @return the ordered load-path collection
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getLoadPaths();

    /// Returns the directory receiving the synchronized output tree.
    ///
    /// @return the output directory property
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /// Returns the strict output target selector.
    ///
    /// @return `css`, `css/javafx@8` through `css/javafx@27`, or
    /// `bss/javafx@8` through `bss/javafx@27`
    @Input
    public abstract Property<String> getTarget();

    /// Returns the text output style selector.
    ///
    /// @return `expanded` or `compressed`
    @Input
    public abstract Property<String> getStyle();

    /// Returns whether standard CSS non-ASCII output receives a charset marker.
    ///
    /// @return the charset-emission property
    @Input
    public abstract Property<Boolean> getCharset();

    /// Returns the filesystem operations service used for transactional output.
    ///
    /// @return the injected filesystem service
    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /// Compiles every entrypoint and synchronizes the completed output tree.
    ///
    /// @throws IOException if an input or output file cannot be read or written
    /// @throws GradleException if configuration is invalid, output paths
    /// collide, or Sass compilation fails
    @TaskAction
    public final void compileStylesheets() throws IOException {
        var sourceRoot = getSourceDirectory().get().getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
        var outputRoot = getOutputDirectory().get().getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
        var target = parseTarget(
                getTarget().get(),
                getStyle().get(),
                getCharset().get()
        );
        var entries = entrypoints(sourceRoot, target.extension());
        var loadPaths = loadPaths();
        var declaredInputRoots = declaredInputRoots(sourceRoot, loadPaths);
        var options = new CompileOptions(false, loadPaths);
        var stagingRoot = getTemporaryDir().toPath()
                .resolve("output")
                .toAbsolutePath()
                .normalize();

        getFileSystemOperations().delete(spec -> spec.delete(stagingRoot));
        Files.createDirectories(stagingRoot);

        var compiler = new SassCompiler();
        for (var entry : entries.entrySet()) {
            var source = entry.getKey();
            var relativeOutput = entry.getValue();
            var stagedOutput = stagingRoot.resolve(relativeOutput);
            Files.createDirectories(
                    Objects.requireNonNull(stagedOutput.getParent())
            );
            CompileResult<?> result;
            try {
                result = compiler.compile(
                        new SassFileSource(source),
                        target.outputTarget(),
                        options
                );
            } catch (SassCompilationException failure) {
                throw new GradleException(
                        "SassFX failed to compile "
                                + displayPath(sourceRoot.relativize(source))
                                + ": " + failure.getMessage(),
                        failure
                );
            }
            validateLoadedFiles(
                    sourceRoot,
                    source,
                    declaredInputRoots,
                    result.loadedUrls()
            );
            writeOutput(stagedOutput, result.output());
            reportDiagnostics(sourceRoot, source, result.diagnostics());
        }

        getFileSystemOperations().sync(spec -> {
            spec.from(stagingRoot);
            spec.into(outputRoot);
        });
    }

    /// Returns ordered filesystem load paths.
    ///
    /// @return an immutable path list retaining Gradle collection order
    private @Unmodifiable List<Path> loadPaths() {
        var paths = new ArrayList<Path>();
        for (var file : getLoadPaths()) {
            var path = file.toPath().toAbsolutePath().normalize();
            if (!Files.isDirectory(path)) {
                throw new GradleException(
                        "SassFX load path is not a directory: " + path
                );
            }
            paths.add(path);
        }
        return List.copyOf(paths);
    }

    /// Resolves the roots whose contents Gradle tracks as task inputs.
    ///
    /// @param sourceRoot the configured source directory
    /// @param loadPaths configured load-path directories
    /// @return canonical existing roots in declaration order
    /// @throws IOException if an existing root cannot be canonicalized
    private static @Unmodifiable List<Path> declaredInputRoots(
            Path sourceRoot,
            List<Path> loadPaths
    ) throws IOException {
        var roots = new ArrayList<Path>();
        if (Files.exists(sourceRoot)) {
            roots.add(sourceRoot.toRealPath());
        }
        for (var loadPath : loadPaths) {
            roots.add(loadPath.toRealPath());
        }
        return List.copyOf(roots);
    }

    /// Verifies that every loaded file was declared to Gradle as an input.
    ///
    /// Non-file canonical URLs have no filesystem state for Gradle to track.
    /// A relative import may escape the source directory, so its containing
    /// tree must be added explicitly to [#getLoadPaths()].
    ///
    /// @param sourceRoot the source directory used for display paths
    /// @param source the current entrypoint
    /// @param declaredRoots canonical source and load-path roots
    /// @param loadedUrls canonical URLs loaded by the compiler
    /// @throws IOException if a loaded file cannot be canonicalized
    /// @throws GradleException if a loaded file is outside every declared root
    private static void validateLoadedFiles(
            Path sourceRoot,
            Path source,
            List<Path> declaredRoots,
            Iterable<URI> loadedUrls
    ) throws IOException {
        for (var loadedUrl : loadedUrls) {
            if (!"file".equalsIgnoreCase(loadedUrl.getScheme())) {
                continue;
            }
            var loaded = Path.of(loadedUrl).toRealPath();
            if (declaredRoots.stream().noneMatch(loaded::startsWith)) {
                throw new GradleException(
                        "SassFX entrypoint "
                                + displayPath(sourceRoot.relativize(source))
                                + " loaded undeclared input " + loaded
                                + "; add its containing directory to loadPaths."
                );
            }
        }
    }

    /// Selects root stylesheets and their relative output paths.
    ///
    /// @param sourceRoot the normalized absolute source root
    /// @param extension the target output extension
    /// @return an immutable insertion-ordered source-to-output map
    /// @throws GradleException if two entrypoints map to the same output path
    private @Unmodifiable Map<Path, Path> entrypoints(
            Path sourceRoot,
            String extension
    ) {
        var sources = getSourceFiles().getFiles().stream()
                .map(File::toPath)
                .map(path -> path.toAbsolutePath().normalize())
                .filter(path -> !path.getFileName().toString().startsWith("_"))
                .sorted(Comparator.comparing(
                        path -> displayPath(sourceRoot.relativize(path))
                ))
                .toList();
        var result = new LinkedHashMap<Path, Path>();
        var owners = new LinkedHashMap<Path, Path>();
        for (var source : sources) {
            var relative = sourceRoot.relativize(source);
            var output = replaceExtension(relative, extension);
            var previous = owners.putIfAbsent(output, source);
            if (previous != null) {
                throw new GradleException(
                        "SassFX entrypoints "
                                + displayPath(sourceRoot.relativize(previous))
                                + " and " + displayPath(relative)
                                + " both map to " + displayPath(output) + "."
                );
            }
            result.put(source, output);
        }
        return Collections.unmodifiableMap(result);
    }

    /// Replaces the final filename extension.
    ///
    /// @param path the relative source path
    /// @param extension the replacement including its leading period
    /// @return the relative output path
    private static Path replaceExtension(Path path, String extension) {
        var fileName = Objects.requireNonNull(path.getFileName()).toString();
        var separator = fileName.lastIndexOf('.');
        if (separator <= 0) {
            throw new GradleException(
                    "SassFX source has no recognized extension: "
                            + displayPath(path)
            );
        }
        var outputName = fileName.substring(0, separator) + extension;
        var parent = path.getParent();
        return parent == null ? Path.of(outputName) : parent.resolve(outputName);
    }

    /// Parses the output target and formatting properties.
    ///
    /// @param targetValue the strict target selector
    /// @param styleValue the strict text style selector
    /// @param charset whether standard CSS output emits a charset marker
    /// @return the concrete compiler target and output extension
    /// @throws GradleException if a property value is unsupported
    private static ParsedTarget parseTarget(
            String targetValue,
            String styleValue,
            boolean charset
    ) {
        var style = switch (styleValue) {
            case "expanded" -> OutputStyle.EXPANDED;
            case "compressed" -> OutputStyle.COMPRESSED;
            default -> throw new GradleException(
                    "Unsupported SassFX style '" + styleValue
                            + "'; expected 'expanded' or 'compressed'."
            );
        };
        final OutputTarget<?> parsedTarget;
        try {
            parsedTarget = OutputTarget.parse(targetValue);
        } catch (IllegalArgumentException ignored) {
            throw unsupportedTarget(targetValue);
        }
        if (parsedTarget instanceof CssTarget) {
            return new ParsedTarget(
                    new CssTarget(style, charset),
                    ".css"
            );
        }
        if (parsedTarget instanceof JavaFXCssTarget javaFXCssTarget) {
            return new ParsedTarget(
                    new JavaFXCssTarget(
                            javaFXCssTarget.javaFXTarget(),
                            style
                    ),
                    ".css"
            );
        }
        if (parsedTarget instanceof BssTarget bssTarget
                && style != OutputStyle.EXPANDED) {
            throw new GradleException(
                    "SassFX style is not supported for BSS output."
            );
        }
        if (parsedTarget instanceof BssTarget bssTarget) {
            return new ParsedTarget(bssTarget, ".bss");
        }
        throw new IllegalStateException(
                "Unsupported output target implementation: "
                        + parsedTarget.getClass().getName()
        );
    }

    /// Creates a failure for a noncanonical target selector.
    ///
    /// @param targetValue the rejected selector
    /// @return the configuration failure
    private static GradleException unsupportedTarget(String targetValue) {
        return new GradleException(
                "Unsupported SassFX target '" + targetValue
                        + "'; expected 'css', 'css/javafx@8' through "
                        + "'css/javafx@27', or 'bss/javafx@8' through "
                        + "'bss/javafx@27'."
        );
    }

    /// Writes one typed compiler output.
    ///
    /// @param outputPath the staged destination
    /// @param output the compiler result value
    /// @throws IOException if the destination cannot be written
    private static void writeOutput(Path outputPath, Object output)
            throws IOException {
        if (output instanceof String css) {
            Files.writeString(outputPath, css, StandardCharsets.UTF_8);
            return;
        }
        if (output instanceof ByteBuffer bss) {
            var copy = bss.asReadOnlyBuffer();
            var bytes = new byte[copy.remaining()];
            copy.get(bytes);
            Files.write(outputPath, bytes);
            return;
        }
        throw new AssertionError(
                "Unsupported SassFX output type: " + output.getClass().getName()
        );
    }

    /// Reports non-error compiler diagnostics through Gradle logging.
    ///
    /// @param sourceRoot the normalized source directory
    /// @param source the compiled entrypoint
    /// @param diagnostics diagnostics emitted by the compiler
    private void reportDiagnostics(
            Path sourceRoot,
            Path source,
            List<Diagnostic> diagnostics
    ) {
        for (var diagnostic : diagnostics) {
            var message = "SassFX "
                    + displayPath(sourceRoot.relativize(source))
                    + ": " + diagnostic.message();
            if (diagnostic.severity() == DiagnosticSeverity.DEBUG) {
                getLogger().lifecycle(message);
            } else {
                getLogger().warn(message);
            }
        }
    }

    /// Returns a platform-independent display path.
    ///
    /// @param path the path to render
    /// @return the path with forward-slash separators
    private static String displayPath(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }

    /// Contains one concrete compiler target and output suffix.
    ///
    /// @param outputTarget the compiler output target
    /// @param extension the output suffix including its leading period
    @NotNullByDefault
    private record ParsedTarget(
            OutputTarget<?> outputTarget,
            String extension
    ) {
        /// Creates a validated target pair.
        private ParsedTarget {
            Objects.requireNonNull(outputTarget, "outputTarget");
            Objects.requireNonNull(extension, "extension");
        }
    }
}
