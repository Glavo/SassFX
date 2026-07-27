// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/// Describes a root stylesheet supplied to a compilation.
@NotNullByDefault
public sealed interface SassSource permits SassFileSource, SassStringSource {
    /// Creates a file source whose syntax is inferred from its file extension.
    ///
    /// @param path the stylesheet path
    /// @return the file source
    /// @throws IllegalArgumentException if the final file extension is not supported
    static SassFileSource fromFile(Path path) {
        return new SassFileSource(path);
    }

    /// Creates a file source with an explicitly selected syntax.
    ///
    /// @param path the stylesheet path
    /// @param syntax the syntax used to parse the file
    /// @return the file source
    static SassFileSource fromFile(Path path, Syntax syntax) {
        return new SassFileSource(path, syntax);
    }

    /// Creates a string source without a stable canonical URL.
    ///
    /// @param content the complete stylesheet text
    /// @param syntax the syntax used to parse the text
    /// @return the string source
    static SassStringSource fromString(String content, Syntax syntax) {
        return new SassStringSource(content, syntax, null);
    }

    /// Creates a string source with an optional canonical URL.
    ///
    /// A relative URL is retained but reports the
    /// `compile-string-relative-url` deprecation when compiled.
    ///
    /// @param content the complete stylesheet text
    /// @param syntax the syntax used to parse the text
    /// @param canonicalUrl the canonical URL, or {@code null} when unavailable
    /// @return the string source
    static SassStringSource fromString(
            String content,
            Syntax syntax,
            @Nullable URI canonicalUrl
    ) {
        return new SassStringSource(content, syntax, canonicalUrl);
    }
}
