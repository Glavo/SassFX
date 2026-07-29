// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A list that also carries leftover keyword arguments from a rest parameter.
@ApiStatus.Internal
@NotNullByDefault
public final class SassArgumentList implements SassValue {
    /// Contains the positional rest elements.
    private final @Unmodifiable List<SassValue> contents;

    /// Contains the list separator.
    private final ListSeparator separator;

    /// Contains leftover keyword arguments without dollar signs.
    private final @Unmodifiable Map<String, SassValue> keywords;

    /// Records whether keyword accessors have been observed.
    private boolean keywordsAccessed;

    /// Creates an argument list.
    ///
    /// @param contents   the positional elements
    /// @param separator  the separator
    /// @param keywords   the leftover keyword arguments
    public SassArgumentList(
            List<SassValue> contents,
            ListSeparator separator,
            Map<String, SassValue> keywords
    ) {
        this.contents = List.copyOf(contents);
        this.separator = Objects.requireNonNull(separator, "separator");
        var copy = new LinkedHashMap<String, SassValue>(keywords.size());
        for (var entry : keywords.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "keyword name"),
                    Objects.requireNonNull(entry.getValue(), "keyword value")
            );
        }
        this.keywords = Collections.unmodifiableMap(copy);
    }

    /// Returns the positional elements.
    ///
    /// @return the immutable contents
    @Override
    public @Unmodifiable List<SassValue> asList() {
        return contents;
    }

    /// Returns the list separator.
    ///
    /// @return the separator
    @Override
    public ListSeparator separator() {
        return separator;
    }

    /// Returns the number of positional elements.
    ///
    /// @return the length
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

    /// Returns leftover keywords and marks them as accessed.
    ///
    /// @return the keyword map
    public @Unmodifiable Map<String, SassValue> keywords() {
        keywordsAccessed = true;
        return keywords;
    }

    /// Returns leftover keywords without marking them as accessed.
    ///
    /// @return the keyword map
    public @Unmodifiable Map<String, SassValue> keywordsWithoutMarking() {
        return keywords;
    }

    /// Returns whether keyword accessors have been observed.
    ///
    /// @return whether keywords were accessed
    public boolean wereKeywordsAccessed() {
        return keywordsAccessed;
    }

    /// Returns the CSS representation of the positional list.
    ///
    /// @return the CSS text
    @Override
    public String toCssString() {
        return new SassList(contents, separator, false).toCssString();
    }

    /// Returns the CSS representation of the positional list with configurable
    /// string quoting and output compaction.
    ///
    /// @param quote      whether quoted strings retain surrounding quotes
    /// @param compressed whether optional separator whitespace is omitted
    /// @return the CSS text
    @Override
    public String toCssString(boolean quote, boolean compressed) {
        return new SassList(contents, separator, false).toCssString(quote, compressed);
    }

    /// Returns the inspect representation.
    ///
    /// @return the inspect text
    @Override
    public String toString() {
        return new SassList(contents, separator, false).toString();
    }

    /// Compares positional contents and separator.
    ///
    /// @param other the object to compare
    /// @return whether the list views are equal
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof SassArgumentList list
                && separator == list.separator
                && contents.equals(list.contents)
                || other instanceof SassList list
                && separator == list.separator()
                && !list.hasBrackets()
                && contents.equals(list.contents());
    }

    /// Returns a hash based on positional contents.
    ///
    /// @return the hash code
    @Override
    public int hashCode() {
        return contents.hashCode();
    }
}
