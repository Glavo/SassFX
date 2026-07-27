// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies named arguments, defaults, and rest parameters.
@NotNullByDefault
final class ArgumentBindingTest {
    /// Compiles named arguments and defaults for functions and mixins.
    @Test
    void bindsNamedArgumentsAndDefaults() throws Exception {
        assertEquals(
                """
                        a {
                          width: 3;
                          height: 4;
                        }

                        a {
                          color: blue;
                        }""",
                compile(
                        """
                                @function add($a, $b: 2) {
                                  @return $a + $b;
                                }
                                @mixin paint($color: red) {
                                  a { color: $color; }
                                }
                                a {
                                  width: add(1);
                                  height: add($b: 3, $a: 1);
                                }
                                @include paint($color: blue);
                                """
                )
        );
    }

    /// Compiles rest parameters and map-as-rest named expansion.
    @Test
    void bindsRestParameters() throws Exception {
        assertEquals(
                """
                        a {
                          width: 2;
                          height: 1;
                        }""",
                compile(
                        """
                                @function count-rest($a, $rest...) {
                                  @return length($rest);
                                }
                                @function first($a, $b) {
                                  @return $a;
                                }
                                a {
                                  width: count-rest(1, 2, 3);
                                  height: first(1, (b: 9)...);
                                }
                                """
                )
        );
    }

    /// Reports arity and naming failures.
    @Test
    void reportsArgumentErrors() {
        assertEquals(
                "Argument $a was passed both by position and by name.",
                failure("@function f($a) { @return $a; } a { b: f(1, $a: 2); }")
        );
        assertEquals(
                "Missing argument $a.",
                failure("@function f($a) { @return $a; } a { b: f($c: 1); }")
        );
        assertEquals(
                "Missing argument $a.",
                failure("@function f($a) { @return $a; } a { b: f(); }")
        );
        assertTrue(failure("@mixin m($args...) {} @include m($x: 1);")
                .contains("No parameter named $x."));
    }

    /// Compiles new built-in map/string/list/color helpers.
    @Test
    void compilesExpandedBuiltIns() throws Exception {
        assertEquals(
                """
                        a {
                          a: 2;
                          b: a, b;
                          c: bc;
                          d: 2;
                          e: a z c;
                          f: 10;
                        }""",
                compile(
                        """
                                $map: (a: 1, b: 2);
                                a {
                                  a: map-get($map, b);
                                  b: map-keys($map);
                                  c: str-slice(abcd, 2, 3);
                                  d: index(a b c, b);
                                  e: set-nth(a b c, 2, z);
                                  f: red(rgb(10, 20, 30));
                                }
                                """
                )
        );
    }

    private static String compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        ).output();
    }

    private static String failure(String source) {
        return assertThrows(
                SassCompilationException.class,
                () -> compile(source)
        ).getMessage();
    }
}
