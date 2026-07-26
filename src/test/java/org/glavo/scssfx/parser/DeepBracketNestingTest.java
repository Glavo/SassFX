// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.parser;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
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
