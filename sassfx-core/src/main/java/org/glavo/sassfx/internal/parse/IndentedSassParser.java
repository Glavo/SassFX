// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.internal.ast.Stylesheet;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Parses indentation-based Sass into the shared stylesheet AST.
///
/// The public entry is a native indented pipeline:
/// {@link IndentedSassLexer} produces structural logical lines and indent
/// levels; this parser owns statement structure. Expression and declaration
/// bodies reuse the SCSS statement grammar through a private structural
/// projection that is not part of the public compile API.
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
        // Lex first so indent diagnostics use the native line model and so
        // call sites can inspect structure without going through SCSS text.
        var lines = IndentedSassLexer.lex(source);
        if (lines.isEmpty()) {
            return new ScssParser(source, false, true).parse();
        }
        // Structural projection into the shared SCSS statement grammar. This is
        // an implementation detail of the indented parser, not a public
        // Sass→SCSS preprocessing API. Direct AST construction for simple
        // statement forms is the intended replacement path.
        SourceFile projected = IndentedSassStructure.project(source);
        return new ScssParser(projected, false, true).parse();
    }
}
