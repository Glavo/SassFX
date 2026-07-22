// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies immutable source interpolation and comment AST models.
@NotNullByDefault
final class InterpolationModelTest {
    /// Verifies plain and empty interpolation semantics.
    @Test
    void representsPlainInterpolations() {
        var emptySpan = span("");
        var empty = new Interpolation(List.of(), emptySpan);
        assertTrue(empty.isPlain());
        assertEquals("", empty.asPlain());
        assertEquals("", empty.initialPlain());

        var plain = Interpolation.plain("plain", span("plain"));
        assertTrue(plain.isPlain());
        assertEquals("plain", plain.asPlain());
        assertEquals("plain", plain.toString());
    }

    /// Verifies expression parts, source ranges, and source rendering.
    @Test
    void representsExpressionInterpolations() {
        var source = new SourceFile("a#{x}b", null);
        var expression = new TestExpression(source.span(3, 4));
        var expressionPart = new ExpressionInterpolationPart(
                expression,
                source.span(1, 5)
        );
        var interpolation = new Interpolation(
                List.of(
                        new TextInterpolationPart("a"),
                        expressionPart,
                        new TextInterpolationPart("b")
                ),
                source.span(0, 6)
        );

        assertFalse(interpolation.isPlain());
        assertNull(interpolation.asPlain());
        assertEquals("a", interpolation.initialPlain());
        assertEquals("a#{x}b", interpolation.toString());
        assertEquals("#{x}", expressionPart.interpolationSpan().text());
    }

    /// Verifies defensive copying and adjacent-text rejection.
    @Test
    void validatesInterpolationParts() {
        var parts = new ArrayList<InterpolationPart>();
        parts.add(new TextInterpolationPart("text"));
        var interpolation = new Interpolation(parts, span("text"));
        parts.clear();

        assertEquals(1, interpolation.parts().size());
        assertThrows(UnsupportedOperationException.class, () -> interpolation.parts().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Interpolation(
                        List.of(
                                new TextInterpolationPart("a"),
                                new TextInterpolationPart("b")
                        ),
                        span("ab")
                )
        );
    }

    /// Verifies incremental interpolation construction and text coalescing.
    @Test
    void buildsInterpolationsIncrementally() {
        var source = new SourceFile("ab#{x}cd", null);
        var expression = new TestExpression(source.span(4, 5));
        var imported = new Interpolation(
                List.of(
                        new TextInterpolationPart("b"),
                        new ExpressionInterpolationPart(expression, source.span(2, 6)),
                        new TextInterpolationPart("c")
                ),
                source.span(1, 7)
        );
        var buffer = new InterpolationBuffer();
        buffer.append("a");
        buffer.add(imported);
        buffer.append('d');

        assertFalse(buffer.isEmpty());
        assertEquals("cd", buffer.trailingText());
        assertEquals("ab#{x}cd", buffer.toString());

        var interpolation = buffer.interpolation(source.span(0, source.length()));
        assertEquals("ab#{x}cd", interpolation.toString());
        assertEquals(3, interpolation.parts().size());

        buffer.append("e");
        assertEquals("ab#{x}cd", interpolation.toString());
        buffer.clear();
        assertTrue(buffer.isEmpty());
        assertEquals("", buffer.toString());
    }

    /// Verifies extraction of documentation lines from silent comments.
    @Test
    void extractsDocumentationComments() {
        var text = "// regular\n/// First\n  /// Second \n///";
        var comment = new SilentComment(text, span(text));

        assertEquals("First\nSecond", comment.documentation());
        assertNull(new SilentComment("// regular", span("// regular")).documentation());
    }

    /// Verifies that stylesheet children are immutable snapshots.
    @Test
    void snapshotsStylesheetChildren() {
        var source = new SourceFile("// comment", null);
        var child = new SilentComment("// comment", source.span(0, source.length()));
        var children = new ArrayList<SassStatement>(List.of(child));
        var stylesheet = new Stylesheet(
                children,
                source.span(0, source.length()),
                false
        );
        children.clear();

        assertEquals(List.of(child), stylesheet.children());
        assertThrows(UnsupportedOperationException.class, () -> stylesheet.children().clear());
    }

    /// Creates a span covering an entire source string.
    ///
    /// @param text the source text
    /// @return the complete source span
    private static SourceSpan span(String text) {
        return new SourceFile(text, null).span(0, text.length());
    }

    /// Provides one expression node for interpolation model tests.
    ///
    /// @param span the expression source range
    @NotNullByDefault
    private record TestExpression(SourceSpan span) implements SassExpression {
        /// Returns the expression source representation.
        ///
        /// @return the fixed expression text
        @Override
        public String toString() {
            return "x";
        }
    }
}
