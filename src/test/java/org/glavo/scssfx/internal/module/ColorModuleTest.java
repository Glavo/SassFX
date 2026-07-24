// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.CompileResult;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies legacy RGB transformations exported by {@code sass:color}.
@NotNullByDefault
final class ColorModuleTest {
    /// Compiles legacy color transformations and compatible plain-CSS filters.
    @Test
    void evaluatesLegacyRgbColorTransformsWithoutLoadingStylesheets() throws Exception {
        var result = compile(
                """
                        @use "sass:color";

                        .example {
                          midpoint: color.mix(#000, #fff);
                          named: color.mix($color2: #fff, $color1: #000, $weight: 25%);
                          alpha-mix: color.mix(rgba(255, 0, 0, 0.25), rgba(0, 0, 255, 0.75));
                          transparent-mix: color.mix(transparent, red);
                          inverted: color.invert(#123);
                          partial-invert: color.invert(#123, 25%);
                          css-invert: color.invert(0.5);
                          css-grayscale: color.grayscale(0.25);
                          rounded-channel: color.red(color.mix(#000, #fff));
                          fuzzy-low: color.mix(red, blue, -0.000000000001%);
                          fuzzy-high: color.mix(red, blue, 100.000000000001%);
                          hue: color.hue(red);
                          saturation: color.saturation(red);
                          lightness: color.lightness(red);
                          grayscale: color.grayscale(red);
                          complement: color.complement(red);
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          midpoint: rgb(127.5, 127.5, 127.5);
                          named: rgb(191.25, 191.25, 191.25);
                          alpha-mix: rgba(63.75, 0, 191.25, 0.5);
                          transparent-mix: rgba(255, 0, 0, 0.5);
                          inverted: #eeddcc;
                          partial-invert: rgb(72.25, 80.75, 89.25);
                          css-invert: invert(0.5);
                          css-grayscale: grayscale(0.25);
                          rounded-channel: 128;
                          fuzzy-low: blue;
                          fuzzy-high: red;
                          hue: 0deg;
                          saturation: 100%;
                          lightness: 50%;
                          grayscale: rgb(127.5, 127.5, 127.5);
                          complement: aqua;
                        }""",
                result.output()
        );
        assertEquals(Set.of(), result.loadedUrls());
    }

    /// Compiles legacy adjust, scale, and change channel updates.
    @Test
    void evaluatesLegacyColorChannelUpdates() throws Exception {
        var result = compile(
                """
                        @use "sass:color";

                        .example {
                          change-blue: color.change(red, $blue: 255);
                          adjust-lightness: color.adjust(red, $lightness: -10%);
                          scale-lightness: color.scale(red, $lightness: -50%);
                          adjust-alpha: color.adjust(rgba(255, 0, 0, 0.5), $alpha: 0.25);
                          change-hue: color.change(red, $hue: 120deg);
                          scale-red: color.scale(#336699, $red: 50%);
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          change-blue: fuchsia;
                          adjust-lightness: #cc0000;
                          scale-lightness: rgb(127.5, 0, 0);
                          adjust-alpha: rgba(255, 0, 0, 0.75);
                          change-hue: lime;
                          scale-red: #996699;
                        }""",
                result.output()
        );
    }

    /// Exercises Color 4 space query, conversion, channel, and same APIs.
    @Test
    void evaluatesColorFourSpaceAndChannelApis() throws Exception {
        var result = compile(
                """
                        @use "sass:color";

                        .example {
                          space: color.space(red);
                          legacy: color.is-legacy(red);
                          modern-legacy: color.is-legacy(color.to-space(red, oklab));
                          converted-space: color.space(color.to-space(red, oklch));
                          red-channel: color.channel(red, "red");
                          oklab-l: color.channel(red, "lightness", $space: oklab);
                          same-self: color.same(red, red);
                          same-spaces: color.same(red, color.to-space(red, srgb));
                          in-gamut: color.is-in-gamut(red);
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          space: rgb;
                          legacy: true;
                          modern-legacy: false;
                          converted-space: oklch;
                          red-channel: 255;
                          oklab-l: 62.7955363921%;
                          same-self: true;
                          same-spaces: true;
                          in-gamut: true;
                        }""",
                result.output()
        );
    }

    /// Exercises Color 4 mix interpolation, invert/complement spaces, and gamut mapping.
    @Test
    void evaluatesColorFourInterpolationAndGamutMapping() throws Exception {
        var result = compile(
                """
                        @use "sass:color";

                        .example {
                          mix-rgb: color.mix(red, blue, $method: rgb);
                          mix-oklab: color.space(color.mix(red, blue, $method: oklab));
                          invert-hsl: color.invert(red, $space: hsl);
                          complement-oklch: color.space(color.complement(red, $space: oklch));
                          change-oklab: color.space(color.change(red, $lightness: 50%, $space: oklab));
                          to-gamut: color.is-in-gamut(
                            color.to-gamut(color.to-space(red, display-p3), $method: clip)
                          );
                          powerless: color.is-powerless(gray, "hue", $space: hsl);
                        }
                        """
        );

        assertEquals(
                """
                        .example {
                          mix-rgb: rgb(127.5, 0, 127.5);
                          mix-oklab: rgb;
                          invert-hsl: aqua;
                          complement-oklch: rgb;
                          change-oklab: rgb;
                          to-gamut: true;
                          powerless: true;
                        }""",
                result.output()
        );
    }

    /// Validates argument errors that remain after Color 4 interpolation landed.
    @Test
    void rejectsUnsupportedColorSpacesAndInvalidWeights() {
        assertEquals(
                "$weight: Expected 101% to be within 0% and 100%.",
                failure("@use \"sass:color\"; .a { value: color.mix(red, blue, 101%); }")
        );
        assertEquals(
                "$weight: Expected 1px to have unit \"%\" or no units.",
                failure("@use \"sass:color\"; .a { value: color.invert(red, 1px); }")
        );
        assertEquals(
                "$color1: 1 is not a color.",
                failure("@use \"sass:color\"; .a { value: color.mix(1, blue, $method: rgb); }")
        );
        assertEquals(
                "$weight: blue is not a number.",
                failure("@use \"sass:color\"; .a { value: color.invert(red, blue, $space: hsl); }")
        );
        assertEquals(
                "Only one argument may be passed to the plain-CSS invert() function.",
                failure("@use \"sass:color\"; .a { value: color.invert(0.5, 50%); }")
        );
        assertEquals(
                "Only one positional argument is allowed. All other arguments must be passed by name.",
                failure("@use \"sass:color\"; .a { value: color.adjust(red, 10%); }")
        );
        assertEquals(
                "$hue: Channel isn't scalable.",
                failure("@use \"sass:color\"; .a { value: color.scale(red, $hue: 10%); }")
        );
        assertEquals(
                "$method: color.to-gamut() requires a $method argument for forwards-compatibility with changes in the CSS spec. Suggestion:\n"
                        + "\n"
                        + "$method: local-minde",
                failure("@use \"sass:color\"; .a { value: color.to-gamut(red); }")
        );
        assertEquals(
                "$method: Hue interpolation method \"longer hue\" may not be set for rectangular color space rgb.",
                failure("@use \"sass:color\"; .a { value: color.mix(red, blue, $method: rgb longer hue); }")
        );
        assertEquals(
                "$method: Unknown hue interpolation method sideways.",
                failure("@use \"sass:color\"; .a { value: color.mix(red, blue, $method: oklch sideways hue); }")
        );
    }

    /// Compiles one SCSS string source with the expanded CSS target.
    ///
    /// @param source the source text to compile
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static CompileResult<String> compile(String source) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(source, Syntax.SCSS),
                CssTarget.DEFAULT
        );
    }

    /// Compiles one source that is expected to fail.
    ///
    /// @param source the source text to compile
    /// @return the primary compilation message
    private static String failure(String source) {
        return assertThrows(
                SassCompilationException.class,
                () -> compile(source)
        ).getMessage();
    }
}
