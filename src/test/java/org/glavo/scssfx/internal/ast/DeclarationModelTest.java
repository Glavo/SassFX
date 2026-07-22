// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies declaration variants, immutability, and structural invariants.
@NotNullByDefault
final class DeclarationModelTest {
    /// Verifies SassScript, raw, and nested declarations retain distinct forms.
    @Test
    void representsDeclarationVariants() {
        var sass = Declaration.sassScript(
                Interpolation.plain("width", span("width")),
                new NumberExpression(1, "px", span("1px")),
                span("width: 1px")
        );
        assertFalse(sass.hasChildren());
        assertTrue(sass.parsedAsSassScript());
        assertEquals("width: 1px;", sass.toString());

        var raw = Declaration.raw(
                Interpolation.plain("--value", span("--value")),
                StringExpression.plain(" $value", span(" $value")),
                span("--value: $value")
        );
        assertFalse(raw.hasChildren());
        assertFalse(raw.parsedAsSassScript());
        assertEquals("--value: $value;", raw.toString());

        var children = new ArrayList<SassStatement>();
        children.add(sass);
        var nested = Declaration.nested(
                Interpolation.plain("font", span("font")),
                null,
                children,
                span("font: {width: 1px;}")
        );
        children.clear();

        var nestedChildren = Objects.requireNonNull(nested.children());
        assertTrue(nested.hasChildren());
        assertEquals(List.of(sass), nestedChildren);
        assertEquals("font: {width: 1px;}", nested.toString());
        assertThrows(UnsupportedOperationException.class, nestedChildren::clear);
    }

    /// Verifies invalid leaf and raw declaration combinations are rejected.
    @Test
    void validatesDeclarationStructure() {
        var name = Interpolation.plain("name", span("name"));
        var value = StringExpression.plain("value", span("value"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Declaration(name, null, null, true, span("name:"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Declaration(
                        name,
                        new StringExpression(value.text(), true),
                        null,
                        false,
                        span("name: value")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Declaration(
                        name,
                        value,
                        List.of(),
                        false,
                        span("name: value {}")
                )
        );
    }

    /// Creates a span covering a complete source string.
    ///
    /// @param text the source text
    /// @return the complete source span
    private static SourceSpan span(String text) {
        return new SourceFile(text, null).span(0, text.length());
    }
}
