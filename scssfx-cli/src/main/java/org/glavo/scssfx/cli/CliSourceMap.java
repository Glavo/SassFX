// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.cli;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import org.glavo.scssfx.SourceMap;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Converts compiler source maps to the URL and embedding conventions used by
/// the command line.
@NotNullByDefault
final class CliSourceMap {
    /// The JSON parser and generator factory used for source-map documents.
    private static final JsonFactory JSON_FACTORY =
            JsonFactory.builder().build();

    /// Prevents instantiation.
    private CliSourceMap() {
    }

    /// Selects how file sources are represented in a source-map document.
    @NotNullByDefault
    enum UrlMode {
        /// Makes file sources relative to the output stylesheet directory.
        RELATIVE,

        /// Emits absolute file URLs.
        ABSOLUTE
    }

    /// Contains source-map data prepared for one CSS output.
    ///
    /// @param json the compact source-map JSON document
    /// @param commentUrl the URL written in the CSS source-map comment
    @NotNullByDefault
    record Output(String json, String commentUrl) {
        /// Creates validated prepared source-map output.
        Output {
            Objects.requireNonNull(json, "json");
            Objects.requireNonNull(commentUrl, "commentUrl");
        }
    }

    /// Prepares a source map for a CSS destination or standard output.
    ///
    /// @param sourceMap the compiler-generated source map
    /// @param destination the CSS destination, or {@code null} for stdout
    /// @param urlMode the source URL mode
    /// @param embedSources whether source contents are included
    /// @param embedSourceMap whether the map is embedded in the CSS
    /// @param stdinUrl the synthetic compiler URL assigned to stdin
    /// @param stdinContents the stdin contents, or {@code null} when the root
    ///                      input is file-backed
    /// @return the rewritten JSON and CSS comment URL
    /// @throws IOException if the map is malformed
    static Output prepare(
            SourceMap sourceMap,
            @Nullable Path destination,
            UrlMode urlMode,
            boolean embedSources,
            boolean embedSourceMap,
            URI stdinUrl,
            @Nullable String stdinContents
    ) throws IOException {
        var document = parse(sourceMap.json());
        var rewrittenSources = new ArrayList<String>(document.sources().size());
        var sourceContents = embedSources
                ? new ArrayList<@Nullable String>(
                        requireSourceContents(document).size()
                )
                : null;

        for (var index = 0; index < document.sources().size(); index++) {
            var source = document.sources().get(index);
            if (stdinContents != null && source.equals(stdinUrl.toString())) {
                rewrittenSources.add(dataUrl(stdinContents, null));
                if (sourceContents != null) {
                    sourceContents.add(
                            Objects.requireNonNull(document.sourceContents())
                                    .get(index)
                    );
                }
                continue;
            }

            var sourceUri = parseUri(source);
            rewrittenSources.add(rewriteSource(sourceUri, destination, urlMode));
            if (sourceContents != null) {
                sourceContents.add(
                        Objects.requireNonNull(document.sourceContents())
                                .get(index)
                );
            }
        }

        var json = write(
                document.version(),
                rewrittenSources,
                document.names(),
                document.mappings(),
                destination,
                sourceContents
        );
        String commentUrl;
        if (embedSourceMap) {
            commentUrl = dataUrl(json, "application/json");
        } else {
            var mapPath = Path.of(
                    Objects.requireNonNull(destination).toString() + ".map"
            );
            var absoluteDestination = destination.toAbsolutePath().normalize();
            var relative = Objects.requireNonNull(
                    absoluteDestination.getParent()
            )
                    .relativize(mapPath.toAbsolutePath().normalize());
            commentUrl = pathUrl(relative);
        }
        return new Output(json, commentUrl.replace("*/", "%2A/"));
    }

    /// Returns the source-map comment for a prepared URL.
    ///
    /// @param commentUrl the embedded or sidecar map URL
    /// @param compressed whether compressed output is selected
    /// @return text to append to the CSS output
    static String comment(String commentUrl, boolean compressed) {
        return (compressed ? "" : "\n\n")
                + "/*# sourceMappingURL=" + commentUrl + " */";
    }

    /// Parses the compiler source-map document.
    ///
    /// @param json the source-map JSON
    /// @return the fields required by the CLI
    /// @throws IOException if the document is malformed or incomplete
    private static Document parse(String json) throws IOException {
        var version = -1;
        var sources = new ArrayList<String>();
        var names = new ArrayList<String>();
        @Nullable String mappings = null;
        @Nullable ArrayList<@Nullable String> sourceContents = null;

        try (var parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("source map is not a JSON object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                var field = parser.currentName();
                if (field == null || parser.nextToken() == null) {
                    throw new IOException("invalid source-map field");
                }
                switch (field) {
                    case "version" -> version = parser.getIntValue();
                    case "sources" -> readStrings(parser, sources);
                    case "sourcesContent" -> {
                        sourceContents = new ArrayList<>();
                        readNullableStrings(parser, sourceContents);
                    }
                    case "names" -> readStrings(parser, names);
                    case "mappings" -> mappings = parser.getValueAsString();
                    default -> parser.skipChildren();
                }
            }
        }
        if (version != 3 || mappings == null) {
            throw new IOException("incomplete version 3 source map");
        }
        if (sourceContents != null
                && sourceContents.size() != sources.size()) {
            throw new IOException(
                    "source-map sourcesContent is not aligned with sources"
            );
        }
        return new Document(
                version,
                sources,
                names,
                mappings,
                sourceContents
        );
    }

    /// Returns embedded source contents required by the selected CLI policy.
    ///
    /// @param document the parsed source-map document
    /// @return source contents aligned with the document's sources
    /// @throws IOException if the compiler omitted source contents
    private static List<@Nullable String> requireSourceContents(
            Document document
    ) throws IOException {
        @Nullable var sourceContents = document.sourceContents();
        if (sourceContents == null) {
            throw new IOException(
                    "compiler source map does not include source contents"
            );
        }
        return sourceContents;
    }

    /// Reads a JSON string array at the parser's current token.
    ///
    /// @param parser the source-map parser
    /// @param destination the mutable destination
    /// @throws IOException if the current value is not a string array
    private static void readStrings(
            com.fasterxml.jackson.core.JsonParser parser,
            List<String> destination
    ) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new IOException("source-map field is not an array");
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                throw new IOException("source-map array contains a non-string");
            }
            destination.add(parser.getText());
        }
    }

    /// Reads a JSON array containing strings or null values.
    ///
    /// @param parser the source-map parser
    /// @param destination the mutable destination
    /// @throws IOException if the current value has another shape
    private static void readNullableStrings(
            com.fasterxml.jackson.core.JsonParser parser,
            List<@Nullable String> destination
    ) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new IOException("source-map field is not an array");
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                destination.add(null);
            } else if (parser.currentToken() == JsonToken.VALUE_STRING) {
                destination.add(parser.getText());
            } else {
                throw new IOException(
                        "source-map sourcesContent contains an invalid value"
                );
            }
        }
    }

    /// Writes the rewritten source-map document as compact JSON.
    ///
    /// @param version the source-map version
    /// @param sources the rewritten source URLs
    /// @param names the source-map names
    /// @param mappings the VLQ mappings
    /// @param destination the CSS destination, or {@code null} for stdout
    /// @param sourceContents aligned source contents, or {@code null}
    /// @return compact source-map JSON
    /// @throws IOException if JSON generation fails
    private static String write(
            int version,
            List<String> sources,
            List<String> names,
            String mappings,
            @Nullable Path destination,
            @Nullable List<@Nullable String> sourceContents
    ) throws IOException {
        var writer = new StringWriter();
        try (var generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeNumberField("version", version);
            generator.writeStringField("sourceRoot", "");
            generator.writeArrayFieldStart("sources");
            for (var source : sources) {
                generator.writeString(source);
            }
            generator.writeEndArray();
            generator.writeArrayFieldStart("names");
            for (var name : names) {
                generator.writeString(name);
            }
            generator.writeEndArray();
            generator.writeStringField("mappings", mappings);
            if (destination != null) {
                generator.writeStringField(
                        "file",
                        pathUrl(destination.getFileName())
                );
            }
            if (sourceContents != null) {
                generator.writeArrayFieldStart("sourcesContent");
                for (@Nullable var contents : sourceContents) {
                    if (contents == null) {
                        generator.writeNull();
                    } else {
                        generator.writeString(contents);
                    }
                }
                generator.writeEndArray();
            }
            generator.writeEndObject();
        }
        return writer.toString();
    }

    /// Rewrites one source URI for the selected output location.
    ///
    /// @param source the source URI
    /// @param destination the CSS destination, or {@code null} for stdout
    /// @param urlMode the selected source URL mode
    /// @return the rewritten source-map URL
    private static String rewriteSource(
            URI source,
            @Nullable Path destination,
            UrlMode urlMode
    ) {
        @Nullable String scheme = source.getScheme();
        if (scheme != null
                && !scheme.isEmpty()
                && !"file".equalsIgnoreCase(scheme)) {
            return source.toASCIIString();
        }

        Path path;
        try {
            path = scheme == null || scheme.isEmpty()
                    ? Path.of(source.getPath())
                    : Path.of(source);
        } catch (IllegalArgumentException failure) {
            return source.toASCIIString();
        }
        path = path.toAbsolutePath().normalize();
        if (urlMode == UrlMode.ABSOLUTE || destination == null) {
            return path.toUri().toASCIIString();
        }

        var outputDirectory = Objects.requireNonNull(
                destination.toAbsolutePath().normalize().getParent()
        );
        try {
            return pathUrl(outputDirectory.relativize(path));
        } catch (IllegalArgumentException failure) {
            return path.toUri().toASCIIString();
        }
    }

    /// Parses one source-map source as a URI.
    ///
    /// @param value the source-map source
    /// @return a URI, treating invalid URI text as a local path
    private static URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException failure) {
            return Path.of(value).toAbsolutePath().normalize().toUri();
        }
    }

    /// Encodes text as a UTF-8 data URL.
    ///
    /// @param contents the text contents
    /// @param mimeType the MIME type, or {@code null} for the default
    /// @return an ASCII data URL
    private static String dataUrl(
            String contents,
            @Nullable String mimeType
    ) {
        var prefix = mimeType == null ? "data:;charset=utf-8," :
                "data:" + mimeType + ";charset=utf-8,";
        return prefix + percentEncode(contents.getBytes(StandardCharsets.UTF_8));
    }

    /// Encodes a filesystem path for a source-map URL.
    ///
    /// @param path the relative path or file name
    /// @return the slash-separated, percent-encoded URL path
    private static String pathUrl(Path path) {
        var value = path.toString().replace('\\', '/');
        try {
            return new URI(null, null, value, null).toASCIIString();
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("invalid output path: " + path, failure);
        }
    }

    /// Percent-encodes bytes using the URI unreserved set.
    ///
    /// @param bytes the UTF-8 payload bytes
    /// @return the encoded payload
    private static String percentEncode(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 3);
        for (var value : bytes) {
            var unsigned = value & 0xff;
            if (isUnreserved(unsigned)) {
                result.append((char) unsigned);
            } else {
                result.append('%');
                result.append(Character.toUpperCase(
                        Character.forDigit(unsigned >>> 4, 16)
                ));
                result.append(Character.toUpperCase(
                        Character.forDigit(unsigned & 0xf, 16)
                ));
            }
        }
        return result.toString();
    }

    /// Tests whether a byte is unreserved in a URI.
    ///
    /// @param value the unsigned byte value
    /// @return whether the byte may be emitted directly
    private static boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '.'
                || value == '_'
                || value == '~';
    }

    /// Contains the compiler fields retained by CLI source-map rewriting.
    ///
    /// @param version the source-map version
    /// @param sources source URLs in mapping order
    /// @param names source-map names
    /// @param mappings the VLQ mappings
    /// @param sourceContents source text aligned with {@code sources}, or
    ///                       {@code null} when omitted
    @NotNullByDefault
    private record Document(
            int version,
            @Unmodifiable List<String> sources,
            @Unmodifiable List<String> names,
            String mappings,
            @Nullable @Unmodifiable List<@Nullable String> sourceContents
    ) {
        /// Creates immutable source-map field snapshots.
        private Document {
            sources = List.copyOf(sources);
            names = List.copyOf(names);
            Objects.requireNonNull(mappings, "mappings");
            sourceContents = sourceContents == null
                    ? null
                    : java.util.Collections.unmodifiableList(
                            new ArrayList<>(sourceContents)
                    );
        }
    }
}
