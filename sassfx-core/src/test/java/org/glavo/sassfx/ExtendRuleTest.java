// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies statement-level {@code @extend} evaluation.
@NotNullByDefault
final class ExtendRuleTest {
    /// Extends a simple class selector and merges the resulting selector list.
    @Test
    void extendsSimpleClassSelectors() throws Exception {
        var result = compile(
                """
                        .a {
                          color: red;
                        }
                        .b {
                          @extend .a;
                        }
                        """
        );
        assertEquals(
                """
                        .a, .b {
                          color: red;
                        }""",
                result.output()
        );
    }

    /// Extends placeholders and omits pure-placeholder rules from output.
    @Test
    void extendsPlaceholderSelectors() throws Exception {
        var result = compile(
                """
                        %base {
                          color: red;
                        }
                        .btn {
                          @extend %base;
                          font-weight: bold;
                        }
                        """
        );
        assertEquals(
                """
                        .btn {
                          color: red;
                        }

                        .btn {
                          font-weight: bold;
                        }""",
                result.output()
        );
    }

    /// Chains extensions across multiple style rules.
    @Test
    void extendsThroughExtensionChains() throws Exception {
        var result = compile(
                """
                        .a {
                          color: red;
                        }
                        .b {
                          @extend .a;
                        }
                        .c {
                          @extend .b;
                        }
                        """
        );
        assertEquals(
                """
                        .a, .b, .c {
                          color: red;
                        }""",
                result.output()
        );
    }

    /// Accepts unmatched optional extends and rejects required misses.
    @Test
    void handlesOptionalAndMissingTargets() throws Exception {
        var optional = compile(
                """
                        .a {
                          @extend .missing !optional;
                          color: red;
                        }
                        """
        );
        assertEquals(
                """
                        .a {
                          color: red;
                        }""",
                optional.output()
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                .a {
                                  @extend .missing;
                                }
                                """
                )
        );
        assertEquals(
                "The target selector was not found.\n"
                        + "Use \"@extend .missing !optional\" to avoid this error.",
                failure.getMessage()
        );
    }

    /// Compiles SCSS source to expanded CSS.
    private static CompileResult<String> compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
    }
}
