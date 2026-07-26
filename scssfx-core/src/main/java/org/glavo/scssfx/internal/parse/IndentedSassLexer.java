// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Lexes indentation-based Sass into structural logical lines.
///
/// Each [IndentedSassStructure.LogicalLine] carries indent depth, statement
/// text, comment flag, and original source offsets. [IndentedSassParser]
/// consumes these lines to recover block structure.
@ApiStatus.Internal
@NotNullByDefault
public final class IndentedSassLexer {
    private IndentedSassLexer() {
    }

    /// Lexes one indented Sass source.
    ///
    /// @param source the original indented source
    /// @return immutable logical lines in source order
    /// @throws ParseException if physical lines cannot form valid logical lines
    public static @Unmodifiable List<IndentedSassStructure.LogicalLine> lex(SourceFile source) {
        Objects.requireNonNull(source, "source");
        return List.copyOf(IndentedSassStructure.logicalLines(source));
    }
}
