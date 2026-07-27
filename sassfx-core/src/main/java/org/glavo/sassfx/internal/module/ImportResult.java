// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.Syntax;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.net.URI;
import java.util.Objects;

/// The result of resolving and loading one stylesheet file.
///
/// @param source       the indexed source text
/// @param syntax       the syntax of the loaded file
/// @param canonicalUrl the canonical URL of the file
/// @param dependency whether compiler warnings from this stylesheet are
///                   dependency warnings
@ApiStatus.Internal
@NotNullByDefault
public record ImportResult(
        SourceFile source,
        Syntax syntax,
        URI canonicalUrl,
        boolean dependency
) {
    /// Creates a first-party import result.
    ///
    /// @param source the indexed source text
    /// @param syntax the parsed syntax
    /// @param canonicalUrl the absolute canonical URL
    public ImportResult(
            SourceFile source,
            Syntax syntax,
            URI canonicalUrl
    ) {
        this(source, syntax, canonicalUrl, false);
    }

    /// Creates an import result.
    public ImportResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
    }

    /// Returns this result with dependency provenance applied.
    ///
    /// @param dependency whether compiler warnings are dependency warnings
    /// @return this result when unchanged, otherwise an equivalent result
    public ImportResult withDependency(boolean dependency) {
        return this.dependency == dependency
                ? this
                : new ImportResult(source, syntax, canonicalUrl, dependency);
    }
}
