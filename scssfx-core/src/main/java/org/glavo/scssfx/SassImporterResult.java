// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Objects;

/// Contains source text returned by a [SassImporter].
///
/// @param contents the complete stylesheet text
/// @param syntax the syntax used to parse the stylesheet
/// @param sourceMapUrl the absolute source URL recorded in generated source
///                     maps, or {@code null} to generate a UTF-8 {@code data:} URL
@NotNullByDefault
public record SassImporterResult(
        String contents,
        Syntax syntax,
        @Nullable URI sourceMapUrl
) {
    /// Creates a result whose source-map URL is generated from its contents.
    ///
    /// @param contents the complete stylesheet text
    /// @param syntax the syntax used to parse the stylesheet
    public SassImporterResult(String contents, Syntax syntax) {
        this(contents, syntax, null);
    }

    /// Validates and stores an importer result.
    public SassImporterResult {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(syntax, "syntax");
        if (sourceMapUrl != null && !sourceMapUrl.isAbsolute()) {
            throw new IllegalArgumentException("sourceMapUrl must be absolute");
        }
    }
}
