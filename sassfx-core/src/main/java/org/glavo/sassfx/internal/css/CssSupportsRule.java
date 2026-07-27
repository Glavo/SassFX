// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A plain-CSS {@code @supports} rule with an evaluated CSS condition.
///
/// The condition is retained as CSS text after Sass interpolation. It is not
/// reparsed so this IR remains forward-compatible with CSS support-test syntax
/// that is not otherwise relevant to Sass evaluation.
@ApiStatus.Internal
@NotNullByDefault
public final class CssSupportsRule extends AbstractCssNode implements CssParentNode {
    /// Contains the nonblank, outer-whitespace-trimmed CSS condition.
    private final String condition;

    /// Contains child statements in evaluation order.
    private final ArrayList<CssNode> children;

    /// Contains an unmodifiable live view of [#children].
    private final @UnmodifiableView List<CssNode> childrenView;

    /// Creates an empty supports rule.
    ///
    /// @param condition the nonblank evaluated CSS supports condition
    /// @param span      the source range of the originating Sass at-rule
    /// @throws IllegalArgumentException if {@code condition} is blank
    public CssSupportsRule(String condition, SourceSpan span) {
        super(span);
        this.condition = Objects.requireNonNull(condition, "condition").strip();
        if (this.condition.isEmpty()) {
            throw new IllegalArgumentException("supports condition must not be blank");
        }
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }

    /// Returns the evaluated CSS supports condition.
    ///
    /// @return the nonblank condition without outer whitespace
    public String condition() {
        return condition;
    }

    /// Returns whether every child is invisible.
    ///
    /// Empty supports rules are invisible and omitted from textual CSS output.
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

    /// Returns whether {@code other} has the same evaluated supports condition.
    ///
    /// @param other the node to compare
    /// @return whether both conditions are equal
    @Override
    public boolean equalsIgnoringChildren(CssNode other) {
        return other instanceof CssSupportsRule rule && condition.equals(rule.condition);
    }

    /// Returns an empty supports rule that shares this condition and source span.
    ///
    /// @return the empty copy
    @Override
    public CssSupportsRule copyWithoutChildren() {
        return new CssSupportsRule(condition, span());
    }
}
