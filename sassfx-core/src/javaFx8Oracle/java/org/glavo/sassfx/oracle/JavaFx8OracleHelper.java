// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.oracle;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/// Runs the JavaFX 8 side of the cross-process binary stylesheet oracle.
///
/// The helper is compiled for Java 8 and accesses JavaFX through reflection so
/// its compile classpath contains neither JavaFX nor SassFX product classes.
@NotNullByDefault
public final class JavaFX8OracleHelper {
    /// Prevents instantiation.
    private JavaFX8OracleHelper() {
    }

    /// Generates JavaFX 8 BSS, compares it with SassFX, and loads the result.
    ///
    /// @param arguments one argument naming the generated input directory
    /// @throws Exception if runtime validation, conversion, comparison, or loading fails
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one oracle directory.");
        }
        String javaVersion = System.getProperty("java.version", "");
        if (!javaVersion.startsWith("1.8.")) {
            throw new IllegalStateException(
                    "JavaFX 8 oracle requires a Java 8 runtime; found " + javaVersion
            );
        }

        Class<?> stylesheetClass = Class.forName("com.sun.javafx.css.Stylesheet");
        String javaFXVersion = System.getProperty(
                "javafx.runtime.version",
                System.getProperty("javafx.version", "")
        );
        if (!javaFXVersion.isEmpty() && !javaFXVersion.startsWith("8")) {
            throw new IllegalStateException(
                    "JavaFX 8 oracle requires a JavaFX 8 runtime; found " + javaFXVersion
            );
        }

        Path directory = Paths.get(arguments[0]).toAbsolutePath();
        Path source = directory.resolve("fixture.css");
        Path expectedPath = directory.resolve("expected.bss");
        Path actualPath = directory.resolve("actual.bss");
        Method convertToBinary = stylesheetClass.getMethod(
                "convertToBinary",
                File.class,
                File.class
        );
        convertToBinary.invoke(null, source.toFile(), expectedPath.toFile());

        byte[] expected = Files.readAllBytes(expectedPath);
        byte[] actual = Files.readAllBytes(actualPath);
        requireVersion5("OpenJFX", expected);
        requireVersion5("SassFX", actual);
        int mismatch = firstMismatch(expected, actual);
        if (mismatch >= 0) {
            throw new AssertionError(
                    "JavaFX 8 BSS differs at byte " + mismatch
                            + "; expected=" + Base64.getEncoder().encodeToString(expected)
                            + "; actual=" + Base64.getEncoder().encodeToString(actual)
            );
        }

        Method loadBinary = stylesheetClass.getMethod("loadBinary", java.net.URL.class);
        @Nullable Object loaded = loadBinary.invoke(null, actualPath.toUri().toURL());
        if (loaded == null) {
            throw new AssertionError("JavaFX 8 returned null when loading SassFX BSS.");
        }
    }

    /// Requires one document to declare BSS format version 5.
    ///
    /// @param producer the document producer named in an error
    /// @param bytes    the complete BSS document
    private static void requireVersion5(
            String producer,
            byte @Unmodifiable [] bytes
    ) {
        if (bytes.length < 2) {
            throw new AssertionError(producer + " produced a truncated BSS document.");
        }
        int version = (Byte.toUnsignedInt(bytes[0]) << 8)
                | Byte.toUnsignedInt(bytes[1]);
        if (version != 5) {
            throw new AssertionError(
                    producer + " produced BSS version " + version + " instead of 5."
            );
        }
    }

    /// Returns the first differing byte index, or {@code -1} when arrays match.
    ///
    /// @param expected the expected bytes
    /// @param actual   the actual bytes
    /// @return the first mismatch, or {@code -1}
    private static int firstMismatch(
            byte @Unmodifiable [] expected,
            byte @Unmodifiable [] actual
    ) {
        int limit = Math.min(expected.length, actual.length);
        for (int index = 0; index < limit; index++) {
            if (expected[index] != actual[index]) {
                return index;
            }
        }
        return expected.length == actual.length ? -1 : limit;
    }
}
