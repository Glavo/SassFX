// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.internal.ast.Stylesheet;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Parses indentation-based Sass into the shared stylesheet AST.
///
/// The parser derives logical lines and indentation levels before projecting
/// statement structure into the shared SCSS grammar. The projection is an
/// internal representation and is not a public Sass-to-SCSS preprocessing API.
///
/// Callers must not depend on intermediate braced text. The only supported
/// entry is {@link #parse(SourceFile)}.
@ApiStatus.Internal
@NotNullByDefault
public final class IndentedSassParser {
    private IndentedSassParser() {
    }

    /// Parses an indented Sass stylesheet.
    ///
    /// @param source the original indented source
    /// @return the unevaluated stylesheet AST
    /// @throws ParseException if the indented structure or statement forms fail
    public static Stylesheet parse(SourceFile source) {
        Objects.requireNonNull(source, "source");
        // Structural projection into the shared SCSS statement grammar. This is
        // an implementation detail of the indented parser, not a public
        // Sass→SCSS preprocessing API. Direct AST construction for simple
        // statement forms is the intended replacement path.
        SourceFile projected = IndentedSassStructure.project(source);
        return new ScssParser(projected, false, true).parse();
    }
}
