// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies context-sensitive introspection functions exported by {@code sass:meta}.
@NotNullByDefault
final class MetaRuntimeQueryTest {
    /// Evaluates lexical, global, module, function, mixin, and module-variable queries.
    @Test
    void evaluatesRuntimeMetaQueriesWithoutLoadingStylesheets() throws Exception {
        var result = compile(
                """
                        @use "sass:map";
                        @use "sass:math" as math;
                        @use "sass:meta";

                        $root_value: root;
                        $null_value: null;

                        @function has-local-variable() {
                          $local_value: local;
                          @return meta.variable-exists("local_value");
                        }

                        @function has-global-local-variable() {
                          $local_value: local;
                          @return meta.global-variable-exists("local-value");
                        }

                        @function local_function() {
                          @return 1;
                        }

                        @mixin local_mixin() {}

                        .example {
                          local-variable: has-local-variable();
                          local-global: has-global-local-variable();
                          root-variable: meta.variable-exists("root_value");
                          root-global: meta.global-variable-exists("root-value");
                          null-global: meta.global-variable-exists("null_value");
                          module-variable: meta.global-variable-exists("pi", "math");
                          builtin-function: meta.function-exists("round");
                          local-function: meta.function-exists("local_function");
                          module-function: meta.function-exists("round", "math");
                          wrong-module-builtin: meta.function-exists("length", "math");
                          absent-function: meta.function-exists("not-a-sass-function");
                          empty-variable: meta.variable-exists("");
                          empty-global: meta.global-variable-exists("");
                          empty-function: meta.function-exists("");
                          empty-mixin: meta.mixin-exists("");
                          local-mixin: meta.mixin-exists("local_mixin");
                          absent-mixin: meta.mixin-exists("not-a-sass-mixin");
                          math-pi: map.get(meta.module-variables("math"), "pi");
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          local-variable: true;
                          local-global: false;
                          root-variable: true;
                          root-global: true;
                          null-global: true;
                          module-variable: true;
                          builtin-function: true;
                          local-function: true;
                          module-function: true;
                          wrong-module-builtin: false;
                          absent-function: false;
                          empty-variable: false;
                          empty-global: false;
                          empty-function: false;
                          empty-mixin: false;
                          local-mixin: true;
                          absent-mixin: false;
                          math-pi: 3.1415926536;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Finds root bindings re-exported by a module loaded with {@code as *}.
    @Test
    void findsGlobalModuleVariablesForGlobalMetaQueries() throws Exception {
        var result = compile(
                """
                        @use "sass:math" as *;
                        @use "sass:meta";

                        .example {
                          pi-exists: meta.global-variable-exists("pi");
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          pi-exists: true;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Rejects invalid names and missing explicitly named modules.
    @Test
    void rejectsInvalidRuntimeMetaQueryArguments() {
        assertEquals(
                "$name: 1 is not a string.",
                failure("@use \"sass:meta\"; .a { value: meta.variable-exists(1); }")
        );
        assertEquals(
                "$module: 1 is not a string.",
                failure(
                        "@use \"sass:meta\"; .a { value: meta.global-variable-exists(\"x\", 1); }"
                )
        );
        assertEquals(
                "There is no module with the namespace \"missing\".",
                failure("@use \"sass:meta\"; .a { value: meta.module-variables(\"missing\"); }")
        );
        assertEquals(
                "There is no module with the namespace \"\".",
                failure("@use \"sass:meta\"; .a { value: meta.module-variables(\"\"); }")
        );
    }

    /// Compiles one SCSS string source with the expanded CSS target.
    ///
    /// @param source the source text to compile
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static CompileResult<String> compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
    }

    /// Compiles one source that is expected to fail.
    ///
    /// @param source the source text to compile
    /// @return the primary compilation message
    private static String failure(String source) {
        return assertThrows(
                SassCompilationException.class,
                () -> compile(source)
        ).getMessage();
    }
}