// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/// Writes complete CLI output files without exposing partially written content.
@NotNullByDefault
final class CliFileWriter {
    /// Prevents instantiation.
    private CliFileWriter() {
    }

    /// Replaces a UTF-8 text file with the complete supplied content.
    ///
    /// The temporary file is created beside the destination so that an atomic
    /// replacement can be used when the filesystem supports it.
    ///
    /// @param destination the file to replace
    /// @param contents the complete text contents
    /// @throws IOException if the parent directory or file cannot be written
    static void writeString(Path destination, String contents)
            throws IOException {
        write(destination, contents.getBytes(StandardCharsets.UTF_8));
    }

    /// Replaces a binary file with the complete supplied content.
    ///
    /// @param destination the file to replace
    /// @param contents the complete byte contents
    /// @throws IOException if the parent directory or file cannot be written
    static void write(Path destination, byte[] contents) throws IOException {
        var absoluteDestination = destination.toAbsolutePath().normalize();
        @Nullable Path parent = absoluteDestination.getParent();
        if (parent == null) {
            throw new IOException(
                    "output path has no parent directory: " + destination
            );
        }
        Files.createDirectories(parent);

        var fileName = absoluteDestination.getFileName().toString();
        var temporary = Files.createTempFile(
                parent,
                "." + fileName + ".",
                ".tmp"
        );
        var moved = false;
        try {
            Files.write(temporary, contents);
            try {
                Files.move(
                        temporary,
                        absoluteDestination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        absoluteDestination,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
