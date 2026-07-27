// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
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
import org.glavo.scssfx.internal.ast.selector.OtherSimpleSelector;
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
import java.util.function.Consumer;

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

    /// Whether plain-CSS selector restrictions are active.
    private final boolean plainCss;

    /// Whether percentage keyframe selectors such as {@code 10%} are accepted.
    private final boolean keyframeSelectors;

    /// Receives selector parse deprecations, or {@code null} to discard them.
    private final @Nullable Consumer<Diagnostic> deprecationConsumer;

    /// Creates a parser for one selector string.
    ///
    /// @param text              the selector source
    /// @param baseSpan          the span covering that source
    /// @param plainCss          whether plain CSS selector restrictions apply
    /// @param keyframeSelectors whether percentage keyframe selectors are accepted
    /// @param deprecationConsumer receives selector parse deprecations, or
    ///                            {@code null} to discard them
    private SelectorParser(
            String text,
            SourceSpan baseSpan,
            boolean plainCss,
            boolean keyframeSelectors,
            @Nullable Consumer<Diagnostic> deprecationConsumer
    ) {
        this.text = Objects.requireNonNull(text, "text");
        this.baseSpan = Objects.requireNonNull(baseSpan, "baseSpan");
        this.plainCss = plainCss;
        this.keyframeSelectors = keyframeSelectors;
        this.deprecationConsumer = deprecationConsumer;
    }

    /// Parses a selector list.
    ///
    /// @param text the selector source after interpolation
    /// @param span the span covering that text
    /// @return the parsed selector list
    /// @throws SassValueException if the selector is invalid
    public static SelectorList parse(String text, SourceSpan span) {
        return parse(text, span, false, false);
    }

    /// Parses a selector list with optional plain-CSS restrictions.
    ///
    /// @param text     the selector source after interpolation
    /// @param span     the span covering that text
    /// @param plainCss whether plain CSS selector restrictions apply
    /// @return the parsed selector list
    /// @throws SassValueException if the selector is invalid
    public static SelectorList parse(String text, SourceSpan span, boolean plainCss) {
        return parse(text, span, plainCss, false);
    }

    /// Parses a selector list with optional plain-CSS and keyframe modes.
    ///
    /// @param text              the selector source after interpolation
    /// @param span              the span covering that text
    /// @param plainCss          whether plain CSS selector restrictions apply
    /// @param keyframeSelectors whether percentage selectors such as {@code 10%}
    ///                          and scientific forms such as {@code 1e2%} are accepted
    /// @return the parsed selector list
    /// @throws SassValueException if the selector is invalid
    public static SelectorList parse(
            String text,
            SourceSpan span,
            boolean plainCss,
            boolean keyframeSelectors
    ) {
        return new SelectorParser(
                text,
                span,
                plainCss,
                keyframeSelectors,
                null
        ).parseList();
    }

    /// Parses a selector list and reports selector-syntax deprecations.
    ///
    /// @param text              the selector source after interpolation
    /// @param span              the span covering that text
    /// @param plainCss          whether plain CSS selector restrictions apply
    /// @param keyframeSelectors whether percentage selectors are accepted
    /// @param deprecationConsumer receives selector deprecations
    /// @return the parsed selector list
    /// @throws SassValueException if the selector is invalid
    public static SelectorList parse(
            String text,
            SourceSpan span,
            boolean plainCss,
            boolean keyframeSelectors,
            Consumer<Diagnostic> deprecationConsumer
    ) {
        return new SelectorParser(
                text,
                span,
                plainCss,
                keyframeSelectors,
                Objects.requireNonNull(
                        deprecationConsumer,
                        "deprecationConsumer"
                )
        ).parseList();
    }

    /// Parses the complete selector list.
    ///
    /// @return the parsed selector list
    private SelectorList parseList() {
        whitespace();
        var components = new ArrayList<ComplexSelector>();
        // Track source line so comma-separated complexes can preserve line breaks.
        int previousLine = lineAt(position);
        components.add(parseComplex(false));
        while (scan(',')) {
            whitespace();
            if (!isDone() && peek() == ',') {
                continue;
            }
            if (isDone()) {
                break;
            }
            int line = lineAt(position);
            boolean lineBreak = line != previousLine;
            if (lineBreak) {
                previousLine = line;
            }
            components.add(parseComplex(lineBreak));
        }
        whitespace();
        if (!isDone()) {
            // Matches dart-sass selector-parse leftovers such as {@code c {}
            // which surface as "expected selector." rather than end-of-input text.
            throw error("expected selector.");
        }
        return new SelectorList(components, baseSpan);
    }

    /// Parses one complex selector.
    ///
    /// @return the parsed complex selector
    private ComplexSelector parseComplex() {
        return parseComplex(false);
    }

    /// Parses one complex selector, recording whether a line break preceded it.
    ///
    /// @param lineBreak whether a newline appeared after the preceding comma
    /// @return the parsed complex selector
    private ComplexSelector parseComplex(boolean lineBreak) {
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
                throw error("expected selector.");
            }
            return new ComplexSelector(leading, List.of(), spanFrom(start), lineBreak);
        }

        @Nullable CompoundSelector previousCompound = null;
        var previousCompoundStart = 0;
        var adjacentToPrevious = false;
        while (lookingAtSimple()) {
            var compoundStart = position;
            var compound = parseCompound();
            if (adjacentToPrevious && previousCompound != null) {
                reportAdjacentCompounds(
                        previousCompound,
                        compound,
                        spanFrom(previousCompoundStart, position)
                );
            }
            // dart-sass: "&" may only be used at the beginning of a compound
            // outside plain CSS (where mid-compound parent is allowed).
            if (!plainCss && peek() == '&') {
                throw error(
                        "\"&\" may only used at the beginning of a compound selector."
                );
            }
            var trailing = new ArrayList<Combinator>();
            var separatorStart = position;
            whitespace();
            var consumedWhitespace = position != separatorStart;
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
            adjacentToPrevious = trailing.isEmpty() && !consumedWhitespace;
            previousCompound = compound;
            previousCompoundStart = compoundStart;
        }
        // Plain CSS rejects trailing combinators such as {@code a >}.
        if (plainCss
                && !components.isEmpty()
                && !components.get(components.size() - 1).combinators().isEmpty()) {
            throw error("expected selector.");
        }
        // Also reject a lone trailing combinator with no compounds.
        if (plainCss && components.isEmpty() && !leading.isEmpty()) {
            // Leading-only forms such as {@code > a} are parsed with compounds;
            // a bare trailing combinator already failed lookingAtSimple above.
        }
        return new ComplexSelector(leading, components, spanFrom(start), lineBreak);
    }

    /// Reports two compound selectors written without an intervening separator.
    ///
    /// @param previous the first adjacent compound
    /// @param next the following adjacent compound
    /// @param span the combined source span
    private void reportAdjacentCompounds(
            CompoundSelector previous,
            CompoundSelector next,
            SourceSpan span
    ) {
        if (deprecationConsumer == null) {
            return;
        }
        deprecationConsumer.accept(new Diagnostic(
                DiagnosticSeverity.DEPRECATION,
                "Adjacent compound selectors must be separated by whitespace. "
                        + "This will be an error in Dart Sass 2.0.0. Suggestion:\n\n"
                        + previous.toCssString() + " " + next.toCssString() + "\n\n"
                        + "More info: https://sass-lang.com/d/adjacent-compounds",
                span,
                "adjacent-compounds"
        ));
    }

    /// Returns a line counter for {@code offset} within the selector text.
    ///
    /// Only relative comparisons matter (whether a newline appears between
    /// commas); the absolute origin is zero at the start of the selector text.
    ///
    /// @param offset the relative UTF-16 offset into {@link #text}
    /// @return the zero-based line number within the selector text
    private int lineAt(int offset) {
        int line = 0;
        int end = Math.min(offset, text.length());
        for (var index = 0; index < end; index++) {
            char ch = text.charAt(index);
            if (ch == '\n') {
                line++;
            } else if (ch == '\r') {
                line++;
                if (index + 1 < end && text.charAt(index + 1) == '\n') {
                    index++;
                }
            }
        }
        return line;
    }

    /// Parses one compound selector.
    ///
    /// After the first simple selector, only punctuation-led simples may
    /// continue the compound ({@code .#[]:%*}). A following type name without
    /// whitespace starts a new compound in the complex-selector loop, matching
    /// dart-sass ({@code [a]b} → {@code [a] b}).
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
                && isCompoundContinuationStart(peek())) {
            var simple = parseSimple();
            // Sass forbids {@code &} after the first simple; plain CSS nesting
            // permits parent references mid-compound ({@code a&}, {@code a&b}).
            // Message matches dart-sass / libsass spelling (including the
            // historical "may only used" wording).
            if (simple instanceof ParentSelector && !plainCss) {
                throw error(
                        "\"&\" may only used at the beginning of a compound selector."
                );
            }
            simples.add(simple);
        }
        return new CompoundSelector(simples, spanFrom(start));
    }

    /// Returns whether a character may continue a compound after its first simple.
    ///
    /// Aligns with dart-sass {@code _isSimpleSelectorStart}: identifiers that
    /// begin type selectors do not continue the current compound. Plain CSS also
    /// allows a mid-compound parent selector.
    ///
    /// @param next the next code unit, or end-of-input sentinel
    /// @return whether compound parsing may consume another simple here
    private boolean isCompoundContinuationStart(int next) {
        return next == '.' || next == '#' || next == '[' || next == '%'
                || next == ':' || next == '*' || next == '|'
                || (plainCss && next == '&');
    }

    /// Parses one simple selector.
    ///
    /// @return the parsed simple selector
    private SimpleSelector parseSimple() {
        var start = position;
        if (keyframeSelectors && lookingAtKeyframePercentage()) {
            return readKeyframePercentage(start);
        }
        return switch (peek()) {
            case '&' -> readParentSelector(start);
            case '.' -> {
                // Class selectors begin with an identifier, not a digit. A bare
                // {@code .3%} form is a keyframe percentage when that mode is on.
                if (keyframeSelectors && lookingAtKeyframePercentage()) {
                    yield readKeyframePercentage(start);
                }
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
                    throw error("expected selector.");
                }
                yield readTypeOrUniversal(start);
            }
        };
    }

    /// Returns whether a keyframe percentage selector begins at the current position.
    ///
    /// Accepts decimal and scientific forms ending in {@code %}, matching
    /// dart-sass keyframe selector parsing ({@code 10%}, {@code 10.3%},
    /// {@code 1e2%}, {@code 13E+1%}).
    ///
    /// @return whether a keyframe percentage can be read here
    private boolean lookingAtKeyframePercentage() {
        if (!keyframeSelectors) {
            return false;
        }
        var index = position;
        if (index >= text.length()) {
            return false;
        }
        // Optional leading sign is not used by the sass-spec keyframe forms.
        if (text.charAt(index) == '.') {
            index++;
            if (index >= text.length() || !CssCharacters.isDigit(text.charAt(index))) {
                return false;
            }
        } else if (CssCharacters.isDigit(text.charAt(index))) {
            while (index < text.length() && CssCharacters.isDigit(text.charAt(index))) {
                index++;
            }
            if (index < text.length() && text.charAt(index) == '.') {
                index++;
                while (index < text.length() && CssCharacters.isDigit(text.charAt(index))) {
                    index++;
                }
            }
        } else {
            return false;
        }
        if (index < text.length()
                && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            var exp = index + 1;
            if (exp < text.length()
                    && (text.charAt(exp) == '+' || text.charAt(exp) == '-')) {
                exp++;
            }
            if (exp >= text.length() || !CssCharacters.isDigit(text.charAt(exp))) {
                return false;
            }
            index = exp + 1;
            while (index < text.length() && CssCharacters.isDigit(text.charAt(index))) {
                index++;
            }
        }
        return index < text.length() && text.charAt(index) == '%';
    }

    /// Reads one keyframe percentage selector and lowercases the scientific
    /// exponent marker when present ({@code 13E+1%} → {@code 13e+1%}).
    ///
    /// @param start the start offset
    /// @return the percentage as an opaque simple selector
    private SimpleSelector readKeyframePercentage(int start) {
        var buffer = new StringBuilder();
        if (peek() == '.') {
            buffer.append((char) read());
            while (CssCharacters.isDigit(peek())) {
                buffer.append((char) read());
            }
        } else {
            while (CssCharacters.isDigit(peek())) {
                buffer.append((char) read());
            }
            if (peek() == '.') {
                buffer.append((char) read());
                while (CssCharacters.isDigit(peek())) {
                    buffer.append((char) read());
                }
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            buffer.append('e');
            read();
            if (peek() == '+' || peek() == '-') {
                buffer.append((char) read());
            }
            while (CssCharacters.isDigit(peek())) {
                buffer.append((char) read());
            }
        }
        if (peek() != '%') {
            throw error("expected selector.");
        }
        buffer.append((char) read());
        return new OtherSimpleSelector(buffer.toString(), spanFrom(start));
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
            if (plainCss) {
                throw error("Parent selectors can't have suffixes in plain CSS.");
            }
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
            // A bare identifier after the name (no matcher) is an invalid
            // modifier placement; dart-sass reports Expected "]".
            if (isAsciiAlphabetic(peek()) || peek() == '_' || peek() == '-') {
                throw error("Expected \"]\".");
            }
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
            return new SelectorPseudoArgument(new SelectorParser(
                    argument,
                    spanFrom(argumentStart, argumentEnd),
                    false,
                    false,
                    deprecationConsumer
            ).parseList());
        }
        return new RawPseudoArgument(argument);
    }

    /// Parses the formula and optional {@code of} selector list of an nth pseudo selector.
    ///
    /// The An+B microsyntax is normalized like dart-sass: internal whitespace around
    /// the {@code n} sign and constant is dropped ({@code 2n + 1} becomes {@code 2n+1}).
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
        var index = 0;
        while (index < argument.length() && isWhitespace(argument.charAt(index))) {
            index++;
        }
        // Empty {@code :nth-child()} has no An+B production; dart-sass reports
        // Expected "n" at the closing parenthesis position.
        if (index >= argument.length()) {
            throw error("Expected \"n\".");
        }

        var parsed = parseAnPlusB(argument, index);
        index = parsed.nextIndex();
        var afterFormula = index;
        while (index < argument.length() && isWhitespace(argument.charAt(index))) {
            index++;
        }
        if (index >= argument.length()) {
            return new NthPseudoArgument(parsed.formula(), null);
        }
        // dart-sass requires whitespace between An+B and {@code of}.
        if (afterFormula == index
                || !regionMatchesIgnoreCase(argument, index, "of")
                || index + 2 < argument.length()
                && isIdentContinuation(argument.charAt(index + 2))) {
            return new RawPseudoArgument(argument);
        }
        index += 2;
        while (index < argument.length() && isWhitespace(argument.charAt(index))) {
            index++;
        }
        var selectorText = argument.substring(index);
        if (selectorText.isBlank()) {
            return new RawPseudoArgument(argument);
        }
        return new NthPseudoArgument(
                parsed.formula(),
                new SelectorParser(
                        selectorText,
                        spanFrom(argumentStart + index, argumentEnd),
                        false,
                        false,
                        deprecationConsumer
                ).parseList()
        );
    }

    /// Result of one An+B parse: normalized formula text and the next source index.
    ///
    /// @param formula   the compact An+B text ({@code even}, {@code odd}, or {@code 2n+1})
    /// @param nextIndex the index after the last formula code unit
    private record AnPlusBParse(String formula, int nextIndex) {
    }

    /// Parses one An+B production from {@code text} starting at {@code start}.
    ///
    /// @param text  the raw pseudo argument text
    /// @param start the index of the first non-whitespace formula character
    /// @return the normalized formula and the index after it
    /// @throws SassValueException if the production is incomplete or invalid
    private AnPlusBParse parseAnPlusB(String text, int start) {
        var index = start;
        var first = text.charAt(index);
        if (first == 'e' || first == 'E') {
            if (!regionMatchesIgnoreCase(text, index, "even")
                    || index + 4 < text.length()
                    && isIdentContinuation(text.charAt(index + 4))) {
                throw error("Expected \"even\".");
            }
            return new AnPlusBParse("even", index + 4);
        }
        if (first == 'o' || first == 'O') {
            if (!regionMatchesIgnoreCase(text, index, "odd")
                    || index + 3 < text.length()
                    && isIdentContinuation(text.charAt(index + 3))) {
                throw error("Expected \"odd\".");
            }
            return new AnPlusBParse("odd", index + 3);
        }

        var buffer = new StringBuilder();
        if (first == '+' || first == '-') {
            buffer.append(first);
            index++;
        }

        if (index < text.length() && CssCharacters.isDigit(text.charAt(index))) {
            do {
                buffer.append(text.charAt(index++));
            } while (index < text.length() && CssCharacters.isDigit(text.charAt(index)));
            while (index < text.length() && isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= text.length() || !isAsciiN(text.charAt(index))) {
                return new AnPlusBParse(buffer.toString(), index);
            }
        } else if (index >= text.length() || !isAsciiN(text.charAt(index))) {
            throw error("Expected \"n\".");
        }
        // Always emit lowercase {@code n}, matching dart-sass.
        buffer.append('n');
        index++;
        while (index < text.length() && isWhitespace(text.charAt(index))) {
            index++;
        }
        if (index >= text.length()) {
            return new AnPlusBParse(buffer.toString(), index);
        }
        var sign = text.charAt(index);
        if (sign != '+' && sign != '-') {
            return new AnPlusBParse(buffer.toString(), index);
        }
        buffer.append(sign);
        index++;
        while (index < text.length() && isWhitespace(text.charAt(index))) {
            index++;
        }
        if (index >= text.length() || !CssCharacters.isDigit(text.charAt(index))) {
            throw error("Expected a number.");
        }
        do {
            buffer.append(text.charAt(index++));
        } while (index < text.length() && CssCharacters.isDigit(text.charAt(index)));
        return new AnPlusBParse(buffer.toString(), index);
    }

    /// Returns whether {@code character} is ASCII {@code n} or {@code N}.
    ///
    /// @param character the code unit to inspect
    /// @return whether the character is {@code n}/{@code N}
    private static boolean isAsciiN(int character) {
        return character == 'n' || character == 'N';
    }

    /// Returns whether {@code character} may continue a CSS identifier body.
    ///
    /// @param character the code unit to inspect
    /// @return whether the character is a name code unit or backslash
    private static boolean isIdentContinuation(int character) {
        return CssCharacters.isName(character) || character == '\\';
    }

    /// Returns whether {@code text} at {@code offset} matches {@code expected} ignoring ASCII case.
    ///
    /// @param text     the haystack
    /// @param offset   the start index
    /// @param expected the expected ASCII text
    /// @return whether the region matches
    private static boolean regionMatchesIgnoreCase(String text, int offset, String expected) {
        return text.regionMatches(true, offset, expected, 0, expected.length());
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

    /// Removes one vendor prefix and lowercases a pseudo name.
    ///
    /// Matches dart-sass {@code unvendor}: any single {@code -prefix-} segment
    /// is stripped so {@code :-pfx-is(...)} parses as a selector-taking pseudo.
    ///
    /// @param name the decoded pseudo name
    /// @return the lowercase, vendor-neutral pseudo name
    private static String normalizedPseudoName(String name) {
        var normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.length() < 2
                || normalized.charAt(0) != '-'
                || normalized.charAt(1) == '-') {
            return normalized;
        }
        for (var index = 2; index < normalized.length(); index++) {
            if (normalized.charAt(index) == '-') {
                return normalized.substring(index + 1);
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
                || lookingAtIdentifier()
                || lookingAtKeyframePercentage();
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

    /// Consumes whitespace and comments that are transparent to selector structure.
    ///
    /// Comments may appear between selector tokens after stylesheet parsing retains
    /// their source spelling in the interpolated selector text.
    private void whitespace() {
        while (true) {
            if (isWhitespace(peek())) {
                read();
                continue;
            }
            if (peek() == '/' && peek(1) == '/') {
                read();
                read();
                while (!isDone() && peek() != '\n' && peek() != '\r' && peek() != '\f') {
                    read();
                }
                continue;
            }
            if (peek() == '/' && peek(1) == '*') {
                read();
                read();
                while (!isDone()) {
                    if (peek() == '*' && peek(1) == '/') {
                        read();
                        read();
                        break;
                    }
                    read();
                }
                continue;
            }
            break;
        }
    }

    /// Consumes one expected character.
    ///
    /// @param expected the expected character
    private void expect(int expected) {
        if (peek() != expected) {
            throw error("expected \"" + (char) expected + "\".");
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
        return new SourceSpan(
                baseSpan.url(),
                locationAt(start),
                locationAt(end),
                text.substring(start, end)
        );
    }

    /// Returns the source location for one selector-relative offset.
    ///
    /// @param offset the offset into [#text]
    /// @return the location relative to [#baseSpan]
    private SourceLocation locationAt(int offset) {
        var line = baseSpan.start().line();
        var column = baseSpan.start().column();
        for (var index = 0; index < offset; index++) {
            var character = text.charAt(index);
            if (character == '\r') {
                line++;
                column = 0;
                if (index + 1 < offset && text.charAt(index + 1) == '\n') {
                    index++;
                }
            } else if (character == '\n' || character == '\f') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new SourceLocation(
                line,
                column,
                baseSpan.start().offset() + offset
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
        // dart-sass string_scanner reports EOF as "expected more input."
        return isDone() ? error("expected more input.") : error("expected \"]\".");
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
