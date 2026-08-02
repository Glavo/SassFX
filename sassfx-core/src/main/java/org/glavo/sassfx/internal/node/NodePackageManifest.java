// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.node;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// Contains one strictly decoded Node package manifest.
///
/// Object member order is retained because Node export condition precedence is
/// order-sensitive. Decoded arrays and objects are exposed only through
/// unmodifiable views and may contain JSON `null` values.
@NotNullByDefault
final class NodePackageManifest {
    /// Contains the insertion-ordered root object.
    private final @UnmodifiableView Map<String, @Nullable Object> values;

    /// Creates a decoded manifest.
    ///
    /// @param values the insertion-ordered root object
    private NodePackageManifest(
            @UnmodifiableView Map<String, @Nullable Object> values
    ) {
        this.values = Objects.requireNonNull(values, "values");
    }

    /// Reads one UTF-8 package manifest as strict JSON.
    ///
    /// @param manifestPath the `package.json` path
    /// @param packageName the package name used in diagnostics
    /// @return the decoded manifest
    /// @throws IOException if the file cannot be read or is not exactly one
    ///                     JSON object
    static NodePackageManifest read(
            Path manifestPath,
            String packageName
    ) throws IOException {
        Objects.requireNonNull(manifestPath, "manifestPath");
        Objects.requireNonNull(packageName, "packageName");
        var json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            @Nullable Object value = readJsonValue(reader);
            if (!(value instanceof Map<?, ?> rawMap)
                    || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException(
                        "package manifest must be one JSON object"
                );
            }
            return new NodePackageManifest(object(rawMap));
        } catch (IOException | RuntimeException failure) {
            throw new IOException(
                    "Failed to parse " + manifestPath + " for \"pkg:"
                            + packageName + "\": "
                            + Objects.requireNonNullElse(
                            failure.getMessage(),
                            failure.getClass().getSimpleName()
                    ),
                    failure
            );
        }
    }

    /// Returns one root manifest member.
    ///
    /// @param name the member name
    /// @return the decoded value, or `null` when absent or explicitly null
    @Nullable Object value(String name) {
        return values.get(Objects.requireNonNull(name, "name"));
    }

    /// Converts a decoded JSON object to a string-keyed unmodifiable view.
    ///
    /// @param rawMap the decoded JSON object
    /// @return the insertion-ordered object
    /// @throws IllegalStateException if a key is not a string
    static @UnmodifiableView Map<String, @Nullable Object> object(
            Map<?, ?> rawMap
    ) {
        Objects.requireNonNull(rawMap, "rawMap");
        var result = new LinkedHashMap<String, @Nullable Object>();
        for (var entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException(
                        "package manifest object key is not a string"
                );
            }
            result.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    /// Reads one JSON value at the reader's current position.
    ///
    /// @param reader the manifest reader
    /// @return the decoded JSON-compatible Java value
    /// @throws IOException if the JSON structure is invalid
    private static @Nullable Object readJsonValue(JsonReader reader)
            throws IOException {
        return switch (reader.peek()) {
            case NULL -> {
                reader.nextNull();
                yield null;
            }
            case STRING -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NUMBER -> new BigDecimal(reader.nextString());
            case BEGIN_ARRAY -> {
                reader.beginArray();
                var values = new ArrayList<@Nullable Object>();
                while (reader.hasNext()) {
                    values.add(readJsonValue(reader));
                }
                reader.endArray();
                yield Collections.unmodifiableList(values);
            }
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var values = new LinkedHashMap<String, @Nullable Object>();
                while (reader.hasNext()) {
                    values.put(reader.nextName(), readJsonValue(reader));
                }
                reader.endObject();
                yield object(values);
            }
            default -> throw new IOException(
                    "unsupported token in package manifest: " + reader.peek()
            );
        };
    }

}
