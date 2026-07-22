// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.Syntax;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.net.URI;
import java.util.Objects;

/// The result of resolving and loading one stylesheet file.
///
/// @param source       the indexed source text
/// @param syntax       the syntax of the loaded file
/// @param canonicalUrl the absolute canonical URL of the file
@ApiStatus.Internal
@NotNullByDefault
public record ImportResult(
        SourceFile source,
        Syntax syntax,
        URI canonicalUrl
) {
    /// Creates an import result.
    public ImportResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        if (!canonicalUrl.isAbsolute()) {
            throw new IllegalArgumentException("canonicalUrl must be absolute");
        }
    }
}
