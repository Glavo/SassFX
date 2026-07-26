// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// A plain-CSS style rule with an already-resolved selector list.
@ApiStatus.Internal
@NotNullByDefault
public final class CssStyleRule extends AbstractCssNode implements CssParentNode {
    /// Contains the evaluated selector list and its source span.
    ///
    /// Structural copies share this reference so later `@extend` updates remain
    /// visible through every copy.
    private final CssSelectorReference selectorReference;

    /// Records whether this rule originated from plain CSS rather than Sass nesting.
    private final boolean fromPlainCss;

    /// Media queries enclosing this rule when it was defined in Sass source.
    ///
    /// Nested {@code @media} that later bubbles this rule under a CSS media
    /// parent does not change this value, so {@code @extend} can still target
    /// placeholders whose declarations live inside nested media.
    private final @Nullable @Unmodifiable List<CssMediaQuery> definingMediaContext;

    /// Contains child statements in evaluation order.
    private final ArrayList<CssNode> children;

    /// Contains an unmodifiable live view of [#children].
    private final @UnmodifiableView List<CssNode> childrenView;

    /// Creates an empty style rule.
    ///
    /// @param selector the resolved selector list
    /// @param span     the source range of the originating Sass style rule
    public CssStyleRule(CssValue<SelectorList> selector, SourceSpan span) {
        this(selector, span, false, null);
    }

    /// Creates an empty style rule with an explicit plain-CSS origin flag.
    ///
    /// @param selector      the resolved selector list
    /// @param span          the source range of the originating style rule
    /// @param fromPlainCss  whether the rule came from plain CSS nesting
    public CssStyleRule(CssValue<SelectorList> selector, SourceSpan span, boolean fromPlainCss) {
        this(selector, span, fromPlainCss, null);
    }

    /// Creates an empty style rule with plain-CSS and defining-media metadata.
    ///
    /// @param selector              the resolved selector list
    /// @param span                  the source range of the originating style rule
    /// @param fromPlainCss          whether the rule came from plain CSS nesting
    /// @param definingMediaContext  media queries active when the rule was defined,
    ///                              or {@code null} outside {@code @media}
    public CssStyleRule(
            CssValue<SelectorList> selector,
            SourceSpan span,
            boolean fromPlainCss,
            @Nullable List<CssMediaQuery> definingMediaContext
    ) {
        this(
                new CssSelectorReference(Objects.requireNonNull(selector, "selector")),
                span,
                fromPlainCss,
                definingMediaContext
        );
    }

    /// Creates an empty style rule backed by a shared selector reference.
    ///
    /// Rules created with the same reference observe each other's selector
    /// updates. Call [CssSelectorReference#copy()] before construction when an
    /// independent deep copy is required.
    ///
    /// @param selectorReference    the shared selector reference
    /// @param span                 the source range of the originating style rule
    /// @param fromPlainCss         whether the rule came from plain CSS nesting
    /// @param definingMediaContext media queries active when the rule was defined,
    ///                             or {@code null} outside {@code @media}
    public CssStyleRule(
            CssSelectorReference selectorReference,
            SourceSpan span,
            boolean fromPlainCss,
            @Nullable List<CssMediaQuery> definingMediaContext
    ) {
        super(span);
        this.selectorReference = Objects.requireNonNull(
                selectorReference,
                "selectorReference"
        );
        this.fromPlainCss = fromPlainCss;
        this.definingMediaContext = definingMediaContext == null
                ? null
                : List.copyOf(definingMediaContext);
        this.children = new ArrayList<>();
        this.childrenView = Collections.unmodifiableList(children);
    }

    /// Returns the resolved selector list.
    ///
    /// @return the selector value
    public CssValue<SelectorList> selector() {
        return selectorReference.value();
    }

    /// Returns the shared reference that owns this rule's selector.
    ///
    /// Structural copies return the same reference. Deep-copy code must use
    /// [CssSelectorReference#copy()] when the copied CSS tree requires isolated
    /// extension state.
    ///
    /// @return the shared selector reference
    public CssSelectorReference selectorReference() {
        return selectorReference;
    }

    /// Replaces the resolved selector list after extension.
    ///
    /// @param selector the extended selector list
    public void setSelector(CssValue<SelectorList> selector) {
        selectorReference.set(selector);
    }

    /// Returns whether this rule originated from plain CSS.
    ///
    /// Plain-CSS rules keep native nesting and do not participate in Sass
    /// selector flattening or style-rule bubbling.
    ///
    /// @return whether the rule is plain CSS
    public boolean fromPlainCss() {
        return fromPlainCss;
    }

    /// Returns the media context active when this rule was defined.
    ///
    /// @return the defining media queries, or {@code null} outside media
    public @Nullable @Unmodifiable List<CssMediaQuery> definingMediaContext() {
        return definingMediaContext;
    }

    /// Returns whether this rule contributes no visible CSS.
    ///
    /// Rules whose selectors are entirely CSS-invisible (placeholders and
    /// invisible selector-taking pseudos) or serialize to an empty non-inspect
    /// CSS string (bogus leading combinators such as {@code :is(> a)}) are
    /// omitted, as are rules whose remaining children are all invisible.
    ///
    /// @return whether this rule may be omitted
    @Override
    public boolean isInvisible() {
        if (children.stream().allMatch(CssNode::isInvisible)) {
            return true;
        }
        if (selector().value().isInvisible()) {
            return true;
        }
        // Bogus complexes are dropped from non-inspect serialization; when every
        // complex is bogus the selector CSS is empty and the rule is omitted.
        return selector().value().toCssString(false).isEmpty();
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

    /// Returns whether {@code other} is a style rule with the same selector CSS.
    ///
    /// @param other the node to compare
    /// @return whether the selectors match
    @Override
    public boolean equalsIgnoringChildren(CssNode other) {
        return other instanceof CssStyleRule rule
                && selector().value().toCssString().equals(rule.selector().value().toCssString());
    }

    /// Returns an empty style rule that shares this selector, span, and media context.
    ///
    /// @return the empty copy
    @Override
    public CssStyleRule copyWithoutChildren() {
        return new CssStyleRule(
                selectorReference,
                span(),
                fromPlainCss,
                definingMediaContext
        );
    }
}
