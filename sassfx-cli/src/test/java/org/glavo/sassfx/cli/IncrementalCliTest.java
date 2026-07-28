// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies CLI update-mode freshness checks and incremental option validation.
@NotNullByDefault
final class IncrementalCliTest {
    /// Maximum time allowed for asynchronous watch output to become observable.
    private static final long WATCH_ASSERTION_TIMEOUT_SECONDS = 15;

    /// Compiles a mapped root whose destination does not yet exist.
    @Test
    void updateCompilesMissingDestination(@TempDir Path directory) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("output/style.css");
        Files.writeString(source, "a { color: red; }");
        var output = new StringWriter();

        assertEquals(0, commandLine("", output, new StringWriter()).execute(
                "--update",
                source + ":" + destination
        ));

        assertTrue(Files.readString(destination).contains("color: red;"));
        assertTrue(output.toString().contains("Compiled"));
    }

    /// Leaves a destination newer than its root unchanged.
    @Test
    void updateSkipsFreshDestination(@TempDir Path directory) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");
        Files.writeString(destination, "preserve exactly");
        setModifiedTime(source, 1_000);
        setModifiedTime(destination, 2_000);
        var output = new StringWriter();

        assertEquals(0, commandLine("", output, new StringWriter()).execute(
                "--update",
                source + ":" + destination
        ));

        assertEquals("preserve exactly", Files.readString(destination));
        assertEquals("", output.toString());
    }

    /// Treats immutable built-in modules as fresh when every file is older.
    @Test
    void updateSkipsFreshDestinationUsingBuiltInModule(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'sass:math'; a { width: math.div(4px, 2); }"
        );
        Files.writeString(destination, "preserve built-in result");
        setModifiedTime(source, 1_000);
        setModifiedTime(destination, 2_000);
        var output = new StringWriter();

        assertEquals(0, commandLine("", output, new StringWriter()).execute(
                "--update",
                source + ":" + destination
        ));

        assertEquals(
                "preserve built-in result",
                Files.readString(destination)
        );
        assertEquals("", output.toString());
    }

    /// Rebuilds a destination older than its root.
    @Test
    void updateRebuildsDestinationOlderThanRoot(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");
        Files.writeString(destination, "stale");
        setModifiedTime(destination, 1_000);
        setModifiedTime(source, 2_000);
        var output = new StringWriter();

        assertEquals(0, commandLine("", output, new StringWriter()).execute(
                "--update",
                source + ":" + destination
        ));

        assertTrue(Files.readString(destination).contains("color: red;"));
        assertTrue(output.toString().contains("Compiled"));
    }

    /// Rebuilds when a transitive Sass dependency is newer than the destination.
    @Test
    void updateRebuildsDestinationOlderThanDependency(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("style.scss");
        var dependency = directory.resolve("_theme.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "@use 'theme'; a { color: theme.$color; }");
        Files.writeString(dependency, "$color: blue;");
        Files.writeString(destination, "stale");
        setModifiedTime(source, 1_000);
        setModifiedTime(destination, 2_000);
        setModifiedTime(dependency, 3_000);
        var output = new StringWriter();

        assertEquals(0, commandLine("", output, new StringWriter()).execute(
                "--update",
                source + ":" + destination
        ));

        assertTrue(Files.readString(destination).contains("color: blue;"));
        assertTrue(output.toString().contains("Compiled"));
    }

    /// Compiles magic standard input for every update invocation.
    @Test
    void updateAlwaysCompilesMagicStandardInput(@TempDir Path directory)
            throws Exception {
        var destination = directory.resolve("style.css");
        Files.writeString(destination, "stale");
        setModifiedTime(destination, 9_000);
        var output = new StringWriter();

        assertEquals(0, commandLine(
                "a { color: purple; }",
                output,
                new StringWriter()
        ).execute("--update", "-:" + destination));

        assertTrue(Files.readString(destination).contains("color: purple;"));
        assertTrue(output.toString().contains("Compiled"));
    }

    /// Suppresses the successful update status when quiet mode is selected.
    @Test
    void updateQuietlyCompilesMissingDestination(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");
        var output = new StringWriter();

        assertEquals(0, commandLine("", output, new StringWriter()).execute(
                "--update",
                "--quiet",
                source + ":" + destination
        ));

        assertTrue(Files.isRegularFile(destination));
        assertEquals("", output.toString());
    }

    /// Rejects standard-output update mappings and explicit standard input.
    @Test
    void rejectsInvalidUpdateInputs(@TempDir Path directory) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");

        var stdoutOutput = new StringWriter();
        assertEquals(64, commandLine("", stdoutOutput, new StringWriter())
                .execute("--update", source.toString()));
        assertTrue(stdoutOutput.toString().contains("--update"));

        var stdinOutput = new StringWriter();
        assertEquals(64, commandLine("a {}", stdinOutput, new StringWriter())
                .execute("--update", "--stdin", destination.toString()));
        assertTrue(stdinOutput.toString().contains("--stdin"));
    }

    /// Rejects polling selection unless watch mode is enabled.
    @Test
    void rejectsPollWithoutWatch(@TempDir Path directory) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");

        for (var option : new String[]{"--poll", "--no-poll"}) {
            var output = new StringWriter();
            assertEquals(64, commandLine("", output, new StringWriter())
                    .execute(option, source + ":" + destination));
            assertTrue(output.toString().contains("--poll"));
        }
    }

    /// Rejects watch mode with explicit standard input because it has no file to observe.
    @Test
    void rejectsWatchWithStandardInput(@TempDir Path directory) throws Exception {
        var destination = directory.resolve("style.css");
        var output = new StringWriter();

        assertEquals(64, commandLine("a {}", output, new StringWriter()).execute(
                "--watch",
                "--stdin",
                destination.toString()
        ));

        assertTrue(output.toString().contains("--stdin"));
        assertFalse(Files.exists(destination));
    }

    /// Recompiles a changed root while recursive metadata polling is selected.
    @Test
    void pollWatchRecompilesChangedRoot(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    new StringWriter()
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            awaitTextContains(output, "Sass is watching");
            Files.writeString(source, "a { color: blue; }");
            Files.setLastModifiedTime(
                    source,
                    FileTime.fromMillis(
                            Files.getLastModifiedTime(destination).toMillis()
                                    + 2_000
                    )
            );
            awaitContains(destination, "color: blue;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Recompiles an entrypoint when one of its loaded partials changes.
    @Test
    void pollWatchRecompilesChangedDependency(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("style.scss");
        var dependency = directory.resolve("_theme.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'theme'; a { color: theme.$color; }"
        );
        Files.writeString(dependency, "$color: red;");
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    new StringWriter()
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            awaitTextContains(output, "Sass is watching");
            Files.writeString(dependency, "$color: blue;");
            Files.setLastModifiedTime(
                    dependency,
                    FileTime.fromMillis(
                            Files.getLastModifiedTime(destination).toMillis()
                                    + 2_000
                    )
            );
            awaitContains(destination, "color: blue;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Re-resolves an import when a higher-priority `SASS_PATH` file appears.
    @Test
    void pollWatchTracksEnvironmentLoadPathResolution(
            @TempDir Path directory
    ) throws Exception {
        var first = Files.createDirectory(directory.resolve("first"));
        var second = Files.createDirectory(directory.resolve("second"));
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'theme'; a { color: theme.$color; }"
        );
        Files.writeString(second.resolve("_theme.scss"), "$color: red;");
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    new StringWriter(),
                    directory,
                    first + File.pathSeparator + second
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            awaitTextContains(output, "Sass is watching");
            Files.writeString(first.resolve("_theme.scss"), "$color: blue;");
            awaitContains(destination, "color: blue;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Does not recompile when a newly added lower-priority candidate cannot
    /// affect the selected relative import.
    @Test
    void pollWatchIgnoresUnselectedLowerPriorityCandidate(
            @TempDir Path directory
    ) throws Exception {
        var loadPath = Files.createDirectory(directory.resolve("load-path"));
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'theme'; a { color: theme.$color; }"
        );
        Files.writeString(directory.resolve("_theme.scss"), "$color: red;");
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    new StringWriter()
            ).execute(
                    "--watch",
                    "--poll",
                    "--load-path",
                    loadPath.toString(),
                    source + ":" + destination
            ));

            awaitContains(destination, "color: red;");
            awaitTextContains(output, "Sass is watching");
            assertEquals(1, countOccurrences(output.toString(), "Compiled "));
            Files.writeString(
                    loadPath.resolve("_theme.scss"),
                    "$color: blue;"
            );
            TimeUnit.MILLISECONDS.sleep(250);
            assertEquals(1, countOccurrences(output.toString(), "Compiled "));
            assertTrue(Files.readString(destination).contains("color: red;"));
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Recovers when a previously missing relative dependency is added.
    @Test
    void pollWatchRecoversWhenMissingDependencyAppears(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'theme'; a { color: theme.$color; }"
        );
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        var error = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    error
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitTextContains(error, "Can't find stylesheet to import");
            awaitTextContains(output, "Sass is watching");
            Files.writeString(
                    directory.resolve("_theme.scss"),
                    "$color: green;"
            );
            awaitContains(destination, "color: green;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Reports and then recovers from a candidate conflict introduced while
    /// watching.
    @Test
    void pollWatchTracksCandidateConflictLifecycle(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var dependency = directory.resolve("_theme.scss");
        var conflict = directory.resolve("_theme.sass");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'theme'; a { color: theme.$color; }"
        );
        Files.writeString(dependency, "$color: red;");
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        var error = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    error
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            awaitTextContains(output, "Sass is watching");
            Files.writeString(conflict, "$color: blue");
            awaitTextContains(error, "It's not clear which file to import");
            Files.delete(conflict);
            awaitOccurrenceCount(output, "Compiled ", 2);
            awaitContains(destination, "color: red;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Falls back to a load-path dependency when the selected relative file is
    /// deleted.
    @Test
    void pollWatchFallsBackAfterDependencyRemoval(
            @TempDir Path directory
    ) throws Exception {
        var loadPath = Files.createDirectory(directory.resolve("load-path"));
        var source = directory.resolve("style.scss");
        var relative = directory.resolve("_theme.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'theme'; a { color: theme.$color; }"
        );
        Files.writeString(relative, "$color: red;");
        Files.writeString(
                loadPath.resolve("_theme.scss"),
                "$color: blue;"
        );
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    new StringWriter(),
                    new StringWriter()
            ).execute(
                    "--watch",
                    "--poll",
                    "--load-path",
                    loadPath.toString(),
                    source + ":" + destination
            ));

            awaitContains(destination, "color: red;");
            Files.delete(relative);
            awaitContains(destination, "color: blue;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Replaces an index dependency when a higher-priority direct candidate
    /// appears.
    @Test
    void pollWatchReplacesIndexWithDirectCandidate(
            @TempDir Path directory
    ) throws Exception {
        var moduleDirectory = Files.createDirectory(
                directory.resolve("theme")
        );
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'theme'; a { color: theme.$color; }"
        );
        Files.writeString(
                moduleDirectory.resolve("_index.scss"),
                "$color: red;"
        );
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    new StringWriter(),
                    new StringWriter()
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            Files.writeString(
                    directory.resolve("_theme.scss"),
                    "$color: purple;"
            );
            awaitContains(destination, "color: purple;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Compiles added directory entrypoints and deletes their owned outputs.
    @Test
    void pollWatchTracksDirectoryEntrypointLifecycle(
            @TempDir Path directory
    ) throws Exception {
        var sourceDirectory = directory.resolve("input");
        var destinationDirectory = directory.resolve("output");
        var source = sourceDirectory.resolve("style.scss");
        var destination = destinationDirectory.resolve("style.css");
        var sourceMap = Path.of(destination + ".map");
        Files.createDirectories(sourceDirectory);
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    new StringWriter()
            ).execute(
                    "--watch",
                    "--poll",
                    sourceDirectory + ":" + destinationDirectory
            ));

            awaitTextContains(output, "Sass is watching");
            Files.writeString(source, "a { color: green; }");
            awaitContains(destination, "color: green;");
            awaitExists(sourceMap);
            Files.delete(source);
            awaitAbsent(destination);
            awaitAbsent(sourceMap);
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Rebuilds every entrypoint that shares a modified dependency.
    @Test
    void updateRebuildsSharedDependencyConsumers(
            @TempDir Path directory
    ) throws Exception {
        var dependency = directory.resolve("_theme.scss");
        var first = directory.resolve("first.scss");
        var second = directory.resolve("second.scss");
        var firstOutput = directory.resolve("first.css");
        var secondOutput = directory.resolve("second.css");
        Files.writeString(dependency, "$color: red;");
        Files.writeString(
                first,
                "@use 'theme'; .first { color: theme.$color; }"
        );
        Files.writeString(
                second,
                "@use 'theme'; .second { color: theme.$color; }"
        );
        var initial = commandLine("", new StringWriter(), new StringWriter());
        assertEquals(0, initial.execute(
                "--update",
                first + ":" + firstOutput,
                second + ":" + secondOutput
        ));

        var outputTime = Math.max(
                Files.getLastModifiedTime(firstOutput).toMillis(),
                Files.getLastModifiedTime(secondOutput).toMillis()
        );
        Files.writeString(dependency, "$color: blue;");
        Files.setLastModifiedTime(
                dependency,
                FileTime.fromMillis(outputTime + 2_000)
        );
        var output = new StringWriter();
        assertEquals(0, commandLine("", output, new StringWriter()).execute(
                "--update",
                first + ":" + firstOutput,
                second + ":" + secondOutput
        ));

        assertTrue(Files.readString(firstOutput).contains("color: blue;"));
        assertTrue(Files.readString(secondOutput).contains("color: blue;"));
        assertEquals(2, countOccurrences(output.toString(), "Compiled "));
    }

    /// Leaves a fresh sibling output untouched when another root changes.
    @Test
    void updateLeavesFreshSiblingUntouched(@TempDir Path directory)
            throws Exception {
        var first = directory.resolve("first.scss");
        var second = directory.resolve("second.scss");
        var firstOutput = directory.resolve("first.css");
        var secondOutput = directory.resolve("second.css");
        Files.writeString(first, ".first { color: red; }");
        Files.writeString(second, ".second { color: red; }");
        Files.writeString(firstOutput, "first stale");
        Files.writeString(secondOutput, "second preserved");
        setModifiedTime(firstOutput, 2_000);
        setModifiedTime(secondOutput, 2_000);
        setModifiedTime(first, 3_000);
        setModifiedTime(second, 1_000);

        assertEquals(0, commandLine(
                "",
                new StringWriter(),
                new StringWriter()
        ).execute(
                "--update",
                first + ":" + firstOutput,
                second + ":" + secondOutput
        ));

        assertTrue(Files.readString(firstOutput).contains("color: red;"));
        assertEquals("second preserved", Files.readString(secondOutput));
    }

    /// Replaces or removes stale output when update compilation fails.
    @Test
    void updateAppliesErrorCssPolicies(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: ; }");
        Files.writeString(destination, "stale");
        setModifiedTime(destination, 1_000);
        setModifiedTime(source, 2_000);

        assertEquals(65, commandLine(
                "",
                new StringWriter(),
                new StringWriter()
        ).execute("--update", source + ":" + destination));
        assertTrue(Files.readString(destination).contains(
                "Error: Expected expression."
        ));

        Files.writeString(destination, "stale again");
        assertEquals(65, commandLine(
                "",
                new StringWriter(),
                new StringWriter()
        ).execute(
                "--update",
                "--no-error-css",
                source + ":" + destination
        ));
        assertFalse(Files.exists(destination));
    }

    /// Preserves a fresh destination when watch mode starts.
    @Test
    void pollWatchPreservesFreshDestinationOnStartup(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");
        Files.writeString(destination, "preserve fresh output");
        setModifiedTime(source, 1_000);
        setModifiedTime(destination, 2_000);
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    new StringWriter()
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitTextContains(output, "Sass is watching");
            assertEquals("preserve fresh output", Files.readString(destination));
            assertEquals(0, countOccurrences(output.toString(), "Compiled "));
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Continues the initial watch batch after an error unless requested to stop.
    @Test
    void pollWatchControlsInitialErrorContinuation(
            @TempDir Path directory
    ) throws Exception {
        var broken = directory.resolve("broken.scss");
        var valid = directory.resolve("valid.scss");
        var brokenOutput = directory.resolve("broken.css");
        var validOutput = directory.resolve("valid.css");
        Files.writeString(broken, "a { color: ; }");
        Files.writeString(valid, "b { color: green; }");
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        var error = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    error
            ).execute(
                    "--watch",
                    "--poll",
                    broken + ":" + brokenOutput,
                    valid + ":" + validOutput
            ));

            awaitTextContains(error, "Expected expression");
            awaitContains(validOutput, "color: green;");
            awaitTextContains(output, "Sass is watching");
            assertTrue(Files.readString(brokenOutput).contains(
                    "Error: Expected expression."
            ));
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }

        Files.deleteIfExists(brokenOutput);
        Files.deleteIfExists(validOutput);
        var stoppedOutput = new StringWriter();
        var stoppedError = new StringWriter();
        assertEquals(
                65,
                commandLine("", stoppedOutput, stoppedError).execute(
                        "--watch",
                        "--poll",
                        "--stop-on-error",
                        broken + ":" + brokenOutput,
                        valid + ":" + validOutput
                )
        );
        assertTrue(stoppedError.toString().contains("Expected expression"));
        assertTrue(Files.isRegularFile(brokenOutput));
        assertFalse(Files.exists(validOutput));
        assertFalse(stoppedOutput.toString().contains("Sass is watching"));
    }

    /// Deletes and recreates output as an explicit watched root disappears.
    @Test
    void pollWatchTracksExplicitRootReplacement(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "a { color: red; }");
        var executor = Executors.newSingleThreadExecutor();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    new StringWriter(),
                    new StringWriter()
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            Files.delete(source);
            awaitAbsent(destination);
            Files.writeString(source, "a { color: blue; }");
            awaitContains(destination, "color: blue;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Tracks dynamic load-css dependencies without rebuilding for unrelated files.
    @Test
    void pollWatchTracksLoadCssAndIgnoresUnrelatedFiles(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var dependency = directory.resolve("_theme.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(
                source,
                "@use 'sass:meta'; @include meta.load-css('theme');"
        );
        Files.writeString(dependency, ".theme { color: red; }");
        var executor = Executors.newSingleThreadExecutor();
        var output = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    output,
                    new StringWriter()
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            awaitTextContains(output, "Sass is watching");
            var compiled = countOccurrences(output.toString(), "Compiled ");
            Files.writeString(
                    directory.resolve("unrelated.scss"),
                    "x { color: black; }"
            );
            TimeUnit.MILLISECONDS.sleep(250);
            assertEquals(
                    compiled,
                    countOccurrences(output.toString(), "Compiled ")
            );
            Files.writeString(dependency, ".theme { color: blue; }");
            awaitContains(destination, "color: blue;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Recovers after a dependency edit introduces and then removes a module loop.
    @Test
    void pollWatchRecoversDependencyLoopLifecycle(
            @TempDir Path directory
    ) throws Exception {
        var source = directory.resolve("style.scss");
        var dependency = directory.resolve("_theme.scss");
        var destination = directory.resolve("style.css");
        Files.writeString(source, "@use 'theme';");
        Files.writeString(dependency, ".theme { color: red; }");
        var executor = Executors.newSingleThreadExecutor();
        var error = new StringWriter();
        try {
            var task = executor.submit(() -> commandLine(
                    "",
                    new StringWriter(),
                    error
            ).execute("--watch", "--poll", source + ":" + destination));

            awaitContains(destination, "color: red;");
            Files.writeString(
                    dependency,
                    "@use 'style'; .theme { color: orange; }"
            );
            awaitTextContains(error, "Module loop");
            Files.writeString(dependency, ".theme { color: blue; }");
            awaitContains(destination, "color: blue;");
            task.cancel(true);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /// Sets a deterministic timestamp without relying on filesystem timestamp resolution.
    ///
    /// @param path the path whose timestamp is changed
    /// @param seconds epoch seconds for the timestamp
    /// @throws java.io.IOException if the filesystem rejects the timestamp
    private static void setModifiedTime(Path path, long seconds)
            throws java.io.IOException {
        Files.setLastModifiedTime(
                path,
                FileTime.from(seconds, TimeUnit.SECONDS)
        );
    }

    /// Waits until a text output contains an expected fragment.
    ///
    /// @param path the output file to inspect
    /// @param expected text expected in the output
    /// @throws Exception if the caller is interrupted while waiting
    private static void awaitContains(Path path, String expected)
            throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                WATCH_ASSERTION_TIMEOUT_SECONDS
        );
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(path)
                    && Files.readString(path).contains(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(
                Files.isRegularFile(path)
                        && Files.readString(path).contains(expected),
                "Expected " + path + " to contain " + expected
        );
    }

    /// Waits until a captured writer contains an expected fragment.
    ///
    /// @param writer the concurrently written text buffer
    /// @param expected text expected in the buffer
    /// @throws Exception if the caller is interrupted while waiting
    private static void awaitTextContains(
            StringWriter writer,
            String expected
    ) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                WATCH_ASSERTION_TIMEOUT_SECONDS
        );
        while (System.nanoTime() < deadline) {
            if (writer.toString().contains(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(
                writer.toString().contains(expected),
                "Expected captured output to contain " + expected
        );
    }

    /// Waits until a captured writer contains a substring at least the expected
    /// number of times.
    ///
    /// @param writer the concurrently written text buffer
    /// @param value the counted nonempty substring
    /// @param expected minimum occurrence count
    /// @throws Exception if the caller is interrupted while waiting
    private static void awaitOccurrenceCount(
            StringWriter writer,
            String value,
            int expected
    ) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                WATCH_ASSERTION_TIMEOUT_SECONDS
        );
        while (System.nanoTime() < deadline) {
            if (countOccurrences(writer.toString(), value) >= expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(
                countOccurrences(writer.toString(), value) >= expected,
                "Expected captured output to contain " + expected
                        + " occurrences of " + value
        );
    }

    /// Counts non-overlapping occurrences of a substring.
    ///
    /// @param text the searched text
    /// @param value the nonempty substring
    /// @return the occurrence count
    private static int countOccurrences(String text, String value) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    /// Waits until one path exists.
    ///
    /// @param path the path expected to exist
    /// @throws Exception if the caller is interrupted while waiting
    private static void awaitExists(Path path) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (Files.exists(path)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(Files.exists(path), "Expected " + path + " to exist");
    }

    /// Waits until one path no longer exists.
    ///
    /// @param path the path expected to be absent
    /// @throws Exception if the caller is interrupted while waiting
    private static void awaitAbsent(Path path) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (!Files.exists(path)) {
                return;
            }
            Thread.sleep(10);
        }
        assertFalse(Files.exists(path), "Expected " + path + " to be absent");
    }

    /// Creates a command line with isolated UTF-8 standard input and writers.
    ///
    /// @param input standard-input text
    /// @param output standard-output buffer
    /// @param error standard-error buffer
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            StringWriter output,
            StringWriter error
    ) {
        return SassFXMain.configure(new CommandLine(new SassFXMain(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }

    /// Creates a command line with an injected working directory and
    /// `SASS_PATH` value.
    ///
    /// @param input standard-input text
    /// @param output standard-output buffer
    /// @param error standard-error buffer
    /// @param workingDirectory invocation working directory
    /// @param sassPath raw `SASS_PATH` value
    /// @return the configured command line
    private static CommandLine commandLine(
            String input,
            StringWriter output,
            StringWriter error,
            Path workingDirectory,
            String sassPath
    ) {
        return SassFXMain.configure(new CommandLine(new SassFXMain(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                workingDirectory,
                sassPath
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }
}
