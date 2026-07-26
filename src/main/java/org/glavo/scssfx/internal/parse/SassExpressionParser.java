// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.ArgumentList;
import org.glavo.scssfx.internal.ast.BinaryOperationExpression;
import org.glavo.scssfx.internal.ast.BinaryOperator;
import org.glavo.scssfx.internal.ast.BooleanExpression;
import org.glavo.scssfx.internal.ast.ColorExpression;
import org.glavo.scssfx.internal.ast.ExpressionInterpolationPart;
import org.glavo.scssfx.internal.ast.FunctionExpression;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.InterpolationBuffer;
import org.glavo.scssfx.internal.ast.InterpolatedFunctionExpression;
import org.glavo.scssfx.internal.ast.IfConditionExpression;
import org.glavo.scssfx.internal.ast.IfExpression;
import org.glavo.scssfx.internal.ast.LegacyIfExpression;
import org.glavo.scssfx.internal.ast.ListExpression;
import org.glavo.scssfx.internal.ast.MapEntry;
import org.glavo.scssfx.internal.ast.MapExpression;
import org.glavo.scssfx.internal.ast.NullExpression;
import org.glavo.scssfx.internal.ast.NumberExpression;
import org.glavo.scssfx.internal.ast.ParenthesizedExpression;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.SelectorExpression;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.UnaryOperationExpression;
import org.glavo.scssfx.internal.ast.UnaryOperator;
import org.glavo.scssfx.internal.ast.VariableExpression;
import org.glavo.scssfx.internal.source.SourceFile;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassColor;
import org.glavo.scssfx.internal.value.SpanColorFormat;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Parses the syntax-only SassScript expression subset shared by SCSS constructs.
///
/// This parser preserves literal, operator, list, parenthesis, variable,
/// function, map, color, interpolation, and CSS unicode-range structure.
/// Evaluation is handled by later implementation stages.
@ApiStatus.Internal
@NotNullByDefault
class SassExpressionParser extends Parser {
    /// Maximum consecutive bracket prefix parsed through recursive productions.
    private static final int MAX_RECURSIVE_BRACKET_PREFIX = 128;

    /// Parse-time diagnostics retained while parsing the current source.
    private final ArrayList<Diagnostic> parseTimeWarnings = new ArrayList<>();

    /// Records whether the current expression is being parsed within parentheses.
    private boolean inParentheses;

    /// Counts nested expression productions so silent comments can remain
    /// unconsumed inside expressions (including plain CSS slash-only forms).
    private int expressionDepth;

    /// Creates a parser for expressions in an indexed source.
    ///
    /// @param source the source containing SassScript expressions
    SassExpressionParser(SourceFile source) {
        super(source);
    }

    /// Returns whether this parser is reading a plain CSS stylesheet.
    ///
    /// Plain CSS rejects Sass-only operators and keywords at parse time so
    /// identifiers such as {@code and}, {@code true}, and {@code null} remain
    /// ordinary unquoted strings.
    ///
    /// @return whether plain CSS restrictions are active
    protected boolean isPlainCssSource() {
        return false;
    }

    /// Returns whether an expression production is currently active.
    ///
    /// @return whether expression parsing is nested at least once
    protected final boolean inExpression() {
        return expressionDepth > 0;
    }

    /// Appends a parse-time diagnostic in source-processing order.
    ///
    /// @param warning the diagnostic to append
    protected final void addParseTimeWarning(Diagnostic warning) {
        parseTimeWarnings.add(Objects.requireNonNull(warning, "warning"));
    }

    /// Returns a checkpoint for later removal of speculative diagnostics.
    ///
    /// @return the current number of retained diagnostics
    protected final int parseTimeWarningCheckpoint() {
        return parseTimeWarnings.size();
    }

    /// Removes diagnostics appended after a checkpoint.
    ///
    /// @param checkpoint a value returned by [#parseTimeWarningCheckpoint()]
    /// @throws IllegalArgumentException if the checkpoint is outside the
    /// current diagnostic range
    protected final void restoreParseTimeWarnings(int checkpoint) {
        if (checkpoint < 0 || checkpoint > parseTimeWarnings.size()) {
            throw new IllegalArgumentException("invalid parse-time warning checkpoint");
        }
        parseTimeWarnings.subList(checkpoint, parseTimeWarnings.size()).clear();
    }

    /// Returns an immutable snapshot of diagnostics retained so far.
    ///
    /// @return the diagnostics in source-processing order
    protected final @Unmodifiable List<Diagnostic> parseTimeWarnings() {
        return List.copyOf(parseTimeWarnings);
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
        return expression(null);
    }

    /// Parses an expression that stops when {@code until} reports a terminator.
    ///
    /// The terminator is checked after whitespace and before binary operators or
    /// additional space-list elements, matching dart-sass {@code _expression}.
    ///
    /// @param until a predicate that returns {@code true} without consuming the
    /// terminator, or {@code null} when no terminator is used
    /// @return the parsed expression
    /// @throws ParseException if no expression begins here or it is malformed
    protected final SassExpression expression(
            @Nullable java.util.function.BooleanSupplier until
    ) {
        if (until != null && until.getAsBoolean()) {
            throw scanner.error("Expected expression.");
        }
        if (!lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }
        expressionDepth++;
        try {
            return commaExpression(until);
        } finally {
            expressionDepth--;
        }
    }

    /// Parses an expression until a top-level media-range comparison operator.
    ///
    /// Stops before {@code <}, {@code >}, or a lone {@code =} that is not part
    /// of {@code ==}, so media feature range syntax can claim those operators.
    ///
    /// @return the parsed expression
    /// @throws ParseException if no expression begins here or it is malformed
    protected final SassExpression expressionUntilComparison() {
        return expression(() -> {
            var next = scanner.peek();
            if (next == '=') {
                return scanner.peek(1) != '=';
            }
            return next == '<' || next == '>';
        });
    }

    /// Parses a comma-separated expression or a single lower-level expression.
    ///
    /// @param until a terminator predicate, or {@code null}
    /// @return the parsed expression
    private SassExpression commaExpression(
            @Nullable java.util.function.BooleanSupplier until
    ) {
        var first = spaceExpression(false, until);
        if (scanner.peek() != ',') {
            return first;
        }

        var contents = new ArrayList<SassExpression>();
        contents.add(first);
        while (scanner.scan(',')) {
            whitespace(true);
            if (until != null && until.getAsBoolean()) {
                break;
            }
            if (lookingAtExpression()) {
                contents.add(spaceExpression(false, until));
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
                scanner.source().span(
                        scanner.source().generatedStartOffset(first.span()),
                        scanner.position()
                )
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
        return spaceExpression(singleEquals, null);
    }

    /// Parses an expression that stops before a top-level comma.
    ///
    /// @return the parsed expression
    protected final SassExpression expressionUntilComma() {
        return expressionUntilComma(false);
    }

    /// Parses expressions separated implicitly as one space list.
    ///
    /// Sass permits some adjacent expression forms without a literal whitespace
    /// code unit; these still use the space-list separator in the syntax tree.
    ///
    /// @param singleEquals whether a lone equals sign is accepted
    /// @param until        a terminator predicate, or {@code null}
    /// @return the parsed expression or space list
    private SassExpression spaceExpression(
            boolean singleEquals,
            @Nullable java.util.function.BooleanSupplier until
    ) {
        var first = slashAwareBinaryExpression(singleEquals, until);
        @Nullable ArrayList<SassExpression> contents = null;

        while (true) {
            whitespace(true);
            if (until != null && until.getAsBoolean()) {
                break;
            }
            if (!lookingAtExpression()) {
                break;
            }
            if (contents == null) {
                contents = new ArrayList<>();
                contents.add(first);
            }
            contents.add(slashAwareBinaryExpression(singleEquals, until));
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
                        scanner.source().generatedStartOffset(first.span()),
                        scanner.source().generatedEndOffset(last.span())
                )
        );
    }

    /// Parses one precedence-ordered binary expression and applies slash metadata.
    ///
    /// @param singleEquals whether a lone equals sign is accepted
    /// @param until        a terminator predicate, or {@code null}
    /// @return the parsed expression
    private SassExpression slashAwareBinaryExpression(
            boolean singleEquals,
            @Nullable java.util.function.BooleanSupplier until
    ) {
        var result = binaryExpression(0, singleEquals, until);
        return !inParentheses && isSlashTree(result)
                ? markSlashTree(result)
                : result;
    }

    /// Parses binary operators whose precedence is at least the requested value.
    ///
    /// @param minimumPrecedence the lowest accepted binary precedence
    /// @param singleEquals      whether the Microsoft-style single-equals operator
    /// may occur at this expression level
    /// @param until             a terminator checked before each operator, or {@code null}
    /// @return the parsed expression
    private SassExpression binaryExpression(
            int minimumPrecedence,
            boolean singleEquals,
            @Nullable java.util.function.BooleanSupplier until
    ) {
        var left = singleExpression();

        while (true) {
            var beforeTrivia = scanner.state();
            whitespace(true);
            // Stop before binary operators when {@code until} reports a
            // terminator. The predicate may consume tokens (for example
            // {@code @for … through}); restore so only spaceExpression's check
            // keeps a consuming match, matching dart-sass's single-loop break.
            if (until != null) {
                var beforeUntil = scanner.state();
                if (until.getAsBoolean()) {
                    scanner.restore(beforeUntil);
                    return left;
                }
            }
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
            if (isPlainCssSource()
                    && operator != BinaryOperator.SINGLE_EQUALS
                    && operator != BinaryOperator.PLUS
                    && operator != BinaryOperator.MINUS
                    && operator != BinaryOperator.TIMES
                    && operator != BinaryOperator.DIVIDED_BY) {
                throw scanner.error(
                        "Operators aren't allowed in plain CSS.",
                        operatorStart.position(),
                        scanner.position() - operatorStart.position()
                );
            }

            var operatorEnd = scanner.state();
            whitespace(true);
            if (!lookingAtExpression()) {
                // Only trailing modulo may fall back so {@code c %} becomes a
                // space list of {@code c} and a percent string. Other incomplete
                // binaries keep the historical {@code Expected expression.} error
                // (including calc trailing-operator diagnostics).
                if (operator == BinaryOperator.MODULO) {
                    scanner.restore(beforeTrivia);
                    return left;
                }
                throw scanner.error(
                        "Expected expression.",
                        operatorStart.position(),
                        operatorEnd.position() - operatorStart.position()
                );
            }

            var right = binaryExpression(operator.precedence() + 1, singleEquals, until);
            var operation = new BinaryOperationExpression(
                    operator,
                    left,
                    right,
                    false,
                    scanner.spanFrom(operatorStart, operatorEnd),
                    scanner.source().span(
                            scanner.source().generatedStartOffset(left.span()),
                            scanner.source().generatedEndOffset(right.span())
                    )
            );
            warnForStrictUnary(operation);
            left = operation;
        }
    }

    /// Records the deprecation for a binary plus or minus that is lexically
    /// indistinguishable from a whitespace-separated unary operand.
    ///
    /// @param operation the newly parsed binary operation
    private void warnForStrictUnary(BinaryOperationExpression operation) {
        var operator = operation.operator();
        if (operator != BinaryOperator.PLUS && operator != BinaryOperator.MINUS) {
            return;
        }

        var content = scanner.source().content();
        var leftEnd = scanner.source().generatedEndOffset(operation.left().span());
        var rightStart = scanner.source().generatedStartOffset(
                operation.right().span()
        );
        if (leftEnd >= content.length()
                || rightStart <= 0
                || content.charAt(rightStart - 1) != operator.source().charAt(0)
                || !CssCharacters.isWhitespace(content.charAt(leftEnd))) {
            return;
        }

        addParseTimeWarning(new Diagnostic(
                DiagnosticSeverity.DEPRECATION,
                strictUnaryMessage(operation),
                operation.span(),
                "strict-unary"
        ));
    }

    /// Creates the Dart Sass strict-unary migration guidance for an operation.
    ///
    /// @param operation the ambiguous binary operation
    /// @return the complete deprecation message
    private static String strictUnaryMessage(BinaryOperationExpression operation) {
        var operator = operation.operator().source();
        return "This operation is parsed as:\n"
                + "\n"
                + "    " + operation.left() + " " + operator + " " + operation.right() + "\n"
                + "\n"
                + "but you may have intended it to mean:\n"
                + "\n"
                + "    " + operation.left() + " (" + operator + operation.right() + ")\n"
                + "\n"
                + "Add a space after " + operator + " to clarify that it's meant to be a binary "
                + "operation, or wrap\n"
                + "it in parentheses to make it a unary operation. This will be an error in future\n"
                + "versions of Sass.\n"
                + "\n"
                + "More info and automated migrator: https://sass-lang.com/d/strict-unary";
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
                // Match dart-sass: `-#{...}` is an interpolated identifier (e.g.
                // space list `0 -#{0.12em}` → `0 -0.12em`), not binary minus.
                // Plain `lookingAtIdentifier()` misses the dash+hash form.
                if (lookingAtInterpolatedIdentifier()
                        || precededByWhitespace && (CssCharacters.isDigit(scanner.peek(1))
                        || scanner.peek(1) == '.')) {
                    yield null;
                }
                yield scanOneCodeUnitOperator(BinaryOperator.MINUS);
            }
            case '/' -> scanOneCodeUnitOperator(BinaryOperator.DIVIDED_BY);
            case '%' -> scanOneCodeUnitOperator(BinaryOperator.MODULO);
            // Plain CSS keeps "and"/"or" as ordinary identifiers so space lists
            // such as {@code true and false} serialize without Sass evaluation.
            case 'a' -> !isPlainCssSource() && scanIdentifier("and")
                    ? BinaryOperator.AND
                    : null;
            case 'o' -> !isPlainCssSource() && scanIdentifier("or")
                    ? BinaryOperator.OR
                    : null;
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
    /// numbers, plain function calls, or (in plain CSS) unquoted identifiers.
    ///
    /// Outside plain CSS, unquoted identifiers such as {@code none} are
    /// intentionally excluded so they become slash-joined strings via the
    /// default division fallback, which color channel parsers re-split (for
    /// example {@code 50%/none}). In plain CSS, chains such as
    /// {@code 1/2/foo/bar} must retain slash presentation rather than evaluating
    /// the leading numeric pair as division.
    ///
    /// @param expression the expression to inspect
    /// @return whether the tree may represent a slash-separated value
    private boolean isSlashTree(SassExpression expression) {
        if (expression instanceof NumberExpression
                || expression instanceof FunctionExpression) {
            return true;
        }
        if (isPlainCssSource()
                && expression instanceof StringExpression string
                && !string.hasQuotes()) {
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
            return unicodeRangeExpression();
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
            case '&' -> {
                if (isPlainCssSource()) {
                    throw scanner.error("The parent selector isn't allowed in plain CSS.");
                }
                var start = scanner.state();
                scanner.expect('&');
                // dart-sass warns when {@code &&} is written; keep the second
                // ampersand unconsumed so it can begin another expression.
                if (scanner.peek() == '&') {
                    addParseTimeWarning(new Diagnostic(
                            DiagnosticSeverity.WARNING,
                            "In Sass, \"&&\" means two copies of the parent selector. You "
                                    + "probably want to use \"and\" instead.",
                            scanner.spanFrom(start),
                            null
                    ));
                }
                yield new SelectorExpression(scanner.spanFrom(start));
            }
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
                // Plain CSS rejects empty {@code ()} at parse time with
                // "Expected expression." rather than later CSS-value validation.
                if (isPlainCssSource()) {
                    throw scanner.error("Expected expression.");
                }
                scanner.expect(')');
                return new ListExpression(
                        List.of(),
                        ListSeparator.UNDECIDED,
                        false,
                        scanner.spanFrom(start)
                );
            }

            var warningCheckpoint = parseTimeWarningCheckpoint();
            var first = expressionUntilCommaWithSlashReparse();
            if (scanner.scan(':')) {
                // Plain CSS has no Sass map literals; a colon after the first
                // parenthesized token is a syntax error (dart-sass expected ")").
                if (isPlainCssSource()) {
                    throw scanner.error("expected \")\".");
                }
                whitespace(true);
                return mapExpression(first, start);
            }

            if (!scanner.scan(',')) {
                scanner.expect(')');
                return new ParenthesizedExpression(first, scanner.spanFrom(start));
            }

            // Once parentheses establish list context, slash operations use
            // the same historical metadata as expressions outside this pair.
            restoreParseTimeWarnings(warningCheckpoint);
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
        var warningCheckpoint = parseTimeWarningCheckpoint();
        var expression = expressionUntilComma(false);
        if (inParentheses
                && expression instanceof ListExpression list
                && !list.hasBrackets()
                && list.separator() == ListSeparator.SPACE) {
            restoreParseTimeWarnings(warningCheckpoint);
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
        rejectUnsafeDeepBracketPrefix();
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

        var expression = commaExpression(null);
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

    /// Rejects a dangerous consecutive bracket prefix without recursive descent.
    ///
    /// Invalid prefixes are scanned through their innermost token so ordinary
    /// expression diagnostics retain the same message and location. A
    /// syntactically plausible prefix beyond the recursion budget receives an
    /// explicit parser error rather than exhausting the JVM stack.
    private void rejectUnsafeDeepBracketPrefix() {
        var start = scanner.state();
        var count = 0;
        while (scanner.peek() == '[') {
            scanner.expect('[');
            whitespace(true);
            count++;
        }
        if (count <= MAX_RECURSIVE_BRACKET_PREFIX) {
            scanner.restore(start);
            return;
        }
        if (scanner.peek() != ']' && !lookingAtExpression()) {
            throw scanner.error("Expected expression.");
        }
        scanner.restore(start);
        throw scanner.error("Expression nesting depth exceeds the supported limit.");
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
        // Plain CSS permits unary slash (for forms such as {@code 1///bar})
        // but rejects other unary operators at parse time.
        if (isPlainCssSource() && operator != UnaryOperator.DIVIDE) {
            throw scanner.error(
                    "Operators aren't allowed in plain CSS.",
                    start.position(),
                    1
            );
        }
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
        if (namespace != null && (name.startsWith("-") || name.startsWith("_"))) {
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
            if (plain.equals("if") && scanner.peek() == '(') {
                // Legacy and modern if() share the leading "if(", so try the
                // comma-separated Sass form first and fall back to CSS syntax.
                var beforeParen = scanner.state();
                var warningCheckpoint = parseTimeWarningCheckpoint();
                try {
                    return new LegacyIfExpression(
                            argumentInvocation(false),
                            scanner.spanFrom(start)
                    );
                } catch (ParseException ignored) {
                    scanner.restore(beforeParen);
                    restoreParseTimeWarnings(warningCheckpoint);
                    return ifExpression(start);
                }
            }
            if (plain.equalsIgnoreCase("if") && scanner.peek() == '(') {
                return ifExpression(start);
            }
            // In plain CSS, not/true/false/null are ordinary identifiers so
            // declarations like {@code x: null} and {@code not: not true}
            // preserve source text rather than Sass keyword semantics.
            if (!isPlainCssSource() && plain.equals("not")) {
                whitespace(true);
                var operand = singleExpression();
                return new UnaryOperationExpression(
                        UnaryOperator.NOT,
                        operand,
                        scanner.source().span(
                                start.position(),
                                scanner.source().generatedEndOffset(operand.span())
                        )
                );
            }

            lower = plain.toLowerCase(Locale.ROOT);
            if (!isPlainCssSource() && scanner.peek() != '(') {
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
                    identifier.span()
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

        // Special-function residual folds silent-comment newlines into a single
        // space ({@code element(//\n  c)} → {@code element( c)}), matching
        // dart-sass property emission for these raw-string forms.
        var residual = foldSpecialFunctionSilentNewlines(
                interpolatedDeclarationValue(true, true)
        );
        buffer.add(residual);
        scanner.expect(')');
        buffer.append(')');
        return new StringExpression(
                buffer.interpolation(scanner.spanFrom(start)),
                false
        );
    }

    /// Folds line breaks left by omitted silent comments in special-function args.
    ///
    /// @param residual the raw argument interpolation
    /// @return an interpolation with newline-led indents collapsed to one space
    private static Interpolation foldSpecialFunctionSilentNewlines(
            Interpolation residual
    ) {
        @Nullable String plain = residual.asPlain();
        if (plain == null || plain.indexOf('\n') < 0 && plain.indexOf('\r') < 0) {
            return residual;
        }
        var folded = new StringBuilder(plain.length());
        for (var index = 0; index < plain.length(); index++) {
            var character = plain.charAt(index);
            if (character == '\n' || character == '\r') {
                if (character == '\r'
                        && index + 1 < plain.length()
                        && plain.charAt(index + 1) == '\n') {
                    index++;
                }
                while (index + 1 < plain.length()) {
                    var next = plain.charAt(index + 1);
                    if (next == ' ' || next == '\t') {
                        index++;
                        continue;
                    }
                    break;
                }
                folded.append(' ');
                continue;
            }
            folded.append(character);
        }
        return Interpolation.plain(folded.toString(), residual.span());
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
    protected final ArgumentList argumentInvocation(boolean allowEmptySecondArgument) {
        var start = scanner.state();
        scanner.expect('(');
        whitespace(true);

        // Function-call parentheses are not "grouping parentheses" for slash
        // division: arguments must keep allowsSlash metadata so modern color
        // channel lists like {@code hsl(180 60% 50% / 0.4)} work even when the
        // call itself is nested inside outer parentheses.
        var previousInParentheses = inParentheses;
        inParentheses = false;
        try {
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
                                variable.span()
                        );
                    }
                    var value = expressionUntilComma(true);
                    named.put(variable.name(), value);
                    namedSpans.put(
                            variable.name(),
                            scanner.source().span(
                                    scanner.source().generatedStartOffset(variable.span()),
                                    scanner.source().generatedEndOffset(value.span())
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
                            argument.span()
                    );
                } else {
                    positional.add(argument);
                }

                whitespace(true);
                if (!scanner.scan(',')) {
                    break;
                }
                whitespace(true);

                // Plain CSS treats empty middle/invalid tokens after a comma as
                // "Expected expression." (not as an empty fallback). SCSS keeps
                // the historical expected ")" diagnostic for the same inputs.
                if (isPlainCssSource()
                        && allowEmptySecondArgument
                        && positional.size() == 1
                        && named.isEmpty()
                        && rest == null
                        && scanner.peek() != ')'
                        && !lookingAtExpression()) {
                    throw scanner.error("Expected expression.");
                }

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
        } finally {
            inParentheses = previousInParentheses;
        }
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
        return interpolatedDeclarationValue(allowEmpty, silentComments, false, null);
    }

    /// Parses a raw declaration value, optionally retaining top-level semicolons.
    ///
    /// @param allowEmpty whether an empty raw value is accepted
    /// @param silentComments whether silent comments are omitted
    /// @param allowSemicolon whether a top-level {@code ;} is part of the value
    /// @return the raw value interpolation
    protected final Interpolation interpolatedDeclarationValue(
            boolean allowEmpty,
            boolean silentComments,
            boolean allowSemicolon
    ) {
        return interpolatedDeclarationValue(allowEmpty, silentComments, allowSemicolon, null);
    }

    /// Parses a raw declaration value with an optional top-level terminator.
    ///
    /// The terminator is tested before each top-level token and must not consume
    /// input. Nested brackets always take precedence over the terminator.
    ///
    /// @param allowEmpty whether an empty raw value is accepted
    /// @param silentComments whether silent comments are omitted
    /// @param atEnd the optional top-level terminator predicate
    /// @return the raw value interpolation
    /// @throws ParseException if a required token is absent or nested syntax is malformed
    protected final Interpolation interpolatedDeclarationValue(
            boolean allowEmpty,
            boolean silentComments,
            @Nullable java.util.function.BooleanSupplier atEnd
    ) {
        return interpolatedDeclarationValue(allowEmpty, silentComments, false, atEnd);
    }

    /// Parses a raw declaration value with optional semicolon retention and terminator.
    ///
    /// @param allowEmpty whether an empty raw value is accepted
    /// @param silentComments whether silent comments are omitted
    /// @param allowSemicolon whether a top-level {@code ;} is part of the value
    /// @param atEnd the optional top-level terminator predicate
    /// @return the raw value interpolation
    /// @throws ParseException if a required token is absent or nested syntax is malformed
    protected final Interpolation interpolatedDeclarationValue(
            boolean allowEmpty,
            boolean silentComments,
            boolean allowSemicolon,
            @Nullable java.util.function.BooleanSupplier atEnd
    ) {
        var start = scanner.state();
        var buffer = new InterpolationBuffer();
        var brackets = new ArrayDeque<Integer>();
        var wroteNewline = false;

        value:
        while (true) {
            if (brackets.isEmpty() && atEnd != null && atEnd.getAsBoolean()) {
                break;
            }
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
                        wroteNewline = false;
                    } else if (scanner.peek(1) == '/' && silentComments) {
                        // Match dart-sass: omit the silent comment without
                        // inserting a replacement space. The trailing newline
                        // (if any) remains for the normal newline branch so
                        // residual text such as {@code @supports a(//\n  b)}
                        // keeps the line break and indentation.
                        // Special functions later fold that residual (see
                        // {@link #trySpecialFunction}).
                        silentComment();
                        wroteNewline = false;
                    } else {
                        buffer.append((char) scanner.read());
                        wroteNewline = false;
                    }
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
                    if (!allowSemicolon && brackets.isEmpty()) {
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

    /// Parses a modern CSS-style {@code if()} expression after the name.
    ///
    /// @param start the position before {@code if}
    /// @return the CSS if expression
    private IfExpression ifExpression(ScannerState start) {
        scanner.expect('(');
        whitespace(true);
        var branches = new ArrayList<IfExpression.Branch>();
        while (scanner.peek() != ')') {
            @Nullable IfConditionExpression condition = scanIdentifier("else")
                    ? null
                    : ifConditionExpression();
            whitespace(true);
            scanner.expect(':');
            whitespace(true);
            var value = expression();
            branches.add(new IfExpression.Branch(condition, value));
            whitespace(true);
            if (!scanner.scan(';')) {
                break;
            }
            whitespace(true);
        }
        scanner.expect(')');
        if (branches.isEmpty()) {
            throw scanner.error("Expected if() branch.");
        }
        return new IfExpression(branches, scanner.spanFrom(start));
    }

    /// Parses one CSS {@code if()} condition.
    private IfConditionExpression ifConditionExpression() {
        var start = scanner.state();
        if (scanIdentifier("not")) {
            if (scanner.peek() == '(') {
                throw scanner.error("Whitespace is required between \"not\" and \"(\"");
            }
            whitespace(true);
            return new IfConditionExpression.Negation(ifGroup(), scanner.spanFrom(start));
        }

        var groups = new ArrayList<IfConditionExpression>();
        groups.add(ifGroup());
        @Nullable IfConditionExpression.BooleanOperator operator = null;
        whitespace(true);
        while (true) {
            if (operator != IfConditionExpression.BooleanOperator.OR
                    && scanIdentifier("and")) {
                if (scanner.peek() == '(') {
                    throw scanner.error("Whitespace is required between \"and\" and \"(\"");
                }
                whitespace(true);
                if (operator == null) {
                    operator = IfConditionExpression.BooleanOperator.AND;
                }
                groups.add(ifGroup());
            } else if (operator != IfConditionExpression.BooleanOperator.AND
                    && scanIdentifier("or")) {
                // dart-sass uses the "and" wording for this branch as well.
                if (scanner.peek() == '(') {
                    throw scanner.error("Whitespace is required between \"and\" and \"(\"");
                }
                whitespace(true);
                if (operator == null) {
                    operator = IfConditionExpression.BooleanOperator.OR;
                }
                groups.add(ifGroup());
            } else {
                int next = scanner.peek();
                IfConditionExpression last = groups.get(groups.size() - 1);
                if (next != ')' && next != ':' && last.isArbitrarySubstitution()) {
                    return ifConditionRaw(combineIfGroups(groups, operator), ifGroup());
                }
                @Nullable IfConditionExpression substitution = tryArbitrarySubstitution();
                if (substitution != null) {
                    return ifConditionRaw(combineIfGroups(groups, operator), substitution);
                }
                break;
            }
            whitespace(true);
        }
        return combineIfGroups(groups, operator);
    }

    /// Combines one or more groups into a single condition.
    private IfConditionExpression combineIfGroups(
            List<IfConditionExpression> groups,
            @Nullable IfConditionExpression.BooleanOperator operator
    ) {
        if (groups.size() == 1) {
            return groups.get(0);
        }
        var first = groups.get(0);
        var last = groups.get(groups.size() - 1);
        var span = scanner.source().span(
                scanner.source().generatedStartOffset(first.span()),
                scanner.source().generatedEndOffset(last.span())
        );
        return new IfConditionExpression.Operation(
                groups,
                Objects.requireNonNull(operator, "operator"),
                span
        );
    }

    /// Continues a condition as raw text once an arbitrary substitution appears.
    private IfConditionExpression.Raw ifConditionRaw(
            IfConditionExpression preceding,
            IfConditionExpression next
    ) {
        var buffer = new InterpolationBuffer();
        buffer.add(conditionToInterpolation(preceding));
        buffer.append(' ');
        buffer.add(conditionToInterpolation(next));

        var lastGroup = next;
        @Nullable IfConditionExpression.BooleanOperator operator =
                preceding instanceof IfConditionExpression.Operation operation
                        ? operation.operator()
                        : null;
        whitespace(true);
        while (true) {
            if (operator != IfConditionExpression.BooleanOperator.OR
                    && scanIdentifier("and")) {
                if (scanner.peek() == '(') {
                    throw scanner.error("Whitespace is required between \"and\" and \"(\"");
                }
                whitespace(true);
                if (operator == null) {
                    operator = IfConditionExpression.BooleanOperator.AND;
                }
                lastGroup = ifGroup();
                buffer.append(" and ");
                buffer.add(conditionToInterpolation(lastGroup));
            } else if (operator != IfConditionExpression.BooleanOperator.AND
                    && scanIdentifier("or")) {
                if (scanner.peek() == '(') {
                    throw scanner.error("Whitespace is required between \"or\" and \"(\"");
                }
                whitespace(true);
                if (operator == null) {
                    operator = IfConditionExpression.BooleanOperator.OR;
                }
                lastGroup = ifGroup();
                buffer.append(" or ");
                buffer.add(conditionToInterpolation(lastGroup));
            } else {
                int nextChar = scanner.peek();
                if (nextChar != ')' && nextChar != ':' && lastGroup.isArbitrarySubstitution()) {
                    lastGroup = ifGroup();
                    buffer.append(' ');
                    buffer.add(conditionToInterpolation(lastGroup));
                } else {
                    @Nullable IfConditionExpression substitution = tryArbitrarySubstitution();
                    if (substitution == null) {
                        break;
                    }
                    lastGroup = substitution;
                    buffer.append(' ');
                    buffer.add(conditionToInterpolation(lastGroup));
                }
            }
            whitespace(true);
        }
        var span = scanner.source().span(
                scanner.source().generatedStartOffset(preceding.span()),
                scanner.position()
        );
        return new IfConditionExpression.Raw(buffer.interpolation(span));
    }

    /// Serializes one condition into an interpolation for raw fallback text.
    private Interpolation conditionToInterpolation(IfConditionExpression condition) {
        if (condition instanceof IfConditionExpression.Sass sass) {
            throw scanner.error(
                    "if() conditions with arbitrary substitutions may not contain sass() "
                            + "expressions.",
                    sass.span()
            );
        }
        if (condition instanceof IfConditionExpression.Raw raw) {
            return raw.text();
        }
        if (condition instanceof IfConditionExpression.Function function) {
            var buffer = new InterpolationBuffer();
            buffer.add(function.name());
            buffer.append('(');
            buffer.add(function.arguments());
            buffer.append(')');
            return buffer.interpolation(function.span());
        }
        if (condition instanceof IfConditionExpression.Parenthesized parenthesized) {
            var buffer = new InterpolationBuffer();
            buffer.append('(');
            buffer.add(conditionToInterpolation(parenthesized.expression()));
            buffer.append(')');
            return buffer.interpolation(parenthesized.span());
        }
        if (condition instanceof IfConditionExpression.Negation negation) {
            var buffer = new InterpolationBuffer();
            buffer.append("not ");
            buffer.add(conditionToInterpolation(negation.expression()));
            return buffer.interpolation(negation.span());
        }
        if (condition instanceof IfConditionExpression.Operation operation) {
            var buffer = new InterpolationBuffer();
            for (var index = 0; index < operation.expressions().size(); index++) {
                if (index > 0) {
                    buffer.append(' ');
                    buffer.append(operation.operator().cssName());
                    buffer.append(' ');
                }
                buffer.add(conditionToInterpolation(operation.expressions().get(index)));
            }
            return buffer.interpolation(operation.span());
        }
        throw new AssertionError("unknown if condition: " + condition);
    }

    /// Parses one CSS {@code if()} condition group.
    private IfConditionExpression ifGroup() {
        var start = scanner.state();
        if (scanner.scan('(')) {
            whitespace(true);
            var expression = ifConditionExpression();
            whitespace(true);
            scanner.expect(')');
            return new IfConditionExpression.Parenthesized(
                    expression,
                    scanner.spanFrom(start)
            );
        }
        if (scanIdentifier("sass", true)) {
            scanner.expect('(');
            whitespace(true);
            var expression = expression();
            whitespace(true);
            scanner.expect(')');
            return new IfConditionExpression.Sass(expression, scanner.spanFrom(start));
        }
        var identifier = interpolatedIdentifier();
        @Nullable String plain = identifier.asPlain();
        // Pure #{…} interpolations may stand alone as a condition group.
        if (plain == null
                && identifier.parts().size() == 1
                && identifier.parts().get(0) instanceof ExpressionInterpolationPart
                && scanner.peek() != '(') {
            return new IfConditionExpression.Raw(identifier);
        }
        if (plain != null
                && ("and".equalsIgnoreCase(plain)
                || "or".equalsIgnoreCase(plain)
                || "not".equalsIgnoreCase(plain))
                && scanner.peek() == '(') {
            throw scanner.error(
                    "Whitespace is required between \"" + plain + "\" and \"(\""
            );
        }
        // Plain identifiers must begin a function group: bare {@code not}/{@code else}
        // after another operator is rejected with expected "(".
        scanner.expect('(');
        whitespace(true);
        var arguments = interpolatedDeclarationValue(true, true, true);
        whitespace(true);
        scanner.expect(')');
        return new IfConditionExpression.Function(
                identifier,
                arguments,
                scanner.spanFrom(start)
        );
    }

    /// Attempts to parse an arbitrary substitution such as {@code var(...)} or
    /// {@code #{...}}.
    private @Nullable IfConditionExpression tryArbitrarySubstitution() {
        if (scanner.peek() == '#' && scanner.peek(1) == '{') {
            var interpolationStart = scanner.state();
            var buffer = new InterpolationBuffer();
            singleInterpolation(buffer);
            return new IfConditionExpression.Raw(
                    buffer.interpolation(scanner.spanFrom(interpolationStart))
            );
        }
        var start = scanner.state();
        @Nullable String name = null;
        if (scanIdentifier("if")) {
            name = "if";
        } else if (scanIdentifier("var")) {
            name = "var";
        } else if (scanIdentifier("attr")) {
            name = "attr";
        } else if (lookingAtIdentifier() && scanner.peek() == '-' && scanner.peek(1) == '-') {
            var ident = interpolatedIdentifier();
            @Nullable String plain = ident.asPlain();
            if (plain != null && plain.startsWith("--") && scanner.scan('(')) {
                whitespace(true);
                var arguments = interpolatedDeclarationValue(true, true);
                whitespace(true);
                scanner.expect(')');
                return new IfConditionExpression.Function(
                        ident,
                        arguments,
                        scanner.spanFrom(start)
                );
            }
            scanner.restore(start);
            return null;
        }
        if (name == null || !scanner.scan('(')) {
            scanner.restore(start);
            return null;
        }
        whitespace(true);
        var arguments = interpolatedDeclarationValue(true, true, true);
        whitespace(true);
        scanner.expect(')');
        return new IfConditionExpression.Function(
                Interpolation.plain(name, scanner.spanFrom(start)),
                arguments,
                scanner.spanFrom(start)
        );
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

        // Dart Sass retains diagnostics from the failed raw-URL attempt, so
        // ordinary-function fallback may report the same source again.
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

    /// Consumes a CSS unicode-range token as an unquoted string expression.
    ///
    /// Matches dart-sass: {@code U+} or {@code u+} followed by hex digits and optional
    /// {@code ?} wildcards or a hyphenated second hex range. The original source text
    /// (including letter case) is preserved in the string value.
    ///
    /// @return an unquoted plain string expression covering the full range token
    /// @throws ParseException if the range is incomplete or exceeds six digits
    private StringExpression unicodeRangeExpression() {
        var start = scanner.state();
        if (!scanIdentChar('u', false)) {
            throw scanner.error("Expected \"u\".");
        }
        scanner.expect('+');

        var firstRangeLength = 0;
        while (scanCharIf(CssCharacters::isHex)) {
            firstRangeLength++;
        }
        var hasQuestionMark = false;
        while (scanner.scan('?')) {
            hasQuestionMark = true;
            firstRangeLength++;
        }

        if (firstRangeLength == 0) {
            throw scanner.error("Expected hex digit or \"?\".");
        }
        if (firstRangeLength > 6) {
            throw scanner.error(
                    "Expected at most 6 digits.",
                    scanner.spanFrom(start)
            );
        }
        if (hasQuestionMark) {
            return StringExpression.plain(
                    scanner.substring(start.position()),
                    scanner.spanFrom(start)
            );
        }

        if (scanner.scan('-')) {
            var secondRangeStart = scanner.state();
            var secondRangeLength = 0;
            while (scanCharIf(CssCharacters::isHex)) {
                secondRangeLength++;
            }
            if (secondRangeLength == 0) {
                throw scanner.error("Expected hex digit.");
            }
            if (secondRangeLength > 6) {
                throw scanner.error(
                        "Expected at most 6 digits.",
                        scanner.spanFrom(secondRangeStart)
                );
            }
        }

        if (lookingAtInterpolatedIdentifierBody()) {
            throw scanner.error("Expected end of identifier.");
        }
        return StringExpression.plain(
                scanner.substring(start.position()),
                scanner.spanFrom(start)
        );
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

    /// Returns whether an identifier body, including interpolation, begins here.
    ///
    /// @return whether more identifier or interpolation text begins at the scanner
    private boolean lookingAtInterpolatedIdentifierBody() {
        var next = scanner.peek();
        return next != CssCharacters.END_OF_INPUT
                && (CssCharacters.isName(next) || next == '\\' || next == '#');
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
