// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Guarantees that imported sass-spec archives never carry scssfx expectation overrides.
///
/// Project-local files such as {@code output-scssfx.css} and {@code error-scssfx}
/// are forbidden so language failures cannot be hidden by private expectations.
@NotNullByDefault
final class SassSpecNoOverrideTest {
    private static final String RESOURCE_ROOT = "sass-spec/";
    private static final String MANIFEST_RESOURCE = RESOURCE_ROOT + "index.json";

    /// Scans every HRX path listed by the manifest for forbidden override names.
    @Test
    void importedArchivesContainNoScssfxExpectationOverrides() throws IOException {
        var manifest = SassSpecManifest.parse(readResource(MANIFEST_RESOURCE), MANIFEST_RESOURCE);
        var forbidden = new ArrayList<String>();
        for (var archive : manifest.archives()) {
            String resource = RESOURCE_ROOT + archive.path();
            HrxArchive hrx = HrxArchive.parse(readResource(resource), resource);
            for (String path : hrx.paths()) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (isForbiddenOverrideName(name)) {
                    forbidden.add(archive.path() + ":" + path);
                }
            }
        }
        assertTrue(
                forbidden.isEmpty(),
                () -> "scssfx expectation overrides must be zero, found: " + forbidden
        );
    }

    /// Returns whether a virtual file name is a forbidden override.
    private static boolean isForbiddenOverrideName(String name) {
        return name.equals("output-scssfx.css")
                || name.equals("error-scssfx")
                || name.equals("scssfx-expect.json")
                || name.startsWith("output-scssfx")
                || name.startsWith("error-scssfx");
    }

    private static String readResource(String resource) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing resource: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
