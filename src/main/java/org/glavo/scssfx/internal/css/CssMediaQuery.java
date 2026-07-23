// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Represents one evaluated CSS media query.
///
/// The query stores the media modifier and type separately from its conditions
/// so nested Sass media rules can be intersected when CSS syntax can express
/// their combined meaning.
///
/// @param modifier    the optional media modifier, normally {@code not} or {@code only}
/// @param type        the optional media type
/// @param conjunction whether conditions are joined by {@code and} rather than {@code or}
/// @param conditions  the nonempty parenthesized media conditions when no type is present,
///                    or the conditions that refine the media type
@ApiStatus.Internal
@NotNullByDefault
public record CssMediaQuery(
        @Nullable String modifier,
        @Nullable String type,
        boolean conjunction,
        @Unmodifiable List<String> conditions
) {
    /// Creates an immutable, normalized media query.
    ///
    /// @throws IllegalArgumentException if a component cannot represent a CSS media query
    public CssMediaQuery {
        if (modifier != null) {
            modifier = requiredToken(modifier, "modifier");
        }
        if (type != null) {
            type = requiredToken(type, "type");
        }
        conditions = normalizedConditions(conditions);
        if (type == null && conditions.isEmpty()) {
            throw new IllegalArgumentException("a media query without a type requires a condition");
        }
        if (!conjunction && (modifier != null || type != null)) {
            throw new IllegalArgumentException("only condition media queries may use or");
        }
    }

    /// Parses an interpolated CSS media-query list.
    ///
    /// The parser recognizes media types, {@code not} and {@code only}
    /// modifiers, parenthesized conditions, comma-separated alternatives, and
    /// {@code and}/{@code or} condition sequences. Condition bodies retain
    /// their CSS text for later serialization.
    ///
    /// @param contents the evaluated query-list text
    /// @return an immutable nonempty query list
    /// @throws SassValueException if {@code contents} is not a supported media-query list
    public static @Unmodifiable List<CssMediaQuery> parseList(String contents) {
        return new Parser(contents).parse();
    }

    /// Merges two query lists as a CSS-expressible intersection.
    ///
    /// A nonempty result contains the Cartesian product of compatible queries.
    /// An empty result denotes an intersection that cannot match. {@code null}
    /// denotes an intersection that is meaningful but cannot be represented by
    /// one CSS media-query list and must therefore remain nested.
    ///
    /// @param first  the outer media-query list
    /// @param second the nested media-query list
    /// @return an immutable merged list, an immutable empty list, or {@code null}
    public static @Nullable @Unmodifiable List<CssMediaQuery> mergeLists(
            List<CssMediaQuery> first,
            List<CssMediaQuery> second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        var merged = new ArrayList<CssMediaQuery>();
        for (var firstQuery : first) {
            Objects.requireNonNull(firstQuery, "first query");
            for (var secondQuery : second) {
                Objects.requireNonNull(secondQuery, "second query");
                var outcome = firstQuery.merge(secondQuery);
                if (outcome.unrepresentable()) {
                    return null;
                }
                @Nullable CssMediaQuery query = outcome.query();
                if (query != null) {
                    merged.add(query);
                }
            }
        }
        return List.copyOf(merged);
    }

    /// Returns whether this query matches all media types.
    ///
    /// @return whether the type is absent or equals {@code all}
    public boolean matchesAllTypes() {
        return type == null || type.equalsIgnoreCase("all");
    }

    /// Returns whether compressed output needs whitespace after {@code @media}.
    ///
    /// @return whether the query starts with an identifier token
    public boolean startsWithIdentifier() {
        return type != null || modifier != null || isNegatedCondition(conditions.get(0));
    }

    /// Returns this query in ordinary CSS layout.
    ///
    /// @return the canonical CSS media-query text
    public String toCssString() {
        return writeCss(false);
    }

    /// Returns this query in compressed CSS layout.
    ///
    /// The result preserves spaces that separate adjacent identifier tokens
    /// while omitting spaces before subsequent condition operators.
    ///
    /// @return the compressed CSS media-query text
    public String toCompressedCss() {
        return writeCss(true);
    }

    /// Returns the CSS text used by [#toCssString()] or [#toCompressedCss()].
    ///
    /// @param compressed whether compressed separators should be used
    /// @return the serialized query
    private String writeCss(boolean compressed) {
        var result = new StringBuilder();
        if (modifier != null) {
            result.append(modifier).append(' ');
        }
        if (type != null) {
            result.append(type);
            if (!conditions.isEmpty()) {
                result.append(" and ");
                result.append(displayCondition(conditions.get(0)));
                for (var index = 1; index < conditions.size(); index++) {
                    result.append(compressed ? "and " : " and ");
                    result.append(displayCondition(conditions.get(index)));
                }
            }
            return result.toString();
        }

        result.append(displayCondition(conditions.get(0)));
        for (var index = 1; index < conditions.size(); index++) {
            if (compressed) {
                result.append(conjunction ? "and " : "or ");
            } else {
                result.append(conjunction ? " and " : " or ");
            }
            result.append(displayCondition(conditions.get(index)));
        }
        return result.toString();
    }

    /// Returns a human-readable form of one stored condition.
    ///
    /// A condition parsed from {@code not (...)} is stored parenthesized so it
    /// can participate in equality and merge checks, but CSS permits its
    /// shorter outer spelling when serialized.
    ///
    /// @param condition the stored parenthesized condition
    /// @return the CSS condition text
    private static String displayCondition(String condition) {
        if (isNegatedCondition(condition)) {
            return "not " + condition.substring(5, condition.length() - 1);
        }
        return condition;
    }

    /// Returns whether a stored condition wraps a top-level {@code not} expression.
    ///
    /// @param condition the stored condition
    /// @return whether the condition uses the normalized negated form
    private static boolean isNegatedCondition(String condition) {
        return condition.startsWith("(not (") && condition.endsWith("))");
    }

    /// Merges this query with {@code other} when CSS syntax can express their intersection.
    ///
    /// @param other the nested query
    /// @return the successful query, an empty result, or an unrepresentable result
    private QueryMerge merge(CssMediaQuery other) {
        if (!conjunction || !other.conjunction) {
            return new QueryMerge(null, true);
        }

        var ourModifier = lower(modifier);
        var ourType = lower(type);
        var theirModifier = lower(other.modifier);
        var theirType = lower(other.type);

        if (ourType == null && theirType == null) {
            return new QueryMerge(
                    new CssMediaQuery(
                            null,
                            null,
                            true,
                            concatenatedConditions(conditions, other.conditions)
                    ),
                    false
            );
        }

        @Nullable String mergedModifier;
        @Nullable String mergedType;
        List<String> mergedConditions;
        if (isNot(ourModifier) != isNot(theirModifier)) {
            if (sameText(ourType, theirType)) {
                var negativeConditions = isNot(ourModifier) ? conditions : other.conditions;
                var positiveConditions = isNot(ourModifier) ? other.conditions : conditions;
                if (positiveConditions.containsAll(negativeConditions)) {
                    return new QueryMerge(null, false);
                }
                return new QueryMerge(null, true);
            }
            if (matchesAllTypes() || other.matchesAllTypes()) {
                return new QueryMerge(null, true);
            }
            if (isNot(ourModifier)) {
                mergedModifier = theirModifier;
                mergedType = theirType;
                mergedConditions = other.conditions;
            } else {
                mergedModifier = ourModifier;
                mergedType = ourType;
                mergedConditions = conditions;
            }
        } else if (isNot(ourModifier)) {
            if (!sameText(ourType, theirType)) {
                return new QueryMerge(null, true);
            }
            var moreConditions = conditions.size() > other.conditions.size()
                    ? conditions
                    : other.conditions;
            var fewerConditions = conditions.size() > other.conditions.size()
                    ? other.conditions
                    : conditions;
            if (!moreConditions.containsAll(fewerConditions)) {
                return new QueryMerge(null, true);
            }
            mergedModifier = ourModifier;
            mergedType = ourType;
            mergedConditions = moreConditions;
        } else if (matchesAllTypes()) {
            mergedModifier = theirModifier;
            mergedType = other.matchesAllTypes() && ourType == null ? null : theirType;
            mergedConditions = concatenatedConditions(conditions, other.conditions);
        } else if (other.matchesAllTypes()) {
            mergedModifier = ourModifier;
            mergedType = ourType;
            mergedConditions = concatenatedConditions(conditions, other.conditions);
        } else if (!sameText(ourType, theirType)) {
            return new QueryMerge(null, false);
        } else {
            mergedModifier = ourModifier != null ? ourModifier : theirModifier;
            mergedType = ourType;
            mergedConditions = concatenatedConditions(conditions, other.conditions);
        }

        @Nullable String resultModifier = sameText(mergedModifier, ourModifier)
                ? modifier
                : other.modifier;
        @Nullable String resultType = sameText(mergedType, ourType) ? type : other.type;
        return new QueryMerge(
                new CssMediaQuery(resultModifier, resultType, true, mergedConditions),
                false
        );
    }

    /// Concatenates immutable condition lists without retaining mutable backing storage.
    ///
    /// @param first  the leading conditions
    /// @param second the trailing conditions
    /// @return one immutable concatenated list
    private static @Unmodifiable List<String> concatenatedConditions(
            List<String> first,
            List<String> second
    ) {
        var result = new ArrayList<String>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    /// Returns the lowercase form of an optional CSS token.
    ///
    /// @param value the token, or {@code null}
    /// @return the lowercase token, or {@code null}
    private static @Nullable String lower(@Nullable String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /// Returns whether an optional token denotes the {@code not} modifier.
    ///
    /// @param modifier the normalized modifier, or {@code null}
    /// @return whether the modifier equals {@code not}
    private static boolean isNot(@Nullable String modifier) {
        return "not".equals(modifier);
    }

    /// Compares optional CSS tokens without case sensitivity.
    ///
    /// @param first  the first token, or {@code null}
    /// @param second the second token, or {@code null}
    /// @return whether both tokens are absent or spell the same token
    private static boolean sameText(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : second != null && first.equalsIgnoreCase(second);
    }

    /// Validates and trims one required CSS token.
    ///
    /// @param value the token text
    /// @param role  the component name used in failure text
    /// @return the nonempty trimmed token
    private static String requiredToken(String value, String role) {
        var normalized = Objects.requireNonNull(value, role).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(role + " must not be empty");
        }
        return normalized;
    }

    /// Copies and validates stored media conditions.
    ///
    /// @param conditions the condition values to normalize
    /// @return an immutable list of nonempty condition text
    private static @Unmodifiable List<String> normalizedConditions(List<String> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        var normalized = new ArrayList<String>(conditions.size());
        for (var condition : conditions) {
            var text = Objects.requireNonNull(condition, "condition").strip();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("media condition must not be empty");
            }
            normalized.add(text);
        }
        return List.copyOf(normalized);
    }

    /// Describes the result of intersecting two individual media queries.
    ///
    /// @param query             the merged query, or {@code null} for an empty or unrepresentable result
    /// @param unrepresentable   whether the nonempty intersection cannot be emitted as one query
    @NotNullByDefault
    private record QueryMerge(@Nullable CssMediaQuery query, boolean unrepresentable) {
        /// Validates the three result states.
        private QueryMerge {
            if (query != null && unrepresentable) {
                throw new IllegalArgumentException("a successful media merge cannot be unrepresentable");
            }
        }
    }

    /// Parses a CSS media-query list without depending on a CSS runtime.
    @NotNullByDefault
    private static final class Parser {
        /// Contains the complete query-list source.
        private final String contents;

        /// Contains the next UTF-16 code-unit offset to read.
        private int index;

        /// Creates a parser for one interpolated query list.
        ///
        /// @param contents the query-list source
        private Parser(String contents) {
            this.contents = Objects.requireNonNull(contents, "contents");
        }

        /// Parses all comma-separated media queries.
        ///
        /// @return the immutable nonempty query list
        private @Unmodifiable List<CssMediaQuery> parse() {
            skipTrivia();
            if (atEnd()) {
                throw failure("Expected a media query.");
            }
            var queries = new ArrayList<CssMediaQuery>();
            while (true) {
                queries.add(parseQuery());
                skipTrivia();
                if (atEnd()) {
                    return List.copyOf(queries);
                }
                if (!scan(',')) {
                    throw failure("Expected ',' or the end of the media query.");
                }
                skipTrivia();
                if (atEnd()) {
                    throw failure("Expected a media query after ','.");
                }
            }
        }

        /// Parses one query ending before a comma or end of input.
        ///
        /// @return the parsed query
        private CssMediaQuery parseQuery() {
            skipTrivia();
            if (peek() == '(') {
                return parseConditionQuery();
            }

            var first = identifier("Expected a media type or condition.");
            skipTrivia();
            if (first.equalsIgnoreCase("not") && peek() == '(') {
                return new CssMediaQuery(
                        null,
                        null,
                        true,
                        List.of(negatedCondition())
                );
            }
            if (atQueryEnd()) {
                return new CssMediaQuery(null, first, true, List.of());
            }
            if (!lookingAtIdentifier()) {
                throw failure("Expected a media condition after media type.");
            }

            var second = identifier("Expected a media condition.");
            skipTrivia();
            if (second.equalsIgnoreCase("and")) {
                return new CssMediaQuery(null, first, true, conjunctionConditions());
            }
            if (second.equalsIgnoreCase("or")) {
                throw failure("Media types may only be followed by 'and'.");
            }
            if (atQueryEnd()) {
                return new CssMediaQuery(first, second, true, List.of());
            }
            if (!lookingAtIdentifier()) {
                throw failure("Expected 'and' after media modifier and type.");
            }
            var conjunction = identifier("Expected 'and' after media modifier and type.");
            if (!conjunction.equalsIgnoreCase("and")) {
                throw failure("Expected 'and' after media modifier and type.");
            }
            skipTrivia();
            return new CssMediaQuery(first, second, true, conjunctionConditions());
        }

        /// Parses a query made only of one or more parenthesized conditions.
        ///
        /// @return the parsed condition query
        private CssMediaQuery parseConditionQuery() {
            var conditions = new ArrayList<String>();
            conditions.add(condition());
            skipTrivia();
            if (atQueryEnd()) {
                return new CssMediaQuery(null, null, true, conditions);
            }
            var operator = identifier("Expected 'and', 'or', or the end of the media query.");
            if (!operator.equalsIgnoreCase("and") && !operator.equalsIgnoreCase("or")) {
                throw failure("Expected 'and', 'or', or the end of the media query.");
            }
            var conjunction = operator.equalsIgnoreCase("and");
            skipTrivia();
            conditions.add(condition());
            while (true) {
                skipTrivia();
                if (atQueryEnd()) {
                    return new CssMediaQuery(null, null, conjunction, conditions);
                }
                var next = identifier("Expected a media condition or the end of the media query.");
                if (!next.equalsIgnoreCase(operator)) {
                    throw failure("Media conditions may not mix 'and' and 'or'.");
                }
                skipTrivia();
                conditions.add(condition());
            }
        }

        /// Parses conditions joined by {@code and} after a media type.
        ///
        /// @return the parsed condition list
        private @Unmodifiable List<String> conjunctionConditions() {
            var conditions = new ArrayList<String>();
            conditions.add(condition());
            while (true) {
                skipTrivia();
                if (atQueryEnd()) {
                    return List.copyOf(conditions);
                }
                var operator = identifier("Expected 'and' or the end of the media query.");
                if (!operator.equalsIgnoreCase("and")) {
                    throw failure("Expected 'and' or the end of the media query.");
                }
                skipTrivia();
                conditions.add(condition());
            }
        }

        /// Parses one parenthesized media condition or a negated condition.
        ///
        /// @return the normalized condition text
        private String condition() {
            if (lookingAtIdentifier()) {
                var before = index;
                var token = identifier("Expected a media condition.");
                if (token.equalsIgnoreCase("not")) {
                    skipTrivia();
                    if (peek() == '(') {
                        return negatedCondition();
                    }
                }
                index = before;
            }
            return parenthesizedCondition();
        }

        /// Parses {@code not} followed by one parenthesized media condition.
        ///
        /// @return the normalized parenthesized negated condition
        private String negatedCondition() {
            return "(not " + parenthesizedCondition() + ")";
        }

        /// Reads one balanced parenthesized condition without parsing its CSS body.
        ///
        /// @return the condition with one outer parenthesis pair
        private String parenthesizedCondition() {
            if (peek() != '(') {
                throw failure("Expected a media condition in parentheses.");
            }
            var start = index;
            var depth = 0;
            while (!atEnd()) {
                var next = contents.charAt(index++);
                if (next == '\\' || next == '\'' || next == '"') {
                    consumeEscapedOrQuoted(next);
                    continue;
                }
                if (next == '/' && peek() == '*') {
                    index++;
                    consumeComment();
                    continue;
                }
                if (next == '(') {
                    depth++;
                    continue;
                }
                if (next == ')') {
                    depth--;
                    if (depth == 0) {
                        var contents = this.contents.substring(start + 1, index - 1).strip();
                        if (contents.isEmpty()) {
                            throw failure("Media conditions may not be empty.");
                        }
                        return "(" + contents + ")";
                    }
                }
            }
            throw failure("Expected ')' to close the media condition.");
        }

        /// Consumes the remainder of an escape, quoted string, or both.
        ///
        /// @param opening the already-consumed backslash or string delimiter
        private void consumeEscapedOrQuoted(char opening) {
            if (opening == '\\') {
                if (!atEnd()) {
                    index++;
                }
                return;
            }
            while (!atEnd()) {
                var next = contents.charAt(index++);
                if (next == '\\') {
                    if (!atEnd()) {
                        index++;
                    }
                } else if (next == opening) {
                    return;
                }
            }
            throw failure("Expected closing quote in media condition.");
        }

        /// Consumes the remainder of an already-open CSS block comment.
        private void consumeComment() {
            while (!atEnd()) {
                if (contents.charAt(index++) == '*' && peek() == '/') {
                    index++;
                    return;
                }
            }
            throw failure("Expected '*/' to close media-query comment.");
        }

        /// Consumes whitespace and CSS block comments between grammar tokens.
        private void skipTrivia() {
            while (!atEnd()) {
                if (Character.isWhitespace(peek())) {
                    index++;
                } else if (peek() == '/' && peek(1) == '*') {
                    index += 2;
                    consumeComment();
                } else {
                    return;
                }
            }
        }

        /// Reads one CSS identifier used as a media token.
        ///
        /// @param message the failure text when no identifier begins here
        /// @return the identifier spelling
        private String identifier(String message) {
            if (!lookingAtIdentifier()) {
                throw failure(message);
            }
            var start = index;
            index++;
            while (!atEnd()) {
                var next = peek();
                if (next == '\\') {
                    index++;
                    if (!atEnd()) {
                        index++;
                    }
                } else if (isIdentifierContinue(next)) {
                    index++;
                } else {
                    break;
                }
            }
            return contents.substring(start, index);
        }

        /// Returns whether a CSS identifier begins at the current position.
        ///
        /// @return whether an identifier can be read
        private boolean lookingAtIdentifier() {
            return !atEnd() && isIdentifierStart(peek());
        }

        /// Returns whether the current token is followed by a query delimiter.
        ///
        /// @return whether parsing has reached a comma or end of input
        private boolean atQueryEnd() {
            return atEnd() || peek() == ',';
        }

        /// Returns whether the parser has consumed all source text.
        ///
        /// @return whether no next code unit exists
        private boolean atEnd() {
            return index >= contents.length();
        }

        /// Returns the current code unit or a zero sentinel at end of input.
        ///
        /// @return the current code unit, or zero at end of input
        private char peek() {
            return peek(0);
        }

        /// Returns one lookahead code unit or a zero sentinel past end of input.
        ///
        /// @param offset the nonnegative lookahead offset
        /// @return the requested code unit, or zero past end of input
        private char peek(int offset) {
            var target = index + offset;
            return target < contents.length() ? contents.charAt(target) : 0;
        }

        /// Consumes {@code expected} when it is the current code unit.
        ///
        /// @param expected the code unit to consume
        /// @return whether the code unit was consumed
        private boolean scan(char expected) {
            if (peek() != expected) {
                return false;
            }
            index++;
            return true;
        }

        /// Creates a media-query parsing failure.
        ///
        /// @param message the human-readable failure text
        /// @return the exception to throw
        private SassValueException failure(String message) {
            return new SassValueException(message);
        }

        /// Returns whether one code unit may begin a CSS identifier.
        ///
        /// @param value the code unit to inspect
        /// @return whether an identifier may start with {@code value}
        private static boolean isIdentifierStart(char value) {
            return value == '-' || value == '_' || value == '\\'
                    || value >= 0x80 || value >= 'A' && value <= 'Z'
                    || value >= 'a' && value <= 'z';
        }

        /// Returns whether one code unit may continue a CSS identifier.
        ///
        /// @param value the code unit to inspect
        /// @return whether an identifier may continue with {@code value}
        private static boolean isIdentifierContinue(char value) {
            return isIdentifierStart(value) || value >= '0' && value <= '9';
        }
    }
}
