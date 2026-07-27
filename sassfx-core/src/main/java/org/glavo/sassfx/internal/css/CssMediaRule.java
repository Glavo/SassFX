// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A plain-CSS {@code @media} rule with evaluated media queries.
///
/// Its child statements preserve the CSS ordering produced after Sass nesting
/// and media-query bubbling have been resolved.
@ApiStatus.Internal
@NotNullByDefault
public final class CssMediaRule extends AbstractCssNode implements CssParentNode {
    /// Contains the immutable evaluated query list.
    private final @Unmodifiable List<CssMediaQuery> queries;

    /// Contains child statements in evaluation order.
    private final ArrayList<CssNode> children;

    /// Contains an unmodifiable live view of [#children].
    private final @UnmodifiableView List<CssNode> childrenView;

    /// Creates an empty media rule.
    ///
    /// @param queries the nonempty evaluated media-query list
    /// @param span    the source range of the originating Sass at-rule
    /// @throws IllegalArgumentException if {@code queries} is empty
    public CssMediaRule(List<CssMediaQuery> queries, SourceSpan span) {
        super(span);
        this.queries = List.copyOf(queries);
        if (this.queries.isEmpty()) {
            throw new IllegalArgumentException("media query list must not be empty");
        }
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }

    /// Returns the evaluated media-query list.
    ///
    /// @return an immutable query list in source order
    public @Unmodifiable List<CssMediaQuery> queries() {
        return queries;
    }

    /// Returns whether every child is invisible.
    ///
    /// Empty media rules are invisible and omitted from textual CSS output.
    ///
    /// @return whether this rule may be omitted
    @Override
    public boolean isInvisible() {
        return children.stream().allMatch(CssNode::isInvisible);
    }

    /// Returns the live unmodifiable child list.
    ///
    /// @return the child statements
    @Override
    public @UnmodifiableView List<CssNode> children() {
        return childrenView;
    }

    /// Appends an evaluated child statement.
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

    /// Returns whether a later sibling under the same parent is visible.
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

    /// Returns whether {@code other} has the same media-query list.
    ///
    /// @param other the node to compare
    /// @return whether both query lists are equal
    @Override
    public boolean equalsIgnoringChildren(CssNode other) {
        return other instanceof CssMediaRule rule && queries.equals(rule.queries);
    }

    /// Returns an empty media rule that shares this query list and source span.
    ///
    /// @return the empty copy
    @Override
    public CssMediaRule copyWithoutChildren() {
        return new CssMediaRule(queries, span());
    }
}
