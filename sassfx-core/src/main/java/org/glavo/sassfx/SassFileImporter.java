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
/// This interface extends [SassImporter] so file importers and contents
/// importers can be stored in one ordered [CompileOptions#importers()] list.
/// The compiler recognizes this subtype and does not invoke the inherited
/// canonicalize or load methods.
@FunctionalInterface
@NotNullByDefault
public interface SassFileImporter extends SassImporter {
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

    /// Is not called for file importers.
    ///
    /// @param url the unused requested URL
    /// @param context the unused canonicalization context
    /// @return this method does not return normally
    /// @throws UnsupportedOperationException always
    @Override
    default @Nullable URI canonicalize(
            URI url,
            SassCanonicalizeContext context
    ) {
        throw new UnsupportedOperationException(
                "SassFileImporter canonicalize is compiler-managed"
        );
    }

    /// Is not called for file importers.
    ///
    /// @param canonicalUrl the unused canonical URL
    /// @return this method does not return normally
    /// @throws UnsupportedOperationException always
    @Override
    default @Nullable SassImporterResult load(URI canonicalUrl) {
        throw new UnsupportedOperationException(
                "SassFileImporter load is compiler-managed"
        );
    }
}
