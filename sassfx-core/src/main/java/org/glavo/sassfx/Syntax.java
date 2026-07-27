// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

/// Identifies the syntax used by a stylesheet source.
@NotNullByDefault
public enum Syntax {
    /// The brace-delimited SCSS syntax.
    SCSS,

    /// The indentation-based Sass syntax.
    SASS,

    /// Plain CSS parsed according to the Sass CSS compatibility rules.
    CSS;

    /// Returns the syntax implied by the final extension of the given path.
    ///
    /// The comparison is case-insensitive. A path ending in {@code .scss},
    /// {@code .sass}, or {@code .css} maps to the corresponding syntax.
    ///
    /// @param path the path whose file name is inspected
    /// @return the inferred syntax, or {@code null} when the extension is not recognized
    public static @Nullable Syntax forPath(Path path) {
        var fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }

        var name = fileName.toString();
        var separator = name.lastIndexOf('.');
        if (separator <= 0 || separator == name.length() - 1) {
            return null;
        }

        return switch (name.substring(separator + 1).toLowerCase(Locale.ROOT)) {
            case "scss" -> SCSS;
            case "sass" -> SASS;
            case "css" -> CSS;
            default -> null;
        };
    }
}
