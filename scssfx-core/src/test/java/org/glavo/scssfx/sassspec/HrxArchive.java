// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Parses one UTF-8 HRX archive into an immutable virtual file tree.
///
/// The archive format uses a line beginning with {@code <===> } to introduce
/// each relative file path. File contents are retained exactly, including
/// line terminators and a terminal newline when present.
@NotNullByDefault
final class HrxArchive {
    /// Introduces a file path in an HRX archive.
    private static final String HEADER_PREFIX = "<===> ";

    /// Identifies a malformed header before its required separator is checked.
    private static final String HEADER_MARKER = "<===>";

    /// Maps normalized relative paths to their exact text contents.
    private final @Unmodifiable Map<String, String> files;

    /// Creates an archive from an ordered file map.
    ///
    /// @param files the parsed files in archive order
    private HrxArchive(Map<String, String> files) {
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }

    /// Parses an HRX archive.
    ///
    /// @param source the complete archive text
    /// @param sourceName the label used in validation failures
    /// @return the immutable virtual file tree
    /// @throws IllegalArgumentException if the archive is empty, malformed, or
    /// contains an unsafe or conflicting path
    static HrxArchive parse(String source, String sourceName) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceName, "sourceName");

        var files = new LinkedHashMap<String, String>();
        @Nullable String currentPath = null;
        var currentContent = new StringBuilder();
        boolean inComment = false;
        int offset = 0;

        while (offset < source.length()) {
            int lineEnd = nextLineEnd(source, offset);
            String line = source.substring(offset, lineEnd);
            String lineWithoutTerminator = stripLineTerminator(line);

            if (lineWithoutTerminator.startsWith(HEADER_MARKER)) {
                if (currentPath != null) {
                    addFile(files, currentPath, currentContent.toString(), sourceName);
                    currentPath = null;
                    currentContent.setLength(0);
                }

                if (lineWithoutTerminator.equals(HEADER_MARKER)
                        || lineWithoutTerminator.equals(HEADER_MARKER + " ")) {
                    // Bare <===> starts an HRX comment that is discarded.
                    inComment = true;
                } else if (lineWithoutTerminator.startsWith(HEADER_PREFIX)) {
                    String path = lineWithoutTerminator.substring(HEADER_PREFIX.length());
                    validateRelativePath(path, "HRX file path");
                    currentPath = path;
                    currentContent.setLength(0);
                    inComment = false;
                } else {
                    throw malformed(sourceName, "HRX headers must use '<===> ' followed by a path.");
                }
            } else if (!inComment) {
                if (currentPath == null) {
                    throw malformed(sourceName, "HRX archives must begin with a file header.");
                }
                currentContent.append(line);
            }

            offset = lineEnd;
        }

        if (currentPath != null) {
            addFile(files, currentPath, currentContent.toString(), sourceName);
        }
        if (files.isEmpty()) {
            throw malformed(sourceName, "HRX archives must contain at least one file.");
        }
        return new HrxArchive(files);
    }

    /// Returns the immutable mapping from archive paths to exact file contents.
    ///
    /// @return an immutable map in archive order
    @Unmodifiable Map<String, String> files() {
        return files;
    }

    /// Returns the archive paths as an immutable map view.
    ///
    /// @return the archive paths in archive order
    @UnmodifiableView Set<String> paths() {
        return files.keySet();
    }

    /// Returns one file's contents when the archive contains the requested path.
    ///
    /// @param path the normalized relative archive path
    /// @return the exact file contents, or {@code null} when absent
    @Nullable String content(String path) {
        Objects.requireNonNull(path, "path");
        return files.get(path);
    }

    /// Validates a portable relative path used by HRX fixtures and manifests.
    ///
    /// @param path the path to validate
    /// @param description the path role used in validation failures
    /// @throws IllegalArgumentException if the path is blank, absolute, or
    /// contains a traversal or platform-specific separator
    static void validateRelativePath(String path, String description) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(description, "description");

        if (path.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank.");
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(description + " must use a relative slash-separated path.");
        }
        if (path.indexOf(':') >= 0 || path.indexOf(0) >= 0) {
            throw new IllegalArgumentException(description + " contains a platform-specific path character.");
        }

        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(description + " contains an invalid path segment.");
            }
        }
    }

    /// Returns the exclusive end index of the line beginning at an offset.
    ///
    /// @param source the archive text
    /// @param offset the start of the current line
    /// @return the index after the line terminator or the source end
    private static int nextLineEnd(String source, int offset) {
        int newline = source.indexOf('\n', offset);
        return newline < 0 ? source.length() : newline + 1;
    }

    /// Removes one optional LF or CRLF terminator from an archive line.
    ///
    /// @param line the line including its possible terminator
    /// @return the line content without its terminator
    private static String stripLineTerminator(String line) {
        if (!line.endsWith("\n")) {
            return line;
        }
        int end = line.length() - 1;
        if (end > 0 && line.charAt(end - 1) == '\r') {
            end--;
        }
        return line.substring(0, end);
    }

    /// Adds a file while rejecting file-directory path conflicts.
    ///
    /// When the same path is declared more than once, the later body replaces
    /// the earlier one unless the later body is blank and a non-blank body is
    /// already present. A few upstream sass-spec archives end cases with an
    /// empty re-declaration of {@code error} that would otherwise wipe the
    /// real diagnostic text (for example {@code color/is_in_gamut} too-few-args).
    ///
    /// @param files the destination file map
    /// @param path the validated path to add
    /// @param content the exact file contents
    /// @param sourceName the archive label used in errors
    private static void addFile(
            Map<String, String> files,
            String path,
            String content,
            String sourceName
    ) {
        if (!files.containsKey(path)) {
            for (String existing : files.keySet()) {
                if (existing.startsWith(path + "/") || path.startsWith(existing + "/")) {
                    throw malformed(sourceName, "HRX paths must not be both files and directories: " + path);
                }
            }
        } else if (content.isBlank()) {
            @Nullable String existing = files.get(path);
            if (existing != null && !existing.isBlank()) {
                // Keep the earlier non-blank body; ignore accidental empty re-declarations.
                return;
            }
        }
        files.put(path, content);
    }

    /// Creates one archive-format validation exception.
    ///
    /// @param sourceName the archive label
    /// @param message the detailed validation failure
    /// @return the exception to throw
    private static IllegalArgumentException malformed(String sourceName, String message) {
        return new IllegalArgumentException(sourceName + ": " + message);
    }
}
