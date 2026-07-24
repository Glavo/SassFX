// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Represents an immutable insertion-ordered Sass map.
///
/// @param contents the insertion-ordered map contents
@ApiStatus.Internal
@NotNullByDefault
public record SassMap(@Unmodifiable Map<SassValue, SassValue> contents) implements SassValue {
    /// Creates a defensive immutable copy of a Sass map.
    public SassMap {
        Objects.requireNonNull(contents, "contents");
        var copy = new LinkedHashMap<SassValue, SassValue>(contents.size());
        for (var entry : contents.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "map key"),
                    Objects.requireNonNull(entry.getValue(), "map value")
            );
        }
        contents = Collections.unmodifiableMap(copy);
    }

    /// Returns comma separation for non-empty maps and undecided separation for empty maps.
    ///
    /// @return the map's list-view separator
    @Override
    public ListSeparator separator() {
        return contents.isEmpty() ? ListSeparator.UNDECIDED : ListSeparator.COMMA;
    }

    /// Returns map entries as space-separated key-value pair lists.
    ///
    /// @return an immutable list in insertion order
    @Override
    public @Unmodifiable List<SassValue> asList() {
        var result = new ArrayList<SassValue>(contents.size());
        for (var entry : contents.entrySet()) {
            result.add(new SassList(
                    List.of(entry.getKey(), entry.getValue()),
                    ListSeparator.SPACE,
                    false
            ));
        }
        return List.copyOf(result);
    }

    /// Returns the number of map entries in the universal list view.
    ///
    /// @return the entry count
    @Override
    public int lengthAsList() {
        return contents.size();
    }

    /// Returns this map.
    ///
    /// @return this map
    @Override
    public SassMap assertMap() {
        return this;
    }

    /// Returns this map.
    ///
    /// @return this map
    @Override
    public SassMap tryMap() {
        return this;
    }

    /// Rejects direct CSS serialization of a Sass map.
    ///
    /// @return no value
    /// @throws SassValueException always, because maps are not CSS values
    @Override
    public String toCssString() {
        throw new SassValueException(this + " isn't a valid CSS value.");
    }

    /// Rejects direct CSS serialization regardless of string quote mode.
    ///
    /// @param quote whether nested strings would retain quotes
    /// @return no value
    /// @throws SassValueException always, because maps are not CSS values
    @Override
    public String toCssString(boolean quote) {
        throw new SassValueException(this + " isn't a valid CSS value.");
    }

    /// Compares semantic map contents without considering insertion order.
    ///
    /// An empty map also equals an empty Sass list.
    ///
    /// @param other the object to compare
    /// @return whether the Sass values are equal
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || other instanceof SassMap map && contents.equals(map.contents)
                || contents.isEmpty() && other instanceof SassList list
                && list.contents().isEmpty();
    }

    /// Returns a content hash compatible with empty-list equality.
    ///
    /// @return the Sass map hash
    @Override
    public int hashCode() {
        return contents.isEmpty() ? List.of().hashCode() : contents.hashCode();
    }

    /// Returns the inspect-mode parenthesized map representation.
    ///
    /// Comma-separated unbracketed lists used as keys or values are wrapped in
    /// an extra pair of parentheses so nested list separators remain unambiguous,
    /// matching dart-sass inspect output.
    ///
    /// @return entries separated by commas
    @Override
    public String toString() {
        var result = new StringBuilder("(");
        var first = true;
        for (var entry : contents.entrySet()) {
            if (!first) {
                result.append(", ");
            }
            first = false;
            result.append(inspectMapElement(entry.getKey()))
                    .append(": ")
                    .append(inspectMapElement(entry.getValue()));
        }
        return result.append(')').toString();
    }

    /// Serializes one map key or value for inspect mode.
    private static String inspectMapElement(SassValue value) {
        if (value instanceof SassList list
                && list.separator() == ListSeparator.COMMA
                && !list.hasBrackets()) {
            return "(" + list + ")";
        }
        if (value instanceof SassArgumentList argumentList
                && argumentList.separator() == ListSeparator.COMMA) {
            return "(" + argumentList + ")";
        }
        return value.toString();
    }
}
