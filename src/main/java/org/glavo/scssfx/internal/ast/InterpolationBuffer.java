// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.Objects;

/// Incrementally builds an interpolation while combining adjacent text.
@ApiStatus.Internal
@NotNullByDefault
public final class InterpolationBuffer {
    /// The completed parts preceding the pending text.
    private final ArrayList<InterpolationPart> parts = new ArrayList<>();

    /// The text accumulated since the last expression part.
    private final StringBuilder text = new StringBuilder();

    /// Creates an empty interpolation buffer.
    public InterpolationBuffer() {
    }

    /// Returns whether no text or expression has been added.
    ///
    /// @return whether the buffer is empty
    public boolean isEmpty() {
        return parts.isEmpty() && text.length() == 0;
    }

    /// Returns the text accumulated after the last expression.
    ///
    /// @return a snapshot of the trailing text
    public String trailingText() {
        return text.toString();
    }

    /// Removes all accumulated text and expression parts.
    public void clear() {
        parts.clear();
        text.setLength(0);
    }

    /// Appends plain text.
    ///
    /// @param value the text to append
    public void append(String value) {
        text.append(Objects.requireNonNull(value, "value"));
    }

    /// Appends one UTF-16 code unit.
    ///
    /// @param value the code unit to append
    public void append(char value) {
        text.append(value);
    }

    /// Appends one Unicode code point.
    ///
    /// @param value the code point to append
    /// @throws IllegalArgumentException if {@code value} is not a valid Unicode code point
    public void appendCodePoint(int value) {
        text.appendCodePoint(value);
    }

    /// Adds an expression after flushing any pending text.
    ///
    /// @param expression the expression to add
    /// @param interpolationSpan the source range covering its interpolation delimiters
    public void add(SassExpression expression, SourceSpan interpolationSpan) {
        flushText();
        parts.add(new ExpressionInterpolationPart(expression, interpolationSpan));
    }

    /// Adds all parts of another interpolation while combining adjacent text.
    ///
    /// @param interpolation the interpolation to append
    public void add(Interpolation interpolation) {
        Objects.requireNonNull(interpolation, "interpolation");
        for (var part : interpolation.parts()) {
            if (part instanceof TextInterpolationPart plain) {
                text.append(plain.text());
            } else if (part instanceof ExpressionInterpolationPart expression) {
                add(expression.expression(), expression.interpolationSpan());
            }
        }
    }

    /// Creates an immutable interpolation snapshot without clearing this buffer.
    ///
    /// @param span the source range covering the complete interpolation
    /// @return the interpolation snapshot
    public Interpolation interpolation(SourceSpan span) {
        var snapshot = new ArrayList<>(parts);
        if (text.length() != 0) {
            snapshot.add(new TextInterpolationPart(text.toString()));
        }
        return new Interpolation(snapshot, span);
    }

    /// Returns the Sass source accumulated by this buffer.
    ///
    /// @return the current interpolation source representation
    @Override
    public String toString() {
        var result = new StringBuilder();
        for (var part : parts) {
            result.append(part);
        }
        return result.append(text).toString();
    }

    /// Moves pending text into the completed part list when nonempty.
    private void flushText() {
        if (text.length() == 0) {
            return;
        }
        parts.add(new TextInterpolationPart(text.toString()));
        text.setLength(0);
    }
}
