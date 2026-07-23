// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.sourcemap;

import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.SourceMap;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies VLQ encoding and end-to-end CSS source maps.
@NotNullByDefault
final class SourceMapTest {
    /// Encodes representative VLQ values.
    @Test
    void encodesVlqIntegers() {
        assertEquals("A", encode(0));
        assertEquals("C", encode(1));
        assertEquals("D", encode(-1));
        assertEquals("gB", encode(16));
    }

    /// Maps selectors and declaration names/values to source locations.
    @Test
    void mapsSelectorsAndDeclarations() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                foo {
                                  bar: baz;
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT,
                new CompileOptions(true, List.of())
        );
        assertEquals(
                """
                        foo {
                          bar: baz;
                        }""",
                result.output()
        );
        var map = result.sourceMap();
        assertNotNull(map);
        var entries = decode(map);
        assertFalse(entries.isEmpty());
        assertTrue(hasMapping(entries, 0, 0, 0, 0), entries.toString());
        assertTrue(hasMapping(entries, 1, 2, 1, 2), entries.toString());
        assertTrue(hasMapping(entries, 1, 7, 1, 7), entries.toString());
    }

    /// Maps nested child selectors to the nested source selector.
    @Test
    void mapsNestedSelectors() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                foo {
                                  bar {
                                    x: y;
                                  }
                                }
                                """,
                        Syntax.SCSS
                ),
                CssTarget.DEFAULT,
                new CompileOptions(true, List.of())
        );
        assertEquals(
                """
                        foo bar {
                          x: y;
                        }""",
                result.output()
        );
        var map = result.sourceMap();
        assertNotNull(map);
        var entries = decode(map);
        assertTrue(hasMapping(entries, 0, 0, 1, 2), entries.toString());
        assertTrue(hasMapping(entries, 1, 2, 2, 4), entries.toString());
    }

    /// Records multiple source files loaded through the module system.
    @Test
    void mapsMultipleSourceFiles(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_theme.scss"), ".theme { color: red; }\n");
        Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "theme";
                        .main { color: blue; }
                        """
        );
        var result = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("main.scss")),
                CssTarget.DEFAULT,
                new CompileOptions(true, List.of())
        );
        var map = result.sourceMap();
        assertNotNull(map);
        var json = map.json();
        assertTrue(json.contains("main.scss") || json.contains("main.scss".replace('\\', '/')), json);
        assertTrue(json.contains("theme"), json);
        assertFalse(decode(map).isEmpty());
    }

    /// Keeps CSS identical when source maps are disabled.
    @Test
    void leavesCssUnchangedWhenDisabled() throws Exception {
        var source = """
                .a {
                  color: red;
                }
                """;
        var withMap = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT,
                new CompileOptions(true, List.of())
        );
        var withoutMap = new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
        assertEquals(withoutMap.output(), withMap.output());
        assertEquals(null, withoutMap.sourceMap());
    }

    /// Encodes one VLQ integer.
    private static String encode(int value) {
        var buffer = new StringBuilder();
        Vlq.encode(value, buffer);
        return buffer.toString();
    }

    /// Returns whether any decoded entry matches the expected coordinates.
    private static boolean hasMapping(
            List<DecodedEntry> entries,
            int generatedLine,
            int generatedColumn,
            int sourceLine,
            int sourceColumn
    ) {
        for (var entry : entries) {
            if (entry.generatedLine == generatedLine
                    && entry.generatedColumn == generatedColumn
                    && entry.sourceLine == sourceLine
                    && entry.sourceColumn == sourceColumn) {
                return true;
            }
        }
        return false;
    }

    /// Decodes absolute mapping entries from a source map.
    private static List<DecodedEntry> decode(SourceMap map) {
        var json = map.json();
        var start = json.indexOf("\"mappings\":\"");
        assertTrue(start >= 0, json);
        start += "\"mappings\":\"".length();
        var end = json.indexOf('"', start);
        var mappings = json.substring(start, end);
        var entries = new ArrayList<DecodedEntry>();
        var generatedLine = 0;
        var generatedColumn = 0;
        var sourceIndex = 0;
        var sourceLine = 0;
        var sourceColumn = 0;
        var index = 0;
        while (index < mappings.length()) {
            var character = mappings.charAt(index);
            if (character == ';') {
                generatedLine++;
                generatedColumn = 0;
                index++;
                continue;
            }
            if (character == ',') {
                index++;
                continue;
            }
            var first = decodeVlq(mappings, index);
            generatedColumn += first.value();
            index = first.nextIndex();
            var second = decodeVlq(mappings, index);
            sourceIndex += second.value();
            index = second.nextIndex();
            var third = decodeVlq(mappings, index);
            sourceLine += third.value();
            index = third.nextIndex();
            var fourth = decodeVlq(mappings, index);
            sourceColumn += fourth.value();
            index = fourth.nextIndex();
            entries.add(new DecodedEntry(
                    generatedLine,
                    generatedColumn,
                    sourceIndex,
                    sourceLine,
                    sourceColumn
            ));
        }
        return entries;
    }

    /// Decodes one VLQ integer starting at {@code index}.
    private static VlqValue decodeVlq(String mappings, int index) {
        var result = 0;
        var shift = 0;
        while (true) {
            var digit = decodeDigit(mappings.charAt(index++));
            result |= (digit & 0x1f) << shift;
            if ((digit & 0x20) == 0) {
                break;
            }
            shift += 5;
        }
        var value = (result & 1) == 0 ? result >>> 1 : -(result >>> 1);
        return new VlqValue(value, index);
    }

    /// Decodes one Base64 VLQ digit.
    private static int decodeDigit(char character) {
        if (character >= 'A' && character <= 'Z') {
            return character - 'A';
        }
        if (character >= 'a' && character <= 'z') {
            return character - 'a' + 26;
        }
        if (character >= '0' && character <= '9') {
            return character - '0' + 52;
        }
        if (character == '+') {
            return 62;
        }
        if (character == '/') {
            return 63;
        }
        throw new IllegalArgumentException("Invalid VLQ digit: " + character);
    }

    /// One decoded absolute mapping entry.
    private record DecodedEntry(
            int generatedLine,
            int generatedColumn,
            int sourceIndex,
            int sourceLine,
            int sourceColumn
    ) {
    }

    /// One decoded VLQ integer and the next parse index.
    private record VlqValue(int value, int nextIndex) {
    }
}
