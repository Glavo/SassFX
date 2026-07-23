// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.Syntax;
import org.glavo.scssfx.internal.ast.DebugRule;
import org.glavo.scssfx.internal.ast.ErrorRule;
import org.glavo.scssfx.internal.ast.FunctionRule;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.WarnRule;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies parsing and source ranges for Sass diagnostic statements.
@NotNullByDefault
final class DiagnosticRuleParserTest {
    /// Parses each diagnostic statement into its dedicated immutable AST node.
    @Test
    void parsesDedicatedDiagnosticRuleNodes() {
        var stylesheet = parse(
                """
                        @debug "value";
                        @warn warning;
                        @error (reason: fatal);
                        """
        );

        var debug = assertInstanceOf(DebugRule.class, stylesheet.children().get(0));
        var warning = assertInstanceOf(WarnRule.class, stylesheet.children().get(1));
        var error = assertInstanceOf(ErrorRule.class, stylesheet.children().get(2));

        assertInstanceOf(StringExpression.class, debug.expression());
        assertInstanceOf(StringExpression.class, warning.expression());
        assertEquals("@debug \"value\"", debug.span().text());
        assertEquals("@warn warning", warning.span().text());
        assertEquals("@error (reason: fatal)", error.span().text());
    }

    /// Accepts diagnostic statements in function bodies despite ordinary child restrictions.
    @Test
    void parsesDiagnosticRulesInFunctionBodies() {
        var stylesheet = parse(
                """
                        @function report() {
                          @debug first;
                          @warn second;
                          @error third;
                        }
                        """
        );

        var function = assertInstanceOf(FunctionRule.class, stylesheet.children().get(0));
        assertInstanceOf(DebugRule.class, function.children().get(0));
        assertInstanceOf(WarnRule.class, function.children().get(1));
        assertInstanceOf(ErrorRule.class, function.children().get(2));
    }

    /// Rejects missing expressions and child blocks after terminal diagnostic rules.
    @Test
    void rejectsMalformedDiagnosticRules() {
        assertThrows(ParseException.class, () -> parse("@debug;"));
        assertThrows(ParseException.class, () -> parse("@warn value {}"));
        assertThrows(ParseException.class, () -> parse("@error"));
    }

    /// Parses one SCSS source through the shared stylesheet entry point.
    ///
    /// @param source the complete SCSS source
    /// @return the parsed stylesheet
    private static org.glavo.scssfx.internal.ast.Stylesheet parse(String source) {
        return StylesheetParser.parse(new SourceFile(source, null), Syntax.SCSS);
    }
}
