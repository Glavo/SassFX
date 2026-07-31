// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.oracle;

import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/// Generates filesystem inputs consumed by the isolated JavaFX 8 process.
///
/// This generator runs on Java 17 and is the only side of the JavaFX 8 oracle
/// that loads SassFX product classes.
@NotNullByDefault
public final class JavaFX8OracleInputGenerator {
    /// Contains the shared JavaFX 8 compatibility fixture.
    private static final String SOURCE = """
            @font-face {
              font-family: "Oracle Font";
              src: url("font.ttf");
            }
            Tooltip {
              -fx-show-delay: 250ms;
              -fx-show-duration: 1.5s;
              -fx-hide-delay: indefinite;
            }
            Shape {
              -fx-fill: url("fill.png");
              -fx-effect: dropshadow(gaussian, rgba(18, 52, 86, 0.3), 8px, 20%, 1px, 2px);
            }
            PrefixColors {
              -fx-a: RgBSuffix(18, 52, 86, 0.3);
              -fx-b: hsbSuffix(210, 79%, 34%, 0.3);
              -fx-c: deriveSuffix(#123456, 10%);
              -fx-d: ladderSuffix(#123456, red 0%, blue 100%);
            }
            PrefixEffects {
              -fx-effect: dropshadowSuffix(
                  gaussian, #123456, 8px, 20%, 1px, 2px);
            }
            PrefixInnerEffect {
              -fx-effect: innershadowSuffix(
                  three-pass-box, #123456, 8px, 20%, 1px, 2px);
            }
            PrefixPaints {
              -fx-background-color:
                  linear-gradientSuffix(red, blue),
                  radial-gradientSuffix(radius 50%, red, blue);
              -fx-fill: image-patternSuffix(
                  "image.png", 0%, 0%, 100%, 100%, false);
              -fx-stroke: repeating-image-patternSuffix("tile.png");
              -fx-text-fill: regionSuffix("#glyph");
            }
            PrefixBorder {
              -fx-border-style: SeGmEnTsSuffix(1px, 2px);
            }
            Pane {
              -fx-graphic: url("icon.png");
              -fx-background-color: linear-gradient(red, blue);
              -fx-font-family: "Oracle Font";
              -fx-blend-mode: multiply;
            }
            SpecialScalars {
              -fx-custom-inherit: InHeRiT;
              -fx-custom-none: NONE;
              -fx-custom-null: NuLl;
              -fx-font-smoothing-type: "GRAY";
              -fx-blend-mode: MULTIPLY;
              -fx-stroke-line-cap: ROUND;
              -fx-stroke-line-join: BeVeL;
              -fx-stroke-type: INSIDE;
              -fx-stroke-dash-array: 1PX 2em -3deg 4turn;
            }
            LegacyBlend { -fx-blend-mode: #123456; }
            Pane:dir(rtl) { -fx-opacity: 0.5; }
            Label:lang("en") { -fx-opacity: 0.5; }
            Region:state(primary selected) { -fx-opacity: 0.5; }
            Button:state(primary/**/secondary) { -fx-opacity: 0.5; }
            Control:empty-state() { -fx-opacity: 0.5; }
            Node:not(leaf) { -fx-opacity: 0.5; }
            CaseInsensitiveProperties {
              -FX-BASE: #123456;
              -FX-SELF: -FX-SELF;
              -FX-FORWARD: -FX-LATER;
              -FX-BACKGROUND-COLOR:
                  linear-gradient(-FX-BASE, derive(-FX-BASE, 10%));
              -FX-PADDING: 1px 2px 3px 4px;
              -FX-LATER: red;
              -FX-CUSTOM-TOKEN: MixedCase;
            }
            QuotedStrings {
              -fx-ellipsis-string: "More";
              -fx-prompt-text: 'Type here';
              -fx-shape: "M 0 0 L 1 1 Z";
              -fx-custom-string: "";
              -fx-custom-true: "TrUe";
              -fx-custom-false: 'FALSE';
              -fx-custom-duration: "INDEFINITE";
              -fx-custom-infinity: "Infinity";
              -fx-custom-color: "red";
              -fx-custom-hex-color: "#123456";
              -fx-font-family: "Oracle Font";
              -fx-keyword-font-family: indefinite;
              -fx-quoted-keyword-font-family: "true";
              -fx-empty-font-family: "";
            }
            GenericSizeSequence {
              -fx-custom-single-size: 1px;
              -fx-custom-sizes:
                  1 2% 3em 4ex 5px 6cm 7mm 8in 9pt 10pc
                  11deg 12grad 13rad 14turn;
              -fx-custom-mixed-sizes: -1.5em 0 2turn;
            }
            FontSizeKeywords {
              -fx-a-font-size: inherit;
              -fx-b-font-size: xx-small;
              -fx-c-font-size: x-small;
              -fx-d-font-size: small;
              -fx-e-font-size: medium;
              -fx-f-font-size: large;
              -fx-g-font-size: x-large;
              -fx-h-font-size: xx-large;
              -fx-i-font-size: smaller;
              -fx-j-font-size: larger;
              -fx-font: italic large/medium "Example Sans";
            }
            FontProperties {
              -fx-font-family: "SERIF";
              -fx-font-size: 45deg;
              -fx-font-style: oblique;
              -fx-font-weight: 600;
              -fx-font: bold inherit small-caps 14deg/18grad "SERIF";
            }
            RegionGeometry {
              -fx-background-color: #123456, rgba(18, 52, 86, 0.3);
              -fx-background-insets: 1px 2px, 3px;
              -fx-background-radius: 4px 5px / 6px 7px, 8%;
              -fx-opaque-insets: 9px 10px 11px 12px;
              -fx-border-color: #123456 red green blue, #abcdef;
              -fx-border-insets: 13px 14px, 15px;
              -fx-border-radius: 16px 17px / 18px 19px, 20%;
              -fx-border-width: 1px 2px 3px 4px, 5%;
            }
            RegionImages {
              -fx-background-image: url("image.png"), url(second.png);
              -fx-background-position: left 10px top 20%, right 5px bottom 6px, center bottom;
              -fx-background-repeat: repeat-x, space no-repeat, round;
              -fx-background-size: 25px auto, auto 30%, cover;
              -fx-border-image-source: url("border.png");
              -fx-border-image-insets: 1px 2px 3px 4px, -fx-image-inset;
              -fx-border-image-repeat: repeat-x, space round, stretch;
              -fx-border-image-slice: 10% fill, -fx-image-slice fill, 20% 30% 40% 50%;
              -fx-border-image-width: 1px 2% 3 4px, auto -fx-image-width 6px 7%, -fx-other-width;
            }
            RegionPaints {
              -fx-base: #123456;
              -fx-background-color:
                  radial-gradient(focus-angle 45deg, focus-distance 20%, center 30% 40%, radius 50%, repeat, red 0%, green 50%, blue 100%),
                  image-pattern("image.png", 1px, 2px, 3px, 4px, false),
                  repeating-image-pattern("tile.png"),
                  -fx-base;
              -fx-border-color:
                  linear-gradient(from 0% 0% to 100% 100%, repeat, red 0%, blue 100%)
                  image-pattern("border.png", 5%, 6%, 7%, 8%, true)
                  radial-gradient(radius 20px, red, blue)
                  #123456;
              -fx-border-style:
                  solid dashed dotted hidden,
                  segments(1px, 2px, 3px) phase 4px outside line-join miter 5px line-cap square,
                  dashed centered line-join bevel line-cap butt;
            }
            RegionReferences {
              -fx-fill: region("#glyph");
              -fx-background-color:
                  region(".source"),
                  region("#badge"),
                  REGION(""),
                  regionXYZ(".escaped\\6f"),
                  region(".first", ".ignored"),
                  region(".series" ".ignored");
            }
            LegacyLinear {
              -fx-background-color:
                  linear (0%,0%) to (100%,100%) stops (0.0,red) (0.5,rgba(0, 255, 0, 0.5)) (1.0,blue) repeat;
            }
            LegacyRadial {
              -fx-background-color:
                  radial focus-angle 45deg focus-distance 20% center (30%,40%) 50% stops (0.0,red) (0.5,green) (1.0,blue) no-cycle;
            }
            LegacyLookup {
              -fx-background-color:
                  linear (-fx-start-x,-fx-start-y) to (-fx-end-x,-fx-end-y)
                  stops (-fx-stop-offset,-fx-base) (1.0,blue);
            }
            LegacyLadder {
              -fx-fill:
                  ladder -fx-base stops
                  (0.0,black) (1.0,derive(-fx-base, 20%));
              -fx-stroke:
                  ladder #123456 stops (0.5,white);
            }
            EmptyBorder {
              -fx-border-style: none;
            }
            LookupBorder {
              -fx-border-style:
                  segments(-fx-dash-a, -fx-dash-b)
                  phase -fx-phase
                  outside
                  line-join miter -fx-miter
                  line-cap round;
            }
            """;

    /// Contains a representative plain-CSS JavaFX declaration matrix.
    private static final String PLAIN_CSS_SOURCE = """
            @font-face {
              font-family: "Plain Oracle";
              src: url("font.ttf");
            }
            PlainPane {
              property: red;
              _property: 1px;
              -property: true;
              property-: "value";
              A0_B-c: 250ms;
              red: #123456;
              green: #abcdef;
              -fx-opacity: .5 ! IMPORTANT;
              -fx-disable: true;
              -fx-font-family: "Plain Oracle";
              -fx-font-size: 12px;
              -fx-padding: 1px 2px 3px 4px;
              -fx-background-color:
                  red,
                  linear-gradient(red, blue);
              -fx-background-insets: 1px 2px, 3px;
              -fx-background-radius: 4px 5px / 6px 7px, 8%;
              -fx-border-color: #123456 red green blue, #abcdef;
              -fx-border-insets: 1px;
              -fx-border-radius: 4px / 8px;
              -fx-border-width: 1px 2px;
              -fx-border-style:
                  segments(1px, 2px) outside,
                  dashed centered;
              -fx-fill: red;
              -fx-effect:
                  dropshadow(gaussian, green, 8px, 20%, 1px, 2px);
              -fx-stroke-dash-array: 1px 2px;
            }
            """;

    /// Prevents instantiation.
    private JavaFX8OracleInputGenerator() {
    }

    /// Writes generated CSS and SassFX BSS into the requested directory.
    ///
    /// @param arguments one argument naming the output directory
    /// @throws Exception if compilation or filesystem output fails
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one output directory.");
        }
        var directory = Path.of(arguments[0]).toAbsolutePath();
        Files.createDirectories(directory);
        var cssPath = directory.resolve("fixture.css");
        var actualBssPath = directory.resolve("actual.bss");
        var source = SassSource.fromString(SOURCE, Syntax.SCSS, cssPath.toUri());
        var plainCssPath = directory.resolve("plain-fixture.css");
        var plainActualBssPath = directory.resolve("plain-actual.bss");
        var plainSource = SassSource.fromString(
                PLAIN_CSS_SOURCE,
                Syntax.CSS,
                plainCssPath.toUri()
        );
        var compiler = new SassCompiler();

        Files.writeString(
                cssPath,
                compiler.compile(
                        source,
                        new JavaFXCssTarget(
                                JavaFXTarget.JAVAFX8,
                                OutputStyle.EXPANDED
                        )
                ).output()
        );
        Files.write(
                actualBssPath,
                remainingBytes(compiler.compile(
                        source,
                        new BssTarget(JavaFXTarget.JAVAFX8)
                ).output())
        );
        Files.writeString(
                plainCssPath,
                compiler.compile(
                        plainSource,
                        new JavaFXCssTarget(
                                JavaFXTarget.JAVAFX8,
                                OutputStyle.EXPANDED
                        )
                ).output()
        );
        Files.write(
                plainActualBssPath,
                remainingBytes(compiler.compile(
                        plainSource,
                        new BssTarget(JavaFXTarget.JAVAFX8)
                ).output())
        );
    }

    /// Copies all remaining output bytes without changing the supplied buffer.
    ///
    /// @param buffer the binary compiler output
    /// @return a newly allocated byte array
    private static byte @Unmodifiable [] remainingBytes(
            @Unmodifiable ByteBuffer buffer
    ) {
        var copy = buffer.duplicate();
        var bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
