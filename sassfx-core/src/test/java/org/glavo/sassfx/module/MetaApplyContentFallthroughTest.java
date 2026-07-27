// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.module;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies content blocks fall through nested {@code meta.apply}/{@code get-mixin}.
@NotNullByDefault
final class MetaApplyContentFallthroughTest {
    @Test
    void contentFallsThroughNestedApplyAndGetMixin() throws Exception {
        var css = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @use "sass:meta";
                                $global: global;

                                @mixin a {
                                  @content(content-rule-a);
                                  global: $global;
                                }

                                @mixin b {
                                  $global: in-mixin-b;
                                  @include meta.apply(meta.get-mixin(a)) using ($content-arg) {
                                    @content($content-arg);
                                  }
                                }

                                @mixin c {
                                  $global: in-mixin-c;
                                  @include meta.apply(meta.get-mixin(b)) using ($content-arg) {
                                    @content($content-arg);
                                  }
                                }

                                a {
                                  $global: in-style-rule;
                                  @include meta.apply(meta.get-mixin(c)) using ($content-arg) {
                                    in-content-body: $content-arg;
                                  }
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT
        ).output().replace("\r\n", "\n");
        assertEquals(
                """
                        a {
                          in-content-body: content-rule-a;
                          global: global;
                        }""",
                css
        );
    }
}
