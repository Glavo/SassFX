// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies {@code @at-root}, media-aware {@code @extend}, and load-css extend registration.
@NotNullByDefault
final class AtRootAndExtendTest {
    /// Lifts nested style rules out of their parent while keeping explicit {@code &}.
    @Test
    void evaluatesDefaultAtRoot() throws Exception {
        var result = compile(
                """
                        .a {
                          color: red;
                          @at-root {
                            .b { color: blue; }
                            &.c { color: green; }
                          }
                        }
                        """
        );
        assertEquals(
                """
                        .a {
                          color: red;
                        }
                        .b {
                          color: blue;
                        }

                        .a.c {
                          color: green;
                        }""",
                result.output()
        );
    }

    /// Keeps default at-root output inside enclosing media.
    @Test
    void keepsDefaultAtRootInsideMedia() throws Exception {
        var result = compile(
                """
                        @media screen {
                          .a {
                            @at-root {
                              .b { color: blue; }
                            }
                          }
                        }
                        """
        );
        assertEquals(
                """
                        @media screen {
                          .b {
                            color: blue;
                          }
                        }""",
                result.output()
        );
    }

    /// Escapes media with an explicit without query.
    @Test
    void escapesMediaWithAtRootQuery() throws Exception {
        var result = compile(
                """
                        @media screen {
                          .a {
                            @at-root (without: media) {
                              .b { color: blue; }
                            }
                          }
                        }
                        """
        );
        assertEquals(
                """
                        .a .b {
                          color: blue;
                        }""",
                result.output()
        );
    }

    /// Supports the trailing style-rule shorthand form.
    @Test
    void supportsAtRootStyleRuleShorthand() throws Exception {
        var result = compile(
                """
                        .a {
                          @at-root .b {
                            color: blue;
                          }
                        }
                        """
        );
        assertEquals(
                """
                        .b {
                          color: blue;
                        }""",
                result.output()
        );
    }

    /// Allows extend inside the same media context and rejects cross-media extend.
    @Test
    void enforcesMediaAwareExtend() throws Exception {
        var sameMedia = compile(
                """
                        @media screen {
                          .a { color: red; }
                          .b { @extend .a; }
                        }
                        """
        );
        assertEquals(
                """
                        @media screen {
                          .a, .b {
                            color: red;
                          }
                        }""",
                sameMedia.output()
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        """
                                .a { color: red; }
                                @media screen {
                                  .b { @extend .a; }
                                }
                                """
                )
        );
        // dart-sass MultiSpanSassException primary text names the target selector.
        assertTrue(
                failure.getMessage().startsWith("From line "),
                () -> "expected multi-span From-line message, got: " + failure.getMessage()
        );
        assertTrue(
                failure.getMessage().contains("input")
                        || failure.getMessage().endsWith(":"),
                () -> "unexpected multi-span message: " + failure.getMessage()
        );
    }

    /// Registers load-css style rules for later host-module extends.
    @Test
    void extendsSelectorsInjectedByLoadCss(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_base.scss"),
                """
                        %btn {
                          color: red;
                        }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "sass:meta";
                        @include meta.load-css("base");
                        .primary {
                          @extend %btn;
                        }
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        .primary {
                          color: red;
                        }""",
                css
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
