// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.node;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Resolves Sass-compatible targets from a Node package `exports` value.
@NotNullByDefault
final class NodePackageExports {
    /// Conditions recognized in package export target objects.
    private static final @Unmodifiable List<String> EXPORT_CONDITIONS =
            List.of("sass", "style", "default");

    /// Prevents instantiation.
    private NodePackageExports() {
    }

    /// Resolves the package exports field for a root or subpath request.
    ///
    /// @param packageRoot the installed package root
    /// @param subpath the requested subpath, or `null`
    /// @param manifest the parsed package manifest
    /// @param packageName the package name used in diagnostics
    /// @param fromImport whether import-only files take precedence
    /// @return the exported file, or `null`
    static @Nullable Path resolve(
            Path packageRoot,
            @Nullable String subpath,
            NodePackageManifest manifest,
            String packageName,
            boolean fromImport
    ) {
        Objects.requireNonNull(packageRoot, "packageRoot");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(packageName, "packageName");
        @Nullable Object exports = manifest.value("exports");
        if (exports == null) {
            return null;
        }

        @Nullable Path direct = resolveVariants(
                packageRoot,
                variants(subpath, false),
                exports,
                subpath,
                packageName
        );
        if (direct != null) {
            return NodePackagePath.resolveImportOnly(direct, fromImport);
        }
        if (subpath != null
                && !NodePackagePath.extension(subpath).isEmpty()) {
            return null;
        }

        @Nullable Path index = resolveVariants(
                packageRoot,
                variants(subpath, true),
                exports,
                subpath,
                packageName
        );
        return index == null
                ? null
                : NodePackagePath.resolveImportOnly(index, fromImport);
    }

    /// Resolves all variants of one subpath against package exports.
    ///
    /// @param packageRoot the installed package root
    /// @param variants extension and partial variants to test
    /// @param exports the exports JSON value
    /// @param subpath the original requested subpath, or `null`
    /// @param packageName the package name used in diagnostics
    /// @return the unique matching path, or `null`
    private static @Nullable Path resolveVariants(
            Path packageRoot,
            List<@Nullable String> variants,
            Object exports,
            @Nullable String subpath,
            String packageName
    ) {
        if (exports instanceof Map<?, ?> rawMap) {
            var map = NodePackageManifest.object(rawMap);
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
                    @Nullable Path resolved = resolveTarget(
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
            var map = NodePackageManifest.object(rawMap);
            if (map.keySet().stream()
                    .noneMatch(key -> key.startsWith("."))) {
                continue;
            }

            var matchKey = "./" + slashPath(variant);
            if (map.containsKey(matchKey)
                    && map.get(matchKey) != null
                    && matchKey.indexOf('*') < 0) {
                @Nullable Path resolved = resolveTarget(
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
                    .sorted(NodePackageExports::compareExpansionKeys)
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
                @Nullable Path resolved = resolveTarget(
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

    /// Resolves one package export target.
    ///
    /// @param target the export target JSON value
    /// @param packageRoot the installed package root
    /// @param patternMatch wildcard replacement text, or `null`
    /// @return the first resolvable target path, or `null`
    private static @Nullable Path resolveTarget(
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
            var path = NodePackagePath.resolve(
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
            var map = NodePackageManifest.object(rawMap);
            for (var entry : map.entrySet()) {
                if (!EXPORT_CONDITIONS.contains(entry.getKey())
                        || entry.getValue() == null) {
                    continue;
                }
                @Nullable Path resolved = resolveTarget(
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
                @Nullable Path resolved = resolveTarget(
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

    /// Returns the main export target represented by an exports value.
    ///
    /// @param exports the exports JSON value
    /// @return the main target, or `null` when none is declared
    private static @Nullable Object mainExport(Object exports) {
        if (exports instanceof String) {
            return exports;
        }
        if (exports instanceof List<?> list
                && list.stream().allMatch(String.class::isInstance)) {
            return list;
        }
        if (exports instanceof Map<?, ?> rawMap) {
            var map = NodePackageManifest.object(rawMap);
            if (map.keySet().stream()
                    .noneMatch(key -> key.startsWith("."))) {
                return map;
            }
            return map.get(".");
        }
        return null;
    }

    /// Generates extension, partial, and optional index export variants.
    ///
    /// @param subpath the requested package subpath, or `null`
    /// @param addIndex whether index variants are generated
    /// @return ordered variants, using `null` for the root main export
    private static List<@Nullable String> variants(
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
        if (NodePackagePath.validExtension(subpath)) {
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

    /// Compares export pattern keys using Node's pattern-key precedence.
    ///
    /// @param first the first pattern key
    /// @param second the second pattern key
    /// @return a negative value when `first` has higher precedence
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
}
