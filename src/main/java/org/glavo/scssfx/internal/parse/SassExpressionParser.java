// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.ArgumentList;
import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.BinaryOperator;
import org.glavo.scssfx.internal.ast.BooleanExpression;
import org.glavo.scssfx.internal.ast.ColorExpression;
import org.glavo.scssfx.internal.ast.FunctionExpression;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.InterpolationBuffer;
import org.glavo.scssfx.internal.ast.InterpolatedFunctionExpression;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.ListSeparator;
import org.glavo.scssfx.internal.ast.MapEntry;
import org.glavo.scssfx.internal.ast.MapExpression;
import org.glavo.scssfx.internal.ast.NullExpression;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.UnaryOperationExpression;
import org.glavo.scssfx.internal.ast.UnaryOperator;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.source.SourceFile;
import org.glavo.scssfx.internal.value.SassColor;
import org.glavo.scssfx.internal.value.SpanColorFormat;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/// Parses the syntax-only SassScript expression subset shared by SCSS constructs.
///
/// This parser preserves literal, operator, list, parenthesis, variable,
/// function, map, color, and interpolation structure. Evaluation and selector
/// and Unicode-range expressions are intentionally handled by later
/// implementation stages.
@ApiStatus.Internal
@NotNullByDefault
class SassExpressionParser extends Parser {
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
        var first = spaceExpression(false);
        if (scanner.peek() != ',') {
            return first;
        }

        var contents = new ArrayList<SassExpression>();
        contents.add(first);
        while (scanner.scan(',')) {
            whitespace(true);
            if (lookingAtExpression()) {
                contents.add(spaceExpression(false));
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

    /// Parses an expression through the code unit before a top-level comma.
    ///
    /// @param singleEquals whether the Microsoft-style single-equals operator
    /// may occur at this expression level
    /// @return the parsed expression
    protected final SassExpression expressionUntilComma(boolean singleEquals) {
        if (!lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }
        return spaceExpression(singleEquals);
    }

    /// Parses expressions separated implicitly as one space list.
    ///
    /// Sass permits some adjacent expression forms without a literal whitespace
    /// code unit; these still use the space-list separator in the syntax tree.
    ///
    /// @param singleEquals whether a lone equals sign is accepted
    /// @return the parsed expression or space list
    private SassExpression spaceExpression(boolean singleEquals) {
        var first = slashAwareBinaryExpression(singleEquals);
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
            contents.add(slashAwareBinaryExpression(singleEquals));
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
    /// @param singleEquals whether a lone equals sign is accepted
    /// @return the parsed expression
    private SassExpression slashAwareBinaryExpression(boolean singleEquals) {
        var result = binaryExpression(0, singleEquals);
        return !inParentheses && isSlashTree(result)
                ? markSlashTree(result)
                : result;
    }

    /// Parses binary operators whose precedence is at least the requested value.
    ///
    /// @param minimumPrecedence the lowest accepted binary precedence
    /// @param singleEquals      whether the Microsoft-style single-equals operator
    /// may occur at this expression level
    /// @return the parsed expression
    private SassExpression binaryExpression(
            int minimumPrecedence,
            boolean singleEquals
    ) {
        var left = singleExpression();

        while (true) {
            var beforeTrivia = scanner.state();
            whitespace(true);
            var operatorStart = scanner.state();
            var preceding = operatorStart.position() == 0
                    ? CssCharacters.END_OF_INPUT
                    : scanner.source().content().charAt(operatorStart.position() - 1);
            var precededByWhitespace = CssCharacters.isWhitespace(preceding);
            @Nullable BinaryOperator operator = scanBinaryOperator(
                    precededByWhitespace,
                    singleEquals
            );
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

            var right = binaryExpression(operator.precedence() + 1, singleEquals);
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
    /// @param singleEquals         whether a lone equals sign is accepted
    /// @return the consumed operator, or {@code null} without consuming input
    private @Nullable BinaryOperator scanBinaryOperator(
            boolean precededByWhitespace,
            boolean singleEquals
    ) {
        var start = scanner.state();
        @Nullable BinaryOperator result = switch (scanner.peek()) {
            case '=' -> scanner.peek(1) == '='
                    ? scanTwoCodeUnitOperator(BinaryOperator.EQUALS)
                    : singleEquals
                    ? scanOneCodeUnitOperator(BinaryOperator.SINGLE_EQUALS)
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

    /// Returns whether an expression consists solely of slash operations over
    /// numbers or plain function calls.
    ///
    /// @param expression the expression to inspect
    /// @return whether the tree may represent a slash-separated value
    private boolean isSlashTree(SassExpression expression) {
        if (expression instanceof NumberExpression
                || expression instanceof FunctionExpression) {
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
            throw scanner.error(
                    "Unicode range expressions are not available.",
                    scanner.position(),
                    2
            );
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
        var previousInParentheses = inParentheses;
        inParentheses = true;
        var start = scanner.state();
        try {
            scanner.expect('(');
            whitespace(true);
            var inside = scanner.state();
            if (!lookingAtExpression()) {
                scanner.expect(')');
                return new ListExpression(
                        List.of(),
                        ListSeparator.UNDECIDED,
                        false,
                        scanner.spanFrom(start)
                );
            }

            var first = expressionUntilCommaWithSlashReparse();
            if (scanner.scan(':')) {
                whitespace(true);
                return mapExpression(first, start);
            }

            if (!scanner.scan(',')) {
                scanner.expect(')');
                return new ParenthesizedExpression(first, scanner.spanFrom(start));
            }

            // Once parentheses establish list context, slash operations use
            // the same historical metadata as expressions outside this pair.
            scanner.restore(inside);
            inParentheses = false;
            first = expressionUntilComma(false);
            scanner.expect(',');
            whitespace(true);

            var expressions = new ArrayList<SassExpression>();
            expressions.add(first);
            while (lookingAtExpression()) {
                expressions.add(expressionUntilComma(false));
                if (!scanner.scan(',')) {
                    break;
                }
                whitespace(true);
            }

            var list = new ListExpression(
                    expressions,
                    ListSeparator.COMMA,
                    false,
                    scanner.source().span(inside.position(), scanner.position())
            );
            scanner.expect(')');
            return new ParenthesizedExpression(list, scanner.spanFrom(start));
        } finally {
            inParentheses = previousInParentheses;
        }
    }

    /// Parses through a comma and reparses a discovered space list outside
    /// parenthetical slash context.
    ///
    /// @return the parsed expression
    private SassExpression expressionUntilCommaWithSlashReparse() {
        var start = scanner.state();
        var expression = expressionUntilComma(false);
        if (inParentheses
                && expression instanceof ListExpression list
                && !list.hasBrackets()
                && list.separator() == ListSeparator.SPACE) {
            scanner.restore(start);
            inParentheses = false;
            return expressionUntilComma(false);
        }
        return expression;
    }

    /// Parses a map after its first key and separating colon were consumed.
    ///
    /// @param first the first map key
    /// @param start the position before the opening parenthesis
    /// @return the parsed map expression
    private MapExpression mapExpression(SassExpression first, ScannerState start) {
        var pairs = new ArrayList<MapEntry>();
        pairs.add(new MapEntry(first, expressionUntilCommaWithSlashReparse()));

        while (scanner.scan(',')) {
            whitespace(true);
            if (!lookingAtExpression()) {
                break;
            }

            var key = expressionUntilCommaWithSlashReparse();
            scanner.expect(':');
            whitespace(true);
            var value = expressionUntilCommaWithSlashReparse();
            pairs.add(new MapEntry(key, value));
        }

        scanner.expect(')');
        return new MapExpression(pairs, scanner.spanFrom(start));
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
                    List.of(),
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
                List.of(expression),
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
    /// @param start     the state before the namespace or dollar sign
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
    /// @return the parsed color or interpolated unquoted string
    private SassExpression hashExpression() {
        if (scanner.peek(1) == '{') {
            return identifierLikeExpression();
        }

        var start = scanner.state();
        scanner.expect('#');
        if (CssCharacters.isDigit(scanner.peek())) {
            return hexColorExpression(start);
        }

        var afterHash = scanner.state();
        var identifier = interpolatedIdentifier();
        var plain = identifier.asPlain();
        if (plain != null && isHexColorName(plain)) {
            scanner.restore(afterHash);
            return hexColorExpression(start);
        }

        var buffer = new InterpolationBuffer();
        buffer.append('#');
        buffer.add(identifier);
        return new StringExpression(
                buffer.interpolation(scanner.spanFrom(start)),
                false
        );
    }

    /// Parses a hexadecimal color after its leading hash was consumed.
    ///
    /// @param start the position before the leading hash
    /// @return the hexadecimal color expression
    private ColorExpression hexColorExpression(ScannerState start) {
        var value = hexColorContents(start);
        return new ColorExpression(value, scanner.spanFrom(start));
    }

    /// Parses three, four, six, or eight hexadecimal color digits.
    ///
    /// A color whose alpha channel is omitted retains its complete source span
    /// as formatting metadata. Alpha-bearing colors deliberately omit that
    /// metadata because four- and eight-digit output is not universally
    /// supported by CSS consumers.
    ///
    /// @param start the position before the leading hash
    /// @return the parsed color value
    private SassColor hexColorContents(ScannerState start) {
        var digit1 = hexDigit();
        var digit2 = hexDigit();
        var digit3 = hexDigit();

        double red;
        double green;
        double blue;
        var alpha = 1.0;
        var hasAlpha = false;
        if (!CssCharacters.isHex(scanner.peek())) {
            red = (digit1 << 4) + digit1;
            green = (digit2 << 4) + digit2;
            blue = (digit3 << 4) + digit3;
        } else {
            var digit4 = hexDigit();
            if (!CssCharacters.isHex(scanner.peek())) {
                red = (digit1 << 4) + digit1;
                green = (digit2 << 4) + digit2;
                blue = (digit3 << 4) + digit3;
                alpha = ((digit4 << 4) + digit4) / 255.0;
                hasAlpha = true;
            } else {
                red = (digit1 << 4) + digit2;
                green = (digit3 << 4) + digit4;
                blue = (hexDigit() << 4) + hexDigit();
                if (CssCharacters.isHex(scanner.peek())) {
                    alpha = (hexDigit() << 4) + hexDigit();
                    alpha /= 255.0;
                    hasAlpha = true;
                }
            }
        }

        @Nullable SpanColorFormat format = hasAlpha
                ? null
                : new SpanColorFormat(scanner.spanFrom(start));
        return SassColor.rgb(red, green, blue, alpha, format);
    }

    /// Consumes one hexadecimal digit.
    ///
    /// @return the digit value from zero through fifteen
    /// @throws ParseException if the next code unit is not hexadecimal
    private int hexDigit() {
        if (!CssCharacters.isHex(scanner.peek())) {
            throw scanner.error("Expected hex digit.");
        }
        return CssCharacters.hexValue(scanner.read());
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
    /// @return the parsed literal, function, variable, or unary expression
    private SassExpression identifierLikeExpression() {
        var start = scanner.state();
        var identifier = interpolatedIdentifier();
        @Nullable String plain = identifier.asPlain();

        @Nullable String lower = null;
        if (plain != null) {
            if (plain.equalsIgnoreCase("if") && scanner.peek() == '(') {
                throw scanner.error(
                        "If expressions are not available.",
                        identifier.span().start().offset(),
                        identifier.span().text().length()
                );
            }
            if (plain.equals("not")) {
                whitespace(true);
                var operand = singleExpression();
                return new UnaryOperationExpression(
                        UnaryOperator.NOT,
                        operand,
                        scanner.source().span(
                                start.position(),
                                operand.span().end().offset()
                        )
                );
            }

            lower = plain.toLowerCase(Locale.ROOT);
            if (scanner.peek() != '(') {
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
                        // Continue with named colors and callable syntax.
                    }
                }

                @Nullable SassColor color = SassColor.named(lower, identifier.span());
                if (color != null) {
                    return new ColorExpression(color, identifier.span());
                }
            }

            @Nullable SassExpression specialFunction = trySpecialFunction(lower, start);
            if (specialFunction != null) {
                return specialFunction;
            }
        }

        if (scanner.peek() == '.') {
            if (scanner.peek(1) == '.') {
                return new StringExpression(identifier, false);
            }
            scanner.read();
            if (plain != null) {
                return namespacedExpression(plain, start);
            }
            throw scanner.error(
                    "Interpolation isn't allowed in namespaces.",
                    identifier.span().start().offset(),
                    identifier.span().text().length()
            );
        }

        if (scanner.peek() == '(') {
            if (plain != null) {
                return new FunctionExpression(
                        null,
                        plain,
                        argumentInvocation("var".equals(lower)),
                        scanner.spanFrom(start)
                );
            }
            return new InterpolatedFunctionExpression(
                    identifier,
                    argumentInvocation(false),
                    scanner.spanFrom(start)
            );
        }

        return new StringExpression(identifier, false);
    }

    /// Parses a function whose arguments use raw declaration-value syntax.
    ///
    /// Names passed to this method are lowercase. Failed URL recognition is
    /// transactional so the ordinary function parser can consume the same
    /// opening parenthesis and arguments.
    ///
    /// @param name  the lowercase decoded function name
    /// @param start the position before the function name
    /// @return the special function expression, or {@code null} if the name
    /// and following punctuation do not select special syntax
    private @Nullable SassExpression trySpecialFunction(
            String name,
            ScannerState start
    ) {
        InterpolationBuffer buffer;
        if (name.equals("type") && scanner.scan('(')) {
            buffer = new InterpolationBuffer();
            buffer.append(name);
            buffer.append('(');
        } else {
            var normalized = unvendor(name);
            var vendored = !normalized.equals(name);

            if (normalized.equals("url")) {
                @Nullable Interpolation url = tryInterpolatedUrlContents(start, "url");
                return url == null ? null : new StringExpression(url, false);
            }

            if (normalized.equals("progid") && scanner.scan(':')) {
                buffer = new InterpolationBuffer();
                buffer.append(name);
                buffer.append(':');
                while (CssCharacters.isAlphabetic(scanner.peek())
                        || scanner.peek() == '.') {
                    buffer.append((char) scanner.read());
                }
                scanner.expect('(');
                buffer.append('(');
            } else {
                var rawFunction = normalized.equals("expression")
                        || normalized.equals("element")
                        || vendored && normalized.equals("calc");
                if (!rawFunction || !scanner.scan('(')) {
                    return null;
                }
                buffer = new InterpolationBuffer();
                buffer.append(name);
                buffer.append('(');
            }
        }

        buffer.add(interpolatedDeclarationValue(true, true));
        scanner.expect(')');
        buffer.append(')');
        return new StringExpression(
                buffer.interpolation(scanner.spanFrom(start)),
                false
        );
    }

    /// Removes one CSS vendor prefix from a lowercase identifier.
    ///
    /// Custom-property names beginning with two hyphens are returned unchanged.
    /// A leading hyphen without a second separating hyphen is likewise not a
    /// vendor prefix.
    ///
    /// @param name the identifier to inspect
    /// @return the unprefixed identifier or {@code name}
    private static String unvendor(String name) {
        if (name.length() < 2 || name.charAt(0) != '-' || name.charAt(1) == '-') {
            return name;
        }
        var separator = name.indexOf('-', 2);
        return separator < 0 ? name : name.substring(separator + 1);
    }

    /// Parses the arguments of a function invocation.
    ///
    /// Function arguments accept the Microsoft-style single-equals operator
    /// only at their top level. A second rest argument becomes the keyword-rest
    /// argument and terminates the invocation grammar.
    ///
    /// @param allowEmptySecondArgument whether a trailing comma after the only
    /// positional argument supplies an empty unquoted second argument
    /// @return the parsed invocation arguments
    private ArgumentList argumentInvocation(boolean allowEmptySecondArgument) {
        var start = scanner.state();
        scanner.expect('(');
        whitespace(true);

        var positional = new ArrayList<SassExpression>();
        var named = new LinkedHashMap<String, SassExpression>();
        var namedSpans = new LinkedHashMap<String, SourceSpan>();
        @Nullable SassExpression rest = null;
        @Nullable SassExpression keywordRest = null;
        while (lookingAtExpression()) {
            var argument = expressionUntilComma(true);
            whitespace(true);

            if (argument instanceof VariableExpression variable
                    && scanner.scan(':')) {
                whitespace(true);
                if (named.containsKey(variable.name())) {
                    throw scanner.error(
                            "Duplicate argument.",
                            variable.span().start().offset(),
                            variable.span().text().length()
                    );
                }
                var value = expressionUntilComma(true);
                named.put(variable.name(), value);
                namedSpans.put(
                        variable.name(),
                        scanner.source().span(
                                variable.span().start().offset(),
                                value.span().end().offset()
                        )
                );
            } else if (scanner.scan('.')) {
                scanner.expect('.');
                scanner.expect('.');
                if (rest == null) {
                    rest = argument;
                } else {
                    keywordRest = argument;
                    whitespace(true);
                    if (scanner.scan(',')) {
                        whitespace(true);
                    }
                    break;
                }
            } else if (!named.isEmpty()) {
                throw scanner.error(
                        "Positional arguments must come before keyword arguments.",
                        argument.span().start().offset(),
                        argument.span().text().length()
                );
            } else {
                positional.add(argument);
            }

            whitespace(true);
            if (!scanner.scan(',')) {
                break;
            }
            whitespace(true);

            if (allowEmptySecondArgument
                    && positional.size() == 1
                    && named.isEmpty()
                    && rest == null
                    && scanner.peek() == ')') {
                var empty = scanner.source().span(
                        scanner.position(),
                        scanner.position()
                );
                positional.add(StringExpression.plain("", empty));
                break;
            }
        }
        scanner.expect(')');

        return new ArgumentList(
                positional,
                named,
                namedSpans,
                rest,
                keywordRest,
                scanner.spanFrom(start)
        );
    }

    /// Parses a variable or function member after a namespace and dot.
    ///
    /// @param namespace the decoded module namespace
    /// @param start     the position before the namespace
    /// @return the namespaced expression
    private SassExpression namespacedExpression(String namespace, ScannerState start) {
        if (scanner.peek() == '$') {
            return variableExpression(namespace, start);
        }

        var name = publicIdentifier();
        return new FunctionExpression(
                namespace,
                name,
                argumentInvocation(false),
                scanner.spanFrom(start)
        );
    }

    /// Parses an identifier that may be accessed through a module namespace.
    ///
    /// @return the decoded public identifier
    /// @throws ParseException if the identifier starts with a private marker
    private String publicIdentifier() {
        var start = scanner.state();
        var result = identifier(false, false);
        if (result.startsWith("-") || result.startsWith("_")) {
            throw scanner.error(
                    "Private members can't be accessed from outside their modules.",
                    start.position(),
                    scanner.position() - start.position()
            );
        }
        return result;
    }

    /// Parses a raw declaration value that may contain Sass interpolation.
    ///
    /// The terminating top-level semicolon or closing bracket remains
    /// unconsumed. Whitespace and URL tokens are normalized, while quoted
    /// strings and loud comments retain their token spelling.
    ///
    /// @param allowEmpty     whether an empty raw value is accepted
    /// @param silentComments whether adjacent slashes begin an omitted silent
    /// comment instead of being retained as raw text
    /// @return the raw value interpolation
    /// @throws ParseException if a required token is absent or a nested token
    /// is malformed
    protected final Interpolation interpolatedDeclarationValue(
            boolean allowEmpty,
            boolean silentComments
    ) {
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
                    } else if (scanner.peek(1) == '/' && silentComments) {
                        silentComment();
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
        if (!allowEmpty && buffer.isEmpty()) {
            throw scanner.error("Expected token.");
        }
        return buffer.interpolation(scanner.spanFrom(start));
    }

    /// Attempts to parse a normalized raw URL token with interpolation.
    ///
    /// The scanner must be positioned immediately after the already-consumed
    /// function name. A grammar mismatch restores that position so the caller
    /// can parse ordinary function or declaration syntax.
    ///
    /// @param start the position before the function name
    /// @param name  the normalized name to place in the returned interpolation
    /// @return the URL interpolation, or {@code null} after restoring input
    protected final @Nullable Interpolation tryInterpolatedUrlContents(
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
