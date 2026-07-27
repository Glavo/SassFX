// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.source;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Projects generated parser-input ranges onto one original source file.
///
/// Projection segments are ordered, non-overlapping, and cover the complete
/// generated text. Original segments preserve offsets exactly, replacements
/// project any selected part to the replaced original range, and synthetic
/// segments project to an empty range at their anchor.
@ApiStatus.Internal
@NotNullByDefault
final class SourceProjection {
    /// Contains the original source whose locations are exposed to callers.
    private final SourceFile original;

    /// Contains projection segments in generated order.
    private final @Unmodifiable List<Segment> segments;

    /// Contains the generated text length covered by the segments.
    private final int generatedLength;

    /// Creates a complete projection.
    ///
    /// @param original the original source
    /// @param segments the complete ordered segment list
    /// @param generatedLength the generated text length
    SourceProjection(
            SourceFile original,
            List<Segment> segments,
            int generatedLength
    ) {
        this.original = Objects.requireNonNull(original, "original");
        this.segments = List.copyOf(segments);
        this.generatedLength = generatedLength;

        var expectedStart = 0;
        for (var segment : this.segments) {
            if (segment.generatedStart() != expectedStart) {
                throw new IllegalArgumentException(
                        "projection segments must cover generated text without gaps"
                );
            }
            if (segment.originalEnd() > original.length()) {
                throw new IllegalArgumentException(
                        "projection segment lies beyond original source"
                );
            }
            expectedStart = segment.generatedEnd();
        }
        if (expectedStart != generatedLength) {
            throw new IllegalArgumentException(
                    "projection segments must cover the complete generated text"
            );
        }
    }

    /// Projects one half-open generated range onto the original source.
    ///
    /// @param generatedStart the inclusive generated offset
    /// @param generatedEnd the exclusive generated offset
    /// @return the corresponding original source span
    SourceSpan project(int generatedStart, int generatedEnd) {
        checkOffset(generatedStart);
        checkOffset(generatedEnd);
        if (generatedEnd < generatedStart) {
            throw new IllegalArgumentException("generatedEnd must not precede generatedStart");
        }
        if (generatedStart == generatedEnd) {
            var offset = projectEmptyBoundary(generatedStart);
            return original.span(offset, offset);
        }

        var first = segmentAt(generatedStart);
        var last = segmentAt(generatedEnd - 1);
        var originalStart = first.projectStart(generatedStart);
        var originalEnd = last.projectEnd(generatedEnd);
        if (originalEnd < originalStart) {
            originalEnd = originalStart;
        }
        return original.span(originalStart, originalEnd);
    }

    /// Projects an empty generated boundary using the following segment when possible.
    private int projectEmptyBoundary(int offset) {
        if (segments.isEmpty()) {
            return 0;
        }
        if (offset == generatedLength) {
            return segments.get(segments.size() - 1).originalEnd();
        }
        return segmentAt(offset).projectStart(offset);
    }

    /// Returns the segment containing one generated code unit.
    private Segment segmentAt(int offset) {
        var low = 0;
        var high = segments.size() - 1;
        while (low <= high) {
            var middle = (low + high) >>> 1;
            var segment = segments.get(middle);
            if (offset < segment.generatedStart()) {
                high = middle - 1;
            } else if (offset >= segment.generatedEnd()) {
                low = middle + 1;
            } else {
                return segment;
            }
        }
        throw new IllegalStateException("generated offset is not covered by projection");
    }

    /// Validates a generated boundary offset.
    private void checkOffset(int offset) {
        if (offset < 0 || offset > generatedLength) {
            throw new IndexOutOfBoundsException("generated offset out of range: " + offset);
        }
    }

    /// Describes one generated segment and its original source range.
    ///
    /// @param kind the projection behavior
    /// @param generatedStart the inclusive generated start
    /// @param generatedEnd the exclusive generated end
    /// @param originalStart the inclusive original start or synthetic anchor
    /// @param originalEnd the exclusive original end or synthetic anchor
    @NotNullByDefault
    record Segment(
            Kind kind,
            int generatedStart,
            int generatedEnd,
            int originalStart,
            int originalEnd
    ) {
        /// Validates one nonempty generated segment.
        Segment {
            Objects.requireNonNull(kind, "kind");
            if (generatedStart < 0 || generatedEnd <= generatedStart) {
                throw new IllegalArgumentException("generated segment must be nonempty");
            }
            if (originalStart < 0 || originalEnd < originalStart) {
                throw new IllegalArgumentException("original range is invalid");
            }
            if (kind == Kind.ORIGINAL
                    && generatedEnd - generatedStart != originalEnd - originalStart) {
                throw new IllegalArgumentException("original segment lengths must match");
            }
            if (kind == Kind.SYNTHETIC && originalStart != originalEnd) {
                throw new IllegalArgumentException("synthetic segments require one anchor");
            }
        }

        /// Projects the beginning of a selected range within this segment.
        private int projectStart(int generatedOffset) {
            return switch (kind) {
                case ORIGINAL -> originalStart + generatedOffset - generatedStart;
                case REPLACEMENT, SYNTHETIC -> originalStart;
            };
        }

        /// Projects the end of a selected range within this segment.
        private int projectEnd(int generatedOffset) {
            return switch (kind) {
                case ORIGINAL -> originalStart + generatedOffset - generatedStart;
                case REPLACEMENT -> originalEnd;
                case SYNTHETIC -> originalEnd;
            };
        }
    }

    /// Identifies how one generated segment relates to original source text.
    enum Kind {
        /// Preserves an equal-length original source range exactly.
        ORIGINAL,
        /// Replaces one original range with generated spelling.
        REPLACEMENT,
        /// Inserts generated spelling at an empty original anchor.
        SYNTHETIC
    }
}
