// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.sourcemap;

import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassCanonicalizeContext;
import org.glavo.sassfx.SassDiagnosticOptions;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassImporterResult;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.SourceMap;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
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

    /// Accounts for the separator required by compressed JavaFX media rules.
    @Test
    void mapsCompressedJavaFXMediaRules() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @media (min-width: 1px) {
                                  Pane {
                                    -fx-opacity: 1;
                                  }
                                }
                                """,
                        Syntax.SCSS
                ),
                new JavaFXCssTarget(
                        JavaFXTarget.JAVAFX27,
                        OutputStyle.COMPRESSED
                ),
                new CompileOptions(true, List.of())
        );

        assertEquals(
                "@media (min-width: 1px){Pane{-fx-opacity:1}}",
                result.output()
        );
        var map = result.sourceMap();
        assertNotNull(map);
        assertTrue(
                hasMapping(decode(map), 0, 24, 1, 2),
                decode(map).toString()
        );
    }

    /// Accounts for charset and BOM prefixes in generated coordinates.
    @Test
    void mapsAfterCharsetPrefixes() throws Exception {
        var compiler = new SassCompiler();
        var options = new CompileOptions(true, List.of());
        var expanded = compiler.compile(
                SassSource.fromString("a { content: \"你好\"; }", Syntax.SCSS),
                new CssTarget(OutputStyle.EXPANDED, true),
                options
        );
        var compressed = compiler.compile(
                SassSource.fromString("a { content: \"你好\"; }", Syntax.SCSS),
                new CssTarget(OutputStyle.COMPRESSED, true),
                options
        );

        var expandedMap = expanded.sourceMap();
        var compressedMap = compressed.sourceMap();
        assertNotNull(expandedMap);
        assertNotNull(compressedMap);
        assertTrue(expanded.output().startsWith("@charset \"UTF-8\";\n"));
        assertTrue(hasMapping(
                decode(expandedMap),
                1,
                0,
                0,
                0
        ));
        assertTrue(compressed.output().startsWith("\uFEFF"));
        assertTrue(hasMapping(
                decode(compressedMap),
                0,
                1,
                0,
                0
        ));
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

    /// Embeds source text captured during compilation without dereferencing a
    /// custom importer's display URL.
    ///
    /// @param directory the isolated directory used for an unreadable display
    ///                  URL
    @Test
    void embedsLoadedSourceContents(@TempDir Path directory) throws Exception {
        var displayUrl = directory.resolve("never-created.scss").toUri();
        SassImporter importer = new SassImporter() {
            /// Canonicalizes the test module.
            @Override
            public URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                return URI.create("custom:module");
            }

            /// Returns source text with a distinct source-map display URL.
            @Override
            public SassImporterResult load(URI canonicalUrl) {
                return new SassImporterResult(
                        ".imported { value: café; }",
                        Syntax.SCSS,
                        displayUrl
                );
            }
        };
        var options = new CompileOptions(
                true,
                List.of(),
                null,
                List.of(importer),
                List.of(),
                SassDiagnosticOptions.DEFAULT,
                true
        );
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        "@use \"module\";\n.root { value: naïve; }",
                        Syntax.SCSS,
                        URI.create("custom:root")
                ),
                CssTarget.DEFAULT,
                options
        );

        var map = result.sourceMap();
        assertNotNull(map);
        assertTrue(map.json().contains("\"sourcesContent\""), map.json());
        assertTrue(
                map.json().contains(".imported { value: café; }"),
                map.json()
        );
        assertTrue(
                map.json().contains("@use \\\"module\\\";\\n.root"),
                map.json()
        );
        assertTrue(map.json().contains(displayUrl.toString()), map.json());
        assertFalse(Files.exists(directory.resolve("never-created.scss")));
    }

    /// Omits source contents unless explicitly requested.
    @Test
    void omitsSourceContentsByDefault() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(".a { value: one; }", Syntax.SCSS),
                CssTarget.DEFAULT,
                new CompileOptions(true, List.of())
        );

        var map = result.sourceMap();
        assertNotNull(map);
        assertFalse(map.json().contains("\"sourcesContent\""));
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
