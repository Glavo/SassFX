// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies public Java custom function registration and value bridging.
@NotNullByDefault
final class SassCustomFunctionTest {
    /// Binds positional, keyword, default, and rest arguments in declaration order.
    @Test
    void bindsDeclaredAndRestArguments() throws Exception {
        var function = new SassCustomFunction(
                "collect($left, $right: $left * 2, $rest...)",
                arguments -> {
                    assertEquals(3, arguments.size());
                    assertEquals(3.0, arguments.get(0).numberValue());
                    assertEquals(6.0, arguments.get(1).numberValue());
                    assertEquals(SassValueType.ARGUMENT_LIST, arguments.get(2).type());
                    assertTrue(arguments.get(2).asList().isEmpty());
                    var keywords = arguments.get(2).keywords();
                    return SassValue.list(
                            List.of(
                                    arguments.get(0),
                                    arguments.get(1),
                                    keywords.get("tone")
                            ),
                            SassListSeparator.SPACE,
                            false
                    );
                }
        );

        assertEquals(
                """
                        .result {
                          value: 3 6 blue;
                        }""",
                compile(
                        ".result { value: collect(3, $tone: blue); }",
                        Syntax.SCSS,
                        List.of(function)
                )
        );
    }

    /// Rejects unobserved leftover keywords after the callback returns.
    @Test
    void rejectsUnusedRestKeywords() {
        var function = new SassCustomFunction(
                "ignore($rest...)",
                arguments -> SassValue.nullValue()
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        ".result { value: ignore($unknown: 1); }",
                        Syntax.SCSS,
                        List.of(function)
                )
        );

        assertEquals(
                "No parameter named $unknown.",
                failure.primaryDiagnostic().message()
        );
    }

    /// Applies Sass callable precedence and exposes custom functions through {@code sass:meta}.
    @Test
    void preservesCallablePrecedenceAndMetaVisibility() throws Exception {
        var functions = List.of(
                new SassCustomFunction(
                        "sqrt($value)",
                        arguments -> SassValue.number(99)
                ),
                new SassCustomFunction(
                        "host($value)",
                        arguments -> SassValue.number(7)
                ),
                new SassCustomFunction(
                        "java_only($value)",
                        arguments -> SassValue.number(
                                arguments.get(0).numberValue() + 1
                        )
                )
        );

        assertEquals(
                """
                        .result {
                          builtin: 3;
                          stylesheet: 8;
                          calculation: 3px;
                          custom: 5;
                          exists: true;
                          dynamic: 6;
                        }""",
                compile(
                        """
                                @use "sass:meta";

                                @function host($value) {
                                  @return 8;
                                }

                                .result {
                                  builtin: sqrt(9);
                                  stylesheet: host(1);
                                  calculation: calc(1px + 2px);
                                  custom: java-only(4);
                                  exists: meta.function-exists("java_only");
                                  dynamic: meta.call(meta.get-function("java-only"), 5);
                                }
                                """,
                        Syntax.SCSS,
                        functions
                )
        );
    }

    /// Uses the last duplicate custom definition and ignores custom functions in plain CSS.
    @Test
    void handlesDuplicateDefinitionsAndPlainCss() throws Exception {
        var functions = List.of(
                new SassCustomFunction(
                        "java-only($value)",
                        arguments -> SassValue.number(1)
                ),
                new SassCustomFunction(
                        "java_only($value)",
                        arguments -> SassValue.number(2)
                )
        );

        assertEquals(
                """
                        .scss {
                          value: 2;
                        }""",
                compile(
                        ".scss { value: java-only(0); }",
                        Syntax.SCSS,
                        functions
                )
        );
        assertEquals(
                """
                        .css {
                          value: java-only(0);
                        }""",
                compile(
                        ".css { value: java-only(0); }",
                        Syntax.CSS,
                        functions
                )
        );
    }

    /// Makes custom functions available while evaluating loaded Sass modules.
    @Test
    void exposesFunctionsToDependencyModules(@TempDir Path directory)
            throws Exception {
        Files.writeString(
                directory.resolve("_dependency.scss"),
                ".dependency { value: java-only(4); }"
        );
        var function = new SassCustomFunction(
                "java-only($value)",
                arguments -> SassValue.number(
                        arguments.get(0).numberValue() + 1
                )
        );
        var options = new CompileOptions(
                false,
                List.of(directory),
                null,
                List.of(),
                List.of(function)
        );

        assertEquals(
                """
                        .dependency {
                          value: 5;
                        }""",
                compile("@use \"dependency\";", options)
        );
    }

    /// Preserves opaque Sass values and constructs scalar, list, and map values.
    @Test
    void bridgesAllValueKindsWithoutLoss() throws Exception {
        var function = new SassCustomFunction(
                "identity($value)",
                arguments -> {
                    assertEquals(SassValueType.COLOR, arguments.get(0).type());
                    return arguments.get(0);
                }
        );
        var mapContents = new LinkedHashMap<SassValue, SassValue>();
        mapContents.put(SassValue.string("key", false), SassValue.number(2, "px"));
        var map = SassValue.map(mapContents);

        assertEquals(SassValueType.MAP, map.type());
        assertEquals(1, map.mapContents().size());
        assertEquals(
                SassListSeparator.COMMA,
                SassValue.list(
                        List.of(SassValue.booleanValue(true), SassValue.nullValue()),
                        SassListSeparator.COMMA,
                        true
                ).separator()
        );
        assertEquals(
                """
                        .result {
                          color: red;
                        }""",
                compile(
                        ".result { color: identity(red); }",
                        Syntax.SCSS,
                        List.of(function)
                )
        );
    }

    /// Associates callback failures with the Sass call and preserves the Java cause.
    @Test
    void preservesCallbackFailureCauseAndSpan() {
        var function = new SassCustomFunction(
                "fail()",
                arguments -> {
                    throw new IOException("host failure");
                }
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        ".result { value: fail(); }",
                        Syntax.SCSS,
                        List.of(function)
                )
        );

        assertEquals("host failure", failure.primaryDiagnostic().message());
        assertNotNull(failure.primaryDiagnostic().span());
        assertInstanceOf(IOException.class, failure.getCause().getCause());
    }

    /// Rejects a Java {@code null} callback result as a source-associated failure.
    @Test
    void rejectsNullCallbackResult() {
        var function = new SassCustomFunction(
                "invalid()",
                arguments -> null
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> compile(
                        ".result { value: invalid(); }",
                        Syntax.SCSS,
                        List.of(function)
                )
        );

        assertTrue(
                failure.primaryDiagnostic().message().contains(
                        "null is not a SassValue"
                )
        );
        assertInstanceOf(
                IllegalStateException.class,
                failure.getCause().getCause()
        );
    }

    /// Rejects malformed signatures before evaluating the root stylesheet.
    @Test
    void rejectsInvalidSignatures() {
        var function = new SassCustomFunction(
                "broken($value",
                arguments -> SassValue.nullValue()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> compile("", Syntax.SCSS, List.of(function))
        );
    }

    /// Snapshots function definitions in compile options.
    @Test
    void snapshotsFunctionOptions() {
        var function = new SassCustomFunction(
                "value()",
                arguments -> SassValue.number(1)
        );
        var functions = new ArrayList<SassCustomFunction>(List.of(function));
        var options = new CompileOptions(
                false,
                List.of(),
                null,
                List.of(),
                functions
        );
        functions.clear();

        assertEquals(List.of(function), options.functions());
        assertThrows(UnsupportedOperationException.class, options.functions()::clear);
    }

    /// Allows one thread-safe callback instance to serve concurrent compilations.
    @Test
    void supportsConcurrentCompilations() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var function = new SassCustomFunction(
                "concurrent($value)",
                arguments -> {
                    entered.countDown();
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    release.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return arguments.get(0);
                }
        );
        var options = options(List.of(function));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(
                    () -> compile(".a { value: concurrent(1); }", options)
            );
            var second = executor.submit(
                    () -> compile(".b { value: concurrent(2); }", options)
            );

            assertEquals(".a {\n  value: 1;\n}", first.get(10, TimeUnit.SECONDS));
            assertEquals(".b {\n  value: 2;\n}", second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    /// Compiles source with the supplied syntax and custom functions.
    private static String compile(
            String source,
            Syntax syntax,
            List<SassCustomFunction> functions
    ) throws IOException, SassCompilationException {
        return compile(source, syntax, options(functions));
    }

    /// Compiles SCSS with an already-created immutable option set.
    private static String compile(
            String source,
            CompileOptions options
    ) throws IOException, SassCompilationException {
        return compile(source, Syntax.SCSS, options);
    }

    /// Compiles source with an already-created immutable option set.
    private static String compile(
            String source,
            Syntax syntax,
            CompileOptions options
    ) throws IOException, SassCompilationException {
        return new SassCompiler().compile(
                SassSource.fromString(
                        source,
                        syntax,
                        URI.create("memory:///custom-function.scss")
                ),
                CssTarget.DEFAULT,
                options
        ).output();
    }

    /// Creates compile options containing only custom functions.
    private static CompileOptions options(List<SassCustomFunction> functions) {
        return new CompileOptions(
                false,
                List.of(),
                null,
                List.of(),
                functions
        );
    }
}
