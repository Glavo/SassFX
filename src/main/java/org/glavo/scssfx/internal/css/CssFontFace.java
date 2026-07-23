// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A plain-CSS {@code @font-face} rule with evaluated descriptor declarations.
///
/// The node is valid only as a stylesheet child. Its children are ordered
/// declarations and comments retained from the Sass body.
@ApiStatus.Internal
@NotNullByDefault
public final class CssFontFace extends AbstractCssNode implements CssParentNode {
    /// Contains child statements in evaluation order.
    private final ArrayList<CssNode> children;

    /// Contains an unmodifiable live view of [#children].
    private final @UnmodifiableView List<CssNode> childrenView;

    /// Creates an empty font-face rule.
    ///
    /// @param span the source range of the originating Sass at-rule
    public CssFontFace(SourceSpan span) {
        super(span);
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }

    /// Returns whether every descriptor and comment is invisible.
    ///
    /// @return whether this rule may be omitted from CSS output
    @Override
    public boolean isInvisible() {
        return children.stream().allMatch(CssNode::isInvisible);
    }

    /// Returns the live unmodifiable child list.
    ///
    /// @return the descriptor statements in evaluation order
    @Override
    public @UnmodifiableView List<CssNode> children() {
        return childrenView;
    }

    /// Appends an evaluated descriptor declaration or comment.
    ///
    /// @param child the child to append
    @Override
    public void addChild(CssNode child) {
        Objects.requireNonNull(child, "child");
        if (child instanceof AbstractCssNode node) {
            node.attach(this, children.size());
        }
        children.add(child);
    }

    /// Returns whether a later stylesheet child is visible.
    ///
    /// @return whether a visible following sibling exists
    @Override
    public boolean hasFollowingSibling() {
        @Nullable CssParentNode parent = parent();
        if (parent == null) {
            return false;
        }
        var siblings = parent.children();
        for (var index = indexInParent() + 1; index < siblings.size(); index++) {
            if (!siblings.get(index).isInvisible()) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether {@code other} is also a font-face rule.
    ///
    /// @param other the node to compare
    /// @return whether both nodes are font-face rules
    @Override
    public boolean equalsIgnoringChildren(CssNode other) {
        return other instanceof CssFontFace;
    }

    /// Returns an empty font-face rule that shares this source span.
    ///
    /// @return the empty copy
    @Override
    public CssFontFace copyWithoutChildren() {
        return new CssFontFace(span());
    }
}
