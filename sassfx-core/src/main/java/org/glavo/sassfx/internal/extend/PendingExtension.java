// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.extend;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.ast.selector.SelectorList;
import org.glavo.sassfx.internal.css.CssMediaQuery;
import org.glavo.sassfx.internal.css.CssStyleRule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/// Records one `@extend` directive for end-of-module application.
///
/// @param extender          the resolved selectors of the extending style rule
/// @param target            the single-compound selectors being extended
/// @param optional          whether an unmatched target is allowed
/// @param mediaContext      the active media-query list, or {@code null} outside media
/// @param originUrl         the canonical URL of the module that declared the extend,
///                          or {@code null} for the anonymous root stylesheet
/// @param span              the `@extend` source span
/// @param importGeneration  zero for ordinary module-graph extensions; a positive id
///                          shared with CSS rules re-emitted for one import-path
///                          {@code @use} copy so extends stay isolated per copy
/// @param crossGeneration   when {@code true} and {@code importGeneration > 0}, also
///                          rewrite generation-0 module-graph originals when the
///                          extender can reach the rule's defining module (dart-sass:
///                          extends written in an {@code @import}-ed file that
///                          {@code @use}s the target apply to both the original and
///                          the import CSS copy). Re-stamped module-graph extensions
///                          from {@code injectModuleCssIsolated} keep this {@code false}
/// @param fromLegacyImport  whether the extend was written while evaluating a legacy
///                          {@code @import} body (used to order import-body extends
///                          before pure module-graph extends for selector interleaving)
/// @param extenderRule      the live style rule that owns the extender, or
///                          {@code null} when the extension belongs to a detached
///                          CSS copy and [#extender] is the authoritative snapshot
@ApiStatus.Internal
@NotNullByDefault
public record PendingExtension(
        SelectorList extender,
        SelectorList target,
        boolean optional,
        @Nullable @Unmodifiable List<CssMediaQuery> mediaContext,
        @Nullable URI originUrl,
        SourceSpan span,
        int importGeneration,
        boolean crossGeneration,
        boolean fromLegacyImport,
        @Nullable CssStyleRule extenderRule
) {
    /// Creates one pending extension with an explicit import generation.
    ///
    /// @param extender         the resolved selectors of the extending style rule
    /// @param target           the single-compound selectors being extended
    /// @param optional         whether an unmatched target is allowed
    /// @param mediaContext     the active media-query list, or {@code null} outside media
    /// @param originUrl        the canonical URL of the declaring module, or
    ///                         {@code null} for an anonymous root
    /// @param span             the `@extend` source span
    /// @param importGeneration zero for module-graph extensions, or the positive
    ///                         identity of one isolated legacy-import copy
    public PendingExtension(
            SelectorList extender,
            SelectorList target,
            boolean optional,
            @Nullable List<CssMediaQuery> mediaContext,
            @Nullable URI originUrl,
            SourceSpan span,
            int importGeneration
    ) {
        this(
                extender,
                target,
                optional,
                mediaContext,
                originUrl,
                span,
                importGeneration,
                false,
                false,
                null
        );
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

    /// Returns the current selector of the extending rule.
    ///
    /// Incremental extension propagation may rewrite the defining rule before a
    /// later directive is registered. Detached copies retain the immutable
    /// selector snapshot supplied at construction.
    ///
    /// @return the current extender selector
    public SelectorList resolvedExtender() {
        return extenderRule == null ? extender : extenderRule.selector().value();
    }
}
