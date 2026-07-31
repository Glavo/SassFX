// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Identifies value functions dispatched by the OpenJFX CSS parser.
///
/// OpenJFX compares only the leading characters required by each supported
/// function name. A longer identifier with the same case-insensitive prefix
/// therefore selects the same parser.
@ApiStatus.Internal
@NotNullByDefault
public enum JavaFXValueFunction {
    /// Selects the RGB or RGBA color parser.
    RGB("rgb"),

    /// Selects the HSB or HSBA color parser.
    HSB("hsb"),

    /// Selects the derived-color parser.
    DERIVE("derive"),

    /// Selects the inner-shadow effect parser.
    INNER_SHADOW("innershadow"),

    /// Selects the drop-shadow effect parser.
    DROP_SHADOW("dropshadow"),

    /// Selects the linear-gradient paint parser.
    LINEAR_GRADIENT("linear-gradient"),

    /// Selects the radial-gradient paint parser.
    RADIAL_GRADIENT("radial-gradient"),

    /// Selects the image-pattern paint parser.
    IMAGE_PATTERN("image-pattern"),

    /// Selects the repeating-image-pattern paint parser.
    REPEATING_IMAGE_PATTERN("repeating-image-pattern"),

    /// Selects the ladder-color parser.
    LADDER("ladder"),

    /// Selects the Region-reference parser.
    REGION("region");

    /// Contains the minimum case-insensitive function-name prefix.
    private final String prefix;

    /// Creates one function classifier.
    ///
    /// @param prefix the minimum OpenJFX function-name prefix
    JavaFXValueFunction(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    /// Returns the OpenJFX function selected by a CSS function name.
    ///
    /// The constants are tested in the same order as OpenJFX's dispatch
    /// chain. The supplied name excludes the opening parenthesis.
    ///
    /// @param name the CSS function name
    /// @return the selected function, or {@code null} when unsupported
    public static @Nullable JavaFXValueFunction fromName(String name) {
        Objects.requireNonNull(name, "name");
        for (var function : values()) {
            if (name.regionMatches(
                    true,
                    0,
                    function.prefix,
                    0,
                    function.prefix.length()
            )) {
                return function;
            }
        }
        return null;
    }

    /// Returns the leading function name in an unquoted CSS value.
    ///
    /// Whitespace between the name and opening parenthesis does not form a
    /// function token and therefore returns {@code null}.
    ///
    /// @param text the complete unquoted CSS value text
    /// @return the function name, or {@code null} when the value does not begin
    ///         with a function token
    public static @Nullable String invocationName(String text) {
        Objects.requireNonNull(text, "text");
        var start = 0;
        while (start < text.length()
                && JavaFXCssLexer.isWhitespace(text.charAt(start))) {
            start++;
        }
        var end = JavaFXCssLexer.identifierEnd(text, start);
        if (end == start
                || end == text.length()
                || text.charAt(end) != '(') {
            return null;
        }
        return text.substring(start, end);
    }
}
