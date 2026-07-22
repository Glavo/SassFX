// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies token operations shared by Sass parser variants.
@NotNullByDefault
final class ParserTest {
    /// Verifies whitespace consumption across adjacent silent and loud comments.
    @Test
    void consumesWhitespaceAndComments() {
        var text = " \t// silent\r\n/* loud **/name";
        var parser = parser(text);

        parser.whitespace(true);

        assertEquals(text.indexOf("name"), parser.scanner.position());
        assertEquals('n', parser.scanner.peek());
    }

    /// Verifies that form feed is CSS whitespace even when callers request no newlines.
    @Test
    void consumesFormFeedAsWhitespace() {
        var parser = parser("\fname");

        parser.whitespace(false);

        assertEquals(1, parser.scanner.position());
        assertEquals('n', parser.scanner.peek());
    }

    /// Verifies comment terminators and the newline ownership of silent comments.
    @Test
    void preservesSilentCommentNewlineAndStopsLoudCommentAtFirstTerminator() {
        var silent = parser("// comment\r\nname");
        assertTrue(silent.silentComment());
        assertEquals('\r', silent.scanner.peek());

        var loud = parser("/* comment */name");
        loud.loudComment();
        assertEquals('n', loud.scanner.peek());
    }

    /// Verifies that unterminated loud comments fail at end of input.
    @Test
    void rejectsUnterminatedLoudComment() {
        var parser = parser("/* comment");

        var failure = assertThrows(ParseException.class, parser::loudComment);
        assertEquals("", failure.span().text());
        assertEquals("Unexpected end of input.", failure.getMessage());
    }

    /// Verifies plain strings, escaped characters, and hexadecimal escapes.
    @Test
    void parsesQuotedStrings() {
        assertEquals("plain", parser("'plain'").string());
        assertEquals("abc", parser("\"a\\62 c\"").string());
        assertEquals("a.b", parser("\"a\\.b\"").string());
        assertEquals("ab", parser("\"a\\\nb\"").string());
    }

    /// Verifies that a CRLF continuation consumes only the CR code unit.
    @Test
    void rejectsCrLfStringContinuationAtRemainingLineFeed() {
        var failure = assertThrows(ParseException.class, () -> parser("\"a\\\r\nb\"").string());

        assertEquals("\n", failure.span().text());
    }

    /// Verifies CSS replacement-character behavior for invalid escapes.
    @Test
    void replacesInvalidEscapedCodePoints() {
        assertEquals("\uFFFD", parser("\"\\0\"").string());
        assertEquals("\uFFFD", parser("\"\\D800\"").string());
        assertEquals("\uFFFD", parser("\"\\110000\"").string());

        var parser = parser("\\");
        assertEquals(0xFFFD, parser.escapeCharacter());
        assertTrue(parser.scanner.isDone());
    }

    /// Verifies CSS escape edge cases and their exact scanner consumption.
    @Test
    void parsesEscapeCharacterBoundaries() {
        assertThrows(ParseException.class, () -> parser("\\\n").escapeCharacter());
        assertEquals(0xFFFD, parser("\\0").escapeCharacter());
        assertEquals(0xFFFD, parser("\\D800").escapeCharacter());
        assertEquals(0xFFFD, parser("\\10ffff").escapeCharacter());
        assertEquals(0x1F600, parser("\\1f600").escapeCharacter());

        var limitedDigits = parser("\\10abcd7");
        assertEquals(0x10ABCD, limitedDigits.escapeCharacter());
        assertEquals('7', limitedDigits.scanner.peek());

        var oneWhitespace = parser("\\41  name");
        assertEquals('A', oneWhitespace.escapeCharacter());
        assertEquals(' ', oneWhitespace.scanner.peek());
    }

    /// Verifies that decoded supplementary escapes are represented as a UTF-16 pair.
    @Test
    void decodesSupplementaryEscapeInString() {
        assertEquals("\uD83D\uDE00", parser("\"\\1f600\"").string());
    }

    /// Verifies string termination and opening-quote diagnostics.
    @Test
    void rejectsMalformedStrings() {
        var missingQuote = assertThrows(ParseException.class, () -> parser("name").string());
        assertEquals("n", missingQuote.span().text());
        assertEquals("Expected string.", missingQuote.getMessage());

        var unterminated = assertThrows(
                ParseException.class,
                () -> parser("\"unterminated").string()
        );
        assertEquals("", unterminated.span().text());

        var newline = assertThrows(ParseException.class, () -> parser("\"a\nb\"").string());
        assertEquals("\n", newline.span().text());
    }

    /// Verifies natural-number value and stopping position.
    @Test
    void parsesNaturalNumber() {
        var parser = parser("123.5");

        assertEquals(123.0, parser.naturalNumber());
        assertEquals(3, parser.scanner.position());
        assertEquals('.', parser.scanner.peek());
    }

    /// Verifies that a natural number stops before non-decimal CSS number syntax.
    @Test
    void stopsNaturalNumberBeforeExponentAndSign() {
        var parser = parser("42e-3");

        assertEquals(42.0, parser.naturalNumber());
        assertEquals('e', parser.scanner.peek());
    }

    /// Verifies natural-number failure at the first non-digit.
    @Test
    void rejectsMissingNaturalNumber() {
        var failure = assertThrows(ParseException.class, () -> parser("x").naturalNumber());

        assertEquals("Expected digit.", failure.getMessage());
        assertEquals("x", failure.span().text());
    }

    /// Verifies shared identifier, body, and variable-name operations.
    @Test
    void parsesIdentifiersAndVariables() {
        var identifier = parser("_foo");
        assertTrue(identifier.lookingAtIdentifier());
        assertEquals("-foo", identifier.identifier(true, false));

        var body = parser("-2");
        assertEquals("-2", body.identifierBody());

        var variable = parser("$foo_bar");
        assertEquals("foo-bar", variable.variableName());
        assertFalse(variable.lookingAtIdentifier());
    }

    /// Verifies required whitespace accepts comments and rejects other tokens.
    @Test
    void requiresWhitespaceOrComments() {
        var whitespace = parser(" \tname");
        whitespace.expectWhitespace(false);
        assertEquals('n', whitespace.scanner.peek());

        var comment = parser("/* comment */name");
        comment.expectWhitespace(false);
        assertEquals('n', comment.scanner.peek());

        assertThrows(ParseException.class, () -> parser("name").expectWhitespace(false));
    }

    /// Verifies CSS number-prefix detection without consuming input.
    @Test
    void detectsNumberPrefixes() {
        for (var text : new String[]{"0", ".5", "+1", "-1", "+.5", "-.5"}) {
            var parser = parser(text);
            assertTrue(parser.lookingAtNumber(), text);
            assertEquals(0, parser.scanner.position(), text);
        }
        for (var text : new String[]{".", "+", "-", "+.x", "name"}) {
            assertFalse(parser(text).lookingAtNumber(), text);
        }
    }

    /// Verifies complete identifier matching, escape handling, and restoration.
    @Test
    void scansCompleteIdentifiers() {
        var caseInsensitive = parser("URL(");
        assertTrue(caseInsensitive.scanIdentifier("url"));
        assertEquals('(', caseInsensitive.scanner.peek());

        var escaped = parser("u\\72l(");
        assertTrue(escaped.scanIdentifier("url"));
        assertEquals('(', escaped.scanner.peek());

        var longer = parser("urlx(");
        assertFalse(longer.scanIdentifier("url"));
        assertEquals(0, longer.scanner.position());

        var wrongCase = parser("URL(");
        assertFalse(wrongCase.scanIdentifier("url", true));
        assertEquals(0, wrongCase.scanner.position());

        var lookahead = parser(".name");
        assertTrue(lookahead.lookingAtIdentifier(1));
        assertEquals(0, lookahead.scanner.position());

        var matching = parser("u\\72l(");
        assertTrue(matching.matchesIdentifier("url"));
        assertEquals(0, matching.scanner.position());
        matching.expectIdentifier("url");
        assertEquals('(', matching.scanner.peek());
    }

    /// Verifies declaration terminators and balanced nested delimiters.
    @Test
    void parsesBalancedDeclarationValues() {
        var nested = parser("a({x:[y;z]});tail");

        assertEquals("a({x:[y;z]})", nested.declarationValue());
        assertEquals(';', nested.scanner.peek());

        var closing = parser("alpha)tail");
        assertEquals("alpha", closing.declarationValue());
        assertEquals(')', closing.scanner.peek());
    }

    /// Verifies empty, mismatched, and unterminated declaration values.
    @Test
    void rejectsMalformedDeclarationValues() {
        var empty = parser(";tail");
        assertThrows(ParseException.class, empty::declarationValue);
        assertEquals(';', empty.scanner.peek());

        var allowedEmpty = parser(";tail");
        assertEquals("", allowedEmpty.declarationValue(true));
        assertEquals(';', allowedEmpty.scanner.peek());

        assertThrows(ParseException.class, () -> parser("a([)]").declarationValue());
        assertThrows(ParseException.class, () -> parser("a(").declarationValue());
    }

    /// Verifies declaration whitespace normalization and raw token preservation.
    @Test
    void normalizesDeclarationWhitespaceAndPreservesRawTokens() {
        assertEquals("a b", parser("a \t  b").declarationValue());
        assertEquals(
                "a\n  b\n c",
                parser("a\r\n  b\f\tc").declarationValue()
        );
        assertEquals(
                "\"a\\62 c\" /* x\r\n y */ tail",
                parser("\"a\\62 c\" /* x\r\n y */  tail;").declarationValue()
        );
        assertEquals("a//x\nb", parser("a//x\nb").declarationValue());
    }

    /// Verifies normalized raw URL tokens and generic-parser fallback.
    @Test
    void parsesAndBacktracksRawUrls() {
        assertEquals("url(foo)", parser("URL( foo )").declarationValue());
        assertEquals("url(foo)", parser("u\\72l(foo)").declarationValue());
        assertEquals("url(foo)", parser("url(/*lead*/foo)").declarationValue());
        assertEquals("url(foo/*tail*/)", parser("url(foo/*tail*/)").declarationValue());
        assertEquals("url(foo bar)", parser("url(foo bar)").declarationValue());
        assertEquals("url(\"foo\")", parser("url(\"foo\")").declarationValue());
        assertEquals("urlx(foo)", parser("urlx(foo)").declarationValue());
        assertEquals("url(é😀)", parser("url(é😀)").declarationValue());
        assertThrows(ParseException.class, () -> parser("url(foo").declarationValue());
    }

    /// Verifies identifier-start escape normalization in declaration values.
    @Test
    void normalizesDeclarationEscapes() {
        var parser = parser("\\31 foo;");

        assertEquals("\\31 foo", parser.declarationValue());
        assertEquals(';', parser.scanner.peek());
        assertEquals("\uD800", parser("\\d800;").declarationValue());
    }

    /// Creates a parser for source text without a stable URL.
    ///
    /// @param text the source text
    /// @return the parser
    private static Parser parser(String text) {
        return new Parser(new SourceFile(text, null));
    }
}
