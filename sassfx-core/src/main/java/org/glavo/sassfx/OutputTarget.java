// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Describes a compiler output representation and its result type.
///
/// @param <T> the type produced for this target
@NotNullByDefault
public sealed interface OutputTarget<T> permits CssTarget, JavaFXCssTarget, BssTarget {
    /// Parses a canonical output-target selector using the target's default
    /// formatting settings.
    ///
    /// `css` selects [CssTarget#DEFAULT]. `css/javafx@N` and
    /// `bss/javafx@N` select JavaFX release `N`, where `N` is an integer from
    /// `8` through `27` without leading zeroes. Text targets use expanded
    /// output. Standard CSS enables charset markers; JavaFX CSS omits them
    /// because OpenJFX does not recognize either marker form.
    ///
    /// @param selector the case-sensitive target selector
    /// @return the parsed output target
    /// @throws IllegalArgumentException if `selector` is not canonical or
    /// names an unsupported JavaFX release
    static OutputTarget<?> parse(String selector) {
        Objects.requireNonNull(selector, "selector");
        if (selector.equals("css")) {
            return CssTarget.DEFAULT;
        }

        var separator = selector.indexOf("/javafx@");
        if (separator <= 0
                || separator != selector.lastIndexOf("/javafx@")) {
            throw unsupportedSelector(selector);
        }
        var format = selector.substring(0, separator);
        var versionText = selector.substring(
                separator + "/javafx@".length()
        );
        if ((!format.equals("css") && !format.equals("bss"))
                || !isCanonicalVersion(versionText)) {
            throw unsupportedSelector(selector);
        }

        final JavaFXTarget javaFXTarget;
        try {
            javaFXTarget = JavaFXTarget.forVersion(
                    Integer.parseInt(versionText)
            );
        } catch (IllegalArgumentException failure) {
            throw unsupportedSelector(selector);
        }
        return format.equals("css")
                ? new JavaFXCssTarget(
                        javaFXTarget,
                        OutputStyle.EXPANDED
                )
                : new BssTarget(javaFXTarget);
    }

    /// Reports whether a version is an unsigned canonical decimal integer.
    ///
    /// @param version the version text
    /// @return whether the text contains only decimal digits and has no
    /// leading zero
    private static boolean isCanonicalVersion(String version) {
        if (version.isEmpty() || version.charAt(0) == '0') {
            return false;
        }
        for (var index = 0; index < version.length(); index++) {
            var character = version.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    /// Creates a failure for a noncanonical or unsupported selector.
    ///
    /// @param selector the rejected selector
    /// @return the selector failure
    private static IllegalArgumentException unsupportedSelector(
            String selector
    ) {
        return new IllegalArgumentException(
                "Unsupported output target '" + selector
                        + "'; expected 'css', 'css/javafx@8' through "
                        + "'css/javafx@27', or 'bss/javafx@8' through "
                        + "'bss/javafx@27'."
        );
    }
}
