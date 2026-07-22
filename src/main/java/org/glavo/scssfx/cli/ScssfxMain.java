// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.DiagnosticSeverity;
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
import java.util.Objects;
import java.util.concurrent.Callable;

/// Provides the command-line entry point for SCSSFX.
@Command(
        name = "scssfx",
        description = "Compiles Sass stylesheets for CSS and JavaFX.",
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
            description = "A stylesheet file to compile, optionally followed by an output CSS path."
    )
    private final List<Path> inputs = new ArrayList<>();

    /// The optional output path selected with {@code -o}/{@code --output}.
    @Option(
            names = {"-o", "--output"},
            paramLabel = "FILE",
            description = "Write CSS to FILE instead of stdout."
    )
    private @Nullable Path output;

    /// The command specification injected by Picocli before invocation.
    @Spec
    private @Nullable CommandSpec commandSpec;

    /// Creates a command-line entry point.
    public ScssfxMain() {
    }

    /// Compiles one SCSS file to stdout or an output path.
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

        try {
            if (!Files.isRegularFile(input)) {
                err.println("scssfx: input is not a file: " + input);
                return FAILURE_EXIT_STATUS;
            }
            @Nullable Syntax syntax = Syntax.forPath(input);
            if (syntax != Syntax.SCSS) {
                err.println("scssfx: only .scss input is supported in this build");
                return FAILURE_EXIT_STATUS;
            }

            CompileResult<String> result = new SassCompiler().compile(
                    new SassFileSource(input, Syntax.SCSS),
                    CssTarget.DEFAULT
            );
            for (var diagnostic : result.diagnostics()) {
                if (diagnostic.severity() != DiagnosticSeverity.ERROR) {
                    err.println(DiagnosticPrinter.format(diagnostic));
                }
            }

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
        } catch (SassCompilationException failure) {
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
