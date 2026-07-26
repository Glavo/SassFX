// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies indented preprocessor emission for selector continuations and loud
/// comment content-after-close rules used by batch38.
@NotNullByDefault
final class PreprocessDumpTest {
    @Test
    void joinsSelectorContinuationAcrossComments() {
        assertTrue(transform("a, // comment\nb\n  x: y\n").contains("b {"));
        assertTrue(transform("a, /* comment */\nb\n  x: y\n").contains("b {"));
        assertTrue(transform("a /* comment */,\nb\n  x: y\n").contains("b {"));
    }

    @Test
    void rejectsTextAfterLoudCommentClose() {
        var failure = assertThrows(
                ParseException.class,
                () -> transform("/* */ a\n")
        );
        assertTrue(failure.getMessage().contains("Unexpected text after end of comment"));
    }

    private static String transform(String source) {
        return IndentedSassStructure.project(new SourceFile(source, null)).content();
    }
}
