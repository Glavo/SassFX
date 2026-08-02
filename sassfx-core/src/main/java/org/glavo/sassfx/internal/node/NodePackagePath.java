// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.node;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Applies Node package URL, path-boundary, and stylesheet-path rules.
@NotNullByDefault
final class NodePackagePath {
    /// Stylesheet extensions accepted for package exports and entry points.
    private static final @Unmodifiable List<String> VALID_EXTENSIONS =
            List.of(".scss", ".sass", ".css");

    /// Prevents instantiation.
    private NodePackagePath() {
    }

    /// Returns a plain file path from an absolute file URL.
    ///
    /// @param url the file URL
    /// @return the normalized filesystem path
    /// @throws IllegalArgumentException if the URL is not a plain file URL
    static Path filePath(URI url) {
        Objects.requireNonNull(url, "url");
        if (!url.isAbsolute()
                || !"file".equalsIgnoreCase(url.getScheme())
                || url.getRawQuery() != null
                || url.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Package importer file URL must be absolute and have no "
                            + "query or fragment: " + url
            );
        }
        return Path.of(url).normalize();
    }

    /// Converts a package slash path into a native relative path.
    ///
    /// Percent escapes are decoded as UTF-8. Empty segments are preserved only
    /// when accepted by the platform path implementation.
    ///
    /// @param value the package or export path
    /// @return the native relative path
    static Path nativePath(String value) {
        var decoded = percentDecode(Objects.requireNonNull(value, "value"));
        var parts = decoded.split("/", -1);
        var path = Path.of(parts[0]);
        for (var index = 1; index < parts.length; index++) {
            path = path.resolve(parts[index]);
        }
        return path;
    }

    /// Resolves a package-relative path without allowing it to escape the
    /// installed package directory.
    ///
    /// Empty, current-directory, parent-directory, `node_modules`, encoded
    /// separator, and native-separator segments are rejected before the
    /// platform filesystem interprets the path.
    ///
    /// @param packageRoot the normalized absolute installed package directory
    /// @param value the slash-separated package-relative path
    /// @param description the value description used in diagnostics
    /// @return the normalized path within `packageRoot`
    /// @throws IllegalStateException if the value is not a safe package path
    static Path resolve(
            Path packageRoot,
            String value,
            String description
    ) {
        Objects.requireNonNull(packageRoot, "packageRoot");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(description, "description");
        if (containsEncodedSeparator(value)) {
            throw invalidPackagePath(description, packageRoot);
        }
        var decoded = percentDecode(value);
        if (decoded.indexOf('\\') >= 0) {
            throw invalidPackagePath(description, packageRoot);
        }
        for (var segment : decoded.split("/", -1)) {
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")
                    || segment.equals("node_modules")) {
                throw invalidPackagePath(description, packageRoot);
            }
        }

        var resolved = packageRoot.resolve(nativePath(value)).normalize();
        if (!resolved.startsWith(packageRoot)) {
            throw invalidPackagePath(description, packageRoot);
        }
        return resolved;
    }

    /// Chooses an exact import-only sibling when legacy import semantics apply.
    ///
    /// @param path the exported or manifest-selected stylesheet
    /// @param fromImport whether the load originates from `@import`
    /// @return the import-only sibling when present, otherwise `path`
    static Path resolveImportOnly(Path path, boolean fromImport) {
        Objects.requireNonNull(path, "path");
        if (!fromImport) {
            return path;
        }
        var name = path.getFileName().toString();
        var dot = name.lastIndexOf('.');
        if (dot < 0) {
            return path;
        }
        var importOnly = path.resolveSibling(
                name.substring(0, dot) + ".import" + name.substring(dot)
        );
        return Files.isRegularFile(importOnly) ? importOnly : path;
    }

    /// Removes the conventional `./` prefix from a manifest path.
    ///
    /// @param value the manifest path
    /// @return the path without one leading `./`
    static String stripRelativePrefix(String value) {
        Objects.requireNonNull(value, "value");
        return value.startsWith("./") ? value.substring(2) : value;
    }

    /// Returns a canonical absolute file URL when possible.
    ///
    /// Existing files resolve symbolic links and platform aliases. Missing
    /// manifest-selected targets remain normalized so their eventual load
    /// reports the filesystem failure.
    ///
    /// @param path the selected stylesheet path
    /// @return the canonical or normalized absolute file URL
    /// @throws IOException if an existing file cannot be canonicalized
    static URI canonicalFileUrl(Path path) throws IOException {
        var absolute = Objects.requireNonNull(path, "path")
                .toAbsolutePath()
                .normalize();
        return (Files.exists(absolute) ? absolute.toRealPath() : absolute)
                .toUri();
    }

    /// Returns the case-sensitive final extension of a slash path.
    ///
    /// @param value the slash or native path text
    /// @return the extension including its dot, or an empty string
    static String extension(String value) {
        Objects.requireNonNull(value, "value");
        var slash = Math.max(
                value.lastIndexOf('/'),
                value.lastIndexOf('\\')
        );
        var dot = value.lastIndexOf('.');
        return dot > slash ? value.substring(dot) : "";
    }

    /// Tests whether a path has a supported case-sensitive Sass extension.
    ///
    /// @param path the filesystem path
    /// @return whether the extension is supported
    static boolean validExtension(Path path) {
        return validExtension(
                Objects.requireNonNull(path, "path")
                        .getFileName()
                        .toString()
        );
    }

    /// Tests whether text has a supported case-sensitive Sass extension.
    ///
    /// @param value a package target or manifest field
    /// @return whether the extension is supported
    static boolean validExtension(String value) {
        return VALID_EXTENSIONS.contains(extension(value));
    }

    /// Decodes percent escapes without interpreting plus as a space.
    ///
    /// @param value the URL path text
    /// @return the decoded UTF-8 text
    private static String percentDecode(String value) {
        var output = new ByteArrayOutputStream(value.length());
        for (var index = 0; index < value.length(); index++) {
            var current = value.charAt(index);
            if (current != '%') {
                var codePoint = value.codePointAt(index);
                output.writeBytes(
                        new String(Character.toChars(codePoint))
                                .getBytes(StandardCharsets.UTF_8)
                );
                index += Character.charCount(codePoint) - 1;
                continue;
            }
            if (index + 2 >= value.length()) {
                throw new IllegalArgumentException(
                        "Invalid percent escape in package path: " + value
                );
            }
            var high = Character.digit(value.charAt(++index), 16);
            var low = Character.digit(value.charAt(++index), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException(
                        "Invalid percent escape in package path: " + value
                );
            }
            output.write(high << 4 | low);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    /// Returns whether a path contains a percent-encoded slash or backslash.
    ///
    /// @param value the raw path
    /// @return whether a separator is encoded
    private static boolean containsEncodedSeparator(String value) {
        for (var index = 0; index + 2 < value.length(); index++) {
            if (value.charAt(index) != '%') {
                continue;
            }
            var high = Character.digit(value.charAt(index + 1), 16);
            var low = Character.digit(value.charAt(index + 2), 16);
            if (high >= 0 && low >= 0) {
                var decoded = high << 4 | low;
                if (decoded == '/' || decoded == '\\') {
                    return true;
                }
            }
        }
        return false;
    }

    /// Creates the standard package-boundary failure.
    ///
    /// @param description the rejected value description
    /// @param packageRoot the installed package root
    /// @return the path-validation failure
    private static IllegalStateException invalidPackagePath(
            String description,
            Path packageRoot
    ) {
        return new IllegalStateException(
                description + " must be a path within the package root at '"
                        + packageRoot + "'."
        );
    }
}
