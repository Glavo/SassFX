// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.glavo.sassfx.internal.ast.VariableExpression;
import org.glavo.sassfx.internal.callable.CustomFunctionCallable;
import org.glavo.sassfx.internal.diagnostic.CompilationDiagnostics;
import org.glavo.sassfx.internal.evaluate.EvaluationException;
import org.glavo.sassfx.internal.evaluate.SassEvaluator;
import org.glavo.sassfx.internal.module.ModuleRegistry;
import org.glavo.sassfx.internal.parse.ParseException;
import org.glavo.sassfx.internal.parse.SassScriptParser;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Evaluates successive lines in one persistent SassScript environment.
///
/// A session retains variables, loaded module namespaces, module caches, and
/// deprecation repetition state. It accepts expressions, variable
/// declarations, and {@code @use} rules. Instances are not thread-safe;
/// callers must evaluate and drain diagnostics serially.
@NotNullByDefault
public final class SassInteractiveSession {
    /// Evaluates expressions and statements in the persistent environment.
    private final SassEvaluator evaluator;

    /// Processes warnings and deprecations for the complete session.
    private final CompilationDiagnostics diagnosticReporter;

    /// Resolves root-relative modules beside the command working directory.
    private final URI baseUrl;

    /// Index of the first diagnostic not yet returned to the caller.
    private int diagnosticCursor;

    /// Creates an interactive session.
    ///
    /// Source-map and JavaFX retained-stylesheet settings in {@code options}
    /// are ignored because interactive evaluation does not serialize a
    /// stylesheet.
    ///
    /// @param options importer, function, and diagnostic configuration
    /// @param workingDirectory directory used for root-relative module loads
    public SassInteractiveSession(
            CompileOptions options,
            Path workingDirectory
    ) {
        Objects.requireNonNull(options, "options");
        var directory = Objects.requireNonNull(
                workingDirectory,
                "workingDirectory"
        ).toAbsolutePath().normalize();
        var customFunctions = options.functions().stream()
                .map(CustomFunctionCallable::parse)
                .toList();
        this.diagnosticReporter = new CompilationDiagnostics(
                options.diagnosticOptions()
        );
        this.evaluator = new SassEvaluator(
                new ModuleRegistry(
                        options.loadPaths(),
                        options.importers()
                ),
                customFunctions,
                diagnosticReporter
        );
        this.baseUrl = directory.resolve(".sassfx-repl.scss").toUri();
    }

    /// Evaluates one physical input line.
    ///
    /// Whitespace-only lines perform no evaluation. A line whose first code
    /// unit is {@code @} is parsed only as an {@code @use} rule. A line that
    /// begins like {@code $name:} is parsed as a variable declaration; every
    /// other line is parsed as one complete expression.
    ///
    /// @param line the input line without its line terminator
    /// @return the optional inspected value and diagnostics emitted by this line
    /// @throws SassCompilationException if parsing, evaluation, module loading,
    /// or deprecation processing fails
    public Result evaluate(String line) throws SassCompilationException {
        Objects.requireNonNull(line, "line");
        if (line.strip().isEmpty()) {
            return new Result(null, drainDiagnostics());
        }

        var source = new SourceFile(line, null);
        try {
            @Nullable String value;
            if (line.startsWith("@")) {
                var parsed = SassScriptParser.parseUseRule(source);
                reportWarnings(parsed.warnings());
                evaluator.executeInteractiveUse(parsed.node(), baseUrl);
                value = null;
            } else if (SassScriptParser.isVariableDeclarationLike(line)) {
                var parsed =
                        SassScriptParser.parseVariableDeclaration(source);
                reportWarnings(parsed.warnings());
                var declaration = parsed.node();
                declaration.accept(evaluator);
                value = evaluator.evaluate(new VariableExpression(
                        declaration.namespace(),
                        declaration.name(),
                        declaration.span()
                )).toString();
            } else {
                var parsed = SassScriptParser.parseExpression(source);
                reportWarnings(parsed.warnings());
                value = evaluator.evaluate(parsed.node()).toString();
            }
            return new Result(value, drainDiagnostics());
        } catch (ParseException failure) {
            var code = failure.code() == null
                    ? null
                    : failure.code().name();
            throw failure(
                    new Diagnostic(
                            DiagnosticSeverity.ERROR,
                            Objects.requireNonNull(
                                    failure.getMessage(),
                                    "parse failure message"
                            ),
                            failure.span(),
                            code
                    ),
                    List.of(),
                    failure
            );
        } catch (EvaluationException failure) {
            throw failure(
                    failure.primaryDiagnostic(),
                    failure.sassTrace(),
                    failure
            );
        }
    }

    /// Returns diagnostics emitted since the preceding drain or evaluation.
    ///
    /// This includes option-validation diagnostics produced by construction
    /// when called before the first input line.
    ///
    /// @return an immutable diagnostic snapshot
    public @Unmodifiable List<Diagnostic> drainDiagnostics() {
        var snapshot = diagnosticReporter.snapshot();
        if (diagnosticCursor > snapshot.size()) {
            throw new IllegalStateException(
                    "interactive diagnostic cursor exceeds the snapshot"
            );
        }
        var result = List.copyOf(
                snapshot.subList(diagnosticCursor, snapshot.size())
        );
        diagnosticCursor = snapshot.size();
        return result;
    }

    /// Delivers parse-time warnings to session-wide diagnostic processing.
    ///
    /// @param warnings warnings emitted while parsing one complete production
    private void reportWarnings(
            @Unmodifiable List<Diagnostic> warnings
    ) {
        for (var warning : warnings) {
            diagnosticReporter.compilerWarning(warning, false);
        }
    }

    /// Creates a public checked failure containing only new line diagnostics.
    ///
    /// @param primary the primary line error
    /// @param trace Sass call frames for the failure
    /// @param cause the internal failure
    /// @return the checked interactive failure
    private SassCompilationException failure(
            Diagnostic primary,
            @Unmodifiable List<? extends org.glavo.sassfx.SassStackFrame> trace,
            Throwable cause
    ) {
        var diagnostics = new ArrayList<Diagnostic>();
        diagnostics.add(primary);
        diagnostics.addAll(drainDiagnostics());
        return new SassCompilationException(
                diagnostics,
                trace,
                cause
        );
    }

    /// Describes one successfully handled input line.
    ///
    /// @param value inspected SassScript value, or {@code null} when the line
    ///              produces no value
    /// @param diagnostics warnings and debug events emitted by the line
    @NotNullByDefault
    public record Result(
            @Nullable String value,
            @Unmodifiable List<Diagnostic> diagnostics
    ) {
        /// Creates an immutable result.
        public Result {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
