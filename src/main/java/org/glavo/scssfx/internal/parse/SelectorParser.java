// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.AttributeMatcher;
import org.glavo.scssfx.internal.ast.selector.AttributeSelector;
import org.glavo.scssfx.internal.ast.selector.ClassSelector;
import org.glavo.scssfx.internal.ast.selector.Combinator;
import org.glavo.scssfx.internal.ast.selector.ComplexSelector;
import org.glavo.scssfx.internal.ast.selector.ComplexSelectorComponent;
import org.glavo.scssfx.internal.ast.selector.CompoundSelector;
import org.glavo.scssfx.internal.ast.selector.CssIdentifier;
import org.glavo.scssfx.internal.ast.selector.IdSelector;
import org.glavo.scssfx.internal.ast.selector.NthPseudoArgument;
import org.glavo.scssfx.internal.ast.selector.ParentSelector;
import org.glavo.scssfx.internal.ast.selector.PlaceholderSelector;
import org.glavo.scssfx.internal.ast.selector.PseudoArgument;
import org.glavo.scssfx.internal.ast.selector.PseudoSelector;
import org.glavo.scssfx.internal.ast.selector.QualifiedName;
import org.glavo.scssfx.internal.ast.selector.RawPseudoArgument;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.ast.selector.SelectorPseudoArgument;
import org.glavo.scssfx.internal.ast.selector.SelectorNamespace;
import org.glavo.scssfx.internal.ast.selector.SimpleSelector;
import org.glavo.scssfx.internal.ast.selector.TypeSelector;
import org.glavo.scssfx.internal.ast.selector.UniversalSelector;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Parses resolved selector text into a structured selector AST.
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
    ///
    /// @return the parsed selector list
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
    ///
    /// @return the parsed complex selector
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
    ///
    /// @return the parsed compound selector
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
            var simple = parseSimple();
            if (simple instanceof ParentSelector) {
                throw error("Parent selector must be the first selector in a compound.");
            }
            simples.add(simple);
        }
        return new CompoundSelector(simples, spanFrom(start));
    }

    /// Parses one simple selector.
    ///
    /// @return the parsed simple selector
    private SimpleSelector parseSimple() {
        var start = position;
        return switch (peek()) {
            case '&' -> readParentSelector(start);
            case '.' -> {
                read();
                yield new ClassSelector(readIdentifier(), spanFrom(start));
            }
            case '#' -> {
            read();
                yield new IdSelector(readIdentifier(), spanFrom(start));
            }
            case '[' -> readAttributeSelector(start);
            case ':' -> readPseudoSelector(start);
            case '%' -> readPlaceholderSelector(start);
            case '*', '|' -> readTypeOrUniversal(start);
            default -> {
                if (!lookingAtIdentifier()) {
                    throw error("Expected selector.");
                }
                yield readTypeOrUniversal(start);
            }
        };
    }

    /// Parses a parent selector and its optional suffix.
    ///
    /// @param start the selector's start offset
    /// @return the parsed parent selector
    private ParentSelector readParentSelector(int start) {
        expect('&');
        @Nullable CssIdentifier suffix = null;
        if (lookingAtIdentifier()) {
            suffix = readIdentifier();
            }
            return new ParentSelector(suffix, spanFrom(start));
        }

    /// Parses a type or universal selector, including a namespace prefix.
    ///
    /// @param start the selector's start offset
    /// @return the parsed selector
    private SimpleSelector readTypeOrUniversal(int start) {
        if (scan('*')) {
            if (!scan('|')) {
                return new UniversalSelector(spanFrom(start));
            }
            return readNameOrUniversal(SelectorNamespace.anyNamespace(), start);
        }
        if (scan('|')) {
            return readNameOrUniversal(SelectorNamespace.noNamespace(), start);
        }

        var prefix = readIdentifier();
        if (!scan('|')) {
            return new TypeSelector(
                    QualifiedName.unqualified(prefix),
                    spanFrom(start)
            );
        }
        return readNameOrUniversal(SelectorNamespace.named(prefix), start);
    }

    /// Parses the local portion after a consumed namespace separator.
    ///
    /// @param namespace the namespace before the separator
    /// @param start     the selector's start offset
    /// @return a type or universal selector using {@code namespace}
    private SimpleSelector readNameOrUniversal(
            SelectorNamespace namespace,
            int start
    ) {
        if (scan('*')) {
            return new UniversalSelector(namespace, spanFrom(start));
        }
        return new TypeSelector(
                new QualifiedName(readIdentifier(), namespace),
                spanFrom(start)
        );
    }

    /// Parses one attribute selector.
    ///
    /// @param start the selector's start offset
    /// @return the parsed attribute selector
    private AttributeSelector readAttributeSelector(int start) {
        expect('[');
        whitespace();
        var name = readAttributeName();
        whitespace();

        @Nullable AttributeMatcher matcher = null;
        @Nullable String value = null;
        @Nullable CssIdentifier modifier = null;
        if (isDone()) {
            throw expectedClosingBracket();
        }
        if (peek() != ']') {
            matcher = readAttributeMatcher();
            whitespace();
            if (isDone()) {
                throw expectedClosingBracket();
            }
            value = readAttributeValue();
            whitespace();
            if (isDone()) {
                throw expectedClosingBracket();
            }
            if (isAsciiAlphabetic(peek())) {
                modifier = CssIdentifier.of(String.valueOf((char) read()));
                whitespace();
            }
        }
        if (isDone()) {
            throw expectedClosingBracket();
        }
        expect(']');
        return new AttributeSelector(
                name,
                matcher,
                value,
                modifier,
                text.substring(start, position),
                spanFrom(start)
        );
    }

    /// Parses one namespace-qualified attribute name.
    ///
    /// @return the parsed attribute name
    private QualifiedName readAttributeName() {
        if (scan('|')) {
            return new QualifiedName(
                    readIdentifier(),
                    SelectorNamespace.noNamespace()
            );
        }
        if (peek() == '*' && peek(1) == '|') {
            read();
            read();
            return new QualifiedName(
                    readIdentifier(),
                    SelectorNamespace.anyNamespace()
            );
        }

        var prefix = readIdentifier();
        if (peek() != '|' || peek(1) == '=') {
            return QualifiedName.unqualified(prefix);
        }
            read();
        return new QualifiedName(
                readIdentifier(),
                SelectorNamespace.named(prefix)
        );
        }

    /// Parses one attribute matcher.
    ///
    /// @return the parsed matcher
    private AttributeMatcher readAttributeMatcher() {
        return switch (read()) {
            case '=' -> AttributeMatcher.EQUALS;
            case '~' -> {
                expect('=');
                yield AttributeMatcher.INCLUDES;
        }
            case '|' -> {
                expect('=');
                yield AttributeMatcher.DASH_MATCH;
                }
            case '^' -> {
                expect('=');
                yield AttributeMatcher.PREFIX_MATCH;
            }
            case '$' -> {
                expect('=');
                yield AttributeMatcher.SUFFIX_MATCH;
        }
            case '*' -> {
                expect('=');
                yield AttributeMatcher.SUBSTRING_MATCH;
            }
            default -> throw error("Expected attribute matcher.");
        };
    }

    /// Parses one attribute value as its decoded CSS string value.
    ///
    /// @return the decoded attribute value
    private String readAttributeValue() {
        if (peek() == '"' || peek() == '\'') {
            return readAttributeString();
        }
        return readIdentifier().value();
    }

    /// Reads a quoted attribute value and decodes its CSS escapes.
    ///
    /// @return the decoded string value without quote delimiters
    /// @throws SassValueException if the string is unterminated or has an invalid escape
    private String readAttributeString() {
        var quote = read();
        var result = new StringBuilder();
        while (!isDone()) {
            if (peek() == quote) {
                read();
                return result.toString();
            }
            if (peek() == '\\') {
                read();
                appendEscapedCodePoint(result);
            } else {
                appendRawCodePoint(result);
            }
        }
        throw error("Expected closing quote.");
    }

    /// Parses one pseudo-class or pseudo-element selector.
    ///
    /// @param start the selector's start offset
    /// @return the parsed pseudo selector
    private PseudoSelector readPseudoSelector(int start) {
        expect(':');
        var element = scan(':');
        var name = readIdentifier();
        @Nullable PseudoArgument argument = null;
        if (peek() == '(') {
            var argumentStart = position + 1;
            var argumentText = readFunctionalArgument();
            argument = parsePseudoArgument(
                    name,
                    element,
                    argumentText,
                    argumentStart,
                    position - 1
            );
        }
        return new PseudoSelector(name, element, argument, spanFrom(start));
    }

    /// Parses the grammar-specific argument of a functional pseudo selector.
    ///
    /// @param name          the decoded pseudo name
    /// @param element       whether the pseudo uses pseudo-element syntax
    /// @param argument      the raw text between parentheses
    /// @param argumentStart the relative start offset of {@code argument}
    /// @param argumentEnd   the exclusive relative end offset of {@code argument}
    /// @return the structured or opaque pseudo argument
    private PseudoArgument parsePseudoArgument(
            CssIdentifier name,
            boolean element,
            String argument,
            int argumentStart,
            int argumentEnd
    ) {
        var normalizedName = normalizedPseudoName(name.value());
        if (!element && (normalizedName.equals("nth-child")
                || normalizedName.equals("nth-last-child"))) {
            return parseNthPseudoArgument(argument, argumentStart, argumentEnd);
        }
        if (acceptsSelectorArgument(normalizedName, element)) {
            return new SelectorPseudoArgument(SelectorList.parse(
                    argument,
                    spanFrom(argumentStart, argumentEnd)
            ));
        }
        return new RawPseudoArgument(argument);
    }

    /// Parses the formula and optional {@code of} selector list of an nth pseudo selector.
    ///
    /// @param argument      the raw text between parentheses
    /// @param argumentStart the relative start offset of {@code argument}
    /// @param argumentEnd   the exclusive relative end offset of {@code argument}
    /// @return the structured or opaque nth argument
    private PseudoArgument parseNthPseudoArgument(
            String argument,
            int argumentStart,
            int argumentEnd
    ) {
        var ofOffset = findNthOfSeparator(argument);
        if (ofOffset < 0) {
            var formula = argument.strip();
            return formula.isEmpty() ? new RawPseudoArgument(argument)
                    : new NthPseudoArgument(formula, null);
        }

        var formula = argument.substring(0, ofOffset).strip();
        var selectorStart = ofOffset + 2;
        while (selectorStart < argument.length()
                && isWhitespace(argument.charAt(selectorStart))) {
            selectorStart++;
        }
        var selectorText = argument.substring(selectorStart);
        if (formula.isEmpty() || selectorText.isBlank()) {
            return new RawPseudoArgument(argument);
        }
        return new NthPseudoArgument(
                formula,
                SelectorList.parse(
                        selectorText,
                        spanFrom(argumentStart + selectorStart, argumentEnd)
                )
        );
    }

    /// Returns whether a pseudo selector accepts a selector-list argument.
    ///
    /// @param name    the lowercase pseudo name after a vendor prefix is removed
    /// @param element whether the pseudo uses pseudo-element syntax
    /// @return whether the argument must be parsed as selectors
    private static boolean acceptsSelectorArgument(String name, boolean element) {
        if (element) {
            return name.equals("slotted");
        }
        return switch (name) {
            case "not", "is", "matches", "where", "current", "any", "has",
                    "host", "host-context" -> true;
            default -> false;
        };
    }

    /// Removes one recognized vendor prefix and lowercases a pseudo name.
    ///
    /// @param name the decoded pseudo name
    /// @return the lowercase, vendor-neutral pseudo name
    private static String normalizedPseudoName(String name) {
        var normalized = name.toLowerCase(Locale.ROOT);
        for (var prefix : List.of("-webkit-", "-moz-", "-ms-", "-o-")) {
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
        }
        return normalized;
    }

    /// Finds the top-level {@code of} token separating an nth selector list.
    ///
    /// @param argument the raw text between parentheses
    /// @return the offset of {@code of}, or {@code -1} when absent
    private static int findNthOfSeparator(String argument) {
        var quote = 0;
        var parentheses = 0;
        var brackets = 0;
        for (var index = 0; index < argument.length(); index++) {
            var character = argument.charAt(index);
            if (character == '\\' && index + 1 < argument.length()) {
                index++;
                continue;
            }
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                continue;
            }
            if (character == '[') {
                brackets++;
                continue;
            }
            if (character == ']' && brackets > 0) {
                brackets--;
                continue;
            }
            if (brackets != 0) {
                continue;
            }
            if (character == '(') {
                parentheses++;
                continue;
            }
            if (character == ')' && parentheses > 0) {
                parentheses--;
                continue;
            }
            if (parentheses == 0
                    && (character == 'o' || character == 'O')
                    && index + 2 < argument.length()
                    && (argument.charAt(index + 1) == 'f'
                    || argument.charAt(index + 1) == 'F')
                    && index > 0
                    && isWhitespace(argument.charAt(index - 1))
                    && isWhitespace(argument.charAt(index + 2))) {
                return index;
            }
        }
        return -1;
    }

    /// Reads the content of a functional pseudo-selector argument.
    ///
    /// @return the argument text without its enclosing parentheses
    /// @throws SassValueException if the argument has no closing parenthesis
    private String readFunctionalArgument() {
        expect('(');
        var start = position;
        var parentheses = 1;
        var brackets = 0;
        while (!isDone()) {
                var character = read();
            if (character == '"' || character == '\'') {
                    readQuoted(character);
                continue;
                }
            if (character == '\\') {
                skipOpaqueEscape();
                continue;
            }
            if (character == '[') {
                brackets++;
                continue;
            }
            if (character == ']' && brackets > 0) {
                brackets--;
                continue;
        }
            if (brackets != 0) {
                continue;
            }
            if (character == '(') {
                parentheses++;
                continue;
            }
            if (character == ')') {
                parentheses--;
                if (parentheses == 0) {
                    return text.substring(start, position - 1);
            }
        }
        }
        throw error("Expected closing ')'.");
    }

    /// Parses one Sass placeholder selector.
    ///
    /// @param start the selector's start offset
    /// @return the parsed placeholder selector
    private PlaceholderSelector readPlaceholderSelector(int start) {
        expect('%');
        return new PlaceholderSelector(readIdentifier(), spanFrom(start));
    }

    /// Attempts to read one combinator token.
    ///
    /// @return the parsed combinator, or {@code null} when no combinator begins here
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

    /// Returns whether a simple selector can begin at the current position.
    ///
    /// @return whether selector parsing can continue with a simple selector
    private boolean lookingAtSimple() {
        var next = peek();
        return next == '&' || next == '.' || next == '#' || next == '*'
                || next == '|' || next == ':' || next == '[' || next == '%'
                || lookingAtIdentifier();
    }

    /// Reads a CSS identifier and returns its decoded structural representation.
    ///
    /// @return the parsed identifier
    /// @throws SassValueException if no identifier starts at the current position
    private CssIdentifier readIdentifier() {
        if (!lookingAtIdentifier()) {
            throw error("Expected identifier.");
        }

        var result = new StringBuilder();
        if (scan('-')) {
            result.append('-');
            if (scan('-')) {
                result.append('-');
                appendIdentifierBody(result);
                return CssIdentifier.of(result.toString());
            }
        }

        if (isIdentStart(peek())) {
            appendRawCodePoint(result);
        } else if (peek() == '\\') {
            appendEscape(result);
        } else {
            throw error("Expected identifier.");
        }
        appendIdentifierBody(result);
        return CssIdentifier.of(result.toString());
    }

    /// Appends zero or more identifier-body code points.
    ///
    /// @param result the decoded identifier buffer
    private void appendIdentifierBody(StringBuilder result) {
        while (true) {
            if (isIdent(peek())) {
                appendRawCodePoint(result);
            } else if (peek() == '\\') {
                appendEscape(result);
            } else {
                return;
        }
        }
    }

    /// Appends one literal source code point.
    ///
    /// @param result the decoded identifier buffer
    private void appendRawCodePoint(StringBuilder result) {
        result.appendCodePoint(readCodePoint());
    }

    /// Appends one CSS escape sequence after decoding it.
    ///
    /// @param result the decoded destination buffer
    private void appendEscape(StringBuilder result) {
        expect('\\');
        appendEscapedCodePoint(result);
    }

    /// Appends the CSS escape content after its backslash has been consumed.
    ///
    /// @param result the decoded destination buffer
    /// @throws SassValueException if the escape has no escaped code point
    private void appendEscapedCodePoint(StringBuilder result) {
        if (isDone() || isNewline(peek())) {
            throw error("Expected escape sequence.");
        }

        int value;
        if (isHex(peek())) {
            value = 0;
            for (var count = 0; count < 6 && isHex(peek()); count++) {
                value = value * 16 + hexValue(read());
            }
            if (isWhitespace(peek())) {
                read();
            }
        } else {
            value = readCodePoint();
        }
        result.appendCodePoint(normalizeEscapedCodePoint(value));
    }

    /// Skips one escape inside opaque functional pseudo-selector content.
    ///
    /// @throws SassValueException if the escape has no escaped code point
    private void skipOpaqueEscape() {
        if (isDone() || isNewline(peek())) {
            throw error("Expected escape sequence.");
        }
        readCodePoint();
    }

    /// Reads a quoted string including its closing quote.
    ///
    /// @param quote the opening quote character
    /// @throws SassValueException if input ends before the closing quote
    private void readQuoted(int quote) {
        while (!isDone()) {
            var character = read();
            if (character == quote) {
                return;
            }
            if (character == '\\') {
                skipOpaqueEscape();
            }
        }
        throw error("Expected closing quote.");
    }

    /// Consumes whitespace.
    private void whitespace() {
        while (isWhitespace(peek())) {
            read();
        }
    }

    /// Consumes one expected character.
    ///
    /// @param expected the expected character
    private void expect(int expected) {
        if (peek() != expected) {
            throw error("Expected \"" + (char) expected + "\".");
        }
        read();
    }

    /// Consumes one character when it matches.
    ///
    /// @param expected the character to scan
    /// @return whether {@code expected} was consumed
    private boolean scan(int expected) {
        if (peek() != expected) {
            return false;
        }
        read();
        return true;
    }

    /// Returns the next UTF-16 code unit without consuming it.
    ///
    /// @return the next code unit, or {@code -1} at end of input
    private int peek() {
        return peek(0);
    }

    /// Returns one future UTF-16 code unit without consuming it.
    ///
    /// @param offset the nonnegative look-ahead offset
    /// @return the selected code unit, or {@code -1} past end of input
    private int peek(int offset) {
        var index = position + offset;
        return index >= text.length() ? -1 : text.charAt(index);
    }

    /// Consumes and returns the next UTF-16 code unit.
    ///
    /// @return the consumed code unit
    private int read() {
        if (isDone()) {
            throw error("Unexpected end of selector.");
        }
        return text.charAt(position++);
    }

    /// Consumes and returns the next Unicode code point.
    ///
    /// @return the consumed code point
    private int readCodePoint() {
        if (isDone()) {
            throw error("Unexpected end of selector.");
        }
        var codePoint = text.codePointAt(position);
        position += Character.charCount(codePoint);
        return codePoint;
    }

    /// Returns whether the input is exhausted.
    ///
    /// @return whether no source text remains
    private boolean isDone() {
        return position >= text.length();
    }

    /// Returns a span from {@code start} to the current position.
    ///
    /// @param start the relative start offset
    /// @return the corresponding source span
    private SourceSpan spanFrom(int start) {
        return spanFrom(start, position);
    }

    /// Returns a span between two relative selector offsets.
    ///
    /// @param start the relative start offset
    /// @param end   the exclusive relative end offset
    /// @return the corresponding source span
    private SourceSpan spanFrom(int start, int end) {
        var absoluteStart = baseSpan.start().offset() + start;
        var absoluteEnd = baseSpan.start().offset() + end;
        var startLocation = new SourceLocation(
                baseSpan.start().line(),
                baseSpan.start().column() + start,
                absoluteStart
        );
        var endLocation = new SourceLocation(
                baseSpan.start().line(),
                baseSpan.start().column() + end,
                absoluteEnd
        );
        return new SourceSpan(
                baseSpan.url(),
                startLocation,
                endLocation,
                text.substring(start, end)
        );
    }

    /// Creates a selector parse failure.
    ///
    /// @param message the diagnostic message
    /// @return the exception to throw
    private SassValueException error(String message) {
        return new SassValueException(message);
    }

    /// Creates the standard unterminated-attribute diagnostic.
    ///
    /// @return the exception to throw
    private SassValueException expectedClosingBracket() {
        return error("Expected closing ']'.");
    }

    /// Returns whether text at the current position starts a CSS identifier.
    ///
    /// @return whether identifier parsing may begin
    private boolean lookingAtIdentifier() {
        var first = peek();
        if (isIdentStart(first) || first == '\\') {
            return true;
        }
        if (first != '-') {
            return false;
        }
        var second = peek(1);
        return isIdentStart(second) || second == '\\' || second == '-';
    }

    /// Returns whether {@code character} is whitespace.
    ///
    /// @param character the code unit to inspect
    /// @return whether the code unit is CSS whitespace
    private static boolean isWhitespace(int character) {
        return character == ' ' || character == '\t' || character == '\n'
                || character == '\r' || character == '\f';
    }

    /// Returns whether {@code character} is one ASCII letter.
    ///
    /// Attribute-selector modifiers contain one ASCII alphabetic code unit.
    ///
    /// @param character the code unit to inspect
    /// @return whether the code unit is an ASCII letter
    private static boolean isAsciiAlphabetic(int character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z';
    }

    /// Returns whether {@code character} is a CSS newline.
    ///
    /// @param character the code unit to inspect
    /// @return whether the code unit is a newline
    private static boolean isNewline(int character) {
        return character == '\n' || character == '\r' || character == '\f';
    }

    /// Returns whether {@code character} may start an unescaped identifier.
    ///
    /// @param character the code unit to inspect
    /// @return whether the code unit is a CSS name-start code point
    private static boolean isIdentStart(int character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= 0x80
                || character == '_';
    }

    /// Returns whether {@code character} may continue an identifier.
    ///
    /// @param character the code unit to inspect
    /// @return whether the code unit is a CSS name code point
    private static boolean isIdent(int character) {
        return isIdentStart(character)
                || character >= '0' && character <= '9'
                || character == '-';
    }

    /// Returns whether {@code character} is a hexadecimal digit.
    ///
    /// @param character the code unit to inspect
    /// @return whether the code unit is hexadecimal
    private static boolean isHex(int character) {
        return character >= '0' && character <= '9'
                || character >= 'A' && character <= 'F'
                || character >= 'a' && character <= 'f';
    }

    /// Returns the integer value of one hexadecimal digit.
    ///
    /// @param character the hexadecimal code unit
    /// @return the digit value from zero through fifteen
    private static int hexValue(int character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return character - 'a' + 10;
    }

    /// Returns the CSS value represented by an escape sequence.
    ///
    /// @param value the escaped scalar value
    /// @return {@code value}, or the replacement character when CSS requires it
    private static int normalizeEscapedCodePoint(int value) {
        if (value == 0
                || value > Character.MAX_CODE_POINT
                || value >= Character.MIN_SURROGATE
                && value <= Character.MAX_SURROGATE) {
            return 0xFFFD;
        }
        return value;
    }
}
