// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.CompileResult;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the first pure AST-backed {@code sass:selector} module functions.
@NotNullByDefault
final class SelectorModuleTest {
    /// Compiles parsed, nested, appended, and decomposed selector values.
    @Test
    void evaluatesSelectorModuleFunctionsWithoutLoadingStylesheets() throws Exception {
        var result = compile(
                """
                        @use "sass:selector";

                        $parsed: selector.parse((".menu" ">" ".item", ".button:hover"));
                        $nested: selector.nest(".menu, .toolbar", "&:hover", "> .item");
                        $appended: selector.append(".button, .link", ".active", ":hover");
                        $appended-pseudo: selector.append(
                          ".choice",
                          ":not([data-kind|=primary])"
                        );
                        $simples: selector.simple-selectors("button.primary#main:hover");

                        .example {
                          parsed: $parsed;
                          nested: $nested;
                          appended: $appended;
                          appended-pseudo: $appended-pseudo;
                          simples: $simples;
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          parsed: .menu > .item, .button:hover;
                          nested: .menu:hover > .item, .toolbar:hover > .item;
                          appended: .button.active:hover, .link.active:hover;
                          appended-pseudo: .choice:not([data-kind|=primary]);
                          simples: button, .primary, #main, :hover;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Compiles selector nesting with parent references inside pseudo arguments.
    @Test
    void nestsRecursivePseudoArgumentsThroughSelectorModule() throws Exception {
        var result = compile(
                """
                        @use "sass:selector";

                        $matched: selector.nest(".menu, .toolbar", ":is(&, .fallback)");
                        $nth: selector.nest(".menu", ":nth-child(2n + 1 of &)");

                        .example {
                          matched: $matched;
                          nth: $nth;
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          matched: :is(.menu, .toolbar, .fallback);
                          nth: :nth-child(2n+1 of .menu);
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Compiles selector algebra values through the public module interface.
    @Test
    void evaluatesSelectorAlgebraFunctions() throws Exception {
        var result = compile(
                """
                        @use "sass:selector";

                        $unified: selector.unify(".badge, .tag", ".selected");
                        $weaved: selector.unify(".a .b", ".c .b");
                        $super: selector.is-superselector(".a .b", ".a > .b");
                        $extended: selector.extend(".info .title", ".info", ".alert");
                        $replaced: selector.replace(".info .title", ".info", ".alert");
                        $attribute-unified: selector.unify("[data-kind=primary]", ".selected");
                        $atomic-unified: selector.unify(":hover", ".button");
                        $attribute-replaced: selector.replace(
                          '.card[data-kind="primary"]',
                          "[data-kind=primary]",
                          ".active"
                        );
                        $atomic-replaced: selector.replace(".button:hover", ":hover", ".active");
                        $namespace-unified: selector.unify("*|a", "svg|*");
                        $namespace-super: selector.is-superselector("*|a", "svg|a");
                        $namespace-replaced: selector.replace(
                          "svg|a.item",
                          "svg|*",
                          ".selected"
                        );
                        $pseudo-unified: selector.unify(":is(.choice)", ".active");
                        $pseudo-super: selector.is-superselector(".choice", ":is(.choice)");
                        $pseudo-extended: selector.extend(":is(.choice)", ".choice", ".selected");
                        $pseudo-replaced: selector.replace(".card::before", "::before", ".generated");

                        .example {
                          unified: $unified;
                          weaved: $weaved;
                          super: $super;
                          extended: $extended;
                          replaced: $replaced;
                          attribute-unified: $attribute-unified;
                          atomic-unified: $atomic-unified;
                          attribute-replaced: $attribute-replaced;
                          atomic-replaced: $atomic-replaced;
                          namespace-unified: $namespace-unified;
                          namespace-super: $namespace-super;
                          namespace-replaced: $namespace-replaced;
                          pseudo-unified: $pseudo-unified;
                          pseudo-super: $pseudo-super;
                          pseudo-extended: $pseudo-extended;
                          pseudo-replaced: $pseudo-replaced;
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          unified: .badge.selected, .tag.selected;
                          weaved: .a .c .b, .c .a .b;
                          super: true;
                          extended: .info .title, .alert .title;
                          replaced: .alert .title;
                          attribute-unified: [data-kind=primary].selected;
                          atomic-unified: .button:hover;
                          attribute-replaced: .card.active;
                          atomic-replaced: .button.active;
                          namespace-unified: svg|a;
                          namespace-super: true;
                          namespace-replaced: svg|a.item.selected;
                          pseudo-unified: .active:is(.choice);
                          pseudo-super: true;
                          pseudo-extended: :is(.choice, .selected);
                          pseudo-replaced: .card.generated;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Reports nested and trailing-comma list shapes with dart-sass inspect text.
    @Test
    void reportsInvalidSelectorListInspect() {
        var tooNested = failure(
                """
                        @use "sass:list";
                        @use "sass:selector";
                        a {b: selector.parse((list.append((), list.append((), c)),))}
                        """
        );
        assertTrue(
                tooNested.contains("$selector: (c,) is not a valid selector"),
                tooNested
        );
        var innerComma = failure(
                """
                        @use "sass:selector";
                        a {b: selector.parse(((c,),))}
                        """
        );
        assertTrue(
                innerComma.contains("$selector: ((c,),) is not a valid selector"),
                innerComma
        );
    }

    /// Rejects selector values that require unsupported or invalid selector semantics.
    @Test
    void rejectsInvalidSelectorModuleArguments() {
        assertEquals(
                "$selector: Parent selectors aren't allowed here.",
                failure("@use \"sass:selector\"; .a { value: selector.parse(\"&\"); }")
        );
        assertEquals(
                "$selector: \".a .b\" is not a compound selector.",
                failure(
                        "@use \"sass:selector\"; .a { value: selector.simple-selectors(\".a .b\"); }"
                )
        );
        assertEquals(
                "Can't append > .item to .a.",
                failure(
                        "@use \"sass:selector\"; .a { value: selector.append(\".a\", \"> .item\"); }"
                )
        );
        assertEquals(
                "Parent selectors in non-selector pseudo arguments aren't supported.",
                failure(
                        "@use \"sass:selector\"; .a { value: selector.nest(\".a\", \":lang(&)\"); }"
                )
        );
        assertEquals(
                "$selector: expected more input.",
                failure(
                        "@use \"sass:selector\"; .a { value: selector.parse(\"[data=value\"); }"
                )
        );

        assertEquals(
                "Can't extend complex selector .a .b.",
                failure(
                        "@use \"sass:selector\"; .a { value: selector.extend(\".a\", \".a .b\", \".c\"); }"
                )
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
