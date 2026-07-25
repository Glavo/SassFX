// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies batch51 module import isolation, diagnostics, and calculation errors.
@NotNullByDefault
final class Batch51LanguageTest {
    @Test
    void rejectsAdjustingPowerlessLegacyHue() {
        var failure = assertThrows(
                Exception.class,
                () -> compile(
                        "@use \"sass:color\"; a {b: color.adjust(grey, $hue: 10deg, $space: hsl)}",
                        Syntax.SCSS
                )
        );
        assertTrue(
                failure.getMessage().contains("modifying missing channels"),
                failure.getMessage()
        );
    }

    @Test
    void rejectsRoundWithNumberStrategyAfterSimplification() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("a { e: round(10px + 2px, 8px, 9px); }", Syntax.SCSS)
        );
        assertTrue(
                failure.getMessage().contains("must be either nearest, up, down or to-zero."),
                failure.getMessage()
        );
    }

    @Test
    void rejectsEmptyNthChildArgument() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("a:nth-child() { color: yellowgreen; }", Syntax.SCSS)
        );
        assertTrue(failure.getMessage().contains("Expected \"n\"."), failure.getMessage());
    }

    @Test
    void unknownSchemeFailsAtLoadNotNamespace() {
        var failure = assertThrows(
                Exception.class,
                () -> compile("@use \"scheme:bar\";", Syntax.SCSS)
        );
        assertTrue(
                failure.getMessage().contains("Can't find stylesheet to import."),
                failure.getMessage()
        );
    }

    @Test
    void importDoesNotLeakUseAsStarMembers(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("input.scss"), """
                @import "midstream";
                a {b: $upstream};
                """);
        Files.writeString(directory.resolve("midstream.scss"), "@use \"upstream\" as *;\n");
        Files.writeString(directory.resolve("upstream.scss"), "$upstream: value;\n");

        var failure = assertThrows(
                Exception.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("input.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertTrue(failure.getMessage().contains("Undefined variable."), failure.getMessage());
    }

    @Test
    void plainCssRejectsMultiImportList(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("plain.css"), "@import \"foo\", \"bar\";\n");
        Files.writeString(directory.resolve("input.scss"), "@use \"plain\";\n");

        var failure = assertThrows(
                Exception.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("input.scss")),
                        CssTarget.DEFAULT
                )
        );
        assertTrue(failure.getMessage().contains("expected \";\"."), failure.getMessage());
    }

    private static String compile(String source, Syntax syntax) throws Exception {
        return new SassCompiler()
                .compile(SassSource.fromString(source, syntax), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }
}
