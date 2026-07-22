// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

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
            description = "A stylesheet file or directory to compile."
    )
    private final List<Path> inputs = new ArrayList<>();

    /// The command specification injected by Picocli before invocation.
    @Spec
    private @Nullable CommandSpec commandSpec;

    /// Creates a command-line entry point.
    public ScssfxMain() {
    }

    /// Validates the initial command invocation.
    ///
    /// @return status {@code 2} when no input is supplied, or status {@code 1}
    /// while the compilation engine is unavailable
    @Override
    public Integer call() {
        var commandLine = commandLine();
        if (inputs.isEmpty()) {
            commandLine.usage(commandLine.getOut());
            return USAGE_EXIT_STATUS;
        }

        commandLine.getErr().println(
                "scssfx: the Sass compilation engine is not available in this build"
        );
        return FAILURE_EXIT_STATUS;
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
