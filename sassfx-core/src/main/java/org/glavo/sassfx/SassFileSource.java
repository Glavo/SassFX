// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/// Describes a stylesheet that will be read from a file.
///
/// The path is retained as supplied and is not accessed until compilation.
///
/// @param path the stylesheet path
/// @param syntax the syntax used to parse the file
@NotNullByDefault
public record SassFileSource(Path path, Syntax syntax) implements SassSource {
    /// Creates a file source whose syntax is inferred from its final extension.
    ///
    /// @param path the stylesheet path
    /// @throws IllegalArgumentException if the final file extension is not supported
    public SassFileSource(Path path) {
        this(path, inferSyntax(path));
    }

    /// Creates a file source with an explicitly selected syntax.
    public SassFileSource {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(syntax, "syntax");
    }

    /// Infers the syntax encoded by the given path.
    ///
    /// @param path the path whose file name is inspected
    /// @return the inferred syntax
    /// @throws IllegalArgumentException if the final file extension is not supported
    private static Syntax inferSyntax(Path path) {
        Objects.requireNonNull(path, "path");
        @Nullable Syntax syntax = Syntax.forPath(path);
        if (syntax == null) {
            throw new IllegalArgumentException(
                    "Cannot infer Sass syntax from path: " + path
            );
        }
        return syntax;
    }
}
