// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/// Describes the fully expanded inputs and destinations for one CLI invocation.
///
/// @param jobs compilation jobs in deterministic execution order
/// @param explicitJobs jobs not discovered by recursive directory scanning
/// @param directoryMappings recursive source-to-destination directory mappings
@NotNullByDefault
record CliCompilationPlan(
        @Unmodifiable List<Job> jobs,
        @Unmodifiable List<Job> explicitJobs,
        @Unmodifiable List<DirectoryMapping> directoryMappings
) {
    /// Creates a plan with an immutable job snapshot.
    CliCompilationPlan {
        jobs = List.copyOf(jobs);
        explicitJobs = List.copyOf(explicitJobs);
        directoryMappings = List.copyOf(directoryMappings);
    }

    /// Expands raw command-line operands into compilation jobs.
    ///
    /// Existing directory operands are scanned recursively. Directory discovery
    /// includes lowercase {@code .scss}, {@code .sass}, and {@code .css}
    /// entrypoints whose basename does not begin with an underscore.
    ///
    /// @param arguments raw positional command-line operands
    /// @param explicitOutput output selected by {@code -o}, or {@code null}
    /// @param standardInput whether {@code --stdin} was selected
    /// @param indented whether every input uses the indented syntax
    /// @param outputExtension extension used for directory-derived destinations
    /// @return the expanded immutable plan
    /// @throws IOException if an input directory cannot be traversed
    /// @throws IllegalArgumentException if the operands select incompatible modes
    static CliCompilationPlan create(
            @Unmodifiable List<String> arguments,
            @Nullable Path explicitOutput,
            boolean standardInput,
            boolean indented,
            String outputExtension
    ) throws IOException {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(outputExtension, "outputExtension");
        if (!outputExtension.startsWith(".") || outputExtension.length() == 1) {
            throw new IllegalArgumentException(
                    "outputExtension must begin with a dot and contain a suffix"
            );
        }

        var directories = new HashSet<String>();
        var hasMapping = false;
        var hasPositional = false;
        for (var argument : arguments) {
            if (argument.isEmpty()) {
                throw new IllegalArgumentException("Invalid argument \"\".");
            }
            if (argument.startsWith("-")
                    && !"-".equals(argument)
                    && !argument.startsWith("-:")) {
                throw new IllegalArgumentException(
                        "Unknown option: " + argument
                );
            }
            if (mappingSeparator(argument) >= 0) {
                hasMapping = true;
            } else if (Files.isDirectory(Path.of(argument))) {
                directories.add(argument);
            } else {
                hasPositional = true;
            }
        }

        if (hasPositional || arguments.isEmpty()) {
            if (hasMapping || !directories.isEmpty()) {
                if (!hasMapping && !directories.isEmpty()) {
                    @Nullable String directory = null;
                    for (var argument : arguments) {
                        if (directories.contains(argument)) {
                            directory = argument;
                            break;
                        }
                    }
                    if (directory == null) {
                        throw new AssertionError(
                                "directory classification lost its operand"
                        );
                    }
                    var message = "Directory \"" + directory
                            + "\" may not be a positional arg.";
                    var target = arguments.get(arguments.size() - 1);
                    if (directory.equals(arguments.get(0))
                            && !Files.exists(Path.of(target))) {
                        message += "\nTo compile all CSS in \"" + directory
                                + "\" to \"" + target + "\", use `scssfx "
                                + directory + ":" + target + "`.";
                    }
                    throw new IllegalArgumentException(message);
                }
                throw new IllegalArgumentException(
                        "Positional and \":\" arguments may not both be used."
                );
            }
            return positional(
                    arguments,
                    explicitOutput,
                    standardInput,
                    indented
            );
        }

        if (standardInput) {
            throw new IllegalArgumentException(
                    "--stdin may not be used with \":\" arguments."
            );
        }
        if (explicitOutput != null) {
            throw new IllegalArgumentException(
                    "-o/--output may only be used with a single positional input"
            );
        }

        var jobs = new ArrayList<Job>();
        var explicitJobs = new ArrayList<Job>();
        var directoryMappings = new ArrayList<DirectoryMapping>();
        var seen = new HashSet<Path>();
        for (var argument : arguments) {
            if (directories.contains(argument)) {
                var sourceDirectory = Path.of(argument);
                directoryMappings.add(new DirectoryMapping(
                        sourceDirectory,
                        sourceDirectory
                ));
                addDirectory(
                        jobs,
                        seen,
                        sourceDirectory,
                        sourceDirectory,
                        indented,
                        outputExtension
                );
                continue;
            }

            var mapping = splitMapping(argument);
            var sourceText = mapping.source();
            var destination = Path.of(mapping.destination());
            if ("-".equals(sourceText)) {
                var job = new Job(
                        null,
                        destination,
                        stdinSyntax(indented)
                );
                addJob(
                        jobs,
                        seen,
                        job,
                        Path.of("-")
                );
                explicitJobs.add(job);
                continue;
            }

            var source = Path.of(sourceText);
            if (Files.isDirectory(source)) {
                directoryMappings.add(new DirectoryMapping(
                        source,
                        destination
                ));
                addDirectory(
                        jobs,
                        seen,
                        source,
                        destination,
                        indented,
                        outputExtension
                );
            } else {
                var job = new Job(
                        source,
                        destination,
                        fileSyntax(source, indented)
                );
                addJob(
                        jobs,
                        seen,
                        job,
                        source
                );
                explicitJobs.add(job);
            }
        }
        return new CliCompilationPlan(
                jobs,
                explicitJobs,
                directoryMappings
        );
    }

    /// Creates the single job selected by positional or stdin mode.
    private static CliCompilationPlan positional(
            @Unmodifiable List<String> arguments,
            @Nullable Path explicitOutput,
            boolean standardInput,
            boolean indented
    ) {
        if (standardInput) {
            if (arguments.size() > 1) {
                throw new IllegalArgumentException(
                        "Only one argument is allowed with --stdin."
                );
            }
            if (explicitOutput != null && !arguments.isEmpty()) {
                throw new IllegalArgumentException(
                        "cannot combine a positional output path with -o/--output"
                );
            }
            @Nullable Path destination = explicitOutput != null
                    ? explicitOutput
                    : arguments.isEmpty() ? null : Path.of(arguments.get(0));
            var jobs = List.of(
                    new Job(null, destination, stdinSyntax(indented))
            );
            return new CliCompilationPlan(jobs, jobs, List.of());
        }

        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("Compile Sass to CSS.");
        }
        if (arguments.size() > 2) {
            throw new IllegalArgumentException(
                    "Only two positional args may be passed."
            );
        }
        if (explicitOutput != null && arguments.size() == 2) {
            throw new IllegalArgumentException(
                    "cannot combine a positional output path with -o/--output"
            );
        }

        var sourceText = arguments.get(0);
        @Nullable Path source = "-".equals(sourceText)
                ? null
                : Path.of(sourceText);
        @Nullable Path destination = explicitOutput != null
                ? explicitOutput
                : arguments.size() == 2 ? Path.of(arguments.get(1)) : null;
        var syntax = source == null
                ? stdinSyntax(indented)
                : fileSyntax(source, indented);
        var jobs = List.of(new Job(source, destination, syntax));
        return new CliCompilationPlan(jobs, jobs, List.of());
    }

    /// Rescans directory mappings while preserving explicit jobs.
    ///
    /// @param indented whether every input uses the indented syntax
    /// @param outputExtension extension used for directory-derived destinations
    /// @return a new immutable plan reflecting the current directory contents
    /// @throws IOException if a source directory cannot be traversed
    CliCompilationPlan refreshDirectories(
            boolean indented,
            String outputExtension
    ) throws IOException {
        if (directoryMappings.isEmpty()) {
            return this;
        }

        var refreshedJobs = new ArrayList<>(explicitJobs);
        var seen = new HashSet<Path>();
        for (var job : explicitJobs) {
            seen.add(pathKey(
                    job.source() == null ? Path.of("-") : job.source()
            ));
        }
        for (var mapping : directoryMappings) {
            if (!Files.isDirectory(mapping.source())) {
                continue;
            }
            addDirectory(
                    refreshedJobs,
                    seen,
                    mapping.source(),
                    mapping.destination(),
                    indented,
                    outputExtension
            );
        }
        return new CliCompilationPlan(
                refreshedJobs,
                explicitJobs,
                directoryMappings
        );
    }

    /// Adds all eligible entrypoints beneath one directory.
    private static void addDirectory(
            List<Job> jobs,
            HashSet<Path> seen,
            Path sourceDirectory,
            Path destinationDirectory,
            boolean indented,
            String outputExtension
    ) throws IOException {
        var sameDirectory = pathKey(sourceDirectory).equals(
                pathKey(destinationDirectory)
        );
        try (var paths = Files.walk(sourceDirectory)) {
            var sources = paths
                    .filter(Files::isRegularFile)
                    .filter(CliCompilationPlan::isDirectoryEntrypoint)
                    .sorted((left, right) -> sourceDirectory
                            .relativize(left)
                            .toString()
                            .compareTo(sourceDirectory
                                    .relativize(right)
                                    .toString()))
                    .toList();
            for (var source : sources) {
                if (sameDirectory && source.toString().endsWith(".css")) {
                    continue;
                }
                var relative = sourceDirectory.relativize(source);
                var destination = destinationDirectory.resolve(
                        replaceExtension(relative, outputExtension)
                );
                addJob(
                        jobs,
                        seen,
                        new Job(
                                source,
                                destination,
                                fileSyntax(source, indented)
                        ),
                        source
                );
            }
        }
    }

    /// Adds a job after rejecting a duplicate source identity.
    private static void addJob(
            List<Job> jobs,
            HashSet<Path> seen,
            Job job,
            Path sourceIdentity
    ) {
        if (!seen.add(pathKey(sourceIdentity))) {
            throw new IllegalArgumentException(
                    "Duplicate source \"" + sourceIdentity + "\"."
            );
        }
        jobs.add(job);
    }

    /// Reports whether a path is a directory-discovered Sass entrypoint.
    private static boolean isDirectoryEntrypoint(Path path) {
        var fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            return false;
        }
        var fileName = fileNamePath.toString();
        if (fileName.startsWith("_")) {
            return false;
        }
        return fileName.endsWith(".scss")
                || fileName.endsWith(".sass")
                || fileName.endsWith(".css");
    }

    /// Selects the syntax used by one file input.
    private static Syntax fileSyntax(Path path, boolean indented) {
        if (indented) {
            return Syntax.SASS;
        }
        var fileNamePath = path.getFileName();
        var fileName = fileNamePath == null ? "" : fileNamePath.toString();
        if (fileName.endsWith(".sass")) {
            return Syntax.SASS;
        }
        if (fileName.endsWith(".css")) {
            return Syntax.CSS;
        }
        return Syntax.SCSS;
    }

    /// Selects the syntax used by standard input.
    private static Syntax stdinSyntax(boolean indented) {
        return indented ? Syntax.SASS : Syntax.SCSS;
    }

    /// Replaces the final extension of a relative path.
    private static Path replaceExtension(Path path, String extension) {
        var fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            throw new IllegalArgumentException(
                    "stylesheet path must have a file name: " + path
            );
        }
        var fileName = fileNamePath.toString();
        var dot = fileName.lastIndexOf('.');
        var outputName = (dot < 0 ? fileName : fileName.substring(0, dot))
                + extension;
        @Nullable Path parent = path.getParent();
        return parent == null ? Path.of(outputName) : parent.resolve(outputName);
    }

    /// Splits one source-to-destination mapping.
    private static Mapping splitMapping(String argument) {
        var separator = mappingSeparator(argument);
        if (separator < 0) {
            throw new IllegalArgumentException(
                    "Expected \"" + argument + "\" to contain a colon."
            );
        }

        var next = argument.indexOf(':', separator + 1);
        if (next == separator + 2
                && isWindowsPath(argument, separator + 1)) {
            next = argument.indexOf(':', next + 1);
        }
        if (next >= 0) {
            throw new IllegalArgumentException(
                    "\"" + argument + "\" may only contain one \":\"."
            );
        }
        return new Mapping(
                argument.substring(0, separator),
                argument.substring(separator + 1)
        );
    }

    /// Returns the mapping separator while ignoring a Windows drive colon.
    private static int mappingSeparator(String argument) {
        for (var index = 0; index < argument.length(); index++) {
            if (argument.charAt(index) != ':') {
                continue;
            }
            if (index == 1 && isWindowsPath(argument, 0)) {
                continue;
            }
            return index;
        }
        return -1;
    }

    /// Reports whether text at an offset begins with a Windows drive prefix.
    private static boolean isWindowsPath(String text, int offset) {
        return text.length() > offset + 2
                && Character.isLetter(text.charAt(offset))
                && text.charAt(offset + 1) == ':';
    }

    /// Returns the normalized absolute identity of a path.
    private static Path pathKey(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /// Describes one root stylesheet compilation.
    ///
    /// @param source input file, or {@code null} for standard input
    /// @param destination output file, or {@code null} for standard output
    /// @param syntax syntax used to parse the root stylesheet
    record Job(
            @Nullable Path source,
            @Nullable Path destination,
            Syntax syntax
    ) {
        /// Creates a job with a non-null syntax.
        Job {
            Objects.requireNonNull(syntax, "syntax");
        }
    }

    /// Describes one recursively monitored directory mapping.
    ///
    /// @param source source directory
    /// @param destination destination directory
    record DirectoryMapping(Path source, Path destination) {
        /// Creates a non-null mapping.
        DirectoryMapping {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }
    }

    /// Holds the two components of a mapping operand.
    ///
    /// @param source raw source component
    /// @param destination raw destination component
    private record Mapping(String source, String destination) {
        /// Creates a non-null mapping.
        private Mapping {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }
    }
}
