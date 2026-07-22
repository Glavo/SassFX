// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.ClassSelector;
import org.glavo.scssfx.internal.ast.selector.Combinator;
import org.glavo.scssfx.internal.ast.selector.ComplexSelector;
import org.glavo.scssfx.internal.ast.selector.ComplexSelectorComponent;
import org.glavo.scssfx.internal.ast.selector.CompoundSelector;
import org.glavo.scssfx.internal.ast.selector.IdSelector;
import org.glavo.scssfx.internal.ast.selector.OtherSimpleSelector;
import org.glavo.scssfx.internal.ast.selector.ParentSelector;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.ast.selector.SimpleSelector;
import org.glavo.scssfx.internal.ast.selector.TypeSelector;
import org.glavo.scssfx.internal.ast.selector.UniversalSelector;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Parses resolved selector text into a selector AST.
@ApiStatus.Internal
@NotNullByDefault
public final class SelectorParser {
    /// Contains the selector source text.
    private final String text;

    /// Contains the span covering the complete selector text.
    private final SourceSpan baseSpan;

    /// Contains the current UTF-16 offset into {@link #text}.
    private int position;

    /// Creates a parser for one selector string.
    ///
    /// @param text     the selector source
    /// @param baseSpan the span covering that source
    private SelectorParser(String text, SourceSpan baseSpan) {
        this.text = Objects.requireNonNull(text, "text");
        this.baseSpan = Objects.requireNonNull(baseSpan, "baseSpan");
    }

    /// Parses a selector list.
    ///
    /// @param text the selector source after interpolation
    /// @param span the span covering that text
    /// @return the parsed selector list
    /// @throws SassValueException if the selector is invalid
    public static SelectorList parse(String text, SourceSpan span) {
        return new SelectorParser(text, span).parseList();
    }

    /// Parses the complete selector list.
    private SelectorList parseList() {
        whitespace();
        var components = new ArrayList<ComplexSelector>();
        components.add(parseComplex());
        while (scan(',')) {
            whitespace();
            components.add(parseComplex());
        }
        whitespace();
        if (!isDone()) {
            throw error("Expected end of selector.");
        }
        return new SelectorList(components, baseSpan);
    }

    /// Parses one complex selector.
    private ComplexSelector parseComplex() {
        var start = position;
        var leading = new ArrayList<Combinator>();
        whitespace();
        while (true) {
            @Nullable Combinator combinator = tryCombinator();
            if (combinator == null) {
                break;
            }
            leading.add(combinator);
            whitespace();
        }

        var components = new ArrayList<ComplexSelectorComponent>();
        if (!lookingAtSimple()) {
            if (leading.isEmpty()) {
                throw error("Expected selector.");
            }
            return new ComplexSelector(leading, List.of(), spanFrom(start));
        }

        while (lookingAtSimple()) {
            var compoundStart = position;
            var compound = parseCompound();
            var trailing = new ArrayList<Combinator>();
            whitespace();
            while (true) {
                @Nullable Combinator combinator = tryCombinator();
                if (combinator == null) {
                    break;
                }
                trailing.add(combinator);
                whitespace();
            }
            components.add(new ComplexSelectorComponent(
                    compound,
                    trailing,
                    spanFrom(compoundStart)
            ));
            if (!lookingAtSimple()) {
                break;
            }
        }
        return new ComplexSelector(leading, components, spanFrom(start));
    }

    /// Parses one compound selector.
    private CompoundSelector parseCompound() {
        var start = position;
        var simples = new ArrayList<SimpleSelector>();
        simples.add(parseSimple());
        while (!isDone()
                && !isWhitespace(peek())
                && peek() != ','
                && peek() != '>'
                && peek() != '+'
                && peek() != '~'
                && lookingAtSimple()) {
            simples.add(parseSimple());
        }
        return new CompoundSelector(simples, spanFrom(start));
    }

    /// Parses one simple selector.
    private SimpleSelector parseSimple() {
        var start = position;
        var next = peek();
        if (next == '&') {
            read();
            @Nullable String suffix = null;
            if (isIdentStart(peek()) || peek() == '\\' || peek() == '-') {
                suffix = readIdentifierBody();
            }
            return new ParentSelector(suffix, spanFrom(start));
        }
        if (next == '.') {
            read();
            return new ClassSelector(readIdentifier(), spanFrom(start));
        }
        if (next == '#') {
            read();
            return new IdSelector(readIdentifier(), spanFrom(start));
        }
        if (next == '*') {
            read();
            return new UniversalSelector(spanFrom(start));
        }
        if (next == ':' || next == '[' || next == '%') {
            return new OtherSimpleSelector(readOpaqueSimple(), spanFrom(start));
        }
        if (isIdentStart(next) || next == '\\' || next == '-') {
            var name = readIdentifier();
            if (peek() == '|') {
                // Namespaced type/universal: keep opaque for now.
                var opaque = new StringBuilder(name);
                opaque.append((char) read());
                if (peek() == '*') {
                    opaque.append((char) read());
                } else if (isIdentStart(peek()) || peek() == '\\' || peek() == '-') {
                    opaque.append(readIdentifier());
                }
                return new OtherSimpleSelector(opaque.toString(), spanFrom(start));
            }
            return new TypeSelector(name, spanFrom(start));
        }
        throw error("Expected selector.");
    }

    /// Reads an opaque simple selector beginning with `:`, `[`, or `%`.
    private String readOpaqueSimple() {
        var start = position;
        var next = read();
        if (next == '[') {
            var depth = 1;
            while (!isDone() && depth > 0) {
                var character = read();
                if (character == '[') {
                    depth++;
                } else if (character == ']') {
                    depth--;
                } else if (character == '"' || character == '\'') {
                    readQuoted(character);
                }
            }
            return text.substring(start, position);
        }
        if (next == ':') {
            if (peek() == ':') {
                read();
            }
            if (isIdentStart(peek()) || peek() == '\\' || peek() == '-') {
                readIdentifier();
            }
            if (peek() == '(') {
                readBalanced('(', ')');
            }
            return text.substring(start, position);
        }
        // placeholder %name
        if (isIdentStart(peek()) || peek() == '\\' || peek() == '-') {
            readIdentifier();
        }
        return text.substring(start, position);
    }

    /// Attempts to read one combinator token.
    private @Nullable Combinator tryCombinator() {
        return switch (peek()) {
            case '>' -> {
                read();
                yield Combinator.CHILD;
            }
            case '+' -> {
                read();
                yield Combinator.NEXT_SIBLING;
            }
            case '~' -> {
                read();
                yield Combinator.FOLLOWING_SIBLING;
            }
            default -> null;
        };
    }

    /// Returns whether a simple selector can begin here.
    private boolean lookingAtSimple() {
        var next = peek();
        return next == '&' || next == '.' || next == '#' || next == '*'
                || next == ':' || next == '[' || next == '%'
                || isIdentStart(next) || next == '\\' || next == '-';
    }

    /// Reads a CSS identifier.
    private String readIdentifier() {
        if (!isIdentStart(peek()) && peek() != '\\' && peek() != '-') {
            throw error("Expected identifier.");
        }
        return readIdentifierBody();
    }

    /// Reads the body of an identifier, including escapes.
    private String readIdentifierBody() {
        var result = new StringBuilder();
        while (true) {
            var next = peek();
            if (isIdent(next)) {
                result.append((char) read());
                continue;
            }
            if (next == '\\') {
                result.append(readEscape());
                continue;
            }
            break;
        }
        if (result.isEmpty()) {
            throw error("Expected identifier.");
        }
        return result.toString();
    }

    /// Reads one escape sequence and returns its decoded character text.
    private String readEscape() {
        read(); // backslash
        if (isDone()) {
            return "\\";
        }
        var next = peek();
        if (isHex(next)) {
            var value = 0;
            var count = 0;
            while (count < 6 && isHex(peek())) {
                value = (value << 4) + hexValue(read());
                count++;
            }
            if (isWhitespace(peek())) {
                read();
            }
            return String.valueOf((char) value);
        }
        return String.valueOf((char) read());
    }

    /// Reads a balanced pair of delimiters.
    private void readBalanced(char open, char close) {
        expect(open);
        var depth = 1;
        while (!isDone() && depth > 0) {
            var character = read();
            if (character == open) {
                depth++;
            } else if (character == close) {
                depth--;
            } else if (character == '"' || character == '\'') {
                readQuoted(character);
            }
        }
    }

    /// Reads a quoted string including the closing quote.
    private void readQuoted(int quote) {
        while (!isDone()) {
            var character = read();
            if (character == quote) {
                return;
            }
            if (character == '\\' && !isDone()) {
                read();
            }
        }
    }

    /// Consumes whitespace.
    private void whitespace() {
        while (isWhitespace(peek())) {
            read();
        }
    }

    /// Consumes one expected character.
    private void expect(int expected) {
        if (peek() != expected) {
            throw error("Expected \"" + (char) expected + "\".");
        }
        read();
    }

    /// Consumes one character when it matches.
    private boolean scan(int expected) {
        if (peek() != expected) {
            return false;
        }
        read();
        return true;
    }

    /// Returns the next character without consuming it.
    private int peek() {
        return isDone() ? -1 : text.charAt(position);
    }

    /// Consumes and returns the next character.
    private int read() {
        if (isDone()) {
            throw error("Unexpected end of selector.");
        }
        return text.charAt(position++);
    }

    /// Returns whether the input is exhausted.
    private boolean isDone() {
        return position >= text.length();
    }

    /// Returns a span from {@code start} to the current position.
    private SourceSpan spanFrom(int start) {
        var absoluteStart = baseSpan.start().offset() + start;
        var absoluteEnd = baseSpan.start().offset() + position;
        var startLocation = new SourceLocation(
                baseSpan.start().line(),
                baseSpan.start().column() + start,
                absoluteStart
        );
        var endLocation = new SourceLocation(
                baseSpan.start().line(),
                baseSpan.start().column() + position,
                absoluteEnd
        );
        return new SourceSpan(
                baseSpan.url(),
                startLocation,
                endLocation,
                text.substring(start, position)
        );
    }

    /// Creates a selector parse failure.
    private SassValueException error(String message) {
        return new SassValueException(message);
    }

    /// Returns whether {@code character} is whitespace.
    private static boolean isWhitespace(int character) {
        return character == ' ' || character == '\t' || character == '\n'
                || character == '\r' || character == '\f';
    }

    /// Returns whether {@code character} may start an identifier.
    private static boolean isIdentStart(int character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= 0x80
                || character == '_';
    }

    /// Returns whether {@code character} may continue an identifier.
    private static boolean isIdent(int character) {
        return isIdentStart(character)
                || character >= '0' && character <= '9'
                || character == '-';
    }

    /// Returns whether {@code character} is a hex digit.
    private static boolean isHex(int character) {
        return character >= '0' && character <= '9'
                || character >= 'A' && character <= 'F'
                || character >= 'a' && character <= 'f';
    }

    /// Returns the integer value of a hex digit.
    private static int hexValue(int character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return character - 'a' + 10;
    }
}
