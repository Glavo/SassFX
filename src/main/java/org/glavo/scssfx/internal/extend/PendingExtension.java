// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.extend;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.css.CssMediaQuery;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Records one `@extend` directive for end-of-module application.
///
/// @param extender     the resolved selectors of the extending style rule
/// @param target       the single-compound selectors being extended
/// @param optional     whether an unmatched target is allowed
/// @param mediaContext the active media-query list, or {@code null} outside media
/// @param span         the `@extend` source span
@ApiStatus.Internal
@NotNullByDefault
public record PendingExtension(
        SelectorList extender,
        SelectorList target,
        boolean optional,
        @Nullable @Unmodifiable List<CssMediaQuery> mediaContext,
        SourceSpan span
) {
    /// Creates one pending extension.
    public PendingExtension {
        Objects.requireNonNull(extender, "extender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(span, "span");
        if (mediaContext != null) {
            mediaContext = List.copyOf(mediaContext);
        }
    }
}
