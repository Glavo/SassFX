// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.bss;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.css.CssStylesheet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/// Resolves and evaluates a stylesheet referenced by a JavaFX CSS import.
@ApiStatus.Internal
@FunctionalInterface
@NotNullByDefault
public interface BssImportResolver {
    /// Resolves one imported stylesheet.
    ///
    /// @param resource the decoded import resource
    /// @param baseUrl  the containing stylesheet URL, or `null`
    /// @param span     the source range of the import rule
    /// @return the evaluated imported stylesheet and its canonical URL
    /// @throws IOException if the resource cannot be loaded
    ResolvedImport resolve(
            String resource,
            @Nullable URI baseUrl,
            SourceSpan span
    ) throws IOException;

    /// Stores one evaluated imported stylesheet.
    ///
    /// @param stylesheet  the evaluated CSS IR root
    /// @param canonicalUrl the absolute canonical URL of the imported resource
    @ApiStatus.Internal
    @NotNullByDefault
    record ResolvedImport(CssStylesheet stylesheet, URI canonicalUrl) {
        /// Validates resolved import components.
        public ResolvedImport {
            Objects.requireNonNull(stylesheet, "stylesheet");
            Objects.requireNonNull(canonicalUrl, "canonicalUrl");
            if (!canonicalUrl.isAbsolute()) {
                throw new IllegalArgumentException("canonicalUrl must be absolute");
            }
        }
    }
}
