// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.util.Objects;

/// Contains a version 3 source map encoded as JSON.
///
/// @param json the complete source-map JSON document
@NotNullByDefault
public record SourceMap(String json) {
    /// The parser factory used to validate source-map documents.
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

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
        try (var parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Source map must be a JSON object");
            }

            var foundVersion = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new IllegalArgumentException("Invalid source-map JSON object");
                }

                var fieldName = parser.currentName();
                var valueToken = parser.nextToken();
                if (valueToken == null) {
                    throw new IllegalArgumentException("Source-map field has no value");
                }

                if ("version".equals(fieldName)) {
                    if (foundVersion) {
                        throw new IllegalArgumentException(
                                "Source map must contain exactly one version field"
                        );
                    }
                    foundVersion = true;
                    if (valueToken != JsonToken.VALUE_NUMBER_INT
                            || parser.getIntValue() != 3) {
                        throw new IllegalArgumentException(
                                "Source-map version must be the integer 3"
                        );
                    }
                } else {
                    parser.skipChildren();
                }
            }

            if (!foundVersion) {
                throw new IllegalArgumentException(
                        "Source map must contain a version field"
                );
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException(
                        "Source map must contain exactly one JSON document"
                );
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid source-map JSON", exception);
        }
    }
}
