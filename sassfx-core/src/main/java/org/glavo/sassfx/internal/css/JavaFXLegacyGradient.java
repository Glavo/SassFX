// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.internal.value.ListSeparator;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Restores grouping parentheses consumed by Sass list evaluation for
/// deprecated JavaFX gradient and ladder syntax.
///
/// Ordinary CSS serialization intentionally follows Sass list semantics. This
/// adapter is used only by JavaFX targets, whose legacy paint grammar gives
/// semantic meaning to parenthesized comma lists.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXLegacyGradient {
    /// Prevents instantiation.
    private JavaFXLegacyGradient() {
    }

    /// Contains one recognized paint and the first unconsumed list index.
    ///
    /// @param css reconstructed legacy JavaFX CSS
    /// @param nextIndex first list element after the gradient
    public record Match(String css, int nextIndex) {
        /// Creates a validated match.
        public Match {
            Objects.requireNonNull(css, "css");
            if (css.isEmpty()) {
                throw new IllegalArgumentException("css must not be empty");
            }
            if (nextIndex <= 0) {
                throw new IllegalArgumentException(
                        "nextIndex must be positive"
                );
            }
        }
    }

    /// Serializes a JavaFX declaration value when it contains legacy gradient
    /// syntax.
    ///
    /// @param value the evaluated Sass value
    /// @return reconstructed CSS, or {@code null} when no legacy gradient is
    ///         present
    /// @throws SassValueException if an ordinary neighboring value cannot be
    ///                            represented in CSS
    public static @Nullable String serialize(SassValue value) {
        Objects.requireNonNull(value, "value");
        if (!(value instanceof SassList list) || list.hasBrackets()) {
            @Nullable var match = consume(List.of(value), 0);
            return match != null && match.nextIndex() == 1
                    ? match.css()
                    : null;
        }
        if (list.separator() != ListSeparator.SPACE
                && list.separator() != ListSeparator.COMMA) {
            return null;
        }

        var result = new StringBuilder();
        var changed = false;
        var delimiter = list.separator() == ListSeparator.COMMA
                ? ", "
                : " ";
        if (list.separator() == ListSeparator.COMMA) {
            for (var item : list.contents()) {
                if (result.length() != 0) {
                    result.append(delimiter);
                }
                @Nullable var rewritten = serialize(item);
                if (rewritten == null) {
                    result.append(item.toCssString());
                } else {
                    result.append(rewritten);
                    changed = true;
                }
            }
            return changed ? result.toString() : null;
        }
        for (var index = 0; index < list.contents().size();) {
            if (result.length() != 0) {
                result.append(delimiter);
            }
            @Nullable var match = consume(list.contents(), index);
            if (match != null) {
                result.append(match.css());
                index = match.nextIndex();
                changed = true;
            } else {
                result.append(list.contents().get(index).toCssString());
                index++;
            }
        }
        return changed ? result.toString() : null;
    }

    /// Recognizes one legacy paint beginning at an evaluated list index.
    ///
    /// @param values evaluated values from one space-separated paint sequence
    /// @param startIndex index of the potential gradient keyword
    /// @return the reconstructed gradient and first unconsumed index, or
    ///         {@code null}
    public static @Nullable Match consume(
            @Unmodifiable List<SassValue> values,
            int startIndex
    ) {
        Objects.requireNonNull(values, "values");
        if (startIndex < 0 || startIndex >= values.size()) {
            return null;
        }
        var value = values.get(startIndex);
        if (value instanceof SassString string && !string.hasQuotes()) {
            var text = string.text().trim();
            if (beginsLegacyText(text, "linear")
                    || beginsLegacyText(text, "radial")
                    || beginsLegacyText(text, "ladder")) {
                return new Match(text, startIndex + 1);
            }
        }
        @Nullable var keyword = keyword(value);
        if ("linear".equals(keyword)) {
            return consumeLinear(values, startIndex);
        }
        if ("radial".equals(keyword)) {
            return consumeRadial(values, startIndex);
        }
        if ("ladder".equals(keyword)) {
            return consumeLadder(values, startIndex);
        }
        return null;
    }

    /// Reconstructs one structured legacy ladder color.
    ///
    /// @param values the containing value sequence
    /// @param startIndex the `ladder` keyword index
    /// @return the reconstructed match, or {@code null}
    private static @Nullable Match consumeLadder(
            List<SassValue> values,
            int startIndex
    ) {
        var index = startIndex + 1;
        if (index >= values.size()) {
            return null;
        }
        var result = new ArrayList<String>();
        result.add("ladder");
        result.add(values.get(index++).toCssString());
        if (!hasKeyword(values, index, "stops")) {
            return null;
        }
        result.add("stops");
        index++;
        var stopStart = index;
        while (isPair(values, index)) {
            result.add(parenthesized(values.get(index++)));
        }
        return index == stopStart
                ? null
                : new Match(String.join(" ", result), index);
    }

    /// Reconstructs one structured legacy linear gradient.
    ///
    /// @param values the containing value sequence
    /// @param startIndex the `linear` keyword index
    /// @return the reconstructed match, or {@code null}
    private static @Nullable Match consumeLinear(
            List<SassValue> values,
            int startIndex
    ) {
        var index = startIndex + 1;
        if (!isPair(values, index)) {
            return null;
        }
        var result = new ArrayList<String>();
        result.add("linear");
        result.add(parenthesized(values.get(index++)));
        if (!hasKeyword(values, index, "to")) {
            return null;
        }
        result.add("to");
        index++;
        if (!isPair(values, index)) {
            return null;
        }
        result.add(parenthesized(values.get(index++)));
        if (!hasKeyword(values, index, "stops")) {
            return null;
        }
        result.add("stops");
        index++;
        var stopStart = index;
        while (isPair(values, index)) {
            result.add(parenthesized(values.get(index++)));
        }
        if (index == stopStart) {
            return null;
        }
        if (isCycle(values, index)) {
            result.add(Objects.requireNonNull(keyword(values.get(index++))));
        }
        return new Match(String.join(" ", result), index);
    }

    /// Reconstructs one structured legacy radial gradient.
    ///
    /// @param values the containing value sequence
    /// @param startIndex the `radial` keyword index
    /// @return the reconstructed match, or {@code null}
    private static @Nullable Match consumeRadial(
            List<SassValue> values,
            int startIndex
    ) {
        var index = startIndex + 1;
        var result = new ArrayList<String>();
        result.add("radial");
        for (var optional : List.of(
                "focus-angle",
                "focus-distance"
        )) {
            if (hasKeyword(values, index, optional)) {
                if (index + 1 >= values.size()) {
                    return null;
                }
                result.add(optional);
                result.add(values.get(index + 1).toCssString());
                index += 2;
            }
        }
        if (hasKeyword(values, index, "center")) {
            if (!isPair(values, index + 1)) {
                return null;
            }
            result.add("center");
            result.add(parenthesized(values.get(index + 1)));
            index += 2;
        }
        if (index >= values.size()) {
            return null;
        }
        result.add(values.get(index++).toCssString());
        if (!hasKeyword(values, index, "stops")) {
            return null;
        }
        result.add("stops");
        index++;
        var stopStart = index;
        while (isPair(values, index)) {
            result.add(parenthesized(values.get(index++)));
        }
        if (index == stopStart) {
            return null;
        }
        if (isCycle(values, index)) {
            result.add(Objects.requireNonNull(keyword(values.get(index++))));
        }
        return new Match(String.join(" ", result), index);
    }

    /// Returns a comma-list as one parenthesized legacy token.
    ///
    /// @param value the evaluated point or stop
    /// @return the reconstructed parenthesized text
    private static String parenthesized(SassValue value) {
        return "(" + value.toCssString() + ")";
    }

    /// Returns whether one index contains a two-component comma list.
    ///
    /// @param values the containing sequence
    /// @param index the candidate index
    /// @return whether the value is a legacy point or stop shape
    private static boolean isPair(List<SassValue> values, int index) {
        return index >= 0
                && index < values.size()
                && values.get(index) instanceof SassList list
                && !list.hasBrackets()
                && list.separator() == ListSeparator.COMMA
                && list.contents().size() >= 2;
    }

    /// Returns whether one index contains a selected keyword.
    ///
    /// @param values the containing sequence
    /// @param index the candidate index
    /// @param expected the lower-case expected keyword
    /// @return whether the keyword matches
    private static boolean hasKeyword(
            List<SassValue> values,
            int index,
            String expected
    ) {
        return index >= 0
                && index < values.size()
                && expected.equals(keyword(values.get(index)));
    }

    /// Returns whether one index contains a legacy cycle method.
    ///
    /// @param values the containing sequence
    /// @param index the candidate index
    /// @return whether a cycle method is present
    private static boolean isCycle(List<SassValue> values, int index) {
        if (index < 0 || index >= values.size()) {
            return false;
        }
        @Nullable var keyword = keyword(values.get(index));
        return "repeat".equals(keyword)
                || "reflect".equals(keyword)
                || "no-cycle".equals(keyword);
    }

    /// Returns the normalized spelling of an unquoted string value.
    ///
    /// @param value the candidate value
    /// @return the lower-case keyword, or {@code null}
    private static @Nullable String keyword(SassValue value) {
        return value instanceof SassString string && !string.hasQuotes()
                ? string.text().trim().toLowerCase(Locale.ROOT)
                : null;
    }

    /// Returns whether raw text begins with a standalone legacy keyword.
    ///
    /// @param text the raw unquoted text
    /// @param keyword the lower-case paint keyword
    /// @return whether the text is a complete legacy token series
    private static boolean beginsLegacyText(String text, String keyword) {
        return text.length() > keyword.length()
                && text.regionMatches(true, 0, keyword, 0, keyword.length())
                && Character.isWhitespace(text.charAt(keyword.length()));
    }
}
