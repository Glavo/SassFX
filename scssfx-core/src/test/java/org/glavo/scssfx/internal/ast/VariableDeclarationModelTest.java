// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies variable-declaration structure, source metadata, and immutable snapshots.
@NotNullByDefault
final class VariableDeclarationModelTest {
    /// Verifies flags and comments are retained while source rendering follows Sass AST conventions.
    @Test
    void representsUnqualifiedDeclaration() {
        var text = "/// docs\n$foo_bar: 1 !default !global;";
        var source = new SourceFile(text, null);
        var declarationStart = text.indexOf('$');
        var expressionStart = text.indexOf('1');
        var comment = new SilentComment("/// docs", source.span(0, declarationStart - 1));
        var expression = new NumberExpression(
                1,
                null,
                source.span(expressionStart, expressionStart + 1)
        );
        var declaration = new VariableDeclaration(
                null,
                "foo-bar",
                expression,
                true,
                true,
                comment,
                source.span(declarationStart, declarationStart + "$foo_bar".length()),
                null,
                source.span(declarationStart, text.length() - 1)
        );

        assertEquals("foo-bar", declaration.name());
        assertEquals("$foo_bar", declaration.originalName());
        assertTrue(declaration.isGuarded());
        assertTrue(declaration.isGlobal());
        assertSame(comment, declaration.comment());
        assertEquals("$foo_bar", declaration.nameSpan().text());
        assertEquals(text.substring(declarationStart, text.length() - 1), declaration.span().text());
        assertEquals("$foo-bar: 1;", declaration.toString());
    }

    /// Verifies qualified declarations retain their namespace range and normalized source form.
    @Test
    void representsQualifiedDeclaration() {
        var text = "theme.$accent_color \t: red !default;";
        var source = new SourceFile(text, null);
        var nameStart = text.indexOf('$');
        var nameEnd = nameStart + "$accent_color".length();
        var expressionStart = text.indexOf("red");
        var declaration = new VariableDeclaration(
                "theme",
                "accent-color",
                StringExpression.plain(
                        "red",
                        source.span(expressionStart, expressionStart + "red".length())
                ),
                true,
                false,
                null,
                source.span(nameStart, nameEnd),
                source.span(0, "theme".length()),
                source.span(0, text.length() - 1)
        );

        assertEquals("theme", declaration.namespace());
        assertEquals("theme", Objects.requireNonNull(declaration.namespaceSpan()).text());
        assertEquals("theme.$accent_color", declaration.originalName());
        assertTrue(declaration.isGuarded());
        assertFalse(declaration.isGlobal());
        assertEquals("theme.$accent-color: red;", declaration.toString());
    }

    /// Verifies invalid names, flag combinations, and source ranges are rejected eagerly.
    @Test
    void validatesDeclarationStructure() {
        var declaration = declaration("$value: 1;");

        assertThrows(
                IllegalArgumentException.class,
                () -> new VariableDeclaration(
                        null,
                        "",
                        declaration.expression(),
                        false,
                        false,
                        null,
                        declaration.nameSpan(),
                        null,
                        declaration.span()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new VariableDeclaration(
                        "",
                        declaration.name(),
                        declaration.expression(),
                        false,
                        false,
                        null,
                        declaration.nameSpan(),
                        declaration.nameSpan(),
                        declaration.span()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new VariableDeclaration(
                        "module",
                        declaration.name(),
                        declaration.expression(),
                        false,
                        true,
                        null,
                        declaration.nameSpan(),
                        declaration.nameSpan(),
                        declaration.span()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new VariableDeclaration(
                        "module",
                        declaration.name(),
                        declaration.expression(),
                        false,
                        false,
                        null,
                        declaration.nameSpan(),
                        null,
                        declaration.span()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new VariableDeclaration(
                        null,
                        declaration.name(),
                        declaration.expression(),
                        false,
                        false,
                        null,
                        declaration.span(),
                        null,
                        declaration.span()
                )
        );

        var missingColonSource = new SourceFile("$value 1", null);
        assertThrows(
                IllegalArgumentException.class,
                () -> new VariableDeclaration(
                        null,
                        declaration.name(),
                        new NumberExpression(1, null, missingColonSource.span(7, 8)),
                        false,
                        false,
                        null,
                        missingColonSource.span(0, 6),
                        null,
                        missingColonSource.span(0, 8)
                )
        );

        var otherSource = new SourceFile("$value: 2;", null);
        var otherExpression = new NumberExpression(2, null, otherSource.span(8, 9));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VariableDeclaration(
                        null,
                        declaration.name(),
                        otherExpression,
                        false,
                        false,
                        null,
                        declaration.nameSpan(),
                        null,
                        declaration.span()
                )
        );
    }

    /// Verifies stylesheet diagnostics and global-variable metadata snapshot mutable inputs.
    @Test
    void snapshotsStylesheetMetadata() {
        var declaration = declaration("$value: 1;");
        var children = new ArrayList<SassStatement>(List.of(declaration));
        var warnings = new ArrayList<Diagnostic>();
        warnings.add(new Diagnostic(
                DiagnosticSeverity.DEPRECATION,
                "warning",
                declaration.span(),
                "test-warning"
        ));
        var globals = new LinkedHashMap<String, SourceSpan>();
        globals.put("first", declaration.span());
        globals.put("second", declaration.nameSpan());

        var stylesheet = new Stylesheet(
                children,
                declaration.span(),
                false,
                warnings,
                globals
        );
        children.clear();
        warnings.clear();
        globals.clear();

        assertEquals(List.of(declaration), stylesheet.children());
        assertEquals(1, stylesheet.parseTimeWarnings().size());
        assertEquals(List.of("first", "second"), List.copyOf(stylesheet.globalVariables().keySet()));
        assertThrows(UnsupportedOperationException.class, stylesheet.children()::clear);
        assertThrows(UnsupportedOperationException.class, stylesheet.parseTimeWarnings()::clear);
        assertThrows(UnsupportedOperationException.class, stylesheet.globalVariables()::clear);
    }

    /// Creates a simple unqualified declaration spanning the supplied source.
    ///
    /// @param text the declaration source
    /// @return the variable declaration
    private static VariableDeclaration declaration(String text) {
        var source = new SourceFile(text, null);
        var nameEnd = text.indexOf(':');
        var expressionStart = text.indexOf('1');
        return new VariableDeclaration(
                null,
                "value",
                new NumberExpression(
                        1,
                        null,
                        source.span(expressionStart, expressionStart + 1)
                ),
                false,
                false,
                null,
                source.span(0, nameEnd),
                null,
                source.span(0, text.length() - 1)
        );
    }
}
