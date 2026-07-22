// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Shared parent-index bookkeeping for non-root CSS IR nodes.
@ApiStatus.Internal
@NotNullByDefault
abstract class AbstractCssNode {
    /// Contains the source range associated with this node.
    private final SourceSpan span;

    /// Contains the current parent, or {@code null} when detached.
    private @Nullable CssParentNode parent;

    /// Contains this node's index in the parent child list, or {@code -1}.
    private int indexInParent = -1;

    /// Records whether a blank line should follow this node in expanded output.
    private boolean groupEnd;

    /// Creates a node with a fixed source span.
    ///
    /// @param span the source range associated with this node
    AbstractCssNode(SourceSpan span) {
        this.span = Objects.requireNonNull(span, "span");
    }

    /// Returns the source range associated with this node.
    ///
    /// @return the node span
    public final SourceSpan span() {
        return span;
    }

    /// Returns the current parent, or {@code null} when detached.
    ///
    /// @return the parent node, or {@code null}
    public final @Nullable CssParentNode parent() {
        return parent;
    }

    /// Returns whether this node ends a logical top-level group.
    ///
    /// @return whether a blank line should follow this node
    public final boolean isGroupEnd() {
        return groupEnd;
    }

    /// Marks whether this node ends a logical top-level group.
    ///
    /// @param groupEnd whether a blank line should follow this node
    public final void setGroupEnd(boolean groupEnd) {
        this.groupEnd = groupEnd;
    }

    /// Records ownership by a parent at the given child index.
    ///
    /// @param parent the owning parent
    /// @param index  the index within the parent child list
    final void attach(CssParentNode parent, int index) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.indexInParent = index;
    }

    /// Returns the index within the parent child list.
    ///
    /// @return the child index, or {@code -1} when detached
    final int indexInParent() {
        return indexInParent;
    }
}
