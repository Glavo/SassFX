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

/// The root plain-CSS stylesheet produced by evaluation.
@ApiStatus.Internal
@NotNullByDefault
public final class CssStylesheet implements CssParentNode {
    /// Contains the stylesheet source span.
    private final SourceSpan span;

    /// Contains child statements in evaluation order.
    private final ArrayList<CssNode> children;

    /// Contains an unmodifiable live view of [#children].
    private final @UnmodifiableView List<CssNode> childrenView;

    /// Creates an empty stylesheet root.
    ///
    /// @param span the source range covering the evaluated stylesheet
    public CssStylesheet(SourceSpan span) {
        this.span = Objects.requireNonNull(span, "span");
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }

    /// Returns the stylesheet source span.
    ///
    /// @return the root span
    @Override
    public SourceSpan span() {
        return span;
    }

    /// Returns {@code null} because the stylesheet is the CSS IR root.
    ///
    /// @return {@code null}
    @Override
    public @Nullable CssParentNode parent() {
        return null;
    }

    /// Returns false because the root never ends a sibling group.
    ///
    /// @return {@code false}
    @Override
    public boolean isGroupEnd() {
        return false;
    }

    /// Ignores group-end updates because the root has no siblings.
    ///
    /// @param groupEnd ignored
    @Override
    public void setGroupEnd(boolean groupEnd) {
    }

    /// Returns false because an empty root still serializes to empty CSS.
    ///
    /// @return {@code false}
    @Override
    public boolean isInvisible() {
        return false;
    }

    /// Returns the live unmodifiable child list.
    ///
    /// @return the child statements
    @Override
    public @UnmodifiableView List<CssNode> children() {
        return childrenView;
    }

    /// Appends a top-level child.
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

    /// Inserts a top-level import after the initial imports and comments.
    ///
    /// Imports evaluated after ordinary CSS are moved into the CSS import
    /// prefix while preserving their relative order.
    ///
    /// @param child the import to insert
    public void addImport(CssImport child) {
        Objects.requireNonNull(child, "child");
        var index = 0;
        while (index < children.size()
                && (children.get(index) instanceof CssImport
                || children.get(index) instanceof CssComment)) {
            index++;
        }
        children.add(index, child);
        for (var current = index; current < children.size(); current++) {
            if (children.get(current) instanceof AbstractCssNode node) {
                node.attach(this, current);
            }
        }
    }

    /// Returns false because the root has no siblings.
    ///
    /// @return {@code false}
    @Override
    public boolean hasFollowingSibling() {
        return false;
    }

    /// Returns whether {@code other} is also a stylesheet root.
    ///
    /// @param other the node to compare
    /// @return whether both nodes are stylesheets
    @Override
    public boolean equalsIgnoringChildren(CssNode other) {
        return other instanceof CssStylesheet;
    }

    /// Returns an empty stylesheet that shares this root span.
    ///
    /// @return the empty copy
    @Override
    public CssStylesheet copyWithoutChildren() {
        return new CssStylesheet(span);
    }
}
