// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.sassspec;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.glavo.sassfx.DiagnosticSeverity;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Reads the versioned manifest that selects HRX compatibility fixtures.
@NotNullByDefault
final class SassSpecManifest {
    /// The only manifest format understood by this runner version.
    private static final int SUPPORTED_FORMAT = 1;

    /// Creates streaming JSON parsers for manifest input.
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /// The validated manifest format version.
    private final int format;

    /// The archives and fixture cases selected by the manifest.
    private final @Unmodifiable List<Archive> archives;

    /// Creates one immutable manifest.
    ///
    /// @param format the manifest format version
    /// @param archives the selected archives
    private SassSpecManifest(int format, List<Archive> archives) {
        if (format != SUPPORTED_FORMAT) {
            throw new IllegalArgumentException("Unsupported sass-spec manifest format: " + format);
        }
        if (archives.isEmpty()) {
            throw new IllegalArgumentException("sass-spec manifests must declare at least one archive.");
        }

        var paths = new HashSet<String>();
        for (Archive archive : archives) {
            Objects.requireNonNull(archive, "archive");
            if (!paths.add(archive.path())) {
                throw new IllegalArgumentException("sass-spec manifests must not declare an archive more than once.");
            }
        }
        this.format = format;
        this.archives = List.copyOf(archives);
    }

    /// Parses a manifest from UTF-8 JSON text.
    ///
    /// @param source the complete JSON document
    /// @param sourceName the label used in validation failures
    /// @return the immutable parsed manifest
    /// @throws IOException if the input is not valid JSON
    /// @throws IllegalArgumentException if valid JSON violates the manifest schema
    static SassSpecManifest parse(String source, String sourceName) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceName, "sourceName");

        try (var parser = JSON_FACTORY.createParser(source)) {
            requireToken(nextRequired(parser, sourceName), JsonToken.START_OBJECT, sourceName, "a JSON object");

            @Nullable Integer format = null;
            @Nullable List<Archive> archives = null;
            var fields = new HashSet<String>();
            while (true) {
                JsonToken token = nextRequired(parser, sourceName);
                if (token == JsonToken.END_OBJECT) {
                    break;
                }
                requireToken(token, JsonToken.FIELD_NAME, sourceName, "a manifest field name");
                String field = Objects.requireNonNull(parser.currentName(), "manifest field name");
                if (!fields.add(field)) {
                    throw malformed(sourceName, "Manifest fields must not be repeated: " + field);
                }

                JsonToken valueToken = nextRequired(parser, sourceName);
                switch (field) {
                    case "format" -> format = readInteger(parser, valueToken, sourceName, field);
                    case "archives" -> archives = readArchives(parser, valueToken, sourceName);
                    default -> throw malformed(sourceName, "Unknown manifest field: " + field);
                }
            }

            if (nextOrNull(parser) != null) {
                throw malformed(sourceName, "Manifest input must contain exactly one JSON value.");
            }
            return new SassSpecManifest(
                    requireValue(format, "format", sourceName),
                    requireValue(archives, "archives", sourceName)
            );
        }
    }

    /// Returns the manifest format version.
    ///
    /// @return the validated format version
    int format() {
        return format;
    }

    /// Returns the immutable archive declarations in manifest order.
    ///
    /// @return the selected archives
    @Unmodifiable List<Archive> archives() {
        return archives;
    }

    /// Reads the archive declarations from one JSON array value.
    ///
    /// @param parser the active JSON parser
    /// @param token the current value token
    /// @param sourceName the manifest label used in errors
    /// @return immutable archive declarations
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<Archive> readArchives(
            JsonParser parser,
            JsonToken token,
            String sourceName
    ) throws IOException {
        requireToken(token, JsonToken.START_ARRAY, sourceName, "an archives array");
        var archives = new ArrayList<Archive>();
        while (true) {
            JsonToken element = nextRequired(parser, sourceName);
            if (element == JsonToken.END_ARRAY) {
                return List.copyOf(archives);
            }
            requireToken(element, JsonToken.START_OBJECT, sourceName, "an archive object");
            archives.add(readArchive(parser, sourceName));
        }
    }

    /// Reads one archive declaration after its opening object token.
    ///
    /// @param parser the active JSON parser
    /// @param sourceName the manifest label used in errors
    /// @return the parsed archive declaration
    /// @throws IOException if JSON parsing fails
    private static Archive readArchive(JsonParser parser, String sourceName) throws IOException {
        @Nullable String path = null;
        @Nullable List<Case> cases = null;
        var fields = new HashSet<String>();

        while (true) {
            JsonToken token = nextRequired(parser, sourceName);
            if (token == JsonToken.END_OBJECT) {
                break;
            }
            requireToken(token, JsonToken.FIELD_NAME, sourceName, "an archive field name");
            String field = Objects.requireNonNull(parser.currentName(), "archive field name");
            if (!fields.add(field)) {
                throw malformed(sourceName, "Archive fields must not be repeated: " + field);
            }

            JsonToken valueToken = nextRequired(parser, sourceName);
            switch (field) {
                case "path" -> path = readString(parser, valueToken, sourceName, field);
                case "cases" -> cases = readCases(parser, valueToken, sourceName);
                default -> throw malformed(sourceName, "Unknown archive field: " + field);
            }
        }

        return new Archive(
                requireValue(path, "archive path", sourceName),
                requireValue(cases, "archive cases", sourceName)
        );
    }

    /// Reads the case declarations from one JSON array value.
    ///
    /// @param parser the active JSON parser
    /// @param token the current value token
    /// @param sourceName the manifest label used in errors
    /// @return immutable case declarations
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<Case> readCases(
            JsonParser parser,
            JsonToken token,
            String sourceName
    ) throws IOException {
        requireToken(token, JsonToken.START_ARRAY, sourceName, "a cases array");
        var cases = new ArrayList<Case>();
        while (true) {
            JsonToken element = nextRequired(parser, sourceName);
            if (element == JsonToken.END_ARRAY) {
                return List.copyOf(cases);
            }
            requireToken(element, JsonToken.START_OBJECT, sourceName, "a case object");
            cases.add(readCase(parser, sourceName));
        }
    }

    /// Reads one case declaration after its opening object token.
    ///
    /// @param parser the active JSON parser
    /// @param sourceName the manifest label used in errors
    /// @return the parsed case declaration
    /// @throws IOException if JSON parsing fails
    private static Case readCase(JsonParser parser, String sourceName) throws IOException {
        @Nullable String directory = null;
        @Nullable Action action = null;
        @Nullable String category = null;
        @Nullable String reason = null;
        List<String> loadedUrls = List.of();
        List<DiagnosticExpectation> diagnostics = List.of();
        var fields = new HashSet<String>();

        while (true) {
            JsonToken token = nextRequired(parser, sourceName);
            if (token == JsonToken.END_OBJECT) {
                break;
            }
            requireToken(token, JsonToken.FIELD_NAME, sourceName, "a case field name");
            String field = Objects.requireNonNull(parser.currentName(), "case field name");
            if (!fields.add(field)) {
                throw malformed(sourceName, "Case fields must not be repeated: " + field);
            }

            JsonToken valueToken = nextRequired(parser, sourceName);
            switch (field) {
                case "directory" -> directory = readString(parser, valueToken, sourceName, field);
                case "action" -> action = Action.parse(readString(parser, valueToken, sourceName, field));
                case "category" -> category = readString(parser, valueToken, sourceName, field);
                case "reason" -> reason = readString(parser, valueToken, sourceName, field);
                case "loadedUrls" -> loadedUrls = readStringList(parser, valueToken, sourceName, field);
                case "diagnostics" -> diagnostics = readDiagnostics(parser, valueToken, sourceName);
                default -> throw malformed(sourceName, "Unknown case field: " + field);
            }
        }

        return new Case(
                requireValue(directory, "case directory", sourceName),
                requireValue(action, "case action", sourceName),
                requireValue(category, "case category", sourceName),
                reason,
                loadedUrls,
                diagnostics
        );
    }

    /// Reads a duplicate-free array of relative paths.
    ///
    /// @param parser the active JSON parser
    /// @param token the current value token
    /// @param sourceName the manifest label used in errors
    /// @param fieldName the field label used in errors
    /// @return immutable relative paths in declaration order
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<String> readStringList(
            JsonParser parser,
            JsonToken token,
            String sourceName,
            String fieldName
    ) throws IOException {
        requireToken(token, JsonToken.START_ARRAY, sourceName, fieldName + " array");
        var values = new LinkedHashSet<String>();
        while (true) {
            JsonToken element = nextRequired(parser, sourceName);
            if (element == JsonToken.END_ARRAY) {
                return List.copyOf(values);
            }
            String value = readString(parser, element, sourceName, fieldName + " element");
            HrxArchive.validateRelativePath(value, fieldName + " element");
            if (!values.add(value)) {
                throw malformed(sourceName, fieldName + " must not contain duplicate paths.");
            }
        }
    }

    /// Reads the structured diagnostic expectations from a JSON array value.
    ///
    /// @param parser the active JSON parser
    /// @param token the current value token
    /// @param sourceName the manifest label used in errors
    /// @return immutable diagnostic expectations
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<DiagnosticExpectation> readDiagnostics(
            JsonParser parser,
            JsonToken token,
            String sourceName
    ) throws IOException {
        requireToken(token, JsonToken.START_ARRAY, sourceName, "a diagnostics array");
        var diagnostics = new ArrayList<DiagnosticExpectation>();
        while (true) {
            JsonToken element = nextRequired(parser, sourceName);
            if (element == JsonToken.END_ARRAY) {
                return List.copyOf(diagnostics);
            }
            requireToken(element, JsonToken.START_OBJECT, sourceName, "a diagnostic object");
            diagnostics.add(readDiagnostic(parser, sourceName));
        }
    }

    /// Reads one diagnostic expectation after its opening object token.
    ///
    /// @param parser the active JSON parser
    /// @param sourceName the manifest label used in errors
    /// @return the parsed diagnostic expectation
    /// @throws IOException if JSON parsing fails
    private static DiagnosticExpectation readDiagnostic(JsonParser parser, String sourceName) throws IOException {
        @Nullable DiagnosticSeverity severity = null;
        @Nullable String code = null;
        @Nullable String message = null;
        var fields = new HashSet<String>();

        while (true) {
            JsonToken token = nextRequired(parser, sourceName);
            if (token == JsonToken.END_OBJECT) {
                break;
            }
            requireToken(token, JsonToken.FIELD_NAME, sourceName, "a diagnostic field name");
            String field = Objects.requireNonNull(parser.currentName(), "diagnostic field name");
            if (!fields.add(field)) {
                throw malformed(sourceName, "Diagnostic fields must not be repeated: " + field);
            }

            JsonToken valueToken = nextRequired(parser, sourceName);
            switch (field) {
                case "severity" -> severity = readSeverity(parser, valueToken, sourceName);
                case "code" -> code = readString(parser, valueToken, sourceName, field);
                case "message" -> message = readString(parser, valueToken, sourceName, field);
                default -> throw malformed(sourceName, "Unknown diagnostic field: " + field);
            }
        }

        return new DiagnosticExpectation(requireValue(severity, "diagnostic severity", sourceName), code, message);
    }

    /// Reads one diagnostic severity enum constant from JSON.
    ///
    /// @param parser the active JSON parser
    /// @param token the current value token
    /// @param sourceName the manifest label used in errors
    /// @return the diagnostic severity
    /// @throws IOException if JSON parsing fails
    private static DiagnosticSeverity readSeverity(
            JsonParser parser,
            JsonToken token,
            String sourceName
    ) throws IOException {
        String value = readString(parser, token, sourceName, "diagnostic severity");
        try {
            return DiagnosticSeverity.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw malformed(sourceName, "Unknown diagnostic severity: " + value);
        }
    }

    /// Reads one required integer field.
    ///
    /// @param parser the active JSON parser
    /// @param token the current value token
    /// @param sourceName the manifest label used in errors
    /// @param fieldName the field label used in errors
    /// @return the integer value
    /// @throws IOException if JSON parsing fails
    private static int readInteger(
            JsonParser parser,
            JsonToken token,
            String sourceName,
            String fieldName
    ) throws IOException {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, sourceName, fieldName + " integer");
        return parser.getIntValue();
    }

    /// Reads one required string field or array element.
    ///
    /// @param parser the active JSON parser
    /// @param token the current value token
    /// @param sourceName the manifest label used in errors
    /// @param fieldName the field label used in errors
    /// @return the string value
    /// @throws IOException if JSON parsing fails
    private static String readString(
            JsonParser parser,
            JsonToken token,
            String sourceName,
            String fieldName
    ) throws IOException {
        requireToken(token, JsonToken.VALUE_STRING, sourceName, fieldName + " string");
        return parser.getText();
    }

    /// Returns the next JSON token or reports an unexpected end of input.
    ///
    /// @param parser the active JSON parser
    /// @param sourceName the manifest label used in errors
    /// @return the next non-null token
    /// @throws IOException if JSON parsing fails
    private static JsonToken nextRequired(JsonParser parser, String sourceName) throws IOException {
        @Nullable JsonToken token = nextOrNull(parser);
        if (token == null) {
            throw malformed(sourceName, "Unexpected end of JSON input.");
        }
        return token;
    }

    /// Returns the next JSON token, including an end-of-input sentinel.
    ///
    /// @param parser the active JSON parser
    /// @return the next token, or {@code null} at end of input
    /// @throws IOException if JSON parsing fails
    private static @Nullable JsonToken nextOrNull(JsonParser parser) throws IOException {
        return parser.nextToken();
    }

    /// Verifies that a token has the expected JSON kind.
    ///
    /// @param actual the token to inspect
    /// @param expected the required token kind
    /// @param sourceName the manifest label used in errors
    /// @param expectedDescription the human-readable token requirement
    private static void requireToken(
            JsonToken actual,
            JsonToken expected,
            String sourceName,
            String expectedDescription
    ) {
        if (actual != expected) {
            throw malformed(sourceName, "Expected " + expectedDescription + " but found " + actual + ".");
        }
    }

    /// Returns a required nullable parser result.
    ///
    /// @param value the result to validate
    /// @param fieldName the absent field label used in errors
    /// @param sourceName the manifest label used in errors
    /// @param <T> the value type
    /// @return the non-null value
    private static <T> T requireValue(@Nullable T value, String fieldName, String sourceName) {
        if (value == null) {
            throw malformed(sourceName, "Missing required field: " + fieldName);
        }
        return value;
    }

    /// Creates one manifest-schema validation exception.
    ///
    /// @param sourceName the manifest label
    /// @param message the detailed validation failure
    /// @return the exception to throw
    private static IllegalArgumentException malformed(String sourceName, String message) {
        return new IllegalArgumentException(sourceName + ": " + message);
    }

    /// Describes one archive selected by the manifest.
    ///
    /// @param path the relative classpath resource path of the HRX archive
    /// @param cases the immutable selected cases in archive order
    @NotNullByDefault
    record Archive(String path, @Unmodifiable List<Case> cases) {
        /// Creates one archive declaration.
        Archive {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(cases, "cases");
            HrxArchive.validateRelativePath(path, "archive path");
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("Archive declarations must contain at least one case.");
            }

            var directories = new HashSet<String>();
            for (Case fixture : cases) {
                Objects.requireNonNull(fixture, "case");
                if (!directories.add(fixture.directory())) {
                    throw new IllegalArgumentException("Archive declarations must not repeat a case directory.");
                }
            }
            cases = List.copyOf(cases);
        }
    }

    /// Selects whether a fixture is executed or classified as skipped.
    @NotNullByDefault
    enum Action {
        /// Executes the fixture and compares its declared expectations.
        RUN,

        /// Aborts the fixture with its explicit compatibility category.
        SKIP;

        /// Parses a manifest action string.
        ///
        /// @param value the JSON action value
        /// @return the selected action
        /// @throws IllegalArgumentException if the value is not recognized
        static Action parse(String value) {
            return switch (value) {
                case "run" -> RUN;
                case "skip" -> SKIP;
                default -> throw new IllegalArgumentException("Unknown sass-spec case action: " + value);
            };
        }
    }

    /// Describes one selected virtual fixture directory.
    ///
    /// @param directory the relative directory containing the fixture files
    /// @param action whether the fixture runs or is skipped
    /// @param category the compatibility capability or skip category
    /// @param reason the explicit skip reason, or {@code null} for run cases
    /// @param loadedUrls the expected loaded stylesheet paths for successful cases
    /// @param diagnostics the expected non-error diagnostics for successful cases
    @NotNullByDefault
    record Case(
            String directory,
            Action action,
            String category,
            @Nullable String reason,
            @Unmodifiable List<String> loadedUrls,
            @Unmodifiable List<DiagnosticExpectation> diagnostics
    ) {
        /// Creates one fixture selection.
        Case {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(loadedUrls, "loadedUrls");
            Objects.requireNonNull(diagnostics, "diagnostics");
            HrxArchive.validateRelativePath(directory, "case directory");
            if (category.isBlank()) {
                throw new IllegalArgumentException("Case categories must not be blank.");
            }
            if (action == Action.SKIP && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("Skipped cases must declare a nonblank reason.");
            }
            if (action == Action.RUN && reason != null) {
                throw new IllegalArgumentException("Run cases must not declare a skip reason.");
            }
            if (action == Action.SKIP && (!loadedUrls.isEmpty() || !diagnostics.isEmpty())) {
                throw new IllegalArgumentException("Skipped cases must not declare execution expectations.");
            }

            var paths = new HashSet<String>();
            for (String loadedUrl : loadedUrls) {
                Objects.requireNonNull(loadedUrl, "loadedUrl");
                HrxArchive.validateRelativePath(loadedUrl, "loaded URL");
                if (!paths.add(loadedUrl)) {
                    throw new IllegalArgumentException("Case loaded URLs must not contain duplicates.");
                }
            }
            for (DiagnosticExpectation diagnostic : diagnostics) {
                Objects.requireNonNull(diagnostic, "diagnostic");
            }
            loadedUrls = List.copyOf(loadedUrls);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /// Describes one structured diagnostic expected from a successful fixture.
    ///
    /// @param severity the required diagnostic severity
    /// @param code the required stable diagnostic code, or {@code null} when unchecked
    /// @param message the required message, or {@code null} when unchecked
    @NotNullByDefault
    record DiagnosticExpectation(
            DiagnosticSeverity severity,
            @Nullable String code,
            @Nullable String message
    ) {
        /// Creates one diagnostic expectation.
        DiagnosticExpectation {
            Objects.requireNonNull(severity, "severity");
            if (code != null && code.isBlank()) {
                throw new IllegalArgumentException("Diagnostic codes must not be blank.");
            }
            if (message != null && message.isBlank()) {
                throw new IllegalArgumentException("Diagnostic messages must not be blank.");
            }
        }
    }
}
