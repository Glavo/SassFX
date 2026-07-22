// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.glavo.scssfx.internal.css.CssSerializeException;
import org.glavo.scssfx.internal.css.CssSerializer;
import org.glavo.scssfx.internal.evaluate.EvaluationException;
import org.glavo.scssfx.internal.evaluate.SassEvaluator;
import org.glavo.scssfx.internal.parse.ParseException;
import org.glavo.scssfx.internal.parse.StylesheetParser;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Compiles Sass sources into typed output representations.
///
/// Instances are stateless, thread-safe, and reusable. One compilation reads
/// its root source, parses and evaluates the currently supported language
/// subset, and serializes CSS IR for [CssTarget] requests.
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
    /// @throws SassCompilationException  if parsing, evaluation, or serialization fails
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
    /// @throws SassCompilationException if parsing, evaluation, or serialization fails
    @SuppressWarnings("unchecked")
    public <T> CompileResult<T> compile(
            SassSource source,
            OutputTarget<T> target,
            CompileOptions options
    ) throws IOException, SassCompilationException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(options, "options");

        if (!(target instanceof CssTarget cssTarget)) {
            throw unsupportedTarget(target);
        }
        if (cssTarget.style() != OutputStyle.EXPANDED) {
            throw compilationFailure(
                    "Compressed CSS output isn't supported.",
                    null
            );
        }
        if (options.sourceMap()) {
            throw compilationFailure(
                    "Source map generation isn't supported.",
                    null
            );
        }

        var loaded = readSource(source);
        try {
            var stylesheet = StylesheetParser.parse(loaded.file(), loaded.syntax());
            var evaluator = new SassEvaluator();
            evaluator.execute(stylesheet);
            var css = evaluator.cssStylesheet();
            if (css == null) {
                throw new IllegalStateException("stylesheet execution produced no CSS IR");
            }
            var text = CssSerializer.serialize(css, cssTarget);
            return (CompileResult<T>) new CompileResult<>(
                    text,
                    null,
                    loaded.loadedUrls(),
                    evaluator.diagnostics()
            );
        } catch (ParseException failure) {
            throw new SassCompilationException(
                    List.of(new Diagnostic(
                            DiagnosticSeverity.ERROR,
                            Objects.requireNonNull(failure.getMessage(), "parse failure message"),
                            failure.span(),
                            null
                    )),
                    failure
            );
        } catch (EvaluationException failure) {
            throw new SassCompilationException(
                    List.of(failure.primaryDiagnostic()),
                    failure.sassTrace(),
                    failure
            );
        } catch (CssSerializeException failure) {
            throw new SassCompilationException(
                    List.of(failure.primaryDiagnostic()),
                    failure.sassTrace(),
                    failure
            );
        }
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
            var content = Files.readString(path, StandardCharsets.UTF_8);
            var url = path.toAbsolutePath().normalize().toUri();
            var file = new SourceFile(content, url);
            return new LoadedSource(file, fileSource.syntax(), Set.of(url));
        }
        throw new IllegalArgumentException("unsupported SassSource: " + source.getClass().getName());
    }

    /// Creates the loaded-URL set for an optional canonical URL.
    ///
    /// @param canonicalUrl the absolute canonical URL, or {@code null}
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
        var label = target instanceof JavaFxCssTarget
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
