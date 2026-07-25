// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.extend;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.css.CssMediaQuery;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/// Records one `@extend` directive for end-of-module application.
///
/// @param extender         the resolved selectors of the extending style rule
/// @param target           the single-compound selectors being extended
/// @param optional         whether an unmatched target is allowed
/// @param mediaContext     the active media-query list, or {@code null} outside media
/// @param originUrl        the canonical URL of the module that declared the extend,
///                         or {@code null} for the anonymous root stylesheet
/// @param span             the `@extend` source span
/// @param importGeneration zero for ordinary module-graph extensions; a positive id
///                         shared with CSS rules re-emitted for one import-path
///                         {@code @use} copy so extends stay isolated per copy
@ApiStatus.Internal
@NotNullByDefault
public record PendingExtension(
        SelectorList extender,
        SelectorList target,
        boolean optional,
        @Nullable @Unmodifiable List<CssMediaQuery> mediaContext,
        @Nullable URI originUrl,
        SourceSpan span,
        int importGeneration
) {
    /// Creates one pending extension with import generation {@code 0}.
    public PendingExtension(
            SelectorList extender,
            SelectorList target,
            boolean optional,
            @Nullable List<CssMediaQuery> mediaContext,
            @Nullable URI originUrl,
            SourceSpan span
    ) {
        this(extender, target, optional, mediaContext, originUrl, span, 0);
    }

    /// Creates one pending extension.
    public PendingExtension {
        Objects.requireNonNull(extender, "extender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(span, "span");
        if (mediaContext != null) {
            mediaContext = List.copyOf(mediaContext);
        }
        if (importGeneration < 0) {
            throw new IllegalArgumentException("importGeneration must be non-negative");
        }
    }
}
