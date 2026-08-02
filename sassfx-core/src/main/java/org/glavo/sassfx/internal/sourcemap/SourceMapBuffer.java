// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.sourcemap;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /// Maps canonical source URLs to importer-provided source-map URLs.
    private final Map<URI, URI> sourceMapUrls;

    /// Maps canonical source URLs to their original text.
    private final Map<URI, String> sourceContentsByUrl;

    /// Contains URL-less root source text, or {@code null} when unavailable.
    private final @Nullable String stdinContents;

    /// Contains source text aligned with [#sources], or {@code null} when omitted.
    private final @Nullable ArrayList<@Nullable String> sourceContents;

    /// Contains the span mapped at the start of each appended line while a
    /// multiline mapped write is active.
    private @Nullable SourceSpan multilineSpan;

    /// Creates a buffer with source identifiers and optional embedded contents.
    ///
    /// @param enabled whether mappings are recorded
    /// @param sourceMapUrls alternate source-map URLs keyed by canonical URL
    /// @param includeSources whether aligned source text is retained
    /// @param sourceContentsByUrl original text keyed by canonical URL
    /// @param stdinContents URL-less root source text, or {@code null}
    public SourceMapBuffer(
            boolean enabled,
            Map<URI, URI> sourceMapUrls,
            boolean includeSources,
            Map<URI, String> sourceContentsByUrl,
            @Nullable String stdinContents
    ) {
        this.enabled = enabled;
        this.sourceMapUrls = Map.copyOf(
                Objects.requireNonNull(sourceMapUrls, "sourceMapUrls")
        );
        this.sourceContentsByUrl = Map.copyOf(
                Objects.requireNonNull(
                        sourceContentsByUrl,
                        "sourceContentsByUrl"
                )
        );
        this.stdinContents = stdinContents;
        this.sourceContents = includeSources ? new ArrayList<>() : null;
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
            if (enabled && multilineSpan != null) {
                addEntry(multilineSpan);
            }
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

    /// Writes multiline content while mapping every generated line start to
    /// the same source span.
    ///
    /// The first mapping is recorded at the current generated position. Each
    /// newline appended by {@code writer} records another mapping immediately
    /// after the newline. Nested calls restore the preceding multiline span.
    ///
    /// @param span the source span, or {@code null} to write without mappings
    /// @param writer the content writer
    public void forSpanLines(@Nullable SourceSpan span, Runnable writer) {
        Objects.requireNonNull(writer, "writer");
        if (enabled && span != null) {
            addEntry(span);
        }
        var previous = multilineSpan;
        multilineSpan = span;
        try {
            writer.run();
        } finally {
            multilineSpan = previous;
        }
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

    /// Returns source text aligned with [#sources].
    ///
    /// @return an immutable aligned list, or {@code null} when source embedding
    ///         was not requested
    public @Nullable @Unmodifiable List<@Nullable String> sourceContents() {
        return sourceContents == null
                ? null
                : Collections.unmodifiableList(
                        new ArrayList<>(sourceContents)
                );
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
        @Nullable var existingIndex = sources.get(sourceId);
        int sourceIndex;
        if (existingIndex == null) {
            sourceIndex = sources.size();
            sources.put(sourceId, sourceIndex);
            if (sourceContents != null) {
                sourceContents.add(sourceContent(span.url()));
            }
        } else {
            sourceIndex = existingIndex;
        }
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
    private String sourceId(@Nullable URI url) {
        if (url == null) {
            return stdinContents == null
                    ? "stdin"
                    : dataUrl(stdinContents);
        }
        return sourceMapUrls.getOrDefault(url, url).toString();
    }

    /// Encodes anonymous root text as a UTF-8 data URL.
    ///
    /// @param contents the root source text
    /// @return a stable source-map URL
    private static String dataUrl(String contents) {
        var bytes = contents.getBytes(StandardCharsets.UTF_8);
        var result = new StringBuilder(
                "data:application/octet-stream;charset=utf-8,"
        );
        for (var value : bytes) {
            var unsigned = value & 0xff;
            if (unsigned >= 'A' && unsigned <= 'Z'
                    || unsigned >= 'a' && unsigned <= 'z'
                    || unsigned >= '0' && unsigned <= '9'
                    || unsigned == '-'
                    || unsigned == '.'
                    || unsigned == '_'
                    || unsigned == '~') {
                result.append((char) unsigned);
            } else {
                result.append('%')
                        .append(Character.toUpperCase(
                                Character.forDigit(unsigned >>> 4, 16)
                        ))
                        .append(Character.toUpperCase(
                                Character.forDigit(unsigned & 0xf, 16)
                        ));
            }
        }
        return result.toString();
    }

    /// Returns source text associated with one canonical source URL.
    ///
    /// @param url the canonical source URL, or {@code null} for the URL-less root
    /// @return the original source text, or {@code null} when unavailable
    private @Nullable String sourceContent(@Nullable URI url) {
        return url == null ? stdinContents : sourceContentsByUrl.get(url);
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
