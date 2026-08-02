// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.glavo.sassfx.internal.node.NodePackageResolver;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/// Resolves {@code pkg:} URLs using Node package lookup and Sass package
/// metadata conventions.
///
/// Resolution begins beside the containing file when one is available and
/// otherwise at the configured entry-point directory. Each ancestor is
/// searched for {@code node_modules/<package>/package.json}. Package
/// {@code exports} are resolved before the {@code sass} and {@code style}
/// fields and filesystem subpaths.
///
/// Instances are immutable and safe for concurrent compilations. Package
/// manifests and stylesheet files are read for each canonicalization or load;
/// one compiler invocation still applies its normal canonical-URL load cache.
@NotNullByDefault
public final class SassNodePackageImporter implements SassContentsImporter {
    /// Resolves package metadata and stylesheet paths.
    private final NodePackageResolver resolver;

    /// Creates an importer rooted at the supplied entry-point directory.
    ///
    /// The path is made absolute and normalized but is not required to exist
    /// until a package request is resolved.
    ///
    /// @param entryPointDirectory the fallback directory for package lookup
    public SassNodePackageImporter(Path entryPointDirectory) {
        resolver = new NodePackageResolver(entryPointDirectory);
    }

    /// Returns the fallback package lookup directory.
    ///
    /// @return the absolute normalized entry-point directory
    public Path entryPointDirectory() {
        return resolver.entryPointDirectory();
    }

    /// Reports {@code pkg} as a contextual, non-canonical URL scheme.
    ///
    /// @param scheme the requested absolute URL scheme
    /// @return whether {@code scheme} is {@code pkg}, ignoring case
    @Override
    public boolean isNonCanonicalScheme(String scheme) {
        Objects.requireNonNull(scheme, "scheme");
        return "pkg".equalsIgnoreCase(scheme);
    }

    /// Resolves a {@code pkg:} URL or a file URL loaded relative to a package
    /// stylesheet.
    ///
    /// Non-{@code pkg:} and non-{@code file:} URLs are declined. Invalid
    /// {@code pkg:} URL structure and invalid package metadata fail the
    /// compilation rather than falling through to another importer.
    ///
    /// @param url the requested Sass URL
    /// @param context contextual information about the load
    /// @return an absolute canonical file URL, or {@code null} when unresolved
    /// @throws IOException if package metadata or the filesystem cannot be read
    @Override
    public @Nullable URI canonicalize(
            URI url,
            SassCanonicalizeContext context
    ) throws IOException {
        return resolver.canonicalize(url, context);
    }

    /// Loads an exact canonical file URL returned by
    /// [#canonicalize(URI, SassCanonicalizeContext)].
    ///
    /// @param canonicalUrl the canonical file URL
    /// @return the decoded stylesheet and its file URL for source maps
    /// @throws IOException if the file cannot be read
    /// @throws IllegalArgumentException if the URL is not a plain file URL
    @Override
    public SassImporterResult load(URI canonicalUrl)
            throws IOException {
        return resolver.load(canonicalUrl);
    }
}
