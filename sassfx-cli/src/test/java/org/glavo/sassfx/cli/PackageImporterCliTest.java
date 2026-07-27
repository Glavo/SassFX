// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies CLI activation and working-directory behavior for package
/// importers.
@NotNullByDefault
final class PackageImporterCliTest {
    /// Does not enable package resolution without an explicit option.
    @Test
    void requiresExplicitPackageImporter(@TempDir Path directory)
            throws Exception {
        var input = createPackageProject(directory);
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine(directory, "", output, error)
                        .execute(input.toString())
        );
        assertEquals("", output.toString());
        assertTrue(error.toString().contains("Can't find stylesheet"));
    }

    /// Enables Node package resolution through the long option.
    @Test
    void enablesNodePackageImporter(@TempDir Path directory)
            throws Exception {
        var input = createPackageProject(directory);
        var output = new StringWriter();
        var error = new StringWriter();

        assertEquals(
                0,
                commandLine(directory, "", output, error).execute(
                        "--pkg-importer=node",
                        input.toString()
                )
        );
        assertTrue(output.toString().contains(".from-package"));
        assertEquals("", error.toString());
    }

    /// Accepts the short package-importer option and scoped packages.
    @Test
    void acceptsShortPackageImporterOption(@TempDir Path directory)
            throws Exception {
        var sourceDirectory = directory.resolve("src");
        var packageRoot = directory.resolve("node_modules")
                .resolve("@scope")
                .resolve("theme");
        Files.createDirectories(packageRoot);
        Files.writeString(
                packageRoot.resolve("package.json"),
                "{\"sass\":\"./index.scss\"}"
        );
        Files.writeString(
                packageRoot.resolve("index.scss"),
                ".from-scoped-package {value: yes}"
        );
        Files.createDirectories(sourceDirectory);
        var input = sourceDirectory.resolve("main.scss");
        Files.writeString(input, "@use \"pkg:@scope/theme\";");
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine(directory, "", output, new StringWriter())
                        .execute("-p", "node", input.toString())
        );
        assertTrue(output.toString().contains(".from-scoped-package"));
    }

    /// Uses the injected working directory for stdin package lookup.
    @Test
    void resolvesPackagesForStandardInput(@TempDir Path directory)
            throws Exception {
        createPackage(directory, "demo", ".from-stdin-package {value: yes}");
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        directory,
                        "@use \"pkg:demo\";",
                        output,
                        new StringWriter()
                ).execute("--stdin", "--pkg-importer", "node")
        );
        assertTrue(output.toString().contains(".from-stdin-package"));
    }

    /// Resolves file inputs beside the containing file rather than CLI cwd.
    @Test
    void resolvesFromContainingFileDirectory(@TempDir Path directory)
            throws Exception {
        var project = directory.resolve("project");
        var input = createPackageProject(project);
        var unrelatedWorkingDirectory = directory.resolve("unrelated");
        Files.createDirectories(unrelatedWorkingDirectory);
        var output = new StringWriter();

        assertEquals(
                0,
                commandLine(
                        unrelatedWorkingDirectory,
                        "",
                        output,
                        new StringWriter()
                ).execute("--pkg-importer=node", input.toString())
        );
        assertTrue(output.toString().contains(".from-package"));
    }

    /// Rejects unsupported built-in importer names as usage errors.
    @Test
    void rejectsUnknownPackageImporter(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("main.scss");
        Files.writeString(input, "a {b: c}");
        var output = new StringWriter();

        assertEquals(
                64,
                commandLine(directory, "", output, new StringWriter())
                        .execute(
                                "--pkg-importer=other",
                                input.toString()
                        )
        );
        assertTrue(output.toString().contains(
                "unsupported package importer 'other'"
        ));
    }

    /// Reports malformed package metadata as Sass data errors with error CSS.
    @Test
    void reportsPackageMetadataFailure(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("src").resolve("main.scss");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "@use \"pkg:demo\";");
        var packageRoot = directory.resolve("node_modules").resolve("demo");
        Files.createDirectories(packageRoot);
        Files.writeString(packageRoot.resolve("package.json"), "{");
        var output = directory.resolve("out.css");
        var error = new StringWriter();

        assertEquals(
                65,
                commandLine(directory, "", new StringWriter(), error)
                        .execute(
                                "--pkg-importer=node",
                                "--no-source-map",
                                input.toString(),
                                output.toString()
                        )
        );
        assertTrue(error.toString().contains("Failed to parse"));
        assertTrue(Files.readString(output).contains("body::before"));
        assertFalse(Files.exists(Path.of(output + ".map")));
    }

    /// Creates a project whose source imports one Node package.
    ///
    /// @param directory the project directory
    /// @return the root stylesheet
    private static Path createPackageProject(Path directory)
            throws Exception {
        createPackage(directory, "demo", ".from-package {value: yes}");
        var sourceDirectory = directory.resolve("src");
        Files.createDirectories(sourceDirectory);
        var input = sourceDirectory.resolve("main.scss");
        Files.writeString(input, "@use \"pkg:demo\";");
        return input;
    }

    /// Creates one installed Sass package.
    ///
    /// @param directory the project directory
    /// @param packageName the unscoped package name
    /// @param stylesheet the package stylesheet
    private static void createPackage(
            Path directory,
            String packageName,
            String stylesheet
    ) throws Exception {
        var packageRoot = directory.resolve("node_modules")
                .resolve(packageName);
        Files.createDirectories(packageRoot);
        Files.writeString(
                packageRoot.resolve("package.json"),
                "{\"sass\":\"./index.scss\"}"
        );
        Files.writeString(packageRoot.resolve("index.scss"), stylesheet);
    }

    /// Creates an isolated CLI with explicit stdin base and streams.
    ///
    /// @param workingDirectory the package importer fallback directory
    /// @param input standard-input contents
    /// @param output standard output
    /// @param error standard error
    /// @return the configured command line
    private static CommandLine commandLine(
            Path workingDirectory,
            String input,
            StringWriter output,
            StringWriter error
    ) {
        return SassFXMain.configure(new CommandLine(new SassFXMain(
                new ByteArrayInputStream(
                        input.getBytes(StandardCharsets.UTF_8)
                ),
                workingDirectory
        )))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));
    }
}
