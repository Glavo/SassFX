// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.IntPredicate;

/// Provides token-level operations shared by Sass parser variants.
///
/// The default whitespace behavior is appropriate for SCSS and plain CSS.
/// The indented syntax parser may override [#whitespaceWithoutComments(boolean)]
/// to preserve statement-ending newlines.
@NotNullByDefault
class Parser {
    /// The mutable scanner for the source being parsed.
    protected final SourceScanner scanner;

    /// Creates a parser for an indexed source.
    ///
    /// @param source the source to parse
    Parser(SourceFile source) {
        scanner = new SourceScanner(Objects.requireNonNull(source, "source"));
    }

    /// Consumes whitespace and any adjacent Sass or CSS comments.
    ///
    /// @param consumeNewlines whether an indented parser may consume newlines
    protected void whitespace(boolean consumeNewlines) {
        do {
            whitespaceWithoutComments(consumeNewlines);
        } while (scanComment());
    }

    /// Consumes whitespace without consuming comments.
    ///
    /// This base implementation consumes newlines regardless of
    /// {@code consumeNewlines}, because newlines do not terminate SCSS or CSS
    /// statements. The indented parser overrides this behavior.
    ///
    /// @param consumeNewlines whether an indented parser may consume newlines
    @SuppressWarnings("unused")
    protected void whitespaceWithoutComments(boolean consumeNewlines) {
        while (!scanner.isDone() && CssCharacters.isWhitespace(scanner.peek())) {
            scanner.read();
        }
    }

    /// Consumes spaces and horizontal tabs.
    protected void spaces() {
        while (CssCharacters.isSpaceOrTab(scanner.peek())) {
            scanner.read();
        }
    }

    /// Consumes whitespace and requires at least one whitespace unit or comment.
    ///
    /// @param consumeNewlines whether an indented parser may consume newlines
    /// @throws ParseException if no whitespace or comment begins here
    protected void expectWhitespace(boolean consumeNewlines) {
        if (scanner.isDone()
                || !(CssCharacters.isWhitespace(scanner.peek()) || scanComment())) {
            throw scanner.error("Expected whitespace.");
        }
        whitespace(consumeNewlines);
    }

    /// Consumes one comment when the scanner is positioned before one.
    ///
    /// @return whether a comment was consumed
    protected boolean scanComment() {
        if (scanner.peek() != '/') {
            return false;
        }
        return switch (scanner.peek(1)) {
            case '/' -> silentComment();
            case '*' -> {
                loudComment();
                yield true;
            }
            default -> false;
        };
    }

    /// Consumes one Sass-style silent comment without its trailing newline.
    ///
    /// @return {@code true}
    /// @throws ParseException if the scanner is not positioned before {@code //}
    protected boolean silentComment() {
        scanner.expect("//");
        while (!scanner.isDone() && !CssCharacters.isNewline(scanner.peek())) {
            scanner.read();
        }
        return true;
    }

    /// Consumes one CSS-style loud comment.
    ///
    /// @throws ParseException if the scanner is not positioned before
    /// {@code /*} or the comment is not terminated
    protected void loudComment() {
        scanner.expect("/*");
        while (true) {
            var next = scanner.read();
            if (next != '*') {
                continue;
            }

            do {
                next = scanner.read();
            } while (next == '*');
            if (next == '/') {
                return;
            }
        }
    }

    /// Consumes a plain CSS identifier.
    ///
    /// @param normalize whether underscores are normalized to hyphens
    /// @param unit whether a hyphen before a dot or digit ends the identifier
    /// @return the parsed identifier representation
    /// @throws ParseException if no identifier begins at the current position
    protected String identifier(boolean normalize, boolean unit) {
        return CssIdentifierParser.parse(scanner, normalize, unit);
    }

    /// Consumes one or more plain CSS identifier-body code units.
    ///
    /// @return the parsed identifier-body representation
    /// @throws ParseException if no identifier-body text begins at the current position
    protected String identifierBody() {
        return CssIdentifierParser.parseBody(scanner, false, false);
    }

    /// Returns whether a plain CSS identifier begins at the current position.
    ///
    /// @return whether an identifier begins here
    protected boolean lookingAtIdentifier() {
        return lookingAtIdentifier(0);
    }

    /// Returns whether a plain CSS identifier begins at a future position.
    ///
    /// @param forward the nonnegative number of UTF-16 code units to look ahead
    /// @return whether an identifier begins at the selected position
    /// @throws IllegalArgumentException if {@code forward} is negative
    protected boolean lookingAtIdentifier(int forward) {
        return CssIdentifierParser.lookingAtIdentifier(scanner, forward);
    }

    /// Returns whether an identifier-body code unit begins at the current position.
    ///
    /// @return whether an identifier-body code unit begins here
    protected boolean lookingAtIdentifierBody() {
        var next = scanner.peek();
        return next != CssCharacters.END_OF_INPUT
                && (CssCharacters.isName(next) || next == '\\');
    }

    /// Consumes an identifier when its normalized value equals the expected text.
    ///
    /// Matching ignores ASCII case and accepts escapes in the source identifier.
    /// The scanner is restored when the complete identifier does not match.
    ///
    /// @param text the expected identifier value
    /// @return whether the complete identifier matched
    protected boolean scanIdentifier(String text) {
        return scanIdentifier(text, false);
    }

    /// Consumes an identifier when its normalized value equals the expected text.
    ///
    /// The scanner is restored when the complete identifier does not match.
    ///
    /// @param text the expected identifier value
    /// @param caseSensitive whether ASCII case differences are significant
    /// @return whether the complete identifier matched
    protected boolean scanIdentifier(String text, boolean caseSensitive) {
        Objects.requireNonNull(text, "text");
        if (!lookingAtIdentifier()) {
            return false;
        }

        var start = scanner.state();
        for (var index = 0; index < text.length(); index++) {
            if (!scanIdentChar(text.charAt(index), caseSensitive)) {
                scanner.restore(start);
                return false;
            }
        }
        if (lookingAtIdentifierBody()) {
            scanner.restore(start);
            return false;
        }
        return true;
    }

    /// Tests whether an identifier equals the expected text without consuming it.
    ///
    /// Matching ignores ASCII case and accepts escapes in the source identifier.
    ///
    /// @param text the expected identifier value
    /// @return whether the complete identifier matches
    protected boolean matchesIdentifier(String text) {
        return matchesIdentifier(text, false);
    }

    /// Tests whether an identifier equals the expected text without consuming it.
    ///
    /// @param text the expected identifier value
    /// @param caseSensitive whether ASCII case differences are significant
    /// @return whether the complete identifier matches
    protected boolean matchesIdentifier(String text, boolean caseSensitive) {
        var start = scanner.state();
        var result = scanIdentifier(text, caseSensitive);
        scanner.restore(start);
        return result;
    }

    /// Consumes and requires an identifier with the expected value.
    ///
    /// @param text the expected identifier value
    /// @throws ParseException if the complete identifier does not match
    protected void expectIdentifier(String text) {
        expectIdentifier(text, "\"" + text + "\"", false);
    }

    /// Consumes and requires an identifier with the expected value.
    ///
    /// @param text the expected identifier value
    /// @param name the diagnostic name, including any desired quotation marks
    /// @param caseSensitive whether ASCII case differences are significant
    /// @throws ParseException if the complete identifier does not match
    protected void expectIdentifier(
            String text,
            String name,
            boolean caseSensitive
    ) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(name, "name");
        var start = scanner.position();
        for (var index = 0; index < text.length(); index++) {
            if (!scanIdentChar(text.charAt(index), caseSensitive)) {
                throw expectedIdentifierError(name, start);
            }
        }
        if (lookingAtIdentifierBody()) {
            throw expectedIdentifierError(name, start);
        }
    }

    /// Consumes one identifier code unit when it equals an expected value.
    ///
    /// An escaped code point that does not match restores the scanner to the
    /// backslash. Matching ignores ASCII case unless requested otherwise.
    ///
    /// @param expected the expected UTF-16 code unit
    /// @param caseSensitive whether ASCII case differences are significant
    /// @return whether a matching code unit or escape was consumed
    protected boolean scanIdentChar(int expected, boolean caseSensitive) {
        var next = scanner.peek();
        if (charactersEqual(expected, next, caseSensitive)) {
            scanner.read();
            return true;
        }
        if (next != '\\') {
            return false;
        }

        var start = scanner.state();
        if (charactersEqual(expected, escapeCharacter(), caseSensitive)) {
            return true;
        }
        scanner.restore(start);
        return false;
    }

    /// Consumes a Sass variable name without returning its dollar sign.
    ///
    /// Underscores in the name are normalized to hyphens.
    ///
    /// @return the normalized variable name
    /// @throws ParseException if a variable name does not begin here
    protected String variableName() {
        scanner.expect('$');
        return identifier(true, false);
    }

    /// Consumes a quoted plain CSS string and resolves its escapes.
    ///
    /// The returned value excludes the surrounding quotes. A backslash followed
    /// by a newline is consumed as a line continuation and contributes no text.
    ///
    /// @return the decoded string contents
    /// @throws ParseException if no quote begins here or the string is not terminated
    protected String string() {
        var quotePosition = scanner.position();
        var quote = scanner.read();
        if (quote != '\'' && quote != '"') {
            throw scanner.error("Expected string.", quotePosition, 1);
        }

        var result = new StringBuilder();
        while (true) {
            var next = scanner.peek();
            if (next == quote) {
                scanner.read();
                return result.toString();
            }
            if (next == CssCharacters.END_OF_INPUT || CssCharacters.isNewline(next)) {
                throw scanner.error("Expected " + (char) quote + ".");
            }
            if (next == '\\') {
                if (CssCharacters.isNewline(scanner.peek(1))) {
                    scanner.read();
                    scanner.read();
                } else {
                    result.appendCodePoint(escapeCharacter());
                }
            } else {
                result.append((char) scanner.read());
            }
        }
    }

    /// Consumes a nonnegative decimal integer and returns it as a double.
    ///
    /// Scientific notation is not consumed.
    ///
    /// @return the parsed numeric value
    /// @throws ParseException if no decimal digit begins at the current position
    protected double naturalNumber() {
        var start = scanner.position();
        var first = scanner.read();
        if (!CssCharacters.isDigit(first)) {
            throw scanner.error("Expected digit.", start, 1);
        }

        double result = first - '0';
        while (CssCharacters.isDigit(scanner.peek())) {
            result = result * 10 + scanner.read() - '0';
        }
        return result;
    }

    /// Returns whether a CSS number begins at the current position.
    ///
    /// @return whether the next code units match the CSS number prefix grammar
    protected boolean lookingAtNumber() {
        var first = scanner.peek();
        if (CssCharacters.isDigit(first)) {
            return true;
        }
        if (first == '.') {
            return CssCharacters.isDigit(scanner.peek(1));
        }
        if (first != '+' && first != '-') {
            return false;
        }

        var second = scanner.peek(1);
        return CssCharacters.isDigit(second)
                || second == '.' && CssCharacters.isDigit(scanner.peek(2));
    }

    /// Consumes the next code unit when it satisfies a predicate.
    ///
    /// The predicate receives [CssCharacters#END_OF_INPUT] when the scanner is
    /// at end of input.
    ///
    /// @param condition the predicate to test
    /// @return whether a code unit was consumed
    /// @throws ParseException if the predicate accepts the end-of-input sentinel
    protected boolean scanCharIf(IntPredicate condition) {
        Objects.requireNonNull(condition, "condition");
        var next = scanner.peek();
        if (!condition.test(next)) {
            return false;
        }
        scanner.read();
        return true;
    }

    /// Consumes a raw declaration value until a top-level terminator.
    ///
    /// The terminating semicolon or closing bracket is left unconsumed.
    /// Strings and loud comments retain their exact source spelling, while
    /// whitespace, identifiers, escapes, and raw URL tokens are normalized.
    ///
    /// @return the declaration value
    /// @throws ParseException if the value is empty, has mismatched brackets,
    /// or contains a malformed token
    protected String declarationValue() {
        return declarationValue(false);
    }

    /// Consumes a raw declaration value until a top-level terminator.
    ///
    /// @param allowEmpty whether an empty value is accepted
    /// @return the declaration value
    /// @throws ParseException if a required token is absent, brackets are
    /// mismatched, or a token is malformed
    protected String declarationValue(boolean allowEmpty) {
        var result = new StringBuilder();
        var brackets = new ArrayDeque<Integer>();
        var wroteNewline = false;

        declaration:
        while (true) {
            var next = scanner.peek();
            switch (next) {
                case CssCharacters.END_OF_INPUT -> {
                    break declaration;
                }
                case '\\' -> {
                    result.append(escape(true));
                    wroteNewline = false;
                }
                case '\'', '"' -> {
                    result.append(rawText(this::string));
                    wroteNewline = false;
                }
                case '/' -> {
                    if (scanner.peek(1) == '*') {
                        result.append(rawText(this::loudComment));
                    } else {
                        result.append((char) scanner.read());
                    }
                    wroteNewline = false;
                }
                case ' ', '\t' -> {
                    if (wroteNewline || !CssCharacters.isWhitespace(scanner.peek(1))) {
                        result.append(' ');
                    }
                    scanner.read();
                }
                case '\n', '\r', '\f' -> {
                    if (!CssCharacters.isNewline(previousCodeUnit())) {
                        result.append('\n');
                    }
                    scanner.read();
                    wroteNewline = true;
                }
                case '(', '{', '[' -> {
                    result.append((char) next);
                    brackets.push(opposite(scanner.read()));
                    wroteNewline = false;
                }
                case ')', '}', ']' -> {
                    if (brackets.isEmpty()) {
                        break declaration;
                    }
                    result.append((char) next);
                    scanner.expect(brackets.pop());
                    wroteNewline = false;
                }
                case ';' -> {
                    if (brackets.isEmpty()) {
                        break declaration;
                    }
                    result.append((char) scanner.read());
                }
                case 'u', 'U' -> {
                    @Nullable String url = tryUrl();
                    if (url == null) {
                        result.append((char) scanner.read());
                    } else {
                        result.append(url);
                    }
                    wroteNewline = false;
                }
                default -> {
                    if (lookingAtIdentifier()) {
                        result.append(identifier(false, false));
                    } else {
                        result.append((char) scanner.read());
                    }
                    wroteNewline = false;
                }
            }
        }

        if (!brackets.isEmpty()) {
            scanner.expect(brackets.peek());
        }
        if (!allowEmpty && result.length() == 0) {
            throw scanner.error("Expected token.");
        }
        return result.toString();
    }

    /// Attempts to consume and normalize one raw CSS URL token.
    ///
    /// The scanner is restored when the complete token cannot be represented
    /// using the raw URL grammar.
    ///
    /// @return the normalized URL token, or {@code null} after restoring input
    protected @Nullable String tryUrl() {
        var start = scanner.state();
        if (!scanIdentifier("url") || !scanner.scan('(')) {
            scanner.restore(start);
            return null;
        }

        whitespace(true);
        var result = new StringBuilder("url(");
        while (true) {
            var next = scanner.peek();
            if (next == CssCharacters.END_OF_INPUT) {
                break;
            }
            if (next == '\\') {
                result.append(escape(false));
                continue;
            }
            if (next == '%' || next == '&' || next == '#'
                    || next >= '*' && next <= '~'
                    || next >= 0x80) {
                result.append((char) scanner.read());
                continue;
            }
            if (CssCharacters.isWhitespace(next)) {
                whitespace(true);
                if (scanner.peek() != ')') {
                    break;
                }
                continue;
            }
            if (next == ')') {
                result.append((char) scanner.read());
                return result.toString();
            }
            break;
        }

        scanner.restore(start);
        return null;
    }

    /// Consumes a CSS escape and returns its normalized token spelling.
    ///
    /// @param identifierStart whether the escape occurs at an identifier start
    /// @return the normalized CSS token text
    /// @throws ParseException if the escape is incomplete or contains an
    /// invalid Unicode code point
    protected String escape(boolean identifierStart) {
        return CssIdentifierParser.escape(scanner, identifierStart);
    }

    /// Runs an operation and returns the exact source text it consumes.
    ///
    /// @param consumer the scanner operation
    /// @return the consumed source text
    protected String rawText(Runnable consumer) {
        Objects.requireNonNull(consumer, "consumer");
        var start = scanner.position();
        consumer.run();
        return scanner.substring(start);
    }

    /// Consumes a CSS escape and returns the represented code point.
    ///
    /// End of input, zero, surrogate code points, and values at or beyond the
    /// CSS maximum allowed code point produce U+FFFD.
    ///
    /// @return the represented Unicode code point
    /// @throws ParseException if a newline immediately follows the backslash
    protected int escapeCharacter() {
        scanner.expect('\\');
        var next = scanner.peek();
        if (next == CssCharacters.END_OF_INPUT) {
            return 0xFFFD;
        }
        if (CssCharacters.isNewline(next)) {
            throw scanner.error("Expected escape sequence.");
        }
        if (!CssCharacters.isHex(next)) {
            return scanner.read();
        }

        var value = 0;
        for (var count = 0; count < 6 && CssCharacters.isHex(scanner.peek()); count++) {
            value = (value << 4) + CssCharacters.hexValue(scanner.read());
        }
        if (CssCharacters.isWhitespace(scanner.peek())) {
            scanner.read();
        }

        return value == 0
                || value >= 0xD800 && value <= 0xDFFF
                || value >= CssCharacters.MAX_ALLOWED_CODE_POINT
                ? 0xFFFD
                : value;
    }

    /// Returns the source code unit immediately before the scanner position.
    ///
    /// @return the previous code unit, or [CssCharacters#END_OF_INPUT] at the start
    private int previousCodeUnit() {
        return scanner.position() == 0
                ? CssCharacters.END_OF_INPUT
                : scanner.source().content().charAt(scanner.position() - 1);
    }

    /// Returns the closing bracket paired with an opening bracket.
    ///
    /// @param opening an opening bracket
    /// @return the corresponding closing bracket
    /// @throws IllegalArgumentException if {@code opening} is not an opening bracket
    protected static int opposite(int opening) {
        return switch (opening) {
            case '(' -> ')';
            case '{' -> '}';
            case '[' -> ']';
            default -> throw new IllegalArgumentException(
                    "Not an opening bracket: " + (char) opening
            );
        };
    }

    /// Returns whether two code units are equal under the requested case rule.
    ///
    /// @param expected the expected code unit
    /// @param actual the actual code unit
    /// @param caseSensitive whether ASCII case differences are significant
    /// @return whether the code units match
    private static boolean charactersEqual(
            int expected,
            int actual,
            boolean caseSensitive
    ) {
        if (caseSensitive || expected == actual) {
            return expected == actual;
        }
        if ((expected ^ actual) != 0x20) {
            return false;
        }
        var upperExpected = expected & ~0x20;
        return upperExpected >= 'A' && upperExpected <= 'Z';
    }

    /// Creates a diagnostic for an expected identifier at a captured position.
    ///
    /// @param name the identifier name used in the message
    /// @param start the identifier start offset
    /// @return the parse failure
    private ParseException expectedIdentifierError(String name, int start) {
        var length = start == scanner.source().length() ? 0 : 1;
        return scanner.error("Expected " + name + ".", start, length);
    }
}
