// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Identifies how the elements of a Sass list value or expression are separated.
@ApiStatus.Internal
@NotNullByDefault
public enum ListSeparator {
    /// Identifies a space-separated list.
    SPACE("space", " "),

    /// Identifies a comma-separated list.
    COMMA("comma", ","),

    /// Identifies a slash-separated list.
    SLASH("slash", "/"),

    /// Identifies an empty or singleton list whose separator is not established.
    UNDECIDED("undecided", null);

    /// Contains the human-readable separator name used by diagnostics.
    private final String displayName;

    /// Contains the source separator, or {@code null} when it is undecided.
    private final @Nullable String source;

    /// Creates a list separator descriptor.
    ///
    /// @param displayName the human-readable separator name
    /// @param source      the source separator, or {@code null} when undecided
    ListSeparator(String displayName, @Nullable String source) {
        this.displayName = displayName;
        this.source = source;
    }

    /// Returns the source separator.
    ///
    /// @return the separator, or {@code null} when it is undecided
    public @Nullable String source() {
        return source;
    }

    /// Returns the human-readable separator name.
    ///
    /// @return the separator name
    @Override
    public String toString() {
        return displayName;
    }
}
