// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.sassspec;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.glavo.sassfx.DiagnosticSeverity;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.StringReader;
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

        try (var reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            requireToken(
                    reader.peek(),
                    JsonToken.BEGIN_OBJECT,
                    sourceName,
                    "a JSON object"
            );
            reader.beginObject();

            @Nullable Integer format = null;
            @Nullable List<Archive> archives = null;
            var fields = new HashSet<String>();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) {
                    throw malformed(sourceName, "Manifest fields must not be repeated: " + field);
                }

                switch (field) {
                    case "format" -> format = readInteger(reader, sourceName, field);
                    case "archives" -> archives = readArchives(reader, sourceName);
                    default -> throw malformed(sourceName, "Unknown manifest field: " + field);
                }
            }
            reader.endObject();

            if (reader.peek() != JsonToken.END_DOCUMENT) {
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
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @return immutable archive declarations
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<Archive> readArchives(
            JsonReader reader,
            String sourceName
    ) throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.BEGIN_ARRAY,
                sourceName,
                "an archives array"
        );
        reader.beginArray();
        var archives = new ArrayList<Archive>();
        while (reader.hasNext()) {
            archives.add(readArchive(reader, sourceName));
        }
        reader.endArray();
        return List.copyOf(archives);
    }

    /// Reads one archive declaration.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @return the parsed archive declaration
    /// @throws IOException if JSON parsing fails
    private static Archive readArchive(JsonReader reader, String sourceName)
            throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.BEGIN_OBJECT,
                sourceName,
                "an archive object"
        );
        reader.beginObject();
        @Nullable String path = null;
        @Nullable List<Case> cases = null;
        var fields = new HashSet<String>();

        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) {
                throw malformed(sourceName, "Archive fields must not be repeated: " + field);
            }

            switch (field) {
                case "path" -> path = readString(reader, sourceName, field);
                case "cases" -> cases = readCases(reader, sourceName);
                default -> throw malformed(sourceName, "Unknown archive field: " + field);
            }
        }
        reader.endObject();

        return new Archive(
                requireValue(path, "archive path", sourceName),
                requireValue(cases, "archive cases", sourceName)
        );
    }

    /// Reads the case declarations from one JSON array value.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @return immutable case declarations
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<Case> readCases(
            JsonReader reader,
            String sourceName
    ) throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.BEGIN_ARRAY,
                sourceName,
                "a cases array"
        );
        reader.beginArray();
        var cases = new ArrayList<Case>();
        while (reader.hasNext()) {
            cases.add(readCase(reader, sourceName));
        }
        reader.endArray();
        return List.copyOf(cases);
    }

    /// Reads one case declaration.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @return the parsed case declaration
    /// @throws IOException if JSON parsing fails
    private static Case readCase(JsonReader reader, String sourceName)
            throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.BEGIN_OBJECT,
                sourceName,
                "a case object"
        );
        reader.beginObject();
        @Nullable String directory = null;
        @Nullable Action action = null;
        @Nullable String category = null;
        @Nullable String reason = null;
        List<String> loadedUrls = List.of();
        List<DiagnosticExpectation> diagnostics = List.of();
        var fields = new HashSet<String>();

        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) {
                throw malformed(sourceName, "Case fields must not be repeated: " + field);
            }

            switch (field) {
                case "directory" -> directory = readString(reader, sourceName, field);
                case "action" -> action = Action.parse(
                        readString(reader, sourceName, field)
                );
                case "category" -> category = readString(reader, sourceName, field);
                case "reason" -> reason = readString(reader, sourceName, field);
                case "loadedUrls" -> loadedUrls = readStringList(
                        reader,
                        sourceName,
                        field
                );
                case "diagnostics" -> diagnostics = readDiagnostics(
                        reader,
                        sourceName
                );
                default -> throw malformed(sourceName, "Unknown case field: " + field);
            }
        }
        reader.endObject();

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
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @param fieldName the field label used in errors
    /// @return immutable relative paths in declaration order
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<String> readStringList(
            JsonReader reader,
            String sourceName,
            String fieldName
    ) throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.BEGIN_ARRAY,
                sourceName,
                fieldName + " array"
        );
        reader.beginArray();
        var values = new LinkedHashSet<String>();
        while (reader.hasNext()) {
            String value = readString(
                    reader,
                    sourceName,
                    fieldName + " element"
            );
            HrxArchive.validateRelativePath(value, fieldName + " element");
            if (!values.add(value)) {
                throw malformed(sourceName, fieldName + " must not contain duplicate paths.");
            }
        }
        reader.endArray();
        return List.copyOf(values);
    }

    /// Reads the structured diagnostic expectations from a JSON array value.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @return immutable diagnostic expectations
    /// @throws IOException if JSON parsing fails
    private static @Unmodifiable List<DiagnosticExpectation> readDiagnostics(
            JsonReader reader,
            String sourceName
    ) throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.BEGIN_ARRAY,
                sourceName,
                "a diagnostics array"
        );
        reader.beginArray();
        var diagnostics = new ArrayList<DiagnosticExpectation>();
        while (reader.hasNext()) {
            diagnostics.add(readDiagnostic(reader, sourceName));
        }
        reader.endArray();
        return List.copyOf(diagnostics);
    }

    /// Reads one diagnostic expectation.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @return the parsed diagnostic expectation
    /// @throws IOException if JSON parsing fails
    private static DiagnosticExpectation readDiagnostic(
            JsonReader reader,
            String sourceName
    ) throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.BEGIN_OBJECT,
                sourceName,
                "a diagnostic object"
        );
        reader.beginObject();
        @Nullable DiagnosticSeverity severity = null;
        @Nullable String code = null;
        @Nullable String message = null;
        var fields = new HashSet<String>();

        while (reader.hasNext()) {
            String field = reader.nextName();
            if (!fields.add(field)) {
                throw malformed(sourceName, "Diagnostic fields must not be repeated: " + field);
            }

            switch (field) {
                case "severity" -> severity = readSeverity(reader, sourceName);
                case "code" -> code = readString(reader, sourceName, field);
                case "message" -> message = readString(reader, sourceName, field);
                default -> throw malformed(sourceName, "Unknown diagnostic field: " + field);
            }
        }
        reader.endObject();

        return new DiagnosticExpectation(requireValue(severity, "diagnostic severity", sourceName), code, message);
    }

    /// Reads one diagnostic severity enum constant from JSON.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @return the diagnostic severity
    /// @throws IOException if JSON parsing fails
    private static DiagnosticSeverity readSeverity(
            JsonReader reader,
            String sourceName
    ) throws IOException {
        String value = readString(reader, sourceName, "diagnostic severity");
        try {
            return DiagnosticSeverity.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw malformed(sourceName, "Unknown diagnostic severity: " + value);
        }
    }

    /// Reads one required integer field.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @param fieldName the field label used in errors
    /// @return the integer value
    /// @throws IOException if JSON parsing fails
    private static int readInteger(
            JsonReader reader,
            String sourceName,
            String fieldName
    ) throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.NUMBER,
                sourceName,
                fieldName + " integer"
        );
        var value = reader.nextString();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw malformed(
                    sourceName,
                    "Expected " + fieldName + " integer but found " + value + "."
            );
        }
    }

    /// Reads one required string field or array element.
    ///
    /// @param reader the active JSON reader
    /// @param sourceName the manifest label used in errors
    /// @param fieldName the field label used in errors
    /// @return the string value
    /// @throws IOException if JSON parsing fails
    private static String readString(
            JsonReader reader,
            String sourceName,
            String fieldName
    ) throws IOException {
        requireToken(
                reader.peek(),
                JsonToken.STRING,
                sourceName,
                fieldName + " string"
        );
        return reader.nextString();
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

    /// Returns a required nullable decoded value.
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
