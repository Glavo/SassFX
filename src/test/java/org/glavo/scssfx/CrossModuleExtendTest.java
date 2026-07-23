// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies `@extend` across `@use` module boundaries.
@NotNullByDefault
final class CrossModuleExtendTest {
    /// Extends a placeholder defined in an upstream `@use` module.
    @Test
    void extendsPlaceholderFromUsedModule(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_base.scss"),
                """
                        %btn {
                          color: red;
                          border: 1px solid;
                        }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "base";
                        .primary {
                          @extend %btn;
                        }
                        .secondary {
                          @extend %btn;
                          opacity: 0.8;
                        }
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        .primary, .secondary {
                          color: red;
                          border: 1px solid;
                        }

                        .secondary {
                          opacity: 0.8;
                        }""",
                css
        );
    }

    /// Chains extensions that span multiple modules.
    @Test
    void chainsExtensionsAcrossModules(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_a.scss"),
                """
                        .a {
                          color: red;
                        }
                        """
        );
        Files.writeString(
                directory.resolve("_b.scss"),
                """
                        @use "a";
                        .b {
                          @extend .a;
                        }
                        """
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "b";
                        .c {
                          @extend .b;
                        }
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        .a, .b, .c {
                          color: red;
                        }""",
                css
        );
    }

    /// Reports a missing cross-module target unless the extend is optional.
    @Test
    void reportsMissingCrossModuleTarget(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_empty.scss"), "// empty\n");
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "empty";
                        .a {
                          @extend %missing;
                        }
                        """
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("main.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals(
                "The target selector was not found.\n"
                        + "Use \"@extend %missing !optional\" to avoid this error.",
                failure.getMessage()
        );
    }

    /// Forwards a library and still allows the entrypoint to extend its placeholders.
    @Test
    void extendsThroughForwardedModule(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("_tokens.scss"),
                """
                        %card {
                          padding: 1rem;
                        }
                        """
        );
        Files.writeString(
                directory.resolve("_facade.scss"),
                "@forward \"tokens\";"
        );
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "facade";
                        .panel {
                          @extend %card;
                        }
                        """
        );

        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT
        ).output();
        assertEquals(
                """
                        .panel {
                          padding: 1rem;
                        }""",
                css
        );
    }
}
