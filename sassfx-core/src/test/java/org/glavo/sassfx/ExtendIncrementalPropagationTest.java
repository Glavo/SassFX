// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies incremental propagation between registered selectors and extensions.
@NotNullByDefault
final class ExtendIncrementalPropagationTest {
    /// Preserves the exact nested-pseudo fixed point from libsass issue 2055.
    @Test
    void propagatesNestedPseudoExtensionsForIssue2055() throws Exception {
        assertEquals(
                """
                        :not(.thing):not(:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))) {
                          color: red;
                        }

                        :not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))):not([disabled]:has(:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))):not([disabled]:has(:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))))))) {
                          background: blue;
                        }

                        :has(:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))):not([disabled]:has(:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))):not([disabled]:has(:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled])))):not([disabled]:not(.thing[disabled]):not([disabled]:has(:not(.thing[disabled]):not([disabled]:not(.thing[disabled]))))))))) {
                          background: blue;
                        }""",
                compile(
                        """
                                :not(.thing) {
                                  color: red;
                                }
                                :not(.thing[disabled]) {
                                  @extend .thing;
                                  background: blue;
                                }
                                :has(:not(.thing[disabled])) {
                                  @extend .thing;
                                  background: blue;
                                }
                                """
                )
        );
    }

    /// Applies extensions registered before both their extenders and target rule.
    @Test
    void propagatesLateSelectorExtensionChain() throws Exception {
        assertEquals(
                """
                        .a, .b, .c {
                          x: y;
                        }""",
                compile(
                        """
                                .b {
                                  @extend .a;
                                }
                                .c {
                                  @extend .b;
                                }
                                .a {
                                  x: y;
                                }
                                """
                )
        );
    }

    /// Makes a selector produced by one extension available to an earlier extension.
    @Test
    void extendsTheResultOfAnotherExtension() throws Exception {
        assertEquals(
                """
                        :not(.c):not(.b), .a:not(.c) {
                          x: y;
                        }""",
                compile(
                        """
                                .a {
                                  @extend :not(.b);
                                }
                                .b {
                                  @extend .c;
                                }
                                :not(.c) {
                                  x: y;
                                }
                                """
                )
        );
    }

    /// Reaches a stable fixed point for a three-extension cycle without duplicates.
    @Test
    void terminatesThreeLevelExtensionCycle() throws Exception {
        assertEquals(
                """
                        .foo, .baz, .bar {
                          a: b;
                        }

                        .bar, .foo, .baz {
                          c: d;
                        }

                        .baz, .bar, .foo {
                          e: f;
                        }""",
                compile(
                        """
                                .foo {
                                  a: b;
                                  @extend .bar;
                                }
                                .bar {
                                  c: d;
                                  @extend .baz;
                                }
                                .baz {
                                  e: f;
                                  @extend .foo;
                                }
                                """
                )
        );
    }

    /// Preserves ancestor ordering when a compound extender closes a cycle.
    @Test
    void unifiesCompoundExtendersAcrossAnExtensionCycle() throws Exception {
        assertEquals(
                """
                        .x.y.a, .x.y.c, .x.y.z.b {
                          x: y;
                        }

                        .c, .z.b, .z.x.y.a, .z.x.y.c, .z.x.y.b {
                          x: y;
                        }

                        .z.b, .z.x.y.a, .z.x.y.c, .z.x.y.b {
                          x: y;
                        }""",
                compile(
                        """
                                .x.y.a {
                                  x: y;
                                  @extend .b;
                                }
                                .c {
                                  x: y;
                                  @extend .a;
                                }
                                .z.b {
                                  x: y;
                                  @extend .c;
                                }
                                """
                )
        );
    }

    /// Weaves every parent ordering when distinct compound targets are extended.
    @Test
    void weavesParentsDuringCompoundUnification() throws Exception {
        assertEquals(
                """
                        .e.f, .a .f.b, .c .e.d, .a .c .b.d, .c .a .b.d {
                          x: y;
                        }""",
                compile(
                        """
                                .a .b {
                                  @extend .e;
                                }
                                .c .d {
                                  @extend .f;
                                }
                                .e.f {
                                  x: y;
                                }
                                """
                )
        );
    }

    /// Compiles one SCSS stylesheet to expanded CSS.
    ///
    /// @param source the SCSS source
    /// @return the expanded CSS output
    private static String compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        ).output();
    }
}
