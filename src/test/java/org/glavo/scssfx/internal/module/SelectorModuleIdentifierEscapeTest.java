// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies selector module functions accept escaped CSS identifiers.
@NotNullByDefault
final class SelectorModuleIdentifierEscapeTest {
    /// Verifies escaped selector strings retain their semantic identifier values.
    @Test
    void evaluatesEscapedIdentifiers() throws Exception {
        var result = compile(
                """
                        @use "sass:selector";

                        $parsed: selector.parse(".\\\\66 oo");
                        $unified: selector.unify(".\\\\66 oo", ".selected");

                        .example {
                          parsed: $parsed;
                          unified: $unified;
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          parsed: .foo;
                          unified: .foo.selected;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Compiles one SCSS string source with the expanded CSS target.
    ///
    /// @param source the source text
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static CompileResult<String> compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
    }
}
