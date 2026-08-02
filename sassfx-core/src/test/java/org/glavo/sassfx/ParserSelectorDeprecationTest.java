// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies parser, module-configuration, and selector deprecations.
@NotNullByDefault
final class ParserSelectorDeprecationTest {
    /// Reports deprecated Mozilla document conditions but keeps empty prefixes.
    @Test
    void reportsMozDocumentConditions() throws Exception {
        var result = compile(
                """
                        @-moz-document url-prefix(foo) {}
                        @-moz-document regexp("example") {}
                        @-moz-document url-prefix() {}
                        @-moz-document url-prefix("") {}
                        """,
                List.of(),
                verboseDiagnostics()
        );

        var warnings = warnings(result, "moz-document");
        assertEquals(2, warnings.size());
        assertTrue(warnings.stream().allMatch(diagnostic ->
                diagnostic.message().equals(
                        """
                                @-moz-document is deprecated and support will be removed in Dart Sass 2.0.0.

                                For details, see https://sass-lang.com/d/moz-document."""
                )
        ));
        assertTrue(warnings.get(0).span() != null);
        assertEquals(
                "@-moz-document url-prefix(foo) {}",
                warnings.get(0).span().text()
        );
    }

    /// Reports private configuration in directives and {@code meta.load-css()}.
    @Test
    void reportsPrivateModuleConfiguration(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_use-config.scss"),
                "$-value: 0 !default;"
        );
        Files.writeString(
                directory.resolve("_forward-config.scss"),
                "$-value: 0 !default;"
        );
        Files.writeString(
                directory.resolve("_load-config.scss"),
                "$-value: 0 !default; .loaded { value: $-value; }"
        );

        var result = compile(
                """
                        @use "sass:meta";
                        @use "use-config" with ($-value: 1);
                        @forward "forward-config" with ($-value: 2);
                        @include meta.load-css(
                          "load-config",
                          $with: ("-value": 3)
                        );
                        """,
                List.of(directory),
                verboseDiagnostics()
        );

        var warnings = warnings(result, "with-private");
        assertEquals(3, warnings.size());
        assertEquals(
                """
                        Configuring private variables is deprecated.
                        This will be an error in Dart Sass 2.0.0.""",
                warnings.get(0).message()
        );
        assertEquals("$-value", warnings.get(0).span().text());
        assertEquals("$-value", warnings.get(1).span().text());
        assertEquals(
                """
                        Configuring private variables (such as $-value) is deprecated.
                        This will be an error in Dart Sass 2.0.0.""",
                warnings.get(2).message()
        );
        assertTrue(result.output().contains("value: 3;"));
    }

    /// Distinguishes positional and named arguments after a rest argument.
    @Test
    void reportsMisplacedRestArguments() throws Exception {
        var result = compile(
                """
                        @use "sass:meta";
                        $rest: (1,);
                        @function collect($args...) {
                          $_: meta.keywords($args);
                          @return 0;
                        }
                        .result {
                          positional: collect($rest..., 2);
                          named: collect($rest..., $name: 2);
                        }
                        """,
                List.of(),
                verboseDiagnostics()
        );

        var warnings = warnings(result, "misplaced-rest");
        assertEquals(2, warnings.size());
        assertEquals(
                """
                        Positional arguments must come before rest arguments.
                        This will be an error in Dart Sass 2.0.0.""",
                warnings.get(0).message()
        );
        assertEquals("2", warnings.get(0).span().text());
        assertEquals(
                """
                        Named arguments must come before rest arguments.
                        This will be an error in Dart Sass 2.0.0.""",
                warnings.get(1).message()
        );
        assertEquals("$name: 2", warnings.get(1).span().text());
    }

    /// Reports adjacent compounds in style and nested functional selectors.
    @Test
    void reportsAdjacentCompounds() throws Exception {
        var result = compile(
                """
                        @use "sass:meta";
                        @use "sass:selector";
                        .a[b]c { first: value; }
                        :is([data]button) { second: value; }
                        .function {
                          third: meta.inspect(selector.parse("[role]button"));
                        }
                        """,
                List.of(),
                verboseDiagnostics()
        );

        var warnings = warnings(result, "adjacent-compounds");
        assertEquals(3, warnings.size());
        assertEquals(
                """
                        Adjacent compound selectors must be separated by whitespace. This will be an error in Dart Sass 2.0.0. Suggestion:

                        .a[b] c

                        More info: https://sass-lang.com/d/adjacent-compounds""",
                warnings.get(0).message()
        );
        assertEquals(".a[b]c", warnings.get(0).span().text());
        assertEquals(
                """
                        Adjacent compound selectors must be separated by whitespace. This will be an error in Dart Sass 2.0.0. Suggestion:

                        [data] button

                        More info: https://sass-lang.com/d/adjacent-compounds""",
                warnings.get(1).message()
        );
        assertEquals(
                """
                        Adjacent compound selectors must be separated by whitespace. This will be an error in Dart Sass 2.0.0. Suggestion:

                        [role] button

                        More info: https://sass-lang.com/d/adjacent-compounds""",
                warnings.get(2).message()
        );
    }

    /// Reports useless, nesting-only, and leading bogus combinators.
    @Test
    void reportsBogusStyleRuleCombinators() throws Exception {
        var result = compile(
                """
                        a > > a { first: value; }
                        a > { second: value; }
                        > a { third: value; }
                        """,
                List.of(),
                verboseDiagnostics()
        );

        assertEquals(
                List.of(
                        """
                                The selector "a > > a" is invalid CSS. It will be omitted from the generated CSS.
                                This will be an error in Dart Sass 2.0.0.

                                More info: https://sass-lang.com/d/bogus-combinators""",
                        """
                                The selector "a >" is only valid for nesting and shouldn't
                                have children other than style rules. It will be omitted from the generated CSS.
                                This will be an error in Dart Sass 2.0.0.

                                More info: https://sass-lang.com/d/bogus-combinators""",
                        """
                                The selector "> a" is invalid CSS.
                                This will be an error in Dart Sass 2.0.0.

                                More info: https://sass-lang.com/d/bogus-combinators"""
                ),
                warnings(result, "bogus-combinators").stream()
                        .map(Diagnostic::message)
                        .toList()
        );
    }

    /// Reports bogus selector-function operands and {@code @extend} sources.
    @Test
    void reportsBogusOperationalSelectors() throws Exception {
        var result = compile(
                """
                        @use "sass:selector";
                        .function {
                          result: selector.is-superselector("> a", "a");
                        }
                        a > {
                          @extend b !optional;
                          value: retained;
                        }
                        """,
                List.of(),
                verboseDiagnostics()
        );

        var messages = warnings(result, "bogus-combinators").stream()
                .map(Diagnostic::message)
                .toList();
        assertTrue(messages.contains(
                """
                        $super: > a is not valid CSS.
                        This will be an error in Dart Sass 2.0.0.

                        More info: https://sass-lang.com/d/bogus-combinators"""
        ));
        assertTrue(messages.contains(
                """
                        The selector "a >" is invalid CSS and shouldn't be an extender.
                        This will be an error in Dart Sass 2.0.0.

                        More info: https://sass-lang.com/d/bogus-combinators"""
        ));
    }

    /// Applies silence and fatal policy to newly connected parser diagnostics.
    @Test
    void appliesDiagnosticPolicy() throws Exception {
        var source = "@-moz-document url-prefix(example) {}";
        var silenced = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(SassDeprecation.MOZ_DOCUMENT),
                Set.of(),
                Set.of()
        );
        assertTrue(warnings(
                compile(source, List.of(), silenced),
                "moz-document"
        ).isEmpty());

        var fatal = new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(SassDeprecation.MOZ_DOCUMENT),
                Set.of()
        );
        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(source, List.of(), fatal)
        );
        assertEquals("moz-document", failure.primaryDiagnostic().code());
    }

    /// Returns diagnostics carrying one stable identifier.
    ///
    /// @param result the completed compilation
    /// @param code the stable deprecation identifier
    /// @return immutable matching diagnostics in delivery order
    private static @Unmodifiable List<Diagnostic> warnings(
            CompileResult<String> result,
            String code
    ) {
        return result.diagnostics().stream()
                .filter(diagnostic -> code.equals(diagnostic.code()))
                .toList();
    }

    /// Creates verbose diagnostic options without logger side effects.
    ///
    /// @return options that retain every deprecation occurrence
    private static SassDiagnosticOptions verboseDiagnostics() {
        return new SassDiagnosticOptions(
                SassLogger.NO_OP,
                false,
                true,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    /// Compiles SCSS with explicit load paths and diagnostic processing.
    ///
    /// @param source SCSS source text
    /// @param loadPaths filesystem load paths
    /// @param diagnostics diagnostic processing configuration
    /// @return the completed CSS result
    /// @throws IOException if an imported source cannot be read
    /// @throws SassCompilationException if compilation fails
    private static CompileResult<String> compile(
            String source,
            List<Path> loadPaths,
            SassDiagnosticOptions diagnostics
    ) throws IOException, SassCompilationException {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT,
                CompileOptions.DEFAULT
                        .withLoadPaths(loadPaths)
                        .withDiagnosticOptions(diagnostics)
        );
    }
}
