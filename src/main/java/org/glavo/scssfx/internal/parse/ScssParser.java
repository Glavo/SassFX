// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.ast.Declaration;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.InterpolationBuffer;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/// Parses SCSS stylesheets containing declarations, nested properties, style rules, and comments.
@NotNullByDefault
final class ScssParser extends SassExpressionParser {
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

    /// Parses a style rule whose selector may contain Sass interpolation.
    ///
    /// @return the style rule node
    /// @throws ParseException if the selector or child block is malformed or
    /// the block contains an unsupported child statement
    private StyleRule styleRule() {
        var start = scanner.state();
        return styleRule(new InterpolationBuffer(), start);
    }

    /// Parses a style rule after declaration lookahead consumed a selector prefix.
    ///
    /// @param selectorPrefix the normalized selector text already consumed
    /// @param start          the beginning of the complete selector
    /// @return the style rule node
    /// @throws ParseException if the remaining selector or child block is malformed
    private StyleRule styleRule(
            InterpolationBuffer selectorPrefix,
            ScannerState start
    ) {
        var selector = styleRuleSelector();
        selectorPrefix.add(selector);
        selector = selectorPrefix.interpolation(scanner.spanFrom(start));
        if (selector.parts().isEmpty()) {
            throw scanner.error("Expected selector.");
        }
        var children = statementBlock(false);
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
    private Interpolation styleRuleSelector() {
        var start = scanner.state();
        var result = new InterpolationBuffer();
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
                case '\'', '"' -> result.add(interpolatedStringToken());
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
                        singleInterpolation(result);
                    } else {
                        result.append((char) scanner.read());
                    }
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
        return result.interpolation(scanner.spanFrom(start));
    }

    /// Attempts to consume raw URL contents after an already-consumed name.
    ///
    /// On ordinary grammar failure or interpolation, the scanner is restored
    /// to its position immediately after the name so the caller can parse the
    /// contents as ordinary interpolated selector text.
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
                scanner.restore(beginningOfContents);
                return null;
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

    /// Parses a braced statement block.
    ///
    /// Style-rule blocks accept declarations and nested style rules. Nested
    /// property blocks accept declarations only. Comments are retained as
    /// statements and empty semicolon statements are discarded.
    ///
    /// @param declarationsOnly whether style-rule fallback is forbidden
    /// @return the child statements in source order
    /// @throws ParseException if the block is unterminated or a child is malformed
    private ArrayList<SassStatement> statementBlock(boolean declarationsOnly) {
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
                        children.add(declarationsOnly
                                ? declarationChild()
                                : declarationOrStyleRule());
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
                case '@', '$' -> throw scanner.error(
                        "This block statement is not available."
                );
                default -> {
                    children.add(declarationsOnly
                            ? declarationChild()
                            : declarationOrStyleRule());
                    whitespaceWithoutComments(true);
                }
            }
        }
    }

    /// Parses a declaration when possible and otherwise reparses from the same
    /// source position as a nested style rule.
    ///
    /// @return the parsed declaration or style rule
    private SassStatement declarationOrStyleRule() {
        var start = scanner.state();
        var selectorPrefix = new InterpolationBuffer();
        @Nullable Declaration declaration = tryDeclaration(
                start,
                false,
                selectorPrefix
        );
        if (declaration != null) {
            return declaration;
        }
        return styleRule(selectorPrefix, start);
    }

    /// Parses one child of a nested-property block without selector fallback.
    ///
    /// @return the nested declaration
    /// @throws ParseException if the child is not a property declaration
    private Declaration declarationChild() {
        var start = scanner.state();
        @Nullable Declaration declaration = tryDeclaration(
                start,
                true,
                new InterpolationBuffer()
        );
        if (declaration == null) {
            throw scanner.error("Expected declaration.");
        }
        return declaration;
    }

    /// Attempts to parse a property declaration beginning at {@code start}.
    ///
    /// In an ordinary style-rule block, ambiguous identifier-and-colon syntax
    /// returns {@code null} after retaining the normalized prefix so the
    /// remaining source can be parsed as a selector. In a nested-property
    /// block, the same syntax must be a declaration and failures are reported
    /// directly.
    ///
    /// @param start            the beginning of the candidate statement
    /// @param declarationsOnly whether selector fallback is forbidden
    /// @param nameBuffer       the normalized name and selector fallback prefix
    /// @return the declaration, or {@code null} when selector parsing must be attempted
    /// @throws ParseException if the source is unambiguously a malformed declaration
    private @Nullable Declaration tryDeclaration(
            ScannerState start,
            boolean declarationsOnly,
            InterpolationBuffer nameBuffer
    ) {
        var startsWithPunctuation = false;
        if (lookingAtPotentialPropertyHack()) {
            startsWithPunctuation = true;
            nameBuffer.append((char) scanner.read());
            nameBuffer.append(rawText(() -> whitespace(false)));
        }

        if (!lookingAtInterpolatedIdentifier()) {
            if (declarationsOnly) {
                throw scanner.error("Expected identifier.");
            }
            return null;
        }
        var identifier = interpolatedIdentifier();
        nameBuffer.add(identifier);
        var declarationNameEnd = scanner.state();

        if (!startsWithPunctuation
                && identifier.isPlain()
                && scanner.peek() == '.'
                && scanner.peek(1) == '$') {
            throw scanner.error("Namespaced variable declarations are not available.");
        }
        if (!declarationsOnly
                && scanner.peek() == '/'
                && scanner.peek(1) == '*') {
            nameBuffer.append(rawText(this::loudComment));
        }

        var preColonWhitespace = rawText(() -> whitespace(false));
        var beforeColon = scanner.state();
        if (!scanner.scan(':')) {
            if (declarationsOnly) {
                scanner.expect(':');
            }
            if (!preColonWhitespace.isEmpty()) {
                nameBuffer.append(' ');
            }
            return null;
        }

        var name = nameBuffer.interpolation(scanner.spanFrom(
                start,
                declarationsOnly ? declarationNameEnd : beforeColon
        ));
        var customProperty = name.initialPlain().startsWith("--");
        if (customProperty) {
            if (declarationsOnly) {
                throw scanner.error(
                        "Declarations whose names begin with \"--\" may not be nested.",
                        name.span().start().offset(),
                        name.span().text().length()
                );
            }

            Interpolation rawValue;
            if (atEndOfStatement()) {
                rawValue = new Interpolation(
                        List.of(),
                        scanner.source().span(scanner.position(), scanner.position())
                );
            } else {
                rawValue = interpolatedDeclarationValue();
            }
            expectStatementSeparator();
            return Declaration.raw(
                    name,
                    new StringExpression(rawValue, false),
                    scanner.spanFrom(start)
            );
        }

        if (!declarationsOnly && scanner.scan(':')) {
            nameBuffer.append(preColonWhitespace);
            nameBuffer.append("::");
            return null;
        }

        var postColonWhitespace = rawText(() -> whitespace(false));
        if (scanner.peek() == '{') {
            return nestedDeclaration(name, null, start);
        }

        var couldBeSelector = !declarationsOnly
                && postColonWhitespace.isEmpty()
                && lookingAtInterpolatedIdentifier();
        var beforeDeclaration = scanner.state();
        SassExpression value;
        try {
            value = expression();
            if (scanner.peek() == '{') {
                if (couldBeSelector) {
                    expectStatementSeparator();
                }
            } else if (!atEndOfStatement()) {
                expectStatementSeparator();
            }
        } catch (ParseException failure) {
            if (!couldBeSelector) {
                throw failure;
            }

            scanner.restore(beforeDeclaration);
            var additional = almostAnyValue();
            if (scanner.peek() == ';') {
                throw failure;
            }
            nameBuffer.append(preColonWhitespace);
            nameBuffer.append(':');
            nameBuffer.append(postColonWhitespace);
            nameBuffer.add(additional);
            return null;
        }

        if (scanner.peek() == '{') {
            return nestedDeclaration(name, value, start);
        }
        expectStatementSeparator();
        return Declaration.sassScript(name, value, scanner.spanFrom(start));
    }

    /// Parses the braced children of a nested property declaration.
    ///
    /// @param name  the already-parsed property name
    /// @param value the optional value preceding the child block
    /// @param start the beginning of the declaration
    /// @return the nested declaration spanning through its closing brace
    private Declaration nestedDeclaration(
            Interpolation name,
            @Nullable SassExpression value,
            ScannerState start
    ) {
        var children = statementBlock(true);
        return Declaration.nested(name, value, children, scanner.spanFrom(start));
    }

    /// Returns whether declaration-hack punctuation begins at the scanner position.
    ///
    /// @return whether a punctuation-prefixed property name may begin here
    private boolean lookingAtPotentialPropertyHack() {
        return switch (scanner.peek()) {
            case ':', '*', '.' -> true;
            case '#' -> scanner.peek(1) != '{';
            default -> false;
        };
    }

    /// Returns whether the scanner is at an SCSS statement boundary.
    ///
    /// An opening brace is included because it may begin nested declaration
    /// children after a successfully parsed value.
    ///
    /// @return whether the current code unit ends a statement value
    private boolean atEndOfStatement() {
        return switch (scanner.peek()) {
            case CssCharacters.END_OF_INPUT, ';', '}', '{' -> true;
            default -> false;
        };
    }

    /// Consumes trailing whitespace and requires an SCSS statement separator.
    ///
    /// The accepted semicolon is left unconsumed so the surrounding statement
    /// loop can discard it consistently with empty semicolon statements.
    ///
    /// @throws ParseException if the next code unit cannot end the statement
    private void expectStatementSeparator() {
        whitespaceWithoutComments(true);
        if (scanner.isDone() || scanner.peek() == ';' || scanner.peek() == '}') {
            return;
        }
        scanner.expect(';');
    }

    /// Consumes selector-like value text to determine whether a semicolon
    /// follows an ambiguous declaration candidate.
    ///
    /// Strings, comments, interpolation, raw URLs, and parentheses are scanned
    /// transactionally. Top-level braces, semicolons, and exclamation marks are
    /// left unconsumed.
    ///
    /// @return the normalized selector suffix consumed by the lookahead
    private Interpolation almostAnyValue() {
        var start = scanner.state();
        var buffer = new InterpolationBuffer();
        var brackets = new ArrayDeque<Integer>();

        value:
        while (true) {
            var next = scanner.peek();
            switch (next) {
                case '\\' -> {
                    buffer.append((char) scanner.read());
                    buffer.append((char) scanner.read());
                }
                case '\'', '"' -> buffer.add(interpolatedStringToken());
                case '/' -> {
                    if (scanner.peek(1) == '*') {
                        buffer.append(rawText(this::loudComment));
                    } else if (scanner.peek(1) == '/') {
                        buffer.append(rawText(this::silentComment));
                    } else {
                        buffer.append((char) scanner.read());
                    }
                }
                case '#' -> {
                    if (scanner.peek(1) == '{') {
                        buffer.add(interpolatedIdentifier());
                    } else {
                        buffer.append((char) scanner.read());
                    }
                }
                case CssCharacters.END_OF_INPUT, '!', ';', '{', '}' -> {
                    break value;
                }
                case 'u', 'U' -> {
                    var beforeUrl = scanner.state();
                    var identifier = identifier(false, false);
                    if (!identifier.equals("url") && !identifier.equals("url-prefix")) {
                        buffer.append(identifier);
                        continue;
                    }

                    @Nullable Interpolation url = tryInterpolatedUrlContents(
                            beforeUrl,
                            identifier
                    );
                    if (url == null) {
                        scanner.restore(beforeUrl);
                        buffer.append((char) scanner.read());
                    } else {
                        buffer.add(url);
                    }
                }
                case '(', '[' -> {
                    var opening = scanner.read();
                    buffer.append((char) opening);
                    brackets.push(opposite(opening));
                }
                case ')', ']' -> {
                    if (brackets.isEmpty()) {
                        throw scanner.error("Unexpected \"" + (char) next + "\".");
                    }
                    int closing = brackets.pop();
                    scanner.expect(closing);
                    buffer.append((char) closing);
                }
                default -> {
                    if (lookingAtIdentifier()) {
                        buffer.append(identifier(false, false));
                    } else {
                        buffer.append((char) scanner.read());
                    }
                }
            }
        }
        return buffer.interpolation(scanner.spanFrom(start));
    }

    /// Parses a raw custom-property value with Sass interpolation.
    ///
    /// The terminating top-level semicolon or closing brace remains
    /// unconsumed. Whitespace and URL tokens are normalized while strings and
    /// loud comments retain their token spelling. Adjacent slashes are raw
    /// value text rather than silent comments.
    ///
    /// @return the nonempty raw value interpolation
    /// @throws ParseException if brackets, strings, comments, or interpolation are malformed
    private Interpolation interpolatedDeclarationValue() {
        var start = scanner.state();
        var buffer = new InterpolationBuffer();
        var brackets = new ArrayDeque<Integer>();
        var wroteNewline = false;

        value:
        while (true) {
            var next = scanner.peek();
            switch (next) {
                case '\\' -> {
                    buffer.append(escape(true));
                    wroteNewline = false;
                }
                case '\'', '"' -> {
                    buffer.add(interpolatedStringToken());
                    wroteNewline = false;
                }
                case '/' -> {
                    if (scanner.peek(1) == '*') {
                        buffer.append(rawText(this::loudComment));
                    } else {
                        buffer.append((char) scanner.read());
                    }
                    wroteNewline = false;
                }
                case '#' -> {
                    if (scanner.peek(1) == '{') {
                        buffer.add(interpolatedIdentifier());
                    } else {
                        buffer.append((char) scanner.read());
                    }
                    wroteNewline = false;
                }
                case ' ', '\t' -> {
                    if (!wroteNewline && CssCharacters.isWhitespace(scanner.peek(1))) {
                        scanner.read();
                    } else {
                        buffer.append((char) scanner.read());
                    }
                }
                case '\n', '\r', '\f' -> {
                    var previous = scanner.position() == 0
                            ? CssCharacters.END_OF_INPUT
                            : scanner.source().content().charAt(scanner.position() - 1);
                    if (!CssCharacters.isNewline(previous)) {
                        buffer.append('\n');
                    }
                    scanner.read();
                    wroteNewline = true;
                }
                case '(', '{', '[' -> {
                    var opening = scanner.read();
                    buffer.append((char) opening);
                    brackets.push(opposite(opening));
                    wroteNewline = false;
                }
                case ')', '}', ']' -> {
                    if (brackets.isEmpty()) {
                        break value;
                    }
                    int closing = brackets.pop();
                    scanner.expect(closing);
                    buffer.append((char) closing);
                    wroteNewline = false;
                }
                case ';' -> {
                    if (brackets.isEmpty()) {
                        break value;
                    }
                    buffer.append((char) scanner.read());
                    wroteNewline = false;
                }
                case 'u', 'U' -> {
                    var beforeUrl = scanner.state();
                    var identifier = identifier(false, false);
                    if (!identifier.equals("url") && !identifier.equals("url-prefix")) {
                        buffer.append(identifier);
                        wroteNewline = false;
                        continue;
                    }

                    @Nullable Interpolation url = tryInterpolatedUrlContents(
                            beforeUrl,
                            identifier
                    );
                    if (url == null) {
                        scanner.restore(beforeUrl);
                        buffer.append((char) scanner.read());
                    } else {
                        buffer.add(url);
                    }
                    wroteNewline = false;
                }
                case CssCharacters.END_OF_INPUT -> {
                    break value;
                }
                default -> {
                    if (lookingAtIdentifier()) {
                        buffer.append(identifier(false, false));
                    } else {
                        buffer.append((char) scanner.read());
                    }
                    wroteNewline = false;
                }
            }
        }

        if (!brackets.isEmpty()) {
            scanner.expect(brackets.peek());
        }
        if (buffer.isEmpty()) {
            throw scanner.error("Expected token.");
        }
        return buffer.interpolation(scanner.spanFrom(start));
    }

    /// Attempts to parse a normalized raw URL token that may contain interpolation.
    ///
    /// The scanner must be positioned immediately after the already-consumed
    /// function name. Failure restores that position so the caller may reparse
    /// the name and contents as ordinary declaration text.
    ///
    /// @param start the position before the function name
    /// @param name  the normalized function name
    /// @return the URL interpolation, or {@code null} after restoring input
    private @Nullable Interpolation tryInterpolatedUrlContents(
            ScannerState start,
            String name
    ) {
        var beginningOfContents = scanner.state();
        if (!scanner.scan('(')) {
            return null;
        }

        whitespaceWithoutComments(true);
        var buffer = new InterpolationBuffer();
        buffer.append(name);
        buffer.append('(');
        while (true) {
            var next = scanner.peek();
            if (next == CssCharacters.END_OF_INPUT) {
                break;
            }
            if (next == '\\') {
                buffer.append(escape(false));
                continue;
            }
            if (next == '#' && scanner.peek(1) == '{') {
                singleInterpolation(buffer);
                continue;
            }
            if (next == '!'
                    || next == '%'
                    || next == '&'
                    || next == '#'
                    || next >= '*' && next <= '~'
                    || next >= 0x80) {
                buffer.append((char) scanner.read());
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
                buffer.append((char) scanner.read());
                return buffer.interpolation(scanner.spanFrom(start));
            }
            break;
        }

        scanner.restore(beginningOfContents);
        return null;
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
    /// @throws ParseException if the comment is unterminated or an embedded
    /// expression is malformed
    private LoudComment loudCommentStatement() {
        var start = scanner.state();
        scanner.expect("/*");
        var text = new InterpolationBuffer();
        text.append("/*");

        while (true) {
            var next = scanner.peek();
            switch (next) {
                case CssCharacters.END_OF_INPUT -> throw scanner.error("Unexpected end of input.");
                case '#' -> {
                    if (scanner.peek(1) == '{') {
                        singleInterpolation(text);
                    } else {
                        text.append((char) scanner.read());
                    }
                }
                case '*' -> {
                    text.append((char) scanner.read());
                    if (scanner.peek() == '/') {
                        text.append((char) scanner.read());
                        var span = scanner.spanFrom(start);
                        return new LoudComment(text.interpolation(span));
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
