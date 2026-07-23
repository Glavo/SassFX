// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Emits one plain CSS `@import` rule without loading a Sass stylesheet.
///
/// @param url       the interpolated CSS URL, including quotes or `url()` syntax
/// @param modifiers the interpolated modifiers following the URL, or {@code null}
/// @param span      the source range occupied by the complete argument
@ApiStatus.Internal
@NotNullByDefault
public record StaticImport(
        Interpolation url,
        @Nullable Interpolation modifiers,
        SourceSpan span
) implements SassImport {
    /// Creates a static CSS import.
    public StaticImport {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(span, "span");
    }
}
