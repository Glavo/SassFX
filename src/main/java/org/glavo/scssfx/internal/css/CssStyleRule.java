// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A plain-CSS style rule with an already-resolved selector string.
@ApiStatus.Internal
@NotNullByDefault
public final class CssStyleRule extends AbstractCssNode implements CssParentNode {
    /// Contains the evaluated selector text and its source span.
    private final CssValue<String> selector;

    /// Contains child statements in evaluation order.
    private final ArrayList<CssNode> children;

    /// Contains an unmodifiable live view of [#children].
    private final @UnmodifiableView List<CssNode> childrenView;

    /// Creates an empty style rule.
    ///
    /// @param selector the resolved selector
    /// @param span     the source range of the originating Sass style rule
    public CssStyleRule(CssValue<String> selector, SourceSpan span) {
        super(span);
        this.selector = Objects.requireNonNull(selector, "selector");
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }

    /// Returns the resolved selector.
    ///
    /// @return the selector value
    public CssValue<String> selector() {
        return selector;
    }

    /// Returns whether every child is invisible.
    ///
    /// Empty rules are invisible and omitted from expanded output.
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

    /// Appends a child declaration, comment, or nested structure retained under
    /// this rule.
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
        var parent = parent();
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

    /// Returns whether {@code other} is a style rule with the same selector text.
    ///
    /// @param other the node to compare
    /// @return whether the selectors match
    @Override
    public boolean equalsIgnoringChildren(CssNode other) {
        return other instanceof CssStyleRule rule
                && selector.value().equals(rule.selector.value());
    }

    /// Returns an empty style rule that shares this selector and span.
    ///
    /// @return the empty copy
    @Override
    public CssStyleRule copyWithoutChildren() {
        return new CssStyleRule(selector, span());
    }
}
