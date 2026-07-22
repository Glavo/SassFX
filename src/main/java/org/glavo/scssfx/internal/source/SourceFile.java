// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.source;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/// Indexes source text for offset, line, column, and span operations.
///
/// Line boundaries recognize LF, CRLF, and lone CR terminators. All offsets
/// and columns use UTF-16 code units.
///
/// This type is shared across internal packages and is not a supported public
/// extension point.
@ApiStatus.Internal
@NotNullByDefault
public final class SourceFile {
    /// The complete source text.
    private final String content;

    /// The stable source URL, when one is available.
    private final @Nullable URI url;

    /// The UTF-16 offset at which each logical line begins.
    private final int @Unmodifiable [] lineStarts;

    /// Creates an indexed source file.
    ///
    /// @param content the complete source text
    /// @param url the source URL, or {@code null} when the source has no stable URL
    public SourceFile(String content, @Nullable URI url) {
        this.content = Objects.requireNonNull(content, "content");
        this.url = url;
        this.lineStarts = indexLineStarts(content);
    }

    /// Returns the complete source text.
    ///
    /// @return the source text
    public String content() {
        return content;
    }

    /// Returns the stable source URL.
    ///
    /// @return the source URL, or {@code null} when none is available
    public @Nullable URI url() {
        return url;
    }

    /// Returns the source length in UTF-16 code units.
    ///
    /// @return the source length
    public int length() {
        return content.length();
    }

    /// Returns the number of logical lines.
    ///
    /// Empty input contains one line. A trailing line terminator introduces a
    /// final empty line.
    ///
    /// @return the positive line count
    public int lineCount() {
        return lineStarts.length;
    }

    /// Returns the location corresponding to a UTF-16 source offset.
    ///
    /// The offset may equal [#length()] to identify the end of the source.
    ///
    /// @param offset the zero-based UTF-16 source offset
    /// @return the corresponding location
    /// @throws IndexOutOfBoundsException if the offset is negative or exceeds the source length
    public SourceLocation locationAt(int offset) {
        checkOffset(offset);

        var result = Arrays.binarySearch(lineStarts, offset);
        var line = result >= 0 ? result : -result - 2;
        return new SourceLocation(line, offset - lineStarts[line], offset);
    }

    /// Returns a span for the given half-open UTF-16 offset range.
    ///
    /// @param startOffset the inclusive start offset
    /// @param endOffset the exclusive end offset
    /// @return a span containing the exact selected text
    /// @throws IndexOutOfBoundsException if either offset lies outside the source
    /// @throws IllegalArgumentException if the end offset precedes the start offset
    public SourceSpan span(int startOffset, int endOffset) {
        checkOffset(startOffset);
        checkOffset(endOffset);
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must not precede startOffset");
        }

        return new SourceSpan(
                url,
                locationAt(startOffset),
                locationAt(endOffset),
                content.substring(startOffset, endOffset)
        );
    }

    /// Returns a logical line without its line terminator.
    ///
    /// @param line the zero-based line index
    /// @return the line text, which may be empty
    /// @throws IndexOutOfBoundsException if the line index is outside the source
    public String lineText(int line) {
        if (line < 0 || line >= lineStarts.length) {
            throw new IndexOutOfBoundsException("line index out of range: " + line);
        }

        var start = lineStarts[line];
        var end = line + 1 < lineStarts.length ? lineStarts[line + 1] : content.length();
        while (end > start) {
            var last = content.charAt(end - 1);
            if (last != '\r' && last != '\n') {
                break;
            }
            end--;
        }
        return content.substring(start, end);
    }

    /// Validates a UTF-16 offset against this source.
    ///
    /// @param offset the offset to validate
    /// @throws IndexOutOfBoundsException if the offset is outside the source
    private void checkOffset(int offset) {
        if (offset < 0 || offset > content.length()) {
            throw new IndexOutOfBoundsException("source offset out of range: " + offset);
        }
    }

    /// Computes the UTF-16 start offset of every logical line.
    ///
    /// @param content the source text to index
    /// @return the nonempty line-start index
    private static int @Unmodifiable [] indexLineStarts(String content) {
        var starts = new int[content.length() + 1];
        var count = 1;

        for (var index = 0; index < content.length(); index++) {
            var current = content.charAt(index);
            if (current == '\r') {
                if (index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                starts[count++] = index + 1;
            } else if (current == '\n') {
                starts[count++] = index + 1;
            }
        }

        return Arrays.copyOf(starts, count);
    }
}
