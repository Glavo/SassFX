// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.glavo.sassfx.internal.module.FilesystemImporter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Resolves {@code pkg:} URLs using Node package lookup and Sass package
/// metadata conventions.
///
/// Resolution begins beside the containing file when one is available and
/// otherwise at the configured entry-point directory. Each ancestor is
/// searched for {@code node_modules/<package>/package.json}. Package
/// {@code exports} are resolved before the {@code sass} and {@code style}
/// fields and filesystem subpaths.
///
/// Instances are immutable and safe for concurrent compilations. Package
/// manifests and stylesheet files are read for each canonicalization or load;
/// one compiler invocation still applies its normal canonical-URL load cache.
@NotNullByDefault
public final class SassNodePackageImporter implements SassImporter {
    /// Stylesheet extensions accepted for package exports and entry points.
    private static final List<String> VALID_EXTENSIONS =
            List.of(".scss", ".sass", ".css");

    /// Conditions recognized in package export target objects.
    private static final List<String> EXPORT_CONDITIONS =
            List.of("sass", "style", "default");

    /// The absolute base used when a request has no file containing URL.
    private final Path entryPointDirectory;

    /// Creates an importer rooted at the supplied entry-point directory.
    ///
    /// The path is made absolute and normalized but is not required to exist
    /// until a package request is resolved.
    ///
    /// @param entryPointDirectory the fallback directory for package lookup
    public SassNodePackageImporter(Path entryPointDirectory) {
        this.entryPointDirectory = Objects.requireNonNull(
                entryPointDirectory,
                "entryPointDirectory"
        ).toAbsolutePath().normalize();
    }

    /// Returns the fallback package lookup directory.
    ///
    /// @return the absolute normalized entry-point directory
    public Path entryPointDirectory() {
        return entryPointDirectory;
    }

    /// Reports {@code pkg} as a contextual, non-canonical URL scheme.
    ///
    /// @param scheme the requested absolute URL scheme
    /// @return whether {@code scheme} is {@code pkg}, ignoring case
    @Override
    public boolean isNonCanonicalScheme(String scheme) {
        Objects.requireNonNull(scheme, "scheme");
        return "pkg".equalsIgnoreCase(scheme);
    }

    /// Resolves a {@code pkg:} URL or a file URL loaded relative to a package
    /// stylesheet.
    ///
    /// Non-{@code pkg:} and non-{@code file:} URLs are declined. Invalid
    /// {@code pkg:} URL structure and invalid package metadata fail the
    /// compilation rather than falling through to another importer.
    ///
    /// @param url the requested Sass URL
    /// @param context contextual information about the load
    /// @return an absolute canonical file URL, or {@code null} when unresolved
    /// @throws IOException if package metadata or the filesystem cannot be read
    @Override
    public @Nullable URI canonicalize(
            URI url,
            SassCanonicalizeContext context
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(context, "context");

        if ("file".equalsIgnoreCase(url.getScheme())) {
            @Nullable Path resolved = FilesystemImporter.resolveAt(
                    filePath(url),
                    context.fromImport()
            );
            return resolved == null ? null : canonicalFileUrl(resolved);
        }
        if (!"pkg".equalsIgnoreCase(url.getScheme())) {
            return null;
        }

        var specifier = packageSpecifier(url);
        var split = splitSpecifier(specifier);
        if (!validPackageName(split.packageName())) {
            return null;
        }

        var baseDirectory = baseDirectory(context.containingUrl());
        @Nullable Path packageRoot = resolvePackageRoot(
                split.packageName(),
                baseDirectory
        );
        if (packageRoot == null) {
            return null;
        }

        var manifestPath = packageRoot.resolve("package.json");
        var manifest = readManifest(
                manifestPath,
                split.packageName()
        );
        @Nullable Path exported = resolvePackageExports(
                packageRoot,
                split.subpath(),
                manifest,
                split.packageName(),
                context.fromImport()
        );
        if (exported != null) {
            if (!validExtension(exported)) {
                throw new IllegalStateException(
                        "The export for '"
                                + Objects.requireNonNullElse(
                                split.subpath(),
                                "root"
                        )
                                + "' in '" + split.packageName()
                                + "' resolved to '" + exported
                                + "', which is not a '.scss', '.sass', or "
                                + "'.css' file."
                );
            }
            return canonicalFileUrl(exported);
        }

        if (split.subpath() == null) {
            @Nullable Path rootValue = resolvePackageRootValue(
                    packageRoot,
                    manifest,
                    context.fromImport()
            );
            return rootValue == null ? null : canonicalFileUrl(rootValue);
        }

        @Nullable Path subpath = FilesystemImporter.resolveAt(
                resolvePackagePath(
                        packageRoot,
                        split.subpath(),
                        "Package subpath '" + split.subpath() + "'"
                ),
                context.fromImport()
        );
        return subpath == null ? null : canonicalFileUrl(subpath);
    }

    /// Loads an exact canonical file URL returned by
    /// [#canonicalize(URI, SassCanonicalizeContext)].
    ///
    /// @param canonicalUrl the canonical file URL
    /// @return the decoded stylesheet and its file URL for source maps
    /// @throws IOException if the file cannot be read
    /// @throws IllegalArgumentException if the URL is not a plain file URL
    @Override
    public SassImporterResult load(URI canonicalUrl)
            throws IOException {
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        var path = filePath(canonicalUrl);
        var syntax = Syntax.forPath(path);
        if (syntax == null) {
            throw new IOException(
                    "Package stylesheet has an unsupported extension: " + path
            );
        }
        return new SassImporterResult(
                Files.readString(path, StandardCharsets.UTF_8),
                syntax,
                canonicalUrl
        );
    }

    /// Returns the directory used for one package lookup.
    ///
    /// @param containingUrl the containing canonical URL, or {@code null}
    /// @return the containing file directory or configured entry-point base
    private Path baseDirectory(@Nullable URI containingUrl) {
        if (containingUrl != null
                && "file".equalsIgnoreCase(containingUrl.getScheme())) {
            @Nullable Path parent = filePath(containingUrl).getParent();
            if (parent != null) {
                return parent;
            }
        }
        return entryPointDirectory;
    }

    /// Validates and returns the raw package specifier from a {@code pkg:} URL.
    ///
    /// @param url the package URL
    /// @return the raw path after the scheme
    private static String packageSpecifier(URI url) {
        if (url.getRawAuthority() != null) {
            throw new IllegalStateException(
                    "A pkg: URL must not have a host, port, username or password."
            );
        }

        @Nullable String rawSpecifier = url.isOpaque()
                ? url.getRawSchemeSpecificPart()
                : url.getRawPath();
        if (rawSpecifier == null) {
            rawSpecifier = "";
        }
        var queryIndex = rawSpecifier.indexOf('?');
        var specifier = queryIndex < 0
                ? rawSpecifier
                : rawSpecifier.substring(0, queryIndex);
        var hasQueryOrFragment = queryIndex >= 0
                || url.getRawQuery() != null
                || url.getRawFragment() != null;
        if (specifier.startsWith("/")) {
            throw new IllegalStateException(
                    "A pkg: URL's path must not begin with /."
            );
        }
        if (specifier.isEmpty()) {
            throw new IllegalStateException(
                    "A pkg: URL must not have an empty path."
            );
        }
        if (hasQueryOrFragment) {
            throw new IllegalStateException(
                    "A pkg: URL must not have a query or fragment."
            );
        }
        return specifier;
    }

    /// Splits a bare package specifier into its package name and subpath.
    ///
    /// @param specifier the raw slash-separated package specifier
    /// @return the package name and optional subpath
    private static PackageSpecifier splitSpecifier(String specifier) {
        var parts = new ArrayList<>(List.of(specifier.split("/", -1)));
        var packageName = parts.remove(0);
        if (packageName.startsWith("@") && !parts.isEmpty()) {
            packageName += "/" + parts.remove(0);
        }
        @Nullable String subpath = parts.isEmpty()
                ? null
                : String.join("/", parts);
        return new PackageSpecifier(packageName, subpath);
    }

    /// Tests whether a package name is eligible for Node lookup.
    ///
    /// @param packageName the unescaped package name
    /// @return whether the name is structurally valid
    private static boolean validPackageName(String packageName) {
        return !packageName.startsWith(".")
                && packageName.indexOf('\\') < 0
                && packageName.indexOf('%') < 0
                && (!packageName.startsWith("@")
                || packageName.indexOf('/') >= 0);
    }

    /// Finds the closest installed package directory.
    ///
    /// @param packageName the bare package name
    /// @param baseDirectory the directory where ancestor lookup begins
    /// @return the package root, or {@code null} when not installed
    private static @Nullable Path resolvePackageRoot(
            String packageName,
            Path baseDirectory
    ) {
        @Nullable Path current = baseDirectory.toAbsolutePath().normalize();
        var packagePath = nativePath(packageName);
        while (current != null) {
            var candidate = current.resolve("node_modules").resolve(packagePath);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    /// Reads and parses a package manifest object.
    ///
    /// @param manifestPath the package.json path
    /// @param packageName the package name used in diagnostics
    /// @return the insertion-ordered manifest object
    /// @throws IOException if the file cannot be read or parsed
    private static Map<String, @Nullable Object> readManifest(
            Path manifestPath,
            String packageName
    ) throws IOException {
        var json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            @Nullable Object value = readJsonValue(reader);
            if (!(value instanceof Map<?, ?> rawMap)
                    || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("package manifest must be one JSON object");
            }
            return stringMap(rawMap);
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
                yield values;
            }
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var values = new LinkedHashMap<String, @Nullable Object>();
                while (reader.hasNext()) {
                    values.put(reader.nextName(), readJsonValue(reader));
                }
                reader.endObject();
                yield values;
            }
            default -> throw new IOException(
                    "unsupported token in package manifest: " + reader.peek()
            );
        };
    }

    /// Converts a JSON object to its string-keyed representation.
    ///
    /// @param rawMap the parsed JSON object
    /// @return the typed insertion-ordered object
    private static Map<String, @Nullable Object> stringMap(Map<?, ?> rawMap) {
        var result = new LinkedHashMap<String, @Nullable Object>();
        for (var entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException(
                        "package manifest object key is not a string"
                );
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    /// Resolves the root {@code sass}, {@code style}, or index entry point.
    ///
    /// @param packageRoot the installed package root
    /// @param manifest the parsed package manifest
    /// @param fromImport whether import-only files take precedence
    /// @return the resolved entry point, or {@code null}
    private static @Nullable Path resolvePackageRootValue(
            Path packageRoot,
            Map<String, @Nullable Object> manifest,
            boolean fromImport
    ) {
        @Nullable Object sass = manifest.get("sass");
        if (sass instanceof String sassValue
                && validExtension(sassValue)) {
            return resolveImportOnly(
                    resolvePackagePath(
                            packageRoot,
                            stripRelativePrefix(sassValue),
                            "The 'sass' field"
                    ),
                    fromImport
            );
        }
        @Nullable Object style = manifest.get("style");
        if (style instanceof String styleValue
                && validExtension(styleValue)) {
            return resolveImportOnly(
                    resolvePackagePath(
                            packageRoot,
                            stripRelativePrefix(styleValue),
                            "The 'style' field"
                    ),
                    fromImport
            );
        }
        return FilesystemImporter.resolveAt(
                packageRoot.resolve("index"),
                fromImport
        );
    }

    /// Resolves the package exports field for a root or subpath request.
    ///
    /// @param packageRoot the installed package root
    /// @param subpath the requested subpath, or {@code null}
    /// @param manifest the parsed package manifest
    /// @param packageName the package name used in diagnostics
    /// @param fromImport whether import-only files take precedence
    /// @return the exported file, or {@code null}
    private static @Nullable Path resolvePackageExports(
            Path packageRoot,
            @Nullable String subpath,
            Map<String, @Nullable Object> manifest,
            String packageName,
            boolean fromImport
    ) {
        @Nullable Object exports = manifest.get("exports");
        if (exports == null) {
            return null;
        }

        @Nullable Path direct = nodePackageExportsResolve(
                packageRoot,
                exportsToCheck(subpath, false),
                exports,
                subpath,
                packageName
        );
        if (direct != null) {
            return resolveImportOnly(direct, fromImport);
        }
        if (subpath != null && !extension(subpath).isEmpty()) {
            return null;
        }

        @Nullable Path index = nodePackageExportsResolve(
                packageRoot,
                exportsToCheck(subpath, true),
                exports,
                subpath,
                packageName
        );
        return index == null ? null : resolveImportOnly(index, fromImport);
    }

    /// Resolves all variants of one subpath against package exports.
    ///
    /// @param packageRoot the installed package root
    /// @param variants extension and partial variants to test
    /// @param exports the exports JSON value
    /// @param subpath the original requested subpath, or {@code null}
    /// @param packageName the package name used in diagnostics
    /// @return the unique matching path, or {@code null}
    private static @Nullable Path nodePackageExportsResolve(
            Path packageRoot,
            List<@Nullable String> variants,
            Object exports,
            @Nullable String subpath,
            String packageName
    ) {
        if (exports instanceof Map<?, ?> rawMap) {
            var map = stringMap(rawMap);
            var hasPath = map.keySet().stream()
                    .anyMatch(key -> key.startsWith("."));
            var hasCondition = map.keySet().stream()
                    .anyMatch(key -> !key.startsWith("."));
            if (hasPath && hasCondition) {
                throw new IllegalStateException(
                        "`exports` in " + packageName
                                + " can not have both conditions and paths "
                                + "at the same level.\nFound "
                                + String.join(",", map.keySet())
                                + " in " + packageRoot.resolve("package.json")
                                + "."
                );
            }
        }

        var matches = new LinkedHashSet<Path>();
        for (@Nullable var variant : variants) {
            if (variant == null) {
                @Nullable Object main = mainExport(exports);
                if (main != null) {
                    @Nullable Path resolved = packageTargetResolve(
                            main,
                            packageRoot,
                            null
                    );
                    if (resolved != null) {
                        matches.add(resolved.normalize());
                    }
                }
                continue;
            }
            if (!(exports instanceof Map<?, ?> rawMap)) {
                continue;
            }
            var map = stringMap(rawMap);
            if (map.keySet().stream().noneMatch(key -> key.startsWith("."))) {
                continue;
            }

            var matchKey = "./" + slashPath(variant);
            if (map.containsKey(matchKey)
                    && map.get(matchKey) != null
                    && matchKey.indexOf('*') < 0) {
                @Nullable Path resolved = packageTargetResolve(
                        map.get(matchKey),
                        packageRoot,
                        null
                );
                if (resolved != null) {
                    matches.add(resolved.normalize());
                }
                continue;
            }

            var expansionKeys = map.keySet().stream()
                    .filter(key -> countAsterisks(key) == 1)
                    .sorted(SassNodePackageImporter::compareExpansionKeys)
                    .toList();
            for (var expansionKey : expansionKeys) {
                var star = expansionKey.indexOf('*');
                var patternBase = expansionKey.substring(0, star);
                var patternTrailer = expansionKey.substring(star + 1);
                if (!matchKey.startsWith(patternBase)
                        || matchKey.equals(patternBase)) {
                    continue;
                }
                if (!patternTrailer.isEmpty()
                        && (!matchKey.endsWith(patternTrailer)
                        || matchKey.length() < expansionKey.length())) {
                    continue;
                }
                @Nullable Object target = map.get(expansionKey);
                if (target == null) {
                    continue;
                }
                var patternMatch = matchKey.substring(
                        patternBase.length(),
                        matchKey.length() - patternTrailer.length()
                );
                @Nullable Path resolved = packageTargetResolve(
                        target,
                        packageRoot,
                        patternMatch
                );
                if (resolved != null) {
                    matches.add(resolved.normalize());
                }
                break;
            }
        }

        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Unable to determine which of multiple potential "
                            + "resolutions found for "
                            + Objects.requireNonNullElse(subpath, "root")
                            + " in " + packageName + " should be used.\n\n"
                            + "Found:\n"
                            + String.join(
                            "\n",
                            matches.stream().map(Path::toString).toList()
                    )
            );
        }
        return matches.isEmpty() ? null : matches.iterator().next();
    }

    /// Chooses an exact import-only sibling when legacy import semantics apply.
    ///
    /// @param path the exported or manifest-selected stylesheet
    /// @param fromImport whether the load originates from {@code @import}
    /// @return the import-only sibling when present, otherwise {@code path}
    private static Path resolveImportOnly(Path path, boolean fromImport) {
        if (!fromImport) {
            return path;
        }
        var name = path.getFileName().toString();
        var dot = name.lastIndexOf('.');
        if (dot < 0) {
            return path;
        }
        var importOnly = path.resolveSibling(
                name.substring(0, dot) + ".import" + name.substring(dot)
        );
        return Files.isRegularFile(importOnly) ? importOnly : path;
    }

    /// Compares export pattern keys using Node's pattern-key precedence.
    ///
    /// @param first the first pattern key
    /// @param second the second pattern key
    /// @return a negative value when {@code first} has higher precedence
    private static int compareExpansionKeys(String first, String second) {
        var firstStar = first.indexOf('*');
        var secondStar = second.indexOf('*');
        var firstBaseLength = firstStar >= 0 ? firstStar + 1 : first.length();
        var secondBaseLength = secondStar >= 0
                ? secondStar + 1
                : second.length();
        if (firstBaseLength != secondBaseLength) {
            return Integer.compare(secondBaseLength, firstBaseLength);
        }
        if (firstStar < 0) {
            return 1;
        }
        if (secondStar < 0) {
            return -1;
        }
        return Integer.compare(second.length(), first.length());
    }

    /// Resolves one package export target.
    ///
    /// @param target the export target JSON value
    /// @param packageRoot the installed package root
    /// @param patternMatch wildcard replacement text, or {@code null}
    /// @return the first resolvable target path, or {@code null}
    private static @Nullable Path packageTargetResolve(
            Object target,
            Path packageRoot,
            @Nullable String patternMatch
    ) {
        if (target instanceof String string) {
            if (!string.startsWith("./")) {
                throw new IllegalStateException(
                        "Export '" + string
                                + "' must be a path relative to the package "
                                + "root at '" + packageRoot + "'."
                );
            }
            var replaced = patternMatch == null
                    ? string
                    : replaceFirstStar(string, patternMatch);
            var path = resolvePackagePath(
                    packageRoot,
                    replaced.substring(2),
                    "Export '" + string + "'"
            );
            if (patternMatch != null) {
                return Files.isRegularFile(path) ? path : null;
            }
            return path;
        }
        if (target instanceof Map<?, ?> rawMap) {
            var map = stringMap(rawMap);
            for (var entry : map.entrySet()) {
                if (!EXPORT_CONDITIONS.contains(entry.getKey())
                        || entry.getValue() == null) {
                    continue;
                }
                @Nullable Path resolved = packageTargetResolve(
                        entry.getValue(),
                        packageRoot,
                        patternMatch
                );
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        }
        if (target instanceof List<?> list) {
            for (@Nullable var value : list) {
                if (value == null) {
                    continue;
                }
                @Nullable Path resolved = packageTargetResolve(
                        value,
                        packageRoot,
                        patternMatch
                );
                if (resolved != null) {
                    return resolved;
                }
            }
            return null;
        }
        throw new IllegalStateException(
                "Invalid 'exports' value " + target + " in "
                        + packageRoot.resolve("package.json") + "."
        );
    }

    /// Removes the conventional `./` prefix from a manifest path.
    ///
    /// @param value the manifest path
    /// @return the path without one leading `./`
    private static String stripRelativePrefix(String value) {
        return value.startsWith("./") ? value.substring(2) : value;
    }

    /// Resolves a package-relative path without allowing it to escape the
    /// installed package directory.
    ///
    /// Empty, current-directory, parent-directory, `node_modules`, encoded
    /// separator, and native-separator segments are rejected. These checks
    /// implement the path boundary required by Node package target resolution
    /// before the platform filesystem interprets the path.
    ///
    /// @param packageRoot the normalized absolute installed package directory
    /// @param value the slash-separated package-relative path
    /// @param description the value description used in diagnostics
    /// @return the normalized path within {@code packageRoot}
    /// @throws IllegalStateException if the value is not a safe package path
    private static Path resolvePackagePath(
            Path packageRoot,
            String value,
            String description
    ) {
        if (containsEncodedSeparator(value)) {
            throw invalidPackagePath(description, packageRoot);
        }
        var decoded = percentDecode(value);
        if (decoded.indexOf('\\') >= 0) {
            throw invalidPackagePath(description, packageRoot);
        }
        for (var segment : decoded.split("/", -1)) {
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")
                    || segment.equals("node_modules")) {
                throw invalidPackagePath(description, packageRoot);
            }
        }

        var resolved = packageRoot.resolve(nativePath(value)).normalize();
        if (!resolved.startsWith(packageRoot)) {
            throw invalidPackagePath(description, packageRoot);
        }
        return resolved;
    }

    /// Returns whether a path contains a percent-encoded slash or backslash.
    ///
    /// @param value the raw path
    /// @return whether a separator is encoded
    private static boolean containsEncodedSeparator(String value) {
        for (var index = 0; index + 2 < value.length(); index++) {
            if (value.charAt(index) != '%') {
                continue;
            }
            var high = Character.digit(value.charAt(index + 1), 16);
            var low = Character.digit(value.charAt(index + 2), 16);
            if (high >= 0 && low >= 0) {
                var decoded = high << 4 | low;
                if (decoded == '/' || decoded == '\\') {
                    return true;
                }
            }
        }
        return false;
    }

    /// Creates the standard package-boundary failure.
    ///
    /// @param description the rejected value description
    /// @param packageRoot the installed package root
    /// @return the path-validation failure
    private static IllegalStateException invalidPackagePath(
            String description,
            Path packageRoot
    ) {
        return new IllegalStateException(
                description + " must be a path within the package root at '"
                        + packageRoot + "'."
        );
    }

    /// Returns the main export target represented by an exports value.
    ///
    /// @param exports the exports JSON value
    /// @return the main target, or {@code null} when none is declared
    private static @Nullable Object mainExport(Object exports) {
        if (exports instanceof String) {
            return exports;
        }
        if (exports instanceof List<?> list
                && list.stream().allMatch(String.class::isInstance)) {
            return list;
        }
        if (exports instanceof Map<?, ?> rawMap) {
            var map = stringMap(rawMap);
            if (map.keySet().stream().noneMatch(key -> key.startsWith("."))) {
                return map;
            }
            return map.get(".");
        }
        return null;
    }

    /// Generates extension, partial, and optional index export variants.
    ///
    /// @param subpath the requested package subpath, or {@code null}
    /// @param addIndex whether index variants are generated
    /// @return ordered variants, using {@code null} for the root main export
    private static List<@Nullable String> exportsToCheck(
            @Nullable String subpath,
            boolean addIndex
    ) {
        if (subpath == null && addIndex) {
            subpath = "index";
        } else if (subpath != null && addIndex) {
            subpath += "/index";
        }
        if (subpath == null) {
            return java.util.Collections.singletonList(null);
        }

        var paths = new ArrayList<String>();
        if (VALID_EXTENSIONS.contains(extension(subpath))) {
            paths.add(subpath);
        } else {
            paths.add(subpath);
            paths.add(subpath + ".scss");
            paths.add(subpath + ".sass");
            paths.add(subpath + ".css");
        }

        var slash = subpath.lastIndexOf('/');
        var basename = slash < 0 ? subpath : subpath.substring(slash + 1);
        if (basename.startsWith("_")) {
            return new ArrayList<>(paths);
        }
        var result = new ArrayList<@Nullable String>(paths);
        for (var path : paths) {
            var separator = path.lastIndexOf('/');
            result.add(separator < 0
                    ? "_" + path
                    : path.substring(0, separator + 1)
                    + "_" + path.substring(separator + 1));
        }
        return result;
    }

    /// Returns a plain file path from an absolute file URL.
    ///
    /// @param url the file URL
    /// @return the normalized filesystem path
    /// @throws IllegalArgumentException if the URL is not a plain file URL
    private static Path filePath(URI url) {
        if (!url.isAbsolute()
                || !"file".equalsIgnoreCase(url.getScheme())
                || url.getRawQuery() != null
                || url.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Package importer file URL must be absolute and have no "
                            + "query or fragment: " + url
            );
        }
        return Path.of(url).normalize();
    }

    /// Converts a package slash path into a native relative path.
    ///
    /// Percent escapes are decoded as UTF-8. Empty segments are preserved only
    /// when accepted by the platform path implementation.
    ///
    /// @param value the package or export path
    /// @return the native relative path
    private static Path nativePath(String value) {
        var decoded = percentDecode(value);
        var parts = decoded.split("/", -1);
        var path = Path.of(parts[0]);
        for (var index = 1; index < parts.length; index++) {
            path = path.resolve(parts[index]);
        }
        return path;
    }

    /// Decodes percent escapes without interpreting plus as a space.
    ///
    /// @param value the URL path text
    /// @return the decoded UTF-8 text
    private static String percentDecode(String value) {
        var output = new ByteArrayOutputStream(value.length());
        for (var index = 0; index < value.length(); index++) {
            var current = value.charAt(index);
            if (current != '%') {
                var codePoint = value.codePointAt(index);
                output.writeBytes(
                        new String(Character.toChars(codePoint))
                                .getBytes(StandardCharsets.UTF_8)
                );
                index += Character.charCount(codePoint) - 1;
                continue;
            }
            if (index + 2 >= value.length()) {
                throw new IllegalArgumentException(
                        "Invalid percent escape in package path: " + value
                );
            }
            var high = Character.digit(value.charAt(++index), 16);
            var low = Character.digit(value.charAt(++index), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException(
                        "Invalid percent escape in package path: " + value
                );
            }
            output.write(high << 4 | low);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    /// Returns a canonical absolute file URL when possible.
    ///
    /// Existing files resolve symbolic links and platform aliases. Missing
    /// manifest-selected targets remain normalized so their eventual load
    /// reports the filesystem failure.
    ///
    /// @param path the selected stylesheet path
    /// @return the canonical or normalized absolute file URL
    /// @throws IOException if an existing file cannot be canonicalized
    private static URI canonicalFileUrl(Path path) throws IOException {
        var absolute = path.toAbsolutePath().normalize();
        return (Files.exists(absolute) ? absolute.toRealPath() : absolute).toUri();
    }

    /// Returns the case-sensitive final extension of a slash path.
    ///
    /// @param value the slash or native path text
    /// @return the extension including its dot, or an empty string
    private static String extension(String value) {
        var slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        var dot = value.lastIndexOf('.');
        return dot > slash ? value.substring(dot) : "";
    }

    /// Tests whether a path has a supported case-sensitive Sass extension.
    ///
    /// @param path the filesystem path
    /// @return whether the extension is supported
    private static boolean validExtension(Path path) {
        return validExtension(path.getFileName().toString());
    }

    /// Tests whether text has a supported case-sensitive Sass extension.
    ///
    /// @param value a package target or manifest field
    /// @return whether the extension is supported
    private static boolean validExtension(String value) {
        return VALID_EXTENSIONS.contains(extension(value));
    }

    /// Replaces the first wildcard in an export target.
    ///
    /// @param target the target containing a wildcard
    /// @param replacement the matched subpath text
    /// @return the replaced target
    private static String replaceFirstStar(
            String target,
            String replacement
    ) {
        var star = target.indexOf('*');
        return star < 0
                ? target
                : target.substring(0, star)
                + replacement
                + target.substring(star + 1);
    }

    /// Returns slash-separated text for a package export key.
    ///
    /// @param value the native or slash path text
    /// @return slash-separated text
    private static String slashPath(String value) {
        return value.replace('\\', '/');
    }

    /// Counts asterisks in a string.
    ///
    /// @param value the string to inspect
    /// @return the number of occurrences
    private static int countAsterisks(String value) {
        var count = 0;
        for (var index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '*') {
                count++;
            }
        }
        return count;
    }

    /// Contains a split package request.
    ///
    /// @param packageName the bare package name
    /// @param subpath the package-relative subpath, or {@code null}
    @NotNullByDefault
    private record PackageSpecifier(
            String packageName,
            @Nullable String subpath
    ) {
        /// Creates a validated split package request.
        private PackageSpecifier {
            Objects.requireNonNull(packageName, "packageName");
        }
    }
}
