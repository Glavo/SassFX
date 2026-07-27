// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the runtime kind of a [SassValue].
@NotNullByDefault
public enum SassValueType {
    /// The singleton Sass null value.
    NULL,

    /// A Sass boolean.
    BOOLEAN,

    /// A Sass number with zero or more units.
    NUMBER,

    /// A quoted or unquoted Sass string.
    STRING,

    /// A Sass list.
    LIST,

    /// A rest argument list with positional and keyword arguments.
    ARGUMENT_LIST,

    /// An insertion-ordered Sass map.
    MAP,

    /// A Sass color.
    COLOR,

    /// An unresolved CSS calculation.
    CALCULATION,

    /// A first-class function reference.
    FUNCTION,

    /// A first-class mixin reference.
    MIXIN
}
