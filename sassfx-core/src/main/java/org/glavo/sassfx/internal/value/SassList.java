// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Represents an immutable Sass list.
///
/// @param contents    the list elements in source order
/// @param separator   the separator between elements
/// @param hasBrackets whether square brackets are emitted
@ApiStatus.Internal
@NotNullByDefault
public record SassList(
        @Unmodifiable List<SassValue> contents,
        ListSeparator separator,
        boolean hasBrackets
) implements SassValue {
    /// Creates a defensive immutable copy of a Sass list.
    ///
    /// @throws IllegalArgumentException if multiple elements use an undecided separator
    public SassList {
        contents = List.copyOf(contents);
        Objects.requireNonNull(separator, "separator");
        if (contents.size() > 1 && separator == ListSeparator.UNDECIDED) {
            throw new IllegalArgumentException(
                    "a list with multiple elements must have an explicit separator"
            );
        }
    }

    /// Returns the immutable list elements.
    ///
    /// @return the elements in source order
    @Override
    public @Unmodifiable List<SassValue> asList() {
        return contents;
    }

    /// Returns the number of list elements.
    ///
    /// @return the element count
    @Override
    public int lengthAsList() {
        return contents.size();
    }

    /// Returns an empty map when this list is empty.
    ///
    /// @return an empty map, or {@code null} when the list is non-empty
    @Override
    public @Nullable SassMap tryMap() {
        return contents.isEmpty() ? new SassMap(Map.of()) : null;
    }

    /// Returns whether this is an unbracketed list containing only blank values.
    ///
    /// The empty unbracketed list is blank.
    ///
    /// @return whether CSS list serialization omits this list as an element
    @Override
    public boolean isBlank() {
        return !hasBrackets && contents.stream().allMatch(SassValue::isBlank);
    }

    /// Returns a CSS representation with this list's separator and brackets.
    ///
    /// Blank elements are omitted before separators are written.
    ///
    /// @return the serialized list
    /// @throws SassValueException if an element cannot be represented in CSS
    @Override
    public String toCssString() {
        return serialize(true, true);
    }

    /// Returns a CSS representation with configurable element-string quoting.
    ///
    /// Blank elements are omitted before separators are written.
    ///
    /// @param quote whether quoted strings retain surrounding quotes
    /// @return the serialized list
    /// @throws SassValueException if this list or an element cannot be represented in CSS
    @Override
    public String toCssString(boolean quote) {
        return serialize(true, quote);
    }

    /// Compares elements, separator, and bracket state.
    ///
    /// An empty list also equals an empty Sass map.
    ///
    /// @param other the object to compare
    /// @return whether the Sass values are equal
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof SassList list
                && separator == list.separator
                && hasBrackets == list.hasBrackets
                && contents.equals(list.contents)
                || contents.isEmpty() && other instanceof SassMap map
                && map.contents().isEmpty();
    }

    /// Returns a list-content hash compatible with empty-map equality.
    ///
    /// @return the Sass list hash
    @Override
    public int hashCode() {
        return contents.hashCode();
    }

    /// Returns the inspect-mode representation.
    ///
    /// Singleton comma- and slash-separated lists retain their trailing
    /// separator and use parentheses when they have no brackets.
    ///
    /// @return the serialized list
    @Override
    public String toString() {
        return serialize(false, true);
    }

    /// Serializes this list for CSS or inspect output.
    ///
    /// @param css   whether elements use CSS rather than inspect serialization
    /// @param quote whether CSS serialization retains string quotes
    /// @return the serialized contents with square brackets when requested
    /// @throws SassValueException if CSS serialization is unavailable
    private String serialize(boolean css, boolean quote) {
        if (contents.isEmpty() && !hasBrackets) {
            if (css) {
                throw new SassValueException("() isn't a valid CSS value.");
            }
            return "()";
        }
        var result = new StringBuilder();
        if (hasBrackets) {
            result.append('[');
        }
        var singleton = !css
                && contents.size() == 1
                && (separator == ListSeparator.COMMA || separator == ListSeparator.SLASH);
        if (singleton && !hasBrackets) {
            result.append('(');
        }
        var delimiter = switch (separator) {
            case COMMA -> ", ";
            case SLASH -> " / ";
            case SPACE -> " ";
            case UNDECIDED -> "";
        };
        var first = true;
        for (var element : contents) {
            if (css && element.isBlank()) {
                continue;
            }
            if (!first) {
                result.append(delimiter);
            }
            first = false;
            if (css) {
                result.append(element.toCssString(quote));
            } else {
                var needsParens = elementNeedsParens(separator, element);
                if (needsParens) {
                    result.append('(');
                }
                result.append(element.toString());
                if (needsParens) {
                    result.append(')');
                }
            }
        }
        if (singleton) {
            result.append(Objects.requireNonNull(separator.source(), "singleton separator"));
            if (!hasBrackets) {
                result.append(')');
            }
        }
        if (hasBrackets) {
            result.append(']');
        }
        return result.toString();
    }

    /// Returns whether a nested list element needs grouping parentheses in inspect mode.
    private static boolean elementNeedsParens(ListSeparator separator, SassValue value) {
        if (!(value instanceof SassList nested) || nested.hasBrackets() || nested.contents().size() <= 1) {
            return false;
        }
        return switch (separator) {
            case COMMA -> nested.separator() == ListSeparator.COMMA;
            case SLASH -> nested.separator() == ListSeparator.COMMA
                    || nested.separator() == ListSeparator.SLASH;
            case SPACE, UNDECIDED -> nested.separator() != ListSeparator.UNDECIDED;
        };
    }
}
