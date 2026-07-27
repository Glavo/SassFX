// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.source;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.Objects;

/// Builds transformed parser input together with its original-source projection.
@ApiStatus.Internal
@NotNullByDefault
public final class MappedSourceBuilder {
    /// Contains the original source.
    private final SourceFile original;

    /// Accumulates generated parser input.
    private final StringBuilder content;

    /// Accumulates projection segments in generated order.
    private final ArrayList<SourceProjection.Segment> segments = new ArrayList<>();

    /// Creates an empty builder for one original source.
    ///
    /// @param original the original source
    public MappedSourceBuilder(SourceFile original) {
        this.original = Objects.requireNonNull(original, "original");
        this.content = new StringBuilder(original.length());
    }

    /// Appends an exact original source range.
    ///
    /// @param start the inclusive original offset
    /// @param end the exclusive original offset
    /// @return this builder
    public MappedSourceBuilder appendOriginal(int start, int end) {
        checkOriginalRange(start, end);
        if (start == end) {
            return this;
        }
        var generatedStart = content.length();
        content.append(original.content(), start, end);
        addSegment(
                SourceProjection.Kind.ORIGINAL,
                generatedStart,
                content.length(),
                start,
                end
        );
        return this;
    }

    /// Appends generated spelling that replaces one original range.
    ///
    /// @param replacement the generated spelling
    /// @param originalStart the inclusive original offset
    /// @param originalEnd the exclusive original offset
    /// @return this builder
    public MappedSourceBuilder appendReplacement(
            String replacement,
            int originalStart,
            int originalEnd
    ) {
        Objects.requireNonNull(replacement, "replacement");
        checkOriginalRange(originalStart, originalEnd);
        if (replacement.isEmpty()) {
            return this;
        }
        var generatedStart = content.length();
        content.append(replacement);
        addSegment(
                SourceProjection.Kind.REPLACEMENT,
                generatedStart,
                content.length(),
                originalStart,
                originalEnd
        );
        return this;
    }

    /// Appends generated spelling at one empty original-source anchor.
    ///
    /// @param synthetic the generated spelling
    /// @param anchor the original source offset
    /// @return this builder
    public MappedSourceBuilder appendSynthetic(String synthetic, int anchor) {
        Objects.requireNonNull(synthetic, "synthetic");
        checkOriginalRange(anchor, anchor);
        if (synthetic.isEmpty()) {
            return this;
        }
        var generatedStart = content.length();
        content.append(synthetic);
        addSegment(
                SourceProjection.Kind.SYNTHETIC,
                generatedStart,
                content.length(),
                anchor,
                anchor
        );
        return this;
    }

    /// Returns the current generated text length.
    ///
    /// @return the generated UTF-16 length
    public int length() {
        return content.length();
    }

    /// Builds the transformed source file.
    ///
    /// @return a source file whose spans project onto the original source
    public SourceFile build() {
        var projection = new SourceProjection(original, segments, content.length());
        return SourceFile.projected(content.toString(), original.url(), projection);
    }

    /// Appends one projection segment.
    private void addSegment(
            SourceProjection.Kind kind,
            int generatedStart,
            int generatedEnd,
            int originalStart,
            int originalEnd
    ) {
        segments.add(new SourceProjection.Segment(
                kind,
                generatedStart,
                generatedEnd,
                originalStart,
                originalEnd
        ));
    }

    /// Validates an original half-open range.
    private void checkOriginalRange(int start, int end) {
        if (start < 0 || end > original.length()) {
            throw new IndexOutOfBoundsException("original range is outside the source");
        }
        if (end < start) {
            throw new IllegalArgumentException("end must not precede start");
        }
    }
}
