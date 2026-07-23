// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/// Converts indentation-based Sass statements to the braced form consumed by
/// the shared SCSS parser.
///
/// The conversion is structural rather than textual indentation replacement:
/// it tracks sibling and child indentation, recognizes nested-property blocks,
/// translates legacy indented mixin syntax, and retains original source spans
/// for preprocessing failures. The generated source uses the same canonical
/// URL as the input source.
@ApiStatus.Internal
@NotNullByDefault
final class IndentedSassPreprocessor {
    /// Marks an interpolation frame in the delimiter stack.
    private static final char INTERPOLATION = '\u0001';
    /// Prevents instantiation.
    private IndentedSassPreprocessor() {
    }

    /// Converts an indented Sass source into SCSS-compatible source text.
    ///
    /// Blank lines are omitted from the generated syntax. Comments are
    /// retained, while statement separators and structural braces are derived
    /// from indentation.
    ///
    /// @param source the original indented Sass source
    /// @return a new source file containing equivalent braced syntax
    /// @throws ParseException if indentation introduces an impossible child,
    /// a block header is missing, or a statement form is malformed
    static SourceFile transform(SourceFile source) {
        Objects.requireNonNull(source, "source");
        var lines = logicalLines(source);
        var output = new StringBuilder(source.content().length() + lines.size() * 4);
        var blocks = new ArrayDeque<OpenBlock>();
        var childIndents = new HashMap<Integer, Integer>();
        var lastStatementIndent = -1;
        var lastStatementOpened = false;

        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.comment()) {
                closeBlocksBefore(line.indent(), blocks, childIndents, output);
                output.append(line.text()).append('\n');
                continue;
            }

            if (line.indent() > 0
                    && line.indent() > lastStatementIndent
                    && !lastStatementOpened) {
                throw error(
                        source,
                        line,
                        "Indented Sass statements must be nested below a block header."
                );
            }

            closeBlocksBefore(line.indent(), blocks, childIndents, output);
            if (blocks.isEmpty() && line.indent() != 0) {
                throw error(source, line, "Top-level Sass statements must start at column zero.");
            }
            if (!blocks.isEmpty() && line.indent() > blocks.peek().indent()) {
                var parentIndent = blocks.peek().indent();
                var expectedIndent = childIndents.putIfAbsent(parentIndent, line.indent());
                if (expectedIndent != null && expectedIndent != line.indent()) {
                    throw error(
                            source,
                            line,
                            "Inconsistent indentation; expected " + expectedIndent + " columns."
                    );
                }
            }

            var next = nextStatement(lines, index + 1);
            var hasChildren = next != null && next.indent() > line.indent();
            var normalized = normalizeStatement(line.text());
            if (hasChildren) {
                if (!isBlockHeader(normalized)) {
                    throw error(
                            source,
                            line,
                            "This statement cannot contain indented children."
                    );
                }
                output.append(normalized).append(" {").append('\n');
                blocks.push(new OpenBlock(line.indent()));
            } else {
                if (isComment(normalized)) {
                    output.append(normalized).append('\n');
                } else {
                    output.append(normalized);
                    if (!normalized.endsWith(";") && !normalized.endsWith("{")) {
                        output.append(';');
                    }
                    output.append('\n');
                }
            }
            lastStatementIndent = line.indent();
            lastStatementOpened = hasChildren;
        }

        while (!blocks.isEmpty()) {
            blocks.pop();
            output.append("}\n");
        }
        return new SourceFile(output.toString(), source.url());
    }

    /// Closes blocks whose headers are siblings of or ancestors of a line.
    ///
    /// @param indent the current line indentation
    /// @param blocks the open block stack
    /// @param childIndents the expected child indentation by block level
    /// @param output the generated source buffer
    private static void closeBlocksBefore(
            int indent,
            Deque<OpenBlock> blocks,
            HashMap<Integer, Integer> childIndents,
            StringBuilder output
    ) {
        while (!blocks.isEmpty() && blocks.peek().indent() >= indent) {
            var block = blocks.pop();
            childIndents.remove(block.indent());
            output.append("}\n");
            if (block.indent() == 0 && indent == 0) {
                output.append('\n');
            }
        }
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
    private static ArrayList<LogicalLine> logicalLines(SourceFile source) {
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
            var lastLine = line;
            while (true) {
                var state = continuationState(combined.toString());
                if (state.errorMessage() != null) {
                    throw error(source, startOffset, endOffset, state.errorMessage());
                }
                if (!state.requiresContinuation()) {
                    break;
                }
                if (lastLine + 1 >= source.lineCount()) {
                    throw error(
                            source,
                            startOffset,
                            endOffset,
                            state.openQuote()
                                    ? "Expected closing quote."
                                    : "Expected a continuation line."
                    );
                }
                lastLine++;
                var continuation = source.lineText(lastLine);
                var continuationIndentation = indentation(continuation);
                var continuationText = continuation
                        .substring(continuationIndentation.length())
                        .stripTrailing();
                combined.append('\n').append(continuationText);
                endOffset = lineStart(source, lastLine) + continuation.length();
            }

            var finalState = continuationState(combined.toString());
            if (finalState.errorMessage() != null) {
                throw error(source, startOffset, endOffset, finalState.errorMessage());
            }
            if (finalState.openQuote()) {
                throw error(source, startOffset, endOffset, "Expected closing quote.");
            }
            if (finalState.requiresContinuation()) {
                throw error(
                        source,
                        startOffset,
                        endOffset,
                        "Expected a continuation line."
                );
            }

            result.add(new LogicalLine(
                    indent,
                    combined.toString(),
                    startOffset,
                    endOffset,
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
        return new CollectedComment(
                new LogicalLine(indent, combined.toString(), startOffset, endOffset, true),
                lastLine + 1
        );
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
                new LogicalLine(indent, combined.toString(), startOffset, endOffset, true),
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
                if (stack.isEmpty()
                        || (character == '}'
                        ? stack.peek() != INTERPOLATION && stack.peek() != '{'
                        : stack.peek() != matchingOpening(character))) {
                    return new ContinuationState(
                            false,
                            false,
                            "Mismatched closing delimiter."
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
            return new ContinuationState(false, true, "Expected closing quote.");
        }
        var trailingComma = lastSignificant >= 0 && text.charAt(lastSignificant) == ',';
        return new ContinuationState(!stack.isEmpty() || trailingComma, false, null);
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
    /// Normalizes indented-syntax shorthand into SCSS statement spelling.
    ///
    /// @param text the source statement
    /// @return the normalized statement
    private static String normalizeStatement(String text) {
        if (text.startsWith("= ") || text.startsWith("=")) {
            return "@mixin " + text.substring(1).stripLeading();
        }
        if (text.startsWith("+ ") || text.startsWith("+")) {
            return "@include " + text.substring(1).stripLeading();
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
        return text;
    }

    /// Returns whether a statement can own an indented child block.
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
        if (isNestedPropertyHeader(text)) {
            return true;
        }
        var colon = topLevelColon(text);
        return colon < 0 || colon + 1 >= text.length() || !Character.isWhitespace(text.charAt(colon + 1));
    }

    /// Returns whether a line is an empty-value nested-property header.
    ///
    /// @param text the normalized statement
    /// @return whether the statement ends in a property colon
    private static boolean isNestedPropertyHeader(String text) {
        return !text.startsWith("$") && text.endsWith(":");
    }

    /// Returns whether an at-rule is a terminal statement.
    ///
    /// @param text the normalized statement
    /// @return whether no child block may follow the at-rule
    private static boolean isTerminalAtRule(String text) {
        var separator = text.indexOf(' ');
        var name = separator < 0 ? text : text.substring(0, separator);
        return switch (name) {
            case "@use", "@forward", "@import", "@extend", "@return",
                    "@debug", "@warn", "@error", "@charset", "@namespace" -> true;
            default -> false;
        };
    }

    /// Finds the first colon outside quotes and balanced CSS brackets.
    ///
    /// @param text the statement text
    /// @return the colon offset, or {@code -1}
    private static int topLevelColon(String text) {
        var quote = 0;
        var depth = 0;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if (quote != 0) {
                if (character == '\\') {
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
                depth = Math.max(0, depth - 1);
            } else if (character == ':' && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    /// Returns whether a statement is a Sass or CSS comment.
    ///
    /// @param text the statement text
    /// @return whether the text begins a comment
    private static boolean isComment(String text) {
        return text.startsWith("//") || text.startsWith("/*");
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

    /// Contains one logical statement line.
    ///
    /// @param indent the indentation width
    /// @param text the statement text
    /// @param startOffset the source start offset
    /// @param endOffset the source end offset
    /// @param comment whether the line is a comment
    private record LogicalLine(
            int indent,
            String text,
            int startOffset,
            int endOffset,
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
    private record ContinuationState(
            boolean requiresContinuation,
            boolean openQuote,
            @Nullable String errorMessage
    ) {
    }
    /// Contains the indentation of one generated block header.
    ///
    /// @param indent the header indentation width
    private record OpenBlock(int indent) {
    }
}
