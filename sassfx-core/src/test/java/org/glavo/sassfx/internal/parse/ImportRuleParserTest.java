// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.DiagnosticSeverity;
import org.glavo.sassfx.Syntax;
import org.glavo.sassfx.internal.ast.DynamicImport;
import org.glavo.sassfx.internal.ast.ImportRule;
import org.glavo.sassfx.internal.ast.StaticImport;
import org.glavo.sassfx.internal.ast.SupportsDeclaration;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies legacy Sass and static CSS import parsing.
@NotNullByDefault
final class ImportRuleParserTest {
    /// Classifies mixed import arguments and reports one deprecation per dynamic import.
    @Test
    void parsesMixedDynamicAndStaticImports() {
        var stylesheet = parse(
                "@import \"tokens\", \"theme.css\", url(#{$asset});"
        );
        var rule = assertInstanceOf(ImportRule.class, stylesheet.children().get(0));

        assertEquals(3, rule.imports().size());
        var dynamic = assertInstanceOf(DynamicImport.class, rule.imports().get(0));
        assertEquals("tokens", dynamic.url());
        assertEquals("\"tokens\"", dynamic.span().text());
        assertInstanceOf(StaticImport.class, rule.imports().get(1));
        assertInstanceOf(StaticImport.class, rule.imports().get(2));
        assertEquals(1, stylesheet.parseTimeWarnings().size());
        assertEquals(DiagnosticSeverity.DEPRECATION, stylesheet.parseTimeWarnings().get(0).severity());
        assertEquals("import", stylesheet.parseTimeWarnings().get(0).code());
    }

    /// Ignores trailing whitespace when classifying a dynamic Sass import.
    @Test
    void classifiesDynamicImportBeforeTrailingWhitespace() {
        var stylesheet = parse("@import \"tokens\" ;");
        var rule = assertInstanceOf(ImportRule.class, stylesheet.children().get(0));

        assertInstanceOf(DynamicImport.class, rule.imports().get(0));
        assertEquals(1, stylesheet.parseTimeWarnings().size());
    }

    /// Treats a quoted URL with modifiers as a static CSS import.
    @Test
    void parsesStaticImportModifiersAsOneArgument() {
        var stylesheet = parse("@import \"print\" screen, print;");
        var rule = assertInstanceOf(ImportRule.class, stylesheet.children().get(0));
        var importRule = assertInstanceOf(StaticImport.class, rule.imports().get(0));

        assertEquals(1, rule.imports().size());
        assertEquals("\"print\"", importRule.url().asPlain());
        assertEquals("screen, print", importRule.modifiersBeforeSupports().asPlain());
        assertNull(importRule.supports());
        assertNull(importRule.modifiersAfterSupports());
        assertEquals(0, stylesheet.parseTimeWarnings().size());
    }

    /// Keeps supports and surrounding CSS modifiers in one modifier interpolation.
    @Test
    void parsesStructuredStaticImportSupportsModifier() {
        var stylesheet = parse(
                "@import \"theme.css\" layer(theme) supports(display: $display) screen;"
        );
        var rule = assertInstanceOf(ImportRule.class, stylesheet.children().get(0));
        var importRule = assertInstanceOf(StaticImport.class, rule.imports().get(0));

        assertNull(importRule.supports());
        assertNull(importRule.modifiersAfterSupports());
        var modifiers = importRule.modifiersBeforeSupports().toString();
        assertEquals(true, modifiers.contains("layer(theme)"));
        assertEquals(true, modifiers.contains("supports("));
        assertEquals(true, modifiers.contains("$display"));
        assertEquals(true, modifiers.contains("screen"));
    }

    /// Accepts repeated top-level supports modifiers as successive function modifiers.
    @Test
    void parsesRepeatedStaticImportSupportsModifiers() {
        var stylesheet = parse(
                "@import \"theme.css\" supports(display: grid) supports(color: red);"
        );
        var rule = assertInstanceOf(ImportRule.class, stylesheet.children().get(0));
        var importRule = assertInstanceOf(StaticImport.class, rule.imports().get(0));
        var modifiers = importRule.modifiersBeforeSupports().toString();
        assertEquals(true, modifiers.contains("supports("));
        assertEquals(true, modifiers.contains("display"));
        assertEquals(true, modifiers.contains("color"));
    }

    /// Rejects malformed conditions within static-import supports modifiers.
    @Test
    void rejectsInvalidStaticImportSupportsConditions() {
        var sources = List.of(
                "@import \"theme.css\" supports();",
                "@import \"theme.css\" supports((display: grid) and not (color: red));",
                "@import \"theme.css\" supports((display: grid) and not(color: red));",
                "@import \"theme.css\" supports((display: grid) and (color: red) or (width: 1px));",
                "@import \"theme.css\" supports(selector (.button));"
        );
        for (var source : sources) {
            assertThrows(ParseException.class, () -> parse(source));
        }
    }

    /// Rejects dynamic imports in control directives, mixins, and declaration-only contexts.
    @Test
    void rejectsDynamicImportsInDisallowedContexts() {
        assertThrows(ParseException.class, () -> parse("@if true { @import \"tokens\"; }"));
        assertThrows(ParseException.class, () -> parse("@mixin load { @import \"tokens\"; }"));
        assertThrows(ParseException.class, () -> parse("a { font: { @import \"x.css\"; } }"));
    }

    /// Accepts static imports in control directives and mixin declarations.
    @Test
    void acceptsStaticImportsInDynamicContexts() {
        parse("@if true { @import \"theme.css\"; }");
        parse("@mixin load { @import url(theme.css); }");
    }

    /// Parses one SCSS source through the shared stylesheet entry point.
    private static org.glavo.sassfx.internal.ast.Stylesheet parse(String source) {
        return StylesheetParser.parse(new SourceFile(source, null), Syntax.SCSS);
    }
}
