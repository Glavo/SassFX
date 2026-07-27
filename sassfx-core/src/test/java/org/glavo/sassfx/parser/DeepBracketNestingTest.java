// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.parser;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies that deeply nested bracket prefixes do not exhaust the JVM stack.
@NotNullByDefault
final class DeepBracketNestingTest {
    /// Preserves sibling elements after a nested bracketed-list prefix.
    @Test
    void parsesNestedBracketListsWithSiblingElements() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString("a {b: [[c] [d]]}", Syntax.SCSS),
                CssTarget.DEFAULT
        );

        assertEquals(
                """
                        a {
                          b: [[c] [d]];
                        }""",
                result.output()
        );
    }

    /// Reports the innermost missing expression for a deeply unclosed list.
    @Test
    void rejectsDeeplyUnclosedBracketListWithoutStackOverflow() {
        var source = "@H#{" + "[".repeat(765) + "}";
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(source, Syntax.SCSS),
                        CssTarget.DEFAULT
                )
        );

        assertEquals("Expected expression.", failure.getMessage());
    }
}
