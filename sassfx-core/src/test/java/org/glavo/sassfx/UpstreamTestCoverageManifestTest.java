// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the complete Dart Sass 1.102.0 test-file coverage manifest.
@NotNullByDefault
final class UpstreamTestCoverageManifestTest {
    /// Contains the repository-relative manifest path.
    private static final Path MANIFEST = Path.of(
            "gradle",
            "verification",
            "dart-sass-1.102.0-tests.tsv"
    );

    /// Contains the expected manifest column names.
    private static final String HEADER = String.join(
            "\t",
            "upstream_file",
            "static_tests",
            "static_groups",
            "disposition",
            "java_sources",
            "contract"
    );

    /// Contains the accepted test-port dispositions.
    private static final @Unmodifiable Set<String> DISPOSITIONS = Set.of(
            "PORTED",
            "EQUIVALENT",
            "ADAPTED",
            "SUPPORT"
    );

    /// Contains the checksum of every upstream path and its static test counts.
    private static final String INVENTORY_SHA256 =
            "3ceb5820e4cef8eb737f6f509bcbcbe67e95586ceaf18d28e451e6ebc504ba6b";

    /// Verifies inventory identity, aggregate counts, and mapped Java sources.
    ///
    /// @throws IOException if the checked-in manifest cannot be read
    /// @throws NoSuchAlgorithmException if the Java runtime lacks SHA-256
    @Test
    void coversEveryPinnedUpstreamTestFile()
            throws IOException, NoSuchAlgorithmException {
        var root = Path.of(Objects.requireNonNull(
                System.getProperty("sassfx.test.rootDirectory"),
                "sassfx.test.rootDirectory"
        ));
        var manifest = root.resolve(MANIFEST);
        var lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);

        assertEquals(60, lines.size(), "header plus 59 upstream files");
        assertEquals(HEADER, lines.get(0));

        var paths = new HashSet<String>();
        var inventory = new StringBuilder();
        var testCount = 0;
        var groupCount = 0;
        for (var lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            var fields = lines.get(lineNumber - 1).split("\t", -1);
            assertEquals(6, fields.length, "manifest line " + lineNumber);

            var upstreamPath = fields[0];
            assertTrue(upstreamPath.startsWith("test/"), upstreamPath);
            assertTrue(upstreamPath.endsWith(".dart"), upstreamPath);
            assertTrue(paths.add(upstreamPath), "duplicate " + upstreamPath);

            var tests = Integer.parseInt(fields[1]);
            var groups = Integer.parseInt(fields[2]);
            assertTrue(tests >= 0, upstreamPath);
            assertTrue(groups >= 0, upstreamPath);
            testCount += tests;
            groupCount += groups;
            inventory.append(upstreamPath)
                    .append('\t').append(tests)
                    .append('\t').append(groups)
                    .append('\n');

            assertTrue(DISPOSITIONS.contains(fields[3]), upstreamPath);
            assertFalse(fields[4].isBlank(), upstreamPath);
            assertFalse(fields[5].isBlank(), upstreamPath);
            for (var source : fields[4].split("; ")) {
                var relative = Path.of(source);
                assertFalse(relative.isAbsolute(), source);
                assertFalse(relative.normalize().startsWith(".."), source);
                assertTrue(Files.isRegularFile(root.resolve(relative)), source);
            }
        }

        assertEquals(59, paths.size());
        assertEquals(1_068, testCount);
        assertEquals(351, groupCount);
        assertEquals(INVENTORY_SHA256, sha256(inventory.toString()));
    }

    /// Returns the lowercase SHA-256 digest of UTF-8 text.
    ///
    /// @param value the text to hash
    /// @return the 64-character lowercase hexadecimal digest
    /// @throws NoSuchAlgorithmException if the Java runtime lacks SHA-256
    private static String sha256(String value) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8)
        );
        return HexFormat.of().formatHex(digest);
    }
}
