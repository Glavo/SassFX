package org.glavo.sassfx.module;

import org.glavo.sassfx.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@NotNullByDefault
final class CompoundThroughImportTest {
    @Test
    void compoundThroughImport(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_upstream.scss"), """
                %b.c {
                  d: e;
                }
                """);
        Files.writeString(directory.resolve("_midstream.scss"), """
                @use "upstream";

                .a {
                  @extend %b;
                }
                """);
        Files.writeString(directory.resolve("_downstream.scss"), """
                @use "midstream";
                """);
        Files.writeString(directory.resolve("input.scss"), """
                @import "downstream";
                """);
        var css = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.scss")),
                CssTarget.DEFAULT
        ).output();
        System.out.println("---CSS---");
        System.out.println(css.isEmpty() ? "<empty>" : css);
        assertEquals("""
                .c.a {
                  d: e;
                }""", css);
    }
}
