// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.DiagnosticSeverity;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.ArgumentList;
import org.glavo.scssfx.internal.ast.ConfiguredVariable;
import org.glavo.scssfx.internal.ast.ContentBlock;
import org.glavo.scssfx.internal.ast.ContentRule;
import org.glavo.scssfx.internal.ast.Declaration;
import org.glavo.scssfx.internal.ast.EachRule;
import org.glavo.scssfx.internal.ast.ElseClause;
import org.glavo.scssfx.internal.ast.ForwardRule;
import org.glavo.scssfx.internal.ast.ForRule;
import org.glavo.scssfx.internal.ast.FunctionRule;
import org.glavo.scssfx.internal.ast.IfClause;
import org.glavo.scssfx.internal.ast.IfRule;
import org.glavo.scssfx.internal.ast.IncludeRule;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.InterpolationBuffer;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.MixinRule;
import org.glavo.scssfx.internal.ast.Parameter;
import org.glavo.scssfx.internal.ast.ParameterList;
import org.glavo.scssfx.internal.ast.ReturnRule;
import org.glavo.scssfx.internal.ast.UseRule;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.ast.VariableDeclaration;
import org.glavo.scssfx.internal.ast.WhileRule;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// Parses SCSS stylesheets containing declarations, nested properties, style rules,
/// control-flow at-rules, module directives, mixins, functions, and comments.
@NotNullByDefault
final class ScssParser extends SassExpressionParser {
    /// Identifies the statement forms allowed inside a braced block.
    private enum StatementContext {
        /// Top-level statements and top-level control-flow bodies.
        ROOT,

        /// Style-rule children, including nested rules and declarations.
        STYLE_RULE,

        /// Nested-property children that reject nested style rules.
        DECLARATION,

        /// Function bodies that accept only variables, control flow, and `@return`.
        FUNCTION
    }

    /// First global declaration span for each normalized variable name.
    private final LinkedHashMap<String, SourceSpan> globalVariables = new LinkedHashMap<>();

    /// The most recently parsed silent comment, or {@code null} when none exists.
    private @Nullable SilentComment lastSilentComment;

    /// Records whether the parser is inside a mixin declaration body.
    private boolean inMixin;

    /// Records whether module directives are still allowed at the stylesheet root.
    private boolean moduleDirectivesAllowed = true;

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
        return new Stylesheet(
                children,
                scanner.spanFrom(start),
                false,
                parseTimeWarnings(),
                globalVariables
        );
    }

    /// Parses top-level variable declarations, style rules, comments, and empty statements.
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
                case '$' -> {
                    statements.add(variableDeclarationWithoutNamespace());
                }
                case '@' -> {
                    var rule = atRule(StatementContext.ROOT, true);
                    if (!(rule instanceof UseRule) && !(rule instanceof ForwardRule)) {
                        moduleDirectivesAllowed = false;
                    }
                    statements.add(rule);
                }
                default -> {
                    var statement = variableDeclarationOrStyleRule();
                    if (!(statement instanceof VariableDeclaration)) {
                        moduleDirectivesAllowed = false;
                    }
                    statements.add(statement);
                }
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

    /// Parses a namespaced variable declaration when one begins here, or a style rule otherwise.
    ///
    /// @return the parsed variable declaration or style rule
    private SassStatement variableDeclarationOrStyleRule() {
        var variable = tryNamespacedVariableDeclaration();
        return variable == null ? styleRule() : variable;
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
        var children = statementBlock(StatementContext.STYLE_RULE);
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

    /// Parses a braced statement block for the given context.
    ///
    /// Comments are retained as statements and empty semicolon statements are
    /// discarded. Control-flow at-rules are accepted in every context; unknown
    /// at-rules remain structured failures.
    ///
    /// @param context the statement forms permitted in the block
    /// @return the child statements in source order
    /// @throws ParseException if the block is unterminated or a child is malformed
    private ArrayList<SassStatement> statementBlock(StatementContext context) {
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
                        children.add(blockChild(context));
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
                case '$' -> children.add(variableDeclarationWithoutNamespace());
                case '@' -> children.add(atRule(context, false));
                default -> {
                    children.add(blockChild(context));
                    whitespaceWithoutComments(true);
                }
            }
        }
    }

    /// Parses one non-comment, non-at-rule child for a braced block.
    ///
    /// @param context the statement forms permitted in the block
    /// @return the parsed child statement
    private SassStatement blockChild(StatementContext context) {
        return switch (context) {
            case ROOT -> variableDeclarationOrStyleRule();
            case STYLE_RULE -> declarationOrStyleRule();
            case DECLARATION -> declarationChild();
            case FUNCTION -> throw scanner.error("Expected @return rule or variable declaration.");
        };
    }

    /// Dispatches a plain `@`-rule after consuming the leading `@`.
    ///
    /// @param context the statement forms permitted in the rule body
    /// @param atStylesheetRoot whether the rule is a direct stylesheet child
    /// @return the parsed at-rule statement
    /// @throws ParseException if the at-rule is unknown or malformed
    private SassStatement atRule(StatementContext context, boolean atStylesheetRoot) {
        var start = scanner.state();
        scanner.expect('@');
        var name = identifier(false, false);
        return switch (name) {
            case "if" -> ifRule(start, context);
            case "each" -> eachRule(start, context);
            case "for" -> forRule(start, context);
            case "while" -> whileRule(start, context);
            case "mixin" -> mixinRule(start, context);
            case "function" -> functionRule(start, context);
            case "include" -> includeRule(start, context);
            case "content" -> contentRule(start, context);
            case "return" -> returnRule(start, context);
            case "use" -> useRule(start, context, atStylesheetRoot);
            case "forward" -> forwardRule(start, context, atStylesheetRoot);
            case "else" -> throw scanner.error(
                    "This at-rule is not allowed here.",
                    start.position(),
                    scanner.position() - start.position()
            );
            default -> throw scanner.error(
                    context == StatementContext.ROOT
                            ? "This stylesheet statement is not available."
                            : "This block statement is not available.",
                    start.position(),
                    scanner.position() - start.position()
            );
        };
    }

    /// Parses an `@if` rule and its trailing `@else if` / `@else` branches.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the statement forms permitted in branch bodies
    /// @return the if rule
    private IfRule ifRule(ScannerState start, StatementContext context) {
        whitespace(true);
        var clauses = new ArrayList<IfClause>();
        clauses.add(new IfClause(expression(), statementBlock(context)));
        whitespaceWithoutComments(false);

        @Nullable ElseClause lastClause = null;
        while (scanElse()) {
            whitespace(false);
            if (scanIdentifier("if")) {
                whitespace(true);
                clauses.add(new IfClause(expression(), statementBlock(context)));
                whitespaceWithoutComments(false);
            } else {
                lastClause = new ElseClause(statementBlock(context));
                whitespaceWithoutComments(false);
                break;
            }
        }
        return new IfRule(clauses, lastClause, scanner.spanFrom(start));
    }

    /// Attempts to consume a trailing `@else` or deprecated `@elseif`.
    ///
    /// @return whether an else introducer was consumed
    private boolean scanElse() {
        var start = scanner.state();
        whitespace(true);
        var beforeAt = scanner.state();
        if (!scanner.scan('@')) {
            scanner.restore(start);
            return false;
        }
        if (scanIdentifier("else", true)) {
            return true;
        }
        if (scanIdentifier("elseif", true)) {
            addParseTimeWarning(new Diagnostic(
                    DiagnosticSeverity.DEPRECATION,
                    "@elseif is deprecated and will not be supported in future Sass "
                            + "versions.\n\n"
                            + "Recommendation: @else if",
                    scanner.spanFrom(beforeAt),
                    "elseif"
            ));
            scanner.restore(new ScannerState(scanner.position() - 2));
            return true;
        }
        scanner.restore(start);
        return false;
    }

    /// Parses an `@each` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the statement forms permitted in the body
    /// @return the each rule
    private EachRule eachRule(ScannerState start, StatementContext context) {
        whitespace(true);
        var variables = new ArrayList<String>();
        variables.add(variableName());
        whitespace(true);
        while (scanner.scan(',')) {
            whitespace(true);
            variables.add(variableName());
            whitespace(true);
        }
        expectIdentifier("in");
        whitespace(true);
        var list = expression();
        var children = statementBlock(context);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new EachRule(variables, list, children, span);
    }

    /// Parses a `@for` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the statement forms permitted in the body
    /// @return the for rule
    private ForRule forRule(ScannerState start, StatementContext context) {
        whitespace(true);
        var variable = variableName();
        whitespace(true);
        expectIdentifier("from");
        whitespace(true);

        var exclusive = new boolean[]{false};
        var bound = new boolean[]{false};
        var from = expression(() -> {
            if (!lookingAtIdentifier()) {
                return false;
            }
            if (scanIdentifier("through")) {
                exclusive[0] = false;
                bound[0] = true;
                return true;
            }
            if (scanIdentifier("to")) {
                exclusive[0] = true;
                bound[0] = true;
                return true;
            }
            return false;
        });
        if (!bound[0]) {
            throw scanner.error("Expected \"to\" or \"through\".");
        }
        whitespace(true);
        var to = expression();
        var children = statementBlock(context);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new ForRule(variable, from, to, exclusive[0], children, span);
    }

    /// Parses a `@while` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the statement forms permitted in the body
    /// @return the while rule
    private WhileRule whileRule(ScannerState start, StatementContext context) {
        whitespace(true);
        var condition = expression();
        var children = statementBlock(context);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new WhileRule(condition, children, span);
    }

    /// Parses a `@mixin` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the surrounding statement context
    /// @return the mixin rule
    private MixinRule mixinRule(ScannerState start, StatementContext context) {
        if (context == StatementContext.FUNCTION || context == StatementContext.DECLARATION) {
            throw scanner.error("Mixins may not be declared here.");
        }
        if (inMixin) {
            throw scanner.error("Mixins may not be declared within a mixin.");
        }
        whitespace(true);
        var originalName = identifier(false, false);
        if (originalName.startsWith("--")) {
            throw scanner.error("Mixin names may not begin with --.");
        }
        whitespace(true);
        var parameters = scanner.peek() == '('
                ? parameterList()
                : ParameterList.empty(scanner.source().span(scanner.position(), scanner.position()));
        whitespace(true);
        inMixin = true;
        ArrayList<SassStatement> children;
        try {
            // Mixin bodies accept style-rule children so bare declarations can be
            // applied when the mixin is included inside a style rule.
            children = statementBlock(StatementContext.STYLE_RULE);
        } finally {
            inMixin = false;
        }
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new MixinRule(originalName, parameters, children, span);
    }

    /// Parses a `@function` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the surrounding statement context
    /// @return the function rule
    private FunctionRule functionRule(ScannerState start, StatementContext context) {
        if (context == StatementContext.FUNCTION || context == StatementContext.DECLARATION) {
            throw scanner.error("Functions may not be declared here.");
        }
        if (inMixin) {
            throw scanner.error("Functions may not be declared within a mixin.");
        }
        whitespace(true);
        var originalName = identifier(false, false);
        if (originalName.startsWith("--")) {
            throw scanner.error("Function names may not begin with --.");
        }
        whitespace(true);
        var parameters = parameterList();
        whitespace(true);
        var children = statementBlock(StatementContext.FUNCTION);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new FunctionRule(originalName, parameters, children, span);
    }

    /// Parses an `@include` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the surrounding statement context
    /// @return the include rule
    private IncludeRule includeRule(ScannerState start, StatementContext context) {
        if (context == StatementContext.FUNCTION) {
            throw scanner.error("Includes may not be used within functions.");
        }
        whitespace(true);
        @Nullable String namespace = null;
        var originalName = identifier(false, false);
        whitespace(true);
        if (scanner.scan('.')) {
            namespace = originalName;
            originalName = identifier(false, false);
            whitespace(true);
        }
        ArgumentList arguments;
        if (scanner.peek() == '(') {
            arguments = argumentInvocation(false);
            whitespace(true);
        } else {
            arguments = ArgumentList.empty(
                    scanner.source().span(scanner.position(), scanner.position())
            );
        }
        if (scanIdentifier("using")) {
            throw scanner.error("Content block parameters aren't supported.");
        }
        @Nullable ContentBlock content = null;
        if (scanner.peek() == '{') {
            var contentStart = scanner.state();
            // Content blocks accept style-rule children even when the include is
            // written at the stylesheet root.
            var children = statementBlock(StatementContext.STYLE_RULE);
            content = new ContentBlock(
                    ParameterList.empty(scanner.source().span(
                            contentStart.position(),
                            contentStart.position()
                    )),
                    children,
                    scanner.spanFrom(contentStart)
            );
        } else {
            expectStatementSeparator();
        }
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new IncludeRule(namespace, originalName, arguments, content, span);
    }

    /// Parses a `@content` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the surrounding statement context
    /// @return the content rule
    private ContentRule contentRule(ScannerState start, StatementContext context) {
        if (!inMixin) {
            throw scanner.error("@content is only allowed within mixin declarations.");
        }
        if (context == StatementContext.FUNCTION) {
            throw scanner.error("@content is only allowed within mixin declarations.");
        }
        whitespace(true);
        ArgumentList arguments;
        if (scanner.peek() == '(') {
            arguments = argumentInvocation(false);
            if (!arguments.isEmpty()) {
                throw scanner.error("Content arguments aren't supported.");
            }
        } else {
            arguments = ArgumentList.empty(
                    scanner.source().span(scanner.position(), scanner.position())
            );
        }
        expectStatementSeparator();
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new ContentRule(arguments, span);
    }

    /// Parses a `@return` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the surrounding statement context
    /// @return the return rule
    private ReturnRule returnRule(ScannerState start, StatementContext context) {
        if (context != StatementContext.FUNCTION) {
            throw scanner.error("@return is only allowed within function declarations.");
        }
        whitespace(true);
        var expression = expression();
        expectStatementSeparator();
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new ReturnRule(expression, span);
    }

    /// Parses a `@use` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the surrounding statement context
    /// @param atStylesheetRoot whether the rule is a direct stylesheet child
    /// @return the use rule
    private UseRule useRule(
            ScannerState start,
            StatementContext context,
            boolean atStylesheetRoot
    ) {
        if (context != StatementContext.ROOT || !atStylesheetRoot) {
            throw scanner.error("@use rules must be at the root of the stylesheet.");
        }
        if (!moduleDirectivesAllowed) {
            throw scanner.error("@use rules must be written before any other rules.");
        }
        whitespace(true);
        var url = string();
        whitespace(true);
        @Nullable String namespace;
        if (scanIdentifier("as")) {
            whitespace(true);
            if (scanner.scan('*')) {
                namespace = null;
            } else {
                namespace = identifier(false, false);
            }
            whitespace(true);
        } else {
            namespace = defaultNamespace(url);
        }
        List<ConfiguredVariable> configuration = List.of();
        if (scanIdentifier("with")) {
            whitespace(true);
            configuration = configuredVariables(false);
            whitespace(true);
        }
        expectStatementSeparator();
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new UseRule(url, namespace, configuration, span);
    }

    /// Parses an {@code @forward} rule and its export transformation.
    ///
    /// @param start            the scanner state at the leading {@code @}
    /// @param context          the surrounding statement context
    /// @param atStylesheetRoot whether the rule is a direct stylesheet child
    /// @return the forward rule
    private ForwardRule forwardRule(
            ScannerState start,
            StatementContext context,
            boolean atStylesheetRoot
    ) {
        if (context != StatementContext.ROOT || !atStylesheetRoot) {
            throw scanner.error("@forward rules must be at the root of the stylesheet.");
        }
        if (!moduleDirectivesAllowed) {
            throw scanner.error("@forward rules must be written before any other rules.");
        }
        whitespace(true);
        var url = string();
        whitespace(true);

        @Nullable String prefix = null;
        if (scanIdentifier("as")) {
            whitespace(true);
            prefix = identifier(true, false);
            scanner.expect('*');
            whitespace(true);
        }

        @Nullable @Unmodifiable Set<String> shownMixinsAndFunctions = null;
        @Nullable @Unmodifiable Set<String> shownVariables = null;
        @Nullable @Unmodifiable Set<String> hiddenMixinsAndFunctions = null;
        @Nullable @Unmodifiable Set<String> hiddenVariables = null;
        if (scanIdentifier("show")) {
            var members = forwardMemberList();
            shownMixinsAndFunctions = members.mixinsAndFunctions();
            shownVariables = members.variables();
        } else if (scanIdentifier("hide")) {
            var members = forwardMemberList();
            hiddenMixinsAndFunctions = members.mixinsAndFunctions();
            hiddenVariables = members.variables();
        }

        List<ConfiguredVariable> configuration = List.of();
        if (scanIdentifier("with")) {
            whitespace(true);
            configuration = configuredVariables(true);
            whitespace(true);
        }
        expectStatementSeparator();
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new ForwardRule(
                url,
                prefix,
                shownMixinsAndFunctions,
                shownVariables,
                hiddenMixinsAndFunctions,
                hiddenVariables,
                configuration,
                span
        );
    }

    /// Parses the members named by a forward {@code show} or {@code hide} clause.
    ///
    /// @return the normalized callable and variable names
    private ForwardMembers forwardMemberList() {
        var mixinsAndFunctions = new LinkedHashSet<String>();
        var variables = new LinkedHashSet<String>();
        do {
            whitespace(true);
            if (scanner.peek() == '$') {
                variables.add(variableName());
            } else {
                mixinsAndFunctions.add(identifier(true, false));
            }
            whitespace(false);
        } while (scanner.scan(','));
        return new ForwardMembers(mixinsAndFunctions, variables);
    }

    /// Parses configured variables in a module directive.
    ///
    /// @param allowGuarded whether entries may end in {@code !default}
    /// @return configured variables in source order
    private ArrayList<ConfiguredVariable> configuredVariables(boolean allowGuarded) {
        scanner.expect('(');
        whitespace(true);
        var variables = new ArrayList<ConfiguredVariable>();
        var names = new HashSet<String>();
        while (true) {
            var variableStart = scanner.state();
            var nameStart = scanner.state();
            var name = variableName();
            var nameSpan = scanner.spanFrom(nameStart);
            if (!names.add(name)) {
                throw scanner.error(
                        "The same variable may only be configured once.",
                        nameSpan.start().offset(),
                        nameSpan.text().length()
                );
            }
            whitespace(true);
            scanner.expect(':');
            whitespace(true);
            var expression = expressionUntilComma();
            var variableEnd = expression.span().end().offset();
            var guarded = false;
            if (allowGuarded && scanner.scan('!')) {
                var flagStart = scanner.state();
                var flag = identifier(false, false);
                if (!"default".equals(flag)) {
                    throw scanner.error(
                            "Invalid flag name.",
                            flagStart.position(),
                            scanner.position() - flagStart.position()
                    );
                }
                guarded = true;
                variableEnd = scanner.position();
            }
            var variableSpan = scanner.source().span(
                    variableStart.position(),
                    variableEnd
            );
            variables.add(new ConfiguredVariable(
                    name,
                    expression,
                    nameSpan,
                    variableSpan,
                    guarded
            ));
            whitespace(true);
            if (!scanner.scan(',')) {
                break;
            }
            whitespace(true);
            if (scanner.peek() == ')') {
                break;
            }
        }
        scanner.expect(')');
        return variables;
    }

    /// Stores normalized forward member filters by Sass member namespace.
    ///
    /// @param mixinsAndFunctions callable member names
    /// @param variables          variable member names
    @NotNullByDefault
    private record ForwardMembers(
            @Unmodifiable Set<String> mixinsAndFunctions,
            @Unmodifiable Set<String> variables
    ) {
        /// Creates an immutable member-filter snapshot.
        private ForwardMembers {
            mixinsAndFunctions = Set.copyOf(mixinsAndFunctions);
            variables = Set.copyOf(variables);
        }
    }

    /// Derives the default namespace from an unresolved module URL.
    ///
    /// @param url the module URL string
    /// @return the default namespace
    private String defaultNamespace(String url) {
        var path = url;
        var slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        if (path.startsWith("sass:")) {
            path = path.substring("sass:".length());
        }
        if (path.startsWith("_")) {
            path = path.substring(1);
        }
        var dot = path.indexOf('.');
        if (dot > 0) {
            path = path.substring(0, dot);
        }
        try {
            return CssIdentifierParser.parse(path, true, false);
        } catch (ParseException ignored) {
            throw scanner.error(
                    "The default namespace \"" + path + "\" is not a valid CSS identifier.\n\n"
                            + "Recommendation: add an \"as\" clause to define an explicit namespace."
            );
        }
    }

    /// Parses a parenthesized parameter list.
    ///
    /// @return the parameter list
    private ParameterList parameterList() {
        var start = scanner.state();
        scanner.expect('(');
        whitespace(true);
        var parameters = new ArrayList<Parameter>();
        var names = new java.util.HashSet<String>();
        @Nullable String restParameter = null;
        while (scanner.peek() == '$') {
            var parameterStart = scanner.state();
            var name = variableName();
            if (!names.add(name)) {
                throw scanner.error("Duplicate parameter name.");
            }
            whitespace(true);
            @Nullable SassExpression defaultValue = null;
            if (scanner.scan(':')) {
                whitespace(true);
                defaultValue = expressionUntilComma();
                whitespace(true);
            }
            if (scanner.scan('.') && scanner.scan('.') && scanner.scan('.')) {
                if (defaultValue != null) {
                    throw scanner.error("Rest parameters may not have default values.");
                }
                restParameter = name;
                whitespace(true);
                scanner.scan(',');
                whitespace(true);
                break;
            }
            parameters.add(new Parameter(name, defaultValue, scanner.spanFrom(parameterStart)));
            if (!scanner.scan(',')) {
                break;
            }
            whitespace(true);
        }
        scanner.expect(')');
        return new ParameterList(parameters, restParameter, scanner.spanFrom(start));
    }

    /// Parses a declaration when possible and otherwise reparses from the same
    /// source position as a nested style rule.
    ///
    /// @return the parsed declaration or style rule
    private SassStatement declarationOrStyleRule() {
        var variable = tryNamespacedVariableDeclaration();
        if (variable != null) {
            return variable;
        }

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
    /// @return the nested variable or property declaration
    /// @throws ParseException if the child is not a supported declaration
    private SassStatement declarationChild() {
        var variable = tryNamespacedVariableDeclaration();
        if (variable != null) {
            return variable;
        }

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

    /// Attempts to parse a variable assignment beginning with a module namespace.
    ///
    /// Input is restored when the next tokens are not exactly an identifier,
    /// period, and dollar sign. Once that prefix is recognized, malformed
    /// declarations are reported rather than reparsed as selectors.
    ///
    /// @return the declaration, or {@code null} without consuming input
    private @Nullable VariableDeclaration tryNamespacedVariableDeclaration() {
        if (!lookingAtIdentifier()) {
            return null;
        }

        var start = scanner.state();
        var namespaceStart = scanner.state();
        var namespace = identifier(false, false);
        var namespaceEnd = scanner.state();
        if (scanner.peek() != '.' || scanner.peek(1) != '$') {
            scanner.restore(start);
            return null;
        }

        scanner.read();
        return variableDeclarationWithoutNamespace(
                namespace,
                scanner.spanFrom(namespaceStart, namespaceEnd),
                start
        );
    }

    /// Parses an unqualified variable declaration at the current position.
    ///
    /// @return the parsed declaration
    private VariableDeclaration variableDeclarationWithoutNamespace() {
        return variableDeclarationWithoutNamespace(null, null, scanner.state());
    }

    /// Parses a variable declaration after an optional namespace.
    ///
    /// The namespace, when present, has already been consumed along with its
    /// following period. Duplicate flags are retained as parse-time
    /// deprecations, while unknown flags and cross-module global assignments
    /// are syntax errors.
    ///
    /// @param namespace the decoded namespace, or {@code null}
    /// @param namespaceSpan the namespace source range, or {@code null}
    /// @param start the beginning of the complete declaration
    /// @return the parsed declaration
    private VariableDeclaration variableDeclarationWithoutNamespace(
            @Nullable String namespace,
            @Nullable SourceSpan namespaceSpan,
            ScannerState start
    ) {
        var precedingComment = lastSilentComment;
        lastSilentComment = null;

        var nameStart = scanner.state();
        var name = variableName();
        var nameSpan = scanner.spanFrom(nameStart);
        if (namespace != null && (name.startsWith("-") || name.startsWith("_"))) {
            throw scanner.error(
                    "Private members can't be accessed from outside their modules.",
                    start.position(),
                    scanner.position() - start.position()
            );
        }

        whitespace(true);
        scanner.expect(':');
        whitespace(true);
        var value = expression();

        var guarded = false;
        var global = false;
        var flagStart = scanner.state();
        while (scanner.scan('!')) {
            var flag = identifier(false, false);
            var flagSpan = scanner.spanFrom(flagStart);
            switch (flag) {
                case "default" -> {
                    if (guarded) {
                        addParseTimeWarning(new Diagnostic(
                                DiagnosticSeverity.DEPRECATION,
                                "!default should only be written once for each variable.\n"
                                        + "This will be an error in Dart Sass 2.0.0.",
                                flagSpan,
                                "duplicate-var-flags"
                        ));
                    }
                    guarded = true;
                }
                case "global" -> {
                    if (namespace != null) {
                        throw scanner.error(
                                "!global isn't allowed for variables in other modules.",
                                flagSpan.start().offset(),
                                flagSpan.text().length()
                        );
                    }
                    if (global) {
                        addParseTimeWarning(new Diagnostic(
                                DiagnosticSeverity.DEPRECATION,
                                "!global should only be written once for each variable.\n"
                                        + "This will be an error in Dart Sass 2.0.0.",
                                flagSpan,
                                "duplicate-var-flags"
                        ));
                    }
                    global = true;
                }
                default -> throw scanner.error(
                        "Invalid flag name.",
                        flagSpan.start().offset(),
                        flagSpan.text().length()
                );
            }

            whitespace(false);
            flagStart = scanner.state();
        }

        expectStatementSeparator();
        var declaration = new VariableDeclaration(
                namespace,
                name,
                value,
                guarded,
                global,
                precedingComment,
                nameSpan,
                namespaceSpan,
                scanner.spanFrom(start)
        );
        if (global) {
            globalVariables.putIfAbsent(name, declaration.span());
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
        if (lookingAtPotentialPropertyHack()) {
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
                rawValue = interpolatedDeclarationValue(false, false);
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

            // Dart Sass retains warnings from speculative declaration values
            // before reparsing the same source as selector text.
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
        var children = statementBlock(StatementContext.DECLARATION);
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

        var comment = new SilentComment(
                scanner.substring(start.position()),
                scanner.spanFrom(start)
        );
        lastSilentComment = comment;
        return comment;
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
