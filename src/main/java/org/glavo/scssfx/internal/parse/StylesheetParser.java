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
    /// SCSS and indentation-based Sass are implemented. Plain CSS remains
    /// unavailable with a structured parse error spanning the complete source.
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
            case SASS -> new ScssParser(IndentedSassPreprocessor.transform(source)).parse();
            case CSS -> throw new ParseException(
                    "Plain CSS stylesheet syntax isn't supported.",
                    source.span(0, source.length())
            );
        };
    }
}
