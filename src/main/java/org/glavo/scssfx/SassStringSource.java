// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Objects;

/// Describes a stylesheet already available as a string.
///
/// @param content the complete stylesheet text
/// @param syntax the syntax used to parse the text
/// @param canonicalUrl the absolute canonical URL, or {@code null} when unavailable
@NotNullByDefault
public record SassStringSource(
        String content,
        Syntax syntax,
        @Nullable URI canonicalUrl
) implements SassSource {
    /// Creates a string source after validating its canonical URL.
    ///
    /// @throws IllegalArgumentException if {@code canonicalUrl} is relative
    public SassStringSource {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(syntax, "syntax");
        if (canonicalUrl != null && !canonicalUrl.isAbsolute()) {
            throw new IllegalArgumentException("canonicalUrl must be absolute");
        }
    }

    /// Creates a string source without a stable canonical URL.
    ///
    /// @param content the complete stylesheet text
    /// @param syntax the syntax used to parse the text
    public SassStringSource(String content, Syntax syntax) {
        this(content, syntax, null);
    }
}
