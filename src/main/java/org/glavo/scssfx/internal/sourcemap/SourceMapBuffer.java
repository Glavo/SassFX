// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.sourcemap;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/// Builds CSS text while recording version-3 source-map entries.
@ApiStatus.Internal
@NotNullByDefault
public final class SourceMapBuffer {
    /// Contains the generated CSS text.
    private final StringBuilder text = new StringBuilder();

    /// Contains map entries in generation order.
    private final ArrayList<Entry> entries = new ArrayList<>();

    /// Contains unique source URLs in first-seen order.
    private final LinkedHashMap<String, Integer> sources = new LinkedHashMap<>();

    /// Contains the current generated line, zero-based.
    private int line;

    /// Contains the current generated column, zero-based UTF-16 code units.
    private int column;

    /// Records whether source-map entries should be collected.
    private final boolean enabled;

    /// Creates a buffer.
    ///
    /// @param enabled whether mappings are recorded
    public SourceMapBuffer(boolean enabled) {
        this.enabled = enabled;
    }

    /// Appends text and advances the generated location.
    ///
    /// @param value the text to append
    /// @return this buffer
    public SourceMapBuffer append(String value) {
        Objects.requireNonNull(value, "value");
        for (var index = 0; index < value.length(); index++) {
            append(value.charAt(index));
        }
        return this;
    }

    /// Appends one character and advances the generated location.
    ///
    /// @param value the character to append
    /// @return this buffer
    public SourceMapBuffer append(char value) {
        text.append(value);
        if (value == '\n') {
            line++;
            column = 0;
        } else {
            column++;
        }
        return this;
    }

    /// Writes content while associating the write start with a source span.
    ///
    /// @param span    the source span, or {@code null} to write without a mapping
    /// @param writer  the content writer
    public void forSpan(@Nullable SourceSpan span, Runnable writer) {
        Objects.requireNonNull(writer, "writer");
        if (enabled && span != null) {
            addEntry(span);
        }
        writer.run();
    }

    /// Returns the generated CSS text.
    ///
    /// @return the CSS document
    public String css() {
        return text.toString();
    }

    /// Returns recorded map entries.
    ///
    /// @return entries in generation order
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /// Returns unique source identifiers in first-seen order.
    ///
    /// @return the source list
    public List<String> sources() {
        return List.copyOf(sources.keySet());
    }

    /// Returns whether mapping is enabled.
    ///
    /// @return whether entries are recorded
    public boolean enabled() {
        return enabled;
    }

    /// Records one mapping from the current target location to a source span start.
    ///
    /// @param span the source span
    private void addEntry(SourceSpan span) {
        var sourceId = sourceId(span.url());
        var sourceIndex = sources.computeIfAbsent(sourceId, ignored -> sources.size());
        var source = span.start();
        if (!entries.isEmpty()) {
            var last = entries.get(entries.size() - 1);
            if (last.generatedLine == line && last.generatedColumn == column) {
                return;
            }
            if (last.generatedLine == line
                    && last.sourceIndex == sourceIndex
                    && last.sourceLine == source.line()
                    && last.sourceColumn == source.column()) {
                return;
            }
        }
        entries.add(new Entry(
                line,
                column,
                sourceIndex,
                source.line(),
                source.column()
        ));
    }

    /// Returns a stable source-map identifier for a URL.
    ///
    /// @param url the source URL, or {@code null}
    /// @return the identifier
    private static String sourceId(@Nullable URI url) {
        if (url == null) {
            return "stdin";
        }
        return url.toString();
    }

    /// One source-map entry from a generated location to a source location.
    ///
    /// @param generatedLine   zero-based generated line
    /// @param generatedColumn zero-based generated column
    /// @param sourceIndex     index into the sources list
    /// @param sourceLine      zero-based source line
    /// @param sourceColumn    zero-based source column
    public record Entry(
            int generatedLine,
            int generatedColumn,
            int sourceIndex,
            int sourceLine,
            int sourceColumn
    ) {
    }
}
