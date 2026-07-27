// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/// Resolves retained JavaFX CSS imports for binary stylesheet compilation.
///
/// A resolver is consulted before the compiler's default exact-file lookup.
/// Returning `null` means that the resolver does not handle the resource and
/// delegates it to that filesystem lookup. A resolver that recognizes a
/// resource but cannot load or decode it must throw [IOException]. This permits
/// explicit support for application-defined URI schemes without enabling
/// implicit network access.
///
/// Resolution is synchronous. A resolver may be called more than once for the
/// same resource. Implementations shared by concurrent compilations must be
/// thread-safe. The caller owns the resolver; the compiler neither closes it
/// nor assumes that it owns resources used by the implementation.
@FunctionalInterface
@NotNullByDefault
public interface JavaFXStylesheetResolver {
    /// Resolves and loads one imported plain-CSS stylesheet.
    ///
    /// The resource is the decoded, nonempty string from the `@import` rule.
    /// The base URL identifies the containing stylesheet when that stylesheet
    /// has a canonical URL. Implementations are responsible for resolving
    /// relative resources and returning a stable absolute canonical URL. In
    /// one compilation, equal canonical URLs must identify the same content,
    /// and aliases for one stylesheet should use equal canonical URLs.
    ///
    /// @param resource the decoded import resource
    /// @param baseUrl  the containing stylesheet URL, or {@code null}
    /// @return the resolved stylesheet, or {@code null} when this resolver does
    ///         not handle the resource
    /// @throws IOException if the resolver recognizes the resource but cannot load it
    @Nullable ResolvedStylesheet resolve(
            String resource,
            @Nullable URI baseUrl
    ) throws IOException;

    /// Stores resolved plain-CSS text and its canonical identity.
    ///
    /// The canonical URL is used as the base of relative nested imports and as
    /// the identity for cycle detection, diagnostics, and
    /// [CompileResult#loadedUrls()]. Equal canonical URLs are treated as the
    /// same stylesheet.
    ///
    /// Content must be complete, decoded plain CSS. The resolver owns byte
    /// decoding and must close any stream or other resource used to produce the
    /// string before returning.
    ///
    /// @param canonicalUrl the absolute canonical URL of the stylesheet
    /// @param content      the complete decoded plain-CSS text
    @NotNullByDefault
    record ResolvedStylesheet(URI canonicalUrl, String content) {
        /// Validates the resolved stylesheet.
        public ResolvedStylesheet {
            Objects.requireNonNull(canonicalUrl, "canonicalUrl");
            Objects.requireNonNull(content, "content");
            if (!canonicalUrl.isAbsolute()) {
                throw new IllegalArgumentException("canonicalUrl must be absolute");
            }
        }
    }
}
