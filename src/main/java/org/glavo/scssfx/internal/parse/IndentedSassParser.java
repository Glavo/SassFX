// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.source.SourceFile;
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
        // Lex first so indent diagnostics use the native line model.
        IndentedSassLexer.lex(source);
        // Structural projection into the shared SCSS statement grammar. This is
        // an implementation detail of the indented parser, not a public
        // Sass→SCSS preprocessing API.
        SourceFile projected = IndentedSassStructure.project(source);
        return new ScssParser(projected).parse();
    }
}
