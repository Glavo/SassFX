// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;

/// Parses SCSS stylesheet roots, plain style rules, and statement comments.
@NotNullByDefault
final class ScssParser extends Parser {
    /// Creates a parser for an indexed SCSS source.
    ///
    /// @param source the SCSS source to parse
    ScssParser(SourceFile source) {
        super(source);
    }

    /// Parses the complete source as an SCSS stylesheet.
    ///
    /// A byte-order mark is accepted only at the beginning. Whitespace and
    /// empty semicolon statements do not produce syntax nodes.
    ///
    /// @return the immutable stylesheet syntax tree
    /// @throws ParseException if a comment is malformed or another statement
    /// production is encountered
    Stylesheet parse() {
        var start = scanner.state();
        scanner.scan(0xFEFF);
        var children = statements();
        scanner.expectDone();
        return new Stylesheet(children, scanner.spanFrom(start), false);
    }

    /// Parses top-level style rules, comment statements, and empty statements.
    ///
    /// @return the parsed statements in source order
    /// @throws ParseException if another statement production begins
    private ArrayList<SassStatement> statements() {
        var statements = new ArrayList<SassStatement>();
        whitespaceWithoutComments(true);
        while (!scanner.isDone()) {
            switch (scanner.peek()) {
                case '/' -> {
                    if (scanner.peek(1) == '/') {
                        statements.add(silentCommentStatement());
                    } else if (scanner.peek(1) == '*') {
                        statements.add(loudCommentStatement());
                    } else {
                        throw scanner.error("Expected stylesheet statement.");
                    }
                    whitespaceWithoutComments(true);
                }
                case ';' -> {
                    scanner.read();
                    whitespaceWithoutComments(true);
                }
                case '@', '$' -> throw scanner.error(
                        "This stylesheet statement is not available."
                );
                default -> statements.add(styleRule());
            }
        }
        return statements;
    }

    /// Parses a style rule whose selector contains no Sass interpolation.
    ///
    /// @return the style rule node
    /// @throws ParseException if the selector or child block is malformed or
    /// the block contains an unsupported child statement
    private StyleRule styleRule() {
        var start = scanner.state();
        var selectorStart = scanner.state();
        var selectorText = plainStyleRuleSelector();
        if (selectorText.isEmpty()) {
            throw scanner.error("Expected selector.");
        }
        var selector = Interpolation.plain(
                selectorText,
                scanner.spanFrom(selectorStart)
        );
        var children = styleRuleChildren();
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new StyleRule(selector, children, span);
    }

    /// Consumes selector-like text up to the opening child brace.
    ///
    /// Strings and comments retain their source spelling. Identifier escapes
    /// are normalized because the selector will be parsed after evaluation.
    ///
    /// @return the selector source preceding the child block
    private String plainStyleRuleSelector() {
        var result = new StringBuilder();
        var brackets = new ArrayDeque<Integer>();

        selector:
        while (true) {
            var next = scanner.peek();
            switch (next) {
                case CssCharacters.END_OF_INPUT, '!', ';', '{', '}' -> {
                    break selector;
                }
                case '\\' -> {
                    result.append((char) scanner.read());
                    result.append((char) scanner.read());
                }
                case '\'', '"' -> result.append(rawSelectorStringToken());
                case '/' -> {
                    if (scanner.peek(1) == '*') {
                        result.append(rawText(this::loudComment));
                    } else if (scanner.peek(1) == '/') {
                        result.append(rawText(this::silentComment));
                    } else {
                        result.append((char) scanner.read());
                    }
                }
                case '#' -> {
                    if (scanner.peek(1) == '{') {
                        throw scanner.error(
                                "Selector interpolation is not available.",
                                scanner.position(),
                                2
                        );
                    }
                    result.append((char) scanner.read());
                }
                case '(', '[' -> {
                    var opening = scanner.read();
                    result.append((char) opening);
                    brackets.push(opposite(opening));
                }
                case ')', ']' -> {
                    if (brackets.isEmpty()) {
                        throw scanner.error("Unexpected \"" + (char) next + "\".");
                    }
                    int closing = brackets.pop();
                    scanner.expect(closing);
                    result.append((char) closing);
                }
                case 'u', 'U' -> {
                    var beforeName = scanner.state();
                    var name = identifier(false, false);
                    if (!name.equals("url") && !name.equals("url-prefix")) {
                        result.append(name);
                        continue;
                    }

                    @Nullable String url = tryPlainUrlContents(name);
                    if (url == null) {
                        scanner.restore(beforeName);
                        result.append((char) scanner.read());
                    } else {
                        result.append(url);
                    }
                }
                default -> {
                    if (lookingAtIdentifier()) {
                        result.append(identifier(false, false));
                    } else {
                        result.append((char) scanner.read());
                    }
                }
            }
        }
        return result.toString();
    }

    /// Consumes a quoted selector string token without resolving its escapes.
    ///
    /// Backslash-newline continuations preserve their complete source spelling,
    /// including both code units of a CRLF sequence.
    ///
    /// @return the exact consumed string token
    /// @throws ParseException if the string is malformed or contains Sass interpolation
    private String rawSelectorStringToken() {
        var quotePosition = scanner.position();
        var quote = scanner.read();
        if (quote != '\'' && quote != '"') {
            throw scanner.error("Expected string.", quotePosition, 1);
        }

        var result = new StringBuilder().append((char) quote);
        while (true) {
            var next = scanner.peek();
            if (next == quote) {
                result.append((char) scanner.read());
                return result.toString();
            }
            if (next == CssCharacters.END_OF_INPUT || CssCharacters.isNewline(next)) {
                throw scanner.error("Expected " + (char) quote + ".");
            }
            if (next == '\\') {
                var second = scanner.peek(1);
                if (CssCharacters.isNewline(second)) {
                    result.append((char) scanner.read());
                    result.append((char) scanner.read());
                    if (second == '\r' && scanner.scan('\n')) {
                        result.append('\n');
                    }
                } else {
                    result.append(rawText(this::escapeCharacter));
                }
            } else if (next == '#' && scanner.peek(1) == '{') {
                throw scanner.error(
                        "Selector interpolation is not available.",
                        scanner.position(),
                        2
                );
            } else {
                result.append((char) scanner.read());
            }
        }
    }

    /// Attempts to consume raw URL contents after an already-consumed name.
    ///
    /// On ordinary grammar failure, the scanner is restored to its position
    /// immediately after the name. Sass interpolation is rejected explicitly.
    ///
    /// @param name the normalized, case-sensitive function name
    /// @return the normalized URL token, or {@code null} after restoring input
    private @Nullable String tryPlainUrlContents(String name) {
        var beginningOfContents = scanner.state();
        if (!scanner.scan('(')) {
            return null;
        }

        whitespaceWithoutComments(true);
        var result = new StringBuilder(name).append('(');
        while (true) {
            var next = scanner.peek();
            if (next == CssCharacters.END_OF_INPUT) {
                break;
            }
            if (next == '\\') {
                result.append(escape(false));
                continue;
            }
            if (next == '#' && scanner.peek(1) == '{') {
                throw scanner.error(
                        "Selector interpolation is not available.",
                        scanner.position(),
                        2
                );
            }
            if (next == '!' || next == '%' || next == '&' || next == '#'
                    || next >= '*' && next <= '~'
                    || next >= 0x80) {
                result.append((char) scanner.read());
                continue;
            }
            if (CssCharacters.isWhitespace(next)) {
                whitespaceWithoutComments(true);
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

        scanner.restore(beginningOfContents);
        return null;
    }

    /// Parses a style rule block containing comment and empty statements.
    ///
    /// @return the child statements in source order
    /// @throws ParseException if the block is unterminated or another child
    /// statement production begins
    private ArrayList<SassStatement> styleRuleChildren() {
        scanner.expect('{');
        var children = new ArrayList<SassStatement>();
        whitespaceWithoutComments(true);
        while (true) {
            switch (scanner.peek()) {
                case CssCharacters.END_OF_INPUT -> throw scanner.error("Expected \"}\".");
                case '/' -> {
                    if (scanner.peek(1) == '/') {
                        children.add(silentCommentStatement());
                    } else if (scanner.peek(1) == '*') {
                        children.add(loudCommentStatement());
                    } else {
                        throw scanner.error("Expected style-rule statement.");
                    }
                    whitespaceWithoutComments(true);
                }
                case ';' -> {
                    scanner.read();
                    whitespaceWithoutComments(true);
                }
                case '}' -> {
                    scanner.read();
                    return children;
                }
                default -> throw scanner.error("Expected style-rule statement.");
            }
        }
    }

    /// Parses one possibly multi-line Sass-style silent comment block.
    ///
    /// A following comment line is joined when only spaces or tabs occur
    /// between its line terminator and opening slashes.
    ///
    /// @return the silent comment node
    private SilentComment silentCommentStatement() {
        var start = scanner.state();
        scanner.expect("//");
        do {
            while (!scanner.isDone()
                    && !CssCharacters.isNewline(scanner.read())) {
                // The consumed source text is retained verbatim below.
            }
            if (scanner.isDone()) {
                break;
            }
            spaces();
        } while (scanner.scan("//"));

        return new SilentComment(
                scanner.substring(start.position()),
                scanner.spanFrom(start)
        );
    }

    /// Parses one CSS-style loud comment and normalizes its line endings.
    ///
    /// @return the loud comment node
    /// @throws ParseException if the comment is unterminated or contains an
    /// expression interpolation
    private LoudComment loudCommentStatement() {
        var start = scanner.state();
        scanner.expect("/*");
        var text = new StringBuilder("/*");

        while (true) {
            var next = scanner.peek();
            switch (next) {
                case CssCharacters.END_OF_INPUT ->
                        throw scanner.error("Unexpected end of input.");
                case '#' -> {
                    if (scanner.peek(1) == '{') {
                        throw scanner.error(
                                "Loud-comment interpolation is not available.",
                                scanner.position(),
                                2
                        );
                    }
                    text.append((char) scanner.read());
                }
                case '*' -> {
                    text.append((char) scanner.read());
                    if (scanner.peek() == '/') {
                        text.append((char) scanner.read());
                        var span = scanner.spanFrom(start);
                        return new LoudComment(Interpolation.plain(text.toString(), span));
                    }
                }
                case '\r' -> {
                    scanner.read();
                    if (scanner.peek() != '\n') {
                        text.append('\n');
                    }
                }
                case '\f' -> {
                    scanner.read();
                    text.append('\n');
                }
                default -> text.append((char) scanner.read());
            }
        }
    }
}
