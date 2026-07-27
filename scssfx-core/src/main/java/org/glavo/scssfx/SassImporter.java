// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/// Resolves stylesheet URLs and supplies their source text to the compiler.
///
/// Importers configured on one compilation are consulted in list order before
/// filesystem load paths. When a stylesheet loaded by an importer performs a
/// relative load, that importer is consulted first with a URL resolved against
/// the containing stylesheet's canonical URL.
///
/// Implementations may be invoked more than once for the same request and must
/// not rely on a particular invocation count. A single importer instance may
/// be used concurrently when the same [CompileOptions] is shared by concurrent
/// compilations; implementations are responsible for synchronizing mutable
/// state.
@NotNullByDefault
public interface SassImporter {
    /// Reports whether a URL scheme requires the containing URL during
    /// canonicalization even though requests using it are absolute.
    ///
    /// Most absolute URLs are canonical requests and receive a context whose
    /// containing URL is {@code null}. Importers for non-canonical schemes,
    /// such as {@code pkg:}, may return {@code true} so that package or other
    /// contextual resolution can begin beside the containing stylesheet.
    ///
    /// @param scheme the lower- or mixed-case absolute URL scheme
    /// @return whether requests using the scheme require a containing URL
    default boolean isNonCanonicalScheme(String scheme) {
        Objects.requireNonNull(scheme, "scheme");
        return false;
    }

    /// Converts a requested URL into the URL that uniquely identifies
    /// the stylesheet.
    ///
    /// Returning {@code null} declines the request and allows the next
    /// configured importer or load path to handle it. A non-{@code null}
    /// result must be absolute. For Dart Sass 1.x compatibility, the compiler
    /// currently accepts relative results, reports the `relative-canonical`
    /// deprecation, and passes the same relative URL to [#load(URI)]. A future
    /// release may reject them. Once a URL is returned, this importer's
    /// [#load(URI)] method is used and no later importer is considered.
    ///
    /// @param url the requested URL, which may be relative
    /// @param context contextual information about the load
    /// @return the canonical URL, or {@code null} to decline the request
    /// @throws IOException if canonicalization fails
    @Nullable URI canonicalize(
            URI url,
            SassCanonicalizeContext context
    ) throws IOException;

    /// Loads the stylesheet identified by a URL returned from
    /// [#canonicalize(URI, SassCanonicalizeContext)].
    ///
    /// Returning {@code null} reports the stylesheet as unavailable. The
    /// compiler will not try a later importer for the original request.
    ///
    /// @param canonicalUrl the canonical URL returned by this importer
    /// @return the stylesheet contents and syntax, or {@code null} when unavailable
    /// @throws IOException if loading fails
    @Nullable SassImporterResult load(URI canonicalUrl) throws IOException;
}
