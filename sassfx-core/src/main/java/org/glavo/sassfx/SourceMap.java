// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

/// Contains a version 3 source map encoded as JSON.
///
/// @param json the complete source-map JSON document
@NotNullByDefault
public record SourceMap(String json) {
    /// Creates and validates source-map data.
    ///
    /// @throws IllegalArgumentException if {@code json} is not exactly one JSON
    /// object with a single integer {@code version} field equal to {@code 3}
    public SourceMap {
        Objects.requireNonNull(json, "json");
        validate(json);
    }

    /// Validates the JSON structure and source-map version.
    ///
    /// @param json the JSON document to validate
    /// @throws IllegalArgumentException if the document is malformed or is not version 3
    private static void validate(String json) {
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException("Source map must be a JSON object");
            }

            reader.beginObject();
            var foundVersion = false;
            while (reader.hasNext()) {
                var fieldName = reader.nextName();
                if ("version".equals(fieldName)) {
                    if (foundVersion) {
                        throw new IllegalArgumentException(
                                "Source map must contain exactly one version field"
                        );
                    }
                    foundVersion = true;
                    if (reader.peek() != JsonToken.NUMBER
                            || !"3".equals(reader.nextString())) {
                        throw new IllegalArgumentException(
                                "Source-map version must be the integer 3"
                        );
                    }
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            if (!foundVersion) {
                throw new IllegalArgumentException(
                        "Source map must contain a version field"
                );
            }
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException(
                        "Source map must contain exactly one JSON document"
                );
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid source-map JSON", exception);
        }
    }
}
