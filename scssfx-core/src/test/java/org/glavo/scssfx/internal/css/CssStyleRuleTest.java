// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Verifies selector ownership across structural and independent style-rule copies.
@NotNullByDefault
final class CssStyleRuleTest {
    /// Shares selector updates with childless structural copies.
    @Test
    void sharesSelectorReferenceWithStructuralCopy() {
        var original = rule(".before");
        var copy = original.copyWithoutChildren();
        var replacement = value(".after");

        assertSame(original.selectorReference(), copy.selectorReference());

        copy.setSelector(replacement);

        assertSame(replacement, original.selector());
        assertSame(replacement, copy.selector());
    }

    /// Keeps separately constructed rules isolated even when initialized alike.
    @Test
    void keepsIndependentConstructionIsolated() {
        var initial = value(".before");
        var first = new CssStyleRule(initial, initial.span());
        var second = new CssStyleRule(initial, initial.span());

        assertNotSame(first.selectorReference(), second.selectorReference());

        first.setSelector(value(".after"));

        assertEquals(".after", first.selector().value().toCssString());
        assertEquals(".before", second.selector().value().toCssString());
    }

    /// Creates one style rule for selector text.
    ///
    /// @param text the selector source
    /// @return the style rule
    private static CssStyleRule rule(String text) {
        var selector = value(text);
        return new CssStyleRule(selector, selector.span());
    }

    /// Parses one selector and associates it with its synthetic source span.
    ///
    /// @param text the selector source
    /// @return the spanned selector value
    private static CssValue<SelectorList> value(String text) {
        var span = span(text);
        return new CssValue<>(SelectorList.parse(text, span), span);
    }

    /// Creates a synthetic source span for selector text.
    ///
    /// @param text the selector source
    /// @return a span covering the selector
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
