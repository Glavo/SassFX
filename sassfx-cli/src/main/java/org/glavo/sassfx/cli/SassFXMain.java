// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.Diagnostic;
import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.OutputTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassDependencyTracker;
import org.glavo.sassfx.SassDeprecation;
import org.glavo.sassfx.SassDiagnosticOptions;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassInteractiveSession;
import org.glavo.sassfx.SassLogger;
import org.glavo.sassfx.SassNodePackageImporter;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.embedded.EmbeddedCompiler;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/// Provides the command-line entry point for SassFX.
@Command(
        name = "sassfx",
        description = "Compiles Sass stylesheets to CSS, JavaFX CSS, or JavaFX BSS.",
        mixinStandardHelpOptions = true,
        versionProvider = SassFXVersionProvider.class,
        exitCodeOnInvalidInput = 64,
        exitCodeOnExecutionException = 255
)
@NotNullByDefault
public final class SassFXMain implements Callable<Integer> {
    /// The exit status used when Sass input is invalid.
    private static final int DATA_EXIT_STATUS = 65;

    /// The exit status used when an input or output cannot be accessed.
    private static final int IO_EXIT_STATUS = 66;

    /// The exit status used when command-line usage is invalid.
    private static final int USAGE_EXIT_STATUS = 64;

    /// The exit status used for an unexpected implementation failure.
    private static final int SOFTWARE_EXIT_STATUS = 255;

    /// Parses three-component semantic versions accepted by fatal deprecations.
    private static final Pattern DEPRECATION_VERSION =
            Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)");

    /// The raw input and output operands collected by Picocli.
    @Parameters(
            arity = "0..*",
            paramLabel = "INPUT",
            description = "Compile INPUT [OUTPUT], INPUT:OUTPUT..., or DIR[:OUTPUT_DIR]."
    )
    private final List<String> inputs = new ArrayList<>();

    /// Selects standard input independently of the positional magic input.
    @Option(
            names = "--stdin",
            negatable = true,
            description = "Read the stylesheet from standard input."
    )
    private boolean standardInput;

    /// Selects the indented Sass syntax for every root input.
    @Option(
            names = "--indented",
            negatable = true,
            description = "Use the indented Sass syntax for root inputs."
    )
    private boolean indented;

    /// The optional output path selected with {@code -o}/{@code --output}.
    @Option(
            names = {"-o", "--output"},
            paramLabel = "FILE",
            description = "Write output to FILE instead of stdout."
    )
    private @Nullable Path output;

    /// The textual output target selected by the caller.
    @Option(
            names = "--target",
            defaultValue = "css",
            paramLabel = "TARGET",
            description = "Select css, css/javafx@8..27, or bss/javafx@8..27 (default: ${DEFAULT-VALUE})."
    )
    private String target = "css";

    /// The textual layout style selected by the caller.
    @Option(
            names = {"-s", "--style"},
            defaultValue = "expanded",
            paramLabel = "STYLE",
            description = "Select CSS output style: expanded or compressed (default: ${DEFAULT-VALUE})."
    )
    private String style = "expanded";

    /// The explicit charset-marker selection, or `null` when unspecified.
    private @Nullable Boolean charsetOption;

    /// Records an explicit charset-marker selection.
    ///
    /// @param enabled whether charset markers are enabled
    @Option(
            names = "--charset",
            negatable = true,
            description = "Emit a UTF-8 charset marker for standard CSS when needed."
    )
    private void setCharsetOption(boolean enabled) {
        charsetOption = enabled;
    }

    /// The explicit source-map selection, or `null` when unspecified.
    private @Nullable Boolean sourceMapOption;

    /// Records an explicit source-map selection.
    ///
    /// @param enabled whether source-map generation is enabled
    @Option(
            names = "--source-map",
            negatable = true,
            description = "Generate source maps for file output."
    )
    private void setSourceMapOption(boolean enabled) {
        sourceMapOption = enabled;
    }

    /// Selects relative or absolute source URLs in generated maps.
    @Option(
            names = "--source-map-urls",
            defaultValue = "relative",
            paramLabel = "TYPE",
            description = "Select source-map URLs: relative or absolute."
    )
    private String sourceMapUrls = "relative";

    /// Includes the original Sass text in generated source maps.
    @Option(
            names = "--embed-sources",
            negatable = true,
            description = "Embed source contents in generated source maps."
    )
    private boolean embedSources;

    /// Includes the generated source map as a data URL in textual output.
    @Option(
            names = "--embed-source-map",
            negatable = true,
            description = "Embed the source map in generated CSS."
    )
    private boolean embedSourceMap;

    /// The explicit error-stylesheet selection, or `null` when unspecified.
    private @Nullable Boolean errorCss;

    /// Records an explicit error-stylesheet selection.
    ///
    /// @param enabled whether failed compilations emit error stylesheets
    @Option(
            names = "--error-css",
            negatable = true,
            description = "Emit a stylesheet that displays compilation errors."
    )
    private void setErrorCss(boolean enabled) {
        errorCss = enabled;
    }

    /// Compiles only jobs whose source dependency snapshot is newer than its
    /// destination.
    @Option(
            names = "--update",
            description = "Only compile out-of-date stylesheets."
    )
    private boolean update;

    /// Continuously recompiles stylesheets after relevant filesystem changes.
    @Option(
            names = {"-w", "--watch"},
            description = "Watch stylesheets and recompile when they change."
    )
    private boolean watch;

    /// The explicit filesystem-polling selection, or `null` when unspecified.
    private @Nullable Boolean pollOption;

    /// Records an explicit filesystem-polling selection.
    ///
    /// @param enabled whether watching uses recursive metadata polling
    @Option(
            names = "--poll",
            negatable = true,
            description = "Poll for changes; only valid with --watch."
    )
    private void setPollOption(boolean enabled) {
        pollOption = enabled;
    }

    /// Runs the Embedded Sass Protocol 3.2.0 compiler endpoint.
    @Option(
            names = "--embedded",
            description = "Run the Embedded Sass protocol compiler."
    )
    private boolean embedded;

    /// Runs a persistent line-oriented SassScript evaluator.
    @Option(
            names = {"-i", "--interactive"},
            description = "Run an interactive SassScript shell."
    )
    private boolean interactive;

    /// Stops a multi-input compilation after its first failed job.
    @Option(
            names = "--stop-on-error",
            negatable = true,
            description = "Stop compiling after the first error."
    )
    private boolean stopOnError;

    /// Additional Sass load paths in command-line order.
    @Option(
            names = {"-I", "--load-path"},
            paramLabel = "PATH",
            description = "Add PATH to the Sass load path."
    )
    private final List<Path> loadPaths = new ArrayList<>();

    /// Built-in package importer types in command-line order.
    @Option(
            names = {"-p", "--pkg-importer"},
            paramLabel = "TYPE",
            split = ",",
            description = "Use a built-in importer for pkg: URLs: node."
    )
    private final List<String> packageImporterTypes = new ArrayList<>();

    /// Suppresses non-error diagnostic output.
    @Option(
            names = {"-q", "--quiet"},
            negatable = true,
            description = "Suppress warnings, deprecations, and debug messages."
    )
    private boolean quiet;

    /// Suppresses compiler warnings from load-path dependencies.
    @Option(
            names = "--quiet-deps",
            negatable = true,
            description = "Suppress compiler warnings from dependencies."
    )
    private boolean quietDeps;

    /// Disables the five-event deprecation repetition limit.
    @Option(
            names = "--verbose",
            negatable = true,
            description = "Print every deprecation warning."
    )
    private boolean verbose;

    /// Accepts the legacy sass-spec precision option without changing output.
    @Option(
            names = "--precision",
            paramLabel = "DIGITS",
            hidden = true
    )
    private @Nullable String compatibilityPrecision;

    /// Accepts the Dart Sass asynchronous evaluator switch.
    ///
    /// The pure-Java compiler has one evaluator implementation, so both values
    /// select the same observable compilation behavior.
    @Option(
            names = "--async",
            negatable = true,
            hidden = true
    )
    private boolean asynchronousCompatibilityMode;

    /// Deprecation identifiers or versions promoted to errors.
    @Option(
            names = "--fatal-deprecation",
            paramLabel = "ID|VERSION",
            split = ",",
            description = "Make a deprecation fatal; may be repeated."
    )
    private final List<String> fatalDeprecations = new ArrayList<>();

    /// Deprecation identifiers omitted from output.
    @Option(
            names = "--silence-deprecation",
            paramLabel = "ID",
            split = ",",
            description = "Silence a deprecation; may be repeated."
    )
    private final List<String> silenceDeprecations = new ArrayList<>();

    /// Future deprecation identifiers explicitly enabled.
    @Option(
            names = "--future-deprecation",
            paramLabel = "ID",
            split = ",",
            description = "Enable a future deprecation; may be repeated."
    )
    private final List<String> futureDeprecations = new ArrayList<>();

    /// The explicit ANSI-color selection, or `null` when unspecified.
    private @Nullable Boolean colorOption;

    /// Records an explicit ANSI-color selection.
    ///
    /// @param enabled whether diagnostics use ANSI terminal styling
    @Option(
            names = {"-c", "--color"},
            negatable = true,
            description = "Use terminal colors for messages."
    )
    private void setColorOption(boolean enabled) {
        colorOption = enabled;
    }

    /// The explicit diagnostic-glyph selection, or `null` when unspecified.
    private @Nullable Boolean unicodeOption;

    /// Records an explicit diagnostic-glyph selection.
    ///
    /// @param enabled whether diagnostic frames use Unicode glyphs
    @Option(
            names = "--unicode",
            negatable = true,
            description = "Use Unicode characters for messages."
    )
    private void setUnicodeOption(boolean enabled) {
        unicodeOption = enabled;
    }

    /// Includes Java implementation stack traces after Sass and IO errors.
    @Option(
            names = "--trace",
            negatable = true,
            description = "Print full Java stack traces for exceptions."
    )
    private boolean trace;

    /// The command specification injected by Picocli before invocation.
    @Spec
    private @Nullable CommandSpec commandSpec;

    /// The standard-input reader owned by the caller.
    private final Reader standardInputReader;

    /// The raw standard input used for binary embedded-protocol packets.
    private final InputStream standardInputStream;

    /// The raw standard output used for binary embedded-protocol packets.
    private final OutputStream standardOutputStream;

    /// The directory used as the entry importer base for standard input.
    private final Path workingDirectory;

    /// The raw `SASS_PATH` value captured for this invocation, or `null` when
    /// the variable is absent.
    private final @Nullable String sassPathEnvironment;

    /// Identifies span-free option warnings for invocation-level output
    /// deduplication.
    private final Set<String> configurationWarningMessages =
            new LinkedHashSet<>();

    /// Records configuration warnings already printed by this invocation.
    private final Set<String> emittedConfigurationWarningMessages =
            new LinkedHashSet<>();

    /// Creates a command-line entry point that reads the process standard input.
    public SassFXMain() {
        this(
                System.in,
                System.out,
                Path.of("").toAbsolutePath(),
                System.getenv("SASS_PATH")
        );
    }

    /// Creates a command-line entry point with an injected standard input.
    ///
    /// The stream is decoded as UTF-8 and is not closed by this command.
    ///
    /// @param standardInput the input stream used for stdin compilations
    SassFXMain(InputStream standardInput) {
        this(
                standardInput,
                System.out,
                Path.of("").toAbsolutePath(),
                System.getenv("SASS_PATH")
        );
    }

    /// Creates an entry point with injected standard input and working directory.
    ///
    /// The stream is decoded as UTF-8 and is not closed by this command.
    ///
    /// @param standardInput the input stream used for stdin compilations
    /// @param workingDirectory the base directory for relative stdin imports
    SassFXMain(InputStream standardInput, Path workingDirectory) {
        this(
                standardInput,
                System.out,
                workingDirectory,
                System.getenv("SASS_PATH")
        );
    }

    /// Creates an entry point with injected standard input, working directory,
    /// and `SASS_PATH` value.
    ///
    /// The stream is decoded as UTF-8 and is not closed by this command.
    ///
    /// @param standardInput the input stream used for stdin compilations
    /// @param workingDirectory the base directory for relative inputs and load
    /// paths
    /// @param sassPathEnvironment the raw `SASS_PATH` value, or `null` to model
    /// an absent variable
    SassFXMain(
            InputStream standardInput,
            Path workingDirectory,
            @Nullable String sassPathEnvironment
    ) {
        this(
                standardInput,
                System.out,
                workingDirectory,
                sassPathEnvironment
        );
    }

    /// Creates an entry point with raw standard streams and a working directory.
    ///
    /// The streams are not closed by this command. Textual stdin is decoded as
    /// UTF-8; embedded mode preserves every byte.
    ///
    /// @param standardInput the input stream
    /// @param standardOutput the binary output stream
    /// @param workingDirectory the base directory for relative inputs
    SassFXMain(
            InputStream standardInput,
            OutputStream standardOutput,
            Path workingDirectory
    ) {
        this(
                standardInput,
                standardOutput,
                workingDirectory,
                System.getenv("SASS_PATH")
        );
    }

    /// Creates an entry point with raw standard streams, a working directory,
    /// and an injected `SASS_PATH` value.
    ///
    /// The streams are not closed by this command. Textual stdin is decoded as
    /// UTF-8; embedded mode preserves every byte.
    ///
    /// @param standardInput the input stream
    /// @param standardOutput the binary output stream
    /// @param workingDirectory the base directory for relative inputs and load
    /// paths
    /// @param sassPathEnvironment the raw `SASS_PATH` value, or `null` to model
    /// an absent variable
    SassFXMain(
            InputStream standardInput,
            OutputStream standardOutput,
            Path workingDirectory,
            @Nullable String sassPathEnvironment
    ) {
        this.standardInputStream = Objects.requireNonNull(
                standardInput,
                "standardInput"
        );
        this.standardOutputStream = Objects.requireNonNull(
                standardOutput,
                "standardOutput"
        );
        this.standardInputReader = new InputStreamReader(
                standardInputStream,
                StandardCharsets.UTF_8
        );
        this.workingDirectory = Objects.requireNonNull(
                workingDirectory,
                "workingDirectory"
        ).toAbsolutePath().normalize();
        this.sassPathEnvironment = sassPathEnvironment;
    }

    /// Compiles every root input to its selected output target.
    ///
    /// @return status {@code 0} on success, {@code 64} for usage errors,
    /// {@code 65} for Sass failures, or {@code 66} for IO failures
    @Override
    public Integer call() {
        var commandLine = commandLine();
        var err = commandLine.getErr();
        var out = commandLine.getOut();

        if (embedded) {
            if (!inputs.isEmpty()
                    || commandLine.getParseResult().matchedOptions().stream()
                    .anyMatch(option ->
                            !Arrays.asList(option.names()).contains("--embedded")
                    )) {
                err.println(
                        "sassfx: --embedded is not intended to be executed with additional arguments."
                );
                return USAGE_EXIT_STATUS;
            }
            return new EmbeddedCompiler().run(
                    standardInputStream,
                    standardOutputStream
            );
        }

        if (interactive) {
            return runInteractive(out, err);
        }

        if (inputs.isEmpty() && !standardInput) {
            printUsageFailure(commandLine, "Compile Sass to CSS.");
            return USAGE_EXIT_STATUS;
        }

        OutputTarget<?> selectedTarget;
        CompileOptions compileOptions;
        CliCompilationPlan plan;
        CliOutputPolicy outputPolicy;
        String outputExtension;
        try {
            selectedTarget = selectedTarget();
            outputExtension = selectedTarget instanceof BssTarget
                    ? ".bss"
                    : ".css";
            validateTargetOptions(selectedTarget);
            plan = CliCompilationPlan.create(
                    inputs,
                    output,
                    standardInput,
                    indented,
                    outputExtension
            );
            validatePlan(selectedTarget, plan);
            validateIncrementalPlan(plan);
            outputPolicy = outputPolicy(selectedTarget, plan);
            compileOptions = compileOptions(outputPolicy.sourceMap());
        } catch (IllegalArgumentException failure) {
            printUsageFailure(commandLine, Objects.requireNonNullElse(
                    failure.getMessage(),
                    "invalid output options"
            ));
            return USAGE_EXIT_STATUS;
        } catch (IOException failure) {
            printIoFailure(failure, err);
            return IO_EXIT_STATUS;
        }

        @Nullable String stdinContents = null;
        try {
            if (plan.jobs().stream().anyMatch(job -> job.source() == null)) {
                stdinContents = readStandardInput();
            }
        } catch (IOException failure) {
            printIoFailure(failure, err);
            return IO_EXIT_STATUS;
        }

        var stdinUrl = workingDirectory.resolve("stdin").toUri();
        var color = colorEnabled();
        var diagnosticPrinter = new DiagnosticPrinter(
                color,
                unicodeEnabled(),
                workingDirectory,
                stdinContents == null ? null : stdinUrl,
                stdinContents
        );
        var context = new CliExecutionContext(
                selectedTarget,
                compileOptions,
                outputPolicy,
                stdinUrl,
                stdinContents,
                diagnosticPrinter,
                color,
                quiet,
                out,
                err,
                this::shouldPrintDiagnostic
        );
        if (!watch) {
            return runJobs(
                    context,
                    plan.jobs(),
                    update,
                    Map.of()
            ).status();
        }
        try {
            return watch(
                    context,
                    plan,
                    outputExtension
            );
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException failure) {
            printIoFailure(failure, err);
            if (trace) {
                printImplementationTrace(failure, err);
            }
            return IO_EXIT_STATUS;
        }
    }

    /// Runs the persistent line-oriented SassScript shell.
    ///
    /// @param out standard output used for prompts, values, and errors
    /// @param err standard error used for warnings and startup IO failures
    /// @return status {@code 0} after EOF, {@code 64} for invalid options, or
    /// {@code 66} when standard input cannot be read
    private int runInteractive(
            java.io.PrintWriter out,
            java.io.PrintWriter err
    ) {
        final SassInteractiveSession session;
        try {
            validateInteractiveOptions();
            session = new SassInteractiveSession(
                    compileOptions(false),
                    workingDirectory
            );
        } catch (IllegalArgumentException failure) {
            printUsageFailure(commandLine(), Objects.requireNonNullElse(
                    failure.getMessage(),
                    "invalid interactive options"
            ));
            return USAGE_EXIT_STATUS;
        }

        var diagnosticPrinter = new DiagnosticPrinter(
                colorEnabled(),
                unicodeEnabled(),
                workingDirectory,
                null,
                null
        );
        var emittedDiagnostic = printInteractiveDiagnostics(
                session.drainDiagnostics(),
                diagnosticPrinter,
                err
        );
        var reader = standardInputReader instanceof BufferedReader buffered
                ? buffered
                : new BufferedReader(standardInputReader);
        try {
            @Nullable String line;
            while ((line = reader.readLine()) != null) {
                out.println(">> " + line);
                out.flush();
                try {
                    var result = session.evaluate(line);
                    emittedDiagnostic |= printInteractiveDiagnostics(
                            result.diagnostics(),
                            diagnosticPrinter,
                            err
                    );
                    if (result.value() != null) {
                        out.println(result.value());
                        out.flush();
                    }
                } catch (SassCompilationException failure) {
                    var diagnostics = failure.diagnostics();
                    var lineEmittedDiagnostic = diagnostics.stream()
                            .skip(1)
                            .anyMatch(diagnostic ->
                                    diagnostic.severity()
                                            != DiagnosticSeverity.ERROR
                            );
                    emittedDiagnostic |= lineEmittedDiagnostic;
                    if (!quiet) {
                        printNonErrorDiagnostics(
                                diagnostics,
                                diagnosticPrinter,
                                err
                        );
                        err.flush();
                    }

                    var primary = failure.primaryDiagnostic();
                    @Nullable SourceSpan span = primary.span();
                    if (span == null || span.url() != null) {
                        out.println(diagnosticPrinter.format(failure));
                    } else if (!quiet && emittedDiagnostic) {
                        out.println(diagnosticPrinter.formatInteractiveError(
                                primary,
                                line
                        ));
                    } else {
                        printCompactInteractiveFailure(
                                line,
                                failure,
                                out
                        );
                    }
                    out.flush();
                }
            }
            return 0;
        } catch (IOException failure) {
            printIoFailure(failure, err);
            if (trace) {
                printImplementationTrace(failure, err);
            }
            return IO_EXIT_STATUS;
        }
    }

    /// Prints one interactive diagnostic batch unless quiet mode is active.
    ///
    /// @param diagnostics diagnostics emitted since the preceding input
    /// @param diagnosticPrinter diagnostic formatter
    /// @param err warning and debug output
    /// @return whether the batch contains any diagnostic
    private boolean printInteractiveDiagnostics(
            @Unmodifiable List<Diagnostic> diagnostics,
            DiagnosticPrinter diagnosticPrinter,
            java.io.PrintWriter err
    ) {
        if (!quiet) {
            printNonErrorDiagnostics(
                    diagnostics,
                    diagnosticPrinter,
                    err
            );
            err.flush();
        }
        return !diagnostics.isEmpty();
    }

    /// Prints a compact pointer beneath one failed interactive input line.
    ///
    /// @param line the complete physical input line
    /// @param failure the line failure
    /// @param out standard output
    private void printCompactInteractiveFailure(
            String line,
            SassCompilationException failure,
            java.io.PrintWriter out
    ) {
        var diagnostic = failure.primaryDiagnostic();
        var span = Objects.requireNonNull(
                diagnostic.span(),
                "compact interactive failure span"
        );
        var spacesBeforeError = 3 + Math.max(
                0,
                span.start().column()
        );
        var builder = new StringBuilder();
        if (colorEnabled()) {
            builder.append("\u001b[31m");
            if (span.start().column() < line.length()) {
                builder.append("\u001b[1F\u001b[")
                        .append(spacesBeforeError)
                        .append('C')
                        .append(span.text())
                        .append('\n');
            }
        }
        builder.append(" ".repeat(spacesBeforeError))
                .append("^".repeat(Math.max(
                        1,
                        span.end().offset() - span.start().offset()
                )))
                .append('\n');
        if (colorEnabled()) {
            builder.append("\u001b[0m");
        }
        builder.append("Error: ").append(diagnostic.message());
        out.println(builder);
        if (trace) {
            printImplementationTrace(failure, out);
        }
    }

    /// Validates options whose output semantics are incompatible with a shell.
    ///
    /// @throws IllegalArgumentException if an incompatible option was explicit
    private void validateInteractiveOptions() {
        if (pollOption != null && !watch) {
            throw new IllegalArgumentException(
                    "--poll may not be passed without --watch."
            );
        }
        var incompatibleOptions = List.of(
                "--stdin",
                "--indented",
                "--style",
                "--source-map",
                "--source-map-urls",
                "--embed-sources",
                "--embed-source-map",
                "--update",
                "--watch",
                "--output",
                "--target"
        );
        for (var option : incompatibleOptions) {
            if (isOptionSpecified(option)) {
                throw new IllegalArgumentException(
                        option + " isn't allowed with --interactive."
                );
            }
        }
    }

    /// Compiles a stable ordered batch and records its dependency snapshots.
    ///
    /// @param context immutable invocation settings
    /// @param jobs jobs to execute in order
    /// @param incremental whether fresh destinations may be skipped
    /// @param previousStates prior watch states keyed by normalized source path
    /// @return the aggregate status and attempted job states
    private BatchResult runJobs(
            CliExecutionContext context,
            Collection<CliCompilationPlan.Job> jobs,
            boolean incremental,
            Map<Path, WatchJobState> previousStates
    ) {
        var states = new LinkedHashMap<Path, WatchJobState>();
        var status = 0;
        var engine = new CliCompilationEngine(context);
        for (var job : jobs) {
            var dependencyTracker = new SassDependencyTracker();
            try {
                var compilation = engine.compile(
                        job,
                        incremental,
                        dependencyTracker
                );
                if (job.source() != null) {
                    states.put(
                            pathKey(job.source()),
                            new WatchJobState(
                                job,
                                compilation.dependencies(),
                                compilation.resolutionCandidates(),
                                compilation.resolutionComplete()
                            )
                    );
                }
                if (compilation.compiled()
                        && (update || watch)
                        && !quiet) {
                    printCompiledStatus(job, context);
                }
            } catch (SassCompilationException failure) {
                try {
                    engine.handleSassFailure(
                            job.destination(),
                            failure
                    );
                } catch (IOException outputFailure) {
                    printIoFailure(outputFailure, context.err());
                    status = IO_EXIT_STATUS;
                }
                if (!quiet) {
                    printNonErrorDiagnostics(
                            failure.diagnostics(),
                            context.diagnosticPrinter(),
                            context.err()
                    );
                }
                context.err().println(
                        context.diagnosticPrinter().format(failure)
                );
                if (trace) {
                    printImplementationTrace(failure, context.err());
                }
                status = Math.max(status, DATA_EXIT_STATUS);
                recordFailedState(
                        states,
                        job,
                        CliCompilationEngine.fileDependencies(
                                failure.loadedUrls(),
                                job.source()
                        ),
                        dependencyTracker
                );
                if (stopOnError) {
                    break;
                }
            } catch (IOException failure) {
                printIoFailure(failure, context.err());
                if (trace) {
                    printImplementationTrace(failure, context.err());
                }
                status = IO_EXIT_STATUS;
                recordIoFailedState(
                        states,
                        previousStates,
                        job,
                        dependencyTracker
                );
                if (stopOnError) {
                    break;
                }
            }
        }
        return new BatchResult(status, states);
    }

    /// Watches inputs and recompiles entrypoints affected by each change batch.
    ///
    /// @param context immutable invocation settings
    /// @param initialPlan the input plan established before watching begins
    /// @param outputExtension extension used for directory-derived destinations
    /// @return the greatest non-zero status observed before watching stops
    /// @throws IOException if filesystem observation or output cleanup fails
    /// @throws InterruptedException if the watching thread is interrupted
    private int watch(
            CliExecutionContext context,
            CliCompilationPlan initialPlan,
            String outputExtension
    ) throws IOException, InterruptedException {
        var currentPlan = initialPlan;
        var states = new LinkedHashMap<Path, WatchJobState>();
        var status = 0;

        try (var watcher = new CliFileWatcher(
                Boolean.TRUE.equals(pollOption)
        )) {
            watcher.watch(watchRoots(currentPlan, states.values()));

            var initial = runJobs(
                    context,
                    currentPlan.jobs(),
                    true,
                    states
            );
            states.putAll(initial.states());
            status = Math.max(status, initial.status());
            watcher.watch(watchRoots(currentPlan, states.values()));
            if (initial.status() != 0 && stopOnError) {
                return initial.status();
            }

            context.out().println(
                    "Sass is watching for changes. Press Ctrl-C to stop."
            );
            context.out().println();
            context.out().flush();

            while (true) {
                var changes = relevantChanges(watcher.take());
                if (changes.isEmpty()) {
                    continue;
                }

                var refreshedPlan = currentPlan.refreshDirectories(
                        indented,
                        outputExtension
                );
                removeDroppedEntrypoints(
                        currentPlan,
                        refreshedPlan,
                        states,
                        context
                );

                var changedPaths = new LinkedHashSet<Path>();
                for (var change : changes) {
                    changedPaths.add(change.path());
                }

                var dirtyJobs = new ArrayList<CliCompilationPlan.Job>();
                for (var job : refreshedPlan.jobs()) {
                    @Nullable Path source = job.source();
                    if (source == null) {
                        continue;
                    }
                    var key = pathKey(source);
                    @Nullable WatchJobState previous = states.get(key);
                    if (!Files.isRegularFile(source)) {
                        if (previous != null
                                && changedPaths.contains(key)) {
                            deleteOwnedOutput(previous.job(), context);
                            states.put(
                                    key,
                                    new WatchJobState(
                                            job,
                                            previous.dependencies(),
                                            previous.resolutionCandidates(),
                                            previous.resolutionComplete()
                                    )
                            );
                        }
                        continue;
                    }

                    if (previous == null
                            || changedPaths.stream().anyMatch(
                                    previous.dependencies()::contains
                            )
                            || changes.stream().anyMatch(change ->
                                    change.kind()
                                            != CliFileWatcher.Kind.MODIFY
                                    && (!previous.resolutionComplete()
                                    || previous.resolutionCandidates()
                                            .contains(change.path())))) {
                        dirtyJobs.add(job);
                    }
                }

                if (!dirtyJobs.isEmpty()) {
                    var batch = runJobs(
                            context,
                            dirtyJobs,
                            false,
                            states
                    );
                    states.putAll(batch.states());
                    status = Math.max(status, batch.status());
                    if (batch.status() != 0 && stopOnError) {
                        return batch.status();
                    }
                }

                currentPlan = refreshedPlan;
                watcher.watch(watchRoots(
                        currentPlan,
                        states.values()
                ));
            }
        }
    }

    /// Returns the directory trees that may affect a watched invocation.
    ///
    /// @param plan the current compilation plan
    /// @param states current dependency snapshots
    /// @return immutable normalized directory roots
    private @Unmodifiable Set<Path> watchRoots(
            CliCompilationPlan plan,
            Collection<WatchJobState> states
    ) {
        var roots = new LinkedHashSet<Path>();
        for (var mapping : plan.directoryMappings()) {
            roots.add(pathKey(mapping.source()));
        }
        for (var job : plan.explicitJobs()) {
            @Nullable Path source = job.source();
            if (source != null) {
                @Nullable Path parent = pathKey(source).getParent();
                if (parent != null) {
                    roots.add(parent);
                }
            }
        }
        for (var loadPath : effectiveLoadPaths()) {
            roots.add(pathKey(loadPath));
        }
        for (var state : states) {
            for (var dependency : state.dependencies()) {
                @Nullable Path parent = dependency.getParent();
                if (parent != null) {
                    roots.add(parent);
                }
            }
            for (var candidate : state.resolutionCandidates()) {
                @Nullable Path parent = candidate.getParent();
                if (parent != null) {
                    roots.add(parent);
                }
            }
        }
        return Set.copyOf(roots);
    }

    /// Keeps only stylesheet changes relevant to Sass resolution.
    ///
    /// @param changes one coalesced filesystem batch
    /// @return immutable relevant changes in observation order
    private static @Unmodifiable List<CliFileWatcher.Change> relevantChanges(
            @Unmodifiable List<CliFileWatcher.Change> changes
    ) {
        return changes.stream()
                .filter(change -> isStylesheetPath(change.path()))
                .toList();
    }

    /// Reports whether a path has a lowercase Sass-supported extension.
    ///
    /// @param path the changed path
    /// @return whether Sass resolution may consider the path
    private static boolean isStylesheetPath(Path path) {
        @Nullable Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            return false;
        }
        var fileName = fileNamePath.toString();
        return fileName.endsWith(".scss")
                || fileName.endsWith(".sass")
                || fileName.endsWith(".css");
    }

    /// Removes outputs whose directory-discovered entrypoints disappeared.
    ///
    /// @param previousPlan the plan used for the preceding watch cycle
    /// @param refreshedPlan the newly scanned directory plan
    /// @param states mutable states keyed by normalized source path
    /// @param context immutable invocation settings
    /// @throws IOException if an owned output cannot be deleted
    private void removeDroppedEntrypoints(
            CliCompilationPlan previousPlan,
            CliCompilationPlan refreshedPlan,
            Map<Path, WatchJobState> states,
            CliExecutionContext context
    ) throws IOException {
        var refreshedSources = new LinkedHashSet<Path>();
        for (var job : refreshedPlan.jobs()) {
            @Nullable Path source = job.source();
            if (source != null) {
                refreshedSources.add(pathKey(source));
            }
        }
        var explicitSources = new LinkedHashSet<Path>();
        for (var job : previousPlan.explicitJobs()) {
            @Nullable Path source = job.source();
            if (source != null) {
                explicitSources.add(pathKey(source));
            }
        }

        for (var job : previousPlan.jobs()) {
            @Nullable Path source = job.source();
            if (source == null) {
                continue;
            }
            var key = pathKey(source);
            if (!explicitSources.contains(key)
                    && !refreshedSources.contains(key)) {
                deleteOwnedOutput(job, context);
                states.remove(key);
            }
        }
    }

    /// Deletes a disappeared entrypoint's exact destination and map sidecar.
    ///
    /// @param job the removed entrypoint
    /// @param context immutable invocation settings
    /// @throws IOException if an existing owned output cannot be deleted
    private void deleteOwnedOutput(
            CliCompilationPlan.Job job,
            CliExecutionContext context
    ) throws IOException {
        @Nullable Path destination = job.destination();
        if (destination == null) {
            return;
        }
        deleteOwnedFile(destination, context);
        deleteOwnedFile(
                Path.of(destination.toString() + ".map"),
                context
        );
    }

    /// Deletes one exact output path and reports a successful deletion.
    ///
    /// @param path the output path owned by the watched entrypoint
    /// @param context immutable invocation settings
    /// @throws IOException if the existing file cannot be deleted
    private void deleteOwnedFile(
            Path path,
            CliExecutionContext context
    ) throws IOException {
        if (!Files.deleteIfExists(path)) {
            return;
        }
        if (context.color()) {
            context.out().print("\u001b[33m");
        }
        context.out().print("Deleted " + displayPath(path) + ".");
        if (context.color()) {
            context.out().print("\u001b[0m");
        }
        context.out().println();
        context.out().flush();
    }

    /// Records a Sass-failed job with the resolution work completed before the
    /// failure.
    ///
    /// @param states attempted states for the current batch
    /// @param job the failed job
    /// @param dependencies file URLs loaded before the failure
    /// @param dependencyTracker resolution candidates observed before failure
    private static void recordFailedState(
            Map<Path, WatchJobState> states,
            CliCompilationPlan.Job job,
            Set<Path> dependencies,
            SassDependencyTracker dependencyTracker
    ) {
        @Nullable Path source = job.source();
        if (source == null) {
            return;
        }
        var key = pathKey(source);
        var resolvedDependencies = new LinkedHashSet<Path>(dependencies);
        resolvedDependencies.add(key);
        states.put(
                key,
                new WatchJobState(
                        job,
                        resolvedDependencies,
                        dependencyTracker.candidatePaths(),
                        dependencyTracker.isComplete()
                )
        );
    }

    /// Records an IO-failed job using prior dependency evidence when available.
    ///
    /// @param states attempted states for the current batch
    /// @param previousStates prior watch states
    /// @param job the failed job
    /// @param dependencyTracker resolution candidates observed before failure
    private static void recordIoFailedState(
            Map<Path, WatchJobState> states,
            Map<Path, WatchJobState> previousStates,
            CliCompilationPlan.Job job,
            SassDependencyTracker dependencyTracker
    ) {
        @Nullable Path source = job.source();
        if (source == null) {
            return;
        }
        var key = pathKey(source);
        @Nullable WatchJobState previous = previousStates.get(key);
        var dependencies = previous == null
                ? Set.of(key)
                : previous.dependencies();
        var candidates = dependencyTracker.candidatePaths().isEmpty()
                && previous != null
                ? previous.resolutionCandidates()
                : dependencyTracker.candidatePaths();
        var complete = dependencyTracker.isComplete()
                && (previous == null || previous.resolutionComplete());
        states.put(
                key,
                new WatchJobState(
                        job,
                        dependencies,
                        candidates,
                        complete
                )
        );
    }

    /// Writes one successful incremental-compilation status line.
    ///
    /// @param job the compiled job
    /// @param context immutable invocation settings
    private void printCompiledStatus(
            CliCompilationPlan.Job job,
            CliExecutionContext context
    ) {
        var timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );
        var source = job.source() == null
                ? "stdin"
                : displayPath(job.source());
        var destination = displayPath(
                Objects.requireNonNull(job.destination())
        );
        if (context.color()) {
            context.out().print("\u001b[90m");
        }
        context.out().print("[" + timestamp + "] ");
        if (context.color()) {
            context.out().print("\u001b[32m");
        }
        context.out().print(
                "Compiled " + source + " to " + destination + "."
        );
        if (context.color()) {
            context.out().print("\u001b[0m");
        }
        context.out().println();
        context.out().flush();
    }

    /// Returns a working-directory-relative display path when possible.
    ///
    /// @param path the path to display
    /// @return a relative or absolute normalized path string
    private String displayPath(Path path) {
        var absolute = pathKey(path);
        if (absolute.getRoot() != null
                && absolute.getRoot().equals(workingDirectory.getRoot())) {
            var relative = workingDirectory.relativize(absolute);
            if (!relative.startsWith("..")) {
                return relative.toString();
            }
        }
        return absolute.toString();
    }

    /// Returns the normalized absolute identity of one path.
    ///
    /// @param path the path to normalize
    /// @return an absolute normalized path
    private static Path pathKey(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /// Reads all standard-input characters without closing the injected reader.
    ///
    /// @return the complete standard-input text
    /// @throws IOException if the input cannot be read
    private String readStandardInput() throws IOException {
        var result = new StringBuilder();
        var buffer = new char[8192];
        int count;
        while ((count = standardInputReader.read(buffer)) >= 0) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    /// Prints a command-line IO failure.
    ///
    /// @param failure the failed filesystem or stream operation
    /// @param err standard error
    private static void printIoFailure(
            IOException failure,
            java.io.PrintWriter err
    ) {
        err.println("sassfx: " + Objects.requireNonNullElse(
                failure.getMessage(),
                failure.getClass().getSimpleName()
        ));
    }

    /// Writes non-error diagnostics from a completed or failed compilation.
    ///
    /// @param diagnostics diagnostics in their container-defined order
    /// @param diagnosticPrinter the command-line diagnostic formatter
    /// @param err the standard-error writer
    private void printNonErrorDiagnostics(
            List<Diagnostic> diagnostics,
            DiagnosticPrinter diagnosticPrinter,
            java.io.PrintWriter err
    ) {
        for (var diagnostic : diagnostics) {
            if (diagnostic.severity() != DiagnosticSeverity.ERROR
                    && shouldPrintDiagnostic(diagnostic)) {
                err.println(diagnosticPrinter.format(diagnostic));
            }
        }
    }

    /// Returns whether one diagnostic has not already been emitted as an
    /// invocation-level configuration warning.
    ///
    /// @param diagnostic the diagnostic considered for output
    /// @return {@code true} if the diagnostic should be printed
    private boolean shouldPrintDiagnostic(Diagnostic diagnostic) {
        if (!configurationWarningMessages.contains(diagnostic.message())) {
            return true;
        }
        return emittedConfigurationWarningMessages.add(diagnostic.message());
    }

    /// Writes the Java implementation trace associated with one failure.
    ///
    /// The primary exception's underlying cause is preferred when available,
    /// because compiler boundary exceptions otherwise hide the operation that
    /// originally failed. Nested causes are retained.
    ///
    /// @param failure the Sass or IO failure
    /// @param err standard error
    private static void printImplementationTrace(
            Throwable failure,
            java.io.PrintWriter err
    ) {
        err.println();
        @Nullable Throwable current = failure.getCause() == null
                ? failure
                : failure.getCause();
        var first = true;
        while (current != null) {
            if (!first) {
                err.println("Caused by: " + current);
            }
            for (var frame : current.getStackTrace()) {
                err.println("\tat " + frame);
            }
            current = current.getCause();
            first = false;
        }
    }

    /// Creates immutable compile options from command-line diagnostic flags.
    ///
    /// @param emitSourceMap whether compiler-level mapping is enabled
    /// @return the resolved compile options
    /// @throws IllegalArgumentException if a deprecation identifier or version
    /// is invalid
    private CompileOptions compileOptions(boolean emitSourceMap) {
        var fatal = fatalDeprecations(fatalDeprecations);
        var silence = deprecations(silenceDeprecations);
        var future = deprecations(futureDeprecations);
        var diagnosticOptions = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                quietDeps,
                verbose,
                silence,
                fatal,
                future
        );
        configurationWarningMessages.clear();
        emittedConfigurationWarningMessages.clear();
        for (var warning
                : diagnosticOptions.configurationWarnings()) {
            configurationWarningMessages.add(warning.message());
        }
        return new CompileOptions(
                emitSourceMap,
                effectiveLoadPaths(),
                null,
                packageImporters(),
                List.of(),
                diagnosticOptions,
                embedSources
        );
    }

    /// Returns explicit load paths followed by paths from `SASS_PATH`.
    ///
    /// Relative paths and empty environment entries are resolved against the
    /// invocation working directory. Empty entries are retained because Dart
    /// Sass treats them as the current working directory.
    ///
    /// @return immutable absolute load paths in importer precedence order
    private @Unmodifiable List<Path> effectiveLoadPaths() {
        var result = new ArrayList<Path>(loadPaths.size() + 4);
        for (var loadPath : loadPaths) {
            result.add(resolveLoadPath(loadPath));
        }
        if (sassPathEnvironment != null) {
            var environmentPaths = sassPathEnvironment.split(
                    Pattern.quote(File.pathSeparator),
                    -1
            );
            for (var environmentPath : environmentPaths) {
                result.add(resolveLoadPath(Path.of(environmentPath)));
            }
        }
        return List.copyOf(result);
    }

    /// Resolves one load path against the invocation working directory.
    ///
    /// @param loadPath the path supplied by the command line or environment
    /// @return the normalized absolute path
    private Path resolveLoadPath(Path loadPath) {
        var resolved = loadPath.isAbsolute()
                ? loadPath
                : workingDirectory.resolve(loadPath);
        return resolved.toAbsolutePath().normalize();
    }

    /// Creates the built-in package importers selected on the command line.
    ///
    /// @return immutable importers in option occurrence order
    /// @throws IllegalArgumentException if a package importer type is unknown
    private List<SassImporter> packageImporters() {
        var result = new ArrayList<SassImporter>(
                packageImporterTypes.size()
        );
        for (var type : packageImporterTypes) {
            if (!"node".equals(type)) {
                throw new IllegalArgumentException(
                        "unsupported package importer '" + type
                                + "'; expected 'node'"
                );
            }
            result.add(new SassNodePackageImporter(workingDirectory));
        }
        return List.copyOf(result);
    }

    /// Resolves source-map and failure-output behavior for a compilation plan.
    ///
    /// @param outputTarget the selected output target
    /// @param plan the fully expanded compilation plan
    /// @return the resolved output policy
    /// @throws IllegalArgumentException if output options conflict
    private CliOutputPolicy outputPolicy(
            OutputTarget<?> outputTarget,
            CliCompilationPlan plan
    ) {
        var urlMode = switch (sourceMapUrls.toLowerCase(Locale.ROOT)) {
            case "relative" -> CliSourceMap.UrlMode.RELATIVE;
            case "absolute" -> CliSourceMap.UrlMode.ABSOLUTE;
            default -> throw new IllegalArgumentException(
                    "unsupported source-map URL type '" + sourceMapUrls
                            + "'; expected 'relative' or 'absolute'"
            );
        };

        var sourceMapUrlsParsed = isOptionSpecified("--source-map-urls");
        var sourceMapParsed = isOptionSpecified("--source-map");
        var sourceMapEnabled = sourceMapOption == null || sourceMapOption;
        if (!sourceMapEnabled) {
            if (sourceMapUrlsParsed) {
                throw new IllegalArgumentException(
                        "--source-map-urls isn't allowed with --no-source-map."
                );
            } else if (embedSources) {
                throw new IllegalArgumentException(
                        "--embed-sources isn't allowed with --no-source-map."
                );
            } else if (embedSourceMap) {
                throw new IllegalArgumentException(
                        "--embed-source-map isn't allowed with --no-source-map."
                );
            }
        }

        var writesToStdout = plan.jobs().size() == 1
                && plan.jobs().get(0).destination() == null;
        boolean emitSourceMap;
        if (outputTarget instanceof JavaFXCssTarget
                && charsetOption != null) {
            throw new IllegalArgumentException(
                    "charset options are not supported for JavaFX CSS targets"
            );
        }
        if (outputTarget instanceof BssTarget) {
            if (sourceMapEnabled && sourceMapParsed
                    || sourceMapUrlsParsed
                    || embedSources
                    || embedSourceMap) {
                throw new IllegalArgumentException(
                        "source maps are not supported for the bss target"
                );
            }
            if (charsetOption != null) {
                throw new IllegalArgumentException(
                        "charset options are not supported for the bss target"
                );
            }
            if (Boolean.TRUE.equals(errorCss)) {
                throw new IllegalArgumentException(
                        "--error-css is not supported for the bss target"
                );
            }
            emitSourceMap = false;
        } else if (!writesToStdout) {
            emitSourceMap = sourceMapEnabled;
        } else if (sourceMapUrlsParsed
                && urlMode == CliSourceMap.UrlMode.RELATIVE) {
            throw new IllegalArgumentException(
                    "--source-map-urls=relative isn't allowed when printing to stdout."
            );
        } else if (embedSourceMap) {
            emitSourceMap = sourceMapEnabled;
        } else if (sourceMapEnabled && sourceMapParsed) {
            throw new IllegalArgumentException(
                    "When printing to stdout, --source-map requires --embed-source-map."
            );
        } else if (sourceMapUrlsParsed) {
            throw new IllegalArgumentException(
                    "When printing to stdout, --source-map-urls requires --embed-source-map."
            );
        } else if (embedSources) {
            throw new IllegalArgumentException(
                    "When printing to stdout, --embed-sources requires --embed-source-map."
            );
        } else {
            emitSourceMap = false;
        }

        var hasFileDestination = plan.jobs().stream()
                .anyMatch(job -> job.destination() != null);
        var emitErrorCss = !(outputTarget instanceof BssTarget)
                && (errorCss != null ? errorCss : hasFileDestination);
        return new CliOutputPolicy(
                emitSourceMap,
                urlMode,
                embedSources,
                embedSourceMap,
                emitErrorCss
        );
    }

    /// Returns whether charset markers are enabled after applying the default.
    ///
    /// @return {@code true} unless {@code --no-charset} was parsed
    private boolean charsetEnabled() {
        return charsetOption == null || charsetOption;
    }

    /// Returns whether ANSI terminal styling is enabled.
    ///
    /// Explicit command-line selection takes precedence. The default follows
    /// Dart Sass's stdout-terminal rule as closely as the standard Java API
    /// permits: an attached process console is treated as an ANSI-capable
    /// stdout terminal.
    ///
    /// @return the resolved color setting
    private boolean colorEnabled() {
        return colorOption != null ? colorOption : System.console() != null;
    }

    /// Returns whether diagnostic frames use Unicode glyphs.
    ///
    /// @return {@code true} unless {@code --no-unicode} was parsed
    private boolean unicodeEnabled() {
        return unicodeOption == null || unicodeOption;
    }

    /// Parses fatal deprecation identifiers and version selectors.
    private static Set<SassDeprecation> fatalDeprecations(
            List<String> values
    ) {
        var result = new LinkedHashSet<SassDeprecation>();
        for (var value : values) {
            @Nullable SassDeprecation deprecation =
                    SassDeprecation.fromId(value);
            if (deprecation != null) {
                result.add(deprecation);
                continue;
            }

            var matcher = DEPRECATION_VERSION.matcher(value);
            if (!matcher.matches()) {
                throw invalidDeprecation(value);
            }
            var major = Integer.parseInt(matcher.group(1));
            var minor = Integer.parseInt(matcher.group(2));
            var patch = Integer.parseInt(matcher.group(3));
            if (major > 1
                    || major == 1 && minor > 101
                    || major == 1 && minor == 101 && patch > 3) {
                throw new IllegalArgumentException(
                        "Invalid version " + value + ". --fatal-deprecation "
                                + "requires a version less than or equal to the "
                                + "current Dart Sass version."
                );
            }
            result.addAll(SassDeprecation.forVersion(major, minor, patch));
        }
        return Set.copyOf(result);
    }

    /// Parses deprecation identifiers without accepting versions.
    private static Set<SassDeprecation> deprecations(List<String> values) {
        var result = new LinkedHashSet<SassDeprecation>();
        for (var value : values) {
            @Nullable SassDeprecation deprecation =
                    SassDeprecation.fromId(value);
            if (deprecation == null) {
                throw invalidDeprecation(value);
            }
            result.add(deprecation);
        }
        return Set.copyOf(result);
    }

    /// Creates the command-line usage failure for an unknown deprecation.
    private static IllegalArgumentException invalidDeprecation(String value) {
        return new IllegalArgumentException(
                "Invalid deprecation \"" + value + "\"."
        );
    }

    /// Validates options that depend on the selected output target.
    ///
    /// @param selectedTarget the parsed output target
    /// @throws IllegalArgumentException if the target or its options are unsupported
    private void validateTargetOptions(OutputTarget<?> selectedTarget) {
        if (selectedTarget instanceof BssTarget
                && isOptionSpecified("--style")) {
            throw new IllegalArgumentException(
                    "--style is supported only for css targets"
            );
        }
    }

    /// Validates destination requirements after expanding every input.
    ///
    /// @param outputTarget the selected output target
    /// @param plan the fully expanded compilation plan
    /// @throws IllegalArgumentException if a job cannot use its destination
    private static void validatePlan(
            OutputTarget<?> outputTarget,
            CliCompilationPlan plan
    ) {
        if (outputTarget instanceof BssTarget
                && plan.jobs().stream().anyMatch(
                        job -> job.destination() == null
                )) {
            throw new IllegalArgumentException(
                    "BSS output requires an output path; use -o/--output, "
                            + "a positional output path, or an input:output mapping"
            );
        }
    }

    /// Validates update and watch destination requirements.
    ///
    /// @param plan the fully expanded compilation plan
    /// @throws IllegalArgumentException if an incremental option is invalid
    private void validateIncrementalPlan(CliCompilationPlan plan) {
        if (pollOption != null && !watch) {
            throw new IllegalArgumentException(
                    "--poll may not be passed without --watch."
            );
        }
        if (!update && !watch) {
            return;
        }
        var option = watch ? "--watch" : "--update";
        if (standardInput) {
            throw new IllegalArgumentException(
                    option + " is not allowed with --stdin."
            );
        }
        if (plan.jobs().stream().anyMatch(
                job -> job.destination() == null
        )) {
            throw new IllegalArgumentException(
                    option + " is not allowed when printing to stdout."
            );
        }
        if (watch && plan.jobs().stream().anyMatch(
                job -> job.source() == null
        )) {
            throw new IllegalArgumentException(
                    "--watch does not support standard-input mappings."
            );
        }
    }

    /// Reports whether an option occurred explicitly in the command line.
    ///
    /// @param optionName one declared option name, including its leading dashes
    /// @return {@code true} if the parser matched the option
    private boolean isOptionSpecified(String optionName) {
        return commandLine().getParseResult().hasMatchedOption(optionName);
    }

    /// Prints one Dart Sass-compatible usage failure to standard output.
    ///
    /// @param commandLine the active command line
    /// @param message the primary usage diagnostic
    private static void printUsageFailure(
            CommandLine commandLine,
            String message
    ) {
        Objects.requireNonNull(commandLine, "commandLine");
        Objects.requireNonNull(message, "message");
        var out = commandLine.getOut();
        out.println(message);
        out.println();
        commandLine.usage(out);
        out.flush();
    }

    /// Retains the latest dependency state for one watched entrypoint.
    ///
    /// @param job current source and destination mapping
    /// @param dependencies last known normalized file dependencies
    /// @param resolutionCandidates filesystem candidates consulted by imports
    /// @param resolutionComplete whether custom importers were absent
    @NotNullByDefault
    private record WatchJobState(
            CliCompilationPlan.Job job,
            @Unmodifiable Set<Path> dependencies,
            @Unmodifiable Set<Path> resolutionCandidates,
            boolean resolutionComplete
    ) {
        /// Creates an immutable watch state.
        private WatchJobState {
            Objects.requireNonNull(job, "job");
            dependencies = Set.copyOf(dependencies);
            resolutionCandidates = Set.copyOf(resolutionCandidates);
        }
    }

    /// Describes one attempted compilation batch.
    ///
    /// @param status aggregate process-compatible status
    /// @param states states produced by attempted file-backed jobs
    @NotNullByDefault
    private record BatchResult(
            int status,
            @Unmodifiable Map<Path, WatchJobState> states
    ) {
        /// Creates an immutable batch result.
        private BatchResult {
            states = Map.copyOf(states);
        }
    }

    /// Resolves the complete output target selected by the command line.
    ///
    /// @return the fully configured output target
    /// @throws IllegalArgumentException if the target value is unsupported
    private OutputTarget<?> selectedTarget() {
        final OutputTarget<?> parsedTarget;
        try {
            parsedTarget = OutputTarget.parse(target);
        } catch (IllegalArgumentException ignored) {
            throw unsupportedTarget();
        }

        if (parsedTarget instanceof BssTarget) {
            return parsedTarget;
        }
        var outputStyle = selectedOutputStyle();
        if (parsedTarget instanceof CssTarget) {
            return new CssTarget(outputStyle, charsetEnabled());
        }
        if (parsedTarget instanceof JavaFXCssTarget javaFXCssTarget) {
            return new JavaFXCssTarget(
                    javaFXCssTarget.javaFXTarget(),
                    outputStyle
            );
        }
        throw new IllegalStateException(
                "unsupported output target implementation: "
                        + parsedTarget.getClass().getName()
        );
    }

    /// Creates the usage failure for an unsupported output target.
    ///
    /// @return the target-specific usage failure
    private IllegalArgumentException unsupportedTarget() {
        return new IllegalArgumentException(
                "unsupported output target '" + target
                        + "'; expected 'css', 'css/javafx@8' through "
                        + "'css/javafx@27', or 'bss/javafx@8' through "
                        + "'bss/javafx@27'"
        );
    }

    /// Resolves the output style selected by the command-line option.
    ///
    /// @return the selected output style
    /// @throws IllegalArgumentException if the option value is unsupported
    private OutputStyle selectedOutputStyle() {
        return switch (style.toLowerCase(Locale.ROOT)) {
            case "expanded" -> OutputStyle.EXPANDED;
            case "compressed" -> OutputStyle.COMPRESSED;
            default -> throw new IllegalArgumentException(
                    "unsupported output style '" + style
                            + "'; expected 'expanded' or 'compressed'"
            );
        };
    }

    /// Executes the command and terminates the process with its status.
    ///
    /// @param args the command-line arguments
    public static void main(String[] args) {
        if (isEmbeddedVersionRequest(args)) {
            System.out.println(EmbeddedCompiler.versionJson());
            return;
        }
        System.exit(execute(args));
    }

    /// Executes the command without terminating the current process.
    ///
    /// @param args the command-line arguments
    /// @return the process-compatible exit status
    static int execute(String... args) {
        if (isEmbeddedVersionRequest(args)) {
            System.out.println(EmbeddedCompiler.versionJson());
            return 0;
        }
        return configure(new CommandLine(new SassFXMain())).execute(args);
    }

    /// Reports whether arguments request standalone embedded version JSON.
    ///
    /// @param args the raw process arguments
    /// @return whether the arguments are exactly {@code --embedded --version}
    /// in either order
    private static boolean isEmbeddedVersionRequest(String[] args) {
        return args.length == 2
                && Arrays.asList(args).contains("--embedded")
                && Arrays.asList(args).contains("--version");
    }

    /// Configures parser behavior required by Sass magic stdin operands.
    ///
    /// @param commandLine the command line to configure
    /// @return the supplied command line
    static CommandLine configure(CommandLine commandLine) {
        return commandLine
                .setUnmatchedOptionsArePositionalParams(true)
                .setParameterExceptionHandler((failure, arguments) -> {
                    printUsageFailure(
                            failure.getCommandLine(),
                            Objects.requireNonNullElse(
                                    failure.getMessage(),
                                    "Invalid command-line arguments."
                            )
                    );
                    return USAGE_EXIT_STATUS;
                })
                .setExecutionExceptionHandler((
                        failure,
                        activeCommandLine,
                        parseResult
                ) -> {
                    var command = activeCommandLine.<SassFXMain>getCommand();
                    var err = activeCommandLine.getErr();
                    if (command.colorEnabled()) {
                        err.print("\u001b[31m\u001b[1m");
                    }
                    err.print("Unexpected exception:");
                    if (command.colorEnabled()) {
                        err.print("\u001b[0m");
                    }
                    err.println();
                    err.println(failure);
                    printImplementationTrace(failure, err);
                    return SOFTWARE_EXIT_STATUS;
                });
    }

    /// Returns the command line associated with the injected specification.
    ///
    /// @return the active command line
    private CommandLine commandLine() {
        return Objects.requireNonNull(commandSpec, "commandSpec").commandLine();
    }
}
