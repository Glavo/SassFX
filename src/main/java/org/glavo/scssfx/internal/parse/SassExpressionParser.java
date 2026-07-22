// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.BinaryOperator;
import org.glavo.scssfx.internal.ast.BooleanExpression;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.InterpolationBuffer;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.ListSeparator;
import org.glavo.scssfx.internal.ast.NullExpression;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.UnaryOperationExpression;
import org.glavo.scssfx.internal.ast.UnaryOperator;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/// Parses the syntax-only SassScript expression subset shared by SCSS constructs.
///
/// This parser preserves literal, operator, list, parenthesis, variable, and
/// interpolation structure. Evaluation and function, map, color, calculation,
/// selector, and Unicode-range expressions are intentionally handled by later
/// implementation stages.
@ApiStatus.Internal
@NotNullByDefault
class SassExpressionParser extends Parser {
    /// Contains identifier spellings reserved for unsupported named-color expressions.
    private static final @Unmodifiable Set<String> NAMED_COLORS = Set.of(
            "yellowgreen",
            "yellow",
            "whitesmoke",
            "white",
            "wheat",
            "violet",
            "turquoise",
            "transparent",
            "tomato",
            "thistle",
            "teal",
            "tan",
            "steelblue",
            "springgreen",
            "snow",
            "slategrey",
            "slategray",
            "slateblue",
            "skyblue",
            "silver",
            "sienna",
            "seashell",
            "seagreen",
            "sandybrown",
            "salmon",
            "saddlebrown",
            "royalblue",
            "rosybrown",
            "red",
            "rebeccapurple",
            "purple",
            "powderblue",
            "plum",
            "pink",
            "peru",
            "peachpuff",
            "papayawhip",
            "palevioletred",
            "paleturquoise",
            "palegreen",
            "palegoldenrod",
            "orchid",
            "orangered",
            "orange",
            "olivedrab",
            "olive",
            "oldlace",
            "navy",
            "navajowhite",
            "moccasin",
            "mistyrose",
            "mintcream",
            "midnightblue",
            "mediumvioletred",
            "mediumturquoise",
            "mediumspringgreen",
            "mediumslateblue",
            "mediumseagreen",
            "mediumpurple",
            "mediumorchid",
            "mediumblue",
            "mediumaquamarine",
            "maroon",
            "magenta",
            "linen",
            "limegreen",
            "lime",
            "lightyellow",
            "lightsteelblue",
            "lightslategrey",
            "lightslategray",
            "lightskyblue",
            "lightseagreen",
            "lightsalmon",
            "lightpink",
            "lightgrey",
            "lightgreen",
            "lightgray",
            "lightgoldenrodyellow",
            "lightcyan",
            "lightcoral",
            "lightblue",
            "lemonchiffon",
            "lawngreen",
            "lavenderblush",
            "lavender",
            "khaki",
            "ivory",
            "indigo",
            "indianred",
            "hotpink",
            "honeydew",
            "grey",
            "greenyellow",
            "green",
            "gray",
            "goldenrod",
            "gold",
            "ghostwhite",
            "gainsboro",
            "fuchsia",
            "forestgreen",
            "floralwhite",
            "firebrick",
            "dodgerblue",
            "dimgrey",
            "dimgray",
            "deepskyblue",
            "deeppink",
            "darkviolet",
            "darkturquoise",
            "darkslategrey",
            "darkslategray",
            "darkslateblue",
            "darkseagreen",
            "darksalmon",
            "darkred",
            "darkorchid",
            "darkorange",
            "darkolivegreen",
            "darkmagenta",
            "darkkhaki",
            "darkgrey",
            "darkgreen",
            "darkgray",
            "darkgoldenrod",
            "darkcyan",
            "darkblue",
            "cyan",
            "crimson",
            "cornsilk",
            "cornflowerblue",
            "coral",
            "chocolate",
            "chartreuse",
            "cadetblue",
            "burlywood",
            "brown",
            "blueviolet",
            "blue",
            "blanchedalmond",
            "black",
            "bisque",
            "beige",
            "azure",
            "aquamarine",
            "aqua",
            "antiquewhite",
            "aliceblue"
    );

    /// Records whether the current expression is being parsed within parentheses.
    private boolean inParentheses;

    /// Creates a parser for expressions in an indexed source.
    ///
    /// @param source the source containing SassScript expressions
    SassExpressionParser(SourceFile source) {
        super(source);
    }

    /// Parses the complete source as one SassScript expression.
    ///
    /// Leading and trailing trivia is consumed but excluded from the returned
    /// expression's span.
    ///
    /// @return the parsed expression
    /// @throws ParseException if no expression is present, the expression is
    /// malformed, or unsupported expression syntax is encountered
    SassExpression parseExpression() {
        whitespace(true);
        if (!lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }
        var result = expression();
        whitespace(true);
        scanner.expectDone();
        return result;
    }

    /// Parses an expression at the current scanner position.
    ///
    /// Parsing stops before the first code unit that cannot continue an
    /// expression. Any trivia immediately before that code unit is consumed.
    ///
    /// @return the parsed expression
    /// @throws ParseException if no expression begins here or it is malformed
    protected final SassExpression expression() {
        if (!lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }
        return commaExpression();
    }

    /// Parses a comma-separated expression or a single lower-level expression.
    ///
    /// @return the parsed expression
    private SassExpression commaExpression() {
        var first = spaceExpression();
        if (scanner.peek() != ',') {
            return first;
        }

        var contents = new ArrayList<SassExpression>();
        contents.add(first);
        while (scanner.scan(',')) {
            whitespace(true);
            if (lookingAtExpression()) {
                contents.add(spaceExpression());
                continue;
            }
            if (scanner.peek() == ',') {
                throw scanner.error("Expected expression.");
            }
            break;
        }

        return new ListExpression(
                contents,
                ListSeparator.COMMA,
                false,
                scanner.source().span(first.span().start().offset(), scanner.position())
        );
    }

    /// Parses expressions separated implicitly as one space list.
    ///
    /// Sass permits some adjacent expression forms without a literal whitespace
    /// code unit; these still use the space-list separator in the syntax tree.
    ///
    /// @return the parsed expression or space list
    private SassExpression spaceExpression() {
        var first = slashAwareBinaryExpression();
        @Nullable ArrayList<SassExpression> contents = null;

        while (true) {
            whitespace(true);
            if (!lookingAtExpression()) {
                break;
            }
            if (contents == null) {
                contents = new ArrayList<>();
                contents.add(first);
            }
            contents.add(slashAwareBinaryExpression());
        }

        if (contents == null) {
            return first;
        }
        var last = contents.get(contents.size() - 1);
        return new ListExpression(
                contents,
                ListSeparator.SPACE,
                false,
                scanner.source().span(
                        first.span().start().offset(),
                        last.span().end().offset()
                )
        );
    }

    /// Parses one precedence-ordered binary expression and applies slash metadata.
    ///
    /// @return the parsed expression
    private SassExpression slashAwareBinaryExpression() {
        var result = binaryExpression(0);
        return !inParentheses && isSlashTree(result)
                ? markSlashTree(result)
                : result;
    }

    /// Parses binary operators whose precedence is at least the requested value.
    ///
    /// @param minimumPrecedence the lowest accepted binary precedence
    /// @return the parsed expression
    private SassExpression binaryExpression(int minimumPrecedence) {
        var left = singleExpression();

        while (true) {
            var beforeTrivia = scanner.state();
            whitespace(true);
            var operatorStart = scanner.state();
            var preceding = operatorStart.position() == 0
                    ? CssCharacters.END_OF_INPUT
                    : scanner.source().content().charAt(operatorStart.position() - 1);
            var precededByWhitespace = CssCharacters.isWhitespace(preceding);
            @Nullable BinaryOperator operator = scanBinaryOperator(precededByWhitespace);
            if (operator == null || operator.precedence() < minimumPrecedence) {
                scanner.restore(beforeTrivia);
                return left;
            }

            var operatorEnd = scanner.state();
            whitespace(true);
            if (!lookingAtExpression()) {
                throw scanner.error(
                        "Expected expression.",
                        operatorStart.position(),
                        operatorEnd.position() - operatorStart.position()
                );
            }

            var right = binaryExpression(operator.precedence() + 1);
            left = new BinaryOperationExpression(
                    operator,
                    left,
                    right,
                    false,
                    scanner.spanFrom(operatorStart, operatorEnd),
                    scanner.source().span(
                            left.span().start().offset(),
                            right.span().end().offset()
                    )
            );
        }
    }

    /// Consumes a binary operator when one begins at the current position.
    ///
    /// A minus followed immediately by a number is not binary when whitespace
    /// separates it from the preceding expression. A minus beginning an
    /// identifier is likewise left for the string-expression parser.
    ///
    /// @param precededByWhitespace whether a whitespace code unit immediately
    /// precedes the candidate operator
    /// @return the consumed operator, or {@code null} without consuming input
    private @Nullable BinaryOperator scanBinaryOperator(boolean precededByWhitespace) {
        var start = scanner.state();
        @Nullable BinaryOperator result = switch (scanner.peek()) {
            case '=' -> scanner.peek(1) == '='
                    ? scanTwoCodeUnitOperator(BinaryOperator.EQUALS)
                    : null;
            case '!' -> scanner.peek(1) == '='
                    ? scanTwoCodeUnitOperator(BinaryOperator.NOT_EQUALS)
                    : null;
            case '<' -> {
                scanner.read();
                yield scanner.scan('=')
                        ? BinaryOperator.LESS_THAN_OR_EQUALS
                        : BinaryOperator.LESS_THAN;
            }
            case '>' -> {
                scanner.read();
                yield scanner.scan('=')
                        ? BinaryOperator.GREATER_THAN_OR_EQUALS
                        : BinaryOperator.GREATER_THAN;
            }
            case '*' -> scanOneCodeUnitOperator(BinaryOperator.TIMES);
            case '+' -> scanOneCodeUnitOperator(BinaryOperator.PLUS);
            case '-' -> {
                if (lookingAtIdentifier()
                        || precededByWhitespace && (CssCharacters.isDigit(scanner.peek(1))
                        || scanner.peek(1) == '.')) {
                    yield null;
                }
                yield scanOneCodeUnitOperator(BinaryOperator.MINUS);
            }
            case '/' -> scanOneCodeUnitOperator(BinaryOperator.DIVIDED_BY);
            case '%' -> scanOneCodeUnitOperator(BinaryOperator.MODULO);
            case 'a' -> scanIdentifier("and") ? BinaryOperator.AND : null;
            case 'o' -> scanIdentifier("or") ? BinaryOperator.OR : null;
            default -> null;
        };
        if (result == null) {
            scanner.restore(start);
        }
        return result;
    }

    /// Consumes and returns a one-code-unit binary operator.
    ///
    /// @param operator the operator beginning here
    /// @return {@code operator}
    private BinaryOperator scanOneCodeUnitOperator(BinaryOperator operator) {
        scanner.read();
        return operator;
    }

    /// Consumes and returns a two-code-unit binary operator.
    ///
    /// @param operator the operator beginning here
    /// @return {@code operator}
    private BinaryOperator scanTwoCodeUnitOperator(BinaryOperator operator) {
        scanner.read();
        scanner.read();
        return operator;
    }

    /// Returns whether an expression consists solely of slash operations over numbers.
    ///
    /// @param expression the expression to inspect
    /// @return whether the tree may represent slash-separated numbers
    private boolean isSlashTree(SassExpression expression) {
        if (expression instanceof NumberExpression) {
            return true;
        }
        return expression instanceof BinaryOperationExpression binary
                && binary.operator() == BinaryOperator.DIVIDED_BY
                && isSlashTree(binary.left())
                && isSlashTree(binary.right());
    }

    /// Rebuilds a slash-only expression tree with slash metadata enabled.
    ///
    /// @param expression a tree accepted by [#isSlashTree(SassExpression)]
    /// @return the metadata-preserving slash tree
    private SassExpression markSlashTree(SassExpression expression) {
        if (!(expression instanceof BinaryOperationExpression binary)) {
            return expression;
        }
        return new BinaryOperationExpression(
                binary.operator(),
                markSlashTree(binary.left()),
                markSlashTree(binary.right()),
                true,
                binary.operatorSpan(),
                binary.span()
        );
    }

    /// Parses one literal, reference, parenthesized expression, or unary operation.
    ///
    /// @return the parsed expression
    private SassExpression singleExpression() {
        var next = scanner.peek();
        if (next == CssCharacters.END_OF_INPUT) {
            throw scanner.error("Expected expression.");
        }
        if ((next == 'u' || next == 'U') && scanner.peek(1) == '+') {
            throw scanner.error("Unicode range expressions are not available.");
        }

        return switch (next) {
            case '(' -> parenthesizedExpression();
            case '[' -> bracketedList();
            case '$' -> variableExpression(null, scanner.state());
            case '\'', '"' -> interpolatedString();
            case '#' -> hashExpression();
            case '+' -> lookingAtNumber() ? numberExpression() : unaryExpression();
            case '-' -> lookingAtNumber()
                    ? numberExpression()
                    : lookingAtInterpolatedIdentifier()
                    ? identifierLikeExpression()
                    : unaryExpression();
            case '/' -> unaryExpression();
            case '%' -> percentExpression();
            case '.' -> numberExpression();
            case '&' -> throw scanner.error("Selector expressions are not available.");
            case '!' -> importantExpression();
            default -> {
                if (CssCharacters.isDigit(next)) {
                    yield numberExpression();
                }
                if (lookingAtInterpolatedIdentifier()) {
                    yield identifierLikeExpression();
                }
                throw scanner.error("Expected expression.");
            }
        };
    }

    /// Parses an {@code !important} expression as a plain unquoted string.
    ///
    /// Whitespace and comments between the exclamation mark and identifier are
    /// accepted, while the resulting value uses the canonical spelling.
    ///
    /// @return the canonical {@code !important} string expression
    private StringExpression importantExpression() {
        var start = scanner.state();
        scanner.expect('!');
        whitespace(true);
        expectIdentifier("important");
        return StringExpression.plain("!important", scanner.spanFrom(start));
    }

    /// Parses a parenthesized expression or an empty parenthesized list.
    ///
    /// @return the parsed expression
    private SassExpression parenthesizedExpression() {
        var start = scanner.state();
        scanner.expect('(');
        whitespace(true);
        if (scanner.scan(')')) {
            return new ListExpression(
                    java.util.List.of(),
                    ListSeparator.UNDECIDED,
                    false,
                    scanner.spanFrom(start)
            );
        }
        if (!lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }

        var inside = scanner.state();
        var previousInParentheses = inParentheses;
        inParentheses = true;
        try {
            var expression = commaExpression();
            if (expression instanceof ListExpression list
                    && !list.hasBrackets()
                    && list.separator() != ListSeparator.UNDECIDED) {
                scanner.restore(inside);
                inParentheses = false;
                expression = commaExpression();
            }
            if (scanner.peek() == ':') {
                throw scanner.error("Map expressions are not available.");
            }
            scanner.expect(')');
            return new ParenthesizedExpression(expression, scanner.spanFrom(start));
        } finally {
            inParentheses = previousInParentheses;
        }
    }

    /// Parses a bracketed list and moves its list semantics onto the bracket node.
    ///
    /// @return the bracketed list
    private ListExpression bracketedList() {
        var start = scanner.state();
        scanner.expect('[');
        whitespace(true);
        if (scanner.scan(']')) {
            return new ListExpression(
                    java.util.List.of(),
                    ListSeparator.UNDECIDED,
                    true,
                    scanner.spanFrom(start)
            );
        }
        if (!lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }

        var expression = commaExpression();
        scanner.expect(']');
        var span = scanner.spanFrom(start);
        if (expression instanceof ListExpression list && !list.hasBrackets()) {
            return new ListExpression(list.contents(), list.separator(), true, span);
        }
        return new ListExpression(
                java.util.List.of(expression),
                ListSeparator.UNDECIDED,
                true,
                span
        );
    }

    /// Parses a unary operation whose operand is one single expression.
    ///
    /// @return the unary operation
    private UnaryOperationExpression unaryExpression() {
        var start = scanner.state();
        var operator = switch (scanner.read()) {
            case '+' -> UnaryOperator.PLUS;
            case '-' -> UnaryOperator.MINUS;
            case '/' -> UnaryOperator.DIVIDE;
            default -> throw scanner.error(
                    "Expected unary operator.",
                    start.position(),
                    1
            );
        };
        whitespace(true);
        var operand = singleExpression();
        return new UnaryOperationExpression(operator, operand, scanner.spanFrom(start));
    }

    /// Parses a signed or unsigned number and its optional single literal unit.
    ///
    /// @return the number literal
    private NumberExpression numberExpression() {
        var start = scanner.state();
        var first = scanner.peek();
        if (first == '+' || first == '-') {
            scanner.read();
        }

        if (scanner.peek() != '.') {
            consumeNaturalNumber();
        }
        tryDecimal(
                scanner.position() != start.position()
                        && first != '+'
                        && first != '-'
        );
        tryExponent();

        var numberEnd = scanner.position();
        var value = Double.parseDouble(scanner.substring(start.position(), numberEnd));
        @Nullable String unit = null;
        if (scanner.scan('%')) {
            unit = "%";
        } else if (lookingAtIdentifier()
                && (scanner.peek() != '-' || scanner.peek(1) != '-')) {
            unit = identifier(false, true);
        }
        return new NumberExpression(value, unit, scanner.spanFrom(start));
    }

    /// Consumes one or more decimal digits.
    private void consumeNaturalNumber() {
        if (!CssCharacters.isDigit(scanner.peek())) {
            throw scanner.error("Expected digit.");
        }
        do {
            scanner.read();
        } while (CssCharacters.isDigit(scanner.peek()));
    }

    /// Consumes a decimal fraction when present.
    ///
    /// @param allowTrailingDot whether a dot without a following digit is left unconsumed
    private void tryDecimal(boolean allowTrailingDot) {
        if (scanner.peek() != '.') {
            return;
        }
        if (!CssCharacters.isDigit(scanner.peek(1))) {
            if (allowTrailingDot) {
                return;
            }
            var position = scanner.position() + 1;
            throw scanner.error(
                    "Expected digit.",
                    position,
                    position == scanner.source().length() ? 0 : 1
            );
        }
        scanner.read();
        while (CssCharacters.isDigit(scanner.peek())) {
            scanner.read();
        }
    }

    /// Consumes scientific notation when its exponent marker is committed.
    private void tryExponent() {
        var first = scanner.peek();
        if (first != 'e' && first != 'E') {
            return;
        }
        var next = scanner.peek(1);
        if (!CssCharacters.isDigit(next) && next != '+' && next != '-') {
            return;
        }

        scanner.read();
        if (next == '+' || next == '-') {
            scanner.read();
        }
        if (!CssCharacters.isDigit(scanner.peek())) {
            throw scanner.error("Expected digit.");
        }
        while (CssCharacters.isDigit(scanner.peek())) {
            scanner.read();
        }
    }

    /// Parses a variable reference with an optional namespace.
    ///
    /// @param namespace the namespace, or {@code null} for an unqualified variable
    /// @param start the state before the namespace or dollar sign
    /// @return the variable reference
    private VariableExpression variableExpression(
            @Nullable String namespace,
            ScannerState start
    ) {
        var name = variableName();
        if (namespace != null && name.startsWith("-")) {
            throw scanner.error(
                    "Private members can't be accessed from outside their modules.",
                    start.position(),
                    scanner.position() - start.position()
            );
        }
        return new VariableExpression(namespace, name, scanner.spanFrom(start));
    }

    /// Parses a quoted string whose decoded contents may contain interpolation.
    ///
    /// @return the quoted string expression
    private StringExpression interpolatedString() {
        var start = scanner.state();
        var quote = scanner.read();
        var buffer = new InterpolationBuffer();

        while (true) {
            var next = scanner.peek();
            if (next == quote) {
                scanner.read();
                return new StringExpression(
                        buffer.interpolation(scanner.spanFrom(start)),
                        true
                );
            }
            if (next == CssCharacters.END_OF_INPUT || CssCharacters.isNewline(next)) {
                throw scanner.error("Expected " + (char) quote + ".");
            }
            if (next == '\\') {
                var second = scanner.peek(1);
                if (CssCharacters.isNewline(second)) {
                    scanner.read();
                    scanner.read();
                    if (second == '\r') {
                        scanner.scan('\n');
                    }
                } else {
                    buffer.appendCodePoint(escapeCharacter());
                }
            } else if (next == '#' && scanner.peek(1) == '{') {
                singleInterpolation(buffer);
            } else {
                buffer.append((char) scanner.read());
            }
        }
    }

    /// Parses a raw quoted string token while retaining interpolation structure.
    ///
    /// Escapes and quote delimiters retain their exact source spelling. This is
    /// used by stylesheet contexts whose text is reparsed after evaluation.
    ///
    /// @return the interpolated raw string token
    protected final Interpolation interpolatedStringToken() {
        var start = scanner.state();
        var quote = scanner.read();
        if (quote != '\'' && quote != '"') {
            throw scanner.error("Expected string.", start.position(), 1);
        }

        var buffer = new InterpolationBuffer();
        buffer.append((char) quote);
        while (true) {
            var next = scanner.peek();
            if (next == quote) {
                buffer.append((char) scanner.read());
                return buffer.interpolation(scanner.spanFrom(start));
            }
            if (next == CssCharacters.END_OF_INPUT || CssCharacters.isNewline(next)) {
                throw scanner.error("Expected " + (char) quote + ".");
            }
            if (next == '\\') {
                var escapeStart = scanner.position();
                var second = scanner.peek(1);
                if (CssCharacters.isNewline(second)) {
                    scanner.read();
                    scanner.read();
                    if (second == '\r') {
                        scanner.scan('\n');
                    }
                } else {
                    escapeCharacter();
                }
                buffer.append(scanner.substring(escapeStart));
            } else if (next == '#' && scanner.peek(1) == '{') {
                singleInterpolation(buffer);
            } else {
                buffer.append((char) scanner.read());
            }
        }
    }

    /// Parses an expression beginning with a hash.
    ///
    /// @return the interpolated unquoted string
    private SassExpression hashExpression() {
        if (scanner.peek(1) == '{') {
            return identifierLikeExpression();
        }

        var start = scanner.state();
        scanner.expect('#');
        if (CssCharacters.isDigit(scanner.peek())) {
            throw scanner.error(
                    "Color expressions are not available.",
                    start.position(),
                    1
            );
        }
        var identifier = interpolatedIdentifier();
        var plain = identifier.asPlain();
        if (plain != null && isHexColorName(plain)) {
            throw scanner.error(
                    "Color expressions are not available.",
                    start.position(),
                    1
            );
        }

        var buffer = new InterpolationBuffer();
        buffer.append('#');
        buffer.add(identifier);
        return new StringExpression(
                buffer.interpolation(scanner.spanFrom(start)),
                false
        );
    }

    /// Returns whether plain text has a hexadecimal color-literal length and alphabet.
    ///
    /// @param text the text after a hash
    /// @return whether the complete hash expression would be a color literal
    private boolean isHexColorName(String text) {
        var length = text.length();
        if (length != 3 && length != 4 && length != 6 && length != 8) {
            return false;
        }
        for (var index = 0; index < length; index++) {
            if (!CssCharacters.isHex(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /// Parses a percent sign used as an unquoted string expression.
    ///
    /// @return the percent string
    private StringExpression percentExpression() {
        var start = scanner.state();
        scanner.read();
        return new StringExpression(
                Interpolation.plain("%", scanner.spanFrom(start)),
                false
        );
    }

    /// Parses an identifier-like expression and its reserved forms.
    ///
    /// @return the parsed string, boolean, null, variable, or unary expression
    private SassExpression identifierLikeExpression() {
        var start = scanner.state();
        var identifier = interpolatedIdentifier();
        @Nullable String plain = identifier.asPlain();

        if (plain != null && plain.equals("not")) {
            whitespace(true);
            var operand = singleExpression();
            return new UnaryOperationExpression(
                    UnaryOperator.NOT,
                    operand,
                    scanner.source().span(start.position(), operand.span().end().offset())
            );
        }

        if (scanner.peek() == '(') {
            throw scanner.error(
                    "Function expressions are not available.",
                    scanner.position(),
                    1
            );
        }
        if (scanner.scan('.')) {
            if (plain != null && scanner.peek() == '$') {
                return variableExpression(plain, start);
            }
            throw scanner.error(
                    "Namespaced function expressions are not available.",
                    scanner.position() - 1,
                    1
            );
        }

        if (plain != null) {
            switch (plain) {
                case "true" -> {
                    return new BooleanExpression(true, identifier.span());
                }
                case "false" -> {
                    return new BooleanExpression(false, identifier.span());
                }
                case "null" -> {
                    return new NullExpression(identifier.span());
                }
                default -> {
                    // All other supported identifier-like expressions are strings.
                }
            }
            if (NAMED_COLORS.contains(plain.toLowerCase(Locale.ROOT))) {
                throw scanner.error(
                        "Color expressions are not available.",
                        identifier.span().start().offset(),
                        identifier.span().text().length()
                );
            }
        }
        return new StringExpression(identifier, false);
    }

    /// Parses an identifier whose body may contain any number of interpolations.
    ///
    /// @return the parsed identifier interpolation
    protected final Interpolation interpolatedIdentifier() {
        var start = scanner.state();
        var buffer = new InterpolationBuffer();

        if (scanner.scan('-')) {
            buffer.append('-');
            if (scanner.scan('-')) {
                buffer.append('-');
                interpolatedIdentifierBody(buffer);
                return buffer.interpolation(scanner.spanFrom(start));
            }
        }

        var next = scanner.peek();
        if (CssCharacters.isNameStart(next)) {
            buffer.append((char) scanner.read());
        } else if (next == '\\') {
            buffer.append(escape(true));
        } else if (next == '#' && scanner.peek(1) == '{') {
            singleInterpolation(buffer);
        } else {
            throw scanner.error("Expected identifier.");
        }

        interpolatedIdentifierBody(buffer);
        return buffer.interpolation(scanner.spanFrom(start));
    }

    /// Parses the remainder of an interpolated identifier into a buffer.
    ///
    /// @param buffer the destination interpolation buffer
    private void interpolatedIdentifierBody(InterpolationBuffer buffer) {
        while (true) {
            var next = scanner.peek();
            if (CssCharacters.isName(next)) {
                buffer.append((char) scanner.read());
            } else if (next == '\\') {
                buffer.append(escape(false));
            } else if (next == '#' && scanner.peek(1) == '{') {
                singleInterpolation(buffer);
            } else {
                return;
            }
        }
    }

    /// Consumes one interpolation and adds its expression and wrapper span to a buffer.
    ///
    /// @param buffer the interpolation receiving the parsed expression
    protected final void singleInterpolation(InterpolationBuffer buffer) {
        var start = scanner.state();
        scanner.expect("#{");
        whitespace(true);
        if (!lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }
        var contents = expression();
        scanner.expect('}');
        buffer.add(contents, scanner.spanFrom(start));
    }

    /// Returns whether an identifier, including interpolation, begins here.
    ///
    /// @return whether an interpolated identifier begins at the scanner position
    protected final boolean lookingAtInterpolatedIdentifier() {
        return lookingAtIdentifier()
                || scanner.peek() == '#' && scanner.peek(1) == '{'
                || scanner.peek() == '-'
                && scanner.peek(1) == '#'
                && scanner.peek(2) == '{';
    }

    /// Returns whether any supported single expression begins here.
    ///
    /// @return whether the current position can begin an expression
    private boolean lookingAtExpression() {
        var next = scanner.peek();
        return switch (next) {
            case '(', '[', '$', '\'', '"', '#', '+', '-', '/', '%', '&' -> true;
            case '!' -> {
                var following = scanner.peek(1);
                yield following == CssCharacters.END_OF_INPUT
                        || following == 'i'
                        || following == 'I'
                        || CssCharacters.isWhitespace(following);
            }
            case '.' -> scanner.peek(1) != '.';
            default -> CssCharacters.isDigit(next) || lookingAtInterpolatedIdentifier();
        };
    }
}
