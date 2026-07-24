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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies first-class mixin values and dynamic inclusion exported by {@code sass:meta}.
@NotNullByDefault
final class MetaMixinValueTest {
    /// Resolves, compares, and dynamically includes local mixin values with content parameters.
    @Test
    void resolvesAppliesAndForwardsMixinValues() throws Exception {
        var result = compile(
                """
                        @use "sass:map";
                        @use "sass:meta";

                        @mixin local-style($value, $factor: 3) {
                          height: $value * $factor;
                        }

                        @mixin accepts {
                          has-content: meta.content-exists();
                          @if meta.content-exists() {
                            @content(6, $label: content);
                          }
                        }

                        @mixin absent {
                          inner-content: meta.content-exists();
                        }

                        @mixin nested {
                          outer-content: meta.content-exists();
                          @include absent;
                          @content;
                        }

                        @mixin forward($mixin, $args...) {
                          @include meta.apply($mixin, $args...);
                        }

                        $mixin: meta.get-mixin("local_style");
                        $mixins: meta.module-mixins("meta");
                        $map: ($mixin: value);

                        .example {
                          type: meta.type-of($mixin);
                          inspected: meta.inspect($mixin);
                          same: $mixin == meta.get-mixin("local-style");
                          mapped: map.get($map, meta.get-mixin("local-style"));
                          local-accepts: meta.accepts-content($mixin);
                          target-accepts: meta.accepts-content(meta.get-mixin("accepts"));
                          apply-accepts: meta.accepts-content(meta.get-mixin("apply", "meta"));
                          meta-apply: map.has-key($mixins, "apply");
                          @include forward($mixin, $value: 2, $factor: 4);
                          @include meta.apply(meta.get-mixin("accepts")) using ($value, $label) {
                            content-value: $value;
                            content-label: $label;
                          }
                        }

                        .nested {
                          @include nested {
                            child: yes;
                          }
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          type: mixin;
                          inspected: get-mixin("local-style");
                          same: true;
                          mapped: value;
                          local-accepts: false;
                          target-accepts: true;
                          apply-accepts: true;
                          meta-apply: true;
                          height: 8;
                          has-content: true;
                          content-value: 6;
                          content-label: content;
                        }

                        .nested {
                          outer-content: true;
                          inner-content: false;
                          child: yes;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Returns public module mixins as mixin values and excludes private declarations.
    @Test
    void readsNamedModuleMixins(@TempDir Path directory) throws Exception {
        Files.writeString(
                directory.resolve("library.scss"),
                """
                        @mixin public-style($value) {
                          library: $value;
                        }

                        @mixin -private-style {
                          private: hidden;
                        }
                        """
        );
        var entry = Files.writeString(
                directory.resolve("main.scss"),
                """
                        @use "sass:map";
                        @use "sass:meta";
                        @use "library" as library;

                        $mixins: meta.module-mixins("library");

                        .example {
                          type: meta.type-of(meta.get-mixin("public_style", "library"));
                          same: map.get($mixins, "public-style") == meta.get-mixin("public-style", "library");
                          private: map.has-key($mixins, "-private-style");
                          @include meta.apply(map.get($mixins, "public-style"), module);
                        }
                        """
        );

        var result = new SassCompiler().compile(SassSource.fromFile(entry), CssTarget.DEFAULT);
        assertEquals(
                """
                        .example {
                          type: mixin;
                          same: true;
                          private: false;
                          library: module;
                        }""",
                result.output()
        );
        assertEquals(2, result.loadedUrls().size());
    }

    /// Rejects missing, incompatible, and contextually invalid mixin-value operations.
    @Test
    void rejectsInvalidMixinValueOperations() {
        assertEquals(
                "Mixin not found: \"missing\"",
                failure("@use \"sass:meta\"; a { value: meta.get-mixin(\"missing\"); }")
        );
        assertEquals(
                "$mixin: 1 is not a mixin reference.",
                failure("@use \"sass:meta\"; a { @include meta.apply(1); }")
        );
        assertEquals(
                "$mixin: 1 is not a mixin reference.",
                failure("@use \"sass:meta\"; a { value: meta.accepts-content(1); }")
        );
        assertEquals(
                "content-exists() may only be called within a mixin.",
                failure("@use \"sass:meta\"; a { value: meta.content-exists(); }")
        );
        assertEquals(
                "Mixin doesn't accept a content block.",
                failure(
                        "@use \"sass:meta\"; @mixin no-content {} "
                                + "@include meta.apply(meta.get-mixin(\"no-content\")) { a { b: c; } }"
                )
        );
        assertEquals(
                "get-mixin(\"value\") isn't a valid CSS value.",
                failure(
                        "@use \"sass:meta\"; @mixin value {} "
                                + "a { value: meta.get-mixin(\"value\"); }"
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