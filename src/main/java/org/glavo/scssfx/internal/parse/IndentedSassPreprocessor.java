// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
        var lastStatementIndent = -1;
        var lastStatementOpened = false;

        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.comment()) {
                closeBlocksBefore(line.indent(), blocks, output);
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

            closeBlocksBefore(line.indent(), blocks, output);
            if (blocks.isEmpty() && line.indent() != 0) {
                throw error(source, line, "Top-level Sass statements must start at column zero.");
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
    /// @param output the generated source buffer
    private static void closeBlocksBefore(
            int indent,
            Deque<OpenBlock> blocks,
            StringBuilder output
    ) {
        while (!blocks.isEmpty() && blocks.peek().indent() >= indent) {
            var block = blocks.pop();
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
            var indent = indentation(raw);
            var text = raw.substring(Math.min(indent, raw.length())).stripTrailing();
            if (text.isBlank()) {
                line++;
                continue;
            }

            var startOffset = source.locationAt(line == 0 ? 0 : lineStart(source, line)).offset();
            var endOffset = startOffset + raw.length();
            if (text.startsWith("/*") && !text.contains("*/")) {
                var combined = new StringBuilder(text);
                var lastLine = line;
                while (++lastLine < source.lineCount()) {
                    var commentLine = source.lineText(lastLine);
                    combined.append('\n').append(commentLine.stripTrailing());
                    if (commentLine.contains("*/")) {
                        endOffset = lineStart(source, lastLine) + commentLine.length();
                        line = lastLine;
                        break;
                    }
                }
                result.add(new LogicalLine(
                        indent,
                        combined.toString(),
                        startOffset,
                        endOffset,
                        true
                ));
                line++;
                continue;
            }

            result.add(new LogicalLine(indent, text, startOffset, endOffset, isComment(text)));
            line++;
        }
        return result;
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

    /// Counts leading spaces and tabs as indentation columns.
    ///
    /// @param text the physical line
    /// @return the indentation width
    private static int indentation(String text) {
        var result = 0;
        while (result < text.length()) {
            var character = text.charAt(result);
            if (character == ' ') {
                result++;
            } else if (character == '\t') {
                result += 2;
            } else {
                break;
            }
        }
        return result;
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
        return colon < 0 || !Character.isWhitespace(text.charAt(colon + 1));
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

    /// Contains the indentation of one generated block header.
    ///
    /// @param indent the header indentation width
    private record OpenBlock(int indent) {
    }
}
