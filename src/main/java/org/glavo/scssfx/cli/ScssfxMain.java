// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.glavo.scssfx.BssTarget;
import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.JavaFXCssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.OutputTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassFileSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;

/// Provides the command-line entry point for SCSSFX.
@Command(
        name = "scssfx",
        description = "Compiles Sass stylesheets to CSS, JavaFX CSS, or JavaFX BSS.",
        mixinStandardHelpOptions = true,
        version = "scssfx 0.1.0-SNAPSHOT"
)
@NotNullByDefault
public final class ScssfxMain implements Callable<Integer> {
    /// The exit status used when a requested compilation fails.
    private static final int FAILURE_EXIT_STATUS = 1;

    /// The exit status used when no compilation input is supplied.
    private static final int USAGE_EXIT_STATUS = 2;

    /// The input paths collected by Picocli.
    @Parameters(
            arity = "0..*",
            paramLabel = "INPUT",
            description = "A stylesheet file to compile, optionally followed by an output path."
    )
    private final List<Path> inputs = new ArrayList<>();

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
            description = "Select output target: css, javafx-css, or bss (default: ${DEFAULT-VALUE})."
    )
    private String target = "css";

    /// The textual layout style selected by the caller.
    @Option(
            names = "--style",
            defaultValue = "expanded",
            paramLabel = "STYLE",
            description = "Select CSS output style: expanded or compressed (default: ${DEFAULT-VALUE})."
    )
    private String style = "expanded";

    /// The JavaFX compatibility level selected for JavaFX CSS and BSS targets.
    @Option(
            names = "--javafx-compatibility",
            defaultValue = "17",
            paramLabel = "VERSION",
            description = "Select JavaFX compatibility: 17 or 27 (default: ${DEFAULT-VALUE})."
    )
    private String javafxCompatibility = "17";

    /// The command specification injected by Picocli before invocation.
    @Spec
    private @Nullable CommandSpec commandSpec;

    /// Creates a command-line entry point.
    public ScssfxMain() {
    }

    /// Compiles one SCSS file to its selected output target.
    ///
    /// @return status {@code 0} on success, {@code 2} for usage errors, or
    /// {@code 1} for compilation and IO failures
    @Override
    public Integer call() {
        var commandLine = commandLine();
        var err = commandLine.getErr();
        var out = commandLine.getOut();

        if (inputs.isEmpty()) {
            commandLine.usage(out);
            return USAGE_EXIT_STATUS;
        }
        if (inputs.size() > 2) {
            err.println("scssfx: only one input and one optional output path are supported");
            return USAGE_EXIT_STATUS;
        }
        if (inputs.size() == 2 && output != null) {
            err.println("scssfx: cannot combine a positional output path with -o/--output");
            return USAGE_EXIT_STATUS;
        }

        var input = inputs.get(0);
        @Nullable Path destination = output != null
                ? output
                : inputs.size() == 2 ? inputs.get(1) : null;
        var targetName = target.toLowerCase(Locale.ROOT);
        JavaFXCompatibility selectedCompatibility;
        @Nullable OutputStyle selectedOutputStyle;
        try {
            validateTargetOptions(targetName, destination);
            selectedCompatibility = selectedJavaFxCompatibility();
            selectedOutputStyle = "bss".equals(targetName) ? null : selectedOutputStyle();
        } catch (IllegalArgumentException failure) {
            err.println("scssfx: " + Objects.requireNonNullElse(
                    failure.getMessage(),
                    "invalid output options"
            ));
            return USAGE_EXIT_STATUS;
        }

        try {
            if (!Files.isRegularFile(input)) {
                err.println("scssfx: input is not a file: " + input);
                return FAILURE_EXIT_STATUS;
            }
            @Nullable Syntax syntax = Syntax.forPath(input);
            if (syntax != Syntax.SCSS && syntax != Syntax.SASS) {
                err.println("scssfx: only .scss and .sass input are supported in this build");
                return FAILURE_EXIT_STATUS;
            }
            var selectedSyntax = Objects.requireNonNull(syntax);

            return switch (targetName) {
                case "css" -> compileText(
                        input,
                        selectedSyntax,
                        destination,
                        new CssTarget(Objects.requireNonNull(selectedOutputStyle), true),
                        out,
                        err
                );
                case "javafx-css" -> compileText(
                        input,
                        selectedSyntax,
                        destination,
                        new JavaFXCssTarget(
                                selectedCompatibility,
                                Objects.requireNonNull(selectedOutputStyle)
                        ),
                        out,
                        err
                );
                case "bss" -> compileBss(
                        input,
                        selectedSyntax,
                        Objects.requireNonNull(destination),
                        new BssTarget(selectedCompatibility),
                        err
                );
                default -> throw new AssertionError("Unsupported target was validated: " + targetName);
            };
        } catch (SassCompilationException failure) {
            printNonErrorDiagnostics(failure.diagnostics(), err);
            err.println(DiagnosticPrinter.format(failure));
            return FAILURE_EXIT_STATUS;
        } catch (IOException failure) {
            err.println("scssfx: " + Objects.requireNonNullElse(
                    failure.getMessage(),
                    failure.getClass().getSimpleName()
            ));
            return FAILURE_EXIT_STATUS;
        }
    }

    /// Compiles a stylesheet to one textual output target.
    ///
    /// @param input the existing Sass input file
    /// @param syntax the input syntax inferred from the file extension
    /// @param destination the optional destination file, or {@code null} for standard output
    /// @param selectedOutputTarget the resolved textual output target
    /// @param out the standard-output writer
    /// @param err the standard-error writer used for non-error diagnostics
    /// @return {@code 0} after the output has been written
    /// @throws IOException if the destination cannot be written
    /// @throws SassCompilationException if Sass evaluation or serialization fails
    private static int compileText(
            Path input,
            Syntax syntax,
            @Nullable Path destination,
            OutputTarget<String> selectedOutputTarget,
            java.io.PrintWriter out,
            java.io.PrintWriter err
    ) throws IOException, SassCompilationException {
        CompileResult<String> result = new SassCompiler().compile(
                new SassFileSource(input, syntax),
                selectedOutputTarget
        );
        printNonErrorDiagnostics(result, err);

        var css = result.output();
        if (destination == null) {
            out.print(css);
            if (!css.isEmpty() && !css.endsWith("\n")) {
                out.println();
            }
        } else {
            @Nullable Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            var text = css.endsWith("\n") || css.isEmpty() ? css : css + "\n";
            Files.writeString(destination, text, StandardCharsets.UTF_8);
        }
        return 0;
    }

    /// Compiles a stylesheet to one JavaFX BSS output target.
    ///
    /// @param input the existing Sass input file
    /// @param syntax the input syntax inferred from the file extension
    /// @param destination the required BSS destination file
    /// @param selectedOutputTarget the resolved binary output target
    /// @param err the standard-error writer used for non-error diagnostics
    /// @return {@code 0} after the BSS document has been written
    /// @throws IOException if the destination cannot be written
    /// @throws SassCompilationException if Sass evaluation or BSS serialization fails
    private static int compileBss(
            Path input,
            Syntax syntax,
            Path destination,
            BssTarget selectedOutputTarget,
            java.io.PrintWriter err
    ) throws IOException, SassCompilationException {
        var result = new SassCompiler().compile(
                new SassFileSource(input, syntax),
                selectedOutputTarget
        );
        printNonErrorDiagnostics(result, err);

        var bss = result.output().duplicate();
        var bytes = new byte[bss.remaining()];
        bss.get(bytes);
        @Nullable Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(destination, bytes);
        return 0;
    }

    /// Writes non-error diagnostics emitted during one successful compilation.
    ///
    /// @param result the completed compilation result
    /// @param err the standard-error writer
    private static void printNonErrorDiagnostics(CompileResult<?> result, java.io.PrintWriter err) {
        printNonErrorDiagnostics(result.diagnostics(), err);
    }

    /// Writes non-error diagnostics from a completed or failed compilation.
    ///
    /// @param diagnostics diagnostics in their container-defined order
    /// @param err the standard-error writer
    private static void printNonErrorDiagnostics(
            List<Diagnostic> diagnostics,
            java.io.PrintWriter err
    ) {
        for (var diagnostic : diagnostics) {
            if (diagnostic.severity() != DiagnosticSeverity.ERROR) {
                err.println(DiagnosticPrinter.format(diagnostic));
            }
        }
    }

    /// Validates options that depend on the selected output target.
    ///
    /// @param targetName the lower-case target name
    /// @param destination the optional output destination
    /// @throws IllegalArgumentException if the target or its options are unsupported
    private void validateTargetOptions(String targetName, @Nullable Path destination) {
        switch (targetName) {
            case "css" -> {
                if (isOptionSpecified("--javafx-compatibility")) {
                    throw new IllegalArgumentException(
                            "--javafx-compatibility is supported only for javafx-css and bss targets"
                    );
                }
            }
            case "javafx-css" -> {
            }
            case "bss" -> {
                if (destination == null) {
                    throw new IllegalArgumentException(
                            "BSS output requires an output path; use -o/--output or a positional output path"
                    );
                }
                if (isOptionSpecified("--style")) {
                    throw new IllegalArgumentException(
                            "--style is supported only for css and javafx-css targets"
                    );
                }
            }
            default -> throw new IllegalArgumentException(
                    "unsupported output target '" + target
                            + "'; expected 'css', 'javafx-css', or 'bss'"
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

    /// Resolves the JavaFX compatibility selected by the command-line option.
    ///
    /// @return the selected JavaFX compatibility level
    /// @throws IllegalArgumentException if the option value is unsupported
    private JavaFXCompatibility selectedJavaFxCompatibility() {
        return switch (javafxCompatibility) {
            case "17" -> JavaFXCompatibility.JAVA_FX_17;
            case "27" -> JavaFXCompatibility.JAVA_FX_27;
            default -> throw new IllegalArgumentException(
                    "unsupported JavaFX compatibility '" + javafxCompatibility
                            + "'; expected '17' or '27'"
            );
        };
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
        System.exit(execute(args));
    }

    /// Executes the command without terminating the current process.
    ///
    /// @param args the command-line arguments
    /// @return the process-compatible exit status
    static int execute(String... args) {
        return new CommandLine(new ScssfxMain()).execute(args);
    }

    /// Returns the command line associated with the injected specification.
    ///
    /// @return the active command line
    private CommandLine commandLine() {
        return Objects.requireNonNull(commandSpec, "commandSpec").commandLine();
    }
}
