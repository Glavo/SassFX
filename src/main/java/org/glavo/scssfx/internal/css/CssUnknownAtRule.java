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

/// A plain-CSS at-rule whose semantics are intentionally opaque.
@ApiStatus.Internal
@NotNullByDefault
public final class CssUnknownAtRule extends AbstractCssNode implements CssParentNode {
    /// Contains the at-rule name without the leading at sign.
    private final String name;

    /// Contains the evaluated raw prelude without surrounding whitespace.
    private final String value;

    /// Records whether the rule owns a block rather than a terminating semicolon.
    private final boolean hasBlock;

    /// Contains child statements for a block rule.
    private final ArrayList<CssNode> children = new ArrayList<>();

    /// Contains an unmodifiable live view of [#children].
    private final @UnmodifiableView List<CssNode> childrenView =
            Collections.unmodifiableList(children);

    /// Creates an empty opaque at-rule.
    ///
    /// @param name the at-rule name
    /// @param value the evaluated prelude
    /// @param hasBlock whether the rule owns a block
    /// @param span the source range
    public CssUnknownAtRule(
            String name,
            String value,
            boolean hasBlock,
            SourceSpan span
    ) {
        super(span);
        this.name = Objects.requireNonNull(name, "name");
        this.value = Objects.requireNonNull(value, "value");
        this.hasBlock = hasBlock;
    }

    /// Returns the at-rule name.
    ///
    /// @return the name without the leading at sign
    public String name() {
        return name;
    }

    /// Returns the evaluated prelude.
    ///
    /// @return the prelude without surrounding whitespace
    public String value() {
        return value;
    }

    /// Returns whether the rule owns a block.
    ///
    /// @return whether braces must be serialized
    public boolean hasBlock() {
        return hasBlock;
    }

    /// Returns whether this rule contributes no CSS.
    ///
    /// Opaque rules are always retained, including empty block rules.
    ///
    /// @return always {@code false}
    @Override
    public boolean isInvisible() {
        return false;
    }

    /// Returns the live unmodifiable child list.
    ///
    /// @return the children
    @Override
    public @UnmodifiableView List<CssNode> children() {
        return childrenView;
    }

    /// Appends one block child.
    ///
    /// @param child the child to append
    /// @throws IllegalStateException if this is a semicolon-terminated rule
    @Override
    public void addChild(CssNode child) {
        if (!hasBlock) {
            throw new IllegalStateException("a leaf at-rule cannot own children");
        }
        Objects.requireNonNull(child, "child");
        if (child instanceof AbstractCssNode node) {
            node.attach(this, children.size());
        }
        children.add(child);
    }

    /// Returns whether a later visible sibling exists.
    ///
    /// @return whether a following sibling is visible
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

    /// Compares the opaque header and block form.
    ///
    /// @param other the node to compare
    /// @return whether both headers and forms match
    @Override
    public boolean equalsIgnoringChildren(CssNode other) {
        return other instanceof CssUnknownAtRule rule
                && name.equals(rule.name)
                && value.equals(rule.value)
                && hasBlock == rule.hasBlock;
    }

    /// Returns an empty copy with the same opaque header.
    ///
    /// @return the empty copy
    @Override
    public CssUnknownAtRule copyWithoutChildren() {
        return new CssUnknownAtRule(name, value, hasBlock, span());
    }
}
