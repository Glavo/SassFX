// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.sourcemap;

import com.google.gson.JsonParser;
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CompileResult;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.SassCanonicalizeContext;
import org.glavo.sassfx.SassContentsImporter;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassDiagnosticOptions;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassImporterResult;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.SourceMap;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/// Ports the complete annotated source-map suite from Dart Sass 1.102.0.
@NotNullByDefault
final class DartSassSourceMapCompatibilityTest {
    /// Runs every annotated Sass and SCSS source-map fixture.
    ///
    /// @return one dynamic test per upstream fixture
    @TestFactory
    Stream<DynamicTest> annotatedMappings() {
        return mappingCases().stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.name(),
                () -> assertCase(testCase)
        ));
    }

    /// Verifies enabling source maps does not replace an evaluation failure's
    /// usage span with the mapped variable-definition span.
    @Test
    void variableErrorsUseTheReferenceLocation() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "$map: (a: b);\nx {y: $map}",
                                Syntax.SCSS
                        ),
                        CssTarget.DEFAULT,
                        CompileOptions.DEFAULT.withSourceMap(true)
                )
        );
        var span = Objects.requireNonNull(failure.primaryDiagnostic().span());
        assertEquals("$map", span.text());
        assertEquals(1, span.start().line());
        assertEquals(6, span.start().column());
    }

    /// Returns every translated mapping fixture in upstream declaration order.
    ///
    /// @return the immutable fixture list
    private static @Unmodifiable List<MappingCase> mappingCases() {
        return List.of(
                dual(
                        "basic style rule",
                        """
                                {{1}}foo
                                  {{2}}bar: baz
                                """,
                        """
                                {{1}}foo {
                                  {{2}}bar: baz;
                                }
                                """,
                        """
                                {{1}}foo {
                                  {{2}}bar: baz;
                                }
                                """
                ),
                dual(
                        "multiline selector",
                        """
                                {{1}}foo,
                                bar
                                  {{2}}bar: baz
                                """,
                        """
                                {{1}}foo,
                                bar {
                                  {{2}}bar: baz;
                                }
                                """,
                        """
                                {{1}}foo,
                                {{1}}bar {
                                  {{2}}bar: baz;
                                }
                                """
                ),
                scss(
                        "property value on another line",
                        """
                                {{1}}foo {
                                  {{2}}bar:
                                      {{3}}baz;
                                }
                                """,
                        """
                                {{1}}foo {
                                  {{2}}bar: {{3}}baz;
                                }
                                """
                ),
                scss(
                        "multiline property value",
                        """
                                {{1}}foo {
                                  {{2}}bar: baz
                                      bang;
                                }
                                """,
                        """
                                {{1}}foo {
                                  {{2}}bar: baz bang;
                                }
                                """
                ),
                dual(
                        "nested style rule",
                        """
                                foo
                                  {{1}}bar
                                    {{2}}baz: bang
                                """,
                        """
                                foo {
                                  {{1}}bar {
                                    {{2}}baz: bang;
                                  }
                                }
                                """,
                        """
                                {{1}}foo bar {
                                  {{2}}baz: bang;
                                }
                                """
                ),
                dual(
                        "nested rule and declaration",
                        """
                                {{1}}foo
                                  {{2}}a: b

                                  {{3}}bar
                                    {{4}}x: y
                                """,
                        """
                                {{1}}foo {
                                  {{2}}a: b;

                                  {{3}}bar {
                                    {{4}}x: y;
                                  }
                                }
                                """,
                        """
                                {{1}}foo {
                                  {{2}}a: b;
                                }
                                {{3}}foo bar {
                                  {{4}}x: y;
                                }
                                """
                ),
                dual(
                        "nested declaration",
                        """
                                {{1}}foo
                                  {{2}}a: b
                                    {{3}}c: d
                                """,
                        """
                                {{1}}foo {
                                  {{2}}a: b {
                                    {{3}}c: d;
                                  }
                                }
                                """,
                        """
                                {{1}}foo {
                                  {{2}}a: b;
                                  {{3}}a-c: d;
                                }
                                """
                ),
                dual(
                        "unknown at-rule without children",
                        "{{1}}@foo (fblthp)",
                        "{{1}}@foo (fblthp);",
                        "{{1}}@foo (fblthp);"
                ),
                dual(
                        "unknown at-rule containing declarations",
                        """
                                {{1}}@foo (fblthp)
                                  {{2}}bar: baz
                                """,
                        """
                                {{1}}@foo (fblthp) {
                                  {{2}}bar: baz;
                                }
                                """,
                        """
                                {{1}}@foo (fblthp) {
                                  {{2}}bar: baz;
                                }
                                """
                ),
                dual(
                        "unknown at-rule containing style rules",
                        """
                                {{1}}@foo (fblthp)
                                  {{2}}bar
                                    {{3}}baz: bang
                                """,
                        """
                                {{1}}@foo (fblthp) {
                                  {{2}}bar {
                                    {{3}}baz: bang;
                                  }
                                }
                                """,
                        """
                                {{1}}@foo (fblthp) {
                                  {{2}}bar {
                                    {{3}}baz: bang;
                                  }
                                }
                                """
                ),
                dual(
                        "unknown at-rule containing at-rules",
                        """
                                {{1}}@foo (fblthp)
                                  {{2}}@bar baz
                                """,
                        """
                                {{1}}@foo (fblthp) {
                                  {{2}}@bar baz;
                                }
                                """,
                        """
                                {{1}}@foo (fblthp) {
                                  {{2}}@bar baz;
                                }
                                """
                ),
                dual(
                        "single-line comments",
                        """
                                {{1}}/* foo bar
                                {{2}}/* baz bang
                                """,
                        """
                                {{1}}/* foo bar */
                                {{2}}/* baz bang */
                                """,
                        """
                                {{1}}/* foo bar */
                                {{2}}/* baz bang */
                                """
                ),
                dual(
                        "multiline comment",
                        """
                                {{1}}/* foo bar
                                   baz bang
                                """,
                        """
                                {{1}}/* foo bar
                                 * baz bang */
                                """,
                        """
                                {{1}}/* foo bar
                                {{1}} * baz bang */
                                """
                ),
                dual(
                        "single import URL",
                        "@import {{1}}url(foo)",
                        "@import {{1}}url(foo);",
                        "{{1}}@import url(foo);"
                ),
                dual(
                        "multiple import URLs",
                        "@import {{1}}url(foo), {{2}}\"bar.css\"",
                        """
                                @import {{1}}url(foo),
                                  {{2}}"bar.css";
                                """,
                        """
                                {{1}}@import url(foo);
                                {{2}}@import "bar.css";
                                """
                ),
                dual(
                        "keyframes",
                        """
                                {{1}}@keyframes name
                                  {{2}}from
                                    {{3}}top: 0px

                                  {{4}}10%
                                    {{5}}top: 10px
                                """,
                        """
                                {{1}}@keyframes name {
                                  {{2}}from {
                                    {{3}}top: 0px;
                                  }

                                  {{4}}10% {
                                    {{5}}top: 10px;
                                  }
                                }
                                """,
                        """
                                {{1}}@keyframes name {
                                  {{2}}from {
                                    {{3}}top: 0px;
                                  }
                                  {{4}}10% {
                                    {{5}}top: 10px;
                                  }
                                }
                                """
                ),
                dual(
                        "root media rule",
                        """
                                {{1}}@media screen
                                  {{2}}foo
                                    {{3}}bar: baz
                                """,
                        """
                                {{1}}@media screen {
                                  {{2}}foo {
                                    {{3}}bar: baz;
                                  }
                                }
                                """,
                        """
                                {{1}}@media screen {
                                  {{2}}foo {
                                    {{3}}bar: baz;
                                  }
                                }
                                """
                ),
                dual(
                        "media rule within style rule",
                        """
                                {{1}}foo
                                  {{2}}@media screen
                                    {{3}}bar: baz
                                """,
                        """
                                {{1}}foo {
                                  {{2}}@media screen {
                                    {{3}}bar: baz;
                                  }
                                }
                                """,
                        """
                                {{2}}@media screen {
                                  {{1}}foo {
                                    {{3}}bar: baz;
                                  }
                                }
                                """
                ),
                dual(
                        "root supports rule",
                        """
                                {{1}}@supports (display: grid)
                                  {{2}}foo
                                    {{3}}bar: baz
                                """,
                        """
                                {{1}}@supports (display: grid) {
                                  {{2}}foo {
                                    {{3}}bar: baz;
                                  }
                                }
                                """,
                        """
                                {{1}}@supports (display: grid) {
                                  {{2}}foo {
                                    {{3}}bar: baz;
                                  }
                                }
                                """
                ),
                dual(
                        "supports rule within style rule",
                        """
                                {{1}}foo
                                  {{2}}@supports (display: grid)
                                    {{3}}bar: baz
                                """,
                        """
                                {{1}}foo {
                                  {{2}}@supports (display: grid) {
                                    {{3}}bar: baz;
                                  }
                                }
                                """,
                        """
                                {{2}}@supports (display: grid) {
                                  {{1}}foo {
                                    {{3}}bar: baz;
                                  }
                                }
                                """
                ),
                scss(
                        "value from variable declaration",
                        """
                                $var: {{1}}value;

                                {{2}}a {
                                  {{3}}b: $var;
                                }
                                """,
                        """
                                {{2}}a {
                                  {{3}}b: {{1}}value;
                                }
                                """
                ),
                scss(
                        "value from each rule",
                        """
                                @each $var in {{1}}1 2 {
                                  {{2}}a {
                                    {{3}}b: $var;
                                  }
                                }
                                """,
                        """
                                {{2}}a {
                                  {{3}}b: {{1}}1;
                                }

                                {{2}}a {
                                  {{3}}b: {{1}}2;
                                }
                                """
                ),
                scss(
                        "value from for rule",
                        """
                                @for $var from {{1}}1 through 2 {
                                  {{2}}a {
                                    {{3}}b: $var;
                                  }
                                }
                                """,
                        """
                                {{2}}a {
                                  {{3}}b: {{1}}1;
                                }

                                {{2}}a {
                                  {{3}}b: {{1}}2;
                                }
                                """
                ),
                scss(
                        "mixin default argument value",
                        """
                                @mixin foo($var: {{1}}1) {
                                  {{2}}b: $var;
                                }

                                {{3}}a {
                                  @include foo();
                                }
                                """,
                        """
                                {{3}}a {
                                  {{2}}b: {{1}}1;
                                }
                                """
                ),
                scss(
                        "mixin positional argument value",
                        """
                                @mixin foo($var) {
                                  {{1}}b: $var;
                                }

                                {{2}}a {
                                  @include foo({{3}}1);
                                }
                                """,
                        """
                                {{2}}a {
                                  {{1}}b: {{3}}1;
                                }
                                """
                ),
                scss(
                        "mixin named argument value",
                        """
                                @mixin foo($var) {
                                  {{1}}b: $var;
                                }

                                {{2}}a {
                                  @include foo($var: {{3}}1);
                                }
                                """,
                        """
                                {{2}}a {
                                  {{1}}b: {{3}}1;
                                }
                                """
                ),
                scss(
                        "mixin arglist argument value",
                        """
                                @mixin foo($var) {
                                  {{1}}b: $var;
                                }

                                {{2}}a {
                                  @include foo({{3}}(1,)...);
                                }
                                """,
                        """
                                {{2}}a {
                                  {{1}}b: {{3}}1;
                                }
                                """
                ),
                scss(
                        "value through variable rename",
                        """
                                $var1: {{1}}value;
                                $var2: $var1;

                                {{2}}a {
                                  {{3}}b: $var2;
                                }
                                """,
                        """
                                {{2}}a {
                                  {{3}}b: {{1}}value;
                                }
                                """
                ),
                scss(
                        "each rule from variable",
                        """
                                $list: {{1}}1 2;

                                @each $var in $list {
                                  {{2}}a {
                                    {{3}}b: $var;
                                  }
                                }
                                """,
                        """
                                {{2}}a {
                                  {{3}}b: {{1}}1;
                                }

                                {{2}}a {
                                  {{3}}b: {{1}}2;
                                }
                                """
                ),
                scss(
                        "for rule from variables",
                        """
                                $start: {{1}}1;
                                $end: 2;

                                @for $var from $start through $end {
                                  {{2}}a {
                                    {{3}}b: $var;
                                  }
                                }
                                """,
                        """
                                {{2}}a {
                                  {{3}}b: {{1}}1;
                                }

                                {{2}}a {
                                  {{3}}b: {{1}}2;
                                }
                                """
                ),
                scss(
                        "module configuration from variable",
                        """
                                $var1: {{1}}new value;
                                @use 'other' with ($var2: $var1);

                                {{2}}a {
                                  {{3}}b: other.$var2;
                                }
                                """,
                        """
                                {{2}}a {
                                  {{3}}b: {{1}}new value;
                                }
                                """,
                        configurationImporter()
                ),
                scss(
                        "mixin default from variable",
                        """
                                $original: {{1}}1;

                                @mixin foo($var: $original) {
                                  {{2}}b: $var;
                                }

                                {{3}}a {
                                  @include foo();
                                }
                                """,
                        """
                                {{3}}a {
                                  {{2}}b: {{1}}1;
                                }
                                """
                ),
                scss(
                        "mixin positional argument from variable",
                        """
                                $original: {{1}}1;

                                @mixin foo($var) {
                                  {{2}}b: $var;
                                }

                                {{3}}a {
                                  @include foo($original);
                                }
                                """,
                        """
                                {{3}}a {
                                  {{2}}b: {{1}}1;
                                }
                                """
                ),
                scss(
                        "mixin named argument from variable",
                        """
                                $original: {{1}}1;

                                @mixin foo($var) {
                                  {{2}}b: $var;
                                }

                                {{3}}a {
                                  @include foo($var: $original);
                                }
                                """,
                        """
                                {{3}}a {
                                  {{2}}b: {{1}}1;
                                }
                                """
                ),
                scss(
                        "mixin arglist argument from variable",
                        """
                                $original: {{1}}1;

                                @mixin foo($var) {
                                  {{2}}b: $var;
                                }

                                {{3}}a {
                                  @include foo($original...);
                                }
                                """,
                        """
                                {{3}}a {
                                  {{2}}b: {{1}}1;
                                }
                                """
                ),
                dual(
                        "Unicode expanded stylesheet",
                        """
                                {{1}}föö
                                  {{2}}bär: bäz
                                """,
                        """
                                {{1}}föö {
                                  {{2}}bär: bäz;
                                }
                                """,
                        """
                                @charset "UTF-8";
                                {{1}}föö {
                                  {{2}}bär: bäz;
                                }
                                """
                ),
                new MappingCase(
                        "Unicode compressed stylesheet",
                        """
                                {{1}}föö
                                  {{2}}bär: bäz
                                """,
                        """
                                {{1}}föö {
                                  {{2}}bär: bäz;
                                }
                                """,
                        "\uFEFF{{1}}föö{{{2}}bär:bäz}",
                        OutputStyle.COMPRESSED,
                        null
                )
        );
    }

    /// Creates a case that runs for indented Sass and SCSS.
    ///
    /// @param name the fixture name
    /// @param sass the annotated indented source
    /// @param scss the annotated SCSS source
    /// @param css the annotated expected CSS
    /// @return the mapping case
    private static MappingCase dual(
            String name,
            String sass,
            String scss,
            String css
    ) {
        return new MappingCase(
                name,
                sass,
                scss,
                css,
                OutputStyle.EXPANDED,
                null
        );
    }

    /// Creates an expanded SCSS-only case.
    ///
    /// @param name the fixture name
    /// @param scss the annotated SCSS source
    /// @param css the annotated expected CSS
    /// @return the mapping case
    private static MappingCase scss(String name, String scss, String css) {
        return scss(name, scss, css, null);
    }

    /// Creates an expanded SCSS-only case with an importer.
    ///
    /// @param name the fixture name
    /// @param scss the annotated SCSS source
    /// @param css the annotated expected CSS
    /// @param importer the importer, or {@code null}
    /// @return the mapping case
    private static MappingCase scss(
            String name,
            String scss,
            String css,
            @Nullable SassImporter importer
    ) {
        return new MappingCase(
                name,
                null,
                scss,
                css,
                OutputStyle.EXPANDED,
                importer
        );
    }

    /// Creates the importer used by the module-configuration fixture.
    ///
    /// @return a self-contained importer for the `other` module
    private static SassImporter configurationImporter() {
        return new SassContentsImporter() {
            /// Canonicalizes every request into the test scheme.
            @Override
            public URI canonicalize(
                    URI url,
                    SassCanonicalizeContext context
            ) {
                return URI.create("test:" + url);
            }

            /// Loads the configurable test variable.
            @Override
            public SassImporterResult load(URI canonicalUrl) {
                return new SassImporterResult(
                        "$var2: default value !default;",
                        Syntax.SCSS,
                        canonicalUrl
                );
            }
        };
    }

    /// Runs every syntax represented by a mapping case.
    ///
    /// @param testCase the fixture to execute
    private static void assertCase(MappingCase testCase) throws Exception {
        if (testCase.sass() != null) {
            assertMapping(
                    testCase.sass(),
                    Syntax.SASS,
                    testCase.css(),
                    testCase.style(),
                    testCase.importer()
            );
        }
        assertMapping(
                testCase.scss(),
                Syntax.SCSS,
                testCase.css(),
                testCase.style(),
                testCase.importer()
        );
    }

    /// Compiles one annotated source and verifies every generated mapping.
    ///
    /// @param annotatedSource the source containing location markers
    /// @param syntax the input syntax
    /// @param annotatedCss the expected CSS containing location markers
    /// @param style the output style
    /// @param importer the optional importer
    private static void assertMapping(
            String annotatedSource,
            Syntax syntax,
            String annotatedCss,
            OutputStyle style,
            @Nullable SassImporter importer
    ) throws Exception {
        var source = extractLocations(annotatedSource);
        var expected = extractLocations(annotatedCss);
        var options = CompileOptions.DEFAULT
                .withSourceMap(true)
                .withImporters(
                        importer == null ? List.of() : List.of(importer)
                );
        var result = compileMappingFixture(source.text(), syntax, style, options);
        assertEquals(expected.text(), result.output(), syntax.toString());

        var sourceLocations = new LinkedHashMap<String, Location>();
        for (var location : source.locations()) {
            assertNull(
                    sourceLocations.put(location.name(), location.location()),
                    "duplicate source marker " + location.name()
            );
        }
        var targetNames = new HashSet<String>();
        for (var location : expected.locations()) {
            targetNames.add(location.name());
        }
        assertEquals(sourceLocations.keySet(), targetNames);

        var map = Objects.requireNonNull(result.sourceMap());
        var entries = decode(map);
        assertEquals(
                expected.locations().size(),
                entries.size(),
                syntax + " mappings: " + entries
        );
        for (var index = 0; index < entries.size(); index++) {
            var marker = expected.locations().get(index);
            var sourceLocation = Objects.requireNonNull(
                    sourceLocations.get(marker.name()),
                    marker.name()
            );
            var entry = entries.get(index);
            assertEquals(sourceLocation.line(), entry.sourceLine(), marker.name());
            assertEquals(sourceLocation.column(), entry.sourceColumn(), marker.name());
            assertEquals(marker.location().line(), entry.generatedLine(), marker.name());
            assertEquals(marker.location().column(), entry.generatedColumn(), marker.name());
        }
    }

    /// Compiles a source-map fixture and adds its primary source text to failures.
    ///
    /// @param source the marker-free source
    /// @param syntax the input syntax
    /// @param style the output style
    /// @param options the compile options
    /// @return the compilation result
    private static CompileResult<String> compileMappingFixture(
            String source,
            Syntax syntax,
            OutputStyle style,
            CompileOptions options
    ) throws Exception {
        try {
            return new SassCompiler().compile(
                    SassSource.fromString(source, syntax),
                    new CssTarget(style, true),
                    options
            );
        } catch (SassCompilationException failure) {
            @Nullable var span = failure.primaryDiagnostic().span();
            return fail(
                    syntax + " compilation failed at "
                            + (span == null ? "an unknown location" : '`' + span.text() + '`')
                            + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    /// Removes location markers and records their UTF-16 line and column.
    ///
    /// @param annotatedText the marked fixture text
    /// @return unmarked text and ordered marker locations
    private static AnnotatedText extractLocations(String annotatedText) {
        var input = reindent(annotatedText);
        var output = new StringBuilder(input.length());
        var locations = new ArrayList<NamedLocation>();
        var line = 0;
        var column = 0;
        for (var index = 0; index < input.length(); ) {
            if (input.startsWith("{{", index)
                    && (index + 2 >= input.length()
                    || input.charAt(index + 2) != '{')) {
                var end = input.indexOf("}}", index + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("Unterminated location marker");
                }
                var name = input.substring(index + 2, end);
                if (name.isEmpty() || name.indexOf('{') >= 0) {
                    throw new IllegalArgumentException("Invalid location marker");
                }
                locations.add(new NamedLocation(name, new Location(line, column)));
                index = end + 2;
                continue;
            }
            var character = input.charAt(index++);
            output.append(character);
            if (character == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new AnnotatedText(output.toString(), locations);
    }

    /// Removes common indentation and trailing ASCII whitespace from a fixture.
    ///
    /// @param value the fixture text
    /// @return normalized text
    private static String reindent(String value) {
        var normalized = trimAsciiRight(
                value.replace("\r\n", "\n").replace('\r', '\n')
        );
        var lines = normalized.split("\n", -1);
        var minimum = Integer.MAX_VALUE;
        for (var line : lines) {
            if (line.isBlank()) {
                continue;
            }
            var indentation = 0;
            while (indentation < line.length() && line.charAt(indentation) == ' ') {
                indentation++;
            }
            minimum = Math.min(minimum, indentation);
        }
        if (minimum == Integer.MAX_VALUE) {
            return "";
        }
        var result = new StringBuilder(normalized.length());
        for (var index = 0; index < lines.length; index++) {
            if (index > 0) {
                result.append('\n');
            }
            if (!lines[index].isBlank()) {
                result.append(lines[index].substring(minimum));
            }
        }
        return result.toString();
    }

    /// Removes trailing ASCII whitespace.
    ///
    /// @param value the input text
    /// @return text ending in a non-whitespace code unit or the empty string
    private static String trimAsciiRight(String value) {
        var end = value.length();
        while (end > 0 && isAsciiWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    /// Returns whether a character is ASCII whitespace.
    ///
    /// @param character the character to inspect
    /// @return whether it is an ASCII whitespace code unit
    private static boolean isAsciiWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\n'
                || character == '\r'
                || character == '\f';
    }

    /// Decodes absolute entries from a version-3 source map.
    ///
    /// @param map the source map
    /// @return entries in generated order
    private static @Unmodifiable List<DecodedEntry> decode(SourceMap map) {
        var mappings = JsonParser.parseString(map.json())
                .getAsJsonObject()
                .get("mappings")
                .getAsString();
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
        return List.copyOf(entries);
    }

    /// Decodes one Base64 VLQ value.
    ///
    /// @param mappings the mappings string
    /// @param index the starting index
    /// @return the decoded value and next index
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

    /// Decodes one Base64 digit.
    ///
    /// @param character the encoded digit
    /// @return its value from 0 through 63
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
        throw new IllegalArgumentException("Invalid Base64 digit: " + character);
    }

    /// Describes one annotated mapping fixture.
    ///
    /// @param name the fixture name
    /// @param sass annotated indented source, or {@code null}
    /// @param scss annotated SCSS source
    /// @param css annotated expected CSS
    /// @param style the output style
    /// @param importer the optional importer
    private record MappingCase(
            String name,
            @Nullable String sass,
            String scss,
            String css,
            OutputStyle style,
            @Nullable SassImporter importer
    ) {
    }

    /// Contains marker-free text and ordered named locations.
    ///
    /// @param text the marker-free text
    /// @param locations the marker locations
    private record AnnotatedText(
            String text,
            @Unmodifiable List<NamedLocation> locations
    ) {
        /// Creates an immutable annotated-text result.
        private AnnotatedText {
            locations = List.copyOf(locations);
        }
    }

    /// Associates a marker name with a location.
    ///
    /// @param name the marker name
    /// @param location its UTF-16 location
    private record NamedLocation(String name, Location location) {
    }

    /// Contains a zero-based line and UTF-16 column.
    ///
    /// @param line the zero-based line
    /// @param column the zero-based column
    private record Location(int line, int column) {
    }

    /// Contains one absolute decoded mapping entry.
    ///
    /// @param generatedLine the zero-based generated line
    /// @param generatedColumn the zero-based generated column
    /// @param sourceIndex the source-list index
    /// @param sourceLine the zero-based source line
    /// @param sourceColumn the zero-based source column
    private record DecodedEntry(
            int generatedLine,
            int generatedColumn,
            int sourceIndex,
            int sourceLine,
            int sourceColumn
    ) {
    }

    /// Contains one decoded VLQ value and the following string index.
    ///
    /// @param value the signed decoded value
    /// @param nextIndex the index after the encoded value
    private record VlqValue(int value, int nextIndex) {
    }
}
