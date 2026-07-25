// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.MappedSourceBuilder;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/// Projects indented Sass structure into the braced form consumed by the
/// shared SCSS statement grammar.
///
/// This is a private implementation detail of [IndentedSassParser], not a
/// public Sass→SCSS preprocessing API. The projection is structural: it
/// tracks sibling and child indentation, recognizes nested-property blocks,
/// translates legacy indented mixin syntax, and retains original source spans
/// for indent diagnostics.
@ApiStatus.Internal
@NotNullByDefault
final class IndentedSassStructure {
    /// Marks an interpolation frame in the delimiter stack.
    private static final char INTERPOLATION = '\u0001';
    /// Prevents instantiation.
    private IndentedSassStructure() {
    }

    /// Projects an indented Sass source into SCSS-compatible source text.
    ///
    /// Blank lines are omitted from the generated syntax. Comments are
    /// retained, while statement separators and structural braces are derived
    /// from indentation.
    ///
    /// @param source the original indented Sass source
    /// @return a new source file containing equivalent braced syntax
    /// @throws ParseException if indentation introduces an impossible child,
    /// a block header is missing, or a statement form is malformed
    static SourceFile project(SourceFile source) {
        Objects.requireNonNull(source, "source");
        var lines = logicalLines(source);
        var output = new MappedSourceBuilder(source);
        var blocks = new ArrayDeque<OpenBlock>();
        var childIndents = new HashMap<Integer, Integer>();
        var lastStatementIndent = -1;
        var lastStatementOpened = false;
        var lastWasLoudComment = false;

        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.comment()) {
                closeBlocksBefore(line.indent(), blocks, childIndents, output, line.startOffset());
                appendLine(output, line, line.text());
                output.appendSynthetic("\n", line.endOffset());
                lastStatementIndent = line.indent();
                lastStatementOpened = false;
                lastWasLoudComment = line.text().startsWith("/*");
                continue;
            }

            if (line.indent() > 0
                    && line.indent() > lastStatementIndent
                    && !lastStatementOpened) {
                throw error(
                        source,
                        line,
                        lastWasLoudComment
                                ? org.glavo.scssfx.DiagnosticCode.INDENTED_TEXT_AFTER_COMMENT
                                : org.glavo.scssfx.DiagnosticCode.INDENTED_NESTING_WITHOUT_HEADER
                );
            }
            lastWasLoudComment = false;

            closeBlocksBefore(line.indent(), blocks, childIndents, output, line.startOffset());
            if (blocks.isEmpty() && line.indent() != 0) {
                throw error(source, line, "Top-level Sass statements must start at column zero.");
            }
            if (!blocks.isEmpty() && line.indent() > blocks.peek().indent()) {
                var parentIndent = blocks.peek().indent();
                var expectedIndent = childIndents.putIfAbsent(parentIndent, line.indent());
                if (expectedIndent != null && expectedIndent != line.indent()) {
                    // dart-sass reports "spaces" even though tabs count as two columns.
                    throw error(
                            source,
                            line,
                            "Inconsistent indentation, expected " + expectedIndent + " spaces."
                    );
                }
            }

            var next = nextStatement(lines, index + 1);
            var hasChildren = next != null && next.indent() > line.indent();
            var normalized = normalizeStatement(line.text());
            // Indented syntax forbids packing several statements on one physical
            // line with semicolons (`b: c; d: e`). Report the second statement.
            // Scan the original statement text so the diagnostic column maps
            // correctly when normalizeStatement rewrites mixins/includes.
            int multiStatementOffset = multiStatementSemicolonOffset(line.text());
            if (multiStatementOffset >= 0) {
                int start = line.startOffset() + multiStatementOffset;
                int end = Math.min(start + 1, line.endOffset());
                throw error(
                        source,
                        start,
                        end,
                        "multiple statements on one line are not supported in the indented syntax."
                );
            }
            if (hasChildren) {
                if (!isBlockHeader(normalized) || isCssFunctionResultHeader(normalized)) {
                    // Bare {@code @charset}/{@code @namespace} still require a
                    // string argument on the same line. Indented children must not
                    // be joined; dart-sass reports "Expected string." on the
                    // at-rule line rather than "Nothing may be indented…".
                    if (isBareCharsetOrNamespace(normalized)) {
                        throw error(source, line, "Expected string.");
                    }
                    // dart-sass reports the error on the indented child line.
                    // Plain {@code result:} under CSS {@code @function} is a block
                    // header shape but still forbids children.
                    throw error(
                            source,
                            Objects.requireNonNull(next, "next"),
                            indentedBeneathMessage(normalized)
                    );
                }
                appendLine(output, line, normalized);
                output.appendSynthetic(" {\n", line.endOffset());
                blocks.push(new OpenBlock(line.indent()));
            } else {
                if (isComment(normalized)) {
                    appendLine(output, line, normalized);
                    output.appendSynthetic("\n", line.endOffset());
                } else if (isBlockHeader(normalized)
                        && !looksLikePropertyDeclaration(normalized)
                        && !isBlocklessIncludeWhenEmpty(normalized)
                        && !isBlocklessAtRuleWhenEmpty(normalized)) {
                    // Empty functions, mixins, control directives, and style rules need
                    // braces. Unknown/blockless at-rules such as bare {@code @d} keep a
                    // trailing semicolon ({@code @d;}), matching dart-sass.
                    // {@code @include … using ()} also needs an empty content block.
                    appendLine(output, line, normalized);
                    output.appendSynthetic(" {}", line.endOffset());
                    output.appendSynthetic("\n", line.endOffset());
                } else {
                    appendLine(output, line, normalized);
                    if (!normalized.endsWith(";") && !normalized.endsWith("{")) {
                        output.appendSynthetic(";", line.endOffset());
                    }
                    output.appendSynthetic("\n", line.endOffset());
                }
            }
            lastStatementIndent = line.indent();
            lastStatementOpened = hasChildren;
        }

        while (!blocks.isEmpty()) {
            blocks.pop();
            output.appendSynthetic("}\n", source.length());
        }
        return output.build();
    }

    /// Closes blocks whose headers are siblings of or ancestors of a line.
    ///
    /// @param indent the current line indentation
    /// @param blocks the open block stack
    /// @param childIndents the expected child indentation by block level
    /// @param output the generated source buffer
    /// @param anchor the original offset of the following statement
    private static void closeBlocksBefore(
            int indent,
            Deque<OpenBlock> blocks,
            HashMap<Integer, Integer> childIndents,
            MappedSourceBuilder output,
            int anchor
    ) {
        while (!blocks.isEmpty() && blocks.peek().indent() >= indent) {
            var block = blocks.pop();
            childIndents.remove(block.indent());
            output.appendSynthetic("}\n", anchor);
            if (block.indent() == 0 && indent == 0) {
                output.appendSynthetic("\n", anchor);
            }
        }
    }

    /// Appends one logical line using exact mapping when its spelling is unchanged.
    ///
    /// @param output the mapped output builder
    /// @param line the original logical line
    /// @param text the generated line spelling
    private static void appendLine(
            MappedSourceBuilder output,
            LogicalLine line,
            String text
    ) {
        if (!text.equals(line.text())) {
            if (appendStructuredNormalization(output, line, text)) {
                return;
            }
            output.appendReplacement(text, line.startOffset(), line.endOffset());
            return;
        }
        for (var piece : line.pieces()) {
            if (piece.original()) {
                output.appendOriginal(piece.startOffset(), piece.endOffset());
            } else {
                output.appendReplacement(
                        piece.text(),
                        piece.startOffset(),
                        piece.endOffset()
                );
            }
        }
    }

    /// Appends a normalized shorthand while retaining exact source-backed suffixes.
    ///
    /// @param output the mapped output builder
    /// @param line the original logical line
    /// @param normalized the generated normalized spelling
    /// @return whether a structured normalization was appended
    private static boolean appendStructuredNormalization(
            MappedSourceBuilder output,
            LogicalLine line,
            String normalized
    ) {
        if (line.pieces().size() != 1 || !line.pieces().get(0).original()) {
            return false;
        }
        var original = line.text();
        var base = line.pieces().get(0).startOffset();

        if (original.startsWith("=") || original.startsWith("+")) {
            var remainder = 1;
            while (remainder < original.length()
                    && Character.isWhitespace(original.charAt(remainder))) {
                remainder++;
            }
            var directive = original.charAt(0) == '=' ? "@mixin " : "@include ";
            output.appendReplacement(directive, base, base + remainder);
            output.appendOriginal(base + remainder, base + original.length());
            return true;
        }

        if (original.startsWith("@elseif")) {
            output.appendReplacement(
                    "@else if",
                    base,
                    base + "@elseif".length()
            );
            output.appendOriginal(base + "@elseif".length(), base + original.length());
            return true;
        }

        if (original.startsWith(":")
                && original.length() > 1
                && Character.isLetter(original.charAt(1))) {
            var separator = original.indexOf(' ');
            if (separator > 1) {
                output.appendReplacement(
                        original.substring(1, separator),
                        base,
                        base + separator
                );
                output.appendSynthetic(":", base + separator);
                output.appendOriginal(base + separator, base + original.length());
                return true;
            }
        }

        if (original.startsWith("@import ")
                && normalized.startsWith("@import \"")
                && normalized.endsWith("\"")) {
            var argumentStart = "@import ".length();
            while (argumentStart < original.length()
                    && Character.isWhitespace(original.charAt(argumentStart))) {
                argumentStart++;
            }
            var argumentEnd = original.length();
            while (argumentEnd > argumentStart
                    && Character.isWhitespace(original.charAt(argumentEnd - 1))) {
                argumentEnd--;
            }
            output.appendReplacement(
                    "@import ",
                    base,
                    base + argumentStart
            );
            output.appendSynthetic("\"", base + argumentStart);
            output.appendOriginal(base + argumentStart, base + argumentEnd);
            output.appendSynthetic("\"", base + argumentEnd);
            return true;
        }

        return false;
    }

    /// Returns the next non-comment, nonblank logical line.
    ///
    /// @param lines all logical lines
    /// @param start the first index to inspect
    /// @return the next statement, or {@code null} at end of input
    private static LogicalLine nextStatement(List<LogicalLine> lines, int start) {
        for (var index = start; index < lines.size(); index++) {
            var line = lines.get(index);
            if (!line.comment()) {
                return line;
            }
        }
        return null;
    }

    /// Collects physical lines and combines multiline loud comments.
    ///
    /// @param source the source to inspect
    /// @return logical lines in source order
    /// Lexes the indented source into logical statement lines.
    ///
    /// @param source the indented Sass source
    /// @return logical lines in source order
    static ArrayList<LogicalLine> logicalLines(SourceFile source) {
        var result = new ArrayList<LogicalLine>();
        var line = 0;
        while (line < source.lineCount()) {
            var raw = source.lineText(line);
            var indentation = indentation(raw);
            var indent = indentation.columns();
            var text = raw.substring(indentation.length()).stripTrailing();
            if (text.isBlank()) {
                line++;
                continue;
            }

            var startOffset = lineStart(source, line);
            var endOffset = startOffset + raw.length();
            if (text.startsWith("/*")) {
                var comment = collectLoudComment(
                        source,
                        line,
                        indent,
                        text,
                        startOffset,
                        endOffset
                );
                result.add(comment.line());
                line = comment.nextLine();
                continue;
            }
            if (text.startsWith("//")) {
                var comment = collectSilentComment(
                        source,
                        line,
                        indent,
                        text,
                        startOffset,
                        endOffset
                );
                result.add(comment.line());
                line = comment.nextLine();
                continue;
            }

            var combined = new StringBuilder(text);
            var contentStartOffset = startOffset + indentation.length();
            var contentEndOffset = contentStartOffset + text.length();
            var pieces = new ArrayList<LinePiece>();
            pieces.add(new LinePiece(
                    text, contentStartOffset, contentEndOffset, true
            ));
            var previousContentEnd = contentEndOffset;
            var lastLine = line;
            while (true) {
                var state = continuationState(combined.toString());
                if (state.errorMessage() != null) {
                    throw error(source, startOffset, endOffset, state.errorMessage());
                }
                var needsForcedContinuation = state.requiresContinuation();
                var needsIndentedContinuation = !needsForcedContinuation
                        && requiresIndentedContinuation(combined.toString());
                if (!needsForcedContinuation && !needsIndentedContinuation) {
                    break;
                }
                @Nullable Integer nextPhysical = nextNonblankPhysicalLine(
                        source,
                        lastLine + 1
                );
                if (nextPhysical == null) {
                    if (needsForcedContinuation && state.mandatory()) {
                        throw error(
                                source,
                                startOffset,
                                endOffset,
                                state.openQuote()
                                        ? "Expected closing quote."
                                        : "Expected a continuation line."
                        );
                    }
                    break;
                }
                var continuation = source.lineText(nextPhysical);
                var continuationIndentation = indentation(continuation);
                if (needsIndentedContinuation
                        && continuationIndentation.columns() < indent) {
                    // An ancestor indentation ends the statement.
                    break;
                }
                if (needsIndentedContinuation
                        && continuationIndentation.columns() == indent
                        && !allowsSameIndentExpressionContinuation(combined.toString())) {
                    // A complete-looking sibling ends the statement, except for
                    // bare expression at-rules ({@code @error}/{@code @debug}/…)
                    // whose dart-sass parsers consume newlines after the keyword.
                    break;
                }
                // Trailing-comma forced joins only absorb same-or-shallower lines
                // (multi-line selectors). A more-indented line after a declaration
                // trailing comma is a nested statement, not a list continuation.
                if (needsForcedContinuation
                        && !state.mandatory()
                        && continuationIndentation.columns() > indent
                        && looksLikeDeclarationOrAssignment(combined.toString())) {
                    break;
                }
                var continuationText = continuation
                        .substring(continuationIndentation.length())
                        .stripTrailing();
                // {@code @mixin name} / {@code @function name} only continue for a
                // parameter list. Body statements such as {@code b: c} open a block.
                // Do not advance {@code lastLine} before this check or the body
                // line is dropped from the logical-line stream.
                if (needsIndentedContinuation
                        && isIncompleteCallableSignature(combined.toString())
                        && !continuationText.stripLeading().startsWith("(")) {
                    break;
                }
                lastLine = nextPhysical;
                // Collapse to a single space when joining indented mid-statement
                // tokens so {@code @forward\n  "x"} becomes {@code @forward "x"}.
                // Trailing silent comments must be dropped first: SCSS {@code //}
                // comments out the rest of the logical line, so
                // {@code @for //\n  $i from 1 through 2} would otherwise become
                // {@code @for // $i from 1 through 2} and lose the continuation.
                if (needsIndentedContinuation) {
                    var lengthBeforeStrip = combined.length();
                    stripTrailingSilentComment(combined);
                    if (combined.length() != lengthBeforeStrip) {
                        // Keep pieces aligned with the stripped spelling used for
                        // SCSS emission; original offsets still span the statement.
                        pieces.clear();
                        pieces.add(new LinePiece(
                                combined.toString(),
                                startOffset,
                                endOffset,
                                false
                        ));
                        previousContentEnd = endOffset;
                    }
                    combined.append(' ').append(continuationText);
                } else {
                    combined.append('\n').append(continuationText);
                }
                var continuationLineStart = lineStart(source, lastLine);
                var continuationStart = continuationLineStart
                        + continuationIndentation.length();
                var continuationEnd = continuationStart + continuationText.length();
                pieces.add(new LinePiece(
                        needsIndentedContinuation ? " " : "\n",
                        previousContentEnd,
                        continuationStart,
                        false
                ));
                pieces.add(new LinePiece(
                        continuationText,
                        continuationStart,
                        continuationEnd,
                        true
                ));
                previousContentEnd = continuationEnd;
                endOffset = continuationLineStart + continuation.length();
            }

            var finalState = continuationState(combined.toString());
            if (finalState.errorMessage() != null) {
                throw error(source, startOffset, endOffset, finalState.errorMessage());
            }
            if (finalState.openQuote()) {
                throw error(source, startOffset, endOffset, "Expected closing quote.");
            }
            if (finalState.requiresContinuation() && finalState.mandatory()) {
                throw error(
                        source,
                        startOffset,
                        endOffset,
                        "Expected a continuation line."
                );
            }

            // Drop trailing silent comments so SCSS emission does not comment out
            // synthetic braces ({@code @for ... // {}} → {@code @for ... {}}).
            // Custom properties keep trailing {@code //} as literal value text
            // (dart-sass parses them with silentComments: false).
            // Same-line loud comments after {@code ;} are also dropped: indented
            // {@code b: c; /* f */} does not emit the comment (dart-sass).
            if (!isCustomPropertyDeclaration(combined.toString())) {
                var lengthBeforeFinalStrip = combined.length();
                stripTrailingSilentComment(combined);
                stripTrailingLoudCommentAfterSemicolon(combined);
                if (combined.length() != lengthBeforeFinalStrip) {
                    pieces.clear();
                    pieces.add(new LinePiece(
                            combined.toString(),
                            startOffset,
                            endOffset,
                            false
                    ));
                }
            }

            result.add(new LogicalLine(
                    indent,
                    combined.toString(),
                    startOffset,
                    endOffset,
                    List.copyOf(pieces),
                    false
            ));
            line = lastLine + 1;
        }
        return result;
    }

    /// Collects a loud comment that spans one or more physical lines.
    ///
    /// @param source the source file
    /// @param line the first physical line
    /// @param indent the first-line indentation
    /// @param text the first-line comment text
    /// @param startOffset the first-line source offset
    /// @param endOffset the first-line end offset
    /// @return the logical comment and the next unconsumed line index
    private static CollectedComment collectLoudComment(
            SourceFile source,
            int line,
            int indent,
            String text,
            int startOffset,
            int endOffset
    ) {
        var combined = new StringBuilder(text);
        var lastLine = line;
        if (!text.contains("*/")) {
            while (++lastLine < source.lineCount()) {
                var commentLine = source.lineText(lastLine);
                combined.append('\n').append(commentLine.stripTrailing());
                endOffset = lineStart(source, lastLine) + commentLine.length();
                if (commentLine.contains("*/")) {
                    break;
                }
            }
        }
        if (!combined.toString().contains("*/")) {
            throw error(
                    source,
                    startOffset,
                    endOffset,
                    "Expected closing comment delimiter."
            );
        }
        // Keep only the first complete loud comment. Trailing whitespace and
        // further comments after {@code */} are discarded; any other text is an
        // error (dart-sass content-after-close).
        var closed = firstLoudCommentPrefix(combined.toString());
        if (closed.errorMessage() != null) {
            throw error(source, startOffset, endOffset, closed.errorMessage());
        }
        var commentText = closed.text();
        // Source span still covers the original collected range so multi-line
        // diagnostics point at the full comment when useful.
        return new CollectedComment(
                new LogicalLine(
                        indent,
                        commentText,
                        startOffset,
                        endOffset,
                        List.of(new LinePiece(
                                commentText, startOffset, endOffset, false
                        )),
                        true
                ),
                lastLine + 1
        );
    }

    /// Result of isolating the first complete loud comment in a line group.
    ///
    /// @param text          the retained comment text, empty when invalid
    /// @param errorMessage  the failure message when content follows the close
    private record LoudCommentPrefix(String text, @Nullable String errorMessage) {
    }

    /// Returns the first complete {@code /*…*/} prefix and validates residual text.
    ///
    /// Residual whitespace and additional comments are ignored. Any other residual
    /// text yields {@code Unexpected text after end of comment}.
    ///
    /// @param text the collected loud-comment candidate
    /// @return the retained prefix or an error
    private static LoudCommentPrefix firstLoudCommentPrefix(String text) {
        if (!text.startsWith("/*")) {
            return new LoudCommentPrefix("", "Expected closing comment delimiter.");
        }
        var index = 2;
        while (index + 1 < text.length()) {
            if (text.charAt(index) == '*' && text.charAt(index + 1) == '/') {
                index += 2;
                var residual = text.substring(index);
                var cursor = 0;
                while (cursor < residual.length()) {
                    var character = residual.charAt(cursor);
                    if (Character.isWhitespace(character)) {
                        cursor++;
                        continue;
                    }
                    if (character == '/' && cursor + 1 < residual.length()
                            && residual.charAt(cursor + 1) == '/') {
                        // Silent comment runs to end of residual physical line
                        // group; treat as allowed trailing filler.
                        break;
                    }
                    if (character == '/' && cursor + 1 < residual.length()
                            && residual.charAt(cursor + 1) == '*') {
                        var close = residual.indexOf("*/", cursor + 2);
                        if (close < 0) {
                            return new LoudCommentPrefix(
                                    "",
                                    "Expected closing comment delimiter."
                            );
                        }
                        cursor = close + 2;
                        continue;
                    }
                    return new LoudCommentPrefix(
                            "",
                            "Unexpected text after end of comment"
                    );
                }
                return new LoudCommentPrefix(text.substring(0, index), null);
            }
            index++;
        }
        return new LoudCommentPrefix("", "Expected closing comment delimiter.");
    }

    /// Collects an indented silent comment and prefixes continuation text with
    /// a comment marker for the generated SCSS source.
    ///
    /// A more-indented line belongs to the comment even when it doesn't begin
    /// with //. At the parent indentation, only another silent-comment line
    /// continues the comment.
    ///
    /// @param source the source file
    /// @param line the first physical line
    /// @param indent the first-line indentation
    /// @param text the first-line comment text
    /// @param startOffset the first-line source offset
    /// @param endOffset the first-line end offset
    /// @return the logical comment and the next unconsumed line index
    private static CollectedComment collectSilentComment(
            SourceFile source,
            int line,
            int indent,
            String text,
            int startOffset,
            int endOffset
    ) {
        var combined = new StringBuilder(text);
        var lastLine = line;
        while (lastLine + 1 < source.lineCount()) {
            var nextLine = lastLine + 1;
            var raw = source.lineText(nextLine);
            var nextIndentation = indentation(raw);
            var nextText = raw.substring(nextIndentation.length()).stripTrailing();
            if (nextText.isBlank()) {
                combined.append('\n');
                lastLine = nextLine;
                endOffset = lineStart(source, lastLine) + raw.length();
                continue;
            }
            if (nextIndentation.columns() < indent
                    || (nextIndentation.columns() == indent
                    && !nextText.startsWith("//"))) {
                break;
            }
            combined.append('\n').append("//").append(
                    nextText.startsWith("//")
                            ? nextText.substring(2)
                            : " " + nextText
            );
            lastLine = nextLine;
            endOffset = lineStart(source, lastLine) + raw.length();
        }
        return new CollectedComment(
                new LogicalLine(
                        indent,
                        combined.toString(),
                        startOffset,
                        endOffset,
                        List.of(new LinePiece(
                                combined.toString(), startOffset, endOffset, false
                        )),
                        true
                ),
                lastLine + 1
        );
    }
    /// Computes the source offset at the beginning of a logical line.
    ///
    /// @param source the source file
    /// @param line the zero-based line index
    /// @return the UTF-16 offset at the line start
    private static int lineStart(SourceFile source, int line) {
        if (line == 0) {
            return 0;
        }
        var previous = 0;
        for (var index = 0; index < line; index++) {
            var text = source.lineText(index);
            previous += text.length();
            if (previous < source.length()) {
                var character = source.content().charAt(previous);
                if (character == '\r' && previous + 1 < source.length()
                        && source.content().charAt(previous + 1) == '\n') {
                    previous += 2;
                } else {
                    previous++;
                }
            }
        }
        return previous;
    }

    /// Counts leading spaces and tabs as indentation columns and source characters.
    ///
    /// @param text the physical line
    /// @return the indentation columns and leading-character count
    private static Indentation indentation(String text) {
        var columns = 0;
        var length = 0;
        while (length < text.length()) {
            var character = text.charAt(length);
            if (character == ' ') {
                columns++;
                length++;
            } else if (character == '\t') {
                columns += 2;
                length++;
            } else {
                break;
            }
        }
        return new Indentation(columns, length);
    }

    /// Scans one accumulated statement for continuation and delimiter errors.
    ///
    /// Quotes are represented as stack frames so interpolation inside a quoted
    /// string can resume the surrounding quote after its closing brace. A
    /// quoted string itself may cross a physical line only when its final
    /// character is an unescaped backslash; interpolation expressions and
    /// balanced Sass values may cross lines directly.
    ///
    /// @param text the accumulated statement text
    /// @return the continuation state
    private static ContinuationState continuationState(String text) {
        var stack = new ArrayDeque<Character>();
        var lastSignificant = -1;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            var top = stack.peek();
            if (isQuote(top)) {
                if (character == '\\') {
                    if (index + 1 < text.length()) {
                        index++;
                    }
                } else if (character == top) {
                    stack.pop();
                } else if (character == '#'
                        && index + 1 < text.length()
                        && text.charAt(index + 1) == '{') {
                    stack.push(INTERPOLATION);
                    index++;
                }
                continue;
            }

            if (character == '/' && index + 1 < text.length()
                    && text.charAt(index + 1) == '/') {
                while (++index < text.length() && text.charAt(index) != '\n') {
                    // Skip silent-comment contents.
                }
                continue;
            }
            if (character == '/' && index + 1 < text.length()
                    && text.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < text.length()
                        && !(text.charAt(index) == '*' && text.charAt(index + 1) == '/')) {
                    index++;
                }
                if (index + 1 < text.length()) {
                    index++; // consume '/' of '*/'
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                stack.push(character);
            } else if (character == '#'
                    && index + 1 < text.length()
                    && text.charAt(index + 1) == '{') {
                stack.push(INTERPOLATION);
                index++;
            } else if (character == '(' || character == '[' || character == '{') {
                stack.push(character);
            } else if (character == ')'
                    || character == ']'
                    || character == '}') {
                if (stack.isEmpty()) {
                    // dart-sass reports Unexpected ")" / "]" for surplus closers.
                    return new ContinuationState(
                            false,
                            false,
                            "Unexpected \"" + (char) character + "\"."
                    );
                }
                var open = stack.peek();
                boolean matches = character == '}'
                        ? open == INTERPOLATION || open == '{'
                        : open == matchingOpening(character);
                if (!matches) {
                    // Wrong closer for the open delimiter → expected the matching one.
                    char expectedCloser = open == INTERPOLATION || open == '{'
                            ? '}'
                            : open == '(' ? ')' : ']';
                    return new ContinuationState(
                            false,
                            false,
                            "expected \"" + expectedCloser + "\"."
                    );
                }
                stack.pop();
            }
            if (!Character.isWhitespace(character)) {
                lastSignificant = index;
            }
        }

        var top = stack.peek();
        if (isQuote(top)) {
            if (hasUnescapedTrailingBackslash(text)) {
                return new ContinuationState(true, true, null);
            }
            // dart-sass string_scanner: Expected '.  / Expected ".
            return new ContinuationState(false, true, "Expected " + top + ".");
        }
        // Trailing commas force a join when a next line exists (selectors like
        // {@code a, // comment} / {@code a,}), but are valid terminators at EOF
        // ({@code b: c, d,}) so they are optional rather than mandatory.
        var trailingComma = lastSignificant >= 0
                && text.charAt(lastSignificant) == ',';
        // Trailing spaced binary operators and {@code and}/{@code or}/{@code not}
        // force a same-indent continuation only for declarations/assignments
        // ({@code b: 3 %} / {@code $a: b +}). Selector lines ending in combinators
        // ({@code a >}) must remain separate so the next sibling selector is not
        // consumed as a continuation.
        var declarationLike = looksLikeDeclarationOrAssignment(text);
        var trailingOperator = declarationLike
                && isTrailingSpacedBinaryOperator(text, lastSignificant);
        var trailingKeyword = declarationLike
                && !trailingOperator
                && !trailingComma
                && endsWithOperatorKeyword(text, lastSignificant);
        var requires = !stack.isEmpty()
                || trailingComma
                || trailingOperator
                || trailingKeyword;
        var mandatory = !stack.isEmpty() || trailingOperator || trailingKeyword;
        return new ContinuationState(requires, mandatory, false, null);
    }

    /// Returns whether {@code text} ends with a spaced binary operator at
    /// {@code lastSignificant}.
    private static boolean isTrailingSpacedBinaryOperator(
            String text,
            int lastSignificant
    ) {
        if (lastSignificant < 0) {
            return false;
        }
        if (isNumberPercentUnit(text, lastSignificant)) {
            return false;
        }
        // Multi-character operators end at lastSignificant.
        if (lastSignificant >= 1) {
            var two = text.substring(lastSignificant - 1, lastSignificant + 1);
            if (two.equals("==") || two.equals("!=")
                    || two.equals("<=") || two.equals(">=")) {
                var before = lastSignificant - 1;
                while (before > 0 && Character.isWhitespace(text.charAt(before - 1))) {
                    before--;
                }
                // Require a preceding non-operator token (expression left-hand side).
                return before > 0 && isTopLevelIndex(text, lastSignificant - 1);
            }
        }
        var last = text.charAt(lastSignificant);
        if (!isBinaryOperatorChar(last) || last == '=' || last == '!') {
            // Lone {@code =}/{@code !} are not complete binary operators.
            return false;
        }
        // Require whitespace immediately before the operator so glued forms such
        // as {@code a-*} and {@code 10%} are not treated as open binaries.
        return lastSignificant > 0
                && Character.isWhitespace(text.charAt(lastSignificant - 1))
                && isTopLevelIndex(text, lastSignificant);
    }

    /// Returns whether {@code text} ends with a top-level {@code and}/{@code or}/
    /// {@code not} keyword at {@code lastSignificant}.
    private static boolean endsWithOperatorKeyword(String text, int lastSignificant) {
        if (lastSignificant < 0) {
            return false;
        }
        var end = lastSignificant + 1;
        var start = end;
        while (start > 0 && isIdentChar(text.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return false;
        }
        var word = text.substring(start, end).toLowerCase(Locale.ROOT);
        if (!(word.equals("and") || word.equals("or") || word.equals("not"))) {
            return false;
        }
        return isTopLevelIndex(text, start);
    }

    /// Returns whether {@code character} may appear in a Sass identifier word.
    private static boolean isIdentChar(char character) {
        return Character.isLetterOrDigit(character)
                || character == '-'
                || character == '_';
    }

    /// Returns whether {@code text} looks like a property or variable assignment.
    ///
    /// Used to decide whether a trailing operator should force a continuation.
    /// Selectors such as {@code a >} must not.
    ///
    /// @param text the statement text
    /// @return whether a top-level colon is present
    private static boolean looksLikeDeclarationOrAssignment(String text) {
        return indexOfTopLevelColon(text) >= 0;
    }

    /// Returns whether {@code text} is only combinator characters (and spaces).
    ///
    /// Bare {@code +}, {@code >}, {@code ~}, and sequences such as {@code + >}
    /// are style-rule headers that open a nest, not open binary operators.
    ///
    /// @param text the statement text
    /// @return whether the text is combinator-only
    private static boolean isCombinatorOnlySelector(String text) {
        var stripped = text.strip();
        if (stripped.isEmpty()) {
            return false;
        }
        for (var index = 0; index < stripped.length(); index++) {
            var character = stripped.charAt(index);
            if (character == '+' || character == '>' || character == '~'
                    || Character.isWhitespace(character)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /// Returns whether {@code character} can end a binary operator token.
    private static boolean isBinaryOperatorChar(char character) {
        return character == '+' || character == '-' || character == '*'
                || character == '/' || character == '%'
                || character == '<' || character == '>' || character == '='
                || character == '!';
    }

    /// Returns whether {@code percentIndex} is the unit of a number ({@code 10%}).
    private static boolean isNumberPercentUnit(String text, int percentIndex) {
        return percentIndex > 0
                && text.charAt(percentIndex) == '%'
                && Character.isDigit(text.charAt(percentIndex - 1));
    }

    /// Returns whether a stack frame is a quote delimiter.
    ///
    /// @param delimiter the stack frame, or {@code null}
    /// @return whether the frame is a single- or double-quote character
    private static boolean isQuote(@Nullable Character delimiter) {
        return delimiter != null && (delimiter == '\'' || delimiter == '"');
    }

    /// Returns the opening delimiter corresponding to a closing delimiter.
    ///
    /// @param delimiter the closing delimiter
    /// @return the expected opening delimiter
    private static char matchingOpening(char delimiter) {
        return switch (delimiter) {
            case ')' -> '(';
            case ']' -> '[';
            default -> throw new AssertionError("unsupported closing delimiter");
        };
    }

    /// Returns whether a statement ends with an odd run of backslashes.
    ///
    /// @param text the accumulated statement text
    /// @return whether the final non-whitespace character escapes the newline
    private static boolean hasUnescapedTrailingBackslash(String text) {
        var index = text.length() - 1;
        while (index >= 0
                && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index--;
        }
        var count = 0;
        while (index >= 0 && text.charAt(index) == '\\') {
            count++;
            index--;
        }
        return (count & 1) != 0;
    }

    /// Removes a trailing top-level silent comment from an accumulated statement.
    ///
    /// Used when an indented continuation is about to be joined onto the same
    /// logical SCSS line. Leaving {@code //} in place would comment out the
    /// continuation tokens.
    ///
    /// @param text the mutable accumulated statement buffer
    private static void stripTrailingSilentComment(StringBuilder text) {
        var end = trailingSilentCommentEnd(text, text.length());
        if (end >= 0) {
            text.setLength(end);
        }
    }

    /// Removes a trailing same-line loud comment that follows a statement
    /// terminator ({@code ;}).
    ///
    /// Indented {@code b: c; /* f */} must not emit the comment (dart-sass),
    /// while a loud comment on its own logical line is preserved.
    ///
    /// @param text the mutable accumulated statement buffer
    private static void stripTrailingLoudCommentAfterSemicolon(StringBuilder text) {
        var length = text.length();
        var end = length;
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end < 2 || text.charAt(end - 1) != '/' || text.charAt(end - 2) != '*') {
            return;
        }
        // Walk back to the matching {@code /*}.
        var open = end - 3;
        while (open >= 0
                && !(text.charAt(open) == '/'
                && open + 1 < end
                && text.charAt(open + 1) == '*')) {
            open--;
        }
        if (open < 0) {
            return;
        }
        var before = open;
        while (before > 0 && Character.isWhitespace(text.charAt(before - 1))) {
            before--;
        }
        if (before == 0 || text.charAt(before - 1) != ';') {
            return;
        }
        text.setLength(before);
    }

    /// Returns whether {@code text} is a custom-property declaration.
    ///
    /// Custom properties keep trailing silent comments as literal value text.
    ///
    /// @param text the statement text
    /// @return whether the statement declares a {@code --*} property
    private static boolean isCustomPropertyDeclaration(String text) {
        var stripped = text.strip();
        if (!stripped.startsWith("--")) {
            return false;
        }
        var colon = indexOfTopLevelColon(stripped);
        return colon > 1;
    }

    /// Returns {@code text} without a trailing top-level silent comment.
    ///
    /// @param text the statement text
    /// @return the text with a trailing {@code //} comment removed
    private static String withoutTrailingSilentComment(String text) {
        var end = trailingSilentCommentEnd(text, text.length());
        return end < 0 ? text : text.substring(0, end);
    }

    /// Returns the keep-length for {@code text} after dropping a trailing silent
    /// comment, or {@code -1} when no such comment is present.
    ///
    /// @param text   the statement text
    /// @param length the prefix length to scan
    /// @return the truncated length, or {@code -1}
    private static int trailingSilentCommentEnd(CharSequence text, int length) {
        var quote = 0;
        var silentStart = -1;
        for (var index = 0; index < length; index++) {
            var character = text.charAt(index);
            if (quote != 0) {
                if (character == '\\' && index + 1 < length) {
                    index++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                continue;
            }
            if (character == '/' && index + 1 < length && text.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < length
                        && !(text.charAt(index) == '*' && text.charAt(index + 1) == '/')) {
                    index++;
                }
                if (index + 1 < length) {
                    index++;
                }
                continue;
            }
            if (character == '/' && index + 1 < length && text.charAt(index + 1) == '/') {
                // A {@code //} only comments out the rest of its physical line.
                // It is trailing for the whole statement only when no further
                // non-whitespace tokens follow on later lines (continuation
                // content must not be discarded with the comment).
                var lineEnd = index + 2;
                while (lineEnd < length && text.charAt(lineEnd) != '\n') {
                    lineEnd++;
                }
                var after = lineEnd;
                while (after < length && Character.isWhitespace(text.charAt(after))) {
                    after++;
                }
                if (after >= length) {
                    silentStart = index;
                    break;
                }
                index = lineEnd;
                continue;
            }
            if (character == '\n') {
                silentStart = -1;
            }
        }
        if (silentStart < 0) {
            return -1;
        }
        var end = silentStart;
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }
    /// Normalizes indented-syntax shorthand into SCSS statement spelling.
    ///
    /// @param text the source statement
    /// @return the normalized statement
    private static String normalizeStatement(String text) {
        if (text.startsWith("= ") || text.startsWith("=")) {
            return "@mixin " + text.substring(1).stripLeading();
        }
        // Indented {@code +mixin} is an include only when {@code +} is glued to
        // the name. {@code + a} / bare {@code +} remain leading next-sibling
        // combinators (dart-sass).
        if (text.startsWith("+")
                && text.length() > 1
                && isIncludeNameStart(text.charAt(1))) {
            return "@include " + text.substring(1);
        }
        if (text.startsWith("@elseif")) {
            return "@else if" + text.substring("@elseif".length());
        }
        if (text.startsWith(":") && text.length() > 1
                && Character.isLetter(text.charAt(1))) {
            var separator = text.indexOf(' ');
            if (separator > 1) {
                return text.substring(1, separator) + ":" + text.substring(separator);
            }
        }
        if (text.startsWith("@import ")) {
            var argument = text.substring("@import ".length()).strip();
            if (!argument.isEmpty()
                    && argument.charAt(0) != '\''
                    && argument.charAt(0) != '"'
                    && !argument.regionMatches(true, 0, "url(", 0, 4)
                    && argument.indexOf(',') < 0
                    && argument.chars().noneMatch(Character::isWhitespace)) {
                return "@import \"" + argument.replace("\"", "\\\"") + "\"";
            }
        }
        return text;
    }

    /// Returns whether a statement can own an indented child block.
    ///
    /// Style rules, mixins, includes with content, control-flow at-rules, empty
    /// nested-property headers (`font:`), and valued nested-property headers
    /// (`border: 1px`) may all open a child block. Variables, comments, and
    /// terminal at-rules may not.
    ///
    /// @param text the normalized statement
    /// @return whether the statement is a valid block header
    private static boolean isBlockHeader(String text) {
        if (text.endsWith(";") || isComment(text) || text.startsWith("$")) {
            return false;
        }
        if (text.startsWith("@")) {
            return !isTerminalAtRule(text);
        }
        // Custom properties never own indented children. Ordinary valued
        // properties may still open nested-property blocks
        // ({@code border: 1px} / {@code style: solid}).
        if (looksLikeCustomPropertyHeader(text) || text.strip().startsWith("--")) {
            return false;
        }
        return true;
    }

    /// Returns whether {@code text} looks like a CSS/Sass property declaration.
    ///
    /// Style rules such as {@code a:hover} keep their colon as part of the selector
    /// and must not be treated as property declarations. Plain {@code name: value}
    /// and nested-property headers ending in {@code :} are treated as properties.
    ///
    /// @param text the normalized statement
    /// @return whether the statement should terminate with a semicolon when empty
    private static boolean looksLikePropertyDeclaration(String text) {
        if (text.startsWith("@") || text.startsWith("$")) {
            return false;
        }
        // Nested-property header with no value on this line.
        if (text.endsWith(":")) {
            return true;
        }
        var colon = indexOfTopLevelColon(text);
        if (colon <= 0) {
            return false;
        }
        var name = text.substring(0, colon).strip();
        if (name.isEmpty()) {
            return false;
        }
        // Namespaced or local variable assignments always terminate with ';'.
        if (name.indexOf('$') >= 0) {
            return true;
        }
        // Selectors frequently begin with combinators or simple selector sigils.
        var first = name.charAt(0);
        if (first == '.' || first == '#' || first == '[' || first == '*'
                || first == '&' || first == '%' || first == '>' || first == '+'
                || first == '~') {
            return false;
        }
        // Pseudo-element/class-only selectors such as `:hover` or `::before`.
        if (first == ':') {
            return false;
        }
        // `a:hover` is a selector; `color: red` is a declaration. Treat a colon
        // immediately followed by an identifier character (no whitespace) as a
        // pseudo-class when the name before the colon is a type selector token.
        if (colon + 1 < text.length()) {
            var after = text.charAt(colon + 1);
            if (!Character.isWhitespace(after) && after != ':'
                    && isTypeSelectorName(name)) {
                return false;
            }
        }
        return isPropertyName(name);
    }

    /// Returns the index of the first top-level colon, or {@code -1}.
    private static int indexOfTopLevelColon(String text) {
        var depth = 0;
        var quote = 0;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quote != 0) {
                if (character == '\\' && index + 1 < text.length()) {
                    index++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(' || character == '[' || character == '{') {
                depth++;
            } else if (character == ')' || character == ']' || character == '}') {
                if (depth > 0) {
                    depth--;
                }
            } else if (character == ':' && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    /// Returns whether {@code name} is a plausible CSS property name.
    private static boolean isPropertyName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        var index = 0;
        if (name.startsWith("--")) {
            index = 2;
        } else if (name.charAt(0) == '-') {
            index = 1;
        }
        if (index >= name.length()) {
            return false;
        }
        for (; index < name.length(); index++) {
            var character = name.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '-'
                    || character == '_'
                    || character == '\\')) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether {@code name} is a bare type/ident selector before a pseudo.
    private static boolean isTypeSelectorName(String name) {
        if (name.isEmpty() || name.equals("*")) {
            return true;
        }
        for (var index = 0; index < name.length(); index++) {
            var character = name.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '-'
                    || character == '_'
                    || character == '\\')) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether an at-rule is a terminal statement.
    ///
    /// @param text the normalized statement
    /// @return whether no child block may follow the at-rule
    private static boolean isTerminalAtRule(String text) {
        var separator = text.indexOf(' ');
        var name = separator < 0 ? text : text.substring(0, separator);
        return switch (name) {
            case "@use", "@forward", "@import", "@extend", "@return", "@content",
                    "@debug", "@warn", "@error", "@charset", "@namespace" -> true;
            default -> false;
        };
    }

    /// Returns whether an empty {@code @include} should omit braces.
    ///
    /// Plain includes are statement-terminated. Includes with a {@code using}
    /// clause still need an empty content block ({@code {}}) when no children
    /// follow.
    ///
    /// @param text the normalized statement
    /// @return whether braces should be omitted for an empty include
    private static boolean isBlocklessIncludeWhenEmpty(String text) {
        if (text.regionMatches(true, 0, "@include ", 0, "@include ".length())) {
            // using() requires a content block even when the body is empty.
            return !containsTopLevelKeyword(text, "using");
        }
        // Glued {@code +mixin} is an include; spaced {@code + a} is a combinator.
        if (text.startsWith("+")
                && text.length() > 1
                && isIncludeNameStart(text.charAt(1))) {
            return !containsTopLevelKeyword(text, "using");
        }
        return false;
    }

    /// Returns whether an empty at-rule statement should emit as {@code @name;}
    /// rather than {@code @name {}}.
    ///
    /// Unknown at-rules are blockless when they have no children. Structural
    /// rules that always open a block keep empty braces.
    ///
    /// @param text the normalized statement
    /// @return whether the empty form is a blockless at-rule
    private static boolean isBlocklessAtRuleWhenEmpty(String text) {
        var name = atRuleName(text);
        if (name == null) {
            return false;
        }
        return switch (name) {
            case "@media", "@supports", "@mixin", "@function", "@if", "@else",
                    "@each", "@for", "@while", "@keyframes", "@-webkit-keyframes",
                    "@-moz-keyframes", "@-o-keyframes", "@font-face",
                    "@at-root", "@layer", "@container" -> false;
            // {@code @include … using ()} needs an empty content block; plain
            // includes stay statement-terminated.
            case "@include" -> !containsTopLevelKeyword(text, "using");
            default -> true;
        };
    }

    /// Keywords that leave a statement incomplete at end-of-line in the indented
    /// syntax, so a more-indented following line is a continuation rather than a
    /// child block.
    private static final Set<String> INDENTED_CONTINUATION_KEYWORDS = Set.of(
            "as",
            "with",
            "show",
            "hide",
            "using",
            "from",
            "through",
            "to",
            "and",
            "or",
            "not",
            "in"
    );

    /// Returns whether {@code text} still needs tokens from a more-indented line.
    ///
    /// Matches dart-sass: mid-statement tokens such as {@code @forward},
    /// {@code as}, {@code show}, or {@code with} may continue on the next
    /// indented line without opening a child block.
    ///
    /// @param text the accumulated statement text
    /// @return whether an indented continuation line may follow
    private static boolean requiresIndentedContinuation(String text) {
        // Analyze without trailing silent comments so
        // {@code @for $i from 1 through //} still continues for the bound.
        var stripped = withoutTrailingSilentComment(text.strip());
        if (stripped.isEmpty()) {
            return false;
        }
        // Indented-syntax bare mixin introducer {@code =} (name on next line).
        // {@code +} with a following name on the same line is an include; a bare
        // {@code +} alone is not continued (space-separated {@code + a} is a
        // selector).
        if (stripped.equals("=")) {
            return true;
        }
        // Incomplete variable: {@code $a} or {@code $a:} before the value.
        if (stripped.startsWith("$")) {
            var colon = indexOfTopLevelColon(stripped);
            if (colon < 0) {
                return true;
            }
            return stripped.substring(colon + 1).isBlank();
        }
        // Bare at-rule names whose dart-sass parsers use
        // {@code whitespace(consumeNewlines: true)} after the keyword may put
        // their first argument on the next indented line. {@code @import} does
        // not ({@code consumeNewlines: false}).
        if (stripped.charAt(0) == '@') {
            var onlyName = true;
            for (var index = 1; index < stripped.length(); index++) {
                var character = stripped.charAt(index);
                if (!(Character.isLetterOrDigit(character)
                        || character == '-'
                        || character == '_')) {
                    onlyName = false;
                    break;
                }
            }
            if (onlyName) {
                // dart-sass uses whitespace(consumeNewlines: true) after these
                // keywords so the first argument may begin on the next line.
                // {@code @charset}/{@code @namespace} do not consume newlines
                // for their string argument (dart-sass reports Expected string
                // at EOL rather than joining the next indented line).
                return switch (stripped.toLowerCase(Locale.ROOT)) {
                    case "@use", "@forward", "@return", "@extend",
                            "@debug", "@warn", "@error", "@content",
                            "@each", "@for", "@while", "@if",
                            "@mixin", "@include", "@function" -> true;
                    default -> false;
                };
            }
            // {@code @function name} / {@code @mixin name} without a parameter
            // list still need the {@code ()} on a later indented line.
            if (isIncompleteCallableSignature(stripped)) {
                return true;
            }
        }
        // Trailing top-level comma: list/map still open
        // ({@code @each $a in b,}). Declarations with a trailing comma do not
        // use more-indented lines as list continuations (dart-sass requires
        // parentheses for multi-line lists); same-indent selector commas are
        // handled by forced continuation instead.
        if (endsWithTopLevelComma(stripped) && !looksLikeDeclarationOrAssignment(stripped)) {
            return true;
        }
        // Trailing binary operator leaves a declaration/assignment open
        // ({@code $a: b +} / {@code b: 3 %}). Bare combinator selectors such as
        // {@code +} / {@code >} open a nested block and must not continue.
        if (endsWithTopLevelBinaryOperator(stripped)
                && !isCombinatorOnlySelector(stripped)
                && looksLikeDeclarationOrAssignment(stripped)) {
            return true;
        }
        // Trailing {@code !} on a declaration waits for {@code important}
        // ({@code b: c!\n  important}).
        if (endsWithTopLevelBang(stripped) && looksLikeDeclarationOrAssignment(stripped)) {
            return true;
        }
        // {@code @include … using} still needs the content-parameter list.
        if (isIncompleteUsingClause(stripped)) {
            return true;
        }
        // Control directives still waiting for structural keywords.
        if (isIncompleteControlDirective(stripped)) {
            return true;
        }
        @Nullable String lastWord = lastTopLevelWord(stripped);
        if (lastWord == null
                || !INDENTED_CONTINUATION_KEYWORDS.contains(
                        lastWord.toLowerCase(Locale.ROOT)
                )) {
            return false;
        }
        // {@code @supports}/{@code @media} conditions do not consume newlines
        // after {@code and}/{@code or}/{@code not}; dart-sass reports an incomplete
        // condition at EOL rather than joining the next indented line.
        var lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith("@supports") || lower.startsWith("@media")) {
            var word = lastWord.toLowerCase(Locale.ROOT);
            if (word.equals("and") || word.equals("or") || word.equals("not")) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether {@code text} ends with a top-level binary operator token.
    ///
    /// Used so indented syntax continues {@code $a: b +} / {@code b: 3 %} onto
    /// the next more-indented line. Multi-character operators ({@code ==},
    /// {@code !=}, {@code <=}, {@code >=}) and keywords ({@code and}, {@code or})
    /// are recognized in addition to the single-character forms.
    ///
    /// @param text the statement text
    /// @return whether a trailing operator leaves the statement open
    private static boolean endsWithTopLevelBinaryOperator(String text) {
        var end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            return false;
        }
        // Multi-character comparison operators.
        if (end >= 2) {
            var two = text.substring(end - 2, end);
            if (two.equals("==") || two.equals("!=")
                    || two.equals("<=") || two.equals(">=")) {
                return isTopLevelIndex(text, end - 2);
            }
        }
        var last = text.charAt(end - 1);
        if (last == '+' || last == '-' || last == '*' || last == '/' || last == '%'
                || last == '<' || last == '>') {
            // A trailing {@code %} that is a unit on a number ({@code 10%}) is not
            // an open binary operator. Detect digit immediately before {@code %}.
            if (last == '%' && end >= 2 && Character.isDigit(text.charAt(end - 2))) {
                return false;
            }
            return isTopLevelIndex(text, end - 1);
        }
        @Nullable String lastWord = lastTopLevelWord(text.substring(0, end));
        if (lastWord == null) {
            return false;
        }
        var lower = lastWord.toLowerCase(Locale.ROOT);
        return lower.equals("and") || lower.equals("or") || lower.equals("not");
    }

    /// Returns whether {@code index} sits at top-level depth (not inside quotes or
    /// brackets) in {@code text}.
    ///
    /// @param text  the full statement text
    /// @param index the candidate operator index
    /// @return whether the index is top-level
    private static boolean isTopLevelIndex(String text, int index) {
        var depth = 0;
        var quote = 0;
        for (var i = 0; i < index; i++) {
            var character = text.charAt(i);
            if (quote != 0) {
                if (character == '\\' && i + 1 < index) {
                    i++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(' || character == '[' || character == '{') {
                depth++;
            } else if (character == ')' || character == ']' || character == '}') {
                if (depth > 0) {
                    depth--;
                }
            }
        }
        return depth == 0 && quote == 0;
    }

    /// Returns whether {@code text} is a control directive missing required
    /// mid-statement keywords ({@code in}, {@code from}, {@code to}/
    /// {@code through}).
    ///
    /// Matching uses at-rule word boundaries so {@code @forward} is not treated
    /// as an incomplete {@code @for}.
    ///
    /// @param text the accumulated statement text
    /// @return whether a more-indented continuation is required
    private static boolean isIncompleteControlDirective(String text) {
        var stripped = text.strip();
        if (startsWithAtRule(stripped, "@each")) {
            return !containsTopLevelKeyword(stripped, "in");
        }
        if (startsWithAtRule(stripped, "@for")) {
            if (!containsTopLevelKeyword(stripped, "from")) {
                return true;
            }
            return !containsTopLevelKeyword(stripped, "to")
                    && !containsTopLevelKeyword(stripped, "through");
        }
        // {@code @else if} still needs its condition expression.
        if (startsWithAtRule(stripped, "@else")) {
            var rest = stripped.substring("@else".length()).strip();
            if (rest.regionMatches(true, 0, "if", 0, 2)
                    && (rest.length() == 2 || !isIdentChar(rest.charAt(2)))) {
                var afterIf = rest.substring(2).strip();
                return afterIf.isEmpty();
            }
        }
        return false;
    }

    /// Returns whether {@code text} begins with the at-rule name {@code rule}.
    ///
    /// The name must be followed by end-of-string or a non-identifier character
    /// so longer names such as {@code @forward} do not match {@code @for}.
    ///
    /// @param text the statement text
    /// @param rule the lowercase at-rule name including {@code @}
    /// @return whether {@code text} starts with that at-rule
    private static boolean startsWithAtRule(String text, String rule) {
        if (text.length() < rule.length()) {
            return false;
        }
        if (!text.regionMatches(true, 0, rule, 0, rule.length())) {
            return false;
        }
        if (text.length() == rule.length()) {
            return true;
        }
        return !isIdentChar(text.charAt(rule.length()));
    }

    /// Returns whether {@code text} ends with a top-level comma.
    ///
    /// @param text the statement text
    /// @return whether a trailing comma leaves the statement open
    private static boolean endsWithTopLevelComma(String text) {
        var end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end == 0 || text.charAt(end - 1) != ',') {
            return false;
        }
        var depth = 0;
        var quote = 0;
        for (var index = 0; index < end - 1; index++) {
            var character = text.charAt(index);
            if (quote != 0) {
                if (character == '\\' && index + 1 < end) {
                    index++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(' || character == '[' || character == '{') {
                depth++;
            } else if (character == ')' || character == ']' || character == '}') {
                if (depth > 0) {
                    depth--;
                }
            }
        }
        return depth == 0 && quote == 0;
    }

    /// Returns whether {@code keyword} appears as a whole top-level word.
    ///
    /// @param text    the statement text
    /// @param keyword the ASCII keyword to find (lowercase)
    /// @return whether the keyword is present outside brackets and strings
    private static boolean containsTopLevelKeyword(String text, String keyword) {
        var depth = 0;
        var quote = 0;
        var length = text.length();
        var keyLength = keyword.length();
        for (var index = 0; index < length; index++) {
            var character = text.charAt(index);
            if (quote != 0) {
                if (character == '\\' && index + 1 < length) {
                    index++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                continue;
            }
            if (character == '(' || character == '[' || character == '{') {
                depth++;
                continue;
            }
            if (character == ')' || character == ']' || character == '}') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth != 0) {
                continue;
            }
            if (index + keyLength > length) {
                break;
            }
            if (!regionMatchesIgnoreCase(text, index, keyword)) {
                continue;
            }
            var before = index == 0 ? 0 : text.charAt(index - 1);
            var after = index + keyLength == length
                    ? 0
                    : text.charAt(index + keyLength);
            if (isIdentChar(before) || isIdentChar(after)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /// Returns whether {@code character} may continue an identifier or variable.
    private static boolean isIdentChar(int character) {
        return Character.isLetterOrDigit(character)
                || character == '-'
                || character == '_'
                || character == '$'
                || character == '\\';
    }

    /// Returns whether {@code character} may begin an indented {@code +include} name.
    ///
    /// @param character the code unit after {@code +}
    /// @return whether a glued include name begins here
    private static boolean isIncludeNameStart(int character) {
        return Character.isLetter(character)
                || character == '_'
                || character == '\\'
                || character == '-'
                || character == '#'; // interpolated include names such as {@code +#{$name}}
    }

    /// Returns whether {@code text} at {@code offset} matches {@code expected}
    /// ignoring ASCII case.
    private static boolean regionMatchesIgnoreCase(
            String text,
            int offset,
            String expected
    ) {
        if (offset + expected.length() > text.length()) {
            return false;
        }
        for (var index = 0; index < expected.length(); index++) {
            var a = text.charAt(offset + index);
            var b = expected.charAt(index);
            if (a == b) {
                continue;
            }
            if (a >= 'A' && a <= 'Z') {
                a += 32;
            }
            if (b >= 'A' && b <= 'Z') {
                b += 32;
            }
            if (a != b) {
                return false;
            }
        }
        return true;
    }

    /// Returns the last top-level identifier-like word in {@code text}.
    ///
    /// @param text the statement text
    /// @return the last word, or {@code null} when none is present
    private static @Nullable String lastTopLevelWord(String text) {
        var end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            return null;
        }
        var depth = 0;
        var quote = 0;
        var wordEnd = end;
        for (var index = 0; index < end; index++) {
            var character = text.charAt(index);
            if (quote != 0) {
                if (character == '\\' && index + 1 < end) {
                    index++;
                } else if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(' || character == '[' || character == '{') {
                depth++;
            } else if (character == ')' || character == ']' || character == '}') {
                if (depth > 0) {
                    depth--;
                }
            }
        }
        if (depth != 0 || quote != 0) {
            return null;
        }
        var start = wordEnd;
        while (start > 0) {
            var character = text.charAt(start - 1);
            if (Character.isLetterOrDigit(character)
                    || character == '-'
                    || character == '_'
                    || character == '@'
                    || character == '$'
                    || character == '\\') {
                start--;
            } else {
                break;
            }
        }
        if (start == wordEnd) {
            return null;
        }
        // Ensure the word is not inside brackets by scanning only when depth is 0
        // at the end (already checked). Reject if a non-space non-word character
        // immediately precedes without being a separator.
        return text.substring(start, wordEnd);
    }

    /// Returns the first non-blank physical line index at or after {@code start}.
    ///
    /// @param source the source file
    /// @param start  the first physical line to inspect
    /// @return the line index, or {@code null} when none remain
    private static @Nullable Integer nextNonblankPhysicalLine(
            SourceFile source,
            int start
    ) {
        for (var index = start; index < source.lineCount(); index++) {
            var raw = source.lineText(index);
            var indentation = indentation(raw);
            var text = raw.substring(indentation.length()).stripTrailing();
            if (!text.isBlank()) {
                return index;
            }
        }
        return null;
    }

    /// Returns whether {@code text} is a bare {@code @charset} or {@code @namespace}
    /// at-rule with no argument on the same line.
    ///
    /// @param text the normalized statement
    /// @return whether the statement still needs a string argument
    private static boolean isBareCharsetOrNamespace(String text) {
        var stripped = text.strip();
        var lower = stripped.toLowerCase(Locale.ROOT);
        return lower.equals("@charset") || lower.equals("@namespace");
    }

    /// Returns whether {@code text} ends with a top-level {@code !} token.
    ///
    /// @param text the statement text
    /// @return whether a trailing bang leaves {@code !important} open
    private static boolean endsWithTopLevelBang(String text) {
        var end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end > 0 && text.charAt(end - 1) == '!';
    }

    /// Returns whether {@code text} is an include that ends with bare {@code using}.
    ///
    /// @param text the statement text
    /// @return whether a content-parameter list is still required
    private static boolean isIncompleteUsingClause(String text) {
        var stripped = text.strip();
        boolean include = stripped.regionMatches(true, 0, "@include", 0, "@include".length())
                || (stripped.startsWith("+")
                && stripped.length() > 1
                && isIncludeNameStart(stripped.charAt(1)));
        if (!include) {
            return false;
        }
        @Nullable String lastWord = lastTopLevelWord(stripped);
        return lastWord != null && lastWord.equalsIgnoreCase("using");
    }

    /// Returns whether a bare expression at-rule may continue on a same-indent line.
    ///
    /// dart-sass consumes newlines after {@code @error}/{@code @debug}/
    /// {@code @warn}/{@code @return}, so the expression may start at the same
    /// indentation as the keyword (for example {@code @error\na}).
    ///
    /// @param text the accumulated statement text so far
    /// @return whether same-indent continuation is permitted
    private static boolean allowsSameIndentExpressionContinuation(String text) {
        var stripped = withoutTrailingSilentComment(text.strip()).toLowerCase(Locale.ROOT);
        return stripped.equals("@error")
                || stripped.equals("@debug")
                || stripped.equals("@warn")
                || stripped.equals("@return");
    }

    /// Returns whether {@code text} is {@code @function}/{@code @mixin} with a
    /// name but no parameter list yet.
    ///
    /// Bare {@code @function}/{@code @mixin} keywords (name still pending) are
    /// not included; those continue via the only-name path and accept any next
    /// token. This form is only {@code @function name} / {@code @mixin name},
    /// which continues solely for a {@code (} parameter list.
    ///
    /// @param text the accumulated statement text
    /// @return whether a parameter list continuation is still required
    private static boolean isIncompleteCallableSignature(String text) {
        var stripped = text.strip();
        var lower = stripped.toLowerCase(Locale.ROOT);
        String keyword;
        if (lower.startsWith("@function")) {
            keyword = "@function";
        } else if (lower.startsWith("@mixin")) {
            keyword = "@mixin";
        } else {
            return false;
        }
        // Bare keyword still waiting for its name — not this helper's concern.
        if (stripped.length() == keyword.length()) {
            return false;
        }
        if (!Character.isWhitespace(stripped.charAt(keyword.length()))) {
            return false;
        }
        // Already has a parameter list or body introducer.
        if (stripped.indexOf('(') >= 0 || stripped.endsWith("{")) {
            return false;
        }
        // {@code @function name} / {@code @mixin name} still needs {@code ()}.
        var rest = stripped.substring(keyword.length()).strip();
        return !rest.isEmpty();
    }

    /// Builds the dart-sass message for illegal indentation beneath a statement.
    ///
    /// @param text the complete parent statement
    /// @return the diagnostic message
    private static String indentedBeneathMessage(String text) {
        var stripped = text.strip();
        if (stripped.startsWith("$")) {
            return "Nothing may be indented beneath a variable declaration.";
        }
        if (stripped.startsWith("--") || looksLikeCustomPropertyHeader(stripped)) {
            return "Nothing may be indented beneath a custom property.";
        }
        if (isCssFunctionResultHeader(stripped)) {
            return "Nothing may be indented beneath a @function result.";
        }
        if (stripped.startsWith("@return")) {
            return "Nothing may be indented beneath a @function result.";
        }
        var atName = atRuleName(stripped);
        if (atName != null) {
            return "Nothing may be indented beneath a " + atName + " rule.";
        }
        return "This statement cannot contain indented children.";
    }

    /// Returns whether {@code text} is a CSS custom-function {@code result} header.
    ///
    /// Plain {@code result:} / {@code RESULT:} under {@code @function --name} may
    /// not open a nested property block (dart-sass). Interpolated names such as
    /// {@code #{result}:} remain ordinary nested-property headers.
    ///
    /// @param text the statement text
    /// @return whether the statement is a non-interpolated result descriptor
    private static boolean isCssFunctionResultHeader(String text) {
        var colon = indexOfTopLevelColon(text);
        if (colon <= 0) {
            return false;
        }
        var name = text.substring(0, colon).strip();
        return name.equalsIgnoreCase("result");
    }

    /// Returns whether {@code text} is a custom-property declaration header.
    ///
    /// @param text the statement text
    /// @return whether the statement declares a custom property
    private static boolean looksLikeCustomPropertyHeader(String text) {
        var colon = indexOfTopLevelColon(text);
        if (colon <= 0) {
            return false;
        }
        var name = text.substring(0, colon).strip();
        return name.startsWith("--");
    }

    /// Returns the leading at-rule name including {@code @}, or {@code null}.
    ///
    /// @param text the statement text
    /// @return {@code @forward}, {@code @import}, …
    private static @Nullable String atRuleName(String text) {
        if (!text.startsWith("@")) {
            return null;
        }
        var end = 1;
        while (end < text.length()) {
            var character = text.charAt(end);
            if (!(Character.isLetterOrDigit(character)
                    || character == '-'
                    || character == '_')) {
                break;
            }
            end++;
        }
        return end == 1 ? null : text.substring(0, end);
    }

    /// Returns whether a statement is a Sass or CSS comment.
    ///
    /// @param text the statement text
    /// @return whether the text begins a comment
    private static boolean isComment(String text) {
        return text.startsWith("//") || text.startsWith("/*");
    }

    /// Finds a second statement after a top-level semicolon on one indented line.
    ///
    /// Semicolons inside quotes, comments, or nested {@code ()}/{@code []}/{@code {}}
    /// do not count. A single trailing semicolon after the only statement is allowed.
    ///
    /// @param text the normalized statement text
    /// @return the index of the first non-whitespace character of the second
    /// statement, or {@code -1} when the line holds at most one statement
    private static int multiStatementSemicolonOffset(String text) {
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
            if (character == '/' && index + 1 < text.length()) {
                var next = text.charAt(index + 1);
                if (next == '/') {
                    break;
                }
                if (next == '*') {
                    index += 2;
                    while (index + 1 < text.length()
                            && !(text.charAt(index) == '*' && text.charAt(index + 1) == '/')) {
                        index++;
                    }
                    index++;
                    continue;
                }
            }
            if (character == '(' || character == '[' || character == '{') {
                depth++;
                continue;
            }
            if (character == ')' || character == ']' || character == '}') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (character != ';' || depth != 0) {
                continue;
            }
            var rest = index + 1;
            while (rest < text.length()
                    && (text.charAt(rest) == ' ' || text.charAt(rest) == '\t')) {
                rest++;
            }
            if (rest < text.length() && text.charAt(rest) != ';') {
                return rest;
            }
        }
        return -1;
    }

    /// Creates a preprocessing failure associated with one source line.
    ///
    /// @param source the original source
    /// @param line the offending line
    /// @param message the diagnostic message
    /// @return the parse failure
    private static ParseException error(
            SourceFile source,
            LogicalLine line,
            String message
    ) {
        SourceSpan span = source.span(line.startOffset(), line.endOffset());
        return new ParseException(message, span);
    }

    /// Creates a preprocessing failure with a structured diagnostic code.
    ///
    /// @param source the original source
    /// @param line   the offending line
    /// @param code   the stable diagnostic code
    /// @return the parse failure
    private static ParseException error(
            SourceFile source,
            LogicalLine line,
            org.glavo.scssfx.DiagnosticCode code
    ) {
        SourceSpan span = source.span(line.startOffset(), line.endOffset());
        return new ParseException(code, span);
    }

    /// Creates a preprocessing failure associated with a source range.
    ///
    /// @param source the original source
    /// @param startOffset the UTF-16 start offset
    /// @param endOffset the UTF-16 end offset
    /// @param message the diagnostic message
    /// @return the parse failure
    private static ParseException error(
            SourceFile source,
            int startOffset,
            int endOffset,
            String message
    ) {
        return new ParseException(message, source.span(startOffset, endOffset));
    }
    /// Contains the physical indentation measurements of one source line.
    ///
    /// @param columns the indentation width used for structural comparison
    /// @param length the number of leading whitespace characters
    private record Indentation(int columns, int length) {
    }

    /// Contains one generated logical-line piece and its original range.
    ///
    /// @param text the generated piece text
    /// @param startOffset the inclusive original offset
    /// @param endOffset the exclusive original offset
    /// @param original whether the text exactly equals the original range
    private record LinePiece(
            String text,
            int startOffset,
            int endOffset,
            boolean original
    ) {
        /// Creates one validated line piece.
        LinePiece {
            Objects.requireNonNull(text, "text");
            if (startOffset < 0 || endOffset < startOffset) {
                throw new IllegalArgumentException("line-piece range is invalid");
            }
        }
    }

    /// Contains one logical statement line.
    ///
    /// @param indent the indentation width
    /// @param text the statement text
    /// @param startOffset the source start offset
    /// @param endOffset the source end offset
    /// @param pieces source-backed pieces forming the unchanged statement text
    /// @param comment whether the line is a comment
    private record LogicalLine(
            int indent,
            String text,
            int startOffset,
            int endOffset,
            @Unmodifiable List<LinePiece> pieces,
            boolean comment
    ) {
    }

    /// Contains a collected comment and the next unconsumed physical line.
    ///
    /// @param line the collected logical comment
    /// @param nextLine the next physical line index
    private record CollectedComment(LogicalLine line, int nextLine) {
    }

    /// Describes the continuation and delimiter state of a logical statement.
    ///
    /// @param requiresContinuation whether another physical line is required
    /// @param openQuote whether the statement ends inside a quoted string
    /// @param errorMessage the delimiter diagnostic, or {@code null}
    /// @param requiresContinuation whether another physical line should be joined
    /// @param mandatory            whether missing that line is an error at EOF
    /// @param openQuote            whether an open quote is active
    /// @param errorMessage         a delimiter error, or {@code null}
    private record ContinuationState(
            boolean requiresContinuation,
            boolean mandatory,
            boolean openQuote,
            @Nullable String errorMessage
    ) {
        /// Creates a continuation state with mandatory equal to requiresContinuation.
        ContinuationState(
                boolean requiresContinuation,
                boolean openQuote,
                @Nullable String errorMessage
        ) {
            this(requiresContinuation, requiresContinuation, openQuote, errorMessage);
        }
    }
    /// Contains the indentation of one generated block header.
    ///
    /// @param indent the header indentation width
    private record OpenBlock(int indent) {
    }
}
