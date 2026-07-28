// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/// Provides the SassFX implementation version embedded by the build.
@NotNullByDefault
public final class SassFXVersion {
    /// Contains the generated version resource path.
    private static final String RESOURCE =
            "/org/glavo/sassfx/sassfx-version.properties";

    /// Contains the version read once during class initialization.
    private static final String CURRENT = loadVersion();

    /// Prevents instantiation.
    private SassFXVersion() {
    }

    /// Returns the SassFX implementation version.
    ///
    /// @return the Gradle project version embedded in the runtime artifact
    public static String current() {
        return CURRENT;
    }

    /// Reads the generated version resource.
    ///
    /// @return the nonempty embedded version
    /// @throws ExceptionInInitializerError if the resource is absent or invalid
    private static String loadVersion() {
        var properties = new Properties();
        try (var input = SassFXVersion.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException(
                        "Missing SassFX version resource " + RESOURCE
                );
            }
            properties.load(input);
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }

        var version = Objects.requireNonNullElse(
                properties.getProperty("version"),
                ""
        ).strip();
        if (version.isEmpty() || version.equals("${version}")) {
            throw new ExceptionInInitializerError(
                    "Invalid SassFX implementation version"
            );
        }
        return version;
    }
}
