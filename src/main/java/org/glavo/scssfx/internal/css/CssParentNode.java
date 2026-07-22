// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

/// A CSS IR node that may own ordered child statements.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface CssParentNode extends CssNode permits CssStylesheet, CssStyleRule {
    /// Returns an unmodifiable live view of this node's children.
    ///
    /// @return the child statements in evaluation order
    @UnmodifiableView List<CssNode> children();

    /// Appends a child and records this node as its parent.
    ///
    /// @param child the child to append
    void addChild(CssNode child);

    /// Returns whether this parent has a later sibling that is not invisible.
    ///
    /// @return whether a visible following sibling exists
    boolean hasFollowingSibling();

    /// Returns whether this parent equals {@code other} when children are ignored.
    ///
    /// @param other the node to compare
    /// @return whether the nodes match ignoring children
    boolean equalsIgnoringChildren(CssNode other);

    /// Returns a childless copy that shares immutable identity fields.
    ///
    /// @return the empty copy
    CssParentNode copyWithoutChildren();
}
