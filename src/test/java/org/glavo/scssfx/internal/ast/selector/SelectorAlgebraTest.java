// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies selector algebra for the structurally modeled selector subset.
@NotNullByDefault
final class SelectorAlgebraTest {
    /// Verifies compound intersections, selector-list products, descendant
    /// weaving, and incompatible selectors.
    @Test
    void unifiesSelectors() {
        assertEquals(".foo.bar", unify(".foo", ".bar"));
        assertEquals(
                ".foo.selected, .foo.focused, .bar.selected, .bar.focused",
                unify(".foo, .bar", ".selected, .focused")
        );
        assertEquals(".foo.bar", unify(".foo, *.foo", ".bar"));
        assertEquals(
                ".a .c .b, .c .a .b",
                unify(".a .b", ".c .b")
        );
        assertEquals(".a > .b", unify(".a > .b", ".a .b"));
        assertEquals("[data=x]", unify("[data=x]", "[data=\"x\"]"));
        assertEquals("[data=x]", unify("[data=\\78]", "[data=x]"));
        assertEquals("[data=x][state=y]", unify("[data=x]", "[state=y]"));
        assertEquals("[data=x][data=y]", unify("[data=x]", "[data=y]"));
        assertEquals(".button:hover", unify(":hover", ".button"));
        assertEquals(".button:hover:focus", unify(".button:hover", ".button:focus"));
        assertEquals(".button:lang(en)", unify(":lang(en)", ".button"));
        assertEquals("%token.button", unify("%token", ".button"));
        assertNull(SelectorAlgebra.unify(parse("a"), parse("b")));
        assertNull(SelectorAlgebra.unify(parse("#one"), parse("#two")));
    }

    /// Verifies attributes compare only structurally modeled constraints.
    @Test
    void comparesAttributeSelectorsStructurally() {
        assertEquals(
                "[data~=x i]",
                unify("[\\64 ata~=x i]", "[data~=\"x\" i]")
        );
        assertEquals("[data=x i][data=x s]", unify("[data=x i]", "[data=x s]"));
        assertEquals("[data=x][data=x i]", unify("[data=x]", "[data=x i]"));
        assertEquals("[data^=x][data$=x]", unify("[data^=x]", "[data$=x]"));
        assertTrue(isSuperselector("[data=x i]", ".item[data=\"x\" i]"));
        assertFalse(isSuperselector("[data=x i]", "[data=x s]"));
        assertEquals(
                ".item[data^=x], .item.active",
                extend(".item[data^=x]", "[data^=\"x\"]", ".active")
        );
        assertEquals(
                ".item[data^=x]",
                replace(".item[data^=x]", "[data$=x]", ".active")
        );
        assertThrows(SassValueException.class, () -> parse("[data=x invalid]"));
    }

    /// Verifies namespace-aware type and universal selector intersections.
    @Test
    void unifiesNamespaceAwareElementSelectors() {
        assertEquals("svg|a", unify("*|a", "svg|a"));
        assertEquals("svg|a", unify("svg|a", "*|a"));
        assertEquals("a", unify("*|a", "a"));
        assertEquals("|a", unify("|*", "|a"));
        assertEquals("svg|a", unify("svg|*", "svg|a"));
        assertEquals(".item", unify("*", ".item"));
        assertEquals(".item", unify("*|*", ".item"));
        assertEquals("svg|*.item", unify("svg|*", ".item"));
        assertEquals("svg|a.item, |a.item", unify("svg|a, |a", ".item"));
        assertNull(SelectorAlgebra.unify(parse("svg|a"), parse("|a")));
        assertNull(SelectorAlgebra.unify(parse("svg|*"), parse("*")));
    }

    /// Verifies compound, list, descendant, child, and sibling superselector
    /// relationships.
    @Test
    void comparesSuperselectors() {
        assertTrue(isSuperselector(".foo", ".foo.bar"));
        assertFalse(isSuperselector(".foo.bar", ".foo"));
        assertTrue(isSuperselector("*", ".foo"));
        assertTrue(isSuperselector(".a .b", ".a > .b"));
        assertTrue(isSuperselector(".a ~ .b", ".a + .b"));
        assertFalse(isSuperselector(".a .b", ".a + .b"));
        assertFalse(isSuperselector(".a", ".a, .b"));
        assertTrue(isSuperselector(".a, .b", ".a"));
        assertTrue(isSuperselector("[data=x]", ".item[data=\"x\"]"));
        assertFalse(isSuperselector("[data]", "[data=x]"));
        assertFalse(isSuperselector("[*|href]", "[svg|href]"));
        assertTrue(isSuperselector(":hover", ".button:hover"));
        assertFalse(isSuperselector(".button:hover", ":hover"));
        assertTrue(isSuperselector(":lang(en)", ".button:lang(en)"));
        assertFalse(isSuperselector(":lang(en)", ":lang(fr)"));
        assertTrue(isSuperselector("%token", ".button%token"));
    }

    /// Verifies namespace-aware type and universal superselector relationships.
    @Test
    void comparesNamespaceAwareElementSelectors() {
        assertTrue(isSuperselector("*|a", "svg|a"));
        assertFalse(isSuperselector("svg|a", "*|a"));
        assertFalse(isSuperselector("a", "svg|a"));
        assertTrue(isSuperselector("svg|*", "svg|a"));
        assertFalse(isSuperselector("svg|*", ".item"));
        assertTrue(isSuperselector("*|*", ".item"));
        assertFalse(isSuperselector("|*", ".item"));
        assertTrue(isSuperselector("*|*", "svg|*"));
        assertFalse(isSuperselector("svg|*", "*|*"));
        assertFalse(isSuperselector("*", "svg|*"));
    }

    /// Verifies structured pseudo and pseudo-element intersections.
    @Test
    void unifiesStructuredPseudoSelectorsAndPseudoElements() {
        assertEquals(".a:is(.b)", unify(":is(.b)", ".a"));
        assertEquals(":is(.a)", unify(":is(.a)", ":is(.a)"));
        assertEquals(".button::before", unify("::before", ".button"));
        assertEquals(":hover::before", unify("::before", ":hover"));
        assertEquals(":before", unify(":before", "::before"));
        assertNull(SelectorAlgebra.unify(parse("::before"), parse("::after")));
    }

    /// Verifies structured pseudo and pseudo-element superselector relationships.
    @Test
    void comparesStructuredPseudoSelectorsAndPseudoElements() {
        assertTrue(isSuperselector(".a", ":is(.a)"));
        assertFalse(isSuperselector(".a", ":is(.a, .b)"));
        assertTrue(isSuperselector(":is(.a, .b)", ":is(.a)"));
        assertFalse(isSuperselector(":is(.a)", ":is(.a, .b)"));
        assertTrue(isSuperselector(":has(.a)", ":has(.a.b)"));
        assertTrue(isSuperselector("::slotted(.a)", "::slotted(.a.b)"));
        assertTrue(isSuperselector(".a::before", ".a.b::before"));
        assertFalse(isSuperselector(".a::before", ".a::after"));
    }

    /// Verifies recursive pseudo argument and pseudo-element extension behavior.
    @Test
    void extendsAndReplacesStructuredPseudoSelectorsAndPseudoElements() {
        assertEquals(":is(.a, .b)", extend(":is(.a)", ".a", ".b"));
        assertEquals(":is(.b)", replace(":is(.a)", ".a", ".b"));
        assertEquals("::slotted(.a, .b)", extend("::slotted(.a)", ".a", ".b"));
        assertEquals("::slotted(.b)", replace("::slotted(.a)", ".a", ".b"));
        assertEquals(
                ".button::before, .button.generated",
                extend(".button::before", "::before", ".generated")
        );
        assertEquals(
                ".button.generated",
                replace(".button::before", "::before", ".generated")
        );
        assertEquals(
                ".button:is(.a), .button.selected",
                extend(".button:is(.a)", ":is(.a)", ".selected")
        );
        assertEquals(
                ".button.selected",
                replace(".button:is(.a)", ":is(.a)", ".selected")
        );
    }

    /// Verifies direct, compound, complex, list-target, and list-replacement
    /// extension behavior.
    @Test
    void extendsAndReplacesSelectors() {
        assertEquals(
                ".info .title, .alert .title",
                extend(".info .title", ".info", ".alert")
        );
        assertEquals(
                ".alert .title",
                replace(".info .title", ".info", ".alert")
        );
        assertEquals(
                ".foo.bar.baz, .baz.x",
                extend(".foo.bar.baz", ".foo.bar", ".x")
        );
        assertEquals(
                ".baz.x",
                replace(".foo.bar.baz", ".foo.bar", ".x")
        );
        assertEquals(
                ".notice .title, .alert .warn .title",
                extend(".notice .title", ".notice", ".alert .warn")
        );
        assertEquals(
                ".foo .bar, .foo .x, .x .bar, .x .x",
                extend(".foo .bar", ".foo, .bar", ".x")
        );
        assertEquals(
                ".x .x",
                replace(".foo .bar", ".foo, .bar", ".x")
        );
        assertEquals(".b, .c", replace(".a", ".a", ".b, .c"));
        assertEquals(
                ".a[data=x], .a.b",
                extend(".a[data=x]", "[data=\"x\"]", ".b")
        );
        assertEquals(
                ".a.b",
                replace(".a[data=x]", "[data=\"x\"]", ".b")
        );
        assertEquals(
                ".button:hover, .button.interactive",
                extend(".button:hover", ":hover", ".interactive")
        );
        assertEquals(
                ".button.interactive",
                replace(".button:hover", ":hover", ".interactive")
        );
        assertEquals(
                ".button:lang(en), .button.localized",
                extend(".button:lang(en)", ":lang(en)", ".localized")
        );
        assertEquals(
                ".button.localized",
                replace(".button:lang(en)", ":lang(en)", ".localized")
        );
        assertEquals(
                ".card%token, .card.replacement",
                extend(".card%token", "%token", ".replacement")
        );
        assertEquals(
                ".card.replacement",
                replace(".card%token", "%token", ".replacement")
        );
    }

    /// Verifies extension and replacement retain namespace restrictions.
    @Test
    void extendsAndReplacesNamespaceAwareElementSelectors() {
        // The extended form is a strict subselector of the original, so dart-sass
        // extend trimming keeps only the broader original alternative.
        assertEquals(
                "svg|a.item",
                extend("svg|a.item", "svg|*", ".selected")
        );
        assertEquals(
                "svg|a.item.selected",
                replace("svg|a.item", "svg|*", ".selected")
        );
        assertEquals(
                "svg|*.item, .item.selected",
                extend("svg|*.item", "svg|*", ".selected")
        );
        assertEquals(
                ".item.selected",
                replace("svg|*.item", "svg|*", ".selected")
        );
    }

    /// Verifies parent-containing pseudo selectors and complex extension targets
    /// fail explicitly instead of using text-based semantics.
    @Test
    void rejectsUnsupportedSelectorSemantics() {
        var nestedParent = assertThrows(
                SassValueException.class,
                () -> SelectorAlgebra.unify(parse(":is(&)"), parse(".a"))
        );
        assertEquals(
                "$selector1: Selector algebra can't operate on pseudo selectors containing parent selectors.",
                nestedParent.getMessage()
        );

        var complexTarget = assertThrows(
                SassValueException.class,
                () -> SelectorAlgebra.extend(parse(".a"), parse(".a .b"), parse(".c"))
        );
        assertEquals("Can't extend complex selector .a .b.", complexTarget.getMessage());
    }

    /// Returns the CSS spelling of a selector unification.
    ///
    /// @param first  the first selector
    /// @param second the second selector
    /// @return the unified selector CSS
    private static String unify(String first, String second) {
        @Nullable SelectorList unified = SelectorAlgebra.unify(parse(first), parse(second));
        assertTrue(unified != null);
        return unified.toCssString();
    }

    /// Returns whether one selector is a superselector of another.
    ///
    /// @param superselector the candidate broader selector
    /// @param subselector   the candidate narrower selector
    /// @return the comparison result
    private static boolean isSuperselector(String superselector, String subselector) {
        return SelectorAlgebra.isSuperselector(parse(superselector), parse(subselector));
    }

    /// Returns the CSS spelling after extension.
    ///
    /// @param selector the selector to extend
    /// @param extendee the target selector
    /// @param extender the replacement selector
    /// @return the extended selector CSS
    private static String extend(String selector, String extendee, String extender) {
        return SelectorAlgebra.extend(
                parse(selector),
                parse(extendee),
                parse(extender)
        ).toCssString();
    }

    /// Returns the CSS spelling after replacement.
    ///
    /// @param selector    the selector to replace within
    /// @param original    the target selector
    /// @param replacement the replacement selector
    /// @return the replaced selector CSS
    private static String replace(String selector, String original, String replacement) {
        return SelectorAlgebra.replace(
                parse(selector),
                parse(original),
                parse(replacement)
        ).toCssString();
    }

    /// Parses one selector using a synthetic source span.
    ///
    /// @param text the selector source
    /// @return the parsed selector list
    private static SelectorList parse(String text) {
        return SelectorList.parse(text, span(text));
    }

    /// Creates a synthetic span for selector text.
    ///
    /// @param text the selector text
    /// @return a span covering the text
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
