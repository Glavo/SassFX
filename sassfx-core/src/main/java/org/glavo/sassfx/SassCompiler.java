// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.glavo.sassfx.internal.bss.BssImportResolver;
import org.glavo.sassfx.internal.bss.BssSerializeException;
import org.glavo.sassfx.internal.bss.BssSerializer;
import org.glavo.sassfx.internal.callable.CustomFunctionCallable;
import org.glavo.sassfx.internal.css.CssSerializeException;
import org.glavo.sassfx.internal.css.CssSerializer;
import org.glavo.sassfx.internal.diagnostic.CompilationDiagnostics;
import org.glavo.sassfx.internal.evaluate.EvaluationException;
import org.glavo.sassfx.internal.evaluate.SassEvaluator;
import org.glavo.sassfx.internal.module.FilesystemImporter;
import org.glavo.sassfx.internal.module.ModuleRegistry;
import org.glavo.sassfx.internal.module.SassResolutionTracker;
import org.glavo.sassfx.internal.parse.ParseException;
import org.glavo.sassfx.internal.parse.StylesheetParser;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Compiles Sass sources into typed output representations.
///
/// Instances are stateless, thread-safe, and reusable. One compilation reads
/// its root source, parses and evaluates the currently supported language
/// subset, and serializes CSS IR for [CssTarget], [JavaFXCssTarget], and the
/// supported [BssTarget] subset.
@NotNullByDefault
public final class SassCompiler {
    /// Creates a reusable compiler.
    public SassCompiler() {
    }

    /// Compiles a source with default options.
    ///
    /// @param source the root stylesheet source
    /// @param target the output representation to produce
    /// @param <T>    the output representation type
    /// @return the compilation result
    /// @throws IOException               if the root source cannot be read
    /// @throws SassCompilationException  if parsing, evaluation, serialization,
    ///                                   or dependent stylesheet resolution fails
    public <T> CompileResult<T> compile(SassSource source, OutputTarget<T> target)
            throws IOException, SassCompilationException {
        return compile(source, target, CompileOptions.DEFAULT);
    }

    /// Compiles a source with explicit options.
    ///
    /// @param source  the root stylesheet source
    /// @param target  the output representation to produce
    /// @param options shared compilation options
    /// @param <T>     the output representation type
    /// @return the compilation result
    /// @throws IOException              if the root source cannot be read
    /// @throws SassCompilationException if parsing, evaluation, serialization,
    ///                                  or dependent stylesheet resolution fails
    public <T> CompileResult<T> compile(
            SassSource source,
            OutputTarget<T> target,
            CompileOptions options
    ) throws IOException, SassCompilationException {
        return compileInternal(source, target, options, null);
    }

    /// Compiles a source while recording mutable import-resolution inputs.
    ///
    /// This overload supports incremental frontends and is not part of the
    /// stable compiler API.
    ///
    /// @param source the root stylesheet source
    /// @param target the output representation to produce
    /// @param options shared compilation options
    /// @param resolutionTracker the tracker receiving filesystem candidates
    /// @param <T> the output representation type
    /// @return the compilation result
    /// @throws IOException if the root source cannot be read
    /// @throws SassCompilationException if compilation fails
    @ApiStatus.Internal
    public <T> CompileResult<T> compile(
            SassSource source,
            OutputTarget<T> target,
            CompileOptions options,
            SassResolutionTracker resolutionTracker
    ) throws IOException, SassCompilationException {
        Objects.requireNonNull(resolutionTracker, "resolutionTracker");
        return compileInternal(
                source,
                target,
                options,
                resolutionTracker
        );
    }

    /// Implements compilation with optional resolution tracking.
    ///
    /// @param source the root stylesheet source
    /// @param target the output representation to produce
    /// @param options shared compilation options
    /// @param resolutionTracker the tracker, or `null`
    /// @param <T> the output representation type
    /// @return the compilation result
    /// @throws IOException if the root source cannot be read
    /// @throws SassCompilationException if compilation fails
    @SuppressWarnings("unchecked")
    private <T> CompileResult<T> compileInternal(
            SassSource source,
            OutputTarget<T> target,
            CompileOptions options,
            @Nullable SassResolutionTracker resolutionTracker
    ) throws IOException, SassCompilationException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(options, "options");

        if (options.sourceMap() && target instanceof BssTarget) {
            throw compilationFailure(
                    "Source map generation isn't supported for BSS output.",
                    null
            );
        }

        var customFunctions = options.functions().stream()
                .map(CustomFunctionCallable::parse)
                .toList();
        var diagnosticReporter = new CompilationDiagnostics(
                options.diagnosticOptions()
        );
        var loaded = readSource(source);
        var registry = new ModuleRegistry(
                options.loadPaths(),
                options.importers(),
                resolutionTracker
        );
        var evaluator = new SassEvaluator(
                registry,
                customFunctions,
                diagnosticReporter
        );
        var urls = new LinkedHashSet<URI>();
        urls.addAll(loaded.loadedUrls());
        var sourceContents = new LinkedHashMap<URI, String>();
        if (loaded.file().url() != null) {
            sourceContents.put(
                    loaded.file().url(),
                    loaded.file().content()
            );
        }
        try {
            var stylesheet = StylesheetParser.parse(loaded.file(), loaded.syntax());
            if (source instanceof SassStringSource stringSource
                    && stringSource.canonicalUrl() != null
                    && !stringSource.canonicalUrl().isAbsolute()) {
                diagnosticReporter.compilerWarning(
                        new Diagnostic(
                                DiagnosticSeverity.DEPRECATION,
                                "Passing a relative `url` argument ("
                                        + stringSource.canonicalUrl()
                                        + ") to compileString() or related "
                                        + "functions is deprecated and will be "
                                        + "an error in Dart Sass 2.0.0.",
                                stylesheet.span(),
                                SassDeprecation.COMPILE_STRING_RELATIVE_URL.id()
                        ),
                        false
                );
            }
            var root = evaluator.executeRoot(stylesheet, loaded.file().url());
            urls.addAll(registry.loadedUrls());
            sourceContents.putAll(registry.sourceContents());
            @Nullable String stdinContents = null;
            if (loaded.file().url() == null) {
                stdinContents = loaded.file().content();
            }
            T output;
            org.glavo.sassfx.SourceMap sourceMap = null;
            if (target instanceof CssTarget cssTarget) {
                var serialized = CssSerializer.serialize(
                        root.css(),
                        cssTarget,
                        options.sourceMap(),
                        registry.sourceMapUrls(),
                        options.sourceMapIncludeSources(),
                        sourceContents,
                        stdinContents
                );
                output = (T) serialized.css();
                sourceMap = serialized.sourceMap();
            } else if (target instanceof JavaFXCssTarget javaFXCssTarget) {
                var serialized = CssSerializer.serialize(
                        root.css(),
                        javaFXCssTarget,
                        options.sourceMap(),
                        registry.sourceMapUrls(),
                        options.sourceMapIncludeSources(),
                        sourceContents,
                        stdinContents
                );
                output = (T) serialized.css();
                sourceMap = serialized.sourceMap();
            } else if (target instanceof BssTarget bssTarget) {
                var cssImporter = new FilesystemImporter(
                        options.loadPaths(),
                        resolutionTracker
                );
                @Nullable JavaFXStylesheetResolver stylesheetResolver =
                        options.javaFXStylesheetResolver();
                BssImportResolver resolver = (resource, baseUrl, span) -> {
                    @Nullable JavaFXStylesheetResolver.ResolvedStylesheet custom = null;
                    if (stylesheetResolver != null) {
                        custom = stylesheetResolver.resolve(
                                resource,
                                baseUrl
                        );
                    }

                    SourceFile importedSource;
                    URI canonicalUrl;
                    if (custom != null) {
                        if (resolutionTracker != null) {
                            resolutionTracker.markIncomplete();
                        }
                        canonicalUrl = custom.canonicalUrl();
                        importedSource = new SourceFile(custom.content(), canonicalUrl);
                    } else {
                        var imported = cssImporter.canonicalizeAndLoadCss(resource, baseUrl);
                        if (imported == null) {
                            throw new BssSerializeException(
                                    "Can't find JavaFX CSS stylesheet to import: \""
                                            + resource + "\".",
                                    span,
                                    null
                            );
                        }
                        canonicalUrl = imported.canonicalUrl();
                        importedSource = imported.source();
                    }
                    urls.add(canonicalUrl);
                    sourceContents.put(
                            canonicalUrl,
                            importedSource.content()
                    );
                    var childRegistry = new ModuleRegistry(
                            options.loadPaths(),
                            options.importers(),
                            resolutionTracker
                    );
                    var childEvaluator = new SassEvaluator(
                            childRegistry,
                            customFunctions,
                            diagnosticReporter
                    );
                    try {
                        var childAst = StylesheetParser.parse(
                                importedSource,
                                Syntax.CSS
                        );
                        var childRoot = childEvaluator.executeRoot(
                                childAst,
                                canonicalUrl
                        );
                        return new BssImportResolver.ResolvedImport(
                                childRoot.css(),
                                canonicalUrl
                        );
                    } finally {
                        urls.addAll(childRegistry.loadedUrls());
                        sourceContents.putAll(
                                childRegistry.sourceContents()
                        );
                    }
                };
                output = (T) BssSerializer.serialize(root.css(), bssTarget, resolver);
            } else {
                throw unsupportedTarget(target);
            }
            evaluator.finishDiagnostics();
            return new CompileResult<>(
                    output,
                    sourceMap,
                    urls,
                    evaluator.diagnostics()
            );
        } catch (ParseException failure) {
            urls.addAll(registry.loadedUrls());
            sourceContents.putAll(registry.sourceContents());
            var code = failure.code() == null
                    ? null
                    : failure.code().name();
            var primary = new Diagnostic(
                            DiagnosticSeverity.ERROR,
                            Objects.requireNonNull(failure.getMessage(), "parse failure message"),
                            failure.span(),
                            code
                    );
            throw new SassCompilationException(
                    failureDiagnostics(primary, evaluator.diagnostics()),
                    urls,
                    sourceContents,
                    failure
            );
        } catch (EvaluationException failure) {
            urls.addAll(registry.loadedUrls());
            sourceContents.putAll(registry.sourceContents());
            throw new SassCompilationException(
                    failureDiagnostics(
                            failure.primaryDiagnostic(),
                            evaluator.diagnostics()
                    ),
                    failure.sassTrace(),
                    urls,
                    sourceContents,
                    failure
            );
        } catch (CssSerializeException failure) {
            urls.addAll(registry.loadedUrls());
            sourceContents.putAll(registry.sourceContents());
            throw new SassCompilationException(
                    failureDiagnostics(
                            failure.primaryDiagnostic(),
                            evaluator.diagnostics()
                    ),
                    failure.sassTrace(),
                    urls,
                    sourceContents,
                    failure
            );
        } catch (BssSerializeException failure) {
            urls.addAll(registry.loadedUrls());
            sourceContents.putAll(registry.sourceContents());
            throw new SassCompilationException(
                    failureDiagnostics(
                            failure.primaryDiagnostic(),
                            evaluator.diagnostics()
                    ),
                    failure.sassTrace(),
                    urls,
                    sourceContents,
                    failure
            );
        }
    }

    /// Combines a primary failure with diagnostics emitted before the failure.
    ///
    /// The primary error remains first as required by [SassCompilationException];
    /// any earlier non-error diagnostics retain their reporting order afterward.
    ///
    /// @param primary  the primary compilation error
    /// @param previous diagnostics emitted before the failure
    /// @return an immutable nonempty diagnostic list
    private static @Unmodifiable List<Diagnostic> failureDiagnostics(
            Diagnostic primary,
            List<? extends Diagnostic> previous
    ) {
        var result = new ArrayList<Diagnostic>(previous.size() + 1);
        result.add(primary);
        result.addAll(previous);
        return List.copyOf(result);
    }

    /// Reads the root source text and records its canonical URL when available.
    ///
    /// @param source the root source
    /// @return the loaded source contents
    /// @throws IOException if a file source cannot be read
    private static LoadedSource readSource(SassSource source) throws IOException {
        if (source instanceof SassStringSource stringSource) {
            var file = new SourceFile(stringSource.content(), stringSource.canonicalUrl());
            return new LoadedSource(file, stringSource.syntax(), loadedUrls(stringSource.canonicalUrl()));
        }
        if (source instanceof SassFileSource fileSource) {
            var path = fileSource.path();
            var realPath = path.toRealPath();
            var content = Files.readString(realPath, StandardCharsets.UTF_8);
            var url = realPath.toUri();
            var file = new SourceFile(content, url);
            return new LoadedSource(file, fileSource.syntax(), Set.of(url));
        }
        throw new IllegalArgumentException("unsupported SassSource: " + source.getClass().getName());
    }

    /// Creates the loaded-URL set for an optional canonical URL.
    ///
    /// @param canonicalUrl the canonical URL, or {@code null}
    /// @return an empty set or a singleton set
    private static Set<URI> loadedUrls(@Nullable URI canonicalUrl) {
        if (canonicalUrl == null) {
            return Set.of();
        }
        var urls = new LinkedHashSet<URI>(1);
        urls.add(canonicalUrl);
        return urls;
    }

    /// Creates a failure for an unsupported output target.
    ///
    /// @param target the unsupported target
    /// @return the compilation exception
    private static SassCompilationException unsupportedTarget(OutputTarget<?> target) {
        var label = target instanceof JavaFXCssTarget
                ? "JavaFX CSS"
                : target instanceof BssTarget ? "BSS" : "CSS";
        return compilationFailure(label + " output isn't supported.", null);
    }

    /// Creates a span-free compilation failure.
    ///
    /// @param message the failure message
    /// @param cause   the underlying cause, or {@code null}
    /// @return the compilation exception
    private static SassCompilationException compilationFailure(
            String message,
            @Nullable Throwable cause
    ) {
        return new SassCompilationException(
                List.of(new Diagnostic(DiagnosticSeverity.ERROR, message, null, null)),
                cause
        );
    }

    /// Contains the loaded root source and its metadata.
    ///
    /// @param file       the indexed source text
    /// @param syntax     the syntax selected for parsing
    /// @param loadedUrls the canonical URLs loaded so far
    private record LoadedSource(
            SourceFile file,
            Syntax syntax,
            Set<URI> loadedUrls
    ) {
        /// Creates a loaded source snapshot.
        private LoadedSource {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(syntax, "syntax");
            Objects.requireNonNull(loadedUrls, "loadedUrls");
            loadedUrls = Set.copyOf(loadedUrls);
        }
    }
}
