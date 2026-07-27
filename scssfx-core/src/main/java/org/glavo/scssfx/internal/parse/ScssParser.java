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
import org.glavo.scssfx.internal.ast.DebugRule;
import org.glavo.scssfx.internal.ast.DynamicImport;
import org.glavo.scssfx.internal.ast.EachRule;
import org.glavo.scssfx.internal.ast.ElseClause;
import org.glavo.scssfx.internal.ast.ErrorRule;
import org.glavo.scssfx.internal.ast.ForwardRule;
import org.glavo.scssfx.internal.ast.ForRule;
import org.glavo.scssfx.internal.ast.FontFaceRule;
import org.glavo.scssfx.internal.ast.FunctionRule;
import org.glavo.scssfx.internal.ast.IfClause;
import org.glavo.scssfx.internal.ast.IfRule;
import org.glavo.scssfx.internal.ast.IncludeRule;
import org.glavo.scssfx.internal.ast.Interpolation;
import org.glavo.scssfx.internal.ast.InterpolationBuffer;
import org.glavo.scssfx.internal.ast.ImportRule;
import org.glavo.scssfx.internal.ast.LoudComment;
import org.glavo.scssfx.internal.ast.MixinRule;
import org.glavo.scssfx.internal.ast.MediaRule;
import org.glavo.scssfx.internal.ast.SupportsRule;
import org.glavo.scssfx.internal.ast.SupportsAnything;
import org.glavo.scssfx.internal.ast.SupportsBooleanOperator;
import org.glavo.scssfx.internal.ast.SupportsCondition;
import org.glavo.scssfx.internal.ast.SupportsDeclaration;
import org.glavo.scssfx.internal.ast.SupportsFunction;
import org.glavo.scssfx.internal.ast.SupportsInterpolation;
import org.glavo.scssfx.internal.ast.SupportsNegation;
import org.glavo.scssfx.internal.ast.SupportsOperation;
import org.glavo.scssfx.internal.ast.Parameter;
import org.glavo.scssfx.internal.ast.ParameterList;
import org.glavo.scssfx.internal.ast.ReturnRule;
import org.glavo.scssfx.internal.ast.UseRule;
import org.glavo.scssfx.internal.ast.SassImport;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.SassStatement;
import org.glavo.scssfx.internal.ast.SilentComment;
import org.glavo.scssfx.internal.ast.StringExpression;
import org.glavo.scssfx.internal.ast.StyleRule;
import org.glavo.scssfx.internal.ast.StaticImport;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.ast.AtRootRule;
import org.glavo.scssfx.internal.ast.ExtendRule;
import org.glavo.scssfx.internal.ast.UnknownAtRule;
import org.glavo.scssfx.internal.ast.VariableDeclaration;
import org.glavo.scssfx.internal.ast.WarnRule;
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
import java.util.Locale;
import java.util.Set;

/// Parses SCSS stylesheets containing declarations, nested properties, style rules,
/// control-flow at-rules, module directives, mixins, functions, and comments.
@NotNullByDefault
final class ScssParser extends SassExpressionParser {
    /// Records whether Sass-only stylesheet syntax must be rejected.
    private final boolean plainCss;

    /// Counts style-rule blocks currently being parsed in plain CSS.
    private int plainCssStyleRuleDepth;

    /// Identifies the statement forms allowed inside a braced block.
    private enum StatementContext {
        /// Top-level statements and top-level control-flow bodies.
        ROOT,

        /// Style-rule children, including nested rules and declarations.
        STYLE_RULE,

        /// Nested-property children that reject nested style rules.
        DECLARATION,

        /// Font-face bodies that accept only declarations and control flow.
        FONT_FACE,

        /// Function bodies that accept only variables, control flow, and `@return`.
        FUNCTION
    }

    /// First global declaration span for each normalized variable name.
    private final LinkedHashMap<String, SourceSpan> globalVariables = new LinkedHashMap<>();

    /// The most recently parsed silent comment, or {@code null} when none exists.
    private @Nullable SilentComment lastSilentComment;

    /// Records whether the parser is inside a mixin declaration body.
    private boolean inMixin;

    /// Contains the number of nested control-directive bodies being parsed.
    private int controlDirectiveDepth;

    /// Records whether the parser is inside an include content block.
    private boolean inContentBlock;

    /// Records whether the parser is inside a plain-CSS {@code @function --name}
    /// rule, where the {@code result} descriptor is parsed like a custom property.
    private boolean inPlainCssFunction;

    /// Records whether module directives are still allowed at the stylesheet root.
    private boolean moduleDirectivesAllowed = true;

    /// Whether this parse was projected from the indented syntax.
    ///
    /// Empty {@code @import} (no URL) is valid only in indented Sass and must
    /// become a dynamic import of the empty path, which reloads the current file.
    private final boolean fromIndented;

    /// Creates a parser for an indexed SCSS source.
    ///
    /// @param source the SCSS source to parse
    ScssParser(SourceFile source) {
        this(source, false, false);
    }

    /// Creates a parser for an indexed SCSS or plain-CSS source.
    ///
    /// @param source the source to parse
    /// @param plainCss whether Sass-only stylesheet syntax must be rejected
    ScssParser(SourceFile source, boolean plainCss) {
        this(source, plainCss, false);
    }

    /// Creates a parser for projected indented Sass, SCSS, or plain CSS.
    ///
    /// @param source the source to parse
    /// @param plainCss whether Sass-only stylesheet syntax must be rejected
    /// @param fromIndented whether the source was projected from indented Sass
    ScssParser(SourceFile source, boolean plainCss, boolean fromIndented) {
        super(source);
        this.plainCss = plainCss;
        this.fromIndented = fromIndented;
    }

    /// Parses the complete source as one interactive variable declaration.
    ///
    /// @return the parsed declaration
    /// @throws ParseException if the source is not exactly one declaration
    VariableDeclaration parseInteractiveVariableDeclaration() {
        VariableDeclaration result;
        if (lookingAtIdentifier()) {
            @Nullable VariableDeclaration namespaced =
                    tryNamespacedVariableDeclaration();
            if (namespaced == null) {
                throw scanner.error("Expected variable.");
            }
            result = namespaced;
        } else {
            result = variableDeclarationWithoutNamespace();
        }
        scanner.expectDone();
        return result;
    }

    /// Parses the complete source as one interactive {@code @use} rule.
    ///
    /// @return the parsed use rule
    /// @throws ParseException if the source is not exactly one use rule
    UseRule parseInteractiveUseRule() {
        var start = scanner.state();
        scanner.expect('@');
        expectIdentifier("use");
        var result = useRule(start, StatementContext.ROOT, true);
        scanner.expectDone();
        return result;
    }

    /// {@inheritDoc}
    @Override
    protected boolean isPlainCssSource() {
        return plainCss;
    }

    /// Consumes a Sass silent comment unless plain-CSS restrictions are active.
    ///
    /// Silent comments inside expressions are left alone so slash-only forms
    /// such as {@code 1///bar} can be parsed as unary division rather than
    /// comment tokens.
    ///
    /// @return {@code true} after consuming the comment
    /// @throws ParseException if a silent comment occurs in plain CSS outside
    /// an expression
    @Override
    protected boolean silentComment() {
        // Match dart-sass CssParser: silent comments are not tokenized inside
        // plain-CSS expressions, so {@code 1///bar} remains slash operators.
        if (plainCss && inExpression()) {
            return false;
        }
        if (plainCss) {
            throw scanner.error("Silent comments aren't allowed in plain CSS.");
        }
        return super.silentComment();
    }

    /// Parses the complete source as an SCSS or plain-CSS stylesheet.
    ///
    /// A byte-order mark is accepted only at the beginning. Whitespace and
    /// empty semicolon statements do not produce syntax nodes.
    ///
    /// @return the immutable stylesheet syntax tree
    /// @throws ParseException if a comment is malformed or another statement
    /// production is encountered
    Stylesheet parse() {
        if (plainCss) {
            validatePlainCssSource();
        }
        var start = scanner.state();
        scanner.scan(0xFEFF);
        var children = statements();
        scanner.expectDone();
        return new Stylesheet(
                children,
                scanner.spanFrom(start),
                plainCss,
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
                        rejectPlainCss("Silent comments aren't allowed in plain CSS.");
                        statements.add(silentCommentStatement());
                    } else if (scanner.peek(1) == '*') {
                        statements.add(loudCommentStatement());
                    } else {
                        throw scanner.error("Expected stylesheet statement.");
                    }
                    whitespaceWithoutComments(true);
                }
                case '}' -> throw scanner.error("unmatched \"}\".");
                case ';' -> {
                    scanner.read();
                    whitespaceWithoutComments(true);
                }
                case '$' -> {
                    rejectPlainCss("Sass variables aren't allowed in plain CSS.");
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
        requirePlainCssText(selector);
        if (selector.parts().isEmpty()) {
            throw scanner.error("expected selector.");
        }
        ArrayList<SassStatement> children;
        if (plainCss) {
            plainCssStyleRuleDepth++;
            try {
                children = statementBlock(StatementContext.STYLE_RULE);
            } finally {
                plainCssStyleRuleDepth--;
            }
        } else {
            children = statementBlock(StatementContext.STYLE_RULE);
        }
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
                case CssCharacters.END_OF_INPUT -> throw scanner.error("expected \"}\".");
                case '/' -> {
                    if (scanner.peek(1) == '/') {
                        rejectPlainCss("Silent comments aren't allowed in plain CSS.");
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
                case '$' -> {
                    rejectPlainCss("Sass variables aren't allowed in plain CSS.");
                    children.add(variableDeclarationWithoutNamespace());
                }
                case '@' -> children.add(
                        context == StatementContext.DECLARATION
                                || context == StatementContext.FUNCTION
                                ? restrictedAtRule(context)
                                : atRule(context, false)
                );
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
            case DECLARATION, FONT_FACE -> declarationChild();
            // dart-sass: property-like children in @function are rejected as
            // declarations. Local and namespaced variable assignments are allowed
            // (and must be disambiguated from property declarations).
            case FUNCTION -> {
                if (scanner.peek() == '$') {
                    yield variableDeclarationWithoutNamespace();
                }
                @Nullable VariableDeclaration namespaced = tryNamespacedVariableDeclaration();
                if (namespaced != null) {
                    yield namespaced;
                }
                throw scanner.error(
                        "@function rules may not contain declarations."
                );
            }
        };
    }

    /// Parses an at-rule allowed only in property declarations or functions.
    ///
    /// Matches dart-sass {@code _declarationAtRule} / {@code _functionChild}:
    /// the name must be a plain identifier (no interpolation), and unknown
    /// names are rejected with {@code This at-rule is not allowed here.}
    ///
    /// @param context {@link StatementContext#DECLARATION} or {@link StatementContext#FUNCTION}
    /// @return the parsed at-rule
    private SassStatement restrictedAtRule(StatementContext context) {
        var start = scanner.state();
        scanner.expect('@');
        // Interpolation after {@code @} is not a valid at-rule name here.
        if (scanner.peek() == '#' && scanner.peek(1) == '{') {
            throw scanner.error("Expected identifier.");
        }
        var name = identifier(false, false);
        return switch (name) {
            case "content" -> contentRule(start, context);
            case "debug" -> debugRule(start);
            case "each" -> eachRule(start, context);
            case "else" -> throw scanner.error(
                    "This at-rule is not allowed here.",
                    start.position(),
                    scanner.position() - start.position()
            );
            case "error" -> errorRule(start);
            case "for" -> forRule(start, context);
            case "if" -> ifRule(start, context);
            case "include" -> {
                if (context == StatementContext.FUNCTION) {
                    throw scanner.error(
                            "This at-rule is not allowed here.",
                            start.position(),
                            scanner.position() - start.position()
                    );
                }
                yield includeRule(start, context);
            }
            case "return" -> returnRule(start, context);
            case "warn" -> warnRule(start);
            case "while" -> whileRule(start, context);
            default -> {
                // Consume the remainder of the rule for a complete span, then reject.
                // Use STYLE_RULE for nested bodies so ordinary declarations do not
                // surface as "@function rules may not contain declarations" before
                // the intended at-rule diagnostic (issue_1941 / nested @mixin).
                whitespace(false);
                interpolatedDeclarationValue(true, false, () -> scanner.peek() == '{');
                if (scanner.peek() == '{') {
                    statementBlock(StatementContext.STYLE_RULE);
                } else {
                    expectStatementSeparator();
                }
                throw scanner.error(
                        "This at-rule is not allowed here.",
                        start.position(),
                        scanner.position() - start.position()
                );
            }
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
        // Support both plain names and interpolated names such as @#{function}.
        var nameInterpolation = interpolatedIdentifier();
        requirePlainCssText(nameInterpolation);
        @Nullable String name = nameInterpolation.asPlain();
        if (name == null) {
            return unknownAtRule(start, nameInterpolation);
        }
        if (plainCss) {
            return switch (name) {
                case "at-root", "content", "debug", "each", "error", "extend",
                        "for", "if", "include", "mixin", "return", "warn", "while" ->
                        throw scanner.error(
                                "This at-rule isn't allowed in plain CSS.",
                                start.position(),
                                scanner.position() - start.position()
                        );
                case "function" -> {
                    whitespace(false);
                    if (scanner.peek() != '-' || scanner.peek(1) != '-') {
                        throw scanner.error(
                                "This at-rule isn't allowed in plain CSS.",
                                start.position(),
                                scanner.position() - start.position()
                        );
                    }
                    yield unknownAtRule(start, nameInterpolation);
                }
                case "font-face" -> fontFaceRule(start, context);
                case "media" -> mediaRule(start, context);
                case "supports" -> supportsRule(start, context);
                case "import" -> importRule(start, context);
                case "charset" -> charsetRule(start);
                default -> unknownAtRule(start, nameInterpolation);
            };
        }
        return switch (name) {
            case "if" -> ifRule(start, context);
            case "each" -> eachRule(start, context);
            case "for" -> forRule(start, context);
            case "font-face" -> fontFaceRule(start, context);
            case "media" -> mediaRule(start, context);
            case "supports" -> supportsRule(start, context);
            case "while" -> whileRule(start, context);
            case "mixin" -> mixinRule(start, context);
            case "function" -> {
                // CSS Custom Functions use @function --name(...). Route those to the
                // plain-CSS at-rule path instead of Sass @function declarations.
                var afterName = scanner.state();
                whitespace(true);
                if (scanner.peek() == '-' && scanner.peek(1) == '-') {
                    scanner.restore(afterName);
                    yield unknownAtRule(start, nameInterpolation);
                }
                scanner.restore(afterName);
                yield functionRule(start, context);
            }
            case "include" -> includeRule(start, context);
            case "content" -> contentRule(start, context);
            case "return" -> returnRule(start, context);
            case "debug" -> debugRule(start);
            case "warn" -> warnRule(start);
            case "import" -> importRule(start, context);
            case "error" -> errorRule(start);
            case "use" -> useRule(start, context, atStylesheetRoot);
            case "forward" -> forwardRule(start, context, atStylesheetRoot);
            case "else" -> throw scanner.error(
                    "This at-rule is not allowed here.",
                    start.position(),
                    scanner.position() - start.position()
            );
            case "at-root" -> atRootRule(start);
            case "extend" -> extendRule(start, context);
            case "charset" -> charsetRule(start);
            default -> unknownAtRule(start, nameInterpolation);
        };
    }

    /// Parses and discards an {@code @charset} rule.
    ///
    /// Sass ignores {@code @charset} for CSS emission; output encoding is
    /// controlled by the host and optional UTF-8 charset injection for
    /// non-ASCII expanded CSS.
    ///
    /// @param start the scanner state at the leading {@code @}
    /// @return a silent no-op statement
    /// @throws ParseException if the charset string is missing or malformed
    private SilentComment charsetRule(ScannerState start) {
        whitespace(false);
        if (scanner.peek() != '\'' && scanner.peek() != '"') {
            throw scanner.error("Expected string.");
        }
        string();
        expectStatementSeparator();
        return new SilentComment("", scanner.spanFrom(start));
    }

    /// Parses an {@code @at-root} rule.
    ///
    /// Supports a parenthesized query, a braced child block, or a trailing style
    /// rule used as the sole child.
    ///
    /// @param start the scanner state at the leading {@code @}
    /// @return the at-root rule
    private AtRootRule atRootRule(ScannerState start) {
        whitespace(false);
        @Nullable Interpolation query = null;
        ArrayList<SassStatement> children;
        if (scanner.peek() == '(') {
            query = atRootQuery();
            whitespace(false);
            children = statementBlock(StatementContext.STYLE_RULE);
        } else if (scanner.peek() == '{') {
            children = statementBlock(StatementContext.STYLE_RULE);
        } else {
            children = new ArrayList<>();
            children.add(styleRule());
        }
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new AtRootRule(query, children, span);
    }

    /// Parses the parenthesized query of an {@code @at-root} rule.
    ///
    /// @return the unevaluated query interpolation
    private Interpolation atRootQuery() {
        var start = scanner.state();
        var buffer = new InterpolationBuffer();
        scanner.expect('(');
        buffer.append('(');
        var depth = 1;
        while (depth > 0) {
            var next = scanner.peek();
            switch (next) {
                case CssCharacters.END_OF_INPUT -> throw scanner.error("Expected \")\".");
                case '\\' -> {
                    buffer.append((char) scanner.read());
                    buffer.append((char) scanner.read());
                }
                case '\'', '"' -> buffer.add(interpolatedStringToken());
                case '/' -> {
                    // Comments are transparent inside {@code @at-root} queries
                    // (dart-sass {@code almostAnyValue}/query scanners).
                    if (scanner.peek(1) == '*') {
                        loudComment();
                    } else if (scanner.peek(1) == '/') {
                        silentComment();
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
                case '(' -> {
                    depth++;
                    buffer.append((char) scanner.read());
                }
                case ')' -> {
                    depth--;
                    if (depth == 0) {
                        scanner.read();
                        buffer.append(')');
                    } else {
                        buffer.append((char) scanner.read());
                    }
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

    /// Parses an {@code @extend} rule.
    ///
    /// @param start   the scanner state at the leading {@code @}
    /// @param context the enclosing statement context
    /// @return the extend rule
    private ExtendRule extendRule(ScannerState start, StatementContext context) {
        if (context == StatementContext.ROOT || context == StatementContext.FUNCTION) {
            throw scanner.error(
                    context == StatementContext.ROOT
                            ? "This stylesheet statement is not available."
                            : "This block statement is not available.",
                    start.position(),
                    scanner.position() - start.position()
            );
        }
        whitespace(false);
        var selector = almostAnyValue();
        @Nullable String plain = selector.asPlain();
        if (selector.parts().isEmpty() || plain != null && plain.isBlank()) {
            throw scanner.error("expected selector.");
        }
        var optional = false;
        if (scanner.scan('!')) {
            expectIdentifier("optional");
            optional = true;
            whitespace(false);
        }
        expectStatementSeparator();
        return new ExtendRule(selector, optional, scanner.spanFrom(start));
    }

    /// Parses an opaque at-rule accepted by plain CSS.
    ///
    /// @param start the state at the leading at sign
    /// @param name the decoded at-rule name
    /// @return the opaque rule
    /// Parses an opaque at-rule accepted by plain CSS.
    ///
    /// @param start the state at the leading at sign
    /// @param name  the at-rule name (may contain interpolation)
    /// @return the opaque rule
    private UnknownAtRule unknownAtRule(ScannerState start, Interpolation name) {
        whitespace(false);
        // Omit silent comments from the prelude (dart-sass default
        // silentComments: true) so {@code @a b //} becomes {@code @a b}.
        var value = interpolatedDeclarationValue(
                true,
                true,
                () -> scanner.peek() == '{'
        );
        requirePlainCssText(value);
        @Nullable String plainName = name.asPlain();
        // dart-sass's dedicated @-moz-document parser consumes trailing comments
        // as whitespace before the block, so they must not appear in the prelude.
        if (plainName != null && plainName.equalsIgnoreCase("-moz-document")) {
            value = stripTrailingCommentsAndWhitespace(value);
        }
        if (scanner.peek() == '{') {
            var wasInPlainCssFunction = inPlainCssFunction;
            // Only a non-interpolated {@code @function} enters CSS-function mode.
            // {@code @#{function}} keeps SassScript evaluation for {@code result}
            // (dart-sass: interpolated at-rule names are not CSS custom functions).
            if (plainName != null && plainName.equalsIgnoreCase("function")) {
                inPlainCssFunction = true;
            }
            ArrayList<SassStatement> children;
            try {
                children = statementBlock(StatementContext.STYLE_RULE);
            } finally {
                inPlainCssFunction = wasInPlainCssFunction;
            }
            var span = scanner.spanFrom(start);
            whitespaceWithoutComments(false);
            return new UnknownAtRule(name, value, children, span);
        }
        expectStatementSeparator();
        return new UnknownAtRule(name, value, null, scanner.spanFrom(start));
    }

    /// Parses a top-level `@font-face` rule.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the enclosing statement context
    /// @return the font-face rule
    /// @throws ParseException if the rule is nested or its body is malformed
    private FontFaceRule fontFaceRule(ScannerState start, StatementContext context) {
        // Nested {@code @font-face} inside style rules is legal and bubbles to
        // the stylesheet root during evaluation (dart-sass). Other contexts keep
        // the historical root-only diagnostic.
        if (context != StatementContext.ROOT && context != StatementContext.STYLE_RULE) {
            throw scanner.error(
                    "This at-rule may only be used at the stylesheet root.",
                    start.position(),
                    scanner.position() - start.position()
            );
        }
        whitespace(true);
        var children = statementBlock(StatementContext.FONT_FACE);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new FontFaceRule(children, span);
    }

    /// Parses an {@code @media} rule in a stylesheet or style-rule context.
    ///
    /// @param start   the scanner state at the leading {@code @}
    /// @param context the enclosing statement context
    /// @return the parsed media rule
    /// @throws ParseException if the query is missing or the rule is disallowed here
    private MediaRule mediaRule(ScannerState start, StatementContext context) {
        switch (context) {
            case DECLARATION -> throw scanner.error(
                    "Media rules may not be used within nested declarations.",
                    start.position(),
                    scanner.position() - start.position()
            );
            case FONT_FACE -> throw scanner.error(
                    "Media rules may not be used within @font-face rules.",
                    start.position(),
                    scanner.position() - start.position()
            );
            case FUNCTION -> throw scanner.error(
                    "Media rules may not be used within functions.",
                    start.position(),
                    scanner.position() - start.position()
            );
            default -> {
            }
        }

        whitespace(true);
        var query = mediaQueryList();
        var children = statementBlock(context);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new MediaRule(query, children, span);
    }

    /// Consumes a comma-separated list of media queries as one interpolation.
    ///
    /// Aligns with dart-sass {@code StylesheetParser._mediaQueryList}: types and
    /// modifiers stay as identifier text, while media-feature bodies embed
    /// SassScript expressions so range syntax and variables evaluate correctly.
    ///
    /// @return the media-query-list interpolation
    private Interpolation mediaQueryList() {
        var start = scanner.state();
        var buffer = new InterpolationBuffer();
        while (true) {
            whitespace(false);
            mediaQuery(buffer);
            whitespace(false);
            if (!scanner.scan(',')) {
                break;
            }
            buffer.append(',');
            buffer.append(' ');
        }
        return buffer.interpolation(scanner.spanFrom(start));
    }

    /// Consumes one media query into {@code buffer}.
    ///
    /// @param buffer the interpolation receiving the query text and expressions
    private void mediaQuery(InterpolationBuffer buffer) {
        // Somewhat duplicated in CssMediaQuery.Parser.parseQuery for evaluated
        // plain CSS after interpolation.
        if (scanner.peek() == '(') {
            mediaInParens(buffer);
            whitespace(false);
            if (scanIdentifier("and")) {
                buffer.append(" and ");
                expectWhitespace(false);
                mediaLogicSequence(buffer, "and");
            } else if (scanIdentifier("or")) {
                buffer.append(" or ");
                expectWhitespace(false);
                mediaLogicSequence(buffer, "or");
            }
            return;
        }

        var identifier1 = interpolatedIdentifier();
        @Nullable String plain1 = identifier1.asPlain();
        if (plain1 != null && plain1.equalsIgnoreCase("not")) {
            // For example, "@media not (...) {"
            expectWhitespace(false);
            if (!lookingAtInterpolatedIdentifier()) {
                buffer.append("not ");
                mediaOrInterp(buffer);
                return;
            }
        }

        whitespace(false);
        buffer.add(identifier1);
        if (!lookingAtInterpolatedIdentifier()) {
            // For example, "@media screen {".
            return;
        }

        buffer.append(' ');
        var identifier2 = interpolatedIdentifier();
        @Nullable String plain2 = identifier2.asPlain();
        if (plain2 != null && plain2.equalsIgnoreCase("and")) {
            expectWhitespace(false);
            // For example, "@media screen and ..."
            buffer.append(" and ");
        } else {
            whitespace(false);
            buffer.add(identifier2);
            if (scanIdentifier("and")) {
                // For example, "@media only screen and ..."
                expectWhitespace(false);
                buffer.append(" and ");
            } else {
                // For example, "@media only screen {"
                return;
            }
        }

        // Consumed either IDENTIFIER "and" or IDENTIFIER IDENTIFIER "and".
        if (scanIdentifier("not")) {
            // For example, "@media screen and not (...) {"
            expectWhitespace(false);
            buffer.append("not ");
            mediaOrInterp(buffer);
            return;
        }

        mediaLogicSequence(buffer, "and");
    }

    /// Consumes one or more media-in-parens (or interpolations) joined by {@code operator}.
    ///
    /// @param buffer   the interpolation receiving the sequence
    /// @param operator the required {@code and} or {@code or} keyword
    private void mediaLogicSequence(InterpolationBuffer buffer, String operator) {
        while (true) {
            mediaOrInterp(buffer);
            whitespace(false);
            if (!scanIdentifier(operator)) {
                return;
            }
            expectWhitespace(false);
            buffer.append(' ');
            buffer.append(operator);
            buffer.append(' ');
        }
    }

    /// Consumes a media-in-parens expression or a lone {@code #{…}} interpolation.
    ///
    /// @param buffer the interpolation receiving the condition
    private void mediaOrInterp(InterpolationBuffer buffer) {
        if (scanner.peek() == '#' && scanner.peek(1) == '{') {
            singleInterpolation(buffer);
        } else {
            mediaInParens(buffer);
        }
    }

    /// Consumes a {@code <media-in-parens>} expression, including range syntax.
    ///
    /// Feature names and values are parsed as SassScript expressions so media
    /// range operators {@code <}/{@code >}/{@code =}/{@code <=}/{@code >=} are
    /// not treated as top-level Sass binary operators. A second range operator
    /// is allowed only when it matches the first operator's direction.
    ///
    /// @param buffer the interpolation receiving the parenthesized condition
    private void mediaInParens(InterpolationBuffer buffer) {
        if (!scanner.scan('(')) {
            throw scanner.error("expected media condition in parentheses.");
        }
        buffer.append('(');
        whitespace(true);

        if (scanner.peek() == '(') {
            mediaInParens(buffer);
            whitespace(true);
            if (scanIdentifier("and")) {
                buffer.append(" and ");
                expectWhitespace(true);
                mediaLogicSequence(buffer, "and");
            } else if (scanIdentifier("or")) {
                buffer.append(" or ");
                expectWhitespace(true);
                mediaLogicSequence(buffer, "or");
            }
        } else if (scanIdentifier("not")) {
            buffer.append("not ");
            expectWhitespace(true);
            mediaOrInterp(buffer);
        } else {
            var expressionBefore = expressionUntilComparison();
            buffer.add(expressionBefore, expressionBefore.span());
            if (scanner.scan(':')) {
                whitespace(true);
                buffer.append(':');
                buffer.append(' ');
                var expressionAfter = expression();
                buffer.add(expressionAfter, expressionAfter.span());
            } else {
                var next = scanner.peek();
                if (next == '<' || next == '>' || next == '=') {
                    buffer.append(' ');
                    buffer.append((char) scanner.read());
                    if ((next == '<' || next == '>') && scanner.scan('=')) {
                        buffer.append('=');
                    }
                    buffer.append(' ');

                    whitespace(true);
                    var expressionMiddle = expressionUntilComparison();
                    buffer.add(expressionMiddle, expressionMiddle.span());

                    // Range form: only the same-direction comparison may repeat.
                    if ((next == '<' || next == '>') && scanner.scan(next)) {
                        buffer.append(' ');
                        buffer.append((char) next);
                        if (scanner.scan('=')) {
                            buffer.append('=');
                        }
                        buffer.append(' ');

                        whitespace(true);
                        var expressionAfter = expressionUntilComparison();
                        buffer.add(expressionAfter, expressionAfter.span());
                    }
                }
            }
        }

        scanner.expect(')');
        whitespace(false);
        buffer.append(')');
    }

    /// Parses an {@code @supports} rule in a stylesheet or style-rule context.
    ///
    /// @param start   the scanner state at the leading {@code @}
    /// @param context the enclosing statement context
    /// @return the parsed supports rule
    /// @throws ParseException if the condition is missing or the rule is disallowed here
    private SupportsRule supportsRule(ScannerState start, StatementContext context) {
        switch (context) {
            case DECLARATION -> throw scanner.error(
                    "Supports rules may not be used within nested declarations.",
                    start.position(),
                    scanner.position() - start.position()
            );
            case FONT_FACE -> throw scanner.error(
                    "Supports rules may not be used within @font-face rules.",
                    start.position(),
                    scanner.position() - start.position()
            );
            case FUNCTION -> throw scanner.error(
                    "Supports rules may not be used within functions.",
                    start.position(),
                    scanner.position() - start.position()
            );
            default -> {
            }
        }

        whitespace(true);
        var condition = supportsCondition();
        whitespace(true);
        var children = statementBlock(context);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new SupportsRule(condition, children, span);
    }

    /// Parses the boolean expression at the start of a supports condition.
    ///
    /// @return the parsed supports condition
    private SupportsCondition supportsCondition() {
        var start = scanner.state();
        whitespace(true);
        if (scanIdentifier("not")) {
            whitespace(true);
            var condition = supportsConditionInParens();
            return new SupportsNegation(condition, scanner.spanFrom(start));
        }

        return supportsOperationRest(supportsConditionInParens(), start);
    }

    /// Parses a homogeneous sequence of boolean operations after one operand.
    ///
    /// @param left the already parsed left operand
    /// @param start the start of the complete condition
    /// @return the original operand or the parsed operation tree
    private SupportsCondition supportsOperationRest(
            SupportsCondition left,
            ScannerState start
    ) {
        whitespace(true);
        var operatorStart = scanner.state();
        @Nullable SupportsBooleanOperator operator = scanSupportsOperator();
        if (operator == null) {
            scanner.restore(operatorStart);
            return left;
        }

        var result = left;
        while (true) {
            whitespace(true);
            var right = supportsConditionInParens();
            result = new SupportsOperation(result, right, operator, scanner.spanFrom(start));
            whitespace(true);
            operatorStart = scanner.state();
            @Nullable SupportsBooleanOperator nextOperator = scanSupportsOperator();
            if (nextOperator == null) {
                scanner.restore(operatorStart);
                return result;
            }
            if (nextOperator != operator) {
                // dart-sass reports the expected continuing operator rather than a
                // generic mixed-operator message (e.g. and then or → Expected "and").
                throw scanner.error(
                        "Expected \"" + operator.cssText() + "\".",
                        operatorStart.position(),
                        scanner.position() - operatorStart.position()
                );
            }
        }
    }

    /// Scans one supports boolean operator at the current position.
    ///
    /// @return the scanned operator, or {@code null} when none begins here
    private @Nullable SupportsBooleanOperator scanSupportsOperator() {
        if (scanIdentifier("or")) {
            return SupportsBooleanOperator.OR;
        }
        if (scanIdentifier("and")) {
            return SupportsBooleanOperator.AND;
        }
        return null;
    }

    /// Parses a parenthesized declaration, function, opaque condition, or
    /// interpolated condition.
    ///
    /// @return the parsed condition
    private SupportsCondition supportsConditionInParens() {
        var start = scanner.state();
        if (lookingAtInterpolatedIdentifier()) {
            var nameStart = scanner.state();
            var name = interpolatedIdentifier();
            @Nullable String plainName = name.asPlain();
            if (plainName != null && plainName.equalsIgnoreCase("not")) {
                throw scanner.error("\"not\" is not a valid identifier here.", name.span());
            }
            if (scanner.scan('(')) {
                // Function-form supports conditions may contain raw {@code ;}
                // tokens inside the argument list (see anything/symbols).
                var arguments = interpolatedDeclarationValue(true, true, true);
                scanner.expect(')');
                return new SupportsFunction(name, arguments, scanner.spanFrom(start));
            }

            scanner.restore(nameStart);
            if (scanner.peek() == '#' && scanner.peek(1) == '{') {
                // Pure interpolation forms such as {@code @supports #{$cond}}
                // are valid. Trailing identifier text after the interpolation
                // ({@code @supports #{a}b}) is not a supports condition.
                var interpolation = supportsInterpolationCondition();
                if (lookingAtInterpolatedIdentifier()) {
                    throw scanner.error("Expected @supports condition.");
                }
                return interpolation;
            }
            throw scanner.error("Expected @supports condition.");
        }

        scanner.expect('(');
        whitespace(true);
        if (scanner.peek() == ')') {
            throw scanner.error("Expected @supports condition.");
        }
        if (scanner.peek() == '#' && scanner.peek(1) == '{') {
            var interpolationStart = scanner.state();
            var interpolation = supportsInterpolationCondition();
            var result = supportsOperationRest(interpolation, start);
            if (result != interpolation) {
                scanner.expect(')');
                return result;
            }
            scanner.restore(interpolationStart);
        }
        if (scanIdentifier("not")) {
            whitespace(true);
            var condition = supportsConditionInParens();
            scanner.expect(')');
            return new SupportsNegation(condition, scanner.spanFrom(start));
        }
        if (scanner.peek() == '(') {
            var condition = supportsCondition();
            whitespace(true);
            scanner.expect(')');
            return condition;
        }
        return supportsDeclarationOrAnything(start);
    }

    /// Parses one complete SassScript interpolation as a supports condition.
    ///
    /// @return the interpolated condition
    private SupportsInterpolation supportsInterpolationCondition() {
        var start = scanner.state();
        scanner.expect("#{");
        whitespace(true);
        var expression = expression();
        scanner.expect('}');
        return new SupportsInterpolation(expression, scanner.spanFrom(start));
    }

    /// Parses a declaration condition and falls back to an opaque condition
    /// when the contents do not contain a declaration colon.
    ///
    /// @param start the source position before the opening parenthesis
    /// @return the parsed condition
    private SupportsCondition supportsDeclarationOrAnything(ScannerState start) {
        var contentsStart = scanner.state();
        var colonConsumed = false;
        try {
            var name = expression();
            whitespace(true);
            if (!scanner.scan(':')) {
                // dart-sass {@code expectChar(':')} uses a lowercase "expected".
                throw scanner.error("expected \":\".");
            }
            colonConsumed = true;

            boolean customProperty = name instanceof StringExpression string
                    && isPlainCustomPropertyName(string);
            SassExpression value;
            if (customProperty) {
                // Keep post-colon spaces and comments in the raw value so
                // serialization can emit dart-sass forms such as `--a: b` and
                // `--a: /**/ b`. Reject a completely empty value (`--a:`)
                // with dart-sass's "Expected token." diagnostic.
                var rawValue = interpolatedDeclarationValue(false, true);
                value = new StringExpression(rawValue, false);
            } else {
                whitespace(true);
                value = expression();
            }
            whitespace(true);
            scanner.expect(')');
            return new SupportsDeclaration(
                    name,
                    value,
                    customProperty,
                    scanner.spanFrom(start)
            );
        } catch (ParseException failure) {
            scanner.restore(contentsStart);
            if (colonConsumed) {
                throw failure;
            }
            var buffer = new InterpolationBuffer();
            buffer.add(interpolatedIdentifier());
            // Supports "anything" conditions may contain top-level semicolons
            // (dart-sass {@code (a !&$ZH()&;*{&A}_=-+#/><)}).
            buffer.add(interpolatedDeclarationValue(true, true, true));
            var contents = buffer.interpolation(scanner.spanFrom(contentsStart));
            var rawText = scanner.substring(contentsStart.position(), scanner.position());
            if (containsTopLevelColon(rawText)) {
                throw failure;
            }
            scanner.expect(')');
            return new SupportsAnything(contents, scanner.spanFrom(start));
        }
    }

    /// Parses a statement block while recording a control-directive context.
    ///
    /// @param context the statement forms permitted in the block
    /// @return the parsed child statements
    private ArrayList<SassStatement> controlDirectiveBlock(StatementContext context) {
        controlDirectiveDepth++;
        try {
            return statementBlock(context);
        } finally {
            controlDirectiveDepth--;
        }
    }

    /// Returns whether a parsed name is a custom-property name.
    ///
    /// @param expression the parsed name expression
    /// @return whether its plain text begins with two hyphens
    private static boolean isPlainCustomPropertyName(StringExpression expression) {
        return !expression.hasQuotes()
                && expression.text().initialPlain().startsWith("--");
    }

    /// Returns whether raw supports contents contain a colon outside nested syntax.
    ///
    /// @param text the source text inside the supports parentheses
    /// @return whether a top-level declaration colon occurs
    private static boolean containsTopLevelColon(String text) {
        var depth = 0;
        var quote = 0;
        var escaped = false;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
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
            switch (character) {
                case '(', '[', '{' -> depth++;
                case ')', ']', '}' -> {
                    if (depth > 0) {
                        depth--;
                    }
                }
                case ':' -> {
                    if (depth == 0) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    /// Parses an {@code @if} rule and its trailing {@code @else if} / {@code @else} branches.
    ///
    /// @param start   the scanner state at the leading {@code @}
    /// @param context the enclosing statement context
    /// @return the if rule
    private IfRule ifRule(ScannerState start, StatementContext context) {
        whitespace(true);
        var clauses = new ArrayList<IfClause>();
        clauses.add(new IfClause(expression(), controlDirectiveBlock(context)));
        whitespaceWithoutComments(false);

        @Nullable ElseClause lastClause = null;
        while (scanElse()) {
            whitespace(false);
            if (scanIdentifier("if")) {
                whitespace(true);
                clauses.add(new IfClause(expression(), controlDirectiveBlock(context)));
                whitespaceWithoutComments(false);
            } else {
                lastClause = new ElseClause(controlDirectiveBlock(context));
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
        var children = controlDirectiveBlock(context);
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
        var children = controlDirectiveBlock(context);
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
        var children = controlDirectiveBlock(context);
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
        whitespace(true);
        var originalName = identifier(false, false);
        if (originalName.startsWith("--")) {
            throw scanner.error(
                    "Sass @mixin names beginning with -- are forbidden for "
                            + "forward-compatibility with plain CSS mixins.\n"
                            + "\n"
                            + "For details, see https://sass-lang.com/d/css-function-mixin"
            );
        }
        whitespace(true);
        var parameters = scanner.peek() == '('
                ? parameterList()
                : ParameterList.empty(scanner.source().span(scanner.position(), scanner.position()));
        if (inMixin || inContentBlock) {
            throw scanner.error("Mixins may not contain mixin declarations.");
        }
        if (controlDirectiveDepth > 0) {
            throw scanner.error("Mixins may not be declared in control directives.");
        }
        whitespace(true);
        var previousInMixin = inMixin;
        inMixin = true;
        ArrayList<SassStatement> children;
        try {
            // Mixin bodies accept style-rule children so bare declarations can be
            // applied when the mixin is included inside a style rule.
            children = statementBlock(StatementContext.STYLE_RULE);
        } finally {
            inMixin = previousInMixin;
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
        whitespace(true);
        var nameStart = scanner.state();
        var originalName = identifier(false, false);
        var nameSpan = scanner.spanFrom(nameStart);
        if (originalName.startsWith("--")) {
            throw scanner.error("Function names may not begin with --.", nameSpan);
        }
        // Reserved CSS special-function names cannot be user-defined Sass
        // functions (dart-sass: expression/url/and/or/not/element, and type).
        // Case-sensitive reject; mixed-case expression/url/element only warn.
        rejectReservedFunctionName(originalName, nameSpan);
        whitespace(true);
        var parameters = parameterList();
        if (inMixin || inContentBlock) {
            // dart-sass wording for functions nested in mixins.
            throw scanner.error("Mixins may not contain function declarations.");
        }
        if (controlDirectiveDepth > 0) {
            throw scanner.error("Functions may not be declared in control directives.");
        }
        whitespace(true);
        var children = statementBlock(StatementContext.FUNCTION);
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new FunctionRule(originalName, parameters, children, span);
    }

    /// Rejects or deprecates Sass {@code @function} names reserved by CSS.
    ///
    /// Exact lowercase {@code expression}/{@code url}/{@code and}/{@code or}/
    /// {@code not}/element forms are errors. Case variants of expression, url,
    /// and element emit the {@code function-name} deprecation instead.
    /// {@code type} is always reserved regardless of case.
    ///
    /// @param originalName the declared function name as written
    /// @param nameSpan     the name's source span
    private void rejectReservedFunctionName(String originalName, SourceSpan nameSpan) {
        var lower = originalName.toLowerCase(java.util.Locale.ROOT);
        if ("type".equals(lower)) {
            throw scanner.error(
                    "This name is reserved for the plain-CSS function.",
                    nameSpan
            );
        }
        // Exact spelling matches are hard errors (case-sensitive for these five).
        if ("expression".equals(originalName)
                || "url".equals(originalName)
                || "and".equals(originalName)
                || "or".equals(originalName)
                || "not".equals(originalName)
                || isVendorElementFunctionName(originalName)) {
            throw scanner.error("Invalid function name.", nameSpan);
        }
        // Mixed-case expression/url/element names are deprecated but still legal.
        if ("expression".equals(lower)
                || "url".equals(lower)
                || isVendorElementFunctionName(lower)) {
            addParseTimeWarning(new Diagnostic(
                    DiagnosticSeverity.DEPRECATION,
                    "Custom functions with this name are deprecated and will be "
                            + "removed in a future\n"
                            + "release. Please choose a different name.\n"
                            + "More info: https://sass-lang.com/d/function-name",
                    nameSpan,
                    "function-name"
            ));
        }
    }

    /// Returns whether {@code name} is {@code element} or a vendor-prefixed form.
    ///
    /// @param lowerName the lowercased function name
    /// @return whether the name is reserved as a CSS element() function
    private static boolean isVendorElementFunctionName(String lowerName) {
        if ("element".equals(lowerName)) {
            return true;
        }
        // Vendor forms such as {@code -moz-element} or {@code -a-element}.
        if (lowerName.length() > 9 && lowerName.charAt(0) == '-') {
            int second = lowerName.indexOf('-', 1);
            return second > 1 && "element".equals(lowerName.substring(second + 1));
        }
        return false;
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
        // Namespaced private includes are a parse-time error (dart-sass).
        if (namespace != null
                && (originalName.startsWith("-") || originalName.startsWith("_"))) {
            throw scanner.error(
                    "Private members can't be accessed from outside their modules."
            );
        }
        // Reject literal `--` includes even when a mixin was declared as `__…`
        // (underscore/hyphen normalization would otherwise resolve the call).
        if (originalName.startsWith("--")) {
            throw scanner.error(
                    "Sass @mixin names beginning with -- are forbidden for "
                            + "forward-compatibility with plain CSS mixins.\n"
                            + "\n"
                            + "For details, see https://sass-lang.com/d/css-function-mixin"
            );
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
        @Nullable ParameterList contentParameters = null;
        if (scanIdentifier("using")) {
            whitespace(true);
            contentParameters = parameterList();
            whitespace(true);
        }
        @Nullable ContentBlock content = null;
        if (scanner.peek() == '{') {
            var contentStart = scanner.state();
            var parameters = contentParameters == null
                    ? ParameterList.empty(scanner.source().span(
                            contentStart.position(),
                            contentStart.position()
                    ))
                    : contentParameters;
            // Content blocks accept style-rule children even when the include is
            // written at the stylesheet root.
            var previousInContentBlock = inContentBlock;
            inContentBlock = true;
            ArrayList<SassStatement> children;
            try {
                children = statementBlock(StatementContext.STYLE_RULE);
            } finally {
                inContentBlock = previousInContentBlock;
            }
            content = new ContentBlock(
                    parameters,
                    children,
                    scanner.spanFrom(contentStart)
            );
        } else {
            if (contentParameters != null) {
                // dart-sass reports the missing brace rather than a using()-specific
                // content-block message (sass-spec missing_block).
                throw scanner.error("expected \"{\".");
            }
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
        } else {
            arguments = ArgumentList.empty(
                    scanner.source().span(scanner.position(), scanner.position())
            );
        }
        // Trailing loud/silent comments after {@code @content()} are allowed
        // (dart-sass); skip them before requiring a statement separator.
        whitespace(true);
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
            // dart-sass uses the generic at-rule rejection outside functions.
            throw scanner.error("This at-rule is not allowed here.");
        }
        whitespace(true);
        var expression = expression();
        expectStatementSeparator();
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new ReturnRule(expression, span);
    }

    /// Parses a `@debug` rule in any statement context.
    ///
    /// @param start the scanner state at the leading `@`
    /// @return the debug rule
    private DebugRule debugRule(ScannerState start) {
        whitespace(true);
        var expression = expression();
        var span = scanner.source().span(
                start.position(),
                scanner.source().generatedEndOffset(expression.span())
        );
        expectStatementSeparator();
        whitespaceWithoutComments(false);
        return new DebugRule(expression, span);
    }

    /// Parses a `@warn` rule in any statement context.
    ///
    /// @param start the scanner state at the leading `@`
    /// @return the warning rule
    private WarnRule warnRule(ScannerState start) {
        whitespace(true);
        var expression = expression();
        var span = scanner.source().span(
                start.position(),
                scanner.source().generatedEndOffset(expression.span())
        );
        expectStatementSeparator();
        whitespaceWithoutComments(false);
        return new WarnRule(expression, span);
    }

    /// Parses an `@error` rule in any statement context.
    ///
    /// @param start the scanner state at the leading `@`
    /// @return the error rule
    private ErrorRule errorRule(ScannerState start) {
        whitespace(true);
        var expression = expression();
        var span = scanner.source().span(
                start.position(),
                scanner.source().generatedEndOffset(expression.span())
        );
        expectStatementSeparator();
        whitespaceWithoutComments(false);
        return new ErrorRule(expression, span);
    }

    /// Parses a legacy Sass `@import` rule.
    ///
    /// Dynamic Sass imports are forbidden in control directives and mixin
    /// declarations. Static CSS imports remain valid in those contexts.
    ///
    /// @param start   the scanner state at the leading `@`
    /// @param context the surrounding statement context
    /// @return the import rule
    private ImportRule importRule(ScannerState start, StatementContext context) {
        if (context != StatementContext.ROOT && context != StatementContext.STYLE_RULE) {
            throw scanner.error("This at-rule is not allowed here.");
        }

        var imports = new ArrayList<SassImport>();
        do {
            whitespace(true);
            var argumentStart = scanner.state();
            if (scanner.peek() == 'u' || scanner.peek() == 'U') {
                var beforeUrl = scanner.state();
                var name = identifier(false, false);
                if (name.equalsIgnoreCase("url")) {
                    var url = importUrl(argumentStart, name);
                    imports.add(staticImport(url, argumentStart));
                } else if (!plainCss) {
                    // {@code @import unquoted} begins with {@code u} but is not url().
                    scanner.restore(beforeUrl);
                    if (controlDirectiveDepth > 0 || inMixin) {
                        throw scanner.error("This at-rule is not allowed here.");
                    }
                    var urlStart = scanner.state();
                    var path = unquotedImportUrl();
                    var dynamic = new DynamicImport(
                            path,
                            scanner.spanFrom(urlStart)
                    );
                    imports.add(dynamic);
                    addParseTimeWarning(new Diagnostic(
                            DiagnosticSeverity.DEPRECATION,
                            "Sass @import rules are deprecated and will be removed in "
                                    + "Dart Sass 3.0.0.\n\n"
                                    + "More info and automated migrator: "
                                    + "https://sass-lang.com/d/import",
                            dynamic.span(),
                            "import"
                    ));
                } else {
                    throw scanner.error("Expected string or url().");
                }
            } else if (scanner.peek() == '\'' || scanner.peek() == '"') {
                var urlStart = scanner.state();
                var url = string();
                var urlEnd = scanner.state();
                var rawUrl = Interpolation.plain(
                        scanner.substring(urlStart.position(), urlEnd.position()),
                        scanner.spanFrom(urlStart, urlEnd)
                );
                whitespace(true);
                if (plainCss || !atImportArgumentEnd() || isPlainImportUrl(url)) {
                    imports.add(staticImport(rawUrl, argumentStart));
                } else {
                    if (controlDirectiveDepth > 0 || inMixin) {
                        throw scanner.error("This at-rule is not allowed here.");
                    }
                    var dynamic = new DynamicImport(url, rawUrl.span());
                    imports.add(dynamic);
                    addParseTimeWarning(new Diagnostic(
                            DiagnosticSeverity.DEPRECATION,
                            "Sass @import rules are deprecated and will be removed in "
                                    + "Dart Sass 3.0.0.\n\n"
                                    + "More info and automated migrator: "
                                    + "https://sass-lang.com/d/import",
                            dynamic.span(),
                            "import"
                    ));
                }
            } else if (!plainCss && lookingAtUnquotedImportUrl()) {
                // Legacy unquoted load paths ({@code @import unquoted, sub/x}).
                if (controlDirectiveDepth > 0 || inMixin) {
                    throw scanner.error("This at-rule is not allowed here.");
                }
                var urlStart = scanner.state();
                var url = unquotedImportUrl();
                var rawUrl = Interpolation.plain(url, scanner.spanFrom(urlStart));
                var dynamic = new DynamicImport(url, rawUrl.span());
                imports.add(dynamic);
                addParseTimeWarning(new Diagnostic(
                        DiagnosticSeverity.DEPRECATION,
                        "Sass @import rules are deprecated and will be removed in "
                                + "Dart Sass 3.0.0.\n\n"
                                + "More info and automated migrator: "
                                + "https://sass-lang.com/d/import",
                        dynamic.span(),
                        "import"
                ));
            } else if (fromIndented && !plainCss && atImportArgumentEnd()) {
                // Indented {@code @import} / {@code @import } with no URL is a
                // dynamic import of the empty path (reloads the current file).
                if (controlDirectiveDepth > 0 || inMixin) {
                    throw scanner.error("This at-rule is not allowed here.");
                }
                var emptySpan = scanner.spanFrom(argumentStart);
                var dynamic = new DynamicImport("", emptySpan);
                imports.add(dynamic);
                addParseTimeWarning(new Diagnostic(
                        DiagnosticSeverity.DEPRECATION,
                        "Sass @import rules are deprecated and will be removed in "
                                + "Dart Sass 3.0.0.\n\n"
                                + "More info and automated migrator: "
                                + "https://sass-lang.com/d/import",
                        dynamic.span(),
                        "import"
                ));
            } else {
                throw scanner.error("Expected string or url().");
            }
            whitespace(true);
            // Plain CSS forbids multi-argument {@code @import "a", "b"} lists;
            // dart-sass reports expected ";" at the comma.
            if (plainCss && scanner.peek() == ',') {
                throw scanner.error("expected \";\".");
            }
        } while (scanner.scan(','));

        expectStatementSeparator();
        var span = scanner.spanFrom(start);
        whitespaceWithoutComments(false);
        return new ImportRule(imports, span);
    }

    /// Parses a static CSS `url()` token used by an import.
    ///
    /// @param start the position before the function name
    /// @param name  the parsed function name
    /// @return the interpolated URL token
    private Interpolation importUrl(ScannerState start, String name) {
        @Nullable Interpolation raw = tryInterpolatedUrlContents(start, name);
        if (raw != null) {
            return raw;
        }

        scanner.expect('(');
        whitespace(true);
        if (scanner.peek() != '\'' && scanner.peek() != '"') {
            throw scanner.error("Expected URL.");
        }
        var buffer = new InterpolationBuffer();
        buffer.append(name);
        buffer.append('(');
        buffer.add(interpolatedStringToken());
        whitespace(true);
        scanner.expect(')');
        buffer.append(')');
        return buffer.interpolation(scanner.spanFrom(start));
    }

    /// Parses the optional modifiers following a static import URL.
    ///
    /// A top-level `supports()` modifier is represented structurally so its
    /// SassScript values use the same evaluation rules as `@supports`. Other
    /// modifiers remain raw interpolations because their CSS grammars are
    /// backend-independent and may contain arbitrary future syntax.
    ///
    /// @param url the already parsed URL token
    /// @param start the position before the complete import argument
    /// @return the complete static import
    private StaticImport staticImport(Interpolation url, ScannerState start) {
        whitespace(true);
        @Nullable Interpolation modifiers = tryImportModifiers();
        // Store the complete modifier text in modifiersBeforeSupports. Supports
        // conditions with SassScript are embedded as expression interpolation
        // parts and evaluated through performInterpolation.
        return new StaticImport(
                url,
                modifiers,
                null,
                null,
                scanner.spanFrom(start)
        );
    }

    /// Consumes optional import modifiers after a static import URL.
    ///
    /// Matches dart-sass {@code tryImportModifiers}: bare identifiers become
    /// media types; {@code supports(...)} and other functions are preserved;
    /// a comma after a bare identifier continues a media-query list (not a new
    /// import argument); a comma after a function leaves the comma for the
    /// multi-import loop.
    ///
    /// @return the modifiers interpolation, or {@code null} when none are present
    private @Nullable Interpolation tryImportModifiers() {
        if (!lookingAtInterpolatedIdentifier() && scanner.peek() != '(') {
            return null;
        }

        var start = scanner.state();
        var buffer = new InterpolationBuffer();
        while (true) {
            if (lookingAtInterpolatedIdentifier()) {
                if (!buffer.isEmpty()) {
                    buffer.append(' ');
                }
                var identifier = interpolatedIdentifier();
                buffer.add(identifier);
                @Nullable String name = identifier.asPlain() == null
                        ? null
                        : identifier.asPlain().toLowerCase(Locale.ROOT);
                if (name != null && !name.equals("and") && scanner.scan('(')) {
                    if (name.equals("supports")) {
                        whitespace(true);
                        var query = importSupportsCondition();
                        if (!(query instanceof SupportsDeclaration)) {
                            buffer.append('(');
                        }
                        appendImportSupportsQuery(buffer, query);
                        if (!(query instanceof SupportsDeclaration)) {
                            buffer.append(')');
                        }
                    } else {
                        buffer.append('(');
                        buffer.add(interpolatedDeclarationValue(true, true, true));
                        buffer.append(')');
                    }
                    scanner.expect(')');
                    whitespace(false);
                } else {
                    whitespace(false);
                    if (scanner.scan(',')) {
                        buffer.append(", ");
                        buffer.add(mediaQueryList());
                        return buffer.interpolation(scanner.spanFrom(start));
                    }
                }
            } else if (scanner.peek() == '(') {
                if (!buffer.isEmpty()) {
                    buffer.append(' ');
                }
                buffer.add(mediaQueryList());
                return buffer.interpolation(scanner.spanFrom(start));
            } else {
                return buffer.isEmpty()
                        ? null
                        : buffer.interpolation(scanner.spanFrom(start));
            }
        }
    }

    /// Appends one import {@code supports()} condition for later evaluation.
    ///
    /// Declaration conditions are written without surrounding parentheses so
    /// the emitted CSS matches {@code supports(name: value)}. Other conditions
    /// are written as their parenthesized CSS form via nested expression parts.
    ///
    /// @param buffer the modifier buffer
    /// @param query  the parsed supports condition
    private void appendImportSupportsQuery(
            InterpolationBuffer buffer,
            SupportsCondition query
    ) {
        if (query instanceof SupportsDeclaration declaration) {
            // Declaration conditions include their own parentheses so the buffer
            // text is {@code supports(name: value)} without an extra wrapper.
            buffer.append('(');
            buffer.add(declaration.name(), declaration.name().span());
            if (declaration.customProperty()) {
                buffer.append(':');
                if (declaration.value() instanceof StringExpression string
                        && string.text().isPlain()) {
                    buffer.add(string.text());
                } else {
                    buffer.add(declaration.value(), declaration.value().span());
                }
            } else {
                buffer.append(": ");
                buffer.add(declaration.value(), declaration.value().span());
            }
            buffer.append(')');
            return;
        }
        appendSupportsConditionParts(buffer, query);
    }

    /// Appends a nested supports condition tree as interpolatable parts.
    ///
    /// @param buffer the destination buffer
    /// @param query  the condition to serialize
    private void appendSupportsConditionParts(
            InterpolationBuffer buffer,
            SupportsCondition query
    ) {
        if (query instanceof SupportsDeclaration) {
            appendImportSupportsQuery(buffer, query);
            return;
        }
        if (query instanceof SupportsNegation negation) {
            buffer.append("not ");
            appendSupportsConditionParts(buffer, negation.condition());
            return;
        }
        if (query instanceof SupportsOperation operation) {
            appendSupportsConditionParts(buffer, operation.left());
            buffer.append(' ');
            buffer.append(operation.operator().cssText());
            buffer.append(' ');
            appendSupportsConditionParts(buffer, operation.right());
            return;
        }
        if (query instanceof SupportsFunction function) {
            buffer.add(function.name());
            buffer.append('(');
            // Plain argument text from indented open-paren joins may contain
            // newline+indent; CSS emission collapses that to a single space.
            @Nullable String plainArguments = function.arguments().asPlain();
            if (plainArguments != null) {
                buffer.append(collapseImportSupportsWhitespace(plainArguments));
            } else {
                buffer.add(function.arguments());
            }
            buffer.append(')');
            return;
        }
        if (query instanceof SupportsAnything anything) {
            buffer.append('(');
            @Nullable String plainContents = anything.contents().asPlain();
            if (plainContents != null) {
                buffer.append(collapseImportSupportsWhitespace(plainContents));
            } else {
                buffer.add(anything.contents());
            }
            buffer.append(')');
            return;
        }
        if (query instanceof SupportsInterpolation interpolation) {
            buffer.add(interpolation.expression(), interpolation.span());
            return;
        }
        throw scanner.error("Expected @supports condition.");
    }

    /// Parses one static-import `supports()` modifier.
    ///
    /// @return the structured condition inside the modifier
    private SupportsCondition importSupportsModifier() {
        expectIdentifier("supports");
        scanner.expect('(');
        whitespace(true);
        if (scanner.peek() == ')') {
            throw scanner.error("Expected @supports condition.");
        }
        var condition = importSupportsCondition();
        whitespace(true);
        scanner.expect(')');
        return condition;
    }

    /// Parses either an unparenthesized declaration or a regular supports condition.
    ///
    /// CSS import modifiers allow `supports(display: grid)`, while an
    /// `@supports` rule spells the same declaration as `(display: grid)`.
    ///
    /// @return the parsed import supports condition
    private SupportsCondition importSupportsCondition() {
        // Match dart-sass {@code _importSupportsQuery}: after optional {@code not}
        // / parenthesized conditions / function forms, a bare expression must be
        // a declaration {@code name: value} (no fallback to general supports).
        if (scanIdentifier("not")) {
            whitespace(true);
            var start = scanner.state();
            return new SupportsNegation(supportsConditionInParens(), scanner.spanFrom(start));
        }
        if (scanner.peek() == '(') {
            return supportsCondition();
        }
        if (lookingAtInterpolatedIdentifier()) {
            var functionStart = scanner.state();
            var functionName = interpolatedIdentifier();
            if (scanner.scan('(')) {
                var arguments = interpolatedDeclarationValue(true, true, true);
                scanner.expect(')');
                return new SupportsFunction(functionName, arguments, scanner.spanFrom(functionStart));
            }
            scanner.restore(functionStart);
        }
        var start = scanner.state();
        var name = expression();
        whitespace(true);
        scanner.expect(':');
        boolean customProperty = name instanceof StringExpression string
                && isPlainCustomPropertyName(string);
        SassExpression value;
        if (customProperty) {
            // Preserve post-colon whitespace/comments for import supports().
            // dart-sass rejects a completely empty custom-property value
            // ({@code supports(--a:)}) with "Expected token."
            var rawValue = interpolatedDeclarationValue(false, true);
            value = new StringExpression(rawValue, false);
        } else {
            whitespace(true);
            value = expression();
        }
        return new SupportsDeclaration(
                name,
                value,
                customProperty,
                scanner.spanFrom(start)
        );
    }

    /// Returns whether a top-level static-import `supports()` modifier begins here.
    ///
    /// Whitespace between the identifier and opening parenthesis is not valid.
    /// The scanner position is unchanged.
    ///
    /// @return whether the modifier begins at the current position
    private boolean lookingAtImportSupportsModifier() {
        var start = scanner.state();
        boolean result = scanIdentifier("supports") && scanner.peek() == '(';
        scanner.restore(start);
        return result;
    }

    /// Collapses line-break whitespace from indented open-paren joins inside a
    /// plain import {@code supports()} body.
    ///
    /// Multiple spaces that do not cross a line break are preserved so silent
    /// comments and deliberate spacing still match dart-sass. Only newline
    /// (and following indent) sequences are reduced to a single space.
    ///
    /// @param text the raw condition or argument text
    /// @return text with line-break whitespace folded to one space
    private static String collapseImportSupportsWhitespace(String text) {
        if (text.indexOf('\n') < 0
                && text.indexOf('\r') < 0
                && text.indexOf('\f') < 0) {
            return text;
        }
        var result = new StringBuilder(text.length());
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (character == '\n' || character == '\r' || character == '\f') {
                if (character == '\r'
                        && index + 1 < text.length()
                        && text.charAt(index + 1) == '\n') {
                    index++;
                }
                while (index + 1 < text.length()) {
                    var next = text.charAt(index + 1);
                    if (next == ' ' || next == '\t') {
                        index++;
                        continue;
                    }
                    break;
                }
                result.append(' ');
                continue;
            }
            result.append(character);
        }
        return result.toString();
    }

    /// Returns whether the current position terminates one import argument.
    ///
    /// @return whether no modifier begins here
    private boolean atImportArgumentEnd() {
        return switch (scanner.peek()) {
            case CssCharacters.END_OF_INPUT, ',', ';', '}' -> true;
            default -> false;
        };
    }

    /// Returns whether an unquoted Sass load path may begin here.
    ///
    /// @return whether {@link #unquotedImportUrl()} can consume a path
    private boolean lookingAtUnquotedImportUrl() {
        return lookingAtInterpolatedIdentifier() || scanner.peek() == '.'
                || scanner.peek() == '/' || scanner.peek() == '\\';
    }

    /// Consumes a legacy unquoted {@code @import} load path.
    ///
    /// Accepts dotted segments and path separators such as {@code sub/unquoted}
    /// and {@code ./foo}.
    ///
    /// @return the decoded load path text
    private String unquotedImportUrl() {
        var start = scanner.state();
        while (true) {
            var next = scanner.peek();
            if (next == '/' || next == '\\') {
                scanner.read();
                continue;
            }
            if (next == '.'
                    && (scanner.peek(1) == '/'
                    || scanner.peek(1) == '\\'
                    || scanner.peek(1) == '.')) {
                scanner.read();
                continue;
            }
            if (lookingAtInterpolatedIdentifier()) {
                // Consume one path segment without expanding interpolation.
                identifier(false, false);
                continue;
            }
            break;
        }
        if (scanner.position() == start.position()) {
            throw scanner.error("Expected string or url().");
        }
        return scanner.substring(start.position());
    }

    /// Returns whether a quoted URL denotes a plain CSS import.
    ///
    /// @param url the decoded URL contents
    /// @return whether the URL must not be loaded as Sass
    private static boolean isPlainImportUrl(String url) {
        return url.endsWith(".css")
                || url.startsWith("//")
                || url.startsWith("http://")
                || url.startsWith("https://");
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
            // Nested / non-root contexts use the generic at-rule rejection text
            // (dart-sass stylesheet parser), not the root-only module message.
            throw scanner.error("This at-rule is not allowed here.");
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
            throw scanner.error("This at-rule is not allowed here.");
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
                // Empty {@code $} without a following name is invalid.
                if (!lookingAtIdentifier(1) && scanner.peek(1) != '-' && scanner.peek(1) != '\\') {
                    throw scanner.error("Expected variable, mixin, or function name");
                }
                variables.add(variableName());
            } else if (lookingAtIdentifier()) {
                mixinsAndFunctions.add(identifier(true, false));
            } else {
                throw scanner.error("Expected variable, mixin, or function name");
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
                        nameSpan
                );
            }
            whitespace(true);
            scanner.expect(':');
            whitespace(true);
            var expression = expressionUntilComma();
            var variableEnd = scanner.source().generatedEndOffset(expression.span());
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
            // ($a: b,,) — empty entry after a comma is "expected )" (dart-sass).
            if (scanner.peek() != '$') {
                throw scanner.error("expected \")\".");
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
    /// Scheme-based URLs such as {@code scheme:bar} use the scheme-specific
    /// part basename ({@code bar}), matching dart-sass {@code namespaceForPath}
    /// so unknown schemes fail later at load time rather than at parse time.
    ///
    /// @param url the module URL string
    /// @return the default namespace
    private String defaultNamespace(String url) {
        var path = url;
        if (path.startsWith("sass:")) {
            path = path.substring("sass:".length());
        } else {
            var colon = path.indexOf(':');
            var slash = Math.max(path.indexOf('/'), path.indexOf('\\'));
            // Opaque or hierarchical URL with a non-sass scheme: take the part
            // after the scheme for basename extraction ({@code scheme:bar} →
            // {@code bar}, {@code pkg:foo/bar} → {@code foo/bar}).
            if (colon > 0 && (slash < 0 || colon < slash)) {
                path = path.substring(colon + 1);
                if (path.startsWith("//")) {
                    path = path.substring(2);
                }
            }
        }
        var slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (slash >= 0) {
            path = path.substring(slash + 1);
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
                    "The default namespace \"" + path + "\" is not a valid Sass identifier.\n\n"
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
                                flagSpan
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
                        flagSpan
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
        requirePlainCssText(identifier);
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
        // Plain-CSS @function --name treats the result descriptor like a custom
        // property: raw CSS tokens with only #{...} interpolation evaluated.
        @Nullable String plainName = name.asPlain();
        var cssFunctionResult = inPlainCssFunction
                && plainName != null
                && plainName.equalsIgnoreCase("result");
        if (customProperty || cssFunctionResult) {
            if (customProperty && declarationsOnly) {
                throw scanner.error(
                        "Declarations whose names begin with \"--\" may not be nested.",
                        name.span()
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
            requirePlainCssText(rawValue);
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
            if (plainCss) {
                throw scanner.error("Nested declarations aren't allowed in plain CSS.");
            }
            return nestedDeclaration(name, null, start);
        }

        var couldBeSelector = !declarationsOnly
                && postColonWhitespace.isEmpty()
                && lookingAtInterpolatedIdentifier();
        var beforeDeclaration = scanner.state();
        SassExpression value;
        try {
            value = expression();
            // Children may follow optional whitespace after the value.
            var afterValue = scanner.state();
            whitespace(false);
            if (scanner.peek() == '{') {
                if (couldBeSelector) {
                    // Force the ambiguous {@code a:b {} } form back to selector
                    // parsing, matching dart-sass.
                    scanner.restore(afterValue);
                    expectStatementSeparator();
                }
            } else {
                scanner.restore(afterValue);
                if (!atEndOfStatement()) {
                    expectStatementSeparator();
                }
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

        var afterValue = scanner.state();
        whitespace(false);
        if (scanner.peek() == '{') {
            if (plainCss) {
                throw scanner.error("Nested declarations aren't allowed in plain CSS.");
            }
            return nestedDeclaration(name, value, start);
        }
        scanner.restore(afterValue);
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

    /// Rejects one Sass-only construct when parsing plain CSS.
    ///
    /// @param message the parse failure message
    private void rejectPlainCss(String message) {
        if (plainCss) {
            throw scanner.error(message);
        }
    }

    /// Requires an interpolation to contain only literal text in plain CSS.
    ///
    /// @param interpolation the parsed text
    private void requirePlainCssText(Interpolation interpolation) {
        if (plainCss && interpolation.asPlain() == null) {
            throw scanner.error("Interpolation isn't allowed in plain CSS.", interpolation.span());
        }
    }

    /// Removes trailing whitespace and CSS comments from a plain interpolation.
    ///
    /// Used for {@code @-moz-document}, whose dart-sass parser treats comments
    /// after the last function argument as inter-token whitespace rather than
    /// prelude text.
    ///
    /// @param value the at-rule prelude
    /// @return the prelude without trailing comments or spaces
    private static Interpolation stripTrailingCommentsAndWhitespace(Interpolation value) {
        @Nullable String plain = value.asPlain();
        if (plain == null || plain.isEmpty()) {
            return value;
        }
        var end = plain.length();
        while (end > 0) {
            var character = plain.charAt(end - 1);
            if (character == ' ' || character == '\t' || character == '\n'
                    || character == '\r' || character == '\f') {
                end--;
                continue;
            }
            if (end >= 2
                    && plain.charAt(end - 2) == '*'
                    && plain.charAt(end - 1) == '/') {
                var open = plain.lastIndexOf("/*", end - 2);
                if (open < 0) {
                    break;
                }
                end = open;
                continue;
            }
            break;
        }
        if (end == plain.length()) {
            return value;
        }
        return Interpolation.plain(plain.substring(0, end), value.span());
    }

    /// Rejects Sass variables and interpolation tokens before parsing plain CSS.
    ///
    /// Quoted strings may contain dollar signs, while loud comments are ignored.
    /// Interpolation syntax remains forbidden within quoted strings because Sass
    /// would otherwise treat it as executable input.
    private void validatePlainCssSource() {
        var content = scanner.source().content();
        for (var index = 0; index < content.length(); index++) {
            var character = content.charAt(index);
            if (character == '/'
                    && index + 1 < content.length()
                    && content.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < content.length()
                        && (content.charAt(index) != '*'
                        || content.charAt(index + 1) != '/')) {
                    index++;
                }
                index++;
                continue;
            }
            if (character == '\\') {
                index++;
                continue;
            }
            if (character == '\'' || character == '"') {
                var quote = character;
                while (++index < content.length()) {
                    character = content.charAt(index);
                    if (character == '\\') {
                        index++;
                    } else if (character == '#'
                            && index + 1 < content.length()
                            && content.charAt(index + 1) == '{') {
                        throw scanner.error(
                                "Interpolation isn't allowed in plain CSS.",
                                index,
                                2
                        );
                    } else if (character == quote) {
                        break;
                    }
                }
                continue;
            }
            // Dollar signs may appear in plain-CSS values (for example CSS
            // custom-function result descriptors). Sass variable *declarations*
            // are still rejected during parse via rejectPlainCss.
            if (character == '#'
                    && index + 1 < content.length()
                    && content.charAt(index + 1) == '{') {
                throw scanner.error(
                        "Interpolation isn't allowed in plain CSS.",
                        index,
                        2
                );
            }
        }
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
                // Match dart-sass string_scanner EOF wording for unterminated
                // multi-line comments.
                case CssCharacters.END_OF_INPUT -> throw scanner.error("expected more input.");
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
