// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies first-class function values and dynamic calls exported by {@code sass:meta}.
@NotNullByDefault
final class MetaFunctionValueTest {
    /// Resolves user, module, and plain-CSS function values and invokes them with preserved arguments.
    @Test
    void resolvesAndCallsFunctionValues() throws Exception {
        var result = compile(
                """
                        @use "sass:map";
                        @use "sass:math";
                        @use "sass:meta";

                        $offset: 4;

                        @function sum-value($value, $factor: 2) {
                          @return $value * $factor + $offset;
                        }

                        @function forward($args...) {
                          @return meta.call(meta.get-function("sum_value"), $args...);
                        }

                        $local: meta.get-function("sum_value");
                        $module: meta.get-function("round", $module: "math");
                        $css: meta.get-function("var", $css: true);
                        $functions: meta.module-functions("math");

                        .example {
                          type: meta.type-of($local);
                          inspected: meta.inspect($local);
                          same: $local == meta.get-function("sum-value");
                          direct: meta.call($local, 3);
                          keyword: meta.call($local, $value: 3, $factor: 4);
                          forwarded: forward($value: 3, $factor: 3);
                          module: meta.call($module, -1.5);
                          module-map: meta.call(map.get($functions, "round"), -1.5);
                          css: meta.call($css, unquote("--theme"), 10px);
                          legacy: meta.call("sum-value", 3);
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          type: function;
                          inspected: get-function("sum-value");
                          same: true;
                          direct: 10;
                          keyword: 16;
                          forwarded: 13;
                          module: -2;
                          module-map: -2;
                          css: var(--theme, 10px);
                          legacy: 10;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
        assertEquals(1, result.diagnostics().size());
        assertEquals(DiagnosticSeverity.DEPRECATION, result.diagnostics().get(0).severity());
        assertEquals("call-string", result.diagnostics().get(0).code());
    }

    /// Rejects unresolved, incompatible, non-function, and CSS-serialized function values.
    @Test
    void rejectsInvalidFunctionValueOperations() {
        assertEquals(
                "Function not found: \"missing\"",
                failure("@use \"sass:meta\"; a { value: meta.get-function(\"missing\"); }")
        );
        assertEquals(
                "$css and $module may not both be passed at once.",
                failure(
                        "@use \"sass:math\"; @use \"sass:meta\"; "
                                + "a { value: meta.get-function(\"var\", $css: true, $module: \"math\"); }"
                )
        );
        assertEquals(
                "$function: 1 is not a function reference.",
                failure("@use \"sass:meta\"; a { value: meta.call(1); }")
        );
        assertEquals(
                "get-function(\"sum-value\") isn't a valid CSS value.",
                failure(
                        "@use \"sass:meta\"; @function sum-value() { @return 1; } "
                                + "a { value: meta.get-function(\"sum-value\"); }"
                )
        );
    }

    /// Compiles one SCSS string source with the expanded CSS target.
    ///
    /// @param source the SCSS source text
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static CompileResult<String> compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
    }

    /// Compiles one source expected to fail and returns its primary diagnostic message.
    ///
    /// @param source the SCSS source text
    /// @return the primary evaluation or serialization failure message
    private static String failure(String source) {
        return assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(source, Syntax.SCSS),
                        CssTarget.DEFAULT
                )
        ).getMessage();
    }
}