// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.glavo.sassfx.internal.module.FilesystemImporter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Resolves and loads Sass stylesheets addressed through Node packages.
///
/// Instances retain only the normalized fallback lookup directory. Package
/// manifests and stylesheet files are read for each operation.
@NotNullByDefault
final class NodePackageResolver {
    /// The absolute base used when a request has no file containing URL.
    private final Path entryPointDirectory;

    /// Creates a resolver rooted at one entry-point directory.
    ///
    /// @param entryPointDirectory the fallback directory for package lookup
    NodePackageResolver(Path entryPointDirectory) {
        this.entryPointDirectory = Objects.requireNonNull(
                entryPointDirectory,
                "entryPointDirectory"
        ).toAbsolutePath().normalize();
    }

    /// Returns the fallback package lookup directory.
    ///
    /// @return the absolute normalized entry-point directory
    Path entryPointDirectory() {
        return entryPointDirectory;
    }

    /// Resolves a `pkg:` URL or a file URL loaded relative to a package
    /// stylesheet.
    ///
    /// @param url the requested Sass URL
    /// @param context contextual information about the load
    /// @return an absolute canonical file URL, or `null` when unresolved
    /// @throws IOException if package metadata or the filesystem cannot be read
    @Nullable URI canonicalize(
            URI url,
            SassCanonicalizeContext context
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(context, "context");

        if ("file".equalsIgnoreCase(url.getScheme())) {
            @Nullable Path resolved = FilesystemImporter.resolveAt(
                    NodePackagePath.filePath(url),
                    context.fromImport()
            );
            return resolved == null
                    ? null
                    : NodePackagePath.canonicalFileUrl(resolved);
        }
        if (!"pkg".equalsIgnoreCase(url.getScheme())) {
            return null;
        }

        var specifier = packageSpecifier(url);
        var split = splitSpecifier(specifier);
        if (!validPackageName(split.packageName())) {
            return null;
        }

        var baseDirectory = baseDirectory(context.containingUrl());
        @Nullable Path packageRoot = resolvePackageRoot(
                split.packageName(),
                baseDirectory
        );
        if (packageRoot == null) {
            return null;
        }

        var manifest = NodePackageManifest.read(
                packageRoot.resolve("package.json"),
                split.packageName()
        );
        @Nullable Path exported = NodePackageExports.resolve(
                packageRoot,
                split.subpath(),
                manifest,
                split.packageName(),
                context.fromImport()
        );
        if (exported != null) {
            if (!NodePackagePath.validExtension(exported)) {
                throw new IllegalStateException(
                        "The export for '"
                                + Objects.requireNonNullElse(
                                split.subpath(),
                                "root"
                        )
                                + "' in '" + split.packageName()
                                + "' resolved to '" + exported
                                + "', which is not a '.scss', '.sass', or "
                                + "'.css' file."
                );
            }
            return NodePackagePath.canonicalFileUrl(exported);
        }

        if (split.subpath() == null) {
            @Nullable Path rootValue = resolvePackageRootValue(
                    packageRoot,
                    manifest,
                    context.fromImport()
            );
            return rootValue == null
                    ? null
                    : NodePackagePath.canonicalFileUrl(rootValue);
        }

        @Nullable Path subpath = FilesystemImporter.resolveAt(
                NodePackagePath.resolve(
                        packageRoot,
                        split.subpath(),
                        "Package subpath '" + split.subpath() + "'"
                ),
                context.fromImport()
        );
        return subpath == null
                ? null
                : NodePackagePath.canonicalFileUrl(subpath);
    }

    /// Loads an exact canonical file URL returned by [#canonicalize].
    ///
    /// @param canonicalUrl the canonical file URL
    /// @return the decoded stylesheet and its file URL for source maps
    /// @throws IOException if the file cannot be read
    /// @throws IllegalArgumentException if the URL is not a plain file URL
    SassImporterResult load(URI canonicalUrl) throws IOException {
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        var path = NodePackagePath.filePath(canonicalUrl);
        var syntax = Syntax.forPath(path);
        if (syntax == null) {
            throw new IOException(
                    "Package stylesheet has an unsupported extension: " + path
            );
        }
        return new SassImporterResult(
                Files.readString(path, StandardCharsets.UTF_8),
                syntax,
                canonicalUrl
        );
    }

    /// Returns the directory used for one package lookup.
    ///
    /// @param containingUrl the containing canonical URL, or `null`
    /// @return the containing file directory or configured entry-point base
    private Path baseDirectory(@Nullable URI containingUrl) {
        if (containingUrl != null
                && "file".equalsIgnoreCase(containingUrl.getScheme())) {
            @Nullable Path parent = NodePackagePath.filePath(containingUrl)
                    .getParent();
            if (parent != null) {
                return parent;
            }
        }
        return entryPointDirectory;
    }

    /// Validates and returns the raw package specifier from a `pkg:` URL.
    ///
    /// @param url the package URL
    /// @return the raw path after the scheme
    private static String packageSpecifier(URI url) {
        if (url.getRawAuthority() != null) {
            throw new IllegalStateException(
                    "A pkg: URL must not have a host, port, username or password."
            );
        }

        @Nullable String rawSpecifier = url.isOpaque()
                ? url.getRawSchemeSpecificPart()
                : url.getRawPath();
        if (rawSpecifier == null) {
            rawSpecifier = "";
        }
        var queryIndex = rawSpecifier.indexOf('?');
        var specifier = queryIndex < 0
                ? rawSpecifier
                : rawSpecifier.substring(0, queryIndex);
        var hasQueryOrFragment = queryIndex >= 0
                || url.getRawQuery() != null
                || url.getRawFragment() != null;
        if (specifier.startsWith("/")) {
            throw new IllegalStateException(
                    "A pkg: URL's path must not begin with /."
            );
        }
        if (specifier.isEmpty()) {
            throw new IllegalStateException(
                    "A pkg: URL must not have an empty path."
            );
        }
        if (hasQueryOrFragment) {
            throw new IllegalStateException(
                    "A pkg: URL must not have a query or fragment."
            );
        }
        return specifier;
    }

    /// Splits a bare package specifier into its package name and subpath.
    ///
    /// @param specifier the raw slash-separated package specifier
    /// @return the package name and optional subpath
    private static PackageSpecifier splitSpecifier(String specifier) {
        var parts = new ArrayList<>(List.of(specifier.split("/", -1)));
        var packageName = parts.remove(0);
        if (packageName.startsWith("@") && !parts.isEmpty()) {
            packageName += "/" + parts.remove(0);
        }
        @Nullable String subpath = parts.isEmpty()
                ? null
                : String.join("/", parts);
        return new PackageSpecifier(packageName, subpath);
    }

    /// Tests whether a package name is eligible for Node lookup.
    ///
    /// @param packageName the unescaped package name
    /// @return whether the name is structurally valid
    private static boolean validPackageName(String packageName) {
        return !packageName.startsWith(".")
                && packageName.indexOf('\\') < 0
                && packageName.indexOf('%') < 0
                && (!packageName.startsWith("@")
                || packageName.indexOf('/') >= 0);
    }

    /// Finds the closest installed package directory.
    ///
    /// @param packageName the bare package name
    /// @param baseDirectory the directory where ancestor lookup begins
    /// @return the package root, or `null` when not installed
    private static @Nullable Path resolvePackageRoot(
            String packageName,
            Path baseDirectory
    ) {
        @Nullable Path current = baseDirectory.toAbsolutePath().normalize();
        var packagePath = NodePackagePath.nativePath(packageName);
        while (current != null) {
            var candidate = current.resolve("node_modules")
                    .resolve(packagePath);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    /// Resolves the root `sass`, `style`, or index entry point.
    ///
    /// @param packageRoot the installed package root
    /// @param manifest the parsed package manifest
    /// @param fromImport whether import-only files take precedence
    /// @return the resolved entry point, or `null`
    private static @Nullable Path resolvePackageRootValue(
            Path packageRoot,
            NodePackageManifest manifest,
            boolean fromImport
    ) {
        @Nullable Object sass = manifest.value("sass");
        if (sass instanceof String sassValue
                && NodePackagePath.validExtension(sassValue)) {
            return NodePackagePath.resolveImportOnly(
                    NodePackagePath.resolve(
                            packageRoot,
                            NodePackagePath.stripRelativePrefix(sassValue),
                            "The 'sass' field"
                    ),
                    fromImport
            );
        }
        @Nullable Object style = manifest.value("style");
        if (style instanceof String styleValue
                && NodePackagePath.validExtension(styleValue)) {
            return NodePackagePath.resolveImportOnly(
                    NodePackagePath.resolve(
                            packageRoot,
                            NodePackagePath.stripRelativePrefix(styleValue),
                            "The 'style' field"
                    ),
                    fromImport
            );
        }
        return FilesystemImporter.resolveAt(
                packageRoot.resolve("index"),
                fromImport
        );
    }

    /// Contains a split package request.
    ///
    /// @param packageName the bare package name
    /// @param subpath the package-relative subpath, or `null`
    @NotNullByDefault
    private record PackageSpecifier(
            String packageName,
            @Nullable String subpath
    ) {
        /// Creates a validated split package request.
        private PackageSpecifier {
            Objects.requireNonNull(packageName, "packageName");
        }
    }
}
