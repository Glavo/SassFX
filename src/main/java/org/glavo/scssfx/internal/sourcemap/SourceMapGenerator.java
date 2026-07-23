// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.sourcemap;

import com.fasterxml.jackson.core.JsonFactory;
import org.glavo.scssfx.SourceMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;

/// Builds a version-3 source-map JSON document from buffer entries.
@ApiStatus.Internal
@NotNullByDefault
public final class SourceMapGenerator {
    /// Contains the JSON factory used to emit source maps.
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    /// Prevents instantiation.
    private SourceMapGenerator() {
    }

    /// Creates a validated source map from one serialization buffer.
    ///
    /// @param buffer the buffer that recorded CSS and entries
    /// @return the source map, or {@code null} when mapping was disabled
    public static SourceMap generate(SourceMapBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer");
        if (!buffer.enabled()) {
            return null;
        }
        try {
            return new SourceMap(toJson(buffer.sources(), buffer.entries()));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to encode source map JSON", failure);
        }
    }

    /// Encodes sources and entries as a version-3 source-map document.
    ///
    /// @param sources the ordered source identifiers
    /// @param entries the map entries
    /// @return the JSON text
    /// @throws IOException if JSON encoding fails
    static String toJson(List<String> sources, List<SourceMapBuffer.Entry> entries)
            throws IOException {
        var writer = new StringWriter();
        try (var generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeNumberField("version", 3);
            generator.writeArrayFieldStart("sources");
            for (var source : sources) {
                generator.writeString(source);
            }
            generator.writeEndArray();
            generator.writeArrayFieldStart("names");
            generator.writeEndArray();
            generator.writeStringField("mappings", encodeMappings(entries));
            generator.writeEndObject();
        }
        return writer.toString();
    }

    /// Encodes entries as a VLQ mappings string.
    ///
    /// @param entries the map entries
    /// @return the mappings field
    static String encodeMappings(List<SourceMapBuffer.Entry> entries) {
        var buffer = new StringBuilder();
        var previousGeneratedLine = 0;
        var previousGeneratedColumn = 0;
        var previousSourceIndex = 0;
        var previousSourceLine = 0;
        var previousSourceColumn = 0;
        for (var entry : entries) {
            while (previousGeneratedLine < entry.generatedLine()) {
                buffer.append(';');
                previousGeneratedLine++;
                previousGeneratedColumn = 0;
            }
            if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != ';') {
                buffer.append(',');
            }
            Vlq.encode(entry.generatedColumn() - previousGeneratedColumn, buffer);
            Vlq.encode(entry.sourceIndex() - previousSourceIndex, buffer);
            Vlq.encode(entry.sourceLine() - previousSourceLine, buffer);
            Vlq.encode(entry.sourceColumn() - previousSourceColumn, buffer);
            previousGeneratedColumn = entry.generatedColumn();
            previousSourceIndex = entry.sourceIndex();
            previousSourceLine = entry.sourceLine();
            previousSourceColumn = entry.sourceColumn();
        }
        return buffer.toString();
    }
}
