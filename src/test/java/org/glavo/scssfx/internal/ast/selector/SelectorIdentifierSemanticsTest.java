// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies selector algebra semantics for canonical identifier values.
@NotNullByDefault
final class SelectorIdentifierSemanticsTest {
    /// Verifies selector algebra compares escaped core identifiers by value.
    @Test
    void comparesEscapedCoreIdentifiersSemantically() {
        assertEquals(
                ".foo.selected",
                Objects.requireNonNull(
                        SelectorAlgebra.unify(parse(".\\66 oo"), parse(".selected"))
                ).toCssString()
        );
        assertTrue(SelectorAlgebra.isSuperselector(
                parse(".foo"),
                parse(".\\66 oo.selected")
        ));
    }

    /// Verifies attribute and type namespace constraints use decoded identifier values.
    @Test
    void supportsAttributesAndNamespaceAwareElementSemantics() {
        assertEquals(
                "svg|a",
                Objects.requireNonNull(
                        SelectorAlgebra.unify(parse("\\73 vg|a"), parse("svg|a"))
                ).toCssString()
        );
        assertTrue(SelectorAlgebra.isSuperselector(parse("*|a"), parse("svg|a")));

        assertEquals(
                "[data-kind].item",
                Objects.requireNonNull(
                        SelectorAlgebra.unify(parse("[data-kind]"), parse(".item"))
                ).toCssString()
        );
        assertEquals(
                "[*|href]",
                Objects.requireNonNull(
                        SelectorAlgebra.unify(parse("[*|href]"), parse("[*|href]"))
                ).toCssString()
        );
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
