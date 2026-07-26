// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.Syntax;
import org.glavo.scssfx.internal.ast.Stylesheet;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Public-internal entry points for stylesheet parsing.
///
/// Compiler orchestration lives outside this package, so these helpers expose
/// the package-private parsers without making the parser classes themselves
/// part of a broader SPI.
@ApiStatus.Internal
@NotNullByDefault
public final class StylesheetParser {
    /// Prevents instantiation.
    private StylesheetParser() {
    }

    /// Parses a stylesheet according to the selected syntax.
    ///
    /// SCSS uses [ScssParser]. Indented Sass uses [IndentedSassParser] (lexer
    /// plus structural projection). Plain CSS uses the SCSS parser in
    /// plain-CSS mode.
    ///
    /// @param source the indexed source text
    /// @param syntax the syntax used to parse the source
    /// @return the unevaluated stylesheet AST
    /// @throws ParseException if parsing fails or the syntax is unavailable
    public static Stylesheet parse(SourceFile source, Syntax syntax) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(syntax, "syntax");
        return switch (syntax) {
            case SCSS -> new ScssParser(source).parse();
            case SASS -> IndentedSassParser.parse(source);
            case CSS -> new ScssParser(source, true).parse();
        };
    }
}
