// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Objects;

/// Describes a stylesheet already available as a string.
///
/// @param content the complete stylesheet text
/// @param syntax the syntax used to parse the text
/// @param canonicalUrl the canonical URL, or {@code null} when unavailable
///
/// A relative canonical URL is accepted for compatibility, but compiling the
/// source reports the `compile-string-relative-url` deprecation.
@NotNullByDefault
public record SassStringSource(
        String content,
        Syntax syntax,
        @Nullable URI canonicalUrl
) implements SassSource {
    /// Creates a string source.
    public SassStringSource {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(syntax, "syntax");
    }

    /// Creates a string source without a stable canonical URL.
    ///
    /// @param content the complete stylesheet text
    /// @param syntax the syntax used to parse the text
    public SassStringSource(String content, Syntax syntax) {
        this(content, syntax, null);
    }
}
