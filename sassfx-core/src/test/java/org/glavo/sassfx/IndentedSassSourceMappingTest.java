// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies that indented Sass diagnostics retain original source locations.
@NotNullByDefault
final class IndentedSassSourceMappingTest {
    /// Reports evaluation and serialization failures against exact original text.
    @Test
    void reportsOriginalExpressionSpans() {
        var undefined = compileFailure(
                """
                        $defined: red
                        .item
                          color: $missing
                        """
        );
        assertSpan(undefined, "$missing", 2, 9);

        var invalidCss = compileFailure(
                """
                        .item
                          color: (
                            a: 1,
                            b: 2
                          )
                        """
        );
        assertSpan(
                invalidCss,
                """
                        (
                            a: 1,
                            b: 2
                          )""",
                1,
                9
        );
    }

    /// Reports parser failures against exact tokens in multiline Sass syntax.
    @Test
    void reportsOriginalParserSpans() {
        var failure = compileFailure(
                """
                        @use "theme" with (
                          $color: red,
                          $color: blue
                        )
                        """
        );

        assertEquals("The same variable may only be configured once.", failure.getMessage());
        assertSpan(failure, "$color", 2, 2);
    }

    /// Maps shorthand mixin bodies and call-site traces to their Sass spelling.
    @Test
    void reportsOriginalShorthandSpans() {
        var failure = compileFailure(
                """
                        =accent($value)
                          color: $missing
                        .item
                          +accent(red)
                        """
        );
        assertSpan(failure, "$missing", 1, 9);
    }

    /// Maps a generated quoted import URL back to the bare Sass URL token.
    @Test
    void mapsBareImportDeprecation(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_tokens.scss"), "$color: red;");
        var root = directory.resolve("root.sass");
        Files.writeString(
                root,
                """
                        @import tokens
                        .item
                          color: $color
                        """
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(root),
                CssTarget.DEFAULT
        );
        var diagnostic = result.diagnostics().stream()
                .filter(item -> "import".equals(item.code()))
                .findFirst()
                .orElseThrow();
        var span = Objects.requireNonNull(diagnostic.span(), "import diagnostic span");
        assertEquals("tokens", span.text());
        assertEquals(0, span.start().line());
        assertEquals(8, span.start().column());
    }

    /// Retains the imported Sass file URL and expression coordinates on failure.
    @Test
    void mapsImportedSassFailures(@TempDir Path directory) throws Exception {
        var imported = directory.resolve("_broken.sass");
        Files.writeString(
                imported,
                """
                        .item
                          color: $missing
                        """
        );
        var root = directory.resolve("root.scss");
        Files.writeString(root, "@import \"broken\";");

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(root),
                        CssTarget.DEFAULT
                )
        );
        var span = Objects.requireNonNull(
                failure.primaryDiagnostic().span(),
                "imported failure span"
        );
        assertEquals(imported.toRealPath().toUri(), span.url());
        assertEquals("$missing", span.text());
        assertEquals(1, span.start().line());
        assertEquals(9, span.start().column());
    }

    /// Compiles one indented source and returns its checked failure.
    private static SassCompilationException compileFailure(String source) {
        return assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(source, Syntax.SASS),
                        CssTarget.DEFAULT
                )
        );
    }

    /// Verifies the primary diagnostic text and starting coordinates.
    private static void assertSpan(
            SassCompilationException failure,
            String text,
            int line,
            int column
    ) {
        var span = Objects.requireNonNull(
                failure.primaryDiagnostic().span(),
                "primary diagnostic span"
        );
        assertEquals(text, span.text());
        assertEquals(line, span.start().line());
        assertEquals(column, span.start().column());
    }
}
