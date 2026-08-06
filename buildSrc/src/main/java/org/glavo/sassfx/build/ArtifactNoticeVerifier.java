// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.build;

import org.gradle.api.GradleException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/// Verifies that a published JAR carries the required third-party notices.
@NotNullByDefault
final class ArtifactNoticeVerifier {
    /// Prevents instantiation.
    private ArtifactNoticeVerifier() {
    }

    /// Verifies the third-party notice entry in the supplied JAR.
    ///
    /// @param jar the JAR to inspect
    /// @param artifact the artifact name used in diagnostics
    /// @throws IOException if the notice cannot be read
    static void verify(JarFile jar, String artifact) throws IOException {
        @Nullable JarEntry entry = jar.getJarEntry(
                "META-INF/THIRD-PARTY-NOTICES.md"
        );
        if (entry == null) {
            throw new GradleException(
                    artifact + " has no META-INF/THIRD-PARTY-NOTICES.md."
            );
        }

        String notice;
        try (var input = jar.getInputStream(entry)) {
            notice = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        @Unmodifiable List<String> requiredNotices = List.of(
                "### Apache License 2.0",
                "Copyright (c) 2021 Lars Grefer",
                "Copyright 2008 Google Inc.  All rights reserved."
        );
        @Unmodifiable List<String> missingNotices = requiredNotices.stream()
                .filter(required -> !notice.contains(required))
                .toList();
        if (!missingNotices.isEmpty()) {
            throw new GradleException(
                    artifact + " has incomplete third-party notices: "
                            + String.join(", ", missingNotices)
            );
        }
    }
}
