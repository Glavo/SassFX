// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

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
                        .secondary, .primary {
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

    /// Keeps sibling module extensions from mutating a shared module instance.
    @Test
    void mergesSiblingExtensionsWithoutDuplicatingSharedCss(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(directory.resolve("_other.scss"), "%in-other.a {x: y}\n");
        Files.writeString(
                directory.resolve("_left.scss"),
                "@use \"other\";\n.a {@extend %in-other}\n"
        );
        Files.writeString(
                directory.resolve("_right.scss"),
                "@use \"other\";\n.b {@extend %in-other}\n"
        );
        Files.writeString(
                directory.resolve("input.scss"),
                "@use \"left\";\n@use \"right\";\n"
        );

        var css = compile(directory);
        assertEquals(
                """
                        .a {
                          x: y;
                        }""",
                css.strip()
        );
    }

    /// Applies extensions from a legacy import to both imported CSS copies.
    @Test
    void appliesLegacyImportExtensionsToBothCopies(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), "shared {x: y}\n");
        Files.writeString(
                directory.resolve("_used.scss"),
                "@use \"shared\";\nin-used {@extend shared}\n"
        );
        Files.writeString(
                directory.resolve("_imported.scss"),
                "@import \"shared\";\nin-imported {@extend shared}\n"
        );
        Files.writeString(
                directory.resolve("input.scss"),
                "@use \"used\";\n@import \"imported\";\n"
        );

        var css = compile(directory);
        assertEquals(
                """
                        shared, in-used, in-imported {
                          x: y;
                        }

                        shared, in-imported {
                          x: y;
                        }""",
                css.strip()
        );
    }

    /// Applies an imported extension to the module CSS and its import copy.
    @Test
    void appliesImportedExtensionToOriginalAndCopy(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), "shared {x: y}\n");
        Files.writeString(
                directory.resolve("_used.scss"),
                "@use \"shared\";\nin-used {@extend shared}\n"
        );
        Files.writeString(
                directory.resolve("_imported.scss"),
                "@use \"shared\";\nin-imported {@extend shared}\n"
        );
        Files.writeString(
                directory.resolve("input.scss"),
                "@use \"used\";\n@import \"imported\";\n"
        );

        var css = compile(directory);
        assertEquals(
                """
                        shared, in-used, in-imported {
                          x: y;
                        }

                        shared, in-imported {
                          x: y;
                        }""",
                css.strip()
        );
    }

    /// Prevents sibling modules from cross-extending products in a shared rule.
    @Test
    void isolatesSiblingExtensionProducts(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), "in-shared {x: y}\n");
        Files.writeString(
                directory.resolve("_left.scss"),
                """
                        @use "shared";
                        left-extendee {@extend in-shared}
                        left-extender {@extend right-extendee !optional}
                        """
        );
        Files.writeString(
                directory.resolve("_right.scss"),
                """
                        @use "shared";
                        right-extendee {@extend in-shared}
                        right-extender {@extend left-extendee !optional}
                        """
        );
        Files.writeString(
                directory.resolve("input.scss"),
                "@use \"left\";\n@use \"right\";\n"
        );

        var css = compile(directory);
        assertEquals(
                """
                        in-shared, right-extendee, left-extendee {
                          x: y;
                        }""",
                css.strip()
        );
    }

    /// Carries compound placeholder extensions through an imported module.
    @Test
    void carriesCompoundExtensionThroughImport(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(
                directory.resolve("_upstream.scss"),
                """
                        %b.c {
                          d: e;
                        }
                        """
        );
        Files.writeString(
                directory.resolve("_midstream.scss"),
                """
                        @use "upstream";

                        .a {
                          @extend %b;
                        }
                        """
        );
        Files.writeString(directory.resolve("_downstream.scss"), "@use \"midstream\";\n");
        Files.writeString(directory.resolve("input.scss"), "@import \"downstream\";\n");

        var css = compile(directory);
        assertEquals(
                """
                        .c.a {
                          d: e;
                        }""",
                css
        );
    }

    /// Keeps the import-path and use-path copies of a module independent.
    @Test
    void isolatesImportPathAndUsePathCopies(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), "shared {x: y}");
        Files.writeString(
                directory.resolve("_imported.scss"),
                """
                        @use "shared";
                        in-imported {@extend shared}
                        """
        );
        Files.writeString(directory.resolve("_importer.scss"), "@import \"imported\";");
        Files.writeString(
                directory.resolve("_used.scss"),
                """
                        @use "shared";
                        in-used {@extend shared}
                        """
        );
        Files.writeString(
                directory.resolve("input.scss"),
                """
                        @use "importer";
                        @use "used";
                        """
        );

        var css = compile(directory);
        assertEquals(
                """
                        shared, in-imported {
                          x: y;
                        }

                        shared, in-used {
                          x: y;
                        }""",
                css
        );
    }

    /// Keeps sibling extension products isolated across imported diamond copies.
    @Test
    void isolatesImportedDiamondCopies(
            @TempDir Path directory
    ) throws Exception {
        Files.writeString(directory.resolve("_shared.scss"), "in-shared {x: y}");
        Files.writeString(
                directory.resolve("_left.scss"),
                """
                        @use "shared";
                        left-extendee {@extend in-shared}
                        left-extender {@extend right-extendee !optional}
                        """
        );
        Files.writeString(
                directory.resolve("_right.scss"),
                """
                        @use "shared";
                        right-extendee {@extend in-shared}
                        right-extender {@extend left-extendee !optional}
                        """
        );
        Files.writeString(
                directory.resolve("_downstream.scss"),
                """
                        @use "left";
                        @use "right";
                        """
        );
        Files.writeString(directory.resolve("_imported.scss"), "@use \"downstream\";");
        Files.writeString(
                directory.resolve("input.scss"),
                """
                        @use "downstream";
                        @import "downstream";
                        @import "imported";
                        """
        );

        var css = compile(directory);
        assertEquals(
                """
                        in-shared, right-extendee, left-extendee {
                          x: y;
                        }

                        in-shared, right-extendee, left-extendee {
                          x: y;
                        }

                        in-shared, right-extendee, left-extendee {
                          x: y;
                        }""",
                css
        );
    }

    /// Compiles `input.scss` from an isolated module fixture.
    ///
    /// @param directory the fixture directory
    /// @return the generated CSS
    private static String compile(Path directory) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT
        ).output();
    }
}
