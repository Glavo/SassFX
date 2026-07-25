// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Scans UTF-16 source text while retaining restorable positions and spans.
///
/// Instances are mutable and intended to be confined to one parser invocation.
@NotNullByDefault
final class SourceScanner {
    /// The indexed source being scanned.
    private final SourceFile source;

    /// The current zero-based UTF-16 offset.
    private int position;

    /// Creates a scanner positioned at the beginning of the source.
    ///
    /// @param source the indexed source to scan
    SourceScanner(SourceFile source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /// Returns the indexed source.
    ///
    /// @return the source being scanned
    SourceFile source() {
        return source;
    }

    /// Returns the current UTF-16 offset.
    ///
    /// @return the current source offset
    int position() {
        return position;
    }

    /// Captures the current scanner position.
    ///
    /// @return restorable scanner state
    ScannerState state() {
        return new ScannerState(position);
    }

    /// Restores a previously captured position.
    ///
    /// @param state the state to restore
    /// @throws IndexOutOfBoundsException if the state lies beyond this source
    void restore(ScannerState state) {
        Objects.requireNonNull(state, "state");
        if (state.position() > source.length()) {
            throw new IndexOutOfBoundsException(
                    "scanner state lies beyond source: " + state.position()
            );
        }
        position = state.position();
    }

    /// Returns whether the scanner is at the end of the source.
    ///
    /// @return whether no UTF-16 code unit remains
    boolean isDone() {
        return position == source.length();
    }

    /// Requires the scanner to be at end of input.
    ///
    /// @throws ParseException if an unconsumed code unit remains
    void expectDone() {
        if (!isDone()) {
            throw error("Expected end of input.");
        }
    }

    /// Returns the next UTF-16 code unit without consuming it.
    ///
    /// @return the next code unit, or [CssCharacters#END_OF_INPUT]
    int peek() {
        return peek(0);
    }

    /// Returns a future UTF-16 code unit without consuming input.
    ///
    /// @param forward the nonnegative number of code units to look ahead
    /// @return the selected code unit, or [CssCharacters#END_OF_INPUT]
    /// @throws IllegalArgumentException if {@code forward} is negative
    int peek(int forward) {
        if (forward < 0) {
            throw new IllegalArgumentException("forward must not be negative");
        }
        var index = (long) position + forward;
        return index >= source.length()
                ? CssCharacters.END_OF_INPUT
                : source.content().charAt((int) index);
    }

    /// Consumes and returns the next UTF-16 code unit.
    ///
    /// @return the consumed code unit
    /// @throws ParseException if the scanner is at the end of the source
    int read() {
        if (isDone()) {
            throw error("Unexpected end of input.");
        }
        return source.content().charAt(position++);
    }

    /// Consumes the next code unit when it equals the expected value.
    ///
    /// @param expected the expected UTF-16 code unit
    /// @return whether the code unit was consumed
    boolean scan(int expected) {
        if (peek() != expected) {
            return false;
        }
        position++;
        return true;
    }

    /// Consumes the given text when it occurs at the current position.
    ///
    /// @param expected the expected text
    /// @return whether the text was consumed
    boolean scan(String expected) {
        Objects.requireNonNull(expected, "expected");
        if (!source.content().startsWith(expected, position)) {
            return false;
        }
        position += expected.length();
        return true;
    }

    /// Consumes the next code unit and requires it to equal the expected value.
    ///
    /// @param expected the expected UTF-16 code unit
    /// @throws ParseException if the next code unit differs
    void expect(int expected) {
        if (scan(expected)) {
            return;
        }
        // Lowercase "expected" matches dart-sass string_scanner _fail().
        throw error("expected \"" + printable(expected) + "\".");
    }

    /// Consumes and requires the given text.
    ///
    /// @param expected the expected text
    /// @throws ParseException if the source does not contain the text here
    void expect(String expected) {
        Objects.requireNonNull(expected, "expected");
        if (scan(expected)) {
            return;
        }
        // Lowercase "expected" matches dart-sass string_scanner _fail().
        throw error("expected \"" + expected + "\".");
    }

    /// Returns source text from the given offset to the current position.
    ///
    /// @param start the inclusive UTF-16 start offset
    /// @return the selected source text
    String substring(int start) {
        return substring(start, position);
    }

    /// Returns source text for the given half-open offset range.
    ///
    /// @param start the inclusive UTF-16 start offset
    /// @param end the exclusive UTF-16 end offset
    /// @return the selected source text
    String substring(int start, int end) {
        return source.content().substring(start, end);
    }

    /// Returns a span from captured state to the current position.
    ///
    /// @param start the inclusive start state
    /// @return the selected source span
    SourceSpan spanFrom(ScannerState start) {
        return spanFrom(start, state());
    }

    /// Returns a span between two captured states.
    ///
    /// @param start the inclusive start state
    /// @param end the exclusive end state
    /// @return the selected source span
    SourceSpan spanFrom(ScannerState start, ScannerState end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        return source.span(start.position(), end.position());
    }

    /// Creates a parse failure at the current source position.
    ///
    /// A nonempty span covers the next UTF-16 code unit. At end of input the
    /// returned failure has an empty span.
    ///
    /// @param message the failure message
    /// @return the parse failure
    ParseException error(String message) {
        var length = isDone() ? 0 : 1;
        return error(message, position, length);
    }

    /// Creates a parse failure for an explicit source range.
    ///
    /// @param message the failure message
    /// @param start the inclusive UTF-16 start offset
    /// @param length the nonnegative UTF-16 range length
    /// @return the parse failure
    ParseException error(String message, int start, int length) {
        Objects.requireNonNull(message, "message");
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        return new ParseException(
                org.glavo.scssfx.DiagnosticCode.PARSE_ERROR,
                source.span(start, Math.addExact(start, length)),
                message
        );
    }

    /// Creates a structured parse failure for an explicit source range.
    ///
    /// @param code   the stable diagnostic code
    /// @param start  the inclusive UTF-16 start offset
    /// @param length the nonnegative UTF-16 range length
    /// @param args   format arguments for [org.glavo.scssfx.DiagnosticMessages]
    /// @return the parse failure
    ParseException error(
            org.glavo.scssfx.DiagnosticCode code,
            int start,
            int length,
            Object... args
    ) {
        Objects.requireNonNull(code, "code");
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        return new ParseException(code, source.span(start, Math.addExact(start, length)), args);
    }

    /// Creates a parse failure for an already projected source span.
    ///
    /// @param message the failure message
    /// @param span the source span associated with the failure
    /// @return the parse failure
    ParseException error(String message, SourceSpan span) {
        return new ParseException(
                org.glavo.scssfx.DiagnosticCode.PARSE_ERROR,
                Objects.requireNonNull(span, "span"),
                Objects.requireNonNull(message, "message")
        );
    }

    /// Creates a structured parse failure for an already projected source span.
    ///
    /// @param code the stable diagnostic code
    /// @param span the source span associated with the failure
    /// @param args format arguments for [org.glavo.scssfx.DiagnosticMessages]
    /// @return the parse failure
    ParseException error(
            org.glavo.scssfx.DiagnosticCode code,
            SourceSpan span,
            Object... args
    ) {
        return new ParseException(
                Objects.requireNonNull(code, "code"),
                Objects.requireNonNull(span, "span"),
                args
        );
    }

    /// Returns readable text for an expected UTF-16 code unit.
    ///
    /// @param character the code unit to format
    /// @return its text representation
    private static String printable(int character) {
        return switch (character) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> Character.toString((char) character);
        };
    }
}
