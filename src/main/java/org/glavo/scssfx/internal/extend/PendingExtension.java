// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.extend;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Records one `@extend` directive for end-of-module application.
///
/// @param extender the resolved selectors of the extending style rule
/// @param target   the single-compound selectors being extended
/// @param optional whether an unmatched target is allowed
/// @param span     the `@extend` source span
@ApiStatus.Internal
@NotNullByDefault
public record PendingExtension(
        SelectorList extender,
        SelectorList target,
        boolean optional,
        SourceSpan span
) {
    /// Creates one pending extension.
    public PendingExtension {
        Objects.requireNonNull(extender, "extender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(span, "span");
    }
}
