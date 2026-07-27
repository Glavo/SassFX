// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A statement in the internal plain-CSS intermediate representation.
///
/// CSS IR is the sole internal backend input. It is not a supported public
/// extension point.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface CssNode permits CssParentNode, CssDeclaration, CssComment, CssImport {
    /// Returns the source range associated with this node.
    ///
    /// @return the node span
    SourceSpan span();

    /// Returns the parent that currently owns this node, or {@code null} for the
    /// stylesheet root and for detached copies.
    ///
    /// @return the parent node, or {@code null}
    @Nullable CssParentNode parent();

    /// Returns whether a blank line should follow this node before the next
    /// top-level sibling in expanded output.
    ///
    /// @return whether this node ends a logical group
    boolean isGroupEnd();

    /// Marks whether this node ends a logical top-level group.
    ///
    /// @param groupEnd whether a blank line should follow this node
    void setGroupEnd(boolean groupEnd);

    /// Returns whether this node contributes no visible expanded CSS.
    ///
    /// @return whether the node may be omitted from serialization
    boolean isInvisible();
}
