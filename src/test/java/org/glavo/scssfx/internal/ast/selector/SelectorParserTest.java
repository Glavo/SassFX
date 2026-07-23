// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies structural parsing and canonical serialization of CSS selectors.
@NotNullByDefault
final class SelectorParserTest {
    /// Verifies escaped identifier values are decoded and serialized safely.
    @Test
    void canonicalizesEscapedIdentifiers() {
        var selector = parse("\\66 oo.\\31 foo#foo\\2e bar");

        assertEquals("foo.\\31 foo#foo\\.bar", selector.toCssString());
        var compound = selector.components().get(0).components().get(0).selector();
        var type = assertInstanceOf(TypeSelector.class, compound.components().get(0));
        var classSelector = assertInstanceOf(
                ClassSelector.class,
                compound.components().get(1)
        );
        var idSelector = assertInstanceOf(IdSelector.class, compound.components().get(2));
        assertEquals("foo", type.name().name().value());
        assertEquals("1foo", classSelector.name().value());
        assertEquals("foo.bar", idSelector.name().value());

        assertEquals("\\31 foo", CssIdentifier.of("1foo").toCssString());
        assertEquals("\\-", CssIdentifier.of("-").toCssString());
        assertEquals("foo\\.bar", CssIdentifier.of("foo.bar").toCssString());
    }

    /// Verifies every supported namespace form is represented explicitly.
    @Test
    void parsesNamespaces() {
        var selector = parse("svg|a, *|a, |a, svg|*, *|*, |*");

        assertEquals("svg|a, *|a, |a, svg|*, *|*, |*", selector.toCssString());

        var named = assertInstanceOf(
                TypeSelector.class,
                selector.components().get(0).components().get(0).selector().components().get(0)
        );
        assertEquals(SelectorNamespaceKind.NAMED, named.name().namespace().kind());
        assertEquals(
                "svg",
                java.util.Objects.requireNonNull(named.name().namespace().name()).value()
        );
        assertEquals("a", named.name().name().value());

        var any = assertInstanceOf(
                TypeSelector.class,
                selector.components().get(1).components().get(0).selector().components().get(0)
        );
        assertEquals(SelectorNamespaceKind.ANY, any.name().namespace().kind());

        var none = assertInstanceOf(
                TypeSelector.class,
                selector.components().get(2).components().get(0).selector().components().get(0)
        );
        assertEquals(SelectorNamespaceKind.NONE, none.name().namespace().kind());

        var namedUniversal = assertInstanceOf(
                UniversalSelector.class,
                selector.components().get(3).components().get(0).selector().components().get(0)
        );
        assertEquals(SelectorNamespaceKind.NAMED, namedUniversal.namespace().kind());
        var anyUniversal = assertInstanceOf(
                UniversalSelector.class,
                selector.components().get(4).components().get(0).selector().components().get(0)
        );
        assertEquals(SelectorNamespaceKind.ANY, anyUniversal.namespace().kind());
        var noNamespaceUniversal = assertInstanceOf(
                UniversalSelector.class,
                selector.components().get(5).components().get(0).selector().components().get(0)
        );
        assertEquals(SelectorNamespaceKind.NONE, noNamespaceUniversal.namespace().kind());
    }

    /// Verifies attribute selectors retain structured values and source spelling.
    @Test
    void parsesAttributeSelectors() {
        var selector = parse("[svg|href][data-kind|=primary i][title=\"a]b\"]");

        assertEquals(
                "[svg|href][data-kind|=primary i][title=\"a]b\"]",
                selector.toCssString()
        );
        var components = selector.components().get(0).components().get(0).selector().components();
        var namespaced = assertInstanceOf(AttributeSelector.class, components.get(0));
        assertEquals(SelectorNamespaceKind.NAMED, namespaced.name().namespace().kind());
        assertEquals("href", namespaced.name().name().value());
        assertEquals(null, namespaced.matcher());

        var dashMatch = assertInstanceOf(AttributeSelector.class, components.get(1));
        assertEquals(AttributeMatcher.DASH_MATCH, dashMatch.matcher());
        assertEquals("primary", dashMatch.value());
        assertEquals(
                "i",
                java.util.Objects.requireNonNull(dashMatch.modifier()).value()
        );

        var quoted = assertInstanceOf(AttributeSelector.class, components.get(2));
        assertEquals("a]b", quoted.value());
    }

    /// Verifies attribute values decode CSS escapes while output retains source spelling.
    @Test
    void decodesAttributeValuesForSemanticComparison() {
        var selector = parse("[data=\\78][title='a\\20 b']");

        assertEquals("[data=\\78][title='a\\20 b']", selector.toCssString());
        var components = selector.components().get(0).components().get(0).selector().components();
        var escapedIdentifier = assertInstanceOf(AttributeSelector.class, components.get(0));
        assertEquals("x", escapedIdentifier.value());
        var escapedString = assertInstanceOf(AttributeSelector.class, components.get(1));
        assertEquals("a b", escapedString.value());
    }

    /// Verifies pseudo selectors retain grammar-specific functional arguments.
    @Test
    void parsesPseudoSelectorsAndPlaceholders() {
        var selector = parse(
                ":is(.a, .b)::slotted(.icon):nth-child(2n + 1 of .a, .b)%token"
        );

        assertEquals(
                ":is(.a, .b)::slotted(.icon):nth-child(2n + 1 of .a, .b)%token",
                selector.toCssString()
        );
        var components = selector.components().get(0).components().get(0).selector().components();
        var is = assertInstanceOf(PseudoSelector.class, components.get(0));
        assertEquals("is", is.name().value());
        var isArgument = assertInstanceOf(SelectorPseudoArgument.class, is.argument());
        assertEquals(".a, .b", isArgument.selectors().toCssString());

        var slotted = assertInstanceOf(PseudoSelector.class, components.get(1));
        assertEquals("slotted", slotted.name().value());
        assertEquals(true, slotted.element());
        var slottedArgument = assertInstanceOf(SelectorPseudoArgument.class, slotted.argument());
        assertEquals(".icon", slottedArgument.selectors().toCssString());

        var nthChild = assertInstanceOf(PseudoSelector.class, components.get(2));
        var nthArgument = assertInstanceOf(NthPseudoArgument.class, nthChild.argument());
        assertEquals("2n + 1", nthArgument.formula());
        assertEquals(
                ".a, .b",
                java.util.Objects.requireNonNull(nthArgument.selectors()).toCssString()
        );

        var placeholder = assertInstanceOf(PlaceholderSelector.class, components.get(3));
        assertEquals("token", placeholder.name().value());
    }

    /// Verifies recursive selector pseudos preserve parent-reference structure.
    @Test
    void parsesRecursivePseudoSelectorArguments() {
        var selector = parse(
                ":is(&, :not(&-active), .fallback):has(> &):-webkit-any(&)"
                        + "::-webkit-slotted(&):lang(\"&\")"
        );

        assertTrue(selector.containsParentSelector());
        assertEquals(5, selector.parentSelectorCount());
        assertTrue(selector.hasParentSelectorSuffix());
        assertEquals(false, selector.hasUnresolvedParentReference());
        assertEquals(
                ":is(.parent, :not(.parent-active), .fallback):has(> .parent)"
                        + ":-webkit-any(.parent)::-webkit-slotted(.parent):lang(\"&\")",
                selector.nestWithin(parse(".parent")).toCssString()
        );

        var opaque = parse(":lang(&)");
        assertTrue(opaque.hasUnresolvedParentReference());
        assertEquals(
                "Parent selectors in non-selector pseudo arguments aren't supported.",
                assertThrows(
                        SassValueException.class,
                        () -> opaque.nestWithin(parse(".parent"))
                ).getMessage()
        );
    }

    /// Verifies malformed selector escapes, attributes, and parent positions retain clear diagnostics.
    @Test
    void rejectsMalformedStructuredSelectors() {
        assertEquals("Expected escape sequence.", failure(".\\"));
        assertEquals("Expected closing ']'.", failure("[data=value"));
        assertEquals(
                "Parent selector must be the first selector in a compound.",
                failure(".item&")
        );
    }

    /// Parses one selector using a synthetic source span.
    ///
    /// @param text the selector source
    /// @return the parsed selector list
    private static SelectorList parse(String text) {
        return SelectorList.parse(text, span(text));
    }

    /// Returns the parse failure message for one invalid selector.
    ///
    /// @param text the invalid selector source
    /// @return the primary parse diagnostic
    private static String failure(String text) {
        return assertThrows(SassValueException.class, () -> parse(text)).getMessage();
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
