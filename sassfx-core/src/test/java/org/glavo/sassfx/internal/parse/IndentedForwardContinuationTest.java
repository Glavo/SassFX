// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.parse;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies indented-syntax continuation boundaries for {@code @forward} and
/// control directives that must not be confused with {@code @for}.
@NotNullByDefault
final class IndentedForwardContinuationTest {
    /// Rejects indentation after a complete {@code @forward} URL line.
    @Test
    void rejectsIndentationAfterCompleteForwardUrl(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_other.scss"), "$a: 1 !default;\n");
        Files.writeString(directory.resolve("input.sass"), "@forward \"other\"\n  as a-*\n");
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("input.sass")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("Nothing may be indented beneath a @forward rule.", failure.getMessage());
    }

    /// Rejects a comma-continued {@code show} list that is not trailing-comma open.
    @Test
    void rejectsIndentationAfterShowMemberWithoutTrailingComma(@TempDir Path directory)
            throws Exception {
        Files.writeString(directory.resolve("_other.scss"), "$a: 1 !default;\n$b: 2 !default;\n");
        Files.writeString(directory.resolve("input.sass"), "@forward \"other\" show $a\n  , $b\n");
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(directory.resolve("input.sass")),
                        CssTarget.DEFAULT
                )
        );
        assertEquals("Nothing may be indented beneath a @forward rule.", failure.getMessage());
    }

    /// Continues after {@code as} because that keyword leaves the statement open.
    @Test
    void continuesAfterAsKeyword(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("_other.scss"), "$a: 1 !default;\n");
        Files.writeString(directory.resolve("input.sass"), "@forward \"other\" as\n  a-*\n");
        var result = new SassCompiler().compile(
                SassSource.fromFile(directory.resolve("input.sass")),
                CssTarget.DEFAULT
        );
        assertEquals("", result.output().strip());
    }

    /// Continues {@code @for} after a trailing silent comment on the previous line.
    @Test
    void continuesForAfterTrailingSilentComment() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @for //
                                  $i from 1 through 2
                                  .a-#{$i}
                                    b: c
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );
        assertEquals(
                """
                        .a-1 {
                          b: c;
                        }

                        .a-2 {
                          b: c;
                        }
                        """.strip(),
                result.output().strip()
        );
        // Bound after {@code through //} and empty body after a trailing comment.
        assertEquals(
                "",
                new SassCompiler().compile(
                        SassSource.fromString(
                                """
                                        @for $i from 1 through //
                                          2
                                        """,
                                Syntax.SASS
                        ),
                        CssTarget.DEFAULT
                ).output().strip()
        );
        assertEquals(
                "",
                new SassCompiler().compile(
                        SassSource.fromString(
                                """
                                        @for $i from 1 through 2 //
                                        """,
                                Syntax.SASS
                        ),
                        CssTarget.DEFAULT
                ).output().strip()
        );
    }

    /// Keeps {@code @each} continuations working after the {@code @for}/{@code @forward}
    /// word-boundary fix.
    @Test
    void continuesEachListsAcrossIndentedLines() throws Exception {
        var result = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @each $v in a, b
                                  .x
                                    color: $v
                                """,
                        Syntax.SASS
                ),
                CssTarget.DEFAULT
        );
        assertTrue(result.output().contains("color: a"), result.output());
        assertTrue(result.output().contains("color: b"), result.output());
    }
}
