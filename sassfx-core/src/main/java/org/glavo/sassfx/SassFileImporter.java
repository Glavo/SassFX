// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;

/// Maps Sass URLs to filesystem locations that the compiler resolves and reads.
///
/// The compiler applies the standard Sass extension, partial, import-only, and
/// directory-index rules to a returned file URL. Requests that are already
/// absolute {@code file:} URLs bypass
/// [#findFileUrl(URI, SassCanonicalizeContext)] and are resolved directly.
///
/// File importers and [SassContentsImporter] instances share one ordered
/// [CompileOptions#importers()] list, but expose disjoint operations so an
/// implementation never needs placeholder methods that cannot be called.
@FunctionalInterface
@NotNullByDefault
public non-sealed interface SassFileImporter extends SassImporter {
    /// Finds the filesystem location corresponding to a Sass URL.
    ///
    /// The returned URL must be absolute, use the {@code file} scheme, and
    /// contain no query or fragment. It may omit a Sass extension or name a
    /// non-partial file; the compiler performs normal Sass file resolution.
    /// Returning {@code null} declines the request.
    ///
    /// @param url the requested URL, which may be relative or use a custom scheme
    /// @param context contextual information about the load
    /// @return an absolute file URL, or {@code null} to decline the request
    /// @throws IOException if locating the file fails
    @Nullable URI findFileUrl(
            URI url,
            SassCanonicalizeContext context
    ) throws IOException;

}
