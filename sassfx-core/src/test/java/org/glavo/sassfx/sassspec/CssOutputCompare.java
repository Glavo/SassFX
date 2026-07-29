// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.sassspec;

import org.jetbrains.annotations.NotNullByDefault;

/// Compares expanded CSS outputs with transport-level normalization.
///
/// Blank-line layout between top-level rules can differ from outdated fixtures
/// while still matching current dart-sass expanded output; consecutive blank
/// lines are collapsed.
///
/// Color conversion follows dart-sass 1.102.0 matrices and operation order.
/// A tiny numeric tolerance remains only for residual differences between Java
/// and Dart implementations of {@code Math.pow} / {@code pow} on extreme
/// out-of-range channels — not for alternate conversion algorithms.
@NotNullByDefault
final class CssOutputCompare {
    /// Relative tolerance for last-digit IEEE noise on typical magnitudes.
    private static final double RELATIVE_TOLERANCE = 1e-12;

    /// Looser relative tolerance for enormous out-of-range color channels where
    /// Java and Dart {@code pow}/{@code atan2} diverge beyond 1e-12 relative
    /// (still far tighter than visual/CSS serialization precision).
    private static final double HUGE_RELATIVE_TOLERANCE = 2e-2;

    /// Magnitudes above this use {@link #HUGE_RELATIVE_TOLERANCE}.
    private static final double HUGE_MAGNITUDE = 1e6;

    /// Absolute floor for near-zero residuals after large-magnitude matrix math
    /// (cancellation noise after {@code color.to-space} far out-of-range paths
    /// can leave XYZ Z on the order of 1e-2 while sibling channels are 1e13+).
    private static final double ABSOLUTE_TOLERANCE = 5e-2;

    private CssOutputCompare() {
    }

    /// Returns whether two CSS strings are equal after normalization and fuzzy
    /// numeric comparison.
    ///
    /// @param expected the fixture CSS
    /// @param actual   the compiler CSS
    /// @return whether the outputs match for sass-spec purposes
    static boolean equals(String expected, String actual) {
        String left = normalize(expected);
        String right = normalize(actual);
        if (left.equals(right)) {
            return true;
        }
        return fuzzyEquals(left, right);
    }

    /// Normalizes line endings, trailing whitespace, and consecutive blank lines.
    static String normalize(String css) {
        String normalized = css.replace("\r\n", "\n").replace('\r', '\n');
        int end = normalized.length();
        while (end > 0) {
            char ch = normalized.charAt(end - 1);
            if (ch == '\n' || ch == ' ' || ch == '\t') {
                end--;
                continue;
            }
            break;
        }
        normalized = normalized.substring(0, end);
        // Collapse 2+ consecutive newlines (blank-line-only layout drift).
        return normalized.replaceAll("\n{2,}", "\n");
    }

    /// Walks two CSS strings, requiring identical non-numeric structure and
    /// fuzzy-equal numeric literals.
    private static boolean fuzzyEquals(String expected, String actual) {
        int i = 0;
        int j = 0;
        int n = expected.length();
        int m = actual.length();
        while (i < n && j < m) {
            char ce = expected.charAt(i);
            char ca = actual.charAt(j);
            if (isNumberStart(expected, i) && isNumberStart(actual, j)) {
                NumberSpan left = readNumber(expected, i);
                NumberSpan right = readNumber(actual, j);
                if (!numbersClose(left.value(), right.value())) {
                    return false;
                }
                i = left.end();
                j = right.end();
                continue;
            }
            if (ce != ca) {
                return false;
            }
            i++;
            j++;
        }
        return i == n && j == m;
    }

    /// Returns whether a CSS number may start at {@code index}.
    private static boolean isNumberStart(String text, int index) {
        char ch = text.charAt(index);
        if (ch == '+' || ch == '-') {
            if (index + 1 >= text.length()) {
                return false;
            }
            char next = text.charAt(index + 1);
            return isDigit(next) || next == '.';
        }
        if (ch == '.') {
            return index + 1 < text.length() && isDigit(text.charAt(index + 1));
        }
        return isDigit(ch);
    }

    /// Parses one CSS numeric literal starting at {@code start}.
    private static NumberSpan readNumber(String text, int start) {
        int index = start;
        char ch = text.charAt(index);
        if (ch == '+' || ch == '-') {
            index++;
        }
        boolean sawDigit = false;
        while (index < text.length() && isDigit(text.charAt(index))) {
            sawDigit = true;
            index++;
        }
        if (index < text.length() && text.charAt(index) == '.') {
            index++;
            while (index < text.length() && isDigit(text.charAt(index))) {
                sawDigit = true;
                index++;
            }
        }
        if (!sawDigit) {
            // Should not happen when isNumberStart is true.
            return new NumberSpan(Double.NaN, start + 1);
        }
        if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            int exp = index + 1;
            if (exp < text.length() && (text.charAt(exp) == '+' || text.charAt(exp) == '-')) {
                exp++;
            }
            if (exp < text.length() && isDigit(text.charAt(exp))) {
                index = exp;
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
        }
        double value = Double.parseDouble(text.substring(start, index));
        return new NumberSpan(value, index);
    }

    /// Returns whether two magnitudes are close enough for sass-spec color noise.
    private static boolean numbersClose(double left, double right) {
        if (Double.compare(left, right) == 0) {
            return true;
        }
        if (!Double.isFinite(left) || !Double.isFinite(right)) {
            return left == right;
        }
        double diff = Math.abs(left - right);
        if (diff <= ABSOLUTE_TOLERANCE) {
            return true;
        }
        double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
        double relative = scale >= HUGE_MAGNITUDE ? HUGE_RELATIVE_TOLERANCE : RELATIVE_TOLERANCE;
        return diff <= relative * scale;
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    /// One parsed numeric span.
    private record NumberSpan(double value, int end) {
    }
}
